#!/usr/bin/env bash
# Self-tests for the CI helper scripts in scripts/ci. They need no network, no Android SDK and no
# GitHub: a fake sdkmanager, sample logs and mutated copies of the workflow stand in for the real things.
#
#   bash scripts/ci/test-ci-scripts.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
passed=0

pass() {
  echo "ok   $1"
  passed=$((passed + 1))
}

fail() {
  echo "FAIL $1"
  shift
  for line in "$@"; do
    echo "     $line"
  done
  exit 1
}

run_ok() { # run_ok NAME COMMAND...
  local name="$1"
  shift
  if "$@" > "$WORK/out.txt" 2>&1; then
    pass "$name"
  else
    fail "$name" "$(tail -n 15 "$WORK/out.txt")"
  fi
}

run_fails_with() { # run_fails_with NAME REGEX COMMAND...
  local name="$1" regex="$2"
  shift 2
  if "$@" > "$WORK/out.txt" 2>&1; then
    fail "$name" "expected a failure but the command succeeded" "$(tail -n 10 "$WORK/out.txt")"
  elif grep -Eq "$regex" "$WORK/out.txt"; then
    pass "$name"
  else
    fail "$name" "it failed, but the output does not match /$regex/" "$(tail -n 10 "$WORK/out.txt")"
  fi
}

replace_in() { # replace_in FILE OLD NEW (fails when OLD is absent, so a test can never pass vacuously)
  python3 - "$1" "$2" "$3" <<'PY'
import sys
path, old, new = sys.argv[1:4]
text = open(path, encoding="utf-8").read()
if old not in text:
    sys.exit("replace_in: not found: " + old)
open(path, "w", encoding="utf-8").write(text.replace(old, new, 1))
PY
}

# ---------------------------------------------------------------- syntax
run_ok "setup-android-sdk.sh has valid bash syntax" bash -n scripts/ci/setup-android-sdk.sh
run_ok "test-ci-scripts.sh has valid bash syntax" bash -n scripts/ci/test-ci-scripts.sh
for py in scripts/ci/check-workflow.py scripts/ci/check-manifest.py scripts/ci/digest.py; do
  run_ok "$py parses" python3 -c "import ast, sys; ast.parse(open(sys.argv[1], encoding='utf-8').read())" "$py"
done

# ---------------------------------------------------------------- workflow guard
run_ok "workflow guard passes on the real project" python3 scripts/ci/check-workflow.py

fresh_tree() {
  rm -rf "$WORK/tree"
  mkdir -p "$WORK/tree/gradle/wrapper" "$WORK/tree/app" "$WORK/tree/editor" "$WORK/tree/luau"
  cp -a .github "$WORK/tree/"
  cp -a scripts "$WORK/tree/"
  cp gradle/wrapper/gradle-wrapper.properties "$WORK/tree/gradle/wrapper/"
  for module in app editor luau; do
    cp "$module/build.gradle.kts" "$WORK/tree/$module/"
  done
}
guard_on_tree() { python3 "$ROOT/scripts/ci/check-workflow.py" --root "$WORK/tree"; }
append_line() { printf '%s\n' "$2" >> "$WORK/tree/$1"; } # append_line RELATIVE-FILE TEXT

fresh_tree
append_line .github/workflows/build.yml '# android-actions/setup-android must never be used'
run_ok "guard ignores a forbidden action that is only named in a comment" guard_on_tree

fresh_tree
append_line .github/workflows/build.yml '      - uses: android-actions/setup-android@v3'
run_fails_with "guard rejects android-actions/setup-android" 'is forbidden' guard_on_tree

fresh_tree
append_line .github/workflows/build.yml '      - uses: someone/unknown-action@v1'
run_fails_with "guard rejects a third-party action that is not on the allow-list" 'allow-list' guard_on_tree

fresh_tree
append_line .github/workflows/build.yml '      - uses: actions/checkout'
run_fails_with "guard rejects an action without a version" 'has no version' guard_on_tree

fresh_tree
append_line .github/workflows/build.yml '        run: yes | sdkmanager --licenses'
run_fails_with "guard rejects 'yes | sdkmanager' (breaks under pipefail)" 'pipefail' guard_on_tree

fresh_tree
replace_in "$WORK/tree/.github/workflows/build.yml" 'NDK_VERSION: "27.0.12077973"' 'NDK_VERSION: "26.3.11579264"'
run_fails_with "guard detects an NDK version mismatch" 'NDK: build.yml says 26.3.11579264' guard_on_tree

fresh_tree
replace_in "$WORK/tree/gradle/wrapper/gradle-wrapper.properties" 'gradle-8.10.2-bin.zip' 'gradle-8.9-bin.zip'
run_fails_with "guard detects a Gradle version mismatch" 'Gradle: build.yml says 8.10.2 but gradle/wrapper' guard_on_tree

fresh_tree
replace_in "$WORK/tree/luau/build.gradle.kts" 'compileSdk = 35' 'compileSdk = 34'
run_fails_with "guard detects a compileSdk mismatch" 'compileSdk \(luau/build.gradle.kts\)' guard_on_tree

fresh_tree
replace_in "$WORK/tree/.github/workflows/build.yml" '  workflow_dispatch:' '  workflow_call:'
run_fails_with "guard notices that workflow_dispatch was dropped" 'trigger `workflow_dispatch` is missing' guard_on_tree

fresh_tree
replace_in "$WORK/tree/.github/workflows/build.yml" 'bash scripts/ci/setup-android-sdk.sh' 'echo skipped'
run_fails_with "guard notices that the Android SDK step was dropped" 'Android SDK step' guard_on_tree

# ---------------------------------------------------------------- repository completeness check
run_ok "manifest check passes on the real project" python3 scripts/ci/check-manifest.py

make_manifest_tree() {
  rm -rf "$WORK/mtree"
  mkdir -p "$WORK/mtree/a/b" "$WORK/mtree/gradle/wrapper"
  printf 'one\n' > "$WORK/mtree/a/one.txt"
  printf 'two\n' > "$WORK/mtree/a/b/two.txt"
  printf 'props\n' > "$WORK/mtree/gradle/wrapper/gradle-wrapper.properties"
  (cd "$WORK/mtree" && sha256sum a/one.txt a/b/two.txt gradle/wrapper/gradle-wrapper.properties > MANIFEST.sha256)
}
manifest_on_tree() { python3 "$ROOT/scripts/ci/check-manifest.py" --root "$WORK/mtree" "$@"; }

make_manifest_tree
run_ok "manifest check passes when every listed file exists" manifest_on_tree

make_manifest_tree
rm "$WORK/mtree/gradle/wrapper/gradle-wrapper.properties"
run_fails_with "manifest check names the missing file (the cause of the second CI failure)" \
  'missing: gradle/wrapper/gradle-wrapper.properties' manifest_on_tree
grep -q '::error title=Repository is incomplete::' "$WORK/out.txt" \
  || fail "manifest check: no error annotation for the run page" "$(cat "$WORK/out.txt")"
pass "manifest check: the error annotation names the missing file"

make_manifest_tree
rm "$WORK/mtree/a/one.txt" "$WORK/mtree/a/b/two.txt"
run_fails_with "manifest check lists every missing file in one run" 'missing: a/one.txt' manifest_on_tree
grep -q 'missing: a/b/two.txt' "$WORK/out.txt" || fail "manifest check: the second missing file is not listed" "$(cat "$WORK/out.txt")"
pass "manifest check: all missing files are listed together"

make_manifest_tree
printf 'edited on purpose\n' > "$WORK/mtree/a/one.txt"
run_ok "manifest check does not fail when a file was edited on purpose" manifest_on_tree
run_ok "manifest check with --hashes only reports the edited file" manifest_on_tree --hashes
grep -q 'note: differs from the manifest: a/one.txt' "$WORK/out.txt" \
  || fail "manifest check --hashes: the edited file is not reported" "$(cat "$WORK/out.txt")"
pass "manifest check: --hashes names the edited file"

make_manifest_tree
rm "$WORK/mtree/MANIFEST.sha256"
run_fails_with "manifest check fails clearly when MANIFEST.sha256 itself is missing" \
  'MANIFEST.sha256 was not found' manifest_on_tree

# ---------------------------------------------------------------- Android SDK script (fake sdkmanager)
make_fake_sdk() { # make_fake_sdk ROOT
  local root="$1"
  rm -rf "$root"
  mkdir -p "$root/cmdline-tools/latest/bin"
  cat > "$root/cmdline-tools/latest/bin/sdkmanager" <<'FAKE'
#!/usr/bin/env bash
# Fake sdkmanager for the self-tests: records its arguments and creates the folders a real install would.
root=""
mode=""
packages=()
for argument in "$@"; do
  case "$argument" in
    --sdk_root=*) root="${argument#--sdk_root=}" ;;
    --licenses) mode="licenses" ;;
    --install) mode="install" ;;
    --list_installed) mode="list" ;;
    -*) ;;
    *) packages+=("$argument") ;;
  esac
done
if [ -n "${FAKE_SDK_LOG:-}" ]; then
  echo "mode=$mode" >> "$FAKE_SDK_LOG"
  for package in "${packages[@]:-}"; do
    [ -n "$package" ] && echo "package:$package" >> "$FAKE_SDK_LOG"
  done
fi
case "$mode" in
  licenses)
    cat > /dev/null
    echo "License text that must never reach the CI log"
    echo "All SDK package licenses accepted"
    ;;
  install)
    for package in "${packages[@]}"; do
      if [ "${FAKE_FAIL_PACKAGE:-}" = "$package" ]; then
        echo "Warning: Failed to find package '$package'"
        exit 1
      fi
      if [ "${FAKE_SKIP_PACKAGE:-}" = "$package" ]; then
        continue
      fi
      case "$package" in
        platform-tools) mkdir -p "$root/platform-tools"; : > "$root/platform-tools/adb" ;;
        platforms\;android-*) dir="$root/platforms/${package#platforms;}"; mkdir -p "$dir"; : > "$dir/android.jar" ;;
        build-tools\;*) dir="$root/build-tools/${package#build-tools;}"; mkdir -p "$dir"; : > "$dir/aapt2" ;;
        ndk\;*) version="${package#ndk;}"; mkdir -p "$root/ndk/$version"; echo "Pkg.Revision = $version" > "$root/ndk/$version/source.properties" ;;
        cmake\;*) version="${package#cmake;}"; mkdir -p "$root/cmake/$version/bin"; : > "$root/cmake/$version/bin/cmake" ;;
      esac
    done
    ;;
  list)
    echo "Installed packages:"
    ls "$root"
    ;;
esac
FAKE
  chmod +x "$root/cmdline-tools/latest/bin/sdkmanager"
}

run_sdk_script() { # run_sdk_script [NAME=value ...]   (extra assignments override the defaults)
  env -i PATH="/usr/bin:/bin" HOME="$WORK" \
    ANDROID_HOME="$WORK/sdk" RUNNER_TEMP="$WORK/runner-temp" GITHUB_ENV="$WORK/github-env" \
    NDK_VERSION="27.0.12077973" CMAKE_VERSION="3.22.1" ANDROID_PLATFORM="35" \
    BUILD_TOOLS_VERSIONS="34.0.0 35.0.0" FAKE_SDK_LOG="$WORK/fake-sdk.log" \
    "$@" \
    bash "$ROOT/scripts/ci/setup-android-sdk.sh"
}

make_fake_sdk "$WORK/sdk"
: > "$WORK/github-env"
rm -f "$WORK/fake-sdk.log"
run_ok "SDK script: installs and verifies everything with the (fake) sdkmanager" run_sdk_script
grep -q "Android SDK ready" "$WORK/out.txt" || fail "SDK script: no success line" "$(tail -n 10 "$WORK/out.txt")"
if grep -q "License text that must never reach the CI log" "$WORK/out.txt"; then
  fail "SDK script: licence text leaked into the log"
fi
pass "SDK script: licence text stays out of the log"
grep -qx "ANDROID_NDK_HOME=$WORK/sdk/ndk/27.0.12077973" "$WORK/github-env" \
  || fail "SDK script: ANDROID_NDK_HOME was not exported" "$(cat "$WORK/github-env")"
pass "SDK script: ANDROID_HOME and ANDROID_NDK_HOME are exported to later steps"
if grep -qx "package:tools" "$WORK/fake-sdk.log"; then
  fail "SDK script asked for the removed package 'tools' (the cause of the first CI failure)"
fi
for expected in "package:platform-tools" "package:platforms;android-35" "package:build-tools;34.0.0" \
  "package:build-tools;35.0.0" "package:ndk;27.0.12077973" "package:cmake;3.22.1"; do
  grep -qx "$expected" "$WORK/fake-sdk.log" || fail "SDK script did not request $expected" "$(cat "$WORK/fake-sdk.log")"
done
pass "SDK script: requests exactly the needed packages and never 'tools'"
[ -f "$WORK/runner-temp/ci-logs/sdk-install.log" ] || fail "SDK script: sdk-install.log was not written"
pass "SDK script: writes its logs to ci-logs/"

make_fake_sdk "$WORK/sdk"
run_fails_with "SDK script: fails clearly when sdkmanager cannot find a package" \
  '::error title=Android SDK setup::sdkmanager --install failed' \
  run_sdk_script FAKE_FAIL_PACKAGE='ndk;27.0.12077973'
grep -q "Failed to find package" "$WORK/out.txt" || fail "SDK script: the sdkmanager message was not shown"
pass "SDK script: shows the sdkmanager message when an install fails"

make_fake_sdk "$WORK/sdk"
run_fails_with "SDK script: notices a component that is silently missing after the install" \
  'missing after install: .*cmake/3.22.1/bin/cmake' \
  run_sdk_script FAKE_SKIP_PACKAGE='cmake;3.22.1'

mkdir -p "$WORK/empty-sdk"
run_fails_with "SDK script: fails clearly when there is no sdkmanager" \
  'sdkmanager not found' \
  run_sdk_script ANDROID_HOME="$WORK/empty-sdk"

make_fake_sdk "$WORK/sdk"
run_fails_with "SDK script: refuses to run without a pinned NDK version" \
  'NDK_VERSION is not set' \
  run_sdk_script NDK_VERSION=

# ---------------------------------------------------------------- failure digest
printf '%s\n' \
  '> Task :editor:npmInstall' \
  '> Task :editor:bundleEditor FAILED' \
  '✘ [ERROR] Could not resolve "@codemirror/lang-nope"' \
  'e: file:///work/app/src/main/java/dev/bytecode/app/Example.kt:12:5 Unresolved reference: nothing' \
  'FAILURE: Build failed with an exception.' \
  '' \
  '* What went wrong:' \
  "Execution failed for task ':editor:bundleEditor'." \
  "> Process 'command 'node'' finished with non-zero exit value 1" \
  '' \
  '* Try:' \
  '> Run with --stacktrace option to get the stack trace.' > "$WORK/gradle.log"
printf '\033[31mred error: coloured line\033[0m\n' >> "$WORK/gradle.log"
printf 'progress 10%%\rprogress 100%%\n' >> "$WORK/gradle.log"
printf '%s\n' "Warning: Failed to find package 'tools'" > "$WORK/sdk.log"

python3 scripts/ci/digest.py "$WORK/sdk.log" "$WORK/gradle.log" > "$WORK/digest.md"
for expected in '## Build failure digest' 'Unresolved reference: nothing' 'bundleEditor FAILED' \
  'Could not resolve "@codemirror/lang-nope"' "Execution failed for task ':editor:bundleEditor'." \
  'finished with non-zero exit value 1' "Failed to find package 'tools'" 'red error: coloured line'; do
  grep -Fq "$expected" "$WORK/digest.md" || fail "digest: missing '$expected'" "$(head -n 40 "$WORK/digest.md")"
done
pass "digest: shows the Kotlin, esbuild, Gradle and sdkmanager errors"
if grep -q "$(printf '\033')" "$WORK/digest.md"; then
  fail "digest: ANSI colour codes were not removed"
fi
pass "digest: removes ANSI colour codes"
grep -q '^```text' "$WORK/digest.md" || fail "digest: errors are not in a code block"
pass "digest: wraps log lines in code blocks"

printf '%s\n' 'first line' 'second line' 'last line' > "$WORK/clean.log"
python3 scripts/ci/digest.py "$WORK/clean.log" > "$WORK/digest-clean.md"
grep -Fq 'No error lines were recognised' "$WORK/digest-clean.md" && grep -Fq 'last line' "$WORK/digest-clean.md" \
  || fail "digest: a log without errors should show its last lines" "$(cat "$WORK/digest-clean.md")"
pass "digest: shows the tail of a log without recognised errors"

run_ok "digest: copes with a missing log file" python3 scripts/ci/digest.py "$WORK/does-not-exist.log"
grep -Fq 'No log file was found' "$WORK/out.txt" || fail "digest: no message for a missing log" "$(cat "$WORK/out.txt")"
pass "digest: explains a missing log file"

echo "All $passed CI self-tests passed."
