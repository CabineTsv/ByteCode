#pragma once
#include "lua.h"
const char* luaL_tolstring(lua_State* L, int idx, size_t* len);
void luaL_openlibs(lua_State* L);
void luaL_sandbox(lua_State* L);
void luaL_sandboxthread(lua_State* L);
[[noreturn]] void luaL_errorL(lua_State* L, const char* fmt, ...) __attribute__((format(printf, 2, 3)));
#define luaL_error(L, ...) luaL_errorL(L, __VA_ARGS__)
