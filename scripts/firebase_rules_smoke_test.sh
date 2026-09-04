#!/usr/bin/env bash
set -euo pipefail

database_host="${FIREBASE_DATABASE_EMULATOR_HOST:-127.0.0.1:9000}"
database_namespace="${FIREBASE_DATABASE_NAMESPACE:-gps-location-phone-default-rtdb}"
response_file="$(mktemp)"
trap 'rm -f "${response_file}"' EXIT

request() {
  local method="$1"
  local path="$2"
  local body="$3"
  local expected_status="$4"
  local status
  status="$(curl --silent --show-error --output "${response_file}" --write-out '%{http_code}' \
    --request "${method}" \
    --header 'Content-Type: application/json' \
    --data "${body}" \
    "http://${database_host}/${path}.json?ns=${database_namespace}")"
  if [[ "${status}" != "${expected_status}" ]]; then
    echo "Expected HTTP ${expected_status}, got ${status} for ${method} ${path}" >&2
    sed -n '1,40p' "${response_file}" >&2
    exit 1
  fi
}

admin_request() {
  local method="$1"
  local path="$2"
  local body="$3"
  local status
  status="$(curl --silent --show-error --output "${response_file}" --write-out '%{http_code}' \
    --request "${method}" \
    --header 'Authorization: Bearer owner' \
    --header 'Content-Type: application/json' \
    --data "${body}" \
    "http://${database_host}/${path}.json?ns=${database_namespace}")"
  if [[ "${status}" != "200" ]]; then
    echo "Admin seed failed with HTTP ${status} for ${method} ${path}" >&2
    exit 1
  fi
}

admin_request PUT users/DEVICE_A \
  '{"userId":"DEVICE_A","name":"Alice","phone":"0912345678","avt":"","avatarKey":"avatar_1","friendIds":["DEVICE_B"],"hasOnline":true,"trackingAvailable":true}'
admin_request PUT users/DEVICE_B \
  '{"userId":"DEVICE_B","name":"Bob","phone":"0987654321","avt":"","avatarKey":"avatar_2","friendIds":["DEVICE_A"],"hasOnline":false,"trackingAvailable":true}'
admin_request PUT locations/DEVICE_A \
  '{"lat":10.7769,"lng":106.7009,"last_location_update_time":1700000000}'
admin_request PUT owners/DEVICE_A '{"secretHash":"private"}'

# All client-side mutations are denied. Only Admin SDK Cloud Functions write.
request PUT users/DEVICE_A \
  '{"userId":"DEVICE_A","name":"Mallory","phone":"","avt":""}' 401
request PUT users/DEVICE_A/name '"Mallory"' 401
request PUT users/DEVICE_A/friendIds '["DEVICE_B"]' 401
request PUT users/DEVICE_B/friendIds '["DEVICE_A"]' 401
request PUT locations/DEVICE_A \
  '{"lat":10.7769,"lng":106.7009,"last_location_update_time":1700000000}' 401
request DELETE locations/DEVICE_A '' 401
request PUT phoneToUidMap/0912345678/DEVICE_A \
  '{"type":"primary","addedBy":"DEVICE_A","addedTime":1700000000000}' 401
request GET owners/DEVICE_A '' 401
request PUT owners/DEVICE_A '{"secretHash":"stolen"}' 401
request PUT presence/DEVICE_A \
  '{"enabled":true,"online":true,"location":{"lat":10.7769,"lng":106.7009,"last_location_update_time":1700000000}}' 401

# All client reads are denied. Owner-verified Cloud Functions return only the
# caller's profile, friends, and visible friend locations.
request GET users/DEVICE_A '' 401
request GET users '' 401
request GET locations/DEVICE_A '' 401
request GET locations '' 401
request GET presence/DEVICE_A '' 401
request GET phoneToUidMap/0912345678 '' 401
request GET phoneToUidMap '' 401

echo "Firebase Realtime Database rules smoke test passed"
