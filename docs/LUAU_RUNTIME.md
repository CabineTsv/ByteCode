# Luau support

## What "standalone Luau" means

ByteCode runs Luau scripts on the **official Luau VM** (https://github.com/luau-lang/luau), compiled
for Android with the NDK. This is the Luau *language* and its standard libraries — the same engine
Roblox uses — but **not Roblox itself**.

Available: syntax, types (erased at runtime), `print`, `warn`, `error`, `pcall`, `string`, `table`,
`math`, `os` (clock, date, time, difftime), `coroutine`, `utf8`, `bit32`, `buffer`, `debug`,
`typeof`, metatables.

Not available, and not faked:

* Everything the Roblox engine provides: `game`, `workspace`, `script`, `Instance`, `Enum`, `Vector3`,
  `CFrame`, `Color3`, `UDim2`, `task`, `wait`, `spawn`, `tick`, ... Reading one of these raises an
  explanatory error naming the global instead of failing later with "attempt to index nil".
* `require` (there is no module loader). Put the code in one file.
* `io`, `os.execute`, files, network. The sandbox only gives scripts the language libraries.
* A scheduler: yielding at the top level of the script ends the run with a warning.

`.lua` files can also be run with this VM; Lua 5.x specifics (`goto`, `load`, `io`, integer division
rules) differ from Luau.

## Execution model

1. `LuauRuntime.run()` (Kotlin) hands the source to `nativeRun` on a dedicated thread.
2. A new Luau state is created with a limiting allocator (256 MB), standard libraries are opened, the host
   functions (`print`, `warn`, the Roblox-global trap) are installed, then `luaL_sandbox` seals the globals.
3. The source is compiled with `luau_compile` (optimisation level 1, debug level 1) and loaded.
4. The script runs on a sandboxed coroutine so that a runtime error still has its stack for a traceback.
5. An interrupt callback runs at every function call and loop iteration. It raises an error when **Stop**
   is pressed or the timeout (Settings → Run timeout, default 15 s) expires, even inside `pcall` loops.
6. `print`/`warn` output is buffered (flushed every 30 ms or 4 KB) and sent to the console through JNI. Total
   output is capped at 4 MB.
7. Errors are printed as `file.luau:LINE: message` plus a traceback; the console makes those lines
   clickable and jumps to the line.

## Diagnostics

While editing a `.luau` file the editor asks the native library for the official parser's syntax errors
(debounced, up to 50 per pass). They appear as squiggles, in the gutter, and in the Problems list. This is
**syntax checking only**: Luau's type checker (`Luau.Analysis`) is not linked, so type errors and lint
warnings are not reported.

## Highlighting and completion

`editor/web/src/luau/tokenizer.js` is a dedicated tokenizer (not the Lua mode). It understands comments and
long strings, interpolated strings, numeric literals with `_`, compound assignment, `continue`, type
annotations, generics, unions/intersections/optionals, casts, `type`/`export type`, and attributes, and
gives Roblox globals, Roblox methods, Luau built-ins and types their own colours. Type handling is a
heuristic over single lines plus a little state, not a full parser.

Completion offers keywords, built-ins, library members, Roblox globals, snippets, document words, service
names inside `GetService("...")`, class names inside `Instance.new("...")`, and members of the likely
class of an expression (`game:GetService("Players").LocalPlayer.` or `local part = Instance.new("Part")`
followed by `part.`). The Roblox data is a curated subset in `roblox-api.js`.

## Build details

* `luau/LUAU_VERSION` pins the Luau release tag (default `0.680`). Override with `-Pbytecode.luauTag=<tag>`.
  If the tag cannot be cloned, the newest numeric tag is used instead and a warning is printed.
* Only `Luau.VM`, `Luau.Compiler` and `Luau.Ast` are built (`EXCLUDE_FROM_ALL` keeps everything else out).
* The library is linked with 16 KB page alignment for current Android versions.
* If the native library cannot be loaded (for example on an unsupported ABI) the app keeps working and
  simply does not offer Luau execution or Luau syntax diagnostics.
