#!/usr/bin/env bash
# Prepares the Android SDK for the CI build. Works on GitHub-hosted Ubuntu runners and locally.
#
# It uses the SDK that ships with the runner image and installs/verifies exactly the components that
# ByteCode needs. The third-party action android-actions/setup-android is deliberately NOT used: its
# default step runs `sdkmanager tools`, the package "tools" no longer exists in the SDK repository,
# and that failed the first CI run before Gradle even started (BYTECODE_HANDOFF.md, section 10, E1).
#
# Environment:
#   NDK_VERSION, CMAKE_VERSION, ANDROID_PLATFORM, BUILD_TOOLS_VERSIONS   required (set in build.yml)
#   ANDROID_HOME or ANDROID_SDK_ROOT   SDK location (default: /usr/local/lib/android/sdk)
#   GITHUB_ENV    when set, ANDROID_HOME, ANDROID_SDK_ROOT and ANDROID_NDK_HOME are exported to later steps
#   RUNNER_TEMP   where the logs go (default: /tmp); the failure digest reads $RUNNER_TEMP/ci-logs
set -euo pipefail

: "${NDK_VERSION:?NDK_VERSION is not set}"
: "${CMAKE_VERSION:?CMAKE_VERSION is not set}"
: "${ANDROID_PLATFORM:?ANDROID_PLATFORM is not set}"
: "${BUILD_TOOLS_VERSIONS:?BUILD_TOOLS_VERSIONS is not set}"

SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
LOG_DIR="${RUNNER_TEMP:-/tmp}/ci-logs"
mkdir -p "$LOG_DIR"

fail() {
  echo "::error title=Android SDK setup::$*"
  exit 1
}

SDKMANAGER="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
if [ ! -x "$SDKMANAGER" ]; then
  SDKMANAGER="$(command -v sdkmanager || true)"
fi
[ -n "$SDKMANAGER" ] || fail "sdkmanager not found in $SDK_ROOT/cmdline-tools/latest/bin and not on PATH"

echo "SDK root:   $SDK_ROOT"
echo "sdkmanager: $SDKMANAGER"

# Accept the licences without printing them (their text is thousands of lines). printf is used instead
# of `yes`: under `set -o pipefail`, `yes | sdkmanager` fails because `yes` dies from SIGPIPE.
if ! printf 'y\n%.0s' $(seq 1 100) | "$SDKMANAGER" --sdk_root="$SDK_ROOT" --licenses > "$LOG_DIR/sdk-licenses.log" 2>&1; then
  tail -n 40 "$LOG_DIR/sdk-licenses.log" || true
  fail "accepting the Android SDK licences failed (log: sdk-licenses.log)"
fi

PACKAGES=("platform-tools" "platforms;android-${ANDROID_PLATFORM}")
for bt in ${BUILD_TOOLS_VERSIONS}; do
  PACKAGES+=("build-tools;${bt}")
done
PACKAGES+=("ndk;${NDK_VERSION}" "cmake;${CMAKE_VERSION}")

echo "Installing: ${PACKAGES[*]}"
if ! "$SDKMANAGER" --sdk_root="$SDK_ROOT" --install "${PACKAGES[@]}" > "$LOG_DIR/sdk-install.log" 2>&1; then
  tail -n 40 "$LOG_DIR/sdk-install.log" || true
  fail "sdkmanager --install failed for: ${PACKAGES[*]} (log: sdk-install.log)"
fi

# Do not trust the exit code alone: check that every component the build needs is really there, so a
# problem in the SDK shows up here with a clear message and not as an obscure Gradle/CMake error.
missing=()
need() {
  [ -e "$SDK_ROOT/$1" ] || missing+=("$1")
}
need "platforms/android-${ANDROID_PLATFORM}/android.jar"
for bt in ${BUILD_TOOLS_VERSIONS}; do
  need "build-tools/${bt}/aapt2"
done
need "ndk/${NDK_VERSION}/source.properties"
need "cmake/${CMAKE_VERSION}/bin/cmake"
if [ "${#missing[@]}" -gt 0 ]; then
  "$SDKMANAGER" --sdk_root="$SDK_ROOT" --list_installed 2>&1 | tail -n 40 || true
  fail "missing after install: ${missing[*]}"
fi
grep -Fq "${NDK_VERSION}" "$SDK_ROOT/ndk/${NDK_VERSION}/source.properties" \
  || fail "ndk/${NDK_VERSION}/source.properties does not mention version ${NDK_VERSION}"

if [ -n "${GITHUB_ENV:-}" ]; then
  {
    echo "ANDROID_HOME=$SDK_ROOT"
    echo "ANDROID_SDK_ROOT=$SDK_ROOT"
    echo "ANDROID_NDK_HOME=$SDK_ROOT/ndk/${NDK_VERSION}"
    echo "ANDROID_NDK_ROOT=$SDK_ROOT/ndk/${NDK_VERSION}"
  } >> "$GITHUB_ENV"
fi

echo "Android SDK ready: platform ${ANDROID_PLATFORM}, build-tools ${BUILD_TOOLS_VERSIONS}, NDK ${NDK_VERSION}, CMake ${CMAKE_VERSION}"
