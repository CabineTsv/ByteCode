// Exercises the editor glue (host.js, documents.js, extensions.js, languages.js) against a fake
// CodeMirror. It checks the bridge protocol and the document bookkeeping, not real editing behaviour.
import test from "node:test";
import assert from "node:assert/strict";
import { register } from "node:module";

register("../harness/loader.mjs", import.meta.url);

const posted = [];
const cssVariables = {};
const bodyClasses = new Set(["no-documents"]);
const contents = {};

globalThis.window = {
  AndroidHost: { postMessage: (json) => posted.push(JSON.parse(json)) },
  addEventListener() {},
};
globalThis.document = {
  documentElement: { style: { setProperty: (key, value) => { cssVariables[key] = value; } } },
  body: { classList: { toggle: (name, on) => (on ? bodyClasses.add(name) : bodyClasses.delete(name)) } },
  head: { appendChild() {} },
  getElementById: () => null,
  createElement: () => ({ remove() {} }),
};
globalThis.requestAnimationFrame = (callback) => callback();
globalThis.fetch = async (url) => {
  if (!(url in contents)) return { ok: false, status: 404, text: async () => "" };
  return { ok: true, status: 200, text: async () => contents[url] };
};

const { DocumentStore } = await import("../src/documents.js");
const { createHost, post, requestLint } = await import("../src/host.js");

const store = new DocumentStore({ parent: {}, post, requestLint });
createHost(store);
const dispatch = (command) => window.ByteCodeHost.dispatch(JSON.stringify(command));
const settle = () => new Promise((resolve) => setTimeout(resolve, 30));
const opened = (docId) => posted.find((message) => message.type === "documentOpened" && message.docId === docId);

// Module loading is slow when many test files run at once, so wait for the condition instead of sleeping.
async function waitFor(predicate, timeoutMs = 5000) {
  const started = Date.now();
  while (Date.now() - started < timeoutMs) {
    const value = predicate();
    if (value) return value;
    await new Promise((resolve) => setTimeout(resolve, 5));
  }
  throw new Error("timed out waiting for a condition");
}
const last = (type) => [...posted].reverse().find((message) => message.type === type);

test("opening a document shows it and reports it", async () => {
  contents["/docs/d1"] = "local x = 1\n";
  dispatch({ cmd: "openDocument", id: "d1", name: "a.luau", language: "luau", contentUrl: "/docs/d1", activate: true });
  await waitFor(() => opened("d1"));
  assert.deepEqual(opened("d1"), { type: "documentOpened", docId: "d1", dirty: false });
  assert.equal(store.activeId, "d1");
  assert.equal(bodyClasses.has("no-documents"), false);
  assert.equal(store.view.state.sliceDoc(), "local x = 1\n");
});

test("a missing document reports a failure instead of throwing", async () => {
  dispatch({ cmd: "openDocument", id: "d404", name: "gone.txt", language: "plaintext", contentUrl: "/docs/none", activate: false });
  const failure = await waitFor(() => posted.find((message) => message.type === "documentFailed"));
  assert.equal(failure.docId, "d404");
  assert.match(failure.message, /404/);
  assert.equal(store.docs.has("d404"), false);
});

test("editing flips the dirty flag; saving clears it only for the text that was written", async () => {
  store.view.dispatch({ changes: { from: 0, to: 0, insert: "-- hi\n" } });
  assert.deepEqual(last("dirty"), { type: "dirty", docId: "d1", dirty: true });

  dispatch({ cmd: "requestText", id: "d1", requestId: "r1" });
  assert.equal(last("text").text, "-- hi\nlocal x = 1\n");

  store.view.dispatch({ changes: { from: 0, to: 0, insert: "more " } }); // typed while the file was being written
  dispatch({ cmd: "markSaved", id: "d1", requestId: "r1" });
  assert.equal(store.docs.get("d1").dirty, true, "edits made during the save must stay unsaved");

  dispatch({ cmd: "requestText", id: "d1", requestId: "r2" });
  dispatch({ cmd: "markSaved", id: "d1", requestId: "r2" });
  assert.deepEqual(last("dirty"), { type: "dirty", docId: "d1", dirty: false });
});

test("switching tabs keeps each document's own state", async () => {
  contents["/docs/d2"] = "{}";
  dispatch({ cmd: "openDocument", id: "d2", name: "b.json", language: "json", contentUrl: "/docs/d2", activate: false });
  await waitFor(() => opened("d2"));
  assert.equal(store.activeId, "d1", "opening without activate must not steal the tab");

  const before = store.view.setStateCalls;
  dispatch({ cmd: "activateDocument", id: "d2" });
  assert.equal(store.activeId, "d2");
  assert.equal(store.view.setStateCalls, before + 1);
  assert.equal(store.view.state.sliceDoc(), "{}");

  dispatch({ cmd: "activateDocument", id: "d1" });
  assert.equal(store.view.state.sliceDoc(), "more -- hi\nlocal x = 1\n", "edits survive a round trip through another tab");
});

test("activation requested while a document is still loading is applied once it loads", async () => {
  contents["/docs/d3"] = "print(1)";
  dispatch({ cmd: "openDocument", id: "d3", name: "c.luau", language: "luau", contentUrl: "/docs/d3", activate: false });
  dispatch({ cmd: "activateDocument", id: "d3" }); // arrives before the fetch finished
  await waitFor(() => store.activeId === "d3");
  assert.equal(store.activeId, "d3");
});

test("restored text opens as an unsaved change on top of the saved baseline", async () => {
  contents["/docs/d4"] = "saved";
  contents["/docs/d4-restore"] = "recovered";
  dispatch({ cmd: "openDocument", id: "d4", name: "d.txt", language: "plaintext", contentUrl: "/docs/d4", restoreUrl: "/docs/d4-restore", activate: true });
  await waitFor(() => opened("d4"));
  assert.equal(opened("d4").dirty, true);
  assert.equal(store.view.state.sliceDoc(), "recovered");
});

test("settings, renames, reveal and actions do not throw", async () => {
  dispatch({ cmd: "applySettings", settings: { fontSize: 18, darkTheme: false, accentColor: "#e9627f", customFontUrl: "/fonts/custom.ttf?v=1" } });
  assert.equal(cssVariables["--bc-font-size"], "18px");
  assert.equal(cssVariables["--bc-accent"], "#e9627f");
  dispatch({ cmd: "applySettings", settings: { fontSize: 999, accentColor: "not a colour" } });
  assert.equal(cssVariables["--bc-font-size"], "40px", "font size is clamped");
  assert.equal(cssVariables["--bc-accent"], "#4ea1ff", "an invalid accent falls back to the default");

  dispatch({ cmd: "renameDocument", id: "d3", name: "c.json", language: "json" });
  dispatch({ cmd: "revealPosition", id: "d3", line: 1, column: 3, focus: true });
  dispatch({ cmd: "insertText", text: "x" });
  dispatch({ cmd: "action", name: "undo" });
  dispatch({ cmd: "focus" });
  dispatch({ cmd: "noSuchCommand" });
  await settle();
  assert.equal(last("log").level, "warn");
  assert.equal(store.docs.get("d3").name, "c.json");
});

test("lint requests are forwarded and answered", async () => {
  const pending = requestLint("d1", "luau", "local x =");
  const request = last("lint");
  assert.equal(request.text, "local x =");
  dispatch({ cmd: "lintResult", requestId: request.requestId, items: [{ line: 1, column: 10, message: "boom" }] });
  const items = await pending;
  assert.equal(items[0].message, "boom");
});

test("closing documents releases them and the empty state returns", async () => {
  for (const id of ["d1", "d2", "d3", "d4", "d404"]) dispatch({ cmd: "closeDocument", id });
  assert.equal(store.docs.size, 0);
  assert.equal(store.activeId, null);
  assert.equal(bodyClasses.has("no-documents"), true);
  await settle();
});
