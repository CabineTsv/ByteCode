// Dedicated Luau language support (not a reuse of the Lua mode).
import { StreamLanguage, LanguageSupport } from "@codemirror/language";
import * as tokenizer from "./tokenizer.js";
import { luauTags } from "./tags.js";
import { luauCompletionSource } from "./completion.js";

export const luauLanguage = StreamLanguage.define({
  name: "luau",
  startState: tokenizer.startState,
  copyState: tokenizer.copyState,
  token: tokenizer.token,
  indent: tokenizer.indent,
  languageData: tokenizer.languageData,
  tokenTable: luauTags,
});

export function luau() {
  return new LanguageSupport(luauLanguage, [luauLanguage.data.of({ autocomplete: luauCompletionSource })]);
}
