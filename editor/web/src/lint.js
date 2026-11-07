// Linting glue. Every lint result is also reported to the host so the app can show a Problems list.
import { linter } from "@codemirror/lint";

const MAX_LINT_CHARS = 400000;

function toReportItem(doc, diagnostic) {
  const line = doc.lineAt(Math.min(diagnostic.from, doc.length));
  return {
    line: line.number,
    column: diagnostic.from - line.from + 1,
    severity: diagnostic.severity,
    message: diagnostic.message,
  };
}

// Wraps a lint source: results are clamped to the current document and reported through `report`.
export function reportingLinter(source, report, delay = 600) {
  return linter(async (view) => {
    if (view.state.doc.length > MAX_LINT_CHARS) {
      report([]);
      return [];
    }
    let diagnostics = [];
    try {
      diagnostics = Array.from(await source(view));
    } catch (error) {
      diagnostics = [];
    }
    const doc = view.state.doc;
    const safe = diagnostics
      .filter((d) => d.from <= doc.length)
      .map((d) => ({ ...d, to: Math.min(Math.max(d.to, d.from), doc.length) }));
    report(safe.map((d) => toReportItem(doc, d)));
    return safe;
  }, { delay });
}

// Converts host diagnostics (1-based line/column) into CodeMirror diagnostics for `doc`.
export function toEditorDiagnostics(doc, items) {
  const result = [];
  for (const item of items) {
    const startLine = doc.line(Math.min(Math.max(item.line, 1), doc.lines));
    const from = Math.min(startLine.from + Math.max(item.column - 1, 0), startLine.to);
    const endNumber = Math.min(Math.max(item.endLine || item.line, 1), doc.lines);
    const endLine = doc.line(endNumber);
    let to = Math.min(endLine.from + Math.max((item.endColumn || item.column) - 1, 0), endLine.to);
    if (to <= from) to = Math.min(from + 1, doc.length);
    result.push({
      from,
      to: Math.max(to, from),
      severity: item.severity || "error",
      message: item.message,
      source: item.source || "luau",
    });
  }
  return result;
}
