#pragma once
#include <string>
#include <vector>
#include "Luau/Lexer.h"
namespace Luau {
struct Position { unsigned int line, column; };
struct Location { Position begin, end; };
struct ParseOptions { bool allowDeclarationSyntax = false; };
class ParseError {
public:
    const Location& getLocation() const { return location; }
    const std::string& getMessage() const { return message; }
private:
    Location location; std::string message;
};
struct ParseResult { std::vector<ParseError> errors; };
class Parser {
public:
    static ParseResult parse(const char* buffer, size_t bufferSize, AstNameTable& names, Allocator& allocator,
                             ParseOptions options = ParseOptions());
};
}
