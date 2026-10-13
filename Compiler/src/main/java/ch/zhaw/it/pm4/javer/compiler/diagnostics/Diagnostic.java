package ch.zhaw.it.pm4.javer.compiler.diagnostics;

import ch.zhaw.it.pm4.javer.compiler.source.SourceLocation;

/**
 * Represents a single user-facing issue discovered during compilation.
 *
 * <p>Examples:</p>
 *
 * <ul>
 * <li>invalid character (lexer)</li>
 * <li>unexpected token (parser)</li>
 * <li>unknown identifier (name resolution)</li>
 * <li>type mismatch (type check)</li>
 * </ul>
 */
public record Diagnostic(SourceLocation location, Severity severity, String message) {

    @Override
    public String toString() {
        return String.format("[%s] at %s: %s", severity.name(), location.toString(), message);
    }
}
