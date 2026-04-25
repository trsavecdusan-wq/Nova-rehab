#!/usr/bin/env sh
# Self-contained Gradle launcher for GitHub Actions and local Linux/macOS.
# It intentionally does not require gradle-wrapper.jar. The launcher downloads
# the configured Gradle distribution into ~/.gradle/rehab-wrapper and runs it.
set -eu

GRADLE_VERSION="8.1.1"
DIST_NAME="gradle-${GRADLE_VERSION}-bin"
BASE_DIR="${GRADLE_USER_HOME:-$HOME/.gradle}/rehab-wrapper"
DIST_DIR="$BASE_DIR/${DIST_NAME}"
ZIP_FILE="$BASE_DIR/${DIST_NAME}.zip"
URL="https://services.gradle.org/distributions/${DIST_NAME}.zip"

mkdir -p "$BASE_DIR"

if [ ! -x "$DIST_DIR/bin/gradle" ]; then
  echo "Gradle $GRADLE_VERSION not found. Downloading..."
  if command -v curl >/dev/null 2>&1; then
    curl -L --retry 3 --connect-timeout 20 -o "$ZIP_FILE" "$URL"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$ZIP_FILE" "$URL"
  else
    echo "ERROR: curl or wget is required to download Gradle." >&2
    exit 1
  fi
  rm -rf "$DIST_DIR"
  unzip -q "$ZIP_FILE" -d "$BASE_DIR"
fi

exec "$DIST_DIR/bin/gradle" "$@"
