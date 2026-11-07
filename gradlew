#!/bin/sh
#
# Minimal Gradle wrapper launcher for ByteCode.
#
# The binary gradle-wrapper.jar is intentionally not stored in this repository.
# On first use it is downloaded from the official Gradle repository (the tag that
# matches the version in gradle/wrapper/gradle-wrapper.properties), then the
# regular wrapper entry point is started.
#
set -eu

APP_HOME=$(cd "$(dirname "$0")" && pwd -P)
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
PROPERTIES="$APP_HOME/gradle/wrapper/gradle-wrapper.properties"

if [ ! -f "$WRAPPER_JAR" ]; then
    GRADLE_VERSION=$(sed -n 's#^distributionUrl=.*gradle-\([0-9][0-9.]*\)-.*\.zip$#\1#p' "$PROPERTIES")
    if [ -z "$GRADLE_VERSION" ]; then
        echo "Could not read the Gradle version from $PROPERTIES" >&2
        exit 1
    fi
    URL="https://raw.githubusercontent.com/gradle/gradle/v${GRADLE_VERSION}/gradle/wrapper/gradle-wrapper.jar"
    echo "Downloading gradle-wrapper.jar for Gradle ${GRADLE_VERSION}..." >&2
    mkdir -p "$(dirname "$WRAPPER_JAR")"
    if command -v curl >/dev/null 2>&1; then
        curl -fsSL "$URL" -o "$WRAPPER_JAR"
    elif command -v wget >/dev/null 2>&1; then
        wget -q "$URL" -O "$WRAPPER_JAR"
    else
        echo "Neither curl nor wget is available. Download $URL manually into $WRAPPER_JAR" >&2
        exit 1
    fi
fi

if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

exec "$JAVACMD" -Dorg.gradle.appname=gradlew -classpath "$WRAPPER_JAR" \
    org.gradle.wrapper.GradleWrapperMain "$@"
