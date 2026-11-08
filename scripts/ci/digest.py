#!/usr/bin/env python3
"""Short Markdown digest of CI logs, for the job summary of a failed build.

A failed Gradle build prints thousands of lines. This picks out the lines that matter (Kotlin, C++,
AAPT, npm/esbuild and Gradle errors, and Gradle's "What went wrong" blocks) so the person running
the build can paste a few lines instead of the whole log.

Usage:  python3 scripts/ci/digest.py LOG [LOG ...]      (Markdown goes to stdout; always exits 0)
Standard library only.
"""
import os
import re
import sys

MAX_ERROR_LINES = 30   # per log
MAX_TAIL_LINES = 40    # used only when no error line was found
MAX_BLOCK_LINES = 10   # per "What went wrong" block
MAX_LINE_CHARS = 400

ERROR_PATTERNS = [re.compile(pattern) for pattern in (
    r"^e: ",                                        # Kotlin compiler
    r"\berror:",                                    # clang / gcc / AAPT2 / javac
    r"\bfatal error\b",
    r"CMake Error",
    r"undefined reference",
    r"\bFAILED\b",                                  # "> Task :x:y FAILED"
    r"^FAILURE:",
    r"Execution failed for task",
    r"npm ERR!",
    r"\[ERROR\]",                                   # esbuild
    r"Could not (?:resolve|find|determine|download|get|start)",
    r"Android resource (?:linking|compilation) failed",
    r"Unresolved reference",
    r"Warning: Failed to find package",             # sdkmanager
    r"No matching toolchains",
    r"\bCaused by:",
)]
ANSI_ESCAPE = re.compile(r"\x1b\[[0-9;?]*[ -/]*[@-~]")


def read_lines(path):
    """Log lines without ANSI colours; progress bars that redraw with \\r keep only their last state."""
    with open(path, "rb") as handle:
        text = handle.read().decode("utf-8", errors="replace")
    lines = []
    for raw in text.replace("\r\n", "\n").split("\n"):
        raw = ANSI_ESCAPE.sub("", raw.split("\r")[-1]).rstrip()
        lines.append(raw)
    return lines


def clip(line):
    return line if len(line) <= MAX_LINE_CHARS else line[: MAX_LINE_CHARS - 1] + "…"


def fence(lines):
    body = "\n".join(clip(line).replace("```", "'''") for line in lines)
    return "```text\n" + body + "\n```\n"


def error_lines(lines):
    seen = set()
    found = []
    for line in lines:
        if line in seen or not line.strip():
            continue
        if any(pattern.search(line) for pattern in ERROR_PATTERNS):
            seen.add(line)
            found.append(line)
    return found


def what_went_wrong(lines):
    """Gradle's '* What went wrong:' blocks (the line after the heading up to '* Try:')."""
    blocks = []
    index = 0
    while index < len(lines):
        if lines[index].startswith("* What went wrong:"):
            block = []
            index += 1
            while index < len(lines) and not lines[index].startswith("* Try:") and len(block) < MAX_BLOCK_LINES:
                if lines[index].strip():
                    block.append(lines[index])
                index += 1
            blocks.append(block)
        else:
            index += 1
    return blocks


def digest(paths):
    out = ["## Build failure digest", ""]
    existing = [path for path in paths if os.path.isfile(path)]
    if not existing:
        out.append("No log file was found, so the failure happened before a log was written. "
                   "Open the failed step of this run instead.")
        return "\n".join(out) + "\n"

    existing.sort(key=os.path.getmtime)
    out.append("Paste the sections below into the chat. The complete logs are in the `ci-logs` artifact.")
    out.append("")
    shown = False
    for path in existing:
        lines = read_lines(path)
        errors = error_lines(lines)
        blocks = what_went_wrong(lines)
        if not errors and not blocks:
            continue
        shown = True
        name = os.path.basename(path)
        if errors:
            extra = "" if len(errors) <= MAX_ERROR_LINES else f", first {MAX_ERROR_LINES} shown"
            out.append(f"### {name}: {len(errors)} error line(s){extra}")
            out.append(fence(errors[:MAX_ERROR_LINES]))
        for number, block in enumerate(blocks, start=1):
            out.append(f"### {name}: Gradle \"What went wrong\" #{number}")
            out.append(fence(block))
    if not shown:
        newest = existing[-1]
        tail = [line for line in read_lines(newest) if line.strip()][-MAX_TAIL_LINES:]
        out.append(f"No error lines were recognised. Last {len(tail)} lines of {os.path.basename(newest)}:")
        out.append("")
        out.append(fence(tail))
    return "\n".join(out) + "\n"


if __name__ == "__main__":
    sys.stdout.write(digest(sys.argv[1:]))
    sys.exit(0)
