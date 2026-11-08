// A small fallback completion source for every language that has no dedicated one:
// it suggests words that already appear in the document. Language packs add their own
// completions (HTML tags, CSS properties, JavaScript globals, SQL keywords, ...) next to it.
import { suggestIdentifiers, validForPrefix, followsDigit, WORD_BEFORE_CURSOR } from "./identifiers.js";

export function createWordCompletionSource({ caseSensitive = true } = {}) {
  return (context) => {
    const word = context.matchBefore(WORD_BEFORE_CURSOR);
    if (!word) return null;
    if (word.to - word.from < 2 && !context.explicit) return null;
    if (followsDigit(context.state, word.from)) return null;
    const { options, truncated } = suggestIdentifiers(context.state, {
      pos: context.pos,
      prefix: word.text,
      caseSensitive,
    });
    if (options.length === 0) return null;
    return { from: word.from, options, validFor: validForPrefix(word.text, truncated) };
  };
}

export const wordCompletionSource = createWordCompletionSource();
export const caseInsensitiveWordCompletionSource = createWordCompletionSource({ caseSensitive: false });
