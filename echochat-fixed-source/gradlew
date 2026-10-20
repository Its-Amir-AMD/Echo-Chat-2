#!/usr/bin/env sh
# Lightweight Gradle bootstrap for environments where the standard wrapper JAR
# is unavailable. Keep this version in sync with gradle/wrapper/gradle-wrapper.properties.
set -eu

GRADLE_VERSION="9.3.1"
GRADLE_ROOT="${GRADLE_USER_HOME:-$HOME/.gradle}/manual-distributions"
INSTALL_DIR="$GRADLE_ROOT/gradle-$GRADLE_VERSION"
GRADLE_BIN="$INSTALL_DIR/gradle-$GRADLE_VERSION/bin/gradle"
ZIP_FILE="$GRADLE_ROOT/gradle-$GRADLE_VERSION-bin.zip"

if [ ! -x "$GRADLE_BIN" ]; then
    mkdir -p "$GRADLE_ROOT"
    if ! command -v curl >/dev/null 2>&1; then
        echo "Error: curl is required to download Gradle." >&2
        exit 1
    fi
    if ! command -v unzip >/dev/null 2>&1; then
        echo "Error: unzip is required to install Gradle." >&2
        exit 1
    fi
    echo "Downloading Gradle $GRADLE_VERSION..."
    curl --fail --location --retry 3 --retry-delay 2 \
        "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" \
        --output "$ZIP_FILE"
    unzip -q "$ZIP_FILE" -d "$INSTALL_DIR"
    rm -f "$ZIP_FILE"
fi

exec "$GRADLE_BIN" "$@"
