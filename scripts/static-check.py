#!/usr/bin/env python3
"""Static consistency checks for ByteCode's sources.

This is NOT a compiler. It catches the mistakes that are easy to make without one: unbalanced
braces, imports that do not resolve, Android resources that are referenced but not defined, JNI
names that do not match, enums that drifted apart and listener methods that were not implemented.
Run it from anywhere:  python3 scripts/static-check.py
"""
import re, glob, os, sys, collections

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
files = sorted(glob.glob(ROOT + "/**/src/main/java/**/*.kt", recursive=True))
problems = []

def _skip_string(src, i):
    n = len(src)
    while i < n:
        ch = src[i]
        if ch == "\\":
            i += 2
        elif ch == '"':
            return i + 1
        elif ch == "$" and i + 1 < n and src[i + 1] == "{":
            i = _skip_template(src, i + 2)
        else:
            i += 1
    return n

def _skip_template(src, i):
    depth, n = 1, len(src)
    while i < n and depth:
        ch = src[i]
        if ch == '"':
            i = _skip_string(src, i + 1)
            continue
        if ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
        i += 1
    return i

def strip(src):
    out, i, n = [], 0, len(src)
    while i < n:
        c = src[i]
        nxt = src[i + 1] if i + 1 < n else ""
        if c == "/" and nxt == "/":
            while i < n and src[i] != "\n":
                i += 1
        elif c == "/" and nxt == "*":
            j = src.find("*/", i + 2)
            i = n if j < 0 else j + 2
        elif src.startswith('"""', i):
            j = src.find('"""', i + 3)
            i = n if j < 0 else j + 3
            out.append('""')
        elif c == '"':
            i = _skip_string(src, i + 1)
            out.append('""')
        elif c == "'":
            if nxt == "\\":
                j = src.find("'", i + 2)
                i = n if j < 0 else j + 1
            else:
                i += 3
            out.append("'c'")
        else:
            out.append(c)
            i += 1
    return "".join(out)

decl_re = re.compile(r'^\s*(?:@\w+(?:\([^)]*\))?\s+)*(?:(?:public|private|internal|protected|abstract|open|sealed|data|enum|annotation|inner|fun|value|const)\s+)*(class|interface|object|typealias)\s+(\w+)', re.M)
fun_re = re.compile(r'^(?:@\w+\s+)*(?:(?:public|internal|inline|suspend|private)\s+)*fun\s+(?:<[^>]+>\s*)?(?:[\w.]+\.)?(\w+)\s*\(', re.M)

pkg_of, top_decls, src_of = {}, collections.defaultdict(set), {}
for f in files:
    raw = open(f).read()
    src_of[f] = raw
    m = re.search(r'^package\s+([\w.]+)', raw, re.M)
    pkg = m.group(1)
    pkg_of[f] = pkg
    body = strip(raw)
    # top-level declarations = those at brace depth 0
    depth = 0
    for line in body.split("\n"):
        if depth == 0:
            dm = decl_re.match(line)
            if dm: top_decls[pkg].add(dm.group(2))
            fm = re.match(r'^(?:(?:public|internal|inline|suspend)\s+)*fun\s+(?:[\w.<>?]+\.)?(\w+)\s*\(', line)
            if fm: top_decls[pkg].add(fm.group(1))
            vm = re.match(r'^(?:(?:public|internal|const)\s+)*(?:val|var)\s+(\w+)', line)
            if vm: top_decls[pkg].add(vm.group(1))
        depth += line.count("{") - line.count("}")

# 1. balance
for f in files:
    body = strip(src_of[f])
    for a, b in ("{}", "()", "[]"):
        if body.count(a) != body.count(b):
            problems.append(f"{os.path.relpath(f, ROOT)}: unbalanced {a}{b}: {body.count(a)} vs {body.count(b)}")

# 2. imports of project symbols
for f in files:
    for imp in re.findall(r'^import\s+(dev\.bytecode\.[\w.]+)', src_of[f], re.M):
        pkg, name = imp.rsplit(".", 1)
        if name not in top_decls.get(pkg, set()) and imp != "dev.bytecode.app.R":
            # allow nested (e.g. Outer.Inner) imports
            outer = pkg.rsplit(".", 1)
            if not (len(outer) == 2 and outer[1] in top_decls.get(outer[0], set())):
                problems.append(f"{os.path.relpath(f, ROOT)}: import {imp} does not resolve")

# 3. use of project types from other packages without import
all_types = {}
for pkg, names in top_decls.items():
    for n in names:
        if n[:1].isupper(): all_types.setdefault(n, set()).add(pkg)
for f in files:
    raw = src_of[f]; pkg = pkg_of[f]
    body = strip(raw)
    body_no_imports = re.sub(r'^import .*$', '', body, flags=re.M)
    imported = set(re.findall(r'^import\s+[\w.]+\.(\w+)\s*$', raw, re.M))
    # local declarations (nested classes) count as defined
    local = set(m.group(2) for m in decl_re.finditer(body))
    for name, pkgs in all_types.items():
        if pkg in pkgs or name in imported or name in local: continue
        if re.search(r'(?<![\w.])' + re.escape(name) + r'\b', body_no_imports):
            problems.append(f"{os.path.relpath(f, ROOT)}: uses {name} (from {sorted(pkgs)}) without import")

# 4. R usage needs import unless in package dev.bytecode.app / library namespaces
for f in files:
    raw = src_of[f]; pkg = pkg_of[f]
    body = strip(raw)
    if re.search(r'(?<![\w.])R\.(string|id|layout|drawable|color|menu|xml|style|array|mipmap|dimen)\.', body):
        if pkg != "dev.bytecode.app" and "import dev.bytecode.app.R" not in raw:
            problems.append(f"{os.path.relpath(f, ROOT)}: uses R but does not import dev.bytecode.app.R")
        if not pkg.startswith("dev.bytecode.app"):
            problems.append(f"{os.path.relpath(f, ROOT)}: library module uses app R")

# 5. interface implementation completeness for MainActivity
def interface_methods(path, outer):
    body = strip(src_of[ROOT + path])
    m = re.search(r'interface\s+Listener\s*\{([\s\S]*?)\n    \}', body)
    if not m:
        return []
    return re.findall(r'fun\s+(\w+)\s*\(', m.group(1))

main = strip(src_of[ROOT + "/app/src/main/java/dev/bytecode/app/MainActivity.kt"])
impl = {
  "CodeEditorView.Listener": "/editor/src/main/java/dev/bytecode/editor/CodeEditorView.kt",
  "DocumentManager.Listener": "/app/src/main/java/dev/bytecode/app/documents/DocumentManager.kt",
  "RunManager.Listener": "/app/src/main/java/dev/bytecode/app/run/CodeRunner.kt",
  "ExplorerController.Listener": "/app/src/main/java/dev/bytecode/app/ui/ExplorerController.kt",
  "TabStrip.Listener": "/app/src/main/java/dev/bytecode/app/ui/TabStrip.kt",
  "ConsolePanel.Listener": "/app/src/main/java/dev/bytecode/app/ui/ConsolePanel.kt",
  "ExtraKeysBar.Listener": "/app/src/main/java/dev/bytecode/app/ui/ExtraKeysBar.kt",
}
for name, path in impl.items():
    src = strip(src_of[ROOT + path])
    m = re.search(r'interface\s+Listener\s*\{([\s\S]*?)\}', src)
    methods = re.findall(r'fun\s+(\w+)\s*\(', m.group(1)) if m else []
    if not methods: problems.append(f"no interface methods found for {name}")
    for method in methods:
        if not re.search(r'override\s+fun\s+' + method + r'\s*\(', main):
            problems.append(f"MainActivity does not implement {name}.{method}")
    print(f"  {name}: {len(methods)} methods checked")

# 6. AppCommand coverage in execute()
cmd = strip(src_of[ROOT + "/app/src/main/java/dev/bytecode/app/AppCommand.kt"])
entries = re.findall(r'^\s*([A-Z_]+)\(R\.string', cmd, re.M)
start = main.index("private fun execute(command: AppCommand)")
exec_body = main[start:main.index("private fun handleEditorCommand")]
for e in entries:
    if f"AppCommand.{e} ->" not in exec_body:
        problems.append(f"execute() does not handle AppCommand.{e}")
print(f"  AppCommand entries: {len(entries)}")

# 7. manifest classes
mf = open(ROOT + "/app/src/main/AndroidManifest.xml").read()
for cls in re.findall(r'android:name="\.([\w.]+)"', mf):
    pkg, _, name = ("dev.bytecode.app." + cls).rpartition(".")
    if name not in top_decls.get(pkg, set()) and imp != "dev.bytecode.app.R":
        problems.append(f"Manifest references missing class {pkg}.{name}")

# 8. layouts referencing custom views
for lf in glob.glob(ROOT + "/app/src/main/res/layout/*.xml"):
    for tag in re.findall(r'<(dev\.bytecode[\w.]+)', open(lf).read()):
        pkg, name = tag.rsplit(".", 1)
        if name not in top_decls.get(pkg, set()) and imp != "dev.bytecode.app.R":
            problems.append(f"{os.path.basename(lf)} references missing view {tag}")

# 9. JNI names in C++ vs Kotlin externals
jni = open(ROOT + "/luau/native/src/jni_bridge.cpp").read()
kt = src_of[ROOT + "/luau/src/main/java/dev/bytecode/luau/LuauNative.kt"]
for native in re.findall(r'Java_dev_bytecode_luau_LuauNative_(\w+)\(', jni):
    if f"fun {native}(" not in kt:
        problems.append(f"JNI function {native} has no Kotlin external declaration")
for ext in re.findall(r'external fun (\w+)\(', kt):
    if f"LuauNative_{ext}(" not in jni:
        problems.append(f"Kotlin external {ext} has no JNI implementation")

# 10. Kotlin enum ordinals vs C++ enums
cpp = open(ROOT + "/luau/native/src/luau_runtime.hpp").read()
status = re.findall(r'(\w+)\s*=\s*(\d+)', re.search(r'enum class RunStatus[^{]*\{([\s\S]*?)\};', cpp).group(1))
kt_status = re.search(r'enum class LuauRunStatus \{([\s\S]*?)companion', src_of[ROOT + "/luau/src/main/java/dev/bytecode/luau/LuauModels.kt"]).group(1)
kt_names = re.findall(r'^\s*([A-Z_]+)[,;]?\s*$', kt_status, re.M)
cpp_names = [re.sub(r'(?<!^)(?=[A-Z])', '_', n).upper() for n, _ in status]
print("  RunStatus C++ :", cpp_names)
print("  RunStatus Kotlin:", kt_names)
if cpp_names != kt_names: problems.append("RunStatus enum order differs between C++ and Kotlin")


# 11. Android resources referenced from Kotlin and XML must exist
def resource_check():
    res = ROOT + "/app/src/main/res"
    used, defined = collections.defaultdict(set), collections.defaultdict(set)
    for path in files:
        for kind, name in re.findall(r"(?<![\w.])R\.(string|id|drawable|layout|menu|xml|color|style|dimen|array|mipmap)\.(\w+)", src_of[path]):
            used[kind].add(name)
    for path in glob.glob(res + "/**/*.xml", recursive=True):
        text = open(path).read()
        for kind, name in re.findall(r"@(string|id|drawable|layout|menu|xml|color|style|dimen|array|mipmap)/([\w.]+)", text):
            used[kind].add(name.replace(".", "_"))
        for name in re.findall(r"@\+id/(\w+)", text):
            defined["id"].add(name)
    for path in glob.glob(res + "/values*/*.xml"):
        text = open(path).read()
        for kind, tag in (("string", "string"), ("color", "color"), ("dimen", "dimen"), ("style", "style"), ("array", "string-array")):
            for name in re.findall(r'<%s name="([\w.]+)"' % tag, text):
                defined[kind].add(name.replace(".", "_"))
    for kind in ("drawable", "layout", "menu", "xml", "mipmap"):
        for path in glob.glob(res + "/%s*/*" % kind):
            defined[kind].add(os.path.splitext(os.path.basename(path))[0])
    # resources that come from libraries (Material, AndroidX Preference), not from this project
    library = {("style", "Widget_Material3_Button_TonalButton"), ("style", "PreferenceThemeOverlay")}
    for kind in sorted(used):
        for name in sorted(used[kind] - defined[kind]):
            if (kind, name) not in library:
                problems.append(f"resource {kind}/{name} is referenced but not defined")

resource_check()

print(f"\nchecked {len(files)} Kotlin files")
if problems:
    print("\nPROBLEMS:")
    for p in problems: print(" -", p)
else:
    print("no problems found")
sys.exit(1 if problems else 0)
