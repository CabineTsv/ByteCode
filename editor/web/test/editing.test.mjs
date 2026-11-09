import test from "node:test";
import assert from "node:assert/strict";
import { register } from "node:module";

register("../harness/loader.mjs", import.meta.url);

const { backspaceRange, pairsFor } = await import("../src/editing.js");
const { leadingColumns, guideColumns } = await import("../src/indent-guides.js");

const all = pairsFor(["(", "[", "{", "'", '"', "`"]);

test("backspace on an empty pair removes both characters", () => {
  for (const [open, close] of [["(", ")"], ["[", "]"], ["{", "}"], ["'", "'"], ['"', '"'], ["`", "`"]]) {
    const text = `x = ${open}${close}`;
    assert.deepEqual(backspaceRange(text, 5, 4, all), { from: 4, to: 6 }, open);
  }
});

test("backspace beside a lone opening character removes only that character", () => {
  assert.equal(backspaceRange("call(abc)", 5, 4, all), null);
  assert.equal(backspaceRange("call(", 5, 4, all), null);
  assert.equal(backspaceRange("a(b)", 2, 4, all), null);
});

test("a pair is only removed when its kind is enabled", () => {
  const brackets = pairsFor(["(", "[", "{"]);
  assert.equal(backspaceRange('s = ""', 5, 4, brackets), null);
  assert.deepEqual(backspaceRange("f()", 2, 4, brackets), { from: 1, to: 3 });
  assert.equal(backspaceRange("f()", 2, 4, {}), null);
});

test("backspace in leading spaces removes back to the previous indent stop", () => {
  assert.deepEqual(backspaceRange("        x", 8, 4, {}), { from: 4, to: 8 });
  assert.deepEqual(backspaceRange("      x", 6, 4, {}), { from: 4, to: 6 });
  assert.deepEqual(backspaceRange("    x", 4, 4, {}), { from: 0, to: 4 });
  assert.deepEqual(backspaceRange("   ", 3, 4, {}), { from: 0, to: 3 });
  assert.deepEqual(backspaceRange("          ", 10, 2, {}), { from: 8, to: 10 });
});

test("backspace outside leading spaces falls through to the normal deletion", () => {
  assert.equal(backspaceRange("    x = 1", 7, 4, {}), null);
  assert.equal(backspaceRange("x    ", 5, 4, {}), null);
  assert.equal(backspaceRange("    x", 0, 4, {}), null);
  assert.equal(backspaceRange("\t\tx", 2, 4, {}), null);
  assert.equal(backspaceRange("    ", 4, 0, {}), null);
});

test("leading columns count spaces and tabs", () => {
  assert.deepEqual(leadingColumns("    x", 4), { columns: 4, blank: false });
  assert.deepEqual(leadingColumns("\t\tx", 4), { columns: 8, blank: false });
  assert.deepEqual(leadingColumns("  \tx", 4), { columns: 4, blank: false });
  assert.deepEqual(leadingColumns("    ", 4), { columns: 4, blank: true });
  assert.deepEqual(leadingColumns("", 4), { columns: 0, blank: true });
});

test("blank lines continue the guides of the surrounding block", () => {
  assert.equal(guideColumns("        x", 4, () => 99), 8);
  assert.equal(guideColumns("", 4, () => 8), 8);
  assert.equal(guideColumns("    ", 4, () => 0), 4);
  assert.equal(guideColumns("", 4, () => 0), 0);
});
