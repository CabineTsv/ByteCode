// A tiny fake of the parts of @codemirror/state and @codemirror/view that the editor glue uses.
// It exists so the bridge logic (documents.js, host.js) can be exercised in Node without a browser.
import { makeStub } from "./stub-runtime.mjs";

class FakeDoc {
  constructor(text) { this.text = text; }
  eq(other) { return other instanceof FakeDoc && other.text === this.text; }
  toString() { return this.text; }
  get length() { return this.text.length; }
  get lines() { return this.text.split("\n").length; }
  sliceString(from, to) { return this.text.slice(from, to); }
  line(number) {
    const lines = this.text.split("\n");
    let from = 0;
    for (let i = 1; i < number; i++) from += lines[i - 1].length + 1;
    return { number, from, to: from + lines[number - 1].length, text: lines[number - 1] };
  }
  lineAt(position) {
    let from = 0;
    let number = 1;
    for (const text of this.text.split("\n")) {
      if (position <= from + text.length) return { number, from, to: from + text.length, text };
      from += text.length + 1;
      number++;
    }
    return this.line(this.lines);
  }
}

const flatten = (value, out = []) => {
  if (Array.isArray(value)) value.forEach((item) => flatten(item, out));
  else if (value && typeof value === "object") out.push(value);
  return out;
};

export class Compartment {
  of(extension) { return { compartment: this, extension }; }
  reconfigure(extension) { return { reconfigure: this, extension }; }
}

export const Prec = { high: (x) => x, highest: (x) => x };

export class EditorState {
  constructor(doc, extensions, head = 0) {
    this.doc = doc;
    this.extensions = extensions;
    this.head = head;
  }
  static create({ doc = "", extensions = [] } = {}) { return new EditorState(new FakeDoc(doc), flatten(extensions)); }
  get selection() {
    const main = { head: this.head, from: this.head, to: this.head };
    return { main, ranges: [main] };
  }
  sliceDoc(from = 0, to = this.doc.length) { return this.doc.sliceString(from, to); }
  replaceSelection(text) { return { changes: { from: this.head, to: this.head, insert: text } }; }
  update(spec = {}) {
    let text = this.doc.text;
    const changes = spec.changes ? [].concat(spec.changes) : [];
    for (const change of changes) {
      text = text.slice(0, change.from) + (change.insert ?? "") + text.slice(change.to ?? change.from);
    }
    let extensions = this.extensions;
    for (const effect of [].concat(spec.effects || [])) {
      if (effect && effect.reconfigure) {
        extensions = extensions.filter((item) => item.compartment !== effect.reconfigure)
          .concat({ compartment: effect.reconfigure, extension: effect.extension });
      }
    }
    return { state: new EditorState(new FakeDoc(text), extensions, this.head) };
  }
}
EditorState.readOnly = { of: (value) => ({ readOnly: value }) };
EditorState.tabSize = { of: (value) => ({ tabSize: value }) };
EditorState.allowMultipleSelections = { of: (value) => ({ multiple: value }) };
EditorState.lineSeparator = { of: (value) => ({ lineSeparator: value }) };
EditorState.languageData = { of: (value) => ({ languageData: value }) };

export class EditorView {
  constructor({ state, parent }) {
    this.state = state;
    this.parent = parent;
    this.scrollDOM = { scrollTop: 0, scrollLeft: 0 };
    this.hasFocus = false;
    this.setStateCalls = 0;
  }
  listeners() {
    return this.state.extensions.filter((item) => item.updateListener).map((item) => item.updateListener);
  }
  setState(state) { this.state = state; this.setStateCalls++; }
  dispatch(spec) {
    const previous = this.state;
    this.state = previous.update(spec).state;
    const update = { docChanged: Boolean(spec.changes), selectionSet: Boolean(spec.selection), state: this.state, view: this };
    for (const listener of this.listeners()) listener(update);
  }
  focus() { this.hasFocus = true; }
  requestMeasure() {}
  static scrollIntoView(position, options) { return { scrollIntoView: position, options }; }
}
EditorView.updateListener = { of: (listener) => ({ updateListener: listener }) };
EditorView.theme = (spec, options) => ({ theme: spec, options });
EditorView.lineWrapping = { lineWrapping: true };
EditorView.contentAttributes = { of: (attributes) => ({ contentAttributes: attributes }) };
EditorView.editable = { of: (value) => ({ editable: value }) };

// Everything else the glue imports from these two packages is configuration only.
export const keymap = makeStub("keymap");
export const lineNumbers = makeStub("lineNumbers");
export const highlightActiveLine = makeStub("highlightActiveLine");
export const highlightActiveLineGutter = makeStub("highlightActiveLineGutter");
export const highlightSpecialChars = makeStub("highlightSpecialChars");
export const drawSelection = makeStub("drawSelection");
export const dropCursor = makeStub("dropCursor");
