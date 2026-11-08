import test from "node:test";
import assert from "node:assert/strict";
import { register } from "node:module";

register("../harness/loader.mjs", import.meta.url);

const { computeThumb, offsetForThumbPosition } = await import("../src/scrollbars.js");

test("no thumb when everything fits", () => {
  assert.equal(computeThumb(500, 500, 0, 500), null);
  assert.equal(computeThumb(500, 501, 0, 500), null);
  assert.equal(computeThumb(0, 500, 0, 500), null);
  assert.equal(computeThumb(500, 1000, 0, 0), null);
});

test("thumb size is proportional with a minimum, and position follows the offset", () => {
  const half = computeThumb(500, 1000, 0, 500);
  assert.equal(half.length, 250);
  assert.equal(half.position, 0);
  assert.equal(computeThumb(500, 1000, 500, 500).position, 250);
  assert.equal(computeThumb(500, 1000, 250, 500).position, 125);
  const tiny = computeThumb(500, 100000, 0, 500);
  assert.equal(tiny.length, 40);
  assert.equal(tiny.travel, 460);
});

test("the thumb never leaves the track even when the offset overshoots", () => {
  const over = computeThumb(500, 1000, 9999, 500);
  assert.equal(over.position, over.travel);
  const under = computeThumb(500, 1000, -50, 500);
  assert.equal(under.position, 0);
});

test("dragging maps back to the same offset the thumb was computed from", () => {
  for (const offset of [0, 1, 137, 4999, 9500]) {
    const thumb = computeThumb(500, 10000, offset, 500);
    const back = offsetForThumbPosition(thumb.position, thumb);
    assert.ok(Math.abs(back - offset) < 1e-6, `${offset} -> ${back}`);
  }
  const thumb = computeThumb(500, 10000, 0, 500);
  assert.equal(offsetForThumbPosition(-30, thumb), 0);
  assert.equal(offsetForThumbPosition(1e9, thumb), thumb.range);
  assert.equal(offsetForThumbPosition(10, null), 0);
});

test("dragging up moves toward earlier lines and down toward later lines", () => {
  const thumb = computeThumb(500, 10000, 5000, 500);
  const up = offsetForThumbPosition(thumb.position - 40, thumb);
  const down = offsetForThumbPosition(thumb.position + 40, thumb);
  assert.ok(up < 5000 && down > 5000);
});
