// A small fallback completion source for every language that has no dedicated one:
// it suggests words that already appear in the document. Language packs add their own
// completions (HTML tags, CSS properties, JavaScript globals, SQL keywords, ...) next to it.
const MAX_SCAN_CHARS = 300000;
const MAX_WORDS = 300;

export function wordCompletionSource(context) {
  const word = context.matchBefore(/[A-Za-z_$][\w$]*/);
  if (!word) return null;
  if (word.to - word.from < 2 && !context.explicit) return null;
  const typed = word.text;
  const doc = context.state.doc;
  const text = doc.sliceString(0, Math.min(doc.length, MAX_SCAN_CHARS));
  const seen = new Set();
  for (const match of text.matchAll(/[A-Za-z_$][\w$]{2,}/g)) {
    if (match[0] !== typed) seen.add(match[0]);
    if (seen.size >= MAX_WORDS) break;
  }
  return {
    from: word.from,
    options: [...seen].map((label) => ({ label, type: "text", boost: -5 })),
    validFor: /^[\w$]*$/,
  };
}
