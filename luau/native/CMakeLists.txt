cmake_minimum_required(VERSION 3.22.1)
project(bytecode_luau LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# --- Official Luau sources (cloned by the Gradle task `fetchLuau`) --------------------------------
set(LUAU_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/third_party/luau")
if(NOT EXISTS "${LUAU_SOURCE_DIR}/CMakeLists.txt")
    message(FATAL_ERROR
        "Luau sources not found in ${LUAU_SOURCE_DIR}.\n"
        "Run `gradle :luau:fetchLuau` or clone https://github.com/luau-lang/luau there.")
endif()

set(BYTECODE_LUAU_TAG "unknown")
if(EXISTS "${LUAU_SOURCE_DIR}/.bytecode-tag")
    file(READ "${LUAU_SOURCE_DIR}/.bytecode-tag" BYTECODE_LUAU_TAG)
    string(STRIP "${BYTECODE_LUAU_TAG}" BYTECODE_LUAU_TAG)
endif()

# Only the VM, compiler and parser are needed. EXCLUDE_FROM_ALL keeps the (large) analysis
# libraries out of the build; only what bytecode_luau links against is compiled.
set(LUAU_BUILD_CLI OFF CACHE BOOL "" FORCE)
set(LUAU_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LUAU_BUILD_WEB OFF CACHE BOOL "" FORCE)
set(LUAU_WERROR OFF CACHE BOOL "" FORCE)
add_subdirectory("${LUAU_SOURCE_DIR}" luau-build EXCLUDE_FROM_ALL)

# --- JNI library ------------------------------------------------------------------------------
add_library(bytecode_luau SHARED
    src/jni_bridge.cpp
    src/luau_runtime.cpp
    src/luau_diagnostics.cpp)

target_include_directories(bytecode_luau PRIVATE src)
target_compile_definitions(bytecode_luau PRIVATE BYTECODE_LUAU_TAG="${BYTECODE_LUAU_TAG}")
target_compile_options(bytecode_luau PRIVATE -fvisibility=hidden -Wall -Wextra)
target_link_options(bytecode_luau PRIVATE -Wl,--exclude-libs,ALL -Wl,-z,max-page-size=16384)
target_link_libraries(bytecode_luau PRIVATE Luau.Compiler Luau.VM Luau.Ast)
