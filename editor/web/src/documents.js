// Keeps one EditorState per open document and shows the active one in a single EditorView.
// Switching tabs swaps the state (undo history, selection, folds and search panel are preserved) and
// restores the scroll position; the WebView and the view itself are never recreated.
import { EditorState } from "@codemirror/state";
import { EditorView } from "@codemirror/view";
import {
  createBaseExtensions, compartments, settingsExtensionMap, normalizeSettings, applyCssVariables, runAction,
} from "./extensions.js";
import { loadLanguage } from "./languages.js";
import { reportingLinter, toEditorDiagnostics } from "./lint.js";

export class DocumentStore {
  constructor({ parent, post, requestLint }) {
    this.parent = parent;
    this.post = post;
    this.requestLint = requestLint;
    this.settings = normalizeSettings({});
    this.docs = new Map();
    this.activeId = null;
    this.wantedActive = null;
    this.view = null;
    this.editedTimer = null;
    this.cursorTimer = null;
    this.jsonLintSource = null;
    this.logError = (message) => this.post({ type: "log", level: "error", message });
    this.base = createBaseExtensions({ post, onUpdate: (update) => this.handleUpdate(update) });
    applyCssVariables(this.settings);
    this.refreshEmptyState();
  }

  // ---- opening, switching and closing -------------------------------------------------------------

  async open(spec) {
    const id = spec.id;
    if (spec.activate !== false) this.wantedActive = id;
    try {
      const language = spec.largeFile ? "plaintext" : spec.language;
      const [text, languageSupport] = await Promise.all([
        this.fetchText(spec.contentUrl),
        loadLanguage(language, this.logError),
      ]);
      const state = this.createState(id, text, spec, languageSupport);
      const entry = {
        id,
        name: spec.name,
        language: spec.language,
        state,
        saved: state.doc,
        dirty: false,
        readOnly: Boolean(spec.readOnly),
        largeFile: Boolean(spec.largeFile),
        scrollTop: 0,
        scrollLeft: 0,
        snapshots: new Map(),
      };
      if (spec.restoreUrl) {
        const restored = await this.fetchText(spec.restoreUrl);
        entry.state = state.update({
          changes: { from: 0, to: state.doc.length, insert: restored },
          userEvent: "input.restore",
        }).state;
        entry.dirty = !entry.state.doc.eq(entry.saved);
      }
      this.docs.set(id, entry);
      this.post({ type: "documentOpened", docId: id, dirty: entry.dirty });
      if (this.wantedActive === id) this.show(id);
      this.refreshEmptyState();
    } catch (error) {
      this.post({ type: "documentFailed", docId: id, message: String((error && error.message) || error) });
    }
  }

  activate(id) {
    this.wantedActive = id;
    if (this.docs.has(id)) this.show(id);
  }

  show(id) {
    const next = this.docs.get(id);
    if (!next) return;
    this.saveActive();
    this.activeId = id;
    if (!this.view) {
      this.view = new EditorView({ state: next.state, parent: this.parent });
    } else {
      this.view.setState(next.state);
    }
    const { scrollTop, scrollLeft } = next;
    requestAnimationFrame(() => {
      if (this.view && this.activeId === id) {
        this.view.scrollDOM.scrollTop = scrollTop;
        this.view.scrollDOM.scrollLeft = scrollLeft;
      }
    });
    this.refreshEmptyState();
    this.postCursor();
  }

  saveActive() {
    if (!this.view || this.activeId === null) return;
    const current = this.docs.get(this.activeId);
    if (!current) return;
    current.state = this.view.state;
    current.scrollTop = this.view.scrollDOM.scrollTop;
    current.scrollLeft = this.view.scrollDOM.scrollLeft;
  }

  close(id) {
    if (id === this.activeId) this.activeId = null; // the closing document's state is not worth keeping
    this.docs.delete(id);
    if (this.wantedActive === id) this.wantedActive = null;
    if (this.docs.size === 0 && this.view) {
      this.view.setState(EditorState.create({ doc: "" })); // release the old document
    }
    this.refreshEmptyState();
  }

  refreshEmptyState() {
    document.body.classList.toggle("no-documents", this.activeId === null);
  }

  // ---- saving ----------------------------------------------------------------------------------------

  currentState(entry) {
    return entry.id === this.activeId && this.view ? this.view.state : entry.state;
  }

  requestText(id, requestId) {
    const entry = this.docs.get(id);
    if (!entry) {
      this.post({ type: "text", requestId, docId: id, text: null });
      return;
    }
    const state = this.currentState(entry);
    entry.snapshots.set(requestId, state.doc);
    if (entry.snapshots.size > 8) entry.snapshots.delete(entry.snapshots.keys().next().value);
    this.post({ type: "text", requestId, docId: id, text: state.sliceDoc(0) });
  }

  // The saved baseline is the snapshot that was actually written, so edits made while saving stay dirty.
  markSaved(id, requestId) {
    const entry = this.docs.get(id);
    if (!entry) return;
    const current = this.currentState(entry).doc;
    entry.saved = entry.snapshots.get(requestId) || current;
    entry.snapshots.delete(requestId);
    const dirty = !current.eq(entry.saved);
    if (dirty !== entry.dirty) {
      entry.dirty = dirty;
      this.post({ type: "dirty", docId: id, dirty });
    }
  }

  // ---- settings and language -----------------------------------------------------------------------------

  effectiveSettings(entry) {
    if (!entry.largeFile) return this.settings;
    return { ...this.settings, wordWrap: false, foldGutter: false, highlightActiveLine: false };
  }

  applySettings(raw) {
    this.settings = normalizeSettings({ ...this.settings, ...raw });
    applyCssVariables(this.settings);
    for (const entry of this.docs.values()) {
      const map = settingsExtensionMap(this.effectiveSettings(entry));
      this.applyEffects(entry, Object.keys(map).map((name) => compartments[name].reconfigure(map[name])));
    }
    if (this.view) this.view.requestMeasure();
  }

  applyEffects(entry, effects) {
    if (entry.id === this.activeId && this.view) this.view.dispatch({ effects });
    else entry.state = entry.state.update({ effects }).state;
  }

  async rename(id, name, language) {
    const entry = this.docs.get(id);
    if (!entry) return;
    entry.name = name;
    if (language && language !== entry.language) {
      entry.language = language;
      const support = await loadLanguage(entry.largeFile ? "plaintext" : language, this.logError);
      this.applyEffects(entry, [
        compartments.language.reconfigure(support),
        compartments.lint.reconfigure(this.lintExtension(id, language, entry.largeFile)),
      ]);
    }
  }

  createState(id, text, spec, languageSupport) {
    const map = settingsExtensionMap(this.effectiveSettings({ largeFile: spec.largeFile }));
    const extensions = [
      this.base,
      compartments.language.of(languageSupport),
      compartments.readOnly.of(spec.readOnly ? EditorState.readOnly.of(true) : []),
      compartments.lint.of(this.lintExtension(id, spec.language, spec.largeFile)),
      ...Object.keys(map).map((name) => compartments[name].of(map[name])),
    ];
    if (text.includes("\r\n")) extensions.push(EditorState.lineSeparator.of("\r\n")); // keep CRLF files CRLF
    return EditorState.create({ doc: text, extensions });
  }

  lintExtension(id, language, largeFile) {
    if (largeFile) return [];
    const report = (items) => this.post({ type: "diagnostics", docId: id, items });
    if (language === "luau") {
      return reportingLinter(async (view) => {
        const doc = view.state.doc;
        const items = await this.requestLint(id, "luau", doc.toString());
        return toEditorDiagnostics(doc, items);
      }, report, 500);
    }
    if (language === "json") {
      return reportingLinter(async (view) => {
        if (!this.jsonLintSource) this.jsonLintSource = (await import("@codemirror/lang-json")).jsonParseLinter();
        return this.jsonLintSource(view);
      }, report, 500);
    }
    return [];
  }

  // ---- editing commands -------------------------------------------------------------------------------------

  runAction(name) {
    return runAction(this.view, name);
  }

  insertText(text) {
    if (!this.view || typeof text !== "string") return;
    this.view.dispatch({ ...this.view.state.replaceSelection(text), scrollIntoView: true, userEvent: "input.type" });
    this.view.focus();
  }

  revealPosition(id, line, column, focus) {
    if (id && id !== this.activeId) this.activate(id);
    if (!this.view) return;
    const doc = this.view.state.doc;
    const target = doc.line(Math.min(Math.max(Number(line) || 1, 1), doc.lines));
    const position = Math.min(target.from + Math.max((Number(column) || 1) - 1, 0), target.to);
    this.view.dispatch({
      selection: { anchor: position },
      effects: EditorView.scrollIntoView(position, { y: "center" }),
    });
    if (focus) this.view.focus();
  }

  focus() {
    if (this.view) this.view.focus();
  }

  keepCursorVisible() {
    if (!this.view || !this.view.hasFocus) return;
    this.view.dispatch({
      effects: EditorView.scrollIntoView(this.view.state.selection.main, { y: "nearest", yMargin: 40 }),
    });
  }

  // ---- notifications to the app -----------------------------------------------------------------------------------

  handleUpdate(update) {
    const entry = this.docs.get(this.activeId);
    if (!entry) return;
    if (update.docChanged) {
      const dirty = !update.state.doc.eq(entry.saved);
      if (dirty !== entry.dirty) {
        entry.dirty = dirty;
        this.post({ type: "dirty", docId: entry.id, dirty });
      }
      clearTimeout(this.editedTimer);
      const id = entry.id;
      this.editedTimer = setTimeout(() => this.post({ type: "edited", docId: id }), 400);
    }
    if (update.docChanged || update.selectionSet) {
      clearTimeout(this.cursorTimer);
      this.cursorTimer = setTimeout(() => this.postCursor(), 100);
    }
  }

  postCursor() {
    const entry = this.docs.get(this.activeId);
    if (!entry || !this.view) return;
    const state = this.view.state;
    const head = state.selection.main.head;
    const line = state.doc.lineAt(head);
    let selected = 0;
    for (const range of state.selection.ranges) selected += range.to - range.from;
    this.post({
      type: "cursor",
      docId: entry.id,
      line: line.number,
      column: head - line.from + 1,
      selected,
      lines: state.doc.lines,
    });
  }

  async fetchText(url) {
    const response = await fetch(url, { cache: "no-store" });
    if (!response.ok) throw new Error(`Could not load document (HTTP ${response.status})`);
    return response.text();
  }
}
