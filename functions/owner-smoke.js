"use strict";

const assert = require("node:assert/strict");

if (!process.env.FIREBASE_DATABASE_EMULATOR_HOST) {
  throw new Error("Refusing to run ownership smoke test outside the emulator");
}

const backend = require("./index.js");
const {getDatabase} = require("firebase-admin/database");

// The reference-app-compatible MVP intentionally has no profile deletion API.
assert.equal(backend.deleteProfile, undefined);
assert.equal(backend.deleteAccount, undefined);

const secret = (letter) => letter.repeat(43);
const request = (uid, ownerSecret, values = {}) => ({
  app: {appId: "ownership-smoke-test"},
  data: {uid, ownerSecret, ...values},
});
const profile = (name, phone = "", secondaryPhones = {}) => ({
  name,
  phone,
  avt: "",
  avatarKey: "avatar_1",
  secondaryPhones,
});

async function expectCode(block, code) {
  let failure;
  try {
    await block();
  } catch (error) {
    failure = error;
  }
  assert.equal(failure?.code, code);
}

async function main() {
  await getDatabase().ref().remove();

  await getDatabase().ref("users/LEGACY").set({
    userId: "LEGACY",
    name: "Legacy",
    phone: "",
    avt: "",
  });
  await expectCode(
      () => backend.upsertProfile.run(
          request("LEGACY", secret("L"), {profile: profile("Claimed")}),
      ),
      "failed-precondition",
  );

  for (const uid of ["A", "B", "C", "D", "E", "F", "G"]) {
    const phone = uid === "A" ? "0912345678" :
      uid === "B" ? "0987654321" : "";
    await backend.upsertProfile.run(
        request(uid, secret(uid), {profile: profile(`User ${uid}`, phone)}),
    );
  }

  await expectCode(
      () => backend.upsertProfile.run(
          request("A", secret("X"), {profile: profile("Hijacked")}),
      ),
      "permission-denied",
  );

  await backend.setLocationSharing.run(
      request("A", secret("A"), {enabled: true}),
  );
  await backend.updateLocation.run(request("A", secret("A"), {
    latitude: 10.7769,
    longitude: 106.7009,
  }));
  assert.equal(
      (await getDatabase().ref("users/A/hasOnline").get()).val(),
      true,
  );
  await expectCode(
      () => backend.updateLocation.run(request("A", secret("X"), {
        latitude: 1,
        longitude: 1,
      })),
      "permission-denied",
  );

  const phoneResult = await backend.findUserByPhone.run(
      request("B", secret("B"), {phone: "0912345678"}),
  );
  assert.equal(phoneResult.matches[0].profile.uid, "A");
  assert.equal(phoneResult.matches[0].isFriend, false);
  assert.equal(phoneResult.matches[0].location, null);

  await backend.addFriend.run(
      request("B", secret("B"), {friendId: "A"}),
  );
  const friendPhoneResult = await backend.findUserByPhone.run(
      request("B", secret("B"), {phone: "0912345678"}),
  );
  assert.equal(friendPhoneResult.matches[0].isFriend, true);
  assert.equal(friendPhoneResult.matches[0].location.latitude, 10.7769);
  const friendSnapshot = await backend.getFriendsSnapshot.run(
      request("B", secret("B")),
  );
  assert.equal(friendSnapshot.friends[0].profile.uid, "A");
  assert.equal(friendSnapshot.friends[0].location.longitude, 106.7009);
  const ownProfile = await backend.getOwnProfile.run(
      request("B", secret("B")),
  );
  assert.equal(ownProfile.profile.friendIds.includes("A"), true);
  const profileById = await backend.findUserById.run(
      request("B", secret("B"), {friendId: "a"}),
  );
  assert.equal(profileById.profile.uid, "A");
  await backend.updateAvailability.run(
      request("A", secret("A"), {hasOnline: false}),
  );
  const backgroundResult = await backend.findUserByPhone.run(
      request("B", secret("B"), {phone: "0912345678"}),
  );
  assert.equal(backgroundResult.matches[0].location, null);
  await backend.updateLocation.run(request("A", secret("A"), {
    latitude: 10.7769,
    longitude: 106.7009,
  }));
  await expectCode(
      () => backend.findUserByPhone.run(
          request("B", secret("X"), {phone: "0912345678"}),
      ),
      "permission-denied",
  );
  await expectCode(
      () => backend.findUserByPhone.run(
          request("B", secret("B"), {phone: "0900000000"}),
      ),
      "not-found",
  );

  await backend.upsertProfile.run(
      request("A", secret("A"), {
        profile: profile("User A", "0901234567"),
      }),
  );
  await expectCode(
      () => backend.findUserByPhone.run(
          request("B", secret("B"), {phone: "0912345678"}),
      ),
      "not-found",
  );
  const updatedPhoneResult = await backend.findUserByPhone.run(
      request("B", secret("B"), {phone: "0901234567"}),
  );
  assert.equal(updatedPhoneResult.matches[0].profile.uid, "A");

  await backend.upsertProfile.run(
      request("C", secret("C"), {
        profile: profile("User C", "0901234567"),
      }),
  );
  const duplicatePhoneResult = await backend.findUserByPhone.run(
      request("B", secret("B"), {phone: "+84901234567"}),
  );
  assert.deepEqual(
      duplicatePhoneResult.matches.map((match) => match.profile.uid),
      ["A", "C"],
  );

  await backend.upsertProfile.run(
      request("C", secret("C"), {
        profile: profile("User C", "", {C: "+14155552671"}),
      }),
  );
  const secondaryPhoneResult = await backend.findUserByPhone.run(
      request("B", secret("B"), {phone: "+14155552671"}),
  );
  assert.equal(secondaryPhoneResult.matches[0].profile.uid, "C");

  await backend.setLocationSharing.run(
      request("A", secret("A"), {enabled: false}),
  );
  assert.equal(
      (await getDatabase().ref("users/A/trackingAvailable").get()).val(),
      false,
  );
  assert.equal((await getDatabase().ref("locations/A").get()).exists(), false);
  assert.equal(
      (await getDatabase().ref("presence/A/enabled").get()).val(),
      false,
  );
  await expectCode(
      () => backend.updateLocation.run(request("A", secret("A"), {
        latitude: 10.7769,
        longitude: 106.7009,
      })),
      "failed-precondition",
  );

  const seededUsers = (await getDatabase().ref("users").get()).val();
  for (const uid of ["A", "B", "C", "D", "E", "F", "G"]) {
    assert.equal(seededUsers?.[uid]?.userId, uid);
  }

  for (const friendId of ["B", "C", "D", "E", "F"]) {
    await backend.addFriend.run(
        request("A", secret("A"), {friendId}),
    );
  }
  await expectCode(
      () => backend.addFriend.run(
          request("A", secret("A"), {friendId: "G"}),
      ),
      "resource-exhausted",
  );

  await expectCode(
      () => backend.removeFriend.run(
          request("A", secret("X"), {friendId: "B"}),
      ),
      "permission-denied",
  );
  const beforeOwnerRemoval =
    (await getDatabase().ref("users/A/friendIds").get()).val();
  assert.equal(Object.values(beforeOwnerRemoval || {}).includes("B"), true);

  await backend.removeFriend.run(
      request("A", secret("A"), {friendId: "B"}),
  );
  const users = (await getDatabase().ref("users").get()).val();
  assert.equal(Object.values(users.A.friendIds || {}).includes("B"), false);
  assert.equal(Object.values(users.B.friendIds || {}).includes("A"), false);
  assert.equal(Object.values(users.A.friendIds || {}).length, 4);
  assert.equal(users.A.name, "User A");
  assert.equal(users.B.name, "User B");
  assert.equal(
      (await getDatabase().ref("owners/A/secretHash").get()).exists(),
      true,
  );

  console.log("Cloud Functions ownership smoke test passed");
}

main()
    .then(() => process.exit(0))
    .catch((error) => {
      console.error(error);
      process.exit(1);
    });
