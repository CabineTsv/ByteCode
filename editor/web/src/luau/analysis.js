// Lightweight, regex-based analysis used by Luau autocompletion.
// It guesses the Roblox class of expressions such as `game:GetService("Players").LocalPlayer`
// and of local variables (`local part = Instance.new("Part")`). This is NOT a type checker.
// No CodeMirror imports here, so the module is unit-tested in Node.
import {
  CLASSES, DATATYPES, ENUMS, SERVICES, MEMBER_TYPES, METHOD_RETURNS, CLASS_FROM_ARGUMENT,
} from "./roblox-api.js";
import { LIBRARIES, ROBLOX_LIBRARIES } from "./data.js";

const words = (text) => text.split(/\s+/).filter(Boolean);
const SERVICE_SET = new Set(SERVICES);
const ALL_LIBRARIES = { ...LIBRARIES, ...ROBLOX_LIBRARIES };

export function isKnownClass(name) {
  return Object.prototype.hasOwnProperty.call(CLASSES, name);
}

// Parses the expression that ends at the end of `text`, root first:
//   game:GetService("Players").LocalPlayer -> [game, GetService("Players"), LocalPlayer]
export function parseReceiver(text) {
  const segments = [];
  let end = text.length;
  while (end > 0) {
    let args = null;
    if (text[end - 1] === ")") {
      let depth = 0;
      let open = end - 1;
      for (; open >= 0; open--) {
        if (text[open] === ")") depth++;
        else if (text[open] === "(") {
          depth--;
          if (depth === 0) break;
        }
      }
      if (open < 0) break;
      args = text.slice(open + 1, end - 1);
      end = open;
    }
    const name = /([A-Za-z_]\w*)$/.exec(text.slice(0, end));
    if (!name) break;
    segments.unshift({ name: name[1], args });
    end -= name[1].length;
    const separator = /([.:])\s*$/.exec(text.slice(0, end));
    if (!separator) break;
    end -= separator[0].length;
  }
  return segments;
}

function stringArgument(args) {
  if (args === null) return null;
  const match = /^\s*["']([^"']+)["']/.exec(args);
  return match ? match[1] : null;
}

function superclassChain(name) {
  const chain = [];
  let current = name;
  while (current && isKnownClass(current) && !chain.includes(current)) {
    chain.push(current);
    current = CLASSES[current][0];
  }
  return chain;
}

function typeOfMember(className, member) {
  for (const cls of superclassChain(className)) {
    const known = MEMBER_TYPES[`${cls}.${member}`];
    if (known) return known;
  }
  return null;
}

function isMemberOf(className, member) {
  for (const cls of superclassChain(className)) {
    const [, methods, props, events] = CLASSES[cls];
    if ([methods, props, events].some((list) => words(list).includes(member))) return true;
  }
  return false;
}

// Resolves a parsed receiver to {kind, name}:
//   class (Roblox instance), library, datatype (Vector3 statics), datatypeInstance, enumRoot, enumCategory
export function resolveType(segments, variableTypes = new Map()) {
  if (segments.length === 0) return null;
  const root = segments[0].name;
  let current = null;

  if (variableTypes.has(root)) current = { kind: "class", name: variableTypes.get(root) };
  else if (root === "game") current = { kind: "class", name: "DataModel" };
  else if (root === "workspace") current = { kind: "class", name: "Workspace" };
  else if (root === "script") current = { kind: "class", name: "Script" };
  else if (root === "Enum") current = { kind: "enumRoot" };
  else if (Object.prototype.hasOwnProperty.call(ALL_LIBRARIES, root)) current = { kind: "library", name: root };
  else if (Object.prototype.hasOwnProperty.call(DATATYPES, root)) current = { kind: "datatype", name: root };
  else return null;

  for (let i = 1; i < segments.length; i++) {
    const { name, args } = segments[i];
    switch (current.kind) {
      case "class": {
        if (CLASS_FROM_ARGUMENT.has(name)) {
          const requested = stringArgument(args);
          current = { kind: "class", name: requested && (isKnownClass(requested) || SERVICE_SET.has(requested)) ? requested : "Instance" };
          if (!isKnownClass(current.name)) current = { kind: "class", name: "Instance" };
          break;
        }
        if (Object.prototype.hasOwnProperty.call(METHOD_RETURNS, name)) {
          const returned = METHOD_RETURNS[name];
          if (returned === null) return null;
          current = { kind: "class", name: returned === "$self" ? current.name : returned };
          break;
        }
        const known = typeOfMember(current.name, name);
        if (known) {
          current = { kind: "class", name: known };
          break;
        }
        if (SERVICE_SET.has(name) && (current.name === "DataModel" || current.name === "Instance")) {
          current = { kind: "class", name: isKnownClass(name) ? name : "Instance" };
          break;
        }
        if (isMemberOf(current.name, name)) return null; // a known member whose type is not tracked
        current = { kind: "class", name: "Instance" };    // anything else is treated as a child instance
        break;
      }
      case "datatype": {
        if (current.name === "Instance" && name === "new") {
          const created = stringArgument(args);
          return created && isKnownClass(created) ? { kind: "class", name: created } : { kind: "class", name: "Instance" };
        }
        if (words(DATATYPES[current.name].statics).includes(name)) {
          current = { kind: "datatypeInstance", name: current.name };
          break;
        }
        return null;
      }
      case "datatypeInstance": {
        if (current.name === "CFrame" && ["Position", "LookVector", "RightVector", "UpVector"].includes(name)) {
          current = { kind: "datatypeInstance", name: "Vector3" };
          break;
        }
        return null;
      }
      case "enumRoot": {
        if (Object.prototype.hasOwnProperty.call(ENUMS, name)) {
          current = { kind: "enumCategory", name };
          break;
        }
        return null;
      }
      default:
        return null;
    }
  }
  return current;
}

const CALL = "\\((?:[^()]|\\([^()]*\\))*\\)";
const CHAIN = new RegExp(`^[A-Za-z_]\\w*(?:${CALL})?(?:\\s*[.:]\\s*[A-Za-z_]\\w*(?:${CALL})?)*$`);

function classOfExpression(expression, variableTypes) {
  const cleaned = expression.replace(/--.*$/, "").replace(/;\s*$/, "").trim();
  if (!CHAIN.test(cleaned)) return null;
  const type = resolveType(parseReceiver(cleaned), variableTypes);
  return type && type.kind === "class" ? type.name : null;
}

// Maps local variable names to Roblox classes using declarations, annotations and parameter types.
export function inferVariableTypes(text, limit = 300000) {
  const types = new Map();
  const source = text.length > limit ? text.slice(0, limit) : text;

  const declaration = /\blocal\s+([A-Za-z_]\w*)\s*(?::\s*([A-Za-z_]\w*)\s*)?(?:=([^\n]*))?/g;
  for (const match of source.matchAll(declaration)) {
    const [, name, annotated, expression] = match;
    let type = null;
    if (annotated && isKnownClass(annotated)) type = annotated;
    else if (expression) type = classOfExpression(expression, types);
    if (type) types.set(name, type);
    else types.delete(name);
  }

  for (const match of source.matchAll(/([A-Za-z_]\w*)\s*:\s*([A-Za-z_]\w*)\s*[,)=]/g)) {
    const [, name, annotated] = match;
    if (isKnownClass(annotated) && !types.has(name)) types.set(name, annotated);
  }
  return types;
}

// Members of a class including inherited ones: [{label, kind: "method" | "property" | "event"}]
export function classMembers(className) {
  const members = new Map();
  for (const cls of superclassChain(className)) {
    const [, methods, props, events] = CLASSES[cls];
    for (const name of words(methods)) if (!members.has(name)) members.set(name, { label: name, kind: "method", from: cls });
    for (const name of words(props)) if (!members.has(name)) members.set(name, { label: name, kind: "property", from: cls });
    for (const name of words(events)) if (!members.has(name)) members.set(name, { label: name, kind: "event", from: cls });
  }
  return [...members.values()];
}

// Completion candidates for a resolved type. `separator` is "." or ":".
export function membersForType(type, separator) {
  if (!type) return [];
  switch (type.kind) {
    case "class": {
      const all = classMembers(type.name);
      return separator === ":" ? all.filter((m) => m.kind === "method") : all;
    }
    case "library": {
      const constants = new Set(["pi", "huge", "charpattern"]);
      return ALL_LIBRARIES[type.name].map((name) => ({ label: name, kind: constants.has(name) ? "constant" : "function", from: type.name }));
    }
    case "datatype": {
      return words(DATATYPES[type.name].statics).map((name) => ({ label: name, kind: /^[a-z]*$/.test(name) && name !== "new" && !/^(now|random|palette)/.test(name) ? "constant" : "function", from: type.name }));
    }
    case "datatypeInstance": {
      const datatype = DATATYPES[type.name];
      const props = words(datatype.props).map((name) => ({ label: name, kind: "property", from: type.name }));
      const methods = words(datatype.methods).map((name) => ({ label: name, kind: "method", from: type.name }));
      return separator === ":" ? methods : [...props, ...methods];
    }
    case "enumRoot":
      return Object.keys(ENUMS).map((name) => ({ label: name, kind: "enum", from: "Enum" }));
    case "enumCategory":
      return words(ENUMS[type.name]).map((name) => ({ label: name, kind: "constant", from: `Enum.${type.name}` }));
    default:
      return [];
  }
}

// Looks at the text before the cursor and says what kind of completion applies.
export function analyzeCursor(before) {
  const stringArg = /(?:\b(GetService)|\b(Instance\.new)|\b(IsA|FindFirstChildOfClass|FindFirstChildWhichIsA|FindFirstAncestorOfClass|FindFirstAncestorWhichIsA))\s*\(\s*(["'])([A-Za-z0-9_]*)$/.exec(before);
  if (stringArg) {
    return { kind: "string-arg", fn: stringArg[1] || stringArg[2] || stringArg[3], prefix: stringArg[5] };
  }

  const member = /([.:])([A-Za-z_]\w*)?$/.exec(before);
  if (member) {
    const preceding = before.slice(0, member.index);
    if (member[1] === "." && preceding.endsWith(".")) return null;              // `..` concatenation
    if (member[1] === ":" && preceding.endsWith(":")) {                           // `::` cast
      return { kind: "type", prefix: member[2] || "" };
    }
    if (member[1] === ":" && !/[A-Za-z_0-9)\]]$/.test(preceding)) return null;
    return { kind: "member", separator: member[1], prefix: member[2] || "", receiver: preceding };
  }

  const annotation = /(?:[^:]:\s+|^:\s+|::\s*|->\s*)([A-Za-z_]\w*)?$/.exec(before);
  if (annotation) return { kind: "type", prefix: annotation[1] || "" };

  const word = /([A-Za-z_]\w*)$/.exec(before);
  return { kind: "word", prefix: word ? word[1] : "" };
}
