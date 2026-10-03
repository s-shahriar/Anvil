#!/usr/bin/env bash
# Builds the signed APK for the version in app/build.gradle.kts and publishes it as a GitHub release.
#   tools/release.sh            build + checksum only (prints the publish command)
#   tools/release.sh --publish  also create the release on s-shahriar/Anvil (repo must be public: the in-app updater reads it)
set -euo pipefail
cd "$(dirname "$0")/.."
V=$(grep -oP 'versionName = "\K[^"]+' app/build.gradle.kts)
TAG="v$V"; APK="Anvil-$TAG.apk"
./gradlew :app:assembleRelease --console=plain -q
cp app/build/outputs/apk/release/app-release.apk "$APK"
sha256sum "$APK" > "$APK.sha256"
AS=$(ls ~/Android/Sdk/build-tools/*/apksigner | tail -1); "$AS" verify "$APK" && echo "signature ok"
echo "built $APK ($(du -h "$APK" | cut -f1))"; cat "$APK.sha256"
if [ "${1:-}" = "--publish" ]; then
  gh release create "$TAG" --repo s-shahriar/Anvil --title "Anvil $TAG" --notes-file "RELEASE_NOTES.md" "$APK" "$APK.sha256"
else
  echo "to publish: tools/release.sh --publish"
fi
