// Editor chrome themes (dark / light) and syntax palettes. Colours that depend on the user's accent
// colour come from CSS variables set in extensions.js (--bc-accent, --bc-accent-soft, ...).
import { EditorView } from "@codemirror/view";
import { HighlightStyle } from "@codemirror/language";
import { tags as t } from "@lezer/highlight";
import { luauTags } from "./luau/tags.js";

const UI = {
  dark: {
    bg: "#1b1d21", fg: "#d4d7dd", gutter: "#5f6672", gutterActive: "#c3c8d0", activeLine: "rgba(255,255,255,0.045)",
    panel: "#23262b", panelAlt: "#2b2f35", border: "#31353c", input: "#1b1d21", muted: "#8a919c",
    guide: "rgba(255,255,255,0.13)",
  },
  light: {
    bg: "#fbfbfc", fg: "#24292f", gutter: "#9aa1ab", gutterActive: "#3b434d", activeLine: "rgba(0,0,0,0.045)",
    panel: "#eef0f3", panelAlt: "#e3e6ea", border: "#d3d8de", input: "#ffffff", muted: "#6b7280",
    guide: "rgba(0,0,0,0.14)",
  },
};

export function createTheme(isDark) {
  const ui = isDark ? UI.dark : UI.light;
  return EditorView.theme({
    "&": { color: ui.fg, backgroundColor: ui.bg, height: "100%", "--bc-guide": ui.guide },
    ".cm-scroller": { fontFamily: "var(--bc-font, monospace)", lineHeight: "1.55", overscrollBehavior: "contain" },
    ".cm-content": { caretColor: "var(--bc-accent)", fontSize: "var(--bc-font-size, 14px)", padding: "6px 0" },
    ".cm-gutters": { backgroundColor: ui.bg, color: ui.gutter, border: "none", fontSize: "var(--bc-font-size, 14px)" },
    ".cm-activeLine": { backgroundColor: ui.activeLine },
    ".cm-activeLineGutter": { backgroundColor: "transparent", color: ui.gutterActive },
    ".cm-lineNumbers .cm-gutterElement": { padding: "0 8px 0 12px", minWidth: "28px" },
    ".cm-cursor, .cm-dropCursor": { borderLeftColor: "var(--bc-accent)", borderLeftWidth: "2px" },
    "&.cm-focused > .cm-scroller > .cm-selectionLayer .cm-selectionBackground, .cm-selectionBackground, .cm-content ::selection": {
      backgroundColor: "var(--bc-accent-soft)",
    },
    ".cm-selectionMatch": { backgroundColor: "var(--bc-accent-faint)" },
    "&.cm-focused .cm-matchingBracket": { backgroundColor: "var(--bc-accent-soft)", outline: "1px solid var(--bc-accent)" },
    "&.cm-focused .cm-nonmatchingBracket": { backgroundColor: "rgba(244,71,71,0.3)" },
    ".cm-foldPlaceholder": { backgroundColor: ui.panelAlt, border: `1px solid ${ui.border}`, color: ui.muted, padding: "0 6px" },
    ".cm-foldGutter .cm-gutterElement": { cursor: "pointer", padding: "0 4px" },
    ".cm-searchMatch": { backgroundColor: "rgba(234,179,8,0.30)", outline: "1px solid rgba(234,179,8,0.55)" },
    ".cm-searchMatch.cm-searchMatch-selected": { backgroundColor: "var(--bc-accent-soft)", outline: "1px solid var(--bc-accent)" },
    ".cm-panels": { backgroundColor: ui.panel, color: ui.fg },
    ".cm-panels.cm-panels-top": { borderBottom: `1px solid ${ui.border}` },
    ".cm-panels.cm-panels-bottom": { borderTop: `1px solid ${ui.border}` },
    ".cm-panel.cm-search": { padding: "8px 34px 8px 8px", fontSize: "14px" },
    ".cm-panel.cm-search [name=close]": { color: ui.fg, fontSize: "22px", right: "6px", top: "4px", padding: "4px 8px" },
    ".cm-panel.cm-search label": { fontSize: "13px", margin: "2px 6px 2px 0" },
    ".cm-textfield": {
      backgroundColor: ui.input, color: ui.fg, border: `1px solid ${ui.border}`, borderRadius: "6px",
      padding: "6px 8px", minHeight: "34px", boxSizing: "border-box", margin: "2px 4px 2px 0",
    },
    ".cm-textfield:focus": { outline: "1px solid var(--bc-accent)", borderColor: "var(--bc-accent)" },
    ".cm-button": {
      backgroundImage: "none", backgroundColor: ui.panelAlt, color: ui.fg, border: `1px solid ${ui.border}`,
      borderRadius: "6px", padding: "6px 12px", minHeight: "34px", margin: "2px 4px 2px 0",
    },
    ".cm-button:active": { backgroundColor: "var(--bc-accent-soft)" },
    ".cm-tooltip": {
      backgroundColor: ui.panel, color: ui.fg, border: `1px solid ${ui.border}`, borderRadius: "8px", overflow: "hidden",
      boxShadow: isDark ? "0 6px 18px rgba(0,0,0,0.45)" : "0 6px 18px rgba(0,0,0,0.16)",
    },
    ".cm-tooltip-autocomplete > ul": { fontFamily: "var(--bc-font, monospace)", fontSize: "13px", maxHeight: "40vh", overscrollBehavior: "contain" },
    ".cm-tooltip-autocomplete > ul > li": { padding: "6px 10px" },
    ".cm-tooltip-autocomplete > ul > li[aria-selected]": { backgroundColor: "var(--bc-accent-soft)", color: ui.fg },
    ".cm-completionDetail": { color: ui.muted, marginLeft: "0.8em" },
    ".cm-diagnostic": { fontSize: "13px" },
  }, { dark: isDark });
}

// name -> { dark, light } token colours
const PALETTES = {
  onecompiler: {
    plain: true,
    dark: {
      comment: "#75715e", string: "#eaf69c", number: "#b496e4", constant: "#b496e4", keyword: "#f77ec5", control: "#f77ec5",
      operator: "#f77ec5", punct: "#f6f6f0", variable: "#f6f6f0", property: "#f6f6f0", fn: "#f6f6f0", type: "#96e4f6",
      typeBuiltin: "#96e4f6", builtin: "#96e4f6", robloxGlobal: "#f6f6f0", robloxMethod: "#f6f6f0", meta: "#f77ec5", self: "#f6f6f0",
    },
    light: {
      comment: "#8a8878", string: "#7d8a00", number: "#7a4fd6", constant: "#7a4fd6", keyword: "#d6338f", control: "#d6338f",
      operator: "#d6338f", punct: "#272822", variable: "#272822", property: "#272822", fn: "#272822", type: "#007fa3",
      typeBuiltin: "#007fa3", builtin: "#007fa3", robloxGlobal: "#272822", robloxMethod: "#272822", meta: "#d6338f", self: "#272822",
    },
  },
  default: {
    dark: {
      comment: "#6a9955", string: "#ce9178", number: "#b5cea8", constant: "#569cd6", keyword: "#569cd6", control: "#c586c0",
      operator: "#d4d4d4", punct: "#b8bcc4", variable: "#9cdcfe", property: "#9cdcfe", fn: "#dcdcaa", type: "#4ec9b0",
      typeBuiltin: "#4ec9b0", builtin: "#dcdcaa", robloxGlobal: "#4fc1ff", robloxMethod: "#d7ba7d", meta: "#c586c0", self: "#569cd6",
    },
    light: {
      comment: "#008000", string: "#a31515", number: "#098658", constant: "#0000ff", keyword: "#0000ff", control: "#af00db",
      operator: "#383a42", punct: "#555b66", variable: "#001080", property: "#001080", fn: "#795e26", type: "#267f99",
      typeBuiltin: "#267f99", builtin: "#795e26", robloxGlobal: "#0070c1", robloxMethod: "#8a6d00", meta: "#af00db", self: "#0000ff",
    },
  },
  monokai: {
    dark: {
      comment: "#75715e", string: "#e6db74", number: "#ae81ff", constant: "#ae81ff", keyword: "#f92672", control: "#f92672",
      operator: "#f92672", punct: "#f8f8f2", variable: "#f8f8f2", property: "#f8f8f2", fn: "#a6e22e", type: "#66d9ef",
      typeBuiltin: "#66d9ef", builtin: "#66d9ef", robloxGlobal: "#fd971f", robloxMethod: "#a6e22e", meta: "#fd971f", self: "#fd971f",
    },
    light: {
      comment: "#8c8a78", string: "#8f7d00", number: "#7a4fd6", constant: "#7a4fd6", keyword: "#d6004f", control: "#d6004f",
      operator: "#d6004f", punct: "#3b3b3b", variable: "#272822", property: "#272822", fn: "#4d8f00", type: "#007fa3",
      typeBuiltin: "#007fa3", builtin: "#007fa3", robloxGlobal: "#c25e00", robloxMethod: "#4d8f00", meta: "#c25e00", self: "#c25e00",
    },
  },
  solarized: {
    dark: {
      comment: "#586e75", string: "#2aa198", number: "#d33682", constant: "#cb4b16", keyword: "#859900", control: "#859900",
      operator: "#93a1a1", punct: "#93a1a1", variable: "#93a1a1", property: "#93a1a1", fn: "#268bd2", type: "#b58900",
      typeBuiltin: "#b58900", builtin: "#268bd2", robloxGlobal: "#cb4b16", robloxMethod: "#268bd2", meta: "#6c71c4", self: "#6c71c4",
    },
    light: {
      comment: "#93a1a1", string: "#2aa198", number: "#d33682", constant: "#cb4b16", keyword: "#859900", control: "#859900",
      operator: "#586e75", punct: "#586e75", variable: "#586e75", property: "#586e75", fn: "#268bd2", type: "#b58900",
      typeBuiltin: "#b58900", builtin: "#268bd2", robloxGlobal: "#cb4b16", robloxMethod: "#268bd2", meta: "#6c71c4", self: "#6c71c4",
    },
  },
};

export const SYNTAX_THEMES = Object.keys(PALETTES);

export function createHighlight(name, isDark) {
  const family = PALETTES[name] || PALETTES.default;
  const plain = Boolean(family.plain);
  const p = isDark ? family.dark : family.light;
  return HighlightStyle.define([
    { tag: t.comment, color: p.comment, fontStyle: plain ? "normal" : "italic" },
    { tag: t.string, color: p.string },
    { tag: t.number, color: p.number },
    { tag: [t.bool, t.null, t.atom], color: p.constant },
    { tag: t.keyword, color: p.keyword },
    { tag: [t.controlKeyword, t.moduleKeyword], color: p.control },
    { tag: t.self, color: p.self, fontStyle: plain ? "normal" : "italic" },
    { tag: t.operator, color: p.operator },
    { tag: [t.punctuation, t.bracket], color: p.punct },
    { tag: t.variableName, color: p.variable },
    { tag: t.definition(t.variableName), color: p.variable },
    { tag: [t.function(t.variableName), t.function(t.propertyName), t.function(t.definition(t.variableName))], color: p.fn },
    { tag: t.propertyName, color: p.property },
    { tag: [t.typeName, t.className, t.definition(t.typeName)], color: p.type },
    { tag: [t.namespace, t.labelName], color: p.type },
    { tag: t.meta, color: p.meta },
    { tag: [t.tagName, t.heading], color: p.keyword, fontWeight: "bold" },
    { tag: [t.attributeName], color: p.property },
    { tag: [t.regexp, t.escape, t.special(t.string)], color: p.number },
    { tag: t.link, color: p.string, textDecoration: "underline" },
    { tag: t.emphasis, fontStyle: "italic" },
    { tag: t.strong, fontWeight: "bold" },
    { tag: t.invalid, color: "#f44747" },
    // Luau / Roblox specific
    { tag: luauTags.robloxGlobal, color: p.robloxGlobal },
    { tag: luauTags.robloxMethod, color: p.robloxMethod },
    { tag: [luauTags.luauBuiltin, luauTags.luauBuiltinMember], color: p.builtin },
    { tag: luauTags.luauType, color: p.typeBuiltin },
  ]);
}
