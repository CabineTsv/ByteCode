#!/usr/bin/env python3
"""Guards the CI workflow and keeps its tool versions in step with the Gradle files.

Why it exists: the first CI run failed inside a third-party action (android-actions/setup-android asked
sdkmanager for the removed package "tools") before Gradle even started. This check runs in the
quick-tests job and locally, uses only the standard library, and fails with an explanation when

  * a workflow uses android-actions/setup-android, or a third-party action that is not on the
    allow-list below (every new third-party action is a new way for CI to break);
  * SDK licences are accepted with `yes | ...` (that fails under `set -o pipefail`);
  * build.yml lost something the project requires (triggers, APK upload, the SDK setup step);
  * the tool versions in build.yml (Gradle, NDK, CMake, compile SDK, JDK) differ from the build files.

Usage:  python3 scripts/ci/check-workflow.py [--root PATH]
"""
import argparse
import pathlib
import re
import sys

ALLOWED_PREFIXES = ("actions/", "gradle/actions/")   # GitHub's and Gradle's own actions
ALLOWED_EXACT = ("softprops/action-gh-release",)      # attaches APKs to releases; only runs on releases
FORBIDDEN = {
    "android-actions/setup-android":
        "its default step runs `sdkmanager tools`, a package that no longer exists; "
        "use scripts/ci/setup-android-sdk.sh",
}
MODULE_BUILD_FILES = ("app/build.gradle.kts", "editor/build.gradle.kts", "luau/build.gradle.kts")


def strip_comments(text):
    """Removes # comments (YAML and shell); good enough for the pattern checks below."""
    return re.sub(r"(?m)(^|\s)#.*$", r"\1", text)


def check(root):
    problems = []

    def read(relative):
        path = root / relative
        if not path.is_file():
            problems.append(f"missing file: {relative}")
            return ""
        return path.read_text(encoding="utf-8")

    def find(text, pattern, where, flags=0):
        match = re.search(pattern, text, flags)
        if not match:
            problems.append(f"could not find {pattern!r} in {where}")
            return None
        return match.group(1)

    def compare(label, workflow_value, build_value, where):
        if workflow_value is not None and build_value is not None and workflow_value != build_value:
            problems.append(f"{label}: build.yml says {workflow_value} but {where} says {build_value}")

    workflow_dir = root / ".github" / "workflows"
    workflows = []
    if workflow_dir.is_dir():
        workflows = sorted(workflow_dir.glob("*.yml")) + sorted(workflow_dir.glob("*.yaml"))
    if not workflows:
        problems.append("no workflow files in .github/workflows")

    # 1. Which actions may be used, and shell pitfalls.
    actions_seen = set()
    for workflow in workflows:
        text = strip_comments(workflow.read_text(encoding="utf-8"))
        for match in re.finditer(r"^\s*(?:-\s+)?uses:\s*(\S+)", text, re.M):
            reference = match.group(1).strip("\"'")
            if reference.startswith("./"):
                continue
            action = reference.split("@", 1)[0]
            actions_seen.add(action)
            where = f"{workflow.name}: uses {reference}"
            if "@" not in reference:
                problems.append(f"{where} has no version (@vN)")
            if action in FORBIDDEN:
                problems.append(f"{where} is forbidden: {FORBIDDEN[action]}")
            elif not (action.startswith(ALLOWED_PREFIXES) or action in ALLOWED_EXACT):
                problems.append(f"{where} is a third-party action that is not on the allow-list in "
                                f"scripts/ci/check-workflow.py (add it there and note why in "
                                f"BYTECODE_HANDOFF.md only if it is really needed)")
        if re.search(r"\byes\s*\|", text):
            problems.append(f"{workflow.name}: `yes | ...` fails under pipefail "
                            f"(see scripts/ci/setup-android-sdk.sh)")
    scripts_dir = root / "scripts" / "ci"
    if scripts_dir.is_dir():
        for script in sorted(scripts_dir.glob("*.sh")):
            if script.name.startswith("test-"):
                continue  # self-tests contain the forbidden text on purpose, as test data
            if re.search(r"\byes\s*\|", strip_comments(script.read_text(encoding="utf-8"))):
                problems.append(f"{script.name}: `yes | ...` fails under pipefail")

    # 2. What build.yml must keep.
    build = workflow_dir / "build.yml"
    versions = {}
    if not build.is_file():
        problems.append("missing file: .github/workflows/build.yml")
    else:
        text = strip_comments(build.read_text(encoding="utf-8"))
        for trigger in ("push", "release", "workflow_dispatch"):
            if not re.search(rf"^  {trigger}:", text, re.M):
                problems.append(f"build.yml: trigger `{trigger}` is missing (the project requires it)")
        if "actions/upload-artifact" not in text or ".apk" not in text:
            problems.append("build.yml: the APK artifact upload is missing")
        if "scripts/ci/setup-android-sdk.sh" not in text:
            problems.append("build.yml: the Android SDK step (scripts/ci/setup-android-sdk.sh) is missing")
        if not (root / "scripts" / "ci" / "setup-android-sdk.sh").is_file():
            problems.append("missing file: scripts/ci/setup-android-sdk.sh")

        # 3. Versions in build.yml against the build files.
        def env_value(name):
            match = re.search(rf'^\s*{name}:\s*"([^"]+)"', text, re.M)
            if not match:
                problems.append(f"build.yml: env {name} not found")
            return match.group(1) if match else None

        versions = {
            "GRADLE_VERSION": env_value("GRADLE_VERSION"),
            "NDK_VERSION": env_value("NDK_VERSION"),
            "CMAKE_VERSION": env_value("CMAKE_VERSION"),
            "ANDROID_PLATFORM": env_value("ANDROID_PLATFORM"),
        }
        java_match = re.search(r'java-version:\s*"?(\d+)"?', text)
        versions["java-version"] = java_match.group(1) if java_match else None
        if java_match is None:
            problems.append("build.yml: java-version not found")

        wrapper = "gradle/wrapper/gradle-wrapper.properties"
        compare("Gradle", versions["GRADLE_VERSION"],
                find(read(wrapper), r"gradle-([0-9][0-9.]*)-(?:bin|all)\.zip", wrapper), wrapper)
        luau = read("luau/build.gradle.kts")
        compare("NDK", versions["NDK_VERSION"],
                find(luau, r'ndkVersion\s*=\s*"([^"]+)"', "luau/build.gradle.kts"), "luau/build.gradle.kts")
        compare("CMake", versions["CMAKE_VERSION"],
                find(luau, r'cmake\s*\{[^{}]*?\bversion\s*=\s*"([^"]+)"', "luau/build.gradle.kts", re.S),
                "luau/build.gradle.kts")
        for module_file in MODULE_BUILD_FILES:
            gradle_text = read(module_file)
            compare(f"compileSdk ({module_file})", versions["ANDROID_PLATFORM"],
                    find(gradle_text, r"compileSdk\s*=\s*(\d+)", module_file), module_file)
            compare(f"JDK ({module_file}, JavaVersion)", versions["java-version"],
                    find(gradle_text, r"JavaVersion\.VERSION_(\d+)", module_file), module_file)
            compare(f"JDK ({module_file}, jvmTarget)", versions["java-version"],
                    find(gradle_text, r'jvmTarget\s*=\s*"(\d+)"', module_file), module_file)
    return problems, workflows, actions_seen, versions


def main():
    parser = argparse.ArgumentParser(description="Guards the CI workflow (see the module docstring).")
    parser.add_argument("--root", default=None, help="project root (default: two levels above this script)")
    arguments = parser.parse_args()
    root = pathlib.Path(arguments.root) if arguments.root else pathlib.Path(__file__).resolve().parents[2]
    problems, workflows, actions_seen, versions = check(root)
    if problems:
        print(f"check-workflow: {len(problems)} problem(s)")
        for problem in problems:
            print(f"  - {problem}")
        return 1
    shown = ", ".join(f"{key}={value}" for key, value in versions.items())
    print(f"check-workflow: OK ({len(workflows)} workflow file(s), {len(actions_seen)} distinct actions; {shown})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
