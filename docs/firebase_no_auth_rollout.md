# Firebase rollout: no-login device identity

The app intentionally does not create a Firebase Authentication session. Each
installation uses its stable installation/device ID to create a profile and to
share a location with friends. A random 256-bit `ownerSecret` is generated in
private app storage; it is not part of the friend code or QR payload.

The Realtime Database wire format follows the reference app: profiles keep
`friendIds`, `hasOnline`, `trackingAvailable`, and optional `secondaryPhones`;
locations use `lat`, `lng`, and `last_location_update_time` in epoch seconds.
Primary phone numbers are indexed below `phoneToUidMap/{phone}/{uid}` by the
backend so the existing Phone Locator flow can find registered app users.
Backend reads retain a compatibility fallback for the previous location node
until installed builds write their first `presence/{uid}` state.
The MVP has a fixed limit of five friends per profile. Both the repository
logic and Cloud Functions enforce the same limit. A later IAP release
must replace this fixed value with server-owned entitlements rather than a
client-writable Premium flag.

## Security model

- Realtime Database and Storage reject every direct client write. Profile,
  availability, location and friend mutations go through App Check protected
  callable Cloud Functions.
- Cloud Functions hash `ownerSecret` with SHA-256 and store only that hash below
  the unreadable `owners/{uid}` path. Each mutation verifies the proof before
  using the Admin SDK. A client also refuses to call a mutation for a UID other
  than this installation's random UUID.
- Firebase App Check is installed with the debug provider for debug builds and
  Play Integrity for release builds.
- App Check verifies that traffic comes from an accepted app installation;
  `ownerSecret` supplies the per-profile ownership proof without a login UI or
  Firebase Authentication session.
- Realtime Database denies every direct client read. The app polls an
  owner-verified callable snapshot while it is foregrounded. Phone lookup may
  return a matching profile, but exact location is returned only for a mutual
  friend who enabled sharing, is foreground-active, and updated within one
  minute.
- Sharing state and location live together under `presence/{uid}`. Per-UID
  transactions make disabling sharing win safely over an in-flight update.
- Clearing app data or uninstalling removes the secret. Without a login/recovery
  flow, that installation cannot reclaim a profile that has already registered
  another secret. This is an intentional limitation of the no-account model.
- The MVP intentionally has no delete-profile Settings action or callable
  endpoint, matching the reference app. Clearing data or reinstalling creates a
  new installation identity; the previous remote profile becomes inaccessible
  from that device and may remain as stale backend data.
- A profile that already exists without an `owners/{uid}` hash cannot be claimed
  from the app. Before the initial pre-store rollout, remove test-only legacy
  profiles or migrate them through a trusted admin process; never enable a
  public "claim existing profile" path.

## Production setup

1. Clear disposable pre-release `users`/`locations` test records, or migrate
   them with a trusted admin process. A profile that already exists without an
   `owners/{uid}` hash is deliberately not claimable by the app.
2. Register the Android app in Firebase App Check with Play Integrity.
3. Add the release signing certificate SHA-256 fingerprint to the Firebase
   Android app configuration.
4. For local/debug testing, run the app once, copy its App Check debug token
   from Logcat, and register that token in Firebase Console.
5. Deploy Cloud Functions first, then the rules that block legacy direct writes:

   ```shell
   firebase deploy --only functions
   firebase deploy --only database,storage
   ```

6. Verify profile creation plus the `computeRoute` callable function succeed
   from a release build.
7. Enable App Check enforcement for Realtime Database and Cloud Functions. The
   callable functions already request enforcement, and unsupported older builds
   cannot write after the new Database Rules are deployed.
8. Configure release signing in ignored `secrets.properties` or CI environment:
   `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and
   `RELEASE_KEY_PASSWORD`. Never commit the keystore or these values.

No production deployment or console enforcement is performed automatically by
the repository changes.

## Two-device acceptance test

- Install a clean build on two physical devices and confirm they receive
  different profile IDs.
- Create/edit each profile and verify another device cannot update that UID
  with a different owner secret.
- Find a registered user by the normalized phone number; change the number and
  verify the old index stops resolving.
- Add each device by ID and QR; verify both friend lists are synchronized.
- Verify adding a sixth friend is rejected with the existing limit dialog.
- Search by phone before connecting and verify the profile is found without an
  exact location; connect both profiles and verify the fresh location appears.
- Clear app data on a test device and verify the old profile cannot be edited
  without its former owner secret.
- Share location on both devices and verify markers update while both apps are
  visible. Background one app and verify its marker becomes unavailable.
- Disable location sharing and verify the device location is removed remotely.
- Turn device Location off/on and verify `hasOnline` and the friend status follow it.
- Reboot a device and verify the app does not auto-start a location service or
  boot rescheduler.
- Create an enter/exit zone and verify alerts while the app is visible.
- Disable in-app notifications and verify zone alerts are suppressed.
- Re-enable notifications and test denied/revoked Android location and
  notification permissions without a crash.
- Verify route calculation succeeds with App Check and fails for a request
  without a valid App Check token.

## Local checks

```shell
./gradlew :app:testProductionDebugUnitTest :app:lintProductionDebug
./scripts/profile_lifecycle_regression_test.sh
./scripts/firebase_rules_smoke_test.sh
(cd functions && npm run check)
```
