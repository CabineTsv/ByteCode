// Language-level Luau data: keywords, standard libraries and Roblox names.
// Used by the tokenizer (highlighting) and by autocompletion.
import { CLASSES, CREATABLE, DATATYPES } from "./roblox-api.js";

const words = (text) => text.split(/\s+/).filter(Boolean);

export const KEYWORDS = new Set(words(
  "and break continue do else elseif end false for function if in local nil not or repeat return then true until while"));
export const CONTROL_KEYWORDS = new Set(words("if then else elseif end for while do repeat until return break continue in"));
// Keywords that can never be part of a type expression (they start or end statements).
export const STATEMENT_KEYWORDS = new Set(words(
  "local return end if then do else elseif for while until repeat break continue and or"));

export const BUILTIN_TYPES = new Set(words(
  "any boolean buffer never nil number string thread unknown vector userdata table function"));

// Functions and values defined by the Luau VM itself (plus warn, which the ByteCode runtime provides).
export const GLOBAL_FUNCTIONS = new Set(words(
  "assert collectgarbage error gcinfo getfenv getmetatable ipairs newproxy next pairs pcall print rawequal rawget rawlen rawset select setfenv setmetatable tonumber tostring type typeof unpack warn xpcall _G _VERSION"));

export const CUSTOM_GLOBAL_FUNCTIONS = new Set(words(
  "getconnections hookfunction hookmetamethod getupvalues getgc"));

// Standard Luau libraries.
export const LIBRARIES = {
  math: words("abs acos asin atan atan2 ceil clamp cos cosh deg exp floor fmod frexp ldexp log log10 map max min modf noise pi pow rad random randomseed round sign sin sinh sqrt tan tanh huge"),
  string: words("byte char find format gmatch gsub len lower match pack packsize rep reverse split sub unpack upper"),
  table: words("clear clone concat create find freeze getn insert isfrozen maxn move pack remove sort unpack"),
  os: words("clock date difftime time"),
  coroutine: words("close create isyieldable resume running status wrap yield"),
  utf8: words("char charpattern codepoint codes len offset"),
  bit32: words("arshift band bnot bor btest bxor byteswap countlz countrz extract lrotate lshift replace rrotate rshift"),
  buffer: words("copy create fill fromstring len readf32 readf64 readi16 readi32 readi8 readstring readu16 readu32 readu8 tostring writef32 writef64 writei16 writei32 writei8 writestring writeu16 writeu32 writeu8"),
  debug: words("info traceback"),
};

// Libraries that only exist in Roblox.
export const ROBLOX_LIBRARIES = {
  task: words("cancel defer delay desynchronize synchronize spawn wait"),
};

export const LIBRARY_NAMES = new Set(Object.keys(LIBRARIES));
export const LIBRARY_MEMBERS = new Set();
for (const [library, members] of Object.entries({ ...LIBRARIES, ...ROBLOX_LIBRARIES })) {
  for (const member of members) LIBRARY_MEMBERS.add(`${library}.${member}`);
}

// Constants inside libraries (everything else is a function).
export const LIBRARY_CONSTANTS = new Set(["math.pi", "math.huge", "utf8.charpattern"]);

// Roblox globals. They are highlighted distinctively and raise a compatibility error when used in the
// standalone runtime.
export const ROBLOX_GLOBALS = new Set(words(
  "game workspace script plugin shared settings UserSettings stats task Instance Enum Vector3 Vector2 Vector3int16 Vector2int16 CFrame UDim UDim2 Color3 BrickColor Ray Region3 Region3int16 Rect TweenInfo RaycastParams OverlapParams NumberRange NumberSequence NumberSequenceKeypoint ColorSequence ColorSequenceKeypoint PhysicalProperties Random DateTime Font Axes Faces"));
export const ROBLOX_GLOBAL_FUNCTIONS = new Set(words("wait spawn delay tick time elapsedTime require printidentity version"));

// Method names that are highlighted as Roblox API: every method of the curated classes and datatypes.
export const ROBLOX_METHODS = new Set();
for (const entry of Object.values(CLASSES)) {
  for (const method of words(entry[1])) ROBLOX_METHODS.add(method);
}
for (const datatype of Object.values(DATATYPES)) {
  for (const method of words(datatype.methods)) ROBLOX_METHODS.add(method);
}

// Class names used for type annotations (`local part: Part`).
export const ROBLOX_CLASS_NAMES = new Set([...Object.keys(CLASSES), ...CREATABLE]);
