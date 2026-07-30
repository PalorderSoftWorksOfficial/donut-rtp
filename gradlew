#!/usr/bin/env sh

set -eu

APP_HOME="$(cd "$(dirname "$0")" && pwd -P)"
PROP_FILE="$APP_HOME/gradle/wrapper/gradle-wrapper.properties"
DIST_URL="$(sed -n 's/^distributionUrl=//p' "$PROP_FILE")"
DIST_NAME="$(basename "$DIST_URL")"
DIST_VERSION="$(printf '%s' "$DIST_NAME" | sed -E 's/^gradle-(.*)-bin\.zip$/\1/')"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
CACHE_DIR="$GRADLE_USER_HOME/wrapper/dists/gradle-${DIST_VERSION}-bin"
INSTALL_DIR="$CACHE_DIR/gradle-${DIST_VERSION}"
ZIP_PATH="$CACHE_DIR/$DIST_NAME"
GRADLE_BIN="$INSTALL_DIR/bin/gradle"

if [ ! -x "$GRADLE_BIN" ]; then
  mkdir -p "$CACHE_DIR"
  if [ ! -f "$ZIP_PATH" ]; then
    if command -v curl >/dev/null 2>&1; then
      curl -fsSL "$DIST_URL" -o "$ZIP_PATH"
    elif command -v wget >/dev/null 2>&1; then
      wget -q "$DIST_URL" -O "$ZIP_PATH"
    else
      echo "curl or wget is required to download Gradle." >&2
      exit 1
    fi
  fi
  if command -v unzip >/dev/null 2>&1; then
    unzip -q -o "$ZIP_PATH" -d "$CACHE_DIR"
  else
    echo "unzip is required to extract Gradle." >&2
    exit 1
  fi
fi

exec "$GRADLE_BIN" "$@"
