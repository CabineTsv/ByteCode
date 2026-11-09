// Luau tokenizer for CodeMirror 6's StreamLanguage.
//
// This module deliberately imports nothing from CodeMirror so that it can be unit-tested in Node
// (see test/luau-tokenizer.test.mjs). It is a line-based tokenizer with a small amount of state; the
// type-annotation handling is heuristic, not a full parser.
//
// Token names returned here are either standard @lezer/highlight tag names ("keyword",
// "variableName.definition", ...) or the custom names mapped in language.js:
//   robloxGlobal, robloxMethod, luauBuiltin, luauBuiltinMember, luauType
import {
  KEYWORDS, CONTROL_KEYWORDS, STATEMENT_KEYWORDS, BUILTIN_TYPES, GLOBAL_FUNCTIONS, CUSTOM_GLOBAL_FUNCTIONS, LIBRARY_NAMES,
  LIBRARY_MEMBERS, ROBLOX_GLOBALS, ROBLOX_GLOBAL_FUNCTIONS, ROBLOX_METHODS, ROBLOX_CLASS_NAMES,
} from "./data.js";

const MODE_CODE = 0;
const MODE_BLOCK_COMMENT = 1;
const MODE_LONG_STRING = 2;
const MODE_BACKTICK = 3;
const MODE_QUOTED_CONTINUATION = 4;

const IDENTIFIER = /^[A-Za-z_][A-Za-z0-9_]*/;
// What may follow an identifier directly when it is being called: f(), f"x", f{...}, f[[x]], f`x`.
const CALL_FOLLOWS = /^\s*(?:[("'`{]|\[=*\[)/;

export function startState() {
  return {
    mode: MODE_CODE,
    level: 0,            // number of '=' in the current long bracket
    quote: "",           // quote character of a string that continues on the next line
    interp: [],          // brace counters of open `{expr}` interpolations
    depth: 0,            // open blocks and brackets (used for indentation)
    typeMode: false,     // currently inside a type expression
    typeDepth: 0,        // nesting of () {} [] inside the type expression
    angle: 0,            // nesting of <> inside the type expression
    typeExpect: false,   // the type expression needs another operand
    typeDecl: 0,         // 1: after `type`, expecting a name; 2: after the name, expecting `<` or `=`
    funcName: false,     // reading the name of a function declaration
    declTail: false,     // the previous token was the last segment of a function name
    localDecl: false,    // reading names declared by `local`
    elseifPending: false,
    prev: "",            // kind of the previous significant token
    prevText: "",
    baseIdent: "",       // identifier in front of the last '.', for `math.floor` style lookups
  };
}

export function copyState(state) {
  return Object.assign({}, state, { interp: state.interp.slice() });
}

// Records the token that was just read and returns its style.
function done(state, kind, text, style) {
  if (!state.typeMode) {
    if (state.typeDecl === 1 && kind !== "ident") state.typeDecl = 0;
    if (state.typeDecl === 2 && !((kind === "op" && text === "=") || (kind === "open" && text === "<"))) {
      state.typeDecl = 0;
    }
  }
  if (kind !== "ident" && kind !== "comma") state.localDecl = false;
  if (kind !== "ident" && kind !== "dot" && kind !== "method-colon") state.funcName = false;
  state.declTail = false;
  state.prev = kind;
  state.prevText = text;
  return style;
}

function enterType(state) {
  state.typeMode = true;
  state.typeDepth = 0;
  state.angle = 0;
  state.typeExpect = true;
}

function endType(state) {
  state.typeMode = false;
  state.typeDepth = 0;
  state.angle = 0;
  state.typeExpect = false;
}

// ---- strings and comments ----------------------------------------------------------------------

function readQuoted(stream, state, quote) {
  state.mode = MODE_CODE;
  while (!stream.eol()) {
    const ch = stream.next();
    if (ch === "\\") {
      if (stream.eol()) {
        state.mode = MODE_QUOTED_CONTINUATION; // backslash at the end of the line continues the string
        state.quote = quote;
        return "string";
      }
      const escaped = stream.next();
      if (escaped === "z" && stream.eol()) {
        state.mode = MODE_QUOTED_CONTINUATION; // \z skips whitespace including the newline
        state.quote = quote;
        return "string";
      }
      continue;
    }
    if (ch === quote) break;
  }
  return done(state, "str", quote, "string");
}

function readLongBracket(stream, state, style) {
  const closer = "]" + "=".repeat(state.level) + "]";
  const index = stream.string.indexOf(closer, stream.pos);
  if (index < 0) {
    stream.skipToEnd();
    return style;
  }
  stream.pos = index + closer.length;
  state.mode = MODE_CODE;
  return style === "comment" ? style : done(state, "str", "", style);
}

function readBacktick(stream, state) {
  while (!stream.eol()) {
    const ch = stream.peek();
    if (ch === "\\") {
      stream.next();
      if (!stream.eol()) stream.next();
      continue;
    }
    if (ch === "`") {
      stream.next();
      state.mode = MODE_CODE;
      return done(state, "str", "`", "string");
    }
    if (ch === "{") {
      if (stream.pos > stream.start) return "string"; // end the string part; the brace starts the next token
      stream.next();
      state.interp.push(0);
      state.mode = MODE_CODE;
      return done(state, "open", "{", "meta");
    }
    stream.next();
  }
  return "string";
}

// ---- identifiers and keywords --------------------------------------------------------------------

function readKeyword(state, word) {
  switch (word) {
    case "function": {
      done(state, "kw", word, "");
      state.depth++;
      state.funcName = true;
      return "definitionKeyword";
    }
    case "local": {
      done(state, "kw", word, "");
      state.localDecl = true;
      return "definitionKeyword";
    }
    case "do":
    case "repeat": {
      done(state, "kw", word, "");
      state.depth++;
      return "controlKeyword";
    }
    case "then": {
      done(state, "kw", word, "");
      if (state.elseifPending) state.elseifPending = false;
      else state.depth++;
      return "controlKeyword";
    }
    case "elseif": {
      done(state, "kw", word, "");
      state.elseifPending = true;
      return "controlKeyword";
    }
    case "end":
    case "until": {
      done(state, "kw", word, "");
      state.depth = Math.max(0, state.depth - 1);
      return "controlKeyword";
    }
    case "nil":
      return done(state, "kw", word, "null");
    case "true":
    case "false":
      return done(state, "kw", word, "bool");
    case "and":
    case "or":
    case "not":
      return done(state, "kw", word, "operatorKeyword");
    default:
      return done(state, "kw", word, CONTROL_KEYWORDS.has(word) ? "controlKeyword" : "keyword");
  }
}

function readIdentifier(stream, state, word) {
  const rest = stream.string.slice(stream.pos);
  const prev = state.prev;
  const afterDot = prev === "dot";
  const afterColon = prev === "method-colon";
  const callFollows = CALL_FOLLOWS.test(rest);

  if (!afterDot && !afterColon) {
    if (state.typeDecl === 1) {
      const style = done(state, "ident", word, "typeName.definition");
      state.typeDecl = 2;
      return style;
    }
    if (word === "export" && /^\s+type\s+[A-Za-z_]/.test(rest)) {
      return done(state, "kw", word, "moduleKeyword");
    }
    if (word === "type" && prev !== "ident" && /^\s+[A-Za-z_]\w*\s*(?:<|=(?!=))/.test(rest)) {
      const style = done(state, "kw", word, "definitionKeyword");
      state.typeDecl = 1;
      return style;
    }
    if (KEYWORDS.has(word)) return readKeyword(state, word);
  }

  if (state.funcName) {
    if (/^\s*[.:]/.test(rest)) return done(state, "ident", word, "variableName");
    const style = done(state, "ident", word, "variableName.definition.function");
    state.declTail = true;
    return style;
  }

  if (state.localDecl && !afterDot && !afterColon) {
    return done(state, "ident", word, "variableName.definition");
  }

  if (afterDot || afterColon) {
    let style;
    if (ROBLOX_METHODS.has(word)) {
      style = "robloxMethod";
    } else if (afterDot && LIBRARY_MEMBERS.has(`${state.baseIdent}.${word}`)) {
      style = "luauBuiltinMember";
    } else if (afterDot && ROBLOX_GLOBALS.has(state.baseIdent)) {
      style = state.baseIdent === "Enum" ? "robloxGlobal" : (callFollows ? "robloxMethod" : "propertyName");
    } else {
      style = callFollows ? "propertyName.function" : "propertyName";
    }
    return done(state, "ident", word, style);
  }

  if (word === "self") return done(state, "ident", word, "self");

  let style;
  if (ROBLOX_GLOBALS.has(word) || ROBLOX_GLOBAL_FUNCTIONS.has(word)) style = "robloxGlobal";
  else if (GLOBAL_FUNCTIONS.has(word) || CUSTOM_GLOBAL_FUNCTIONS.has(word) || LIBRARY_NAMES.has(word)) style = "luauBuiltin";
  else style = callFollows ? "variableName.function" : "variableName";
  return done(state, "ident", word, style);
}

// ---- type expressions ------------------------------------------------------------------------------

// Decides, without consuming anything, whether the type expression ended before the next token.
function shouldEndType(stream, state) {
  if (state.typeDepth > 0 || state.angle > 0) return false;
  const rest = stream.string.slice(stream.pos);
  const ch = rest.charAt(0);
  if (ch === "") return false;
  if (ch === "," || ch === ")" || ch === "}" || ch === "]" || ch === ";" || ch === "=" || ch === ">") return true;
  if (!state.typeExpect) {
    // A complete type: only an operator can continue it.
    if (/[A-Za-z_0-9"'`({\[:]/.test(ch)) return true;
    if (ch === "-" && rest.charAt(1) !== ">") return true;
    if (/[+*\/%^#~<]/.test(ch)) return true;
    return false;
  }
  // An operand is expected but the next token cannot start a type.
  if (/[+*\/%^#~]/.test(ch)) return true;
  const word = IDENTIFIER.exec(rest);
  return word !== null && STATEMENT_KEYWORDS.has(word[0]);
}

function readTypeToken(stream, state) {
  const ch = stream.peek();

  if (ch === '"' || ch === "'") {
    stream.next();
    state.typeExpect = false;
    return readQuoted(stream, state, ch);
  }
  if (stream.match("->")) {
    state.typeExpect = true;
    return done(state, "op", "->", "typeOperator");
  }
  if (ch === "|" || ch === "&") {
    stream.next();
    state.typeExpect = true;
    return done(state, "op", ch, "typeOperator");
  }
  if (ch === "?") {
    stream.next();
    state.typeExpect = false;
    return done(state, "op", ch, "typeOperator");
  }
  if (ch === "(" || ch === "{" || ch === "[") {
    stream.next();
    state.typeDepth++;
    state.depth++;
    state.typeExpect = true;
    return done(state, "open", ch, ch === "{" ? "brace" : "bracket");
  }
  if (ch === ")" || ch === "}" || ch === "]") {
    stream.next();
    state.typeDepth = Math.max(0, state.typeDepth - 1);
    state.depth = Math.max(0, state.depth - 1);
    state.typeExpect = false;
    return done(state, "close", ch, ch === "}" ? "brace" : "bracket");
  }
  if (ch === "<") {
    stream.next();
    state.angle++;
    state.typeExpect = true;
    return done(state, "open", ch, "angleBracket");
  }
  if (ch === ">") {
    stream.next();
    state.angle = Math.max(0, state.angle - 1);
    state.typeExpect = false;
    return done(state, "close", ch, "angleBracket");
  }
  if (ch === ",") {
    stream.next();
    state.typeExpect = true;
    return done(state, "comma", ch, "punctuation");
  }
  if (stream.match("...")) {
    state.typeExpect = true;
    return done(state, "op", "...", "operator");
  }
  if (ch === ".") {
    stream.next();
    state.typeExpect = true;
    return done(state, "dot", ".", "derefOperator");
  }
  if (ch === ":" || ch === "=") {
    stream.next();
    if (ch === ":" && stream.peek() === ":") stream.next();
    state.typeExpect = true;
    return done(state, "colon", ch, "punctuation");
  }

  const match = stream.match(IDENTIFIER);
  if (match) {
    const word = match[0];
    const rest = stream.string.slice(stream.pos);
    // `name:` inside a table type or function type is a field / parameter name.
    if (state.typeDepth > 0 && /^\s*:(?!:)/.test(rest)) {
      return done(state, "ident", word, "propertyName");
    }
    if ((word === "read" || word === "write") && /^\s+[A-Za-z_]\w*\s*:/.test(rest)) {
      return done(state, "kw", word, "modifier");
    }
    if (word === "typeof") {
      state.typeExpect = true;
      return done(state, "kw", word, "keyword");
    }
    state.typeExpect = false;
    if (word === "true" || word === "false") return done(state, "kw", word, "bool");
    if (BUILTIN_TYPES.has(word)) return done(state, "ident", word, "luauType");
    if (ROBLOX_CLASS_NAMES.has(word)) return done(state, "ident", word, "className");
    return done(state, "ident", word, "typeName");
  }

  stream.next();
  return done(state, "op", ch, "operator");
}

// ---- code tokens --------------------------------------------------------------------------------------

// `:` starts a type annotation, except in method calls (obj:method(...)) and method names.
function readColon(stream, state) {
  if (stream.match("::")) {
    enterType(state);
    return done(state, "op", "::", "typeOperator");
  }
  stream.next();
  const rest = stream.string.slice(stream.pos);
  const isMethod = state.funcName || /^[A-Za-z_]\w*\s*(?:[("'`{]|\[=*\[)/.test(rest);
  if (isMethod || (state.prev !== "ident" && state.prev !== "close")) {
    return done(state, "method-colon", ":", "operator");
  }
  enterType(state);
  return done(state, "colon", ":", "operator");
}

function readCodeToken(stream, state) {
  const ch = stream.peek();
  let match;

  if (ch === '"' || ch === "'") {
    stream.next();
    return readQuoted(stream, state, ch);
  }
  if (ch === "`") {
    stream.next();
    state.mode = MODE_BACKTICK;
    return readBacktick(stream, state);
  }
  if ((match = stream.match(/^\[(=*)\[/))) {
    state.mode = MODE_LONG_STRING;
    state.level = match[1].length;
    return readLongBracket(stream, state, "string");
  }
  if (/[0-9]/.test(ch) || (ch === "." && /[0-9]/.test(stream.string.charAt(stream.pos + 1)))) {
    if (!stream.match(/^0[xX][0-9a-fA-F_]+/) && !stream.match(/^0[bB][01_]+/)) {
      stream.match(/^(?:\d[\d_]*(?:\.[\d_]*)?|\.\d[\d_]*)(?:[eE][+-]?\d[\d_]*)?/);
    }
    return done(state, "num", "", "number");
  }
  if ((match = stream.match(IDENTIFIER))) return readIdentifier(stream, state, match[0]);
  if (ch === "@" && stream.match(/^@[A-Za-z_]\w*/)) return done(state, "attr", "", "meta");
  if (ch === ":") return readColon(stream, state);

  if (stream.match("...")) return done(state, "op", "...", "operator");
  if (stream.match("..=")) return done(state, "op", "..=", "updateOperator");
  if (stream.match("//=")) return done(state, "op", "//=", "updateOperator");
  for (const op of ["+=", "-=", "*=", "/=", "%=", "^="]) {
    if (stream.match(op)) return done(state, "op", op, "updateOperator");
  }
  for (const op of ["==", "~=", "<=", ">="]) {
    if (stream.match(op)) return done(state, "op", op, "compareOperator");
  }
  if (stream.match("//")) return done(state, "op", "//", "arithmeticOperator");
  if (stream.match("..")) return done(state, "op", "..", "operator");
  if (stream.match("->")) return done(state, "op", "->", "typeOperator");

  stream.next();
  switch (ch) {
    case ".": {
      const base = state.prev === "ident" ? state.prevText : "";
      const style = done(state, "dot", ".", "derefOperator");
      state.baseIdent = base;
      return style;
    }
    case "=": {
      const startsType = state.typeDecl === 2;
      const style = done(state, "op", "=", "definitionOperator");
      if (startsType) {
        state.typeDecl = 0;
        enterType(state);
      }
      return style;
    }
    case "<": {
      if (state.declTail || state.typeDecl === 2) {
        enterType(state);
        state.angle = 1;
        return done(state, "open", "<", "angleBracket");
      }
      return done(state, "op", "<", "compareOperator");
    }
    case ">":
      return done(state, "op", ">", "compareOperator");
    case "(":
    case "[":
      state.depth++;
      return done(state, "open", ch, "bracket");
    case ")":
    case "]":
      state.depth = Math.max(0, state.depth - 1);
      return done(state, "close", ch, "bracket");
    case "{":
      if (state.interp.length > 0) state.interp[state.interp.length - 1]++;
      state.depth++;
      return done(state, "open", "{", "brace");
    case "}": {
      if (state.interp.length > 0) {
        const top = state.interp.length - 1;
        if (state.interp[top] === 0) {
          state.interp.pop();
          state.mode = MODE_BACKTICK;
          return done(state, "close", "}", "meta");
        }
        state.interp[top]--;
      }
      state.depth = Math.max(0, state.depth - 1);
      return done(state, "close", "}", "brace");
    }
    case ",":
      return done(state, "comma", ",", "punctuation");
    case ";":
      return done(state, "semi", ";", "punctuation");
    case "#":
      return done(state, "op", "#", "operator");
    default:
      return done(state, "op", ch, "arithmeticOperator");
  }
}

export function token(stream, state) {
  switch (state.mode) {
    case MODE_BLOCK_COMMENT:
      return readLongBracket(stream, state, "comment");
    case MODE_LONG_STRING:
      return readLongBracket(stream, state, "string");
    case MODE_BACKTICK:
      return readBacktick(stream, state);
    case MODE_QUOTED_CONTINUATION:
      return readQuoted(stream, state, state.quote);
    default:
      break;
  }

  if (stream.eatSpace()) return null;

  let match;
  if ((match = stream.match(/^--\[(=*)\[/))) {
    state.mode = MODE_BLOCK_COMMENT;
    state.level = match[1].length;
    return readLongBracket(stream, state, "comment");
  }
  if (stream.match(/^--!/, false)) {
    stream.skipToEnd();
    return "meta"; // --!strict, --!nonstrict, --!native ...
  }
  if (stream.match("--")) {
    stream.skipToEnd();
    return "comment";
  }

  if (state.typeMode && shouldEndType(stream, state)) endType(state);
  return state.typeMode ? readTypeToken(stream, state) : readCodeToken(stream, state);
}

// Indentation for the line that is about to be typed or reindented.
export function indent(state, textAfter, context) {
  if (state.mode !== MODE_CODE) return null;
  let depth = state.depth;
  if (/^\s*(?:end\b|else\b|elseif\b|until\b|[)}\]])/.test(textAfter)) depth -= 1;
  return Math.max(0, depth) * context.unit;
}

export const languageData = {
  commentTokens: { line: "--", block: { open: "--[[", close: "]]" } },
  indentOnInput: /^\s*(?:end|else|elseif|until|\}|\)|\])$/,
  closeBrackets: { brackets: ["(", "[", "{", '"', "'", "`"] },
  wordChars: "_",
};

// Exposed for tests.
export const internals = { MODE_CODE, STATEMENT_KEYWORDS };
