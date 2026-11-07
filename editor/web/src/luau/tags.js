// Custom highlight tags for Luau. They inherit from standard tags, so themes that do not know about
// them still colour Roblox/Luau names sensibly.
import { Tag, tags } from "@lezer/highlight";

export const luauTags = {
  robloxGlobal: Tag.define(tags.variableName),
  robloxMethod: Tag.define(tags.propertyName),
  luauBuiltin: Tag.define(tags.variableName),
  luauBuiltinMember: Tag.define(tags.propertyName),
  luauType: Tag.define(tags.typeName),
};
