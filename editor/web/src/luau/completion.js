// Autocompletion for Luau: keywords, standard libraries, Roblox API (curated subset), snippets,
// document words and chained member completion (`game:GetService("Players").LocalPlayer.`).
import { syntaxTree } from "@codemirror/language";
import { snippetCompletion } from "@codemirror/autocomplete";
import {
  KEYWORDS, GLOBAL_FUNCTIONS, CUSTOM_GLOBAL_FUNCTIONS, LIBRARY_NAMES, ROBLOX_GLOBALS, ROBLOX_GLOBAL_FUNCTIONS, BUILTIN_TYPES,
} from "./data.js";
import { SERVICES, CREATABLE, CLASSES, DATATYPES } from "./roblox-api.js";
import { analyzeCursor, inferVariableTypes, parseReceiver, resolveType, membersForType } from "./analysis.js";
import { suggestIdentifiers, validForPrefix } from "../identifiers.js";

const MAX_SCAN_CHARS = 300000;

const COMPLETION_TYPE = {
  method: "method", property: "property", event: "property", function: "function", constant: "constant", enum: "enum",
};

// [label, template, description]
const SNIPPETS = [
  ["fn", "local function ${name}(${params})\n\t${}\nend", "local function"],
  ["fun", "function ${name}(${params})\n\t${}\nend", "function"],
  ["if", "if ${condition} then\n\t${}\nend", "if … then"],
  ["ife", "if ${condition} then\n\t${}\nelse\n\t${}\nend", "if … else"],
  ["fori", "for ${i}, ${v} in ipairs(${list}) do\n\t${}\nend", "for … in ipairs"],
  ["forp", "for ${key}, ${value} in pairs(${table}) do\n\t${}\nend", "for … in pairs"],
  ["fornum", "for ${i} = ${start}, ${stop} do\n\t${}\nend", "numeric for"],
  ["while", "while ${condition} do\n\t${}\nend", "while loop"],
  ["repeat", "repeat\n\t${}\nuntil ${condition}", "repeat … until"],
  ["pcall", "local ${ok}, ${result} = pcall(function()\n\t${}\nend)", "protected call"],
  ["class", "local ${Class} = {}\n${Class}.__index = ${Class}\n\nfunction ${Class}.new(${params})\n\tlocal self = setmetatable({}, ${Class})\n\t${}\n\treturn self\nend", "metatable class"],
  ["typedef", "type ${Name} = {\n\t${field}: ${string},\n}", "type alias"],
  ["service", "local ${Players} = game:GetService(\"${Players}\")", "Roblox: GetService"],
  ["connect", "${signal}:Connect(function(${})\n\t${}\nend)", "Roblox: Connect handler"],
  ["playeradded", "game:GetService(\"Players\").PlayerAdded:Connect(function(player)\n\t${}\nend)", "Roblox: PlayerAdded"],
  ["heartbeat", "game:GetService(\"RunService\").Heartbeat:Connect(function(dt)\n\t${}\nend)", "Roblox: Heartbeat"],
  ["tween", "local tween = game:GetService(\"TweenService\"):Create(${instance}, TweenInfo.new(${time}), { ${Property} = ${value} })\ntween:Play()", "Roblox: tween"],
  ["instnew", "local ${part} = Instance.new(\"${Part}\")\n${part}.Parent = ${parent}", "Roblox: Instance.new"],
  ["waitchild", "local ${child} = ${parent}:WaitForChild(\"${Name}\")", "Roblox: WaitForChild"],
  ["remote", "${remote}.OnServerEvent:Connect(function(player, ${})\n\t${}\nend)", "Roblox: OnServerEvent"],
];

const staticWordOptions = [
  ...[...KEYWORDS].map((label) => ({ label, type: "keyword" })),
  ...[...GLOBAL_FUNCTIONS].map((label) => ({ label, type: label.startsWith("_") ? "variable" : "function", detail: "Luau" })),
  ...[...CUSTOM_GLOBAL_FUNCTIONS].map((label) => ({ label, type: "function", detail: "environment" })),
  ...[...LIBRARY_NAMES].map((label) => ({ label, type: "namespace", detail: "library" })),
  { label: "task", type: "namespace", detail: "Roblox" },
  ...[...ROBLOX_GLOBALS].filter((label) => label !== "task").map((label) => ({ label, type: "variable", detail: "Roblox" })),
  ...[...ROBLOX_GLOBAL_FUNCTIONS].map((label) => ({ label, type: "function", detail: "Roblox" })),
  ...SNIPPETS.map(([label, template, detail]) => snippetCompletion(template, { label, detail, type: "text", boost: -2 })),
];

function insideStringOrComment(state, pos) {
  const node = syntaxTree(state).resolveInner(pos, -1);
  return /string|comment/i.test(node.name);
}

const staticLabels = new Set(staticWordOptions.map((option) => option.label));

function dynamicIdentifiers(state, pos, prefix) {
  return suggestIdentifiers(state, {
    pos,
    prefix,
    caseSensitive: true,
    skip: staticLabels,
    type: "variable",
    detail: "document",
  });
}

function stringArgumentCompletions(analysis, pos) {
  const names = analysis.fn === "GetService" ? SERVICES : analysis.fn === "Instance.new" ? CREATABLE : Object.keys(CLASSES);
  return {
    from: pos - analysis.prefix.length,
    options: names.map((label) => ({ label, type: "class", detail: analysis.fn === "GetService" ? "service" : "class" })),
    validFor: /^\w*$/,
  };
}

function memberCompletions(context, analysis, pos) {
  const { state } = context;
  const variableTypes = inferVariableTypes(state.doc.sliceString(0, Math.min(state.doc.length, MAX_SCAN_CHARS)));
  const type = resolveType(parseReceiver(analysis.receiver), variableTypes);
  const members = membersForType(type, analysis.separator);
  if (members.length === 0) {
    if (analysis.prefix === "" && !context.explicit) return null;
    const dynamic = dynamicIdentifiers(state, pos, analysis.prefix);
    if (dynamic.options.length === 0) return null;
    return {
      from: pos - analysis.prefix.length,
      options: dynamic.options,
      validFor: validForPrefix(analysis.prefix, dynamic.truncated),
    };
  }
  return {
    from: pos - analysis.prefix.length,
    options: members.map((member) => ({
      label: member.label,
      type: COMPLETION_TYPE[member.kind] || "property",
      detail: member.from,
    })),
    validFor: /^\w*$/,
  };
}

function typeCompletions(state, analysis, pos) {
  const declared = [];
  const text = state.doc.sliceString(0, Math.min(state.doc.length, MAX_SCAN_CHARS));
  for (const match of text.matchAll(/\btype\s+([A-Za-z_]\w*)/g)) declared.push(match[1]);
  const options = [
    ...[...BUILTIN_TYPES].map((label) => ({ label, type: "type", detail: "Luau type" })),
    ...Object.keys(CLASSES).map((label) => ({ label, type: "class", detail: "Roblox class" })),
    ...Object.keys(DATATYPES).filter((label) => !(label in CLASSES)).map((label) => ({ label, type: "type", detail: "Roblox datatype" })),
    ...[...new Set(declared)].map((label) => ({ label, type: "type", detail: "type alias" })),
  ];
  return { from: pos - analysis.prefix.length, options, validFor: /^\w*$/ };
}

export function luauCompletionSource(context) {
  const { state, pos } = context;
  const line = state.doc.lineAt(pos);
  const analysis = analyzeCursor(line.text.slice(0, pos - line.from));
  if (!analysis) return null;
  if (analysis.kind !== "string-arg" && insideStringOrComment(state, pos)) return null;

  switch (analysis.kind) {
    case "string-arg":
      return stringArgumentCompletions(analysis, pos);
    case "member":
      return memberCompletions(context, analysis, pos);
    case "type":
      if (analysis.prefix === "" && !context.explicit) return null;
      return typeCompletions(state, analysis, pos);
    default: {
      if (analysis.prefix === "" && !context.explicit) return null;
      const dynamic = dynamicIdentifiers(state, pos, analysis.prefix);
      return {
        from: pos - analysis.prefix.length,
        options: [...staticWordOptions, ...dynamic.options],
        validFor: validForPrefix(analysis.prefix, dynamic.truncated),
      };
    }
  }
}
