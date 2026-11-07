#pragma once
#include <stddef.h>
struct lua_CompileOptions {
    int optimizationLevel;
    int debugLevel;
    int typeInfoLevel;
    int coverageLevel;
    const char* vectorLib;
    const char* vectorCtor;
    const char* vectorType;
    const char* const* mutableGlobals;
};
char* luau_compile(const char* source, size_t size, lua_CompileOptions* options, size_t* outsize);
