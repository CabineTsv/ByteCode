#!/usr/bin/env python3
"""Checks that the repository is complete: every file listed in MANIFEST.sha256 must exist.

Why it exists: a CI run failed because gradle/wrapper/gradle-wrapper.properties had not made it into
the GitHub repository (an upload had dropped it). Without this check an incomplete repository shows up
one missing file per run, far from its cause. This lists every missing file at once.

  * a listed file is missing   -> error, exit code 1: the repository is incomplete
  * MANIFEST.sha256 is missing -> error, exit code 1
  * a file differs from its hash -> only reported with --hashes, and never an error: a file may have been
    edited on purpose, and the manifest is regenerated only with each handoff

MANIFEST.sha256 and BYTECODE_HANDOFF.md are not listed in the manifest (the snapshot id is derived from it).

Usage:  python3 scripts/ci/check-manifest.py [--root PATH] [--hashes]
Standard library only.
"""
import argparse
import hashlib
import pathlib
import re
import sys

LINE = re.compile(r"^([0-9a-f]{64})[ \t]+\*?(.+?)\s*$")
MAX_NAMES_IN_ANNOTATION = 20


def read_manifest(root):
    """Returns [(path, sha256)] or None when MANIFEST.sha256 does not exist."""
    manifest = root / "MANIFEST.sha256"
    if not manifest.is_file():
        return None
    entries = []
    for number, line in enumerate(manifest.read_text(encoding="utf-8").splitlines(), start=1):
        if not line.strip():
            continue
        match = LINE.match(line)
        if not match:
            raise ValueError(f"MANIFEST.sha256 line {number} is not '<sha256>  <path>': {line[:80]!r}")
        path = match.group(2)
        entries.append((path[2:] if path.startswith("./") else path, match.group(1)))
    return entries


def main():
    parser = argparse.ArgumentParser(description="Checks that every file listed in MANIFEST.sha256 exists.")
    parser.add_argument("--root", default=None, help="project root (default: two levels above this script)")
    parser.add_argument("--hashes", action="store_true",
                        help="also list files whose content differs from the manifest (informational)")
    arguments = parser.parse_args()
    root = pathlib.Path(arguments.root) if arguments.root else pathlib.Path(__file__).resolve().parents[2]

    try:
        entries = read_manifest(root)
    except ValueError as error:
        print(f"::error title=MANIFEST.sha256 is malformed::{error}")
        print(f"check-manifest: {error}")
        return 1
    if entries is None:
        print("::error title=Repository is incomplete::MANIFEST.sha256 is missing")
        print("check-manifest: MANIFEST.sha256 was not found in the repository root. "
              "Add it (it is in every ByteCode zip).")
        return 1

    missing = [path for path, _ in entries if not (root / path).is_file()]
    print(f"check-manifest: {len(entries) - len(missing)} of {len(entries)} files listed in MANIFEST.sha256 exist")

    if arguments.hashes:
        differing = [path for path, digest in entries
                     if path not in missing and hashlib.sha256((root / path).read_bytes()).hexdigest() != digest]
        for path in differing:
            print(f"  note: differs from the manifest: {path}")
        print(f"check-manifest: {len(differing)} file(s) differ from the manifest (informational only)")

    if missing:
        shown = ", ".join(missing[:MAX_NAMES_IN_ANNOTATION])
        if len(missing) > MAX_NAMES_IN_ANNOTATION:
            shown += f" and {len(missing) - MAX_NAMES_IN_ANNOTATION} more"
        print(f"::error title=Repository is incomplete::{len(missing)} file(s) listed in MANIFEST.sha256 "
              f"are missing: {shown}")
        for path in missing:
            print(f"  missing: {path}")
        print("Add the missing files with exactly these paths (they are in the ByteCode zip). "
              "If a file was removed on purpose, delete its line from MANIFEST.sha256.")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
