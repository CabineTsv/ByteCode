// Syntax diagnostics from the official Luau parser (multiple errors, with columns).
// Type checking is intentionally not done here: it needs the full analysis library.
#include <string>
#include <utility>
#include <vector>

#include "Luau/Allocator.h"
#include "Luau/Lexer.h"
#include "Luau/Parser.h"
#include "luau_runtime.hpp"

namespace bytecode {

std::vector<Diagnostic> checkSyntax(const std::string& source, size_t maxDiagnostics) {
    std::vector<Diagnostic> diagnostics;

    Luau::Allocator allocator;
    Luau::AstNameTable names(allocator);
    Luau::ParseOptions options;
    Luau::ParseResult result =
        Luau::Parser::parse(source.data(), source.size(), names, allocator, options);

    for (const Luau::ParseError& error : result.errors) {
        if (diagnostics.size() >= maxDiagnostics) break;
        const Luau::Location& location = error.getLocation();
        Diagnostic diagnostic;
        diagnostic.line = static_cast<int>(location.begin.line);
        diagnostic.column = static_cast<int>(location.begin.column);
        diagnostic.endLine = static_cast<int>(location.end.line);
        diagnostic.endColumn = static_cast<int>(location.end.column);
        diagnostic.message = error.getMessage();
        diagnostics.push_back(std::move(diagnostic));
    }
    return diagnostics;
}

}  // namespace bytecode
