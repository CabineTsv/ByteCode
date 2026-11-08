import { StateField } from "@codemirror/state";

const WORD = /(?<![\p{L}\p{N}_$])[\p{L}_$][\p{L}\p{N}_$]*/gu;
const MIN_WORD_LENGTH = 3;
const MAX_WORD_LENGTH = 64;
const RECENT_WINDOW = 60;

export const MAX_INDEXED_CHARS = 1500000;
export const RESULT_LIMIT = 300;
export const WORD_BEFORE_CURSOR = /[\p{L}_$][\p{L}\p{N}_$]*$/u;
export const WORD_CHARS_ONLY = /^[\p{L}\p{N}_$]*$/u;

export class IdentifierIndex {
  constructor() {
    this.counts = new Map();
    this.lastSeen = new Map();
    this.tick = 0;
    this.doc = null;
    this.disabled = false;
  }

  clear() {
    this.counts.clear();
    this.lastSeen.clear();
    this.tick = 0;
  }

  addLine(text) {
    for (const match of text.matchAll(WORD)) {
      const word = match[0];
      if (word.length < MIN_WORD_LENGTH || word.length > MAX_WORD_LENGTH) continue;
      this.counts.set(word, (this.counts.get(word) || 0) + 1);
      this.lastSeen.set(word, ++this.tick);
    }
  }

  removeLine(text) {
    for (const match of text.matchAll(WORD)) {
      const word = match[0];
      const count = this.counts.get(word);
      if (count === undefined) continue;
      if (count <= 1) {
        this.counts.delete(word);
        this.lastSeen.delete(word);
      } else {
        this.counts.set(word, count - 1);
      }
    }
  }

  rebuild(doc) {
    this.clear();
    this.doc = doc;
    this.disabled = doc.length > MAX_INDEXED_CHARS;
    if (this.disabled) return;
    const lines = doc.iterLines();
    for (let step = lines.next(); !step.done; step = lines.next()) this.addLine(step.value);
  }

  advance(oldDoc, newDoc, changes) {
    if (this.disabled && newDoc.length <= MAX_INDEXED_CHARS) {
      this.rebuild(newDoc);
      return;
    }
    if (newDoc.length > MAX_INDEXED_CHARS) {
      this.clear();
      this.disabled = true;
      this.doc = newDoc;
      return;
    }
    const removed = new Set();
    const added = new Set();
    changes.iterChangedRanges((fromA, toA, fromB, toB) => {
      const firstOld = oldDoc.lineAt(fromA).number;
      const lastOld = oldDoc.lineAt(toA).number;
      for (let number = firstOld; number <= lastOld; number++) removed.add(number);
      const firstNew = newDoc.lineAt(fromB).number;
      const lastNew = newDoc.lineAt(toB).number;
      for (let number = firstNew; number <= lastNew; number++) added.add(number);
    });
    for (const number of removed) this.removeLine(oldDoc.line(number).text);
    for (const number of added) this.addLine(newDoc.line(number).text);
    this.doc = newDoc;
  }
}

const indexField = StateField.define({
  create() {
    return { index: new IdentifierIndex() };
  },
  update(value, transaction) {
    if (!transaction.docChanged) return value;
    const { index } = value;
    if (index.doc !== null && index.doc === transaction.startState.doc) {
      index.advance(transaction.startState.doc, transaction.newDoc, transaction.changes);
    }
    return value;
  },
});

export const identifierIndexExtension = indexField;

export function identifierIndex(state) {
  const value = state.field(indexField, false);
  if (!value) return null;
  const { index } = value;
  if (index.doc !== state.doc) index.rebuild(state.doc);
  return index.disabled ? null : index;
}

export function wordAt(state, pos) {
  const line = state.doc.lineAt(pos);
  const offset = pos - line.from;
  for (const match of line.text.matchAll(WORD)) {
    const start = match.index;
    const end = start + match[0].length;
    if (start <= offset && offset <= end) return match[0];
    if (start > offset) break;
  }
  return "";
}

export function matchesLoosely(word, lowerPrefix) {
  if (lowerPrefix === "") return true;
  const lower = word.toLowerCase();
  let at = 0;
  for (let i = 0; i < lowerPrefix.length; i++) {
    at = lower.indexOf(lowerPrefix[i], at);
    if (at < 0) return false;
    at++;
  }
  return true;
}

export function relevance(word, prefix, lowerPrefix, count, recentGap) {
  let score = Math.log2(count + 1);
  if (prefix !== "") {
    if (word.startsWith(prefix)) score += 12;
    else if (word.toLowerCase().startsWith(lowerPrefix)) score += 8;
    else score += 2;
  }
  if (recentGap < RECENT_WINDOW) score += 3;
  else if (recentGap < RECENT_WINDOW * 10) score += 1;
  return score;
}

export function boostFor(count, recentGap) {
  const frequency = Math.min(2, Math.log2(count) * 0.5);
  const recent = recentGap < RECENT_WINDOW ? 1 : 0;
  return -3 + frequency + recent;
}

export function suggestIdentifiers(state, options) {
  const {
    pos, prefix = "", caseSensitive = true, limit = RESULT_LIMIT, skip = null, type = "text", detail = null,
  } = options;
  const index = identifierIndex(state);
  if (!index) return { options: [], truncated: false };

  const lowerPrefix = prefix.toLowerCase();
  const current = wordAt(state, pos);
  const candidates = [];
  const byFolded = caseSensitive ? null : new Map();

  for (const [word, count] of index.counts) {
    const effective = word === current ? count - 1 : count;
    if (effective <= 0 || word === prefix) continue;
    if (skip && skip.has(word)) continue;
    if (!matchesLoosely(word, lowerPrefix)) continue;
    const recentGap = index.tick - (index.lastSeen.get(word) || 0);
    const entry = { word, count: effective, recentGap, score: relevance(word, prefix, lowerPrefix, effective, recentGap) };
    if (byFolded) {
      const folded = word.toLowerCase();
      const existing = byFolded.get(folded);
      if (existing && existing.score >= entry.score) continue;
      byFolded.set(folded, entry);
    } else {
      candidates.push(entry);
    }
  }

  const pool = byFolded ? [...byFolded.values()] : candidates;
  pool.sort((a, b) => b.score - a.score || (a.word < b.word ? -1 : 1));
  const truncated = pool.length > limit;
  const chosen = truncated ? pool.slice(0, limit) : pool;
  const result = chosen.map((entry) => {
    const option = { label: entry.word, type, boost: boostFor(entry.count, entry.recentGap) };
    if (detail) option.detail = detail;
    return option;
  });
  return { options: result, truncated };
}

export function validForPrefix(prefix, truncated) {
  if (!truncated) return WORD_CHARS_ONLY;
  return (text) => text === prefix;
}

export function followsDigit(state, from) {
  if (from <= 0) return false;
  return /[0-9]/.test(state.sliceDoc(from - 1, from));
}
