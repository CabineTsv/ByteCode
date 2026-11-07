// Bundles the editor into ../src/main/assets/editor/dist with esbuild.
//
// - ES modules with code splitting: every language pack is a lazy chunk, so startup only parses the
//   core editor.
// - Output is readable by default. Set BYTECODE_MINIFY=1 (the release workflow does) to minify.
import { build } from "esbuild";
import { rmSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const here = dirname(fileURLToPath(import.meta.url));
const outdir = join(here, "..", "src", "main", "assets", "editor", "dist");
const minify = process.env.BYTECODE_MINIFY === "1";

rmSync(outdir, { recursive: true, force: true });

await build({
  absWorkingDir: here,
  entryPoints: ["src/main.js"],
  outdir,
  bundle: true,
  splitting: true,
  format: "esm",
  target: ["chrome80"],
  minify,
  sourcemap: false,
  chunkNames: "chunks/[name]-[hash]",
  logLevel: "info",
});

console.log(`Editor bundle written to ${outdir} (minify=${minify})`);
