// Lazy language loading. Each language pack becomes its own chunk, so only the languages that are
// actually opened are parsed. If a chunk fails to load the editor falls back to plain text.
import { StreamLanguage } from "@codemirror/language";
import { EditorState } from "@codemirror/state";
import { wordCompletionSource, caseInsensitiveWordCompletionSource } from "./words.js";

// Every language except Luau (which has its own source) and plain text also completes words from the document.
const wordCompletion = EditorState.languageData.of(() => [{ autocomplete: wordCompletionSource }]);
const caseInsensitiveWordCompletion = EditorState.languageData.of(() => [{ autocomplete: caseInsensitiveWordCompletionSource }]);
const CASE_INSENSITIVE = new Set(["sql", "css", "html"]);

const legacy = (loader, pick) => async () => StreamLanguage.define(pick(await loader()));

const LOADERS = {
  plaintext: async () => [],
  javascript: async () => (await import("@codemirror/lang-javascript")).javascript({ jsx: true }),
  jsx: async () => (await import("@codemirror/lang-javascript")).javascript({ jsx: true }),
  typescript: async () => (await import("@codemirror/lang-javascript")).javascript({ typescript: true }),
  tsx: async () => (await import("@codemirror/lang-javascript")).javascript({ typescript: true, jsx: true }),
  json: async () => (await import("@codemirror/lang-json")).json(),
  html: async () => (await import("@codemirror/lang-html")).html(),
  css: async () => (await import("@codemirror/lang-css")).css(),
  python: async () => (await import("@codemirror/lang-python")).python(),
  java: async () => (await import("@codemirror/lang-java")).java(),
  c: async () => (await import("@codemirror/lang-cpp")).cpp(),
  cpp: async () => (await import("@codemirror/lang-cpp")).cpp(),
  php: async () => (await import("@codemirror/lang-php")).php(),
  rust: async () => (await import("@codemirror/lang-rust")).rust(),
  sql: async () => (await import("@codemirror/lang-sql")).sql(),
  xml: async () => (await import("@codemirror/lang-xml")).xml(),
  markdown: async () => (await import("@codemirror/lang-markdown")).markdown(),
  kotlin: legacy(() => import("@codemirror/legacy-modes/mode/clike"), (m) => m.kotlin),
  csharp: legacy(() => import("@codemirror/legacy-modes/mode/clike"), (m) => m.csharp),
  ruby: legacy(() => import("@codemirror/legacy-modes/mode/ruby"), (m) => m.ruby),
  go: legacy(() => import("@codemirror/legacy-modes/mode/go"), (m) => m.go),
  shell: legacy(() => import("@codemirror/legacy-modes/mode/shell"), (m) => m.shell),
  lua: legacy(() => import("@codemirror/legacy-modes/mode/lua"), (m) => m.lua),
  yaml: legacy(() => import("@codemirror/legacy-modes/mode/yaml"), (m) => m.yaml),
  luau: async () => (await import("./luau/language.js")).luau(),
};

const cache = new Map();

export function isKnownLanguage(id) {
  return Object.prototype.hasOwnProperty.call(LOADERS, id);
}

export function loadLanguage(id, onError) {
  const key = isKnownLanguage(id) ? id : "plaintext";
  if (!cache.has(key)) {
    const withWords = key !== "luau" && key !== "plaintext";
    const words = CASE_INSENSITIVE.has(key) ? caseInsensitiveWordCompletion : wordCompletion;
    cache.set(key, LOADERS[key]().then((support) => (withWords ? [support, words] : support)).catch((error) => {
      if (onError) onError(`Could not load language '${key}': ${error && error.message}`);
      cache.delete(key);
      return [];
    }));
  }
  return cache.get(key);
}
