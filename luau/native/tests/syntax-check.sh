#!/bin/sh
# Type-checks the native sources against small hand-written stand-ins for the Luau and JNI headers
# (tests/stubs). This proves the C++ is internally consistent; it does NOT prove that it matches the
# real Luau headers - only the Android build (which clones Luau) can do that.
set -e
cd "$(dirname "$0")/.."
for file in src/luau_runtime.cpp src/luau_diagnostics.cpp src/jni_bridge.cpp; do
    g++ -std=c++17 -fsyntax-only -Wall -Wextra -DBYTECODE_LUAU_TAG='"test"' -Itests/stubs -Isrc "$file"
    echo "ok  $file"
done
