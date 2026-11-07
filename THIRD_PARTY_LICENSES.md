# Third-party software

ByteCode itself is released under the MIT License (see `LICENSE`). It builds on the following
projects. None of their source code is copied into this repository: the JavaScript packages and the
Luau sources are downloaded when you build, and the Android libraries come from Google's Maven
repository. Their licence texts ship inside the downloaded packages and inside the built APK.

| Component | Used for | Licence |
|---|---|---|
| [CodeMirror 6](https://codemirror.net) (`@codemirror/*`) | The editor engine, language packs, search, autocomplete, lint | MIT |
| [Lezer](https://lezer.codemirror.net) (`@lezer/*`) | Parsers behind the CodeMirror language packs, highlight tags | MIT |
| `@codemirror/legacy-modes` | Highlighting for Kotlin, C#, Ruby, Go, Shell, Lua, YAML | MIT |
| [esbuild](https://esbuild.github.io) | Bundles the editor (build time only, not shipped) | MIT |
| [Luau](https://github.com/luau-lang/luau) | The Luau VM, compiler and parser behind the runtime and the syntax diagnostics | MIT (Roblox Corporation; contains code derived from Lua 5.x, MIT, Lua.org / PUC-Rio) |
| AndroidX (core, appcompat, activity, lifecycle, recyclerview, drawerlayout, preference, webkit) | Android UI and WebView support | Apache License 2.0 |
| [Material Components for Android](https://github.com/material-components/material-components-android) | Material 3 widgets and theme | Apache License 2.0 |
| Kotlin standard library, kotlinx.coroutines | Language runtime | Apache License 2.0 |

## Notes

* The Luau tag that was actually built is recorded in `luau/native/third_party/luau/.bytecode-tag`
  after the first build and shown in **Settings → About**.
* The Roblox API data used for completion (`editor/web/src/luau/roblox-api.js`) is a small curated list
  of public class, member and enum names that was typed by hand. It does not contain Roblox
  documentation text. "Roblox" is a trademark of Roblox Corporation; ByteCode is not affiliated with it.
* The launcher icon and the file-type badges are original artwork drawn in code and vector files.
