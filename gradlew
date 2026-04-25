#!/usr/bin/env sh
# Self-contained Gradle launcher for GitHub Actions and local Linux/macOS.
# Downloads Gradle distribution and runs the real gradle executable.
set -eu

GRADLE_VERSION="8.1.1"
DIST_ZIP_NAME="gradle-${GRADLE_VERSION}-bin"
DIST_DIR_NAME="gradle-${GRADLE_VERSION}"
BASE_DIR="${GRADLE_USER_HOME:-$HOME/.gradle}/rehab-wrapper"
DIST_DIR="$BASE_DIR/${DIST_DIR_NAME}"
ZIP_FILE="$BASE_DIR/${DIST_ZIP_NAME}.zip"
URL="https://services.gradle.org/distributions/${DIST_ZIP_NAME}.zip"

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

if [ ! -x "$DIST_DIR/bin/gradle" ]; then
  echo "ERROR: Gradle executable not found after extraction: $DIST_DIR/bin/gradle" >&2
  echo "Contents of $BASE_DIR:" >&2
  ls -la "$BASE_DIR" >&2 || true
  exit 127
fi

exec "$DIST_DIR/bin/gradle" "$@"
