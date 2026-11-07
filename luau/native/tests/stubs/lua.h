#pragma once
#include <stddef.h>
#include <stdint.h>
struct lua_State;
typedef int (*lua_CFunction)(lua_State* L);
typedef int (*lua_Continuation)(lua_State* L, int status);
typedef void* (*lua_Alloc)(void* ud, void* ptr, size_t osize, size_t nsize);
#define LUA_OK 0
#define LUA_YIELD 1
#define LUA_ERRRUN 2
#define LUA_ERRSYNTAX 3
#define LUA_ERRMEM 4
#define LUA_ERRERR 5
#define LUA_BREAK 6
#define LUA_GLOBALSINDEX (-10002)
#define LUA_TSTRING 5
struct lua_Callbacks {
    void* userdata;
    void (*interrupt)(lua_State* L, int gc);
};
lua_State* lua_newstate(lua_Alloc f, void* ud);
void lua_close(lua_State* L);
lua_State* lua_newthread(lua_State* L);
int lua_gettop(lua_State* L);
void lua_settop(lua_State* L, int idx);
#define lua_pop(L, n) lua_settop(L, -(n)-1)
const char* lua_tolstring(lua_State* L, int idx, size_t* len);
#define lua_tostring(L, i) lua_tolstring(L, (i), NULL)
int lua_type(lua_State* L, int idx);
const char* lua_typename(lua_State* L, int tp);
#define luaL_typename(L, i) lua_typename(L, lua_type(L, (i)))
void lua_pushnil(lua_State* L);
void lua_pushcclosurek(lua_State* L, lua_CFunction fn, const char* debugname, int nup, lua_Continuation cont);
#define lua_pushcfunction(L, fn, debugname) lua_pushcclosurek(L, (fn), (debugname), 0, NULL)
void lua_setfield(lua_State* L, int idx, const char* k);
#define lua_setglobal(L, s) lua_setfield(L, LUA_GLOBALSINDEX, (s))
void lua_createtable(lua_State* L, int narr, int nrec);
#define lua_newtable(L) lua_createtable(L, 0, 0)
int lua_setmetatable(lua_State* L, int objindex);
int lua_resume(lua_State* L, lua_State* from, int narg);
const char* lua_debugtrace(lua_State* L);
void lua_rawcheckstack(lua_State* L, int sz);
lua_Callbacks* lua_callbacks(lua_State* L);
int luau_load(lua_State* L, const char* chunkname, const char* data, size_t size, int env);
