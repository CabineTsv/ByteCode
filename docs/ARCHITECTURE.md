# Architecture

ByteCode is one Android app made of three Gradle modules.

```
:app      Native UI, files, tabs, console, runners, settings        (Kotlin, Views)
 ├── :editor  CodeMirror 6 in a WebView + the Kotlin/JavaScript bridge (Kotlin + JS bundle)
 └── :luau    Official Luau VM, compiler and parser through JNI        (C++ / Kotlin)
```

`:editor` and `:luau` do not know about `:app`. The editor knows nothing about Luau execution, and the
Luau module knows nothing about the editor, so either can be replaced on its own.

## `:app` (package `dev.bytecode.app`)

| Package | Responsibility |
|---|---|
| `MainActivity`, `AppCommand` | Wires everything together. Every action in the app is an `AppCommand`, reachable from the toolbar, the command palette or a hardware shortcut. |
| `workspace` | `WorkspaceFileSystem` (Storage Access Framework through `DocumentsContract`), `WorkspaceStore` (persisted folders, recent files, tab session), `TextSearch`/`WorkspaceIndexer` (search in files, go to file). |
| `documents` | `DocumentManager` keeps metadata for open files, loads and saves them, runs autosave; `RecoveryStore` keeps copies of unsaved text. |
| `run` | `RunManager` and three `CodeRunner`s: `LuauRunner` (native), `JavaScriptRunner` (hidden WebView), `ShellRunner` (device `sh`). |
| `ui` | Explorer tree, tab strip, console, extra-keys bar, palette dialog, dialogs, file icons. |
| `settings` | `SettingsRepository` (typed access to preferences) and the Settings screen. |

Layout: on screens narrower than 600 dp the explorer is a drawer; on wider screens (tablets, landscape
phones) it is docked and its width can be dragged. The activity handles configuration changes itself, so
rotating the device never reloads the editor.

## `:editor` (package `dev.bytecode.editor`, JavaScript in `editor/web`)

`CodeEditorView` hosts one WebView that loads the bundled editor from the app's assets through
`WebViewAssetLoader` (`https://appassets.androidplatform.net/`). Nothing is fetched from the internet.

* One CodeMirror `EditorView` serves all tabs. Each open file has its own `EditorState`; switching tabs
  swaps states (undo history, selection and folds are kept) and restores the scroll position.
* Settings live in CodeMirror compartments, so changing a setting reconfigures the open editor.
* Language packs are separate lazily loaded chunks (esbuild code splitting).
* File text travels from Kotlin to the page as an HTTP response (`/docs/<id>`), not as a JavaScript
  string literal, which keeps large files cheap.

### Bridge protocol

Kotlin → JavaScript: `window.ByteCodeHost.dispatch(json)` with `{ "cmd": ..., ... }`

| cmd | Fields | Effect |
|---|---|---|
| `openDocument` | `id, name, language, contentUrl, restoreUrl?, largeFile, readOnly, activate` | Loads a document |
| `activateDocument` / `closeDocument` | `id` | Switch / release |
| `renameDocument` | `id, name, language` | Rename, re-detect language |
| `requestText` | `id, requestId` | Reply with a `text` event |
| `markSaved` | `id, requestId` | The text handed out for `requestId` was written |
| `applySettings` | `settings` | Reconfigure the editor |
| `insertText`, `action`, `revealPosition`, `focus` | | Editing helpers (extra-keys bar, console links) |
| `lintResult` | `requestId, items` | Answer to a `lint` event |

JavaScript → Kotlin: `window.AndroidHost.postMessage(json)` with `{ "type": ..., ... }`

`ready`, `documentOpened`, `documentFailed`, `dirty`, `edited` (debounced, drives autosave and recovery),
`cursor`, `text`, `lint` (ask the native Luau parser), `diagnostics` (for the Problems list), `command`
(a keyboard shortcut handled by the app), `log`.

All messages are JSON strings. Kotlin queues commands until `ready` arrives. If the page does not become
ready within 10 s the app shows an error (the usual cause is a missing editor bundle). If the WebView
renderer process dies the app creates a new WebView and reopens the tabs, restoring unsaved text from the
recovery copies.

## `:luau`

* `native/src/luau_runtime.cpp` – compiles and runs one script on a fresh sandboxed state, with an
  allocator that enforces a memory limit, an interrupt callback for Stop and timeouts, and buffered
  output.
* `native/src/luau_diagnostics.cpp` – syntax errors from the official parser (all errors, with columns).
* `native/src/jni_bridge.cpp` – JNI glue only; `text_convert.hpp` converts between Java UTF-16 and UTF-8.
* `LuauRuntime.kt` – runs scripts on a dedicated thread, syntax checks on another.

See `LUAU_RUNTIME.md`.

## Persistence and recovery

| What | Where |
|---|---|
| Settings | Default `SharedPreferences` (written by the Settings screen) |
| Workspace folder, recent files/folders, open tabs, layout sizes | `SharedPreferences` file `bytecode_state` |
| Access to chosen folders and files | Persistable SAF URI permissions |
| Unsaved text | `files/recovery/<sha1 of the file URI>.json`, written ~1.5 s after the last edit and when the app goes to the background; offered for restore the next time the file is opened |
| Imported font | `files/fonts/custom.ttf` |

Files are written with mode `wt` (truncate). Files that are not valid UTF-8 are opened read-only so a lossy
round trip cannot damage them; a UTF-8 BOM and CRLF line endings are preserved.
