import test from "node:test";
import assert from "node:assert/strict";
import { register } from "node:module";

register("../harness/loader.mjs", import.meta.url);

const { EditorState } = await import("@codemirror/state");
const ids = await import("../src/identifiers.js");
const { luauCompletionSource } = await import("../src/luau/completion.js");
const { createWordCompletionSource } = await import("../src/words.js");

const makeState = (doc) => EditorState.create({ doc, extensions: [ids.identifierIndexExtension] });
const labels = (result) => result.options.map((option) => option.label);
const snapshot = (index) => JSON.stringify([...index.counts.entries()].sort());

test("an identifier typed earlier is suggested again from its first letters", () => {
  const doc = "local hookfunction_alias = 1\nprint(hookfunction_alias)\nhookf";
  const state = makeState(doc);
  const result = ids.suggestIdentifiers(state, { pos: doc.length, prefix: "hookf" });
  assert.deepEqual(labels(result), ["hookfunction_alias"]);
  assert.equal(result.truncated, false);
});

test("the word being typed does not suggest itself", () => {
  const state = makeState("hookf");
  assert.deepEqual(labels(ids.suggestIdentifiers(state, { pos: 5, prefix: "hookf" })), []);
  const twice = makeState("hookf\nhookf");
  assert.deepEqual(labels(ids.suggestIdentifiers(twice, { pos: 11, prefix: "hookf" })), []);
  const other = makeState("hookfoo\nhookf");
  assert.deepEqual(labels(ids.suggestIdentifiers(other, { pos: 13, prefix: "hookf" })), ["hookfoo"]);
});

test("suggestions are unique, case-sensitive by default and folded for case-insensitive languages", () => {
  const doc = "Value value VALUE Value\nva";
  const state = makeState(doc);
  const sensitive = labels(ids.suggestIdentifiers(state, { pos: doc.length, prefix: "va" })).sort();
  assert.deepEqual(sensitive, ["VALUE", "Value", "value"]);
  const folded = labels(ids.suggestIdentifiers(state, { pos: doc.length, prefix: "va", caseSensitive: false }));
  assert.equal(folded.length, 1);
});

test("short words and digits-led text are not indexed", () => {
  const state = makeState("ab abc 12abc x_1");
  const index = ids.identifierIndex(state);
  assert.deepEqual([...index.counts.entries()].sort(), [["abc", 1], ["x_1", 1]]);
  assert.equal(ids.followsDigit(state, 9), true);
  assert.equal(ids.followsDigit(state, 5), false);
});

test("the index follows edits without rescanning and matches a full rebuild", () => {
  let state = makeState("alpha_one beta_two\ngamma_three");
  const index = ids.identifierIndex(state);
  assert.deepEqual([...index.counts.keys()].sort(), ["alpha_one", "beta_two", "gamma_three"]);

  state = state.update({ changes: { from: 0, to: 0, insert: "delta_four " } }).state;
  assert.equal(state.field(ids.identifierIndexExtension).index, index);
  assert.equal(index.doc, state.doc);
  assert.ok(index.counts.has("delta_four"));

  state = state.update({ changes: { from: 0, to: 11 + 9, insert: "" } }).state;
  assert.equal(index.counts.has("alpha_one"), false);
  assert.equal(index.counts.has("delta_four"), false);

  state = state.update({ changes: [{ from: 0, to: 0, insert: "first_word\n" }, { from: state.doc.length, to: state.doc.length, insert: "\nlast_word" }] }).state;
  assert.ok(index.counts.has("first_word") && index.counts.has("last_word"));

  const fresh = new ids.IdentifierIndex();
  fresh.rebuild(state.doc);
  assert.equal(snapshot(index), snapshot(fresh));
});

test("joining and splitting lines keeps counts exact", () => {
  let state = makeState("one_word\ntwo_word\nthree_word");
  const index = ids.identifierIndex(state);
  state = state.update({ changes: { from: 8, to: 9, insert: " " } }).state;
  state = state.update({ changes: { from: 4, to: 5, insert: "\n" } }).state;
  const fresh = new ids.IdentifierIndex();
  fresh.rebuild(state.doc);
  assert.equal(snapshot(index), snapshot(fresh));
});

test("an index that fell out of sync rebuilds itself", () => {
  const state = makeState("stale_name");
  const index = ids.identifierIndex(state);
  const branch = state.update({ changes: { from: 0, to: 10, insert: "fresh_name" } }).state;
  const sibling = state.update({ changes: { from: 0, to: 10, insert: "other_name" } }).state;
  assert.ok(ids.identifierIndex(branch).counts.has("fresh_name") || index.doc === branch.doc);
  assert.deepEqual([...ids.identifierIndex(sibling).counts.keys()], ["other_name"]);
});

test("many matches are capped and the result asks to be recomputed while typing", () => {
  const words = Array.from({ length: 450 }, (_, i) => `item_${String(i).padStart(3, "0")}`);
  const doc = `${words.join("\n")}\nit`;
  const state = makeState(doc);
  const result = ids.suggestIdentifiers(state, { pos: doc.length, prefix: "it" });
  assert.equal(result.options.length, ids.RESULT_LIMIT);
  assert.equal(result.truncated, true);
  const validFor = ids.validForPrefix("it", true);
  assert.equal(typeof validFor, "function");
  assert.equal(validFor("it"), true);
  assert.equal(validFor("ite"), false);
  assert.ok(ids.validForPrefix("it", false) instanceof RegExp);
});

test("frequent and recently used identifiers rank higher", () => {
  const doc = "rarely_used\nfrequent_word frequent_word frequent_word frequent_word\nrecent_word\nr";
  const state = makeState(doc);
  const result = ids.suggestIdentifiers(state, { pos: doc.length, prefix: "r" });
  const boost = (label) => result.options.find((option) => option.label === label).boost;
  assert.ok(boost("frequent_word") > boost("rarely_used"));
  assert.ok(boost("recent_word") >= boost("rarely_used"));
});

test("the generic word source returns options only inside words", () => {
  const source = createWordCompletionSource();
  const doc = "custom_identifier = 1\ncust";
  const state = makeState(doc);
  const context = {
    state, pos: doc.length, explicit: false,
    matchBefore: (regex) => {
      const text = doc.slice(doc.lastIndexOf("\n") + 1);
      const found = text.match(regex);
      return found ? { from: doc.length - found[0].length, to: doc.length, text: found[0] } : null;
    },
  };
  const result = source(context);
  assert.deepEqual(result.options.map((option) => option.label), ["custom_identifier"]);
  assert.equal(result.from, doc.length - 4);
});

test("luau completion merges language suggestions with document identifiers", () => {
  const doc = "local myCustomHelper = 1\nlocal function buildThing() end\nmyC";
  const state = makeState(doc);
  const result = luauCompletionSource({ state, pos: doc.length, explicit: false });
  const found = result.options.map((option) => option.label);
  assert.ok(found.includes("myCustomHelper"));
  assert.ok(found.includes("print"));
  assert.ok(found.includes("hookfunction"));
  assert.equal(found.filter((label) => label === "myCustomHelper").length, 1);
  assert.equal(found.filter((label) => label === "print").length, 1);
});

test("luau completion does not repeat identifiers that are already built in", () => {
  const doc = "print(1) hookfunction(print)\nhoo";
  const state = makeState(doc);
  const result = luauCompletionSource({ state, pos: doc.length, explicit: false });
  assert.equal(result.options.filter((option) => option.label === "hookfunction").length, 1);
  assert.equal(result.options.filter((option) => option.label === "print").length, 1);
});

test("luau completion offers document words after a dot on an unknown receiver", () => {
  const doc = "local config = {}\nconfig.threshold = 1\nconfig.thr";
  const state = makeState(doc);
  const result = luauCompletionSource({ state, pos: doc.length, explicit: false });
  assert.ok(result.options.some((option) => option.label === "threshold"));
});
