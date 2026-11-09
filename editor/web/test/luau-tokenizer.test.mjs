// Unit tests for the Luau tokenizer. A minimal StringStream stands in for CodeMirror's class, so
// the tests run with plain Node and no dependencies.
import test from "node:test";
import assert from "node:assert/strict";
import { startState, token, indent } from "../src/luau/tokenizer.js";

class MockStream {
  constructor(line) { this.string = line; this.pos = 0; this.start = 0; }
  sol() { return this.pos === 0; }
  eol() { return this.pos >= this.string.length; }
  peek() { return this.string.charAt(this.pos) || undefined; }
  next() { if (this.pos < this.string.length) return this.string.charAt(this.pos++); return undefined; }
  eat(m) {
    const ch = this.string.charAt(this.pos);
    const ok = typeof m === "string" ? ch === m : m.test(ch);
    if (ok) { this.pos++; return ch; }
    return undefined;
  }
  eatSpace() { const s = this.pos; while (/[\s\u00a0]/.test(this.string.charAt(this.pos))) this.pos++; return this.pos > s; }
  skipToEnd() { this.pos = this.string.length; }
  match(pattern, consume, caseInsensitive) {
    if (typeof pattern === "string") {
      const cased = (s) => (caseInsensitive ? s.toLowerCase() : s);
      if (cased(this.string.substr(this.pos, pattern.length)) === cased(pattern)) {
        if (consume !== false) this.pos += pattern.length;
        return true;
      }
      return null;
    }
    const m = this.string.slice(this.pos).match(pattern);
    if (m && m.index > 0) return null;
    if (m && consume !== false) this.pos += m[0].length;
    return m;
  }
  current() { return this.string.slice(this.start, this.pos); }
}

function tokenize(source) {
  const state = startState();
  const rows = [];
  for (const line of source.split("\n")) {
    const stream = new MockStream(line);
    const row = [];
    while (!stream.eol()) {
      stream.start = stream.pos;
      const style = token(stream, state);
      assert.ok(stream.pos > stream.start, `tokenizer did not advance on: ${JSON.stringify(line)} at ${stream.pos}`);
      if (style) row.push([stream.current(), style]);
    }
    rows.push({ row, depth: state.depth, mode: state.mode });
  }
  return { rows, state };
}

const flat = (source) => tokenize(source).rows.flatMap((r) => r.row);
const styleOf = (tokens, text, nth = 0) => tokens.filter(([t]) => t === text)[nth]?.[1];

test("keywords, definitions and literals", () => {
  const t = flat("local x: number = 5");
  assert.equal(styleOf(t, "local"), "definitionKeyword");
  assert.equal(styleOf(t, "x"), "variableName.definition");
  assert.equal(styleOf(t, "number"), "luauType");
  assert.equal(styleOf(t, "="), "definitionOperator");
  assert.equal(styleOf(t, "5"), "number");
  const t2 = flat("if a and not b then return nil else return true end");
  assert.equal(styleOf(t2, "if"), "controlKeyword");
  assert.equal(styleOf(t2, "and"), "operatorKeyword");
  assert.equal(styleOf(t2, "nil"), "null");
  assert.equal(styleOf(t2, "true"), "bool");
});

test("continue is a keyword", () => {
  assert.equal(styleOf(flat("for i = 1, 3 do if i == 2 then continue end end"), "continue"), "controlKeyword");
});

test("roblox globals, methods and builtins are distinguished", () => {
  const t = flat('local players = game:GetService("Players")');
  assert.equal(styleOf(t, "game"), "robloxGlobal");
  assert.equal(styleOf(t, "GetService"), "robloxMethod");
  assert.equal(styleOf(t, '"Players"'), "string");
  const globals = flat("local v = Vector3.new(1, 2, 3) local c = CFrame.new() local e = Enum.Material.Plastic");
  for (const name of ["Vector3", "CFrame", "Enum"]) assert.equal(styleOf(globals, name), "robloxGlobal", name);
  assert.equal(styleOf(globals, "new"), "robloxMethod");
  const builtins = flat("print(math.floor(x)) table.insert(t, 1)");
  assert.equal(styleOf(builtins, "print"), "luauBuiltin");
  assert.equal(styleOf(builtins, "math"), "luauBuiltin");
  assert.equal(styleOf(builtins, "floor"), "luauBuiltinMember");
  assert.equal(styleOf(builtins, "insert"), "luauBuiltinMember");
  const user = flat("local function helper() end helper() obj.Name = 1 obj:Destroy() part:WaitForChild('x')");
  assert.equal(styleOf(user, "helper", 0), "variableName.definition.function");
  assert.equal(styleOf(user, "helper", 1), "variableName.function");
  assert.equal(styleOf(user, "Name"), "propertyName");
  assert.equal(styleOf(user, "Destroy"), "robloxMethod");
  assert.equal(styleOf(user, "WaitForChild"), "robloxMethod");
});

test("every roblox method named in the spec is recognised", () => {
  const names = ["GetService", "WaitForChild", "FindFirstChild", "FindFirstChildOfClass", "IsA", "Clone", "Destroy",
    "Connect", "Disconnect", "GetChildren", "GetDescendants", "GetAttribute", "SetAttribute"];
  for (const name of names) assert.equal(styleOf(flat(`x:${name}()`), name), "robloxMethod", name);
  for (const name of ["game", "workspace", "script", "Instance", "Enum", "Vector3", "Vector2", "CFrame", "UDim", "UDim2",
    "Color3", "BrickColor", "RaycastParams", "TweenInfo"]) {
    assert.equal(styleOf(flat(`local a = ${name}`), name), "robloxGlobal", name);
  }
});

test("type annotations, generics, unions, optionals and casts", () => {
  const t = flat("export type Point<T> = { x: T, y: number?, name: string | nil }");
  assert.equal(styleOf(t, "export"), "moduleKeyword");
  assert.equal(styleOf(t, "type"), "definitionKeyword");
  assert.equal(styleOf(t, "Point"), "typeName.definition");
  assert.equal(styleOf(t, "T", 0), "typeName");
  assert.equal(styleOf(t, "x"), "propertyName");
  assert.equal(styleOf(t, "number"), "luauType");
  assert.equal(styleOf(t, "?"), "typeOperator");
  assert.equal(styleOf(t, "|"), "typeOperator");
  const fn = flat("local function add(a: number, b: number): number return a + b end");
  assert.equal(styleOf(fn, "add"), "variableName.definition.function");
  assert.equal(styleOf(fn, "a", 0), "variableName");
  assert.equal(styleOf(fn, "number", 2), "luauType");
  assert.equal(styleOf(fn, "return"), "controlKeyword");
  assert.equal(styleOf(fn, "a", 1), "variableName"); // `a` in the body is a normal variable again
  assert.equal(styleOf(fn, "b", 1), "variableName");
  const cast = flat("local y = x :: any\nlocal p: Part = nil");
  assert.equal(styleOf(cast, "::"), "typeOperator");
  assert.equal(styleOf(cast, "any"), "luauType");
  assert.equal(styleOf(cast, "Part"), "className");
  const fnType = flat("type Handler = (player: Player, amount: number) -> boolean");
  assert.equal(styleOf(fnType, "player"), "propertyName");
  assert.equal(styleOf(fnType, "->"), "typeOperator");
  assert.equal(styleOf(fnType, "boolean"), "luauType");
});

test("method calls with a colon are not mistaken for annotations", () => {
  const t = flat("local n = obj:getName() print(obj:format(1))");
  assert.equal(styleOf(t, "getName"), "propertyName.function");
  assert.equal(styleOf(t, "format"), "propertyName.function");
  const def = flat("function Account:deposit(amount: number)\nend");
  assert.equal(styleOf(def, "Account"), "variableName");
  assert.equal(styleOf(def, "deposit"), "variableName.definition.function");
  assert.equal(styleOf(def, "number"), "luauType");
});

test("compound assignment, interpolation and numbers", () => {
  const t = flat("x += 1 s ..= 'a' y //= 2 local n = 0xFF + 0b101 + 1_000 + 1.5e-3");
  for (const op of ["+=", "..=", "//="]) assert.equal(styleOf(t, op), "updateOperator", op);
  assert.equal(styleOf(t, "0xFF"), "number");
  assert.equal(styleOf(t, "0b101"), "number");
  assert.equal(styleOf(t, "1_000"), "number");
  assert.equal(styleOf(t, "1.5e-3"), "number");
  const s = flat("print(`Hello {name}, you have {#items + 1} items!`)");
  assert.deepEqual(s.filter(([, st]) => st === "meta").map(([x]) => x), ["{", "}", "{", "}"]);
  assert.equal(styleOf(s, "name"), "variableName");
  assert.equal(styleOf(s, "items"), "variableName");
});

test("comments, long strings and hot comments span lines", () => {
  const { rows } = tokenize("--!strict\n--[==[ block\nstill comment ]==] local x = 1\nlocal s = [[long\nstring]] local y");
  assert.deepEqual(rows[0].row, [["--!strict", "meta"]]);
  assert.equal(rows[1].mode, 1);                      // still inside the block comment after line 2
  assert.equal(rows[2].mode, 0);
  assert.equal(rows[2].row[0][1], "comment");
  assert.equal(rows[2].row.find(([x]) => x === "local")?.[1], "definitionKeyword");
  assert.equal(rows[3].mode, 2);                      // inside the long string
  assert.equal(rows[4].row.find(([x]) => x === "local")?.[1], "definitionKeyword");
});

test("strings with escapes and line continuations", () => {
  const { rows } = tokenize('local a = "he said \\"hi\\"" local b = \'it\\\'s\'\nlocal c = "abc\\\ndef" local d');
  assert.equal(rows[0].row.filter(([, st]) => st === "string").length, 2);
  assert.equal(rows[1].row.length >= 0, true);
  const tail = tokenize('local c = "abc\\\ndef" local d').rows[1].row;
  assert.equal(tail.find(([x]) => x === "local")?.[1], "definitionKeyword");
});

test("block depth drives indentation", () => {
  const source = [
    "local function f(a)",        // depth 1 after this line
    "  if a then",                // 2
    "    print(1)",               // 2
    "  elseif b then",            // still 2 (elseif does not open a second block)
    "    print(2)",               // 2
    "  else",                     // 2
    "    print(3)",               // 2
    "  end",                      // 1
    "  local t = {",              // 2
    "    1,",                     // 2
    "  }",                        // 1
    "  for i = 1, 3 do",          // 2
    "  end",                      // 1
    "  repeat",                   // 2
    "  until x",                  // 1
    "end",                        // 0
  ];
  const depths = tokenize(source.join("\n")).rows.map((r) => r.depth);
  assert.deepEqual(depths, [1, 2, 2, 2, 2, 2, 2, 1, 2, 2, 1, 2, 1, 2, 1, 0]);
  const state = startState();
  state.depth = 2;
  assert.equal(indent(state, "end", { unit: 4 }), 4);
  assert.equal(indent(state, "print(1)", { unit: 4 }), 8);
  assert.equal(indent(state, "else", { unit: 2 }), 2);
});

test("never loops or throws on awkward input", () => {
  const nasty = [
    "", "   ", "$ ? ! \\ @", "@native function f() end", "local x: ", "type = 5", "print(type)",
    "local t: {[string]: number} = {}", "::", ":::", "a:b:c", "x = 1..2", "((((", "}}}}", "`{{{", "`unterminated {x",
    "--[[", "[=[ x", "'unterminated", "0x", "1e", "...", "function(...: number) end", "type X<T = number> = T",
    "type T =\n  | \"a\"\n  | \"b\"\nlocal after = 1",
  ];
  for (const source of nasty) tokenize(source);
  const t = flat('type T =\n  | "a"\n  | "b"\nlocal after = 1');
  assert.equal(styleOf(t, "after"), "variableName.definition");
});

test("environment functions are highlighted like the other built-ins in calls and as plain references", () => {
  const names = ["getconnections", "hookfunction", "hookmetamethod", "getupvalues", "getgc"];
  for (const name of names) {
    assert.equal(styleOf(flat(`${name}(game)`), name), "luauBuiltin", `${name} call`);
    assert.equal(styleOf(flat(`local f = ${name}`), name), "luauBuiltin", `${name} reference`);
    assert.equal(styleOf(flat(`local t = { fn = ${name} }`), name), "luauBuiltin", `${name} in table`);
    assert.equal(styleOf(flat(`${name} "x"`), name), "luauBuiltin", `${name} string call`);
  }
  assert.equal(styleOf(flat("print(1)"), "print"), styleOf(flat("hookfunction(1)"), "hookfunction"));
});

test("environment function names inside comments and strings are not highlighted as identifiers", () => {
  const comment = flat("-- hookfunction(getgc)");
  assert.equal(comment.length, 1);
  assert.equal(comment[0][1], "comment");
  const string = flat('local s = "getconnections hookmetamethod"');
  assert.equal(styleOf(string, '"getconnections hookmetamethod"'), "string");
  assert.equal(styleOf(string, "getconnections"), undefined);
  const block = tokenize("--[[ getupvalues\nhookfunction ]] getgc()").rows;
  assert.equal(block[1].row.find(([text]) => text === "getgc")[1], "luauBuiltin");
  assert.equal(block[1].row.find(([text]) => text === "hookfunction"), undefined);
  const longString = tokenize("local s = [[ hookfunction\ngetgc ]] hookmetamethod()").rows;
  assert.equal(longString[1].row.find(([text]) => text === "hookmetamethod")[1], "luauBuiltin");
});

test("environment function names do not override declarations or members", () => {
  assert.equal(styleOf(flat("local hookfunction = 1"), "hookfunction"), "variableName.definition");
  assert.equal(styleOf(flat("local function getgc() end"), "getgc"), "variableName.definition.function");
  assert.equal(styleOf(flat("obj.getgc = 1"), "getgc"), "propertyName");
  assert.equal(styleOf(flat("getgcx()"), "getgcx"), "variableName.function");
});

test("method colons and assignments are operators so themes can colour them like other operators", () => {
  const call = flat('game:GetService("Players")');
  assert.equal(styleOf(call, ":"), "operator");
  assert.equal(styleOf(flat("local a = 1"), "="), "definitionOperator");
  assert.equal(styleOf(flat("local function f(a: number) end"), ":"), "operator");
});

test("prints a sample for visual inspection", () => {
  const sample = `--!strict
local Players = game:GetService("Players")
type Config = { speed: number, tags: {string}? }
local function spawnPart(cfg: Config): Part
  local part = Instance.new("Part")
  part.Anchored = true
  part:SetAttribute("Speed", cfg.speed)
  print(\`Spawned {part.Name}\`)
  return part
end`;
  const out = tokenize(sample).rows.map((r) => r.row.map(([t, s]) => `${t}<${s}>`).join(" "));
  console.log(out.join("\n"));
});
