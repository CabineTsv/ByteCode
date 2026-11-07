// Node module hooks: map CodeMirror and Lezer packages to fakes/stubs so the editor glue can run in Node.
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

const here = new URL(".", import.meta.url);
const fakeUrl = new URL("./fake-cm.mjs", here).href;
const runtimeUrl = new URL("./stub-runtime.mjs", here).href;
const sourceRoot = fileURLToPath(new URL("../src/", here));

function sourceFiles(directory) {
  const files = [];
  for (const entry of readdirSync(directory)) {
    const path = join(directory, entry);
    if (statSync(path).isDirectory()) files.push(...sourceFiles(path));
    else if (path.endsWith(".js")) files.push(path);
  }
  return files;
}

// Names imported from any package anywhere in the sources, plus names used through dynamic imports.
const names = new Set([
  "javascript", "json", "jsonParseLinter", "html", "css", "python", "java", "cpp", "php", "rust", "sql", "xml",
  "markdown", "kotlin", "csharp", "ruby", "go", "shell", "lua", "yaml",
]);
for (const file of sourceFiles(sourceRoot)) {
  const text = readFileSync(file, "utf8");
  for (const match of text.matchAll(/import\s*\{([^}]*)\}\s*from\s*"(@codemirror|@lezer)[^"]*"/g)) {
    for (const part of match[1].split(",")) {
      const name = part.trim().split(/\s+as\s+/)[0];
      if (name) names.add(name);
    }
  }
}
const stubSource = `import { makeStub } from "${runtimeUrl}";\n` +
  [...names].map((name) => `export const ${name} = makeStub(${JSON.stringify(name)});`).join("\n");

export async function resolve(specifier, context, nextResolve) {
  if (specifier === "@codemirror/state" || specifier === "@codemirror/view") {
    return { url: fakeUrl, shortCircuit: true };
  }
  if (specifier.startsWith("@codemirror/") || specifier.startsWith("@lezer/")) {
    return { url: `stub:${specifier}`, shortCircuit: true };
  }
  return nextResolve(specifier, context);
}

export async function load(url, context, nextLoad) {
  if (url.startsWith("stub:")) return { format: "module", source: stubSource, shortCircuit: true };
  return nextLoad(url, context);
}
