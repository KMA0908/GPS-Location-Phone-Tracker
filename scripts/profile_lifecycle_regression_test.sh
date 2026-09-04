#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_dir="$(cd "${script_dir}/.." && pwd)"

settings_sources=(
  "${project_dir}/app/src/main/java/com/nhn/gps/location/phone/tracker/ui/settings"
  "${project_dir}/app/src/main/res/layout/fragment_settings.xml"
  "${project_dir}/app/src/main/res/values/strings.xml"
)
backend_source="${project_dir}/functions/index.js"
manifest="${project_dir}/app/src/main/AndroidManifest.xml"
gradle_sources=(
  "${project_dir}/app/build.gradle.kts"
  "${project_dir}/gradle/libs.versions.toml"
)

if rg --ignore-case --quiet \
  'delete.{0,16}(profile|account)|remove.{0,16}(profile|account)|delete_(profile|account)|xóa.{0,16}hồ sơ' \
  "${settings_sources[@]}"; then
  echo "Unexpected delete-profile UI found in the MVP Settings flow" >&2
  exit 1
fi

if rg --quiet '^exports\.(deleteProfile|deleteAccount)\s*=' "${backend_source}"; then
  echo "Unexpected delete-profile callable found in the MVP backend" >&2
  exit 1
fi

if ! rg --quiet 'android:allowBackup="false"' "${manifest}"; then
  echo "App backup must stay disabled so reinstall creates a new installation identity" >&2
  exit 1
fi

if rg --ignore-case --quiet \
  'firebase-auth|FirebaseAuth|signInAnonymously' \
  "${gradle_sources[@]}" "${project_dir}/app/src/main"; then
  echo "Firebase Authentication must not be added to the no-login MVP" >&2
  exit 1
fi

echo "Profile lifecycle regression test passed"
