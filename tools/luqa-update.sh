#!/usr/bin/env bash
# Explicit Luqa-only updater. No cron, unattended ADB or automatic TV deployment.
set -euo pipefail
cd "$(dirname "$0")/.."

: "${LUQA_TV_HOST:?Set LUQA_TV_HOST to the Luqa TV IP or IP:port}"
export TV_HOST="$LUQA_TV_HOST"
PY=tools/venv/bin/python
[ -x "$PY" ] || PY=tools/venv/Scripts/python.exe
[ -x "$PY" ] || { echo "Install tools requirements into tools/venv first (see README)." >&2; exit 2; }
tv() { "$PY" -I tools/tv.py "$@"; }
device_id="$(tv 'settings get secure android_id' | tr -d '\r\n[:space:]')"
model="$(tv 'getprop ro.product.model' | tr -d '\r\n')"
echo "Connected TV: ${model:-unknown}, Android ID: ${device_id:-unavailable}"
if [ "${1:-}" = "--identify" ]; then
  echo "Compare this Android ID with the Luqa box, then set LUQA_ANDROID_ID locally. Never commit it."
  exit 0
fi
[ "$#" -eq 0 ] || { echo "Usage: tools/luqa-update.sh [--identify]" >&2; exit 2; }
: "${LUQA_ANDROID_ID:?Set the verified Android ID of the Luqa box (use --identify first)}"
if [ -z "$device_id" ] || [ "$device_id" = "null" ] || [ "$device_id" != "$LUQA_ANDROID_ID" ]; then
  echo "REFUSED: This is not the registered Luqa device. Nothing installed." >&2
  exit 2
fi
[ "${LUQA_CONFIRM:-}" = "INSTALL_LUQA" ] || {
  echo "Refused: set LUQA_CONFIRM=INSTALL_LUQA to approve this particular deployment." >&2
  exit 2
}
# Upstream sync is handled by the fork's own workflow, never merged directly into a TV.
# Refuse local changes or an unexpected branch, and allow only a fast-forward from the tested fork main.
[ "$(git branch --show-current)" = main ] || { echo "Checkout main first." >&2; exit 2; }
[ -z "$(git status --porcelain)" ] || { echo "Working tree has changes. Refusing update." >&2; exit 2; }
git fetch origin main
git merge --ff-only origin/main
echo "Deploying fork main $(git rev-parse --short HEAD) to verified Luqa box."
echo "Release APKs use the local Gradle debug signing key. Keep ~/.android/debug.keystore stable"
echo "across updates or Android may reject a replacement due to a different signature."
(
  cd launcher
  ./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease
)
(
  cd addons/gozo-football
  ./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease
)
install_apk() {
  local source="$1" remote="$2" expected_pkg="$3" answer
  [ -f "$source" ] || { echo "APK not produced: $source" >&2; exit 1; }
  "$PY" -I tools/b64push.py "$source" "$remote"
  answer="$(tv "pm install -r $remote")"
  echo "$expected_pkg: $answer"
  [[ "$answer" == *Success* ]] || { echo "Installation failed; not applying theme." >&2; exit 1; }
  tv "rm -f $remote" >/dev/null
  tv "pm path $expected_pkg" | grep -q 'package:' || {
    echo "Package verification failed for $expected_pkg" >&2; exit 1;
  }
}
# Do not disable or remove the original Android launcher; use existing Home configuration.
install_apk launcher/app/build/outputs/apk/release/app-release.apk /data/local/tmp/luqa-home.apk com.gathin.tvhome
install_apk addons/gozo-football/app/build/outputs/apk/release/app-release.apk /data/local/tmp/luqa-football.apk com.wiggli.gozofootball
TV_HOST="$LUQA_TV_HOST" tools/theme.sh themes/family-gozo
echo "Luqa apps installed and theme applied. Review Home, Weather and Football with the remote."
echo "If Football asks whether to show its module card, select Show."
