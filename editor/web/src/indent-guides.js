import { Decoration, ViewPlugin, EditorView } from "@codemirror/view";
import { RangeSetBuilder } from "@codemirror/state";
import { getIndentUnit } from "@codemirror/language";

const LOOK_LIMIT = 60;

export function leadingColumns(text, tabSize) {
  let columns = 0;
  for (let i = 0; i < text.length; i++) {
    const character = text[i];
    if (character === " ") columns++;
    else if (character === "\t") columns += tabSize - (columns % tabSize);
    else return { columns, blank: false };
  }
  return { columns, blank: true };
}

export function guideColumns(lineText, tabSize, neighbours) {
  const own = leadingColumns(lineText, tabSize);
  if (!own.blank) return own.columns;
  return Math.max(own.columns, neighbours());
}

function nearestIndent(doc, from, step, tabSize) {
  let found = -1;
  for (let n = from, steps = 0; n >= 1 && n <= doc.lines && steps < LOOK_LIMIT; n += step, steps++) {
    const info = leadingColumns(doc.line(n).text, tabSize);
    if (!info.blank) {
      found = info.columns;
      break;
    }
  }
  return found;
}

function buildGuides(view) {
  const { state } = view;
  const doc = state.doc;
  const tabSize = state.tabSize;
  const unit = Math.max(1, getIndentUnit(state));
  const builder = new RangeSetBuilder();
  let last = 0;
  for (const { from, to } of view.visibleRanges) {
    const first = Math.max(doc.lineAt(from).number, last + 1);
    const final = doc.lineAt(to).number;
    for (let n = first; n <= final; n++) {
      const line = doc.line(n);
      const columns = guideColumns(line.text, tabSize, () => {
        const above = nearestIndent(doc, n - 1, -1, tabSize);
        const below = nearestIndent(doc, n + 1, 1, tabSize);
        return above < 0 || below < 0 ? 0 : Math.min(above, below);
      });
      if (columns > 0) {
        builder.add(line.from, line.from, Decoration.line({
          attributes: { class: "cm-bc-guides", style: `--bc-cols:${columns};--bc-unit:${unit}` },
        }));
      }
      last = n;
    }
  }
  return builder.finish();
}

class GuidesPlugin {
  constructor(view) {
    this.decorations = buildGuides(view);
  }

  update(update) {
    if (update.docChanged || update.viewportChanged || update.transactions.some((tr) => tr.reconfigured)) {
      this.decorations = buildGuides(update.view);
    }
  }
}

const guidesTheme = EditorView.baseTheme({
  ".cm-line.cm-bc-guides": {
    backgroundImage:
      "repeating-linear-gradient(to right, var(--bc-guide, rgba(128,128,128,0.3)) 0, var(--bc-guide, rgba(128,128,128,0.3)) 1px, transparent 1px, transparent calc(var(--bc-unit) * 1ch))",
    backgroundSize: "calc(var(--bc-cols) * 1ch) 100%",
    backgroundRepeat: "no-repeat",
    backgroundOrigin: "content-box",
  },
});

export function indentGuides() {
  return [ViewPlugin.fromClass(GuidesPlugin, { decorations: (plugin) => plugin.decorations }), guidesTheme];
}
