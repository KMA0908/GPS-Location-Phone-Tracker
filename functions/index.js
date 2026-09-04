const {GoogleAuth} = require("google-auth-library");
const crypto = require("node:crypto");
const {initializeApp} = require("firebase-admin/app");
const {getDatabase} = require("firebase-admin/database");
const {logger} = require("firebase-functions");
const {HttpsError, onCall} = require("firebase-functions/v2/https");

initializeApp();

const COMPUTE_ROUTES_URL =
  "https://routes.googleapis.com/directions/v2:computeRoutes";
const RESPONSE_FIELD_MASK =
  "routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline";
const SUPPORTED_TRAVEL_MODES = new Set(["DRIVE", "TWO_WHEELER", "WALK"]);
const GOOGLE_CLOUD_SCOPE = "https://www.googleapis.com/auth/cloud-platform";

const auth = new GoogleAuth({scopes: [GOOGLE_CLOUD_SCOPE]});

const OWNER_FUNCTION_OPTIONS = {
  region: "asia-southeast1",
  timeoutSeconds: 15,
  memory: "256MiB",
  minInstances: 0,
  maxInstances: 5,
  concurrency: 40,
  enforceAppCheck: true,
};
const MAX_FRIEND_COUNT = 5;
const MAX_PHONE_MATCHES = 20;
const MAX_SECONDARY_PHONE_COUNT = 5;
const FRIEND_LOCATION_MAX_AGE_SECONDS = 60;

exports.upsertProfile = onCall(OWNER_FUNCTION_OPTIONS, async (request) => {
  const {uid, ownerSecret} = readOwnerCredentials(request.data);
  const profile = readProfile(request.data?.profile, uid);
  const database = getDatabase();
  const ref = database.ref(`users/${uid}`);
  const existingProfile = (await ref.get()).val();
  const profileAlreadyExists = Boolean(existingProfile?.userId);
  await verifyOwner(uid, ownerSecret, !profileAlreadyExists);

  // Profile fields and every phone index move together. Specific child paths are
  // used so concurrent friend/availability updates are never overwritten.
  const updates = {
    [`users/${uid}/userId`]: uid,
    [`users/${uid}/name`]: profile.name,
    [`users/${uid}/phone`]: profile.phone,
    [`users/${uid}/avt`]: profile.avt,
    [`users/${uid}/avatarKey`]: profile.avatarKey,
    [`users/${uid}/secondaryPhones`]:
      Object.keys(profile.secondaryPhones).length > 0 ?
        profile.secondaryPhones : null,
  };
  const previousPhones = profileIndexedPhones(existingProfile);
  const nextPhones = profileIndexedPhones(profile);
  const rawPreviousPhone = String(existingProfile?.phone || "")
      .trim().replace(/[\s()-]/g, "");
  if (rawPreviousPhone && !nextPhones.has(rawPreviousPhone) &&
      !/[.#$\[\]/]/.test(rawPreviousPhone)) {
    updates[`phoneToUidMap/${rawPreviousPhone}/${uid}`] = null;
  }
  for (const phone of previousPhones) {
    if (!nextPhones.has(phone)) {
      updates[`phoneToUidMap/${phone}/${uid}`] = null;
    }
  }
  for (const phone of nextPhones) {
    updates[`phoneToUidMap/${phone}/${uid}`] = {
      type: phone === profile.phone ? "primary" : "secondary",
      addedBy: uid,
      addedTime: Date.now(),
    };
  }
  await database.ref().update(updates);
  return {ok: true};
});

exports.findUserByPhone = onCall(OWNER_FUNCTION_OPTIONS, async (request) => {
  const {uid, ownerSecret} = readOwnerCredentials(request.data);
  await verifyOwner(uid, ownerSecret);
  const ownProfile = await requireProfile(uid);
  const ownFriendIds = new Set(normalizeFriendIds(ownProfile.friendIds, uid));
  const phone = normalizePhone(request.data?.phone);
  const mapped = (await getDatabase().ref(`phoneToUidMap/${phone}`).get()).val();
  const candidateIds = Object.keys(mapped || {}).sort().slice(0, MAX_PHONE_MATCHES);
  const matches = [];
  for (const candidateId of candidateIds) {
    const profile = (await getDatabase().ref(`users/${candidateId}`).get()).val();
    if (!profile || !profileIndexedPhones(profile).has(phone)) continue;

    const targetFriendIds = new Set(
        normalizeFriendIds(profile.friendIds, candidateId),
    );
    const isMutualFriend = ownFriendIds.has(candidateId) &&
      targetFriendIds.has(uid);
    const presence = isMutualFriend ?
      await readPresence(candidateId, profile) : hiddenPresence();

    matches.push({
      profile: publicProfile(candidateId, profile, presence),
      location: visibleLocation(presence),
      isFriend: isMutualFriend,
    });
  }
  if (matches.length > 0) return {matches};
  throw new HttpsError("not-found", "No user found with this phone number");
});

exports.getOwnProfile = onCall(OWNER_FUNCTION_OPTIONS, async (request) => {
  const {uid, ownerSecret} = readOwnerCredentials(request.data);
  await verifyOwner(uid, ownerSecret);
  const profile = await requireProfile(uid);
  const presence = await readPresence(uid, profile);
  return {
    profile: {
      ...publicProfile(uid, profile, presence),
      friendIds: normalizeFriendIds(profile.friendIds, uid),
      secondaryPhones: profile.secondaryPhones || {},
    },
  };
});

exports.getFriendsSnapshot = onCall(
    OWNER_FUNCTION_OPTIONS,
    async (request) => {
      const {uid, ownerSecret} = readOwnerCredentials(request.data);
      await verifyOwner(uid, ownerSecret);
      const ownProfile = await requireProfile(uid);
      const friendIds = normalizeFriendIds(ownProfile.friendIds, uid);
      const friends = [];
      for (const friendId of friendIds) {
        const profile = (
          await getDatabase().ref(`users/${friendId}`).get()
        ).val();
        if (!profile) continue;
        const isMutualFriend = normalizeFriendIds(
            profile.friendIds,
            friendId,
        ).includes(uid);
        const presence = isMutualFriend ?
          await readPresence(friendId, profile) : hiddenPresence();
        friends.push({
          profile: publicProfile(friendId, profile, presence),
          location: visibleLocation(presence),
        });
      }
      return {friends};
    },
);

exports.findUserById = onCall(OWNER_FUNCTION_OPTIONS, async (request) => {
  const {uid, ownerSecret} = readOwnerCredentials(request.data);
  await verifyOwner(uid, ownerSecret);
  await requireProfile(uid);
  const requestedId = readUid(request.data?.friendId, "friendId");
  for (const candidateId of idCandidates(requestedId)) {
    const profile = (await getDatabase().ref(`users/${candidateId}`).get()).val();
    if (!profile) continue;
    return {
      profile: publicProfile(candidateId, profile, hiddenPresence()),
    };
  }
  throw new HttpsError("not-found", "Profile not found");
});

exports.isFriend = onCall(OWNER_FUNCTION_OPTIONS, async (request) => {
  const {uid, ownerSecret} = readOwnerCredentials(request.data);
  await verifyOwner(uid, ownerSecret);
  const ownProfile = await requireProfile(uid);
  const requestedId = readUid(request.data?.friendId, "friendId");
  const candidates = new Set(idCandidates(requestedId));
  return {
    isFriend: normalizeFriendIds(ownProfile.friendIds, uid)
        .some((friendId) => candidates.has(friendId)),
  };
});

exports.setLocationSharing = onCall(
    OWNER_FUNCTION_OPTIONS,
    async (request) => {
      const {uid, ownerSecret} = readOwnerCredentials(request.data);
      await verifyOwner(uid, ownerSecret);
      await requireProfile(uid);
      const enabled = request.data?.enabled;
      if (typeof enabled !== "boolean") {
        throw new HttpsError("invalid-argument", "Invalid sharing value");
      }
      const presenceRef = getDatabase().ref(`presence/${uid}`);
      const before = (await presenceRef.get()).val();
      await presenceRef.transaction((current) => {
        const state = current || before || {};
        if (!enabled) return {enabled: false, online: false};
        return {
          enabled: true,
          online: state.enabled === true && state.online === true,
          ...(state.enabled === true && state.location ?
            {location: state.location} : {}),
        };
      }, undefined, false);
      const updates = {
        [`users/${uid}/trackingAvailable`]: enabled,
        [`users/${uid}/hasOnline`]: false,
        [`locations/${uid}`]: null,
      };
      await getDatabase().ref().update(updates);
      return {ok: true};
    },
);

exports.updateAvailability = onCall(
    OWNER_FUNCTION_OPTIONS,
    async (request) => {
      const {uid, ownerSecret} = readOwnerCredentials(request.data);
      await verifyOwner(uid, ownerSecret);
      if (typeof request.data?.hasOnline !== "boolean") {
        throw new HttpsError("invalid-argument", "Invalid availability value");
      }
      const profile = await requireProfile(uid);
      const presenceRef = getDatabase().ref(`presence/${uid}`);
      const before = (await presenceRef.get()).val();
      let actualOnline = false;
      await presenceRef.transaction((current) => {
        const state = current || before || {
          enabled: profile.trackingAvailable === true,
        };
        actualOnline = state.enabled === true && request.data.hasOnline;
        return {
          ...state,
          enabled: state.enabled === true,
          online: actualOnline,
        };
      }, undefined, false);
      await getDatabase().ref(`users/${uid}/hasOnline`).set(actualOnline);
      return {ok: true};
    },
);

exports.updateLocation = onCall(OWNER_FUNCTION_OPTIONS, async (request) => {
  const {uid, ownerSecret} = readOwnerCredentials(request.data);
  await verifyOwner(uid, ownerSecret);
  const profile = await requireProfile(uid);
  const latitude = Number(request.data?.latitude);
  const longitude = Number(request.data?.longitude);
  if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90 ||
      !Number.isFinite(longitude) || longitude < -180 || longitude > 180 ||
      (latitude === 0 && longitude === 0)) {
    throw new HttpsError("invalid-argument", "Invalid location");
  }
  const presenceRef = getDatabase().ref(`presence/${uid}`);
  const before = (await presenceRef.get()).val();
  let sharingDisabled = false;
  const updatedAtSeconds = Math.floor(Date.now() / 1000);
  const result = await presenceRef.transaction((current) => {
    const state = current || before || {
      enabled: profile.trackingAvailable === true,
    };
    if (state.enabled !== true) {
      sharingDisabled = true;
      return;
    }
    sharingDisabled = false;
    return {
      enabled: true,
      online: true,
      location: {
        lat: latitude,
        lng: longitude,
        last_location_update_time: updatedAtSeconds,
      },
    };
  }, undefined, false);
  if (!result.committed || sharingDisabled) {
    throw new HttpsError(
        "failed-precondition",
        "Location sharing is disabled",
    );
  }
  await getDatabase().ref().update({
    [`users/${uid}/hasOnline`]: true,
    [`locations/${uid}`]: null,
  });
  return {ok: true};
});

exports.removeLocation = onCall(OWNER_FUNCTION_OPTIONS, async (request) => {
  const {uid, ownerSecret} = readOwnerCredentials(request.data);
  await verifyOwner(uid, ownerSecret);
  const presenceRef = getDatabase().ref(`presence/${uid}`);
  const before = (await presenceRef.get()).val();
  await presenceRef.transaction((current) => {
    const state = current || before || {};
    return {enabled: state.enabled === true, online: false};
  }, undefined, false);
  await getDatabase().ref().update({
    [`users/${uid}/hasOnline`]: false,
    [`locations/${uid}`]: null,
  });
  return {ok: true};
});

exports.addFriend = onCall(OWNER_FUNCTION_OPTIONS, async (request) => {
  const {uid, ownerSecret} = readOwnerCredentials(request.data);
  const friendId = readUid(request.data?.friendId, "friendId");
  if (uid === friendId) {
    throw new HttpsError("invalid-argument", "Cannot add yourself");
  }
  await verifyOwner(uid, ownerSecret);

  const usersRef = getDatabase().ref("users");
  const before = (await usersRef.get()).val();
  if (!before?.[uid] || !before?.[friendId]) {
    throw new HttpsError("not-found", "Profile not found");
  }
  let failure = null;
  const result = await usersRef.transaction((currentUsers) => {
    // Admin RTDB can invoke the first local transaction pass with null. Seed
    // that pass from the verified read; the server revision check still forces
    // a retry if any concurrent write happened.
    const users = currentUsers || cloneJson(before);
    failure = null;
    if (!users?.[uid] || !users?.[friendId]) {
      failure = "not-found";
      return;
    }
    const ownFriends = normalizeFriendIds(users[uid].friendIds, uid);
    const targetFriends = normalizeFriendIds(users[friendId].friendIds, friendId);
    if (!ownFriends.includes(friendId) &&
        ownFriends.length >= MAX_FRIEND_COUNT) {
      failure = "owner-limit";
      return;
    }
    if (!targetFriends.includes(uid) &&
        targetFriends.length >= MAX_FRIEND_COUNT) {
      failure = "friend-limit";
      return;
    }
    users[uid].friendIds = [...new Set([...ownFriends, friendId])];
    users[friendId].friendIds = [...new Set([...targetFriends, uid])];
    delete users[uid].friends;
    delete users[friendId].friends;
    return users;
  }, undefined, false);

  if (!result.committed) throwFriendTransactionError(failure);
  return {ok: true, limit: MAX_FRIEND_COUNT};
});

exports.removeFriend = onCall(OWNER_FUNCTION_OPTIONS, async (request) => {
  const {uid, ownerSecret} = readOwnerCredentials(request.data);
  const friendId = readUid(request.data?.friendId, "friendId");
  await verifyOwner(uid, ownerSecret);

  const usersRef = getDatabase().ref("users");
  const before = (await usersRef.get()).val();
  if (!before?.[uid]) {
    throw new HttpsError("not-found", "Profile not found");
  }
  let failure = null;
  const result = await usersRef.transaction((currentUsers) => {
    const users = currentUsers || cloneJson(before);
    failure = null;
    if (!users?.[uid]) {
      failure = "not-found";
      return;
    }
    users[uid].friendIds = normalizeFriendIds(
        users[uid].friendIds,
        uid,
    ).filter((id) => id !== friendId);
    delete users[uid].friends;
    if (users[friendId]) {
      users[friendId].friendIds = normalizeFriendIds(
          users[friendId].friendIds,
          friendId,
      ).filter((id) => id !== uid);
      delete users[friendId].friends;
    }
    return users;
  }, undefined, false);

  if (!result.committed) throwFriendTransactionError(failure);
  return {ok: true};
});

exports.computeRoute = onCall(
    {
      region: "asia-southeast1",
      timeoutSeconds: 30,
      memory: "256MiB",
      minInstances: 0,
      maxInstances: 2,
      concurrency: 20,
      enforceAppCheck: true,
    },
    async (request) => {
      const origin = readCoordinate(request.data?.origin, "origin");
      const destination = readCoordinate(
          request.data?.destination,
          "destination",
      );
      const travelMode = String(request.data?.travelMode || "");
      if (!SUPPORTED_TRAVEL_MODES.has(travelMode)) {
        throw new HttpsError("invalid-argument", "Unsupported travel mode");
      }

      const body = {
        origin: waypoint(origin),
        destination: waypoint(destination),
        travelMode,
        // The app renders a route overview and follows the live device location locally.
        // OVERVIEW keeps long Famous routes small and significantly lowers latency.
        polylineQuality: "OVERVIEW",
        computeAlternativeRoutes: false,
        units: "METRIC",
      };
      if (travelMode === "DRIVE" || travelMode === "TWO_WHEELER") {
        body.routingPreference = "TRAFFIC_AWARE";
      }

      try {
        const accessToken = await auth.getAccessToken();
        if (!accessToken) {
          throw new Error("Could not obtain a service account access token");
        }
        const projectId = await auth.getProjectId();
        const response = await fetch(COMPUTE_ROUTES_URL, {
          method: "POST",
          headers: {
            "Authorization": `Bearer ${accessToken}`,
            "Content-Type": "application/json; charset=UTF-8",
            "X-Goog-FieldMask": RESPONSE_FIELD_MASK,
            "X-Goog-User-Project": projectId,
          },
          body: JSON.stringify(body),
        });
        const responseBody = await response.text();
        const parsed = safeJson(responseBody);
        if (!response.ok) {
          const backendStatus = parsed?.error?.status || "UNKNOWN";
          logger.error("Routes API request failed", {
            httpCode: response.status,
            backendStatus,
          });
          throw new HttpsError(
              mapBackendError(response.status, backendStatus),
              parsed?.error?.message || "Routes API request failed",
          );
        }

        const route = parsed?.routes?.[0];
        const encodedPolyline = route?.polyline?.encodedPolyline;
        if (!encodedPolyline) {
          throw new HttpsError("not-found", "No route was returned");
        }
        return {
          encodedPolyline,
          distanceMeters: Math.max(0, Number(route.distanceMeters) || 0),
          durationSeconds: parseDurationSeconds(route.duration),
        };
      } catch (error) {
        if (error instanceof HttpsError) throw error;
        logger.error("Route function failed", error);
        throw new HttpsError("internal", "Could not calculate this route");
      }
    },
);

function readCoordinate(value, fieldName) {
  const latitude = Number(value?.latitude);
  const longitude = Number(value?.longitude);
  const valid = Number.isFinite(latitude) &&
    Number.isFinite(longitude) &&
    latitude >= -90 && latitude <= 90 &&
    longitude >= -180 && longitude <= 180 &&
    !(latitude === 0 && longitude === 0);
  if (!valid) {
    throw new HttpsError("invalid-argument", `Invalid ${fieldName}`);
  }
  return {latitude, longitude};
}

function waypoint(point) {
  return {
    location: {
      latLng: {
        latitude: point.latitude,
        longitude: point.longitude,
      },
    },
  };
}

function parseDurationSeconds(value) {
  const seconds = Number(String(value || "").replace(/s$/, ""));
  return Number.isFinite(seconds) ? Math.max(0, Math.floor(seconds)) : 0;
}

function safeJson(value) {
  try {
    return JSON.parse(value);
  } catch (_) {
    return null;
  }
}

function mapBackendError(httpCode, backendStatus) {
  if (httpCode === 400 || backendStatus === "INVALID_ARGUMENT") {
    return "invalid-argument";
  }
  if (httpCode === 403 || backendStatus === "PERMISSION_DENIED") {
    return "permission-denied";
  }
  if (httpCode === 429 || backendStatus === "RESOURCE_EXHAUSTED") {
    return "resource-exhausted";
  }
  if (httpCode >= 500) return "unavailable";
  return "internal";
}

function readOwnerCredentials(data) {
  const uid = readUid(data?.uid, "uid");
  const ownerSecret = String(data?.ownerSecret || "");
  if (ownerSecret.length < 32 || ownerSecret.length > 256 ||
      !/^[A-Za-z0-9_-]+$/.test(ownerSecret)) {
    throw new HttpsError("invalid-argument", "Invalid owner proof");
  }
  return {uid, ownerSecret};
}

function readUid(value, fieldName) {
  const uid = String(value || "").trim();
  if (!uid || uid.length > 128 || /[.#$\[\]/\u0000-\u001F\u007F]/.test(uid)) {
    throw new HttpsError("invalid-argument", `Invalid ${fieldName}`);
  }
  return uid;
}

function readProfile(value, uid) {
  const name = String(value?.name || "").trim();
  const phone = normalizePhone(value?.phone, true);
  const avt = String(value?.avt || "").trim();
  const avatarKey = String(value?.avatarKey || "").trim();
  const secondaryPhones = readSecondaryPhones(value?.secondaryPhones);
  if (!name || name.length > 100 || phone.length > 32 ||
      avt.length > 2048 || avatarKey.length > 64) {
    throw new HttpsError("invalid-argument", "Invalid profile");
  }
  return {uid, name, phone, avt, avatarKey, secondaryPhones};
}

function readSecondaryPhones(value) {
  if (value == null) return {};
  if (typeof value !== "object" || Array.isArray(value)) {
    throw new HttpsError("invalid-argument", "Invalid secondary phones");
  }
  const entries = Object.entries(value);
  if (entries.length > MAX_SECONDARY_PHONE_COUNT) {
    throw new HttpsError("invalid-argument", "Too many secondary phones");
  }
  const result = {};
  for (const [key, rawPhone] of entries) {
    const safeKey = readUid(key, "secondary phone key");
    result[safeKey] = normalizePhone(rawPhone);
  }
  return result;
}

function normalizePhone(value, allowEmpty = false) {
  let phone = String(value || "").trim().replace(/[\s()-]/g, "");
  if (allowEmpty && !phone) return "";
  // Pre-E.164 builds only accepted Vietnamese national numbers.
  if (/^00[1-9][0-9]+$/.test(phone)) phone = `+${phone.slice(2)}`;
  if (/^0[0-9]+$/.test(phone)) phone = `+84${phone.slice(1)}`;
  if (phone.length < 7 || phone.length > 20 || !/^\+?[0-9]+$/.test(phone)) {
    throw new HttpsError("invalid-argument", "Invalid phone number");
  }
  if (!phone.startsWith("+")) {
    throw new HttpsError("invalid-argument", "Phone number must include country code");
  }
  return phone;
}

function normalizeExistingPhone(value) {
  try {
    return normalizePhone(value, true);
  } catch (_) {
    return "";
  }
}

function profileIndexedPhones(profile) {
  const phones = new Set();
  const primary = normalizeExistingPhone(profile?.phone);
  if (primary) phones.add(primary);
  for (const value of Object.values(profile?.secondaryPhones || {})) {
    const secondary = normalizeExistingPhone(value);
    if (secondary) phones.add(secondary);
  }
  return phones;
}

function idCandidates(uid) {
  return [...new Set([uid, uid.toUpperCase(), uid.toLowerCase()])];
}

function hiddenPresence() {
  return {enabled: false, online: false, location: null};
}

function publicProfile(uid, profile, presence) {
  return {
    uid,
    name: String(profile.name || ""),
    phone: normalizeExistingPhone(profile.phone),
    avatarUrl: String(profile.avt || ""),
    avatarKey: String(profile.avatarKey || ""),
    hasOnline: presence.online === true,
    trackingAvailable: presence.enabled === true,
  };
}

async function readPresence(uid, profile) {
  const stored = (await getDatabase().ref(`presence/${uid}`).get()).val();
  if (stored && typeof stored.enabled === "boolean") {
    return {
      enabled: stored.enabled === true,
      online: stored.online === true,
      location: stored.location || null,
    };
  }

  // One-time compatibility for profiles created before presence was added.
  const legacyLocation = (
    await getDatabase().ref(`locations/${uid}`).get()
  ).val();
  return {
    enabled: profile?.trackingAvailable === true,
    online: profile?.hasOnline === true,
    location: legacyLocation,
  };
}

function visibleLocation(presence) {
  if (presence.enabled !== true || presence.online !== true) return null;
  const storedLocation = presence.location;
  const updatedAtSeconds = Number(
      storedLocation?.last_location_update_time,
  );
  const ageSeconds = Math.floor(Date.now() / 1000) - updatedAtSeconds;
  const latitude = Number(storedLocation?.lat);
  const longitude = Number(storedLocation?.lng);
  const isValid = Number.isFinite(latitude) &&
    latitude >= -90 && latitude <= 90 &&
    Number.isFinite(longitude) &&
    longitude >= -180 && longitude <= 180 &&
    !(latitude === 0 && longitude === 0) &&
    Number.isFinite(ageSeconds) && ageSeconds >= 0 &&
    ageSeconds <= FRIEND_LOCATION_MAX_AGE_SECONDS;
  if (!isValid) return null;
  return {
    latitude,
    longitude,
    updatedAt: updatedAtSeconds * 1000,
  };
}

function normalizeFriendIds(value, ownUid) {
  const raw = Array.isArray(value) ? value : Object.values(value || {});
  return [...new Set(raw
      .filter((item) => typeof item === "string")
      .map((item) => item.trim())
      .filter((item) => item && item !== ownUid))]
      .slice(0, MAX_FRIEND_COUNT);
}

function cloneJson(value) {
  return JSON.parse(JSON.stringify(value));
}

async function verifyOwner(uid, ownerSecret, allowClaim = false) {
  const digest = crypto.createHash("sha256").update(ownerSecret).digest("hex");
  const ref = getDatabase().ref(`owners/${uid}/secretHash`);
  let stored = (await ref.get()).val();
  if (!stored && allowClaim) {
    const result = await ref.transaction(
        (current) => current || digest,
        undefined,
        false,
    );
    stored = result.snapshot.val();
  }
  if (!stored) {
    throw new HttpsError(
        "failed-precondition",
        "Profile ownership has not been registered",
    );
  }
  const storedBuffer = Buffer.from(String(stored));
  const digestBuffer = Buffer.from(digest);
  if (storedBuffer.length !== digestBuffer.length ||
      !crypto.timingSafeEqual(storedBuffer, digestBuffer)) {
    throw new HttpsError("permission-denied", "Invalid owner proof");
  }
}

async function requireProfile(uid) {
  const profile = (await getDatabase().ref(`users/${uid}`).get()).val();
  if (!profile?.userId) {
    throw new HttpsError("failed-precondition", "Profile is not set up");
  }
  return profile;
}

function throwFriendTransactionError(reason) {
  if (reason === "not-found") {
    throw new HttpsError("not-found", "Profile not found");
  }
  if (reason === "owner-limit" || reason === "friend-limit") {
    throw new HttpsError(
        "resource-exhausted",
        "Friend limit reached",
        {code: "FRIEND_LIMIT_REACHED", limit: MAX_FRIEND_COUNT},
    );
  }
  throw new HttpsError("aborted", "Friend update was not committed");
}
