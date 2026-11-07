import test from "node:test";
import assert from "node:assert/strict";
import {
  parseReceiver, resolveType, inferVariableTypes, analyzeCursor, membersForType, classMembers,
} from "../src/luau/analysis.js";

const names = (members) => members.map((m) => m.label);

test("parseReceiver reads call chains", () => {
  const segments = parseReceiver('game:GetService("Players").LocalPlayer');
  assert.deepEqual(segments.map((s) => s.name), ["game", "GetService", "LocalPlayer"]);
  assert.equal(segments[1].args, '"Players"');
  assert.deepEqual(parseReceiver("part").map((s) => s.name), ["part"]);
  assert.deepEqual(parseReceiver("x + part.Position").map((s) => s.name), ["part", "Position"]);
  assert.deepEqual(parseReceiver("("), []);
});

test("resolves services, locals and chained members", () => {
  assert.deepEqual(resolveType(parseReceiver('game:GetService("Players")')), { kind: "class", name: "Players" });
  assert.deepEqual(resolveType(parseReceiver('game:GetService("Players").LocalPlayer.Character')), { kind: "class", name: "Model" });
  assert.deepEqual(resolveType(parseReceiver("game.Workspace.CurrentCamera")), { kind: "class", name: "Camera" });
  assert.deepEqual(resolveType(parseReceiver("workspace.CurrentCamera")), { kind: "class", name: "Camera" });
  assert.deepEqual(resolveType(parseReceiver('Instance.new("Part")')), { kind: "class", name: "Part" });
  assert.deepEqual(resolveType(parseReceiver('character:FindFirstChildOfClass("Humanoid")'), new Map([["character", "Model"]])), { kind: "class", name: "Humanoid" });
  assert.deepEqual(resolveType(parseReceiver("math")), { kind: "library", name: "math" });
  assert.deepEqual(resolveType(parseReceiver("Enum.Material")), { kind: "enumCategory", name: "Material" });
  assert.deepEqual(resolveType(parseReceiver("CFrame.new().Position")), { kind: "datatypeInstance", name: "Vector3" });
  assert.equal(resolveType(parseReceiver("unknownThing")), null);
});

test("infers variable types from declarations and annotations", () => {
  const code = [
    'local Players = game:GetService("Players")',
    "local player = Players.LocalPlayer",
    'local part = Instance.new("Part")',
    "local clone = part:Clone()",
    "local cam: Camera = nil",
    "local function onTouch(hit: BasePart, who: Player)",
    "local n = 5",
    'local weird = foo() or bar',
  ].join("\n");
  const types = inferVariableTypes(code);
  assert.equal(types.get("Players"), "Players");
  assert.equal(types.get("player"), "Player");
  assert.equal(types.get("part"), "Part");
  assert.equal(types.get("clone"), "Part");
  assert.equal(types.get("cam"), "Camera");
  assert.equal(types.get("hit"), "BasePart");
  assert.equal(types.get("who"), "Player");
  assert.equal(types.has("n"), false);
  assert.equal(types.has("weird"), false);
});

test("members include inherited ones and respect : versus .", () => {
  const part = names(classMembers("Part"));
  for (const expected of ["Anchored", "Touched", "Clone", "Destroy", "GetPivot", "Shape", "Name", "Parent"]) {
    assert.ok(part.includes(expected), expected);
  }
  const methodsOnly = names(membersForType({ kind: "class", name: "Part" }, ":"));
  assert.ok(methodsOnly.includes("Destroy"));
  assert.ok(!methodsOnly.includes("Anchored"));
  assert.ok(names(membersForType({ kind: "library", name: "math" }, ".")).includes("floor"));
  assert.ok(names(membersForType({ kind: "enumRoot" }, ".")).includes("KeyCode"));
  assert.ok(names(membersForType({ kind: "enumCategory", name: "Material" }, ".")).includes("Plastic"));
});

test("analyzeCursor classifies the text before the cursor", () => {
  assert.deepEqual(analyzeCursor('local s = game:GetService("Pl'), { kind: "string-arg", fn: "GetService", prefix: "Pl" });
  assert.deepEqual(analyzeCursor('Instance.new("'), { kind: "string-arg", fn: "Instance.new", prefix: "" });
  assert.deepEqual(analyzeCursor("part.Anc"), { kind: "member", separator: ".", prefix: "Anc", receiver: "part" });
  assert.deepEqual(analyzeCursor("part:"), { kind: "member", separator: ":", prefix: "", receiver: "part" });
  assert.equal(analyzeCursor('local s = "a" .. '.trimEnd() + "."), null);
  assert.deepEqual(analyzeCursor("local x: Pa"), { kind: "type", prefix: "Pa" });
  assert.deepEqual(analyzeCursor("local y = x :: Pa"), { kind: "type", prefix: "Pa" });
  assert.deepEqual(analyzeCursor("local fo"), { kind: "word", prefix: "fo" });
  assert.deepEqual(analyzeCursor(""), { kind: "word", prefix: "" });
});
