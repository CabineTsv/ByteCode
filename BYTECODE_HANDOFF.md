# ByteCode — Development Handoff

> Read this file first in any new chat about ByteCode. It describes the **real** state of the project at snapshot `6b2c80b07189c0b7` and keeps three things apart: what *exists in source*, what was *actually verified*, and what is only *planned or assumed*. Nothing here claims that the app has been built or run, because it has not.

## 0. Snapshot identity

| Item | Value |
|---|---|
| Project | ByteCode — Android code editor inspired by VS Code (applicationId `dev.bytecode.ide`, versionName 1.0.0, versionCode 1; namespaces `dev.bytecode.app`, `.editor`, `.luau`) |
| Snapshot id | `6b2c80b07189c0b7` = first 16 hex digits of the SHA-256 of `MANIFEST.sha256` |
| Created | in the chat of 2026-11-05 (the sandbox clock read 2026-11-04) |
| Files | 149 listed in `MANIFEST.sha256`; the zip additionally contains `MANIFEST.sha256` and this file |
| Size | 5,036 lines of Kotlin in 33 files, plus C++, JavaScript, XML, Gradle (see section 7) |
| Authoring environment | Linux sandbox with Node v22.22.2, g++ 13.3.0, Python 3.12.3 and a JRE (no JDK). **No network, no Android SDK/NDK, no Gradle, no Kotlin compiler, no emulator or device.** |
| Git | The project is **not** a git repository and has never been pushed to GitHub |

## 1. Where the code actually is — preservation status

| Location | What is there | State |
|---|---|---|
| Claude sandbox `/home/claude/ByteCode` | The complete project tree (this snapshot) | Exists now. **Temporary** — it may be gone in a new chat. |
| `/mnt/user-data/outputs/ByteCode.zip` and `BYTECODE_HANDOFF.md` | The same snapshot as a zip, and this file | Created and offered as downloads in the chat that produced this handoff. Whether they are still reachable from another chat is **not guaranteed**. |
| Claude **Project files** | Unknown. I cannot list or upload Project files from a chat. | **Nothing was saved there by me.** You must upload the files yourself. |
| **GitHub** | No repository exists for this project, nothing was committed or pushed (no GitHub tool was available) | **Not preserved there.** |
| Claude memory | Short notes: the project scope/rules and the existence of this handoff file | Saved; contains **no source code**. |
| Earlier chats | I could not open any. The sandbox started this session empty (no uploads, no transcripts, no earlier handoff). | Their content is not carried over. |

**To keep the code, do this once (your action, not done by me):**

1. Download `ByteCode.zip` and `BYTECODE_HANDOFF.md` from the chat that produced them.
2. **Recommended:** create a GitHub repository, unzip, `git init`, commit and push (`gradlew` keeps its executable bit inside the zip). Then run the workflow (section 13).
3. Upload `BYTECODE_HANDOFF.md` to the Claude Project files. Also upload `ByteCode.zip` (or the unzipped sources) so a new chat can read the real files; a new chat can only unpack it if it has a code-execution tool. If upload size is limited, upload the handoff plus the source folders `app/`, `editor/`, `luau/` as separate files.
4. In a new chat, check that the files match this handoff with `sha256sum -c MANIFEST.sha256` (section 16).

## 2. Architecture and technology stack

Three Gradle modules (details and the Kotlin<->JavaScript protocol: `docs/ARCHITECTURE.md`):

```
:app     Native UI (Views, Material 3), files, tabs, console, runners, settings      Kotlin
 ├── :editor  CodeMirror 6 in one WebView + bridge (Kotlin host, JS bundle in editor/web)  Kotlin + JS
 └── :luau    Official Luau VM/compiler/parser via JNI (libbytecode_luau.so)               C++ + Kotlin
```

- **Editor:** one WebView and one CodeMirror `EditorView` for all tabs; each file has its own `EditorState` (history, selection, folds); switching tabs swaps states and restores scroll. Bundled at build time with esbuild (code-split language packs) into `editor/src/main/assets/editor/dist`; served from assets through `WebViewAssetLoader`; no network, no INTERNET permission. Document text reaches the page as an HTTP response (`/docs/<id>`).
- **Files:** Storage Access Framework via `DocumentsContract` (tree URI with persistable permission); no storage permission. Saves use mode `wt`; non-UTF-8 files open read-only; BOM and CRLF are preserved; recovery copies in `files/recovery/`.
- **Luau:** the official Luau repository is cloned during the Gradle build (tag in `luau/LUAU_VERSION`, fallback to the newest tag); the JNI library links `Luau.VM`, `Luau.Compiler`, `Luau.Ast`. Standalone Luau only — Roblox globals raise an explanatory error.
- **Run:** `RunManager` + `LuauRunner` (native), `JavaScriptRunner` (hidden WebView), `ShellRunner` (`/system/bin/sh`).
- **CI:** `.github/workflows/build.yml` — quick tests, then `assembleDebug`/`assembleRelease`, APK artifact, release attachment.

## 3. Current development stage

**Source-complete, pre-first-build.** Every area of the specification has an implementation in source (the specification's stages 1–7 were all written), but **nothing has been compiled by Gradle, Kotlin, the NDK or AAPT, and nothing has run on a device.** By the owner's own rule ("do not move on while the current stage has unresolved critical issues") the real position is *Stage 1: make it build and launch*. Only logic that runs in plain Node/g++ was tested (section 11).

## 4. Feature status

Legend: **V** = verified by an automated check that actually ran; **S** = implemented in source, not verified on Android; **–** = not implemented.

| Area | Feature | Status | Where |
|---|---|:-:|---|
| Explorer | Lazy tree, expand/collapse, workspace folder persisted, expanded folders remembered | S | `ui/ExplorerController.kt`, `workspace/WorkspaceStore.kt` |
| Explorer | Create, rename, delete, copy/cut/paste, import, export (real SAF operations) | S | `workspace/WorkspaceFileSystem.kt` |
| Explorer | Language badges, long-press menu, adjustable width (wide layout only) | S | `ui/FileIconDrawable.kt`, `MainActivity.kt` |
| Editor | Tabs, unsaved dots, per-tab state kept when switching, no WebView reload | S (JS glue **V** against a fake CodeMirror) | `editor/web/src/documents.js`, `ui/TabStrip.kt` |
| Editor | Highlighting, line numbers, active line, bracket matching/closing, indentation, folding, comments, undo/redo, find/replace, go to line | S | `extensions.js`, CodeMirror packages |
| Editor | Autocomplete: language packs + document words; Luau-specific | S (Luau analysis **V**) | `words.js`, `luau/completion.js`, `luau/analysis.js` |
| Editor | Settings (font, size, tab, wrap, numbers, brackets, quotes, indent, folding, themes, accent, syntax palette), imported font | S (clamping/CSS vars **V**) | `SettingsRepository.kt`, `extensions.js`, `themes.js` |
| Editor | Autosave (off/delay/leave), crash-safe recovery copies, session restore of tabs | S | `documents/DocumentManager.kt`, `RecoveryStore.kt` |
| Editor | Extra-keys bar, hardware shortcuts, caret kept visible with the keyboard | S | `ui/ExtraKeysBar.kt`, `extensions.js` |
| Console | Output/Problems/Search/Log channels, resize, collapse, clear, clickable locations | S | `ui/ConsolePanel.kt` |
| Luau | Dedicated tokenizer: types, generics, interpolation, `continue`, casts, Roblox globals/methods | V (12 tests) | `luau/tokenizer.js` |
| Luau | Syntax diagnostics from the official parser; Problems list | S | `luau_diagnostics.cpp`, `LuauRuntime.kt` |
| Luau | Native runtime: print/warn capture, errors with traceback, Stop, timeout, memory limit, Roblox-global error | S | `luau_runtime.cpp` |
| Luau | UTF-8 <-> UTF-16 conversion used by JNI | V (sanitizers) | `text_convert.hpp` |
| Run | JavaScript (hidden WebView) and Shell runners; Run button only when a runner exists | S | `run/` |
| Navigation | Command palette, go to file, recent files/folders, search in files (plain/case/regex) | S | `ui/PaletteDialog.kt`, `MainActivity.kt` |
| UI | Phone drawer vs docked explorer, dark/light/system, 8 accents + custom hex | S | `MainActivity.kt`, `res/` |
| Build | Gradle modules, esbuild bundle task, Luau clone task, CMake, workflow | S (never run; YAML parses) | `*/build.gradle.kts`, `build.yml` |
| – | Minimap; breadcrumbs | – | documented as not implemented |
| – | Luau type checking, lint warnings; workspace-aware `require` | – | planned |
| – | Running Python / C / C++ / TypeScript; terminal; Git; file watching | – | planned / not feasible without extra runtimes |
| – | README screenshots | – | none exist; the app has not been run |

## 5. Established requirements and decisions

**Binding (from the owner; full text in `docs/ORIGINAL_SPEC.md` and the Project Instructions):** Kotlin; Android SDK; Gradle; Material 3; Storage Access Framework (avoid `MANAGE_EXTERNAL_STORAGE`); CodeMirror 6 in a local WebView, offline, with a Kotlin<->JS bridge (Monaco only if the owner approves); official Luau via NDK/JNI, independent of the editor; `.luau` is first-class and the standalone runtime must **never pretend to be Roblox**; GitHub + `.github/workflows/build.yml` with `workflow_dispatch`, push and release builds and APK artifacts; no placeholder features, no fake file operations, no buttons that do nothing; never claim builds/tests that did not run; no unnecessary dependencies; no giant source files; never minify source; deliver full files; make WIP features failsafe; preserve earlier decisions across chats.

**Decisions made while building (change only with a reason):**

- Multi-module (`:app`, `:editor`, `:luau`); `:editor` and `:luau` do not depend on `:app`.
- Android **Views** + Material 3, not Jetpack Compose (smaller API surface for blind authoring, no compiler plugin).
- `org.json` for the bridge and `SharedPreferences` for settings; no serialization/DataStore dependency.
- `DocumentsContract` directly instead of `DocumentFile` (one query lists a folder; `DocumentFile` queries per property).
- One WebView and one `EditorView`; per-document `EditorState` swapped with `setState`; settings in compartments; CSS variables for fonts/accent.
- Document text is served to the page as a one-shot HTTP response, not injected as a JS string.
- esbuild with code splitting; unminified by default (`BYTECODE_MINIFY=1` for release). Dependencies are `^6` ranges because no lockfile could be generated offline.
- Languages: official CodeMirror packs where they exist; `@codemirror/legacy-modes` for Kotlin, C#, Ruby, Go, Shell, Lua, YAML (chosen over `lang-go`/`lang-yaml` because the legacy modes are certain to exist); a **custom StreamLanguage tokenizer** for Luau (not the Lua mode).
- Luau diagnostics come from `Luau::Parser` (multiple errors with columns), not from `luau_load` messages; type checking is deliberately not linked.
- Luau is cloned at build time (pinned tag `0.680`, fallback to the newest numeric tag) rather than vendored or a submodule.
- Native runtime: one run at a time; custom allocator (256 MB limit); interrupt callback keeps raising after Stop so `pcall` loops cannot swallow it; Roblox-only globals trapped through the globals metatable before `luaL_sandbox`.
- No `INTERNET` permission and no permissions at all; targetSdk 34 (avoids forced edge-to-edge), compileSdk 35, minSdk 26.
- `MainActivity` handles orientation/size changes itself (no editor reload); it does **not** handle `uiMode`, so a day/night switch recreates the activity and the session is restored from saved tabs and recovery copies.
- Files that are not valid UTF-8 open read-only; files >2 MB open as plain text without lint/folding/wrap; files >20 MB are refused.
- `gradlew` is a small bootstrap script because the binary `gradle-wrapper.jar` could not be included.
- JavaScript runs in a hidden WebView and finishes when no timers/intervals are pending; shell runs `/system/bin/sh` in the app cache.

## 6. Dependencies and versions

| Layer | Dependency | Version / range | Notes |
|---|---|---|---|
| Build | Android Gradle Plugin | 8.7.3 | needs Gradle >= 8.9 |
| Build | Gradle | 8.10.2 | CI uses `gradle/actions/setup-gradle` with this version |
| Build | Kotlin | 2.0.21 | `kotlinOptions.jvmTarget = 17` |
| Build | JDK / SDK / NDK / CMake | 17 / platform 35, build-tools 35.0.0 / 27.0.12077973 / 3.22.1 | installed by `sdkmanager` in CI |
| Build | Node.js | >= 18 (CI: 20) | for `npm install` and esbuild |
| Android | androidx core-ktx, appcompat, activity-ktx, lifecycle-runtime-ktx, recyclerview, drawerlayout, preference-ktx, webkit | 1.15.0, 1.7.0, 1.9.3, 2.8.7, 1.3.2, 1.2.0, 1.2.1, 1.11.0 | `gradle/libs.versions.toml` |
| Android | Material Components | 1.12.0 | theme attrs `colorSurfaceContainer*` need >= 1.11 |
| Android | kotlinx-coroutines-android | 1.9.0 |  |
| Editor (npm) | `@codemirror/{state,view,commands,language,search,autocomplete,lint}` | `^6.0.0` | resolved to latest 6.x at install time |
| Editor (npm) | `@codemirror/lang-{html,css,javascript,json,python,java,cpp,php,rust,sql,xml,markdown}` | `^6.0.0` |  |
| Editor (npm) | `@codemirror/legacy-modes`, `@lezer/highlight` | `^6.0.0`, `^1.0.0` |  |
| Editor (npm) | esbuild | `^0.24.0` | dev dependency |
| Native | Luau (cloned) | tag in `luau/LUAU_VERSION` = `0.680` | existence of this tag is unverified; fallback exists |

No `package-lock.json` and no `gradle-wrapper.jar` exist (no network was available).

## 7. Source inventory

All 149 files below exist in this snapshot (sandbox tree and `ByteCode.zip`) and **nowhere else** (not in the Project files, not on GitHub). The SHA-256 prefix identifies the exact version; full hashes are in `MANIFEST.sha256`. Everything was created in this chat; no earlier ByteCode files existed.

| Path | Lines | SHA-256 (12) | Role |
|---|---:|---|---|
| `.gitattributes` | 4 | `db250a2ec13a` | LF line endings |
| `.github/workflows/build.yml` | 157 | `a8c20c2388e7` | CI: quick tests, APK build, artifact upload, release attachment |
| `.gitignore` | 22 | `ede84167ea52` | Ignores build output, node_modules, cloned Luau, the generated editor bundle, keystores |
| `LICENSE` | 21 | `6d7da31a6b81` | MIT licence of ByteCode |
| `README.md` | 204 | `f35c9892b57e` | Overview, features, languages, stack, build, CI, status, limitations |
| `THIRD_PARTY_LICENSES.md` | 26 | `740487fe8147` | Licences of CodeMirror, Lezer, Luau, AndroidX, Material, Kotlin |
| `app/build.gradle.kts` | 78 | `1e8c2941e36c` | App module: SDK levels, optional release signing, dependencies |
| `app/proguard-rules.pro` | 9 | `b3e3ad0eea8e` | Keep rules (minification is off by default) |
| `app/src/main/AndroidManifest.xml` | 38 | `61b14e322cb5` | No permissions; MainActivity handles config changes; SettingsActivity |
| `app/src/main/java/dev/bytecode/app/AppCommand.kt` | 41 | `07ab021eaa49` | All user commands (34) with titles and shortcuts |
| `app/src/main/java/dev/bytecode/app/ByteCodeApp.kt` | 13 | `319a4045d58b` | Application: applies the saved day/night mode |
| `app/src/main/java/dev/bytecode/app/MainActivity.kt` | 1076 | `731995e53a51` | Orchestrator (1,076 lines - too large, split planned) |
| `app/src/main/java/dev/bytecode/app/documents/DocumentManager.kt` | 349 | `8b6fb9d064a8` | Open/save/close, autosave, recovery, editor events |
| `app/src/main/java/dev/bytecode/app/documents/OpenDocument.kt` | 24 | `8e54ab1f8617` | Open-tab metadata |
| `app/src/main/java/dev/bytecode/app/documents/RecoveryStore.kt` | 67 | `84e724d32f04` | Unsaved-text recovery copies |
| `app/src/main/java/dev/bytecode/app/run/CodeRunner.kt` | 126 | `f31fd9912a10` | Runner interfaces and RunManager |
| `app/src/main/java/dev/bytecode/app/run/JavaScriptRunner.kt` | 174 | `38570182eb10` | JavaScript in a hidden WebView |
| `app/src/main/java/dev/bytecode/app/run/LuauRunner.kt` | 73 | `5c2b360a5352` | Native Luau execution, error-location parsing |
| `app/src/main/java/dev/bytecode/app/run/ShellRunner.kt` | 81 | `5737566e53ff` | Shell via /system/bin/sh |
| `app/src/main/java/dev/bytecode/app/settings/SettingsActivity.kt` | 129 | `9ef6763f94fa` | Settings screen, font import, accent validation |
| `app/src/main/java/dev/bytecode/app/settings/SettingsRepository.kt` | 185 | `f723d8d515f6` | Typed preferences, accent presets, EditorSettings factory |
| `app/src/main/java/dev/bytecode/app/ui/ConsolePanel.kt` | 268 | `bbc6bcc1baba` | Console: 4 channels, resize handle, clickable lines |
| `app/src/main/java/dev/bytecode/app/ui/Dialogs.kt` | 98 | `a35d8c0d662b` | Prompt/confirm/choose dialogs with accent buttons |
| `app/src/main/java/dev/bytecode/app/ui/ExplorerController.kt` | 524 | `202dcb4328ef` | Explorer tree and file operations |
| `app/src/main/java/dev/bytecode/app/ui/ExtraKeysBar.kt` | 134 | `d6e5769f33db` | Tab/undo/redo/arrow/symbol keys |
| `app/src/main/java/dev/bytecode/app/ui/FileIconDrawable.kt` | 58 | `204a99018627` | Language badge drawable |
| `app/src/main/java/dev/bytecode/app/ui/Palette.kt` | 16 | `2737ace1317f` | Runtime accent colour, dp() helper |
| `app/src/main/java/dev/bytecode/app/ui/PaletteDialog.kt` | 118 | `919b613c0c78` | Command palette, go to file, recents |
| `app/src/main/java/dev/bytecode/app/ui/TabStrip.kt` | 91 | `27328ecb24ab` | Tab bar |
| `app/src/main/java/dev/bytecode/app/workspace/FileIo.kt` | 85 | `0d35efa6e89a` | Read/write/copy streams; strict UTF-8 decoding, binary detection |
| `app/src/main/java/dev/bytecode/app/workspace/FsEntry.kt` | 15 | `b8f295cb0b4e` | File/folder model |
| `app/src/main/java/dev/bytecode/app/workspace/TextSearch.kt` | 82 | `49279d18ae70` | Workspace indexer and search in files |
| `app/src/main/java/dev/bytecode/app/workspace/WorkspaceFileSystem.kt` | 224 | `4c07f0409127` | SAF operations through DocumentsContract (list, create, rename, delete, copy, move, walk) |
| `app/src/main/java/dev/bytecode/app/workspace/WorkspaceStore.kt` | 159 | `441de47dd986` | Persisted workspace, recents, session tabs, layout sizes, URI permissions |
| `build.gradle.kts` | 6 | `e39eca5ad0d5` | Top-level plugin declarations (versions come from the catalog) |
| `docs/ARCHITECTURE.md` | 89 | `23f99cd78afc` | Modules, bridge protocol, persistence |
| `docs/LUAU_RUNTIME.md` | 66 | `bcf01c693c03` | What standalone Luau is, execution model, diagnostics, completion |
| `docs/ORIGINAL_SPEC.md` | 781 | `5c19a642f52d` | Verbatim copy of the owner's specification |
| `docs/TEST_PLAN.md` | 53 | `d18a28fb23dd` | Device test checklist (every item still unchecked) |
| `editor/build.gradle.kts` | 103 | `43f74eaaf203` | Editor module: npm install + esbuild bundle tasks hooked into preBuild |
| `editor/consumer-rules.pro` | 4 | `0b7932f4e223` | Keep rule for the WebView JavaScript interface |
| `editor/src/main/AndroidManifest.xml` | 2 | `c8fe08b06e47` | Empty library manifest |
| `editor/src/main/assets/editor/editor.css` | 53 | `a29e9a146433` | Page background, empty state |
| `editor/src/main/assets/editor/index.html` | 19 | `0c4b7c36b26a` | Editor page (CSP, loads dist/main.js as an ES module) |
| `editor/src/main/java/dev/bytecode/editor/CodeEditorView.kt` | 438 | `528636e3971c` | WebView host, asset loader, Kotlin<->JS bridge, lifecycle, renderer-crash handling |
| `editor/src/main/java/dev/bytecode/editor/DocumentContentStore.kt` | 22 | `cdce90484b42` | Serves document text to the page once |
| `editor/src/main/java/dev/bytecode/editor/EditorEvents.kt` | 58 | `ad3e0c4147ca` | Events from the editor, diagnostics model |
| `editor/src/main/java/dev/bytecode/editor/EditorSettings.kt` | 43 | `b4d814d37187` | Editor options sent to JavaScript |
| `editor/src/main/java/dev/bytecode/editor/LanguageRegistry.kt` | 65 | `8eb7d4a20e01` | Extension -> language id, name, icon label/colour |
| `editor/web/build.mjs` | 31 | `4613ed07c6e5` | esbuild bundling with code splitting into assets/editor/dist |
| `editor/web/harness/fake-cm.mjs` | 113 | `f537fb8a73c4` | Fake EditorState/EditorView/Compartment |
| `editor/web/harness/loader.mjs` | 51 | `3f6ba56a908d` | Node module hooks mapping CodeMirror packages to fakes/stubs |
| `editor/web/harness/stub-runtime.mjs` | 18 | `38436e2901a8` | Universal Proxy stub |
| `editor/web/package.json` | 38 | `4ac583e19f95` | npm dependencies (CodeMirror 6 packages, esbuild) |
| `editor/web/src/documents.js` | 302 | `27105a742289` | One EditorState per document in a single EditorView; dirty/save bookkeeping |
| `editor/web/src/extensions.js` | 222 | `56e9aadeff99` | Extensions, settings compartments, shortcuts, actions, CSS variables |
| `editor/web/src/host.js` | 87 | `532bf0a0ea05` | Bridge: dispatch(commands), post(events), lint requests |
| `editor/web/src/languages.js` | 57 | `716f323ce362` | Lazy language loading; word-completion fallback |
| `editor/web/src/lint.js` | 57 | `4c8967673ac1` | Lint glue that reports diagnostics to the app |
| `editor/web/src/luau/analysis.js` | 256 | `ce96235eddad` | Receiver parsing and class inference for completion (unit-tested) |
| `editor/web/src/luau/completion.js` | 130 | `8494bcfc6654` | Completion sources: keywords, libraries, Roblox API, snippets, members |
| `editor/web/src/luau/data.js` | 64 | `c52f8e97c390` | Keywords, libraries, Roblox global/method sets |
| `editor/web/src/luau/language.js` | 19 | `500abbaf2485` | StreamLanguage wrapper + completion hookup |
| `editor/web/src/luau/roblox-api.js` | 200 | `13822e9a47d4` | Curated Roblox classes, services, datatypes, enums |
| `editor/web/src/luau/tags.js` | 11 | `0aac6ac2ec89` | Custom highlight tags (roblox/builtin/type) |
| `editor/web/src/luau/tokenizer.js` | 543 | `8b00c3132c4f` | Dedicated Luau tokenizer (no CodeMirror imports; unit-tested) |
| `editor/web/src/main.js` | 10 | `4d73f88075fe` | Entry point: creates the document store and the host |
| `editor/web/src/themes.js` | 140 | `9ac5c4556b79` | Dark/light editor chrome and 3 syntax palettes |
| `editor/web/src/words.js` | 24 | `37db90fc06bd` | Document-words completion source |
| `editor/web/test/analysis.test.mjs` | 77 | `1dd2950312a0` | 5 completion-analysis tests |
| `editor/web/test/host-smoke.test.mjs` | 153 | `c34ed82c2208` | 9 bridge/document-store tests against a fake CodeMirror |
| `editor/web/test/luau-tokenizer.test.mjs` | 229 | `05d6f4c94b5c` | 12 tokenizer tests |
| `gradle.properties` | 13 | `27ae1c3e288e` | JVM args; `bytecode.abis`; `bytecode.skipEditorBundle` |
| `gradle/libs.versions.toml` | 30 | `762324773c5e` | Version catalog (AGP 8.7.3, Kotlin 2.0.21, AndroidX, Material, coroutines) |
| `gradle/wrapper/gradle-wrapper.properties` | 7 | `23b7b98fa7f0` | Gradle 8.10.2 distribution URL (the wrapper jar is NOT included) |
| `gradlew` | 42 | `a07e6209ca2f` | Bootstrap launcher: downloads gradle-wrapper.jar on first run, then starts the wrapper |
| `luau/LUAU_VERSION` | 1 | `3132bef6642d` | Pinned Luau release tag (0.680) |
| `luau/build.gradle.kts` | 141 | `066376963d69` | Luau module: clones Luau (fetchLuau), NDK/CMake config, ABIs |
| `luau/consumer-rules.pro` | 6 | `8ac871b1a9e6` | Keep rules for JNI-visible classes |
| `luau/native/CMakeLists.txt` | 40 | `92689ea01a74` | Builds libbytecode_luau.so; adds Luau as a subdirectory (VM, Compiler, Ast only) |
| `luau/native/src/jni_bridge.cpp` | 105 | `3d2d29ca7e12` | JNI glue (nativeRun, nativeRequestStop, nativeCheckSyntax, nativeVersion) |
| `luau/native/src/luau_diagnostics.cpp` | 37 | `c17b23ade19d` | Syntax errors via the official Luau parser |
| `luau/native/src/luau_runtime.cpp` | 331 | `2e93da46bf34` | Runs a script on a sandboxed Luau state: limits, interrupt, output, Roblox-global trap |
| `luau/native/src/luau_runtime.hpp` | 59 | `f04ecd699368` | Native interface (no JNI/Luau headers): enums, options, functions |
| `luau/native/src/text_convert.hpp` | 117 | `2134f89f4b95` | UTF-8 <-> UTF-16 helpers (unit-tested) |
| `luau/native/tests/syntax-check.sh` | 10 | `94ea48343304` | Type-checks the native sources against the stub headers |
| `luau/native/tests/text_convert_test.cpp` | 44 | `2455d95d3e35` | Unit test for text_convert.hpp |
| `luau/src/main/AndroidManifest.xml` | 2 | `c8fe08b06e47` | Empty library manifest |
| `luau/src/main/java/dev/bytecode/luau/LuauModels.kt` | 49 | `386ba8468699` | Channel/status enums, options, diagnostics |
| `luau/src/main/java/dev/bytecode/luau/LuauNative.kt` | 44 | `d3088c2518db` | JNI declarations + library loading (isLoaded) |
| `luau/src/main/java/dev/bytecode/luau/LuauRuntime.kt` | 107 | `8a59bb7ac62b` | Runs scripts on a dedicated thread; syntax checks on another |
| `scripts/static-check.py` | 249 | `0c340e6f1707` | Source consistency checker (imports, R refs, JNI names, enums, listeners) |
| `settings.gradle.kts` | 27 | `b8d1ce3fc859` | Declares modules :app, :editor, :luau and repositories |
| `app/src/main/res/drawable/` (30 files) | 294 | – | vector icons (`ic_*`), shape/ripple backgrounds, launcher foreground |
| `app/src/main/res/layout/` (10 files) | 572 | – | activity_main, activity_settings, view_explorer, view_console, item_* rows, dialog_* |
| `app/src/main/res/menu/` (1 files) | 63 | – | main_menu (toolbar and overflow) |
| `app/src/main/res/mipmap-anydpi-v26/` (1 files) | 5 | – | adaptive launcher icon |
| `app/src/main/res/values-night/` (1 files) | 21 | – | dark colour set |
| `app/src/main/res/values/` (5 files) | 393 | – | strings (153), arrays, colors, dimens, themes (Theme.ByteCode + 8 accent overlays) |
| `app/src/main/res/xml/` (1 files) | 154 | – | preferences.xml (Settings screen) |
| `luau/native/tests/stubs/Luau/` (3 files) | 27 | – | stand-ins for Luau/Parser.h, Lexer.h, Allocator.h |
| `luau/native/tests/stubs/` (4 files) | 78 | – | hand-written stand-in headers for Luau and JNI (NOT the real headers) |

## 8. What changed in the latest session

- **Created:** every file listed in section 7. The sandbox contained no ByteCode files when the session started.
- **Modified after first creation (notable):** `DocumentManager.openFile` now returns `Job?` (sequential tab restore); `ConsolePanel` and `ExtraKeysBar` got explicit generic/`View.OnTouchListener` references; `editor/build.gradle.kts` gained explicit dependencies of asset-reading tasks on the bundle task; `languages.js` gained the document-words fallback (`words.js` added); `luau/native/tests` gained the stub headers; the editor test harness moved from `test/support` to `harness/`; CI gained the static-check and native type-check steps.
- **Removed:** nothing from the deliverable. An early directory skeleton created with shell brace expansion (which `sh` = dash does not support) was deleted and recreated. Temporary scripts in `/tmp` were promoted into the repo (`scripts/static-check.py`, `luau/native/tests/`).

## 9. Known issues, risks and unverified assumptions (most important first)

- **K1 — Never compiled.** 33 Kotlin files (5,036 lines), 5 Gradle scripts, CMake and 49 resource files have not been seen by kotlinc, AGP, AAPT2 or CMake. Expect compile and resource errors on the first build.
- **K2 — Luau API conformance.** The C++ was type-checked only against *hand-written* stub headers (`luau/native/tests/stubs`). Assumed real API: `lua_newstate`, `luaL_openlibs`, `luaL_sandbox`, `luaL_sandboxthread`, `lua_newthread`, `luau_compile` (+`lua_CompileOptions.optimizationLevel/debugLevel`), `luau_load`, `lua_resume`, `lua_debugtrace`, `lua_rawcheckstack`, `lua_callbacks(L)->userdata/interrupt`, `luaL_tolstring`, `lua_pushcfunction(L, fn, name)`, `lua_setglobal`, `lua_setmetatable(L, LUA_GLOBALSINDEX)`; parser: `Luau/Parser.h`, `Luau::Parser::parse(const char*, size_t, AstNameTable&, Allocator&, ParseOptions)`, `ParseResult::errors`, `ParseError::getLocation()/getMessage()`; CMake targets `Luau.VM`, `Luau.Compiler`, `Luau.Ast`, options `LUAU_BUILD_CLI/TESTS/WEB`. Files: `luau_runtime.cpp`, `luau_diagnostics.cpp`, `native/CMakeLists.txt`.
- **K3 — Luau tag.** `0.680` was chosen from memory; if it cannot be cloned `luau/build.gradle.kts` falls back to the newest numeric tag, which might have a different API (see K2).
- **K4 — Real CodeMirror bundle never built.** `npm install`/esbuild never ran. The glue was exercised only against a fake. If esbuild reports a missing export, check the symbol list in section 17. Also unverified against the real packages: the `tokenTable` option of `StreamLanguage.define`, `Tag.define(parent)`, `HighlightStyle` modifier tags, completion `snippetCompletion` template syntax (tabs for indentation), `EditorState.languageData` overriding `closeBrackets`.
- **K5 — Gradle/AGP details unverified:** version compatibility (AGP 8.7.3, Gradle 8.10.2, Kotlin 2.0.21), `kotlinOptions` DSL, `ndk.abiFilters` in a library module, the `-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON` argument, version-catalog accessor names, `Exec` tasks writing into `src/main/assets`, theme attributes (`colorSurfaceContainer*`, `preferenceTheme`).
- **K6 — `gradlew`** downloads `gradle-wrapper.jar` from `raw.githubusercontent.com/gradle/gradle/v8.10.2/...` (URL assumed; unverified).
- **K7 — WebView runtime unverified:** the CSP in `index.html`, ES-module and dynamic-import chunk loading through `WebViewAssetLoader`, the JS<->Kotlin bridge, `evaluateJavascript` with `JSONObject.quote`, soft-keyboard resizing (`adjustResize`) and caret visibility, renderer-crash recovery.
- **K8 — SAF behaviour is provider-specific:** `createDocument` name handling (workaround: `application/octet-stream` plus rename), `copyDocument`/`moveDocument` support (manual fallback), path-like document ids assumed in the 'copy folder into itself' guard.
- **K9 — `MainActivity.kt` is 1,076 lines**, contradicting 'avoid excessively large source files'. Split into controllers (workspace, search, palettes, layout).
- **K10 — UX gaps:** no minimap or breadcrumbs; explorer width adjustable only when docked; custom accent only approximated for stock Material widgets; theme/accent changes recreate the activity; external file changes are not detected (save overwrites); autosave delay is fixed at 1.5 s.
- **K11 — Data limits:** the Roblox API data is a hand-typed subset; syntax diagnostics only (no type errors); JSON and Luau are the only linted languages; the JavaScript runner ends on timer quiescence only.
- **K12 — Version drift:** npm ranges `^6`/`^0.24` and the Luau fallback make builds non-reproducible until a lockfile and a verified tag are committed.

## 10. Build errors and previous fixes

**No Gradle, AGP, Kotlin, NDK, CMake, AAPT or esbuild build has ever been run, so there are no real build errors or build fixes to report.** Problems met in the authoring sandbox and how they were resolved:

- `sh` is dash: brace expansion in `mkdir` created literal directories -> deleted and recreated without brace expansion; later scripts avoid bashisms.
- Two tokenizer tests failed because the *tests* were wrong (a bad expectation and an off-by-one index) -> tests corrected; tokenizer unchanged.
- `host-smoke.test.mjs` was timing-dependent (module loading under parallel test processes) -> replaced sleeps with `waitFor`; 3 consecutive full runs passed.
- The static checker first mis-stripped strings/comments (false 'missing method' errors) -> rewritten as a real scanner; the generated `dev.bytecode.app.R` import is whitelisted.
- The Node test runner executed the harness files as tests -> moved to `editor/web/harness/`.

## 11. Testing performed (actual results of the final run)

| Check | Command | Result |
|---|---|---|
| JavaScript unit tests (Node v22.22.2) | `cd editor/web && node --test` | 26 tests, 26 pass, 0 fail (12 tokenizer, 5 completion analysis, 9 bridge/document store against a **fake** CodeMirror) |
| JavaScript syntax | `node --check` on every `.js`/`.mjs` | 22 files OK |
| Native UTF conversion test | `g++ -std=c++17 -fsanitize=address,undefined … text_convert_test.cpp` | all assertions passed |
| Native type-check vs stubs | `sh luau/native/tests/syntax-check.sh` | 3 sources OK (against hand-written stubs only) |
| Static source check | `python3 scripts/static-check.py` | 33 Kotlin files: no problems (imports, R refs, JNI names, enum order, 7 listener interfaces, 34 commands) |
| XML | `xmllint --noout` on all XML | 52 files well-formed |
| Workflow / shell | PyYAML parse; `sh -n gradlew` | both OK |
| Manifest | `sha256sum -c MANIFEST.sha256` | 149 entries verified against the working tree |

The fake-CodeMirror test checks the bridge protocol and bookkeeping, **not** real editing behaviour.

## 12. Features that remain unverified

Everything marked **S** in section 4, in particular: Gradle/Kotlin/NDK builds, the Luau native library, the real editor bundle, app launch, explorer and SAF operations, tab switching in the real editor, saving/autosave/recovery, console and runners, settings, themes, search/palettes, the GitHub Actions run and APK artifact. The complete checklist is `docs/TEST_PLAN.md` — **all items are unchecked**.

## 13. Next recommended task

**Get the first real build and make it green** (do not add features first).

Prerequisites: the source in a GitHub repository (or a machine with JDK 17, Android SDK 35, NDK 27.0.12077973, CMake 3.22.1, Node 18+, Git and network), the first build log if it fails, and later an Android 8.0+ device or emulator.

Order of work if the build fails:

1. `:editor:bundleEditor` — `npm install` / esbuild errors (package names, missing exports; compare with section 17). Fix in `editor/web`.
2. `:luau:fetchLuau` and CMake — tag exists? header/API mismatches in `luau_runtime.cpp`, `luau_diagnostics.cpp` (K2). Pin a verified tag in `luau/LUAU_VERSION`.
3. Kotlin compilation (`:luau`, `:editor`, `:app`) and AAPT resource errors (K1, K5).
4. Packaging/lint, then confirm the workflow uploads the APK artifact.
5. Install the APK; work through `docs/TEST_PLAN.md`; fix runtime problems (K7, K8) starting with launch -> open folder -> open file -> edit -> save -> run a `.luau` script.
6. Commit `package-lock.json` and the Gradle wrapper jar; then split `MainActivity.kt` (K9).

Done when: the workflow is green with an APK artifact, the app launches, and the Application/Explorer/Editor/Luau items of the test plan are ticked with real results.

## 14. Context that could not be carried over

- Earlier chats of this Project: not accessible from here. If ByteCode was discussed there, the new chat should reconcile it against this handoff.
- The first two 'Continue' turns of the chat that produced this snapshot left nothing on disk; I cannot see what, if anything, they produced.
- Claude Project files: I cannot read or write them, so I do not know what is already uploaded.
- The owner's **Project Instructions** are not stored in the repo (they live in the Project settings); the specification is preserved verbatim in `docs/ORIGINAL_SPEC.md`.
- No screenshots, device logs, build logs or CI runs exist.
- The sandbox copy of the tree is temporary; only the downloaded zip/handoff (and whatever you upload) persists.

## 15. Continuation prompt (paste into the new chat)

```text
You are continuing ByteCode, an Android VS Code-style mobile IDE (Kotlin + CodeMirror 6 in a WebView + the official Luau VM via NDK/JNI). Follow the Project Instructions.

1. Read BYTECODE_HANDOFF.md completely before doing anything else (Project files, or the repository root). If you cannot find it, tell me and stop.
2. Inspect the real project files before modifying anything. Use ByteCode.zip / the GitHub repo / uploaded files, whichever is available. Verify they match the handoff: run `sha256sum -c MANIFEST.sha256` from the project root (or compare file hashes with the manifest) and report every difference.
3. If earlier project conversations are accessible, review the ones about ByteCode and compare them with the handoff. Trust verified files over summaries and tell me about any conflict.
4. Continue from the latest verified implementation. The project is source-complete but has NEVER been built or run. Your first task is section 13 of the handoff: get a first real build (GitHub Actions or local Gradle) and fix errors in the listed order. Do not restart from scratch, do not rewrite subsystems that are not broken, do not duplicate existing features, and preserve the decisions in section 5 and all existing functionality.
5. Never claim that something was built, tested or committed unless it actually ran. Keep "verified", "implemented but unverified" and "planned" separate.
6. Ask me only for what you cannot recover from the files and conversations above — typically the zip/repo (if it is not accessible), the first build log, or a device test result. Name exactly what you need.
7. After substantial work, update BYTECODE_HANDOFF.md (state, changed files, test results, next task).
```

## 16. How to verify this snapshot

```sh
unzip ByteCode.zip && cd ByteCode
sha256sum -c MANIFEST.sha256 --quiet     # prints nothing when all 149 files match
sha256sum MANIFEST.sha256                  # first 16 hex digits must be 6b2c80b07189c0b7
cd editor/web && node --test                # 26 tests
sh luau/native/tests/syntax-check.sh
python3 scripts/static-check.py
```

## 17. Appendix — CodeMirror/Lezer symbols the editor bundle imports

If `npm run build` fails with a missing export, compare with this list (dynamic imports in `languages.js` additionally use the language packs' `javascript`, `json`, `html`, `css`, `python`, `java`, `cpp`, `php`, `rust`, `sql`, `xml`, `markdown` and the legacy modes `kotlin`, `csharp`, `ruby`, `go`, `shell`, `lua`, `yaml`, plus `jsonParseLinter`).

- `@codemirror/autocomplete`: `autocompletion`, `closeBrackets`, `closeBracketsKeymap`, `snippetCompletion`
- `@codemirror/commands`: `copyLineDown`, `cursorCharLeft`, `cursorCharRight`, `cursorLineBoundaryBackward`, `cursorLineBoundaryForward`, `cursorLineDown`, `cursorLineUp`, `defaultKeymap`, `deleteLine`, `history`, `historyKeymap`, `indentLess`, `indentMore`, `indentWithTab`, `insertNewline`, `insertTab`, `moveLineDown`, `moveLineUp`, `redo`, `selectAll`, `toggleComment`, `undo`
- `@codemirror/language`: `HighlightStyle`, `LanguageSupport`, `StreamLanguage`, `bracketMatching`, `foldAll`, `foldCode`, `foldGutter`, `foldKeymap`, `indentOnInput`, `indentUnit`, `syntaxHighlighting`, `syntaxTree`, `unfoldAll`, `unfoldCode`
- `@codemirror/lint`: `lintGutter`, `lintKeymap`, `linter`
- `@codemirror/search`: `gotoLine`, `highlightSelectionMatches`, `openSearchPanel`, `search`, `searchKeymap`
- `@codemirror/state`: `Compartment`, `EditorState`, `Prec`
- `@codemirror/view`: `EditorView`, `drawSelection`, `dropCursor`, `highlightActiveLine`, `highlightActiveLineGutter`, `highlightSpecialChars`, `keymap`, `lineNumbers`
- `@lezer/highlight`: `Tag`, `tags`

