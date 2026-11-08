// Builds the CodeMirror extension set. Everything the user can change in Settings lives in a
// Compartment, so changing a setting reconfigures the open editor without reloading it.
import { EditorState, Compartment, Prec } from "@codemirror/state";
import {
  EditorView, keymap, lineNumbers, highlightActiveLine, highlightActiveLineGutter, highlightSpecialChars,
  drawSelection, dropCursor,
} from "@codemirror/view";
import {
  defaultKeymap, history, historyKeymap, indentWithTab, insertNewline, undo, redo, selectAll, indentMore,
  indentLess, toggleComment, insertTab, cursorCharLeft, cursorCharRight, cursorLineUp, cursorLineDown,
  cursorLineBoundaryBackward, cursorLineBoundaryForward, deleteLine, copyLineDown, moveLineUp, moveLineDown,
} from "@codemirror/commands";
import {
  indentOnInput, bracketMatching, foldGutter, foldKeymap, indentUnit, syntaxHighlighting, foldAll, unfoldAll,
  foldCode, unfoldCode,
} from "@codemirror/language";
import { search, searchKeymap, highlightSelectionMatches, openSearchPanel, gotoLine } from "@codemirror/search";
import { autocompletion, closeBrackets, closeBracketsKeymap } from "@codemirror/autocomplete";
import { lintGutter, lintKeymap } from "@codemirror/lint";
import { createTheme, createHighlight } from "./themes.js";
import { editorScrollbars } from "./scrollbars.js";
import { identifierIndexExtension } from "./identifiers.js";

export const compartments = {
  language: new Compartment(),
  readOnly: new Compartment(),
  lint: new Compartment(),
  lineNumbers: new Compartment(),
  activeLine: new Compartment(),
  wrap: new Compartment(),
  tabs: new Compartment(),
  closeBrackets: new Compartment(),
  enterKey: new Compartment(),
  autoIndent: new Compartment(),
  fold: new Compartment(),
  theme: new Compartment(),
  highlight: new Compartment(),
};

export const DEFAULT_SETTINGS = {
  fontSize: 14,
  fontFamily: "monospace",
  customFontUrl: null,
  tabSize: 4,
  insertSpaces: true,
  wordWrap: false,
  lineNumbers: true,
  highlightActiveLine: true,
  autoCloseBrackets: true,
  autoCloseQuotes: true,
  autoIndent: true,
  foldGutter: true,
  darkTheme: true,
  syntaxTheme: "default",
  accentColor: "#4ea1ff",
};

const clamp = (value, min, max, fallback) => {
  const number = Number(value);
  return Number.isFinite(number) ? Math.min(max, Math.max(min, Math.round(number))) : fallback;
};

export function normalizeSettings(raw) {
  const merged = { ...DEFAULT_SETTINGS, ...raw };
  merged.fontSize = clamp(merged.fontSize, 8, 40, DEFAULT_SETTINGS.fontSize);
  merged.tabSize = clamp(merged.tabSize, 1, 8, DEFAULT_SETTINGS.tabSize);
  for (const key of ["insertSpaces", "wordWrap", "lineNumbers", "highlightActiveLine", "autoCloseBrackets",
    "autoCloseQuotes", "autoIndent", "foldGutter", "darkTheme"]) {
    merged[key] = Boolean(merged[key]);
  }
  if (!/^#[0-9a-fA-F]{6}$/.test(merged.accentColor)) merged.accentColor = DEFAULT_SETTINGS.accentColor;
  return merged;
}

function closeBracketsExtension(settings) {
  if (!settings.autoCloseBrackets && !settings.autoCloseQuotes) return [];
  const brackets = [];
  if (settings.autoCloseBrackets) brackets.push("(", "[", "{");
  if (settings.autoCloseQuotes) brackets.push("'", '"', "`");
  return [
    closeBrackets(),
    keymap.of(closeBracketsKeymap),
    // Overrides the per-language bracket list so the two settings work in every language.
    Prec.high(EditorState.languageData.of(() => [{ closeBrackets: { brackets } }])),
  ];
}

// One extension per compartment for the given settings.
export function settingsExtensionMap(settings) {
  return {
    lineNumbers: settings.lineNumbers ? [lineNumbers(), highlightActiveLineGutter()] : [],
    activeLine: settings.highlightActiveLine ? highlightActiveLine() : [],
    wrap: settings.wordWrap ? EditorView.lineWrapping : [],
    tabs: [
      EditorState.tabSize.of(settings.tabSize),
      indentUnit.of(settings.insertSpaces ? " ".repeat(settings.tabSize) : "\t"),
    ],
    closeBrackets: closeBracketsExtension(settings),
    // With automatic indentation off, Enter only inserts a line break.
    enterKey: settings.autoIndent ? [] : Prec.highest(keymap.of([{ key: "Enter", run: insertNewline }])),
    autoIndent: settings.autoIndent ? indentOnInput() : [],
    fold: settings.foldGutter ? foldGutter() : [],
    theme: createTheme(settings.darkTheme),
    highlight: syntaxHighlighting(createHighlight(settings.syntaxTheme, settings.darkTheme)),
  };
}

function hexToRgb(hex) {
  return [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16));
}

// Colours and fonts are CSS variables, so changing them never needs a new editor state.
export function applyCssVariables(settings) {
  const root = document.documentElement;
  const [r, g, b] = hexToRgb(settings.accentColor);
  root.style.setProperty("--bc-accent", settings.accentColor);
  root.style.setProperty("--bc-accent-soft", `rgba(${r},${g},${b},0.30)`);
  root.style.setProperty("--bc-accent-faint", `rgba(${r},${g},${b},0.14)`);
  root.style.setProperty("--bc-font-size", `${settings.fontSize}px`);
  root.style.setProperty("--bc-font", settings.fontFamily || "monospace");
  root.style.setProperty("--bc-bg", settings.darkTheme ? "#1b1d21" : "#fbfbfc");
  root.style.setProperty("--bc-muted", settings.darkTheme ? "#8a919c" : "#6b7280");
  root.style.colorScheme = settings.darkTheme ? "dark" : "light";

  let style = document.getElementById("bc-custom-font");
  if (settings.customFontUrl) {
    if (!style) {
      style = document.createElement("style");
      style.id = "bc-custom-font";
      document.head.appendChild(style);
    }
    style.textContent = `@font-face { font-family: "BC Custom"; src: url("${settings.customFontUrl}"); font-display: swap; }`;
  } else if (style) {
    style.remove();
  }
}

// Shortcuts that are handled by the Android app (hardware keyboards).
function appKeymap(post) {
  const command = (name) => () => {
    post({ type: "command", name });
    return true;
  };
  return [
    { key: "Mod-s", run: command("save"), preventDefault: true },
    { key: "Mod-Shift-s", run: command("saveAll"), preventDefault: true },
    { key: "Mod-p", run: command("quickOpen"), preventDefault: true },
    { key: "Mod-Shift-p", run: command("commandPalette"), preventDefault: true },
    { key: "F1", run: command("commandPalette"), preventDefault: true },
    { key: "Mod-Shift-f", run: command("searchInFiles"), preventDefault: true },
    { key: "Mod-b", run: command("toggleExplorer"), preventDefault: true },
    { key: "Mod-j", run: command("toggleConsole"), preventDefault: true },
    { key: "F5", run: command("run"), preventDefault: true },
    { key: "Mod-Enter", run: command("run"), preventDefault: true },
    { key: "Mod-w", run: command("closeTab"), preventDefault: true },
    { key: "Ctrl-Tab", run: command("nextTab"), preventDefault: true },
    { key: "Ctrl-Shift-Tab", run: command("previousTab"), preventDefault: true },
    { key: "Mod-h", run: openSearchPanel, preventDefault: true },
    { key: "Mod-Alt-g", run: gotoLine, preventDefault: true },
  ];
}

// Extensions shared by every document. `onUpdate` receives every ViewUpdate of the visible editor.
export function createBaseExtensions({ post, onUpdate }) {
  return [
    highlightSpecialChars(),
    identifierIndexExtension,
    editorScrollbars(),
    history(),
    drawSelection(),
    dropCursor(),
    EditorState.allowMultipleSelections.of(true),
    bracketMatching(),
    highlightSelectionMatches(),
    autocompletion({ maxRenderedOptions: 60 }),
    search({ top: true }),
    lintGutter(),
    keymap.of([
      ...appKeymap(post),
      ...searchKeymap,
      ...foldKeymap,
      ...historyKeymap,
      ...lintKeymap,
      ...defaultKeymap,
      indentWithTab,
    ]),
    // Phone keyboards must not "fix" code.
    EditorView.contentAttributes.of({ autocapitalize: "off", autocorrect: "off", spellcheck: "false" }),
    EditorView.updateListener.of(onUpdate),
  ];
}

const ACTIONS = {
  undo,
  redo,
  selectAll,
  indentMore,
  indentLess,
  insertTab,
  toggleComment,
  find: openSearchPanel,
  replace: openSearchPanel,
  gotoLine,
  foldAll,
  unfoldAll,
  foldCode,
  unfoldCode,
  cursorLeft: cursorCharLeft,
  cursorRight: cursorCharRight,
  cursorUp: cursorLineUp,
  cursorDown: cursorLineDown,
  lineStart: cursorLineBoundaryBackward,
  lineEnd: cursorLineBoundaryForward,
  deleteLine,
  duplicateLine: copyLineDown,
  moveLineUp,
  moveLineDown,
};

export function runAction(view, name) {
  const action = ACTIONS[name];
  if (!view || !action) return false;
  const handled = action(view);
  view.focus();
  return handled;
}
