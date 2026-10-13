package ch.zhaw.it.pm4.javer.compiler.source;

/**
 * Immutable record representing a location inside the source file.
 * Contains the start column, end column, and line number for precise error reporting.
 *
 * <p>Used by tokens, AST nodes, and diagnostics.</p>
 *
 * @param startColumn first 1-based column covered by the location
 * @param endColumn   last 1-based column covered by the location
 * @param lineNumber  1-based source line number
 */
public record SourceLocation(int startColumn, int endColumn, int lineNumber) {

    /**
     * Compact constructor to validate the source location boundaries.
     * Enforces that locations map to real, physical text coordinates (1-indexed).
     */
    public SourceLocation {
        if (startColumn < 1) {
            throw new IllegalArgumentException("Start column must be at least 1: " + startColumn);
        }
        if (endColumn < startColumn) {
            throw new IllegalArgumentException("End column cannot be less than start column: " + endColumn + " < " + startColumn);
        }
        if (lineNumber < 1) {
            throw new IllegalArgumentException("Line number must be at least 1: " + lineNumber);
        }
    }

    /**
     * Formats the location for debugging and error reporting.
     * Output format: [lineNumber : startColumn : endColumn]
     */
    @Override
    public String toString() {
        return String.format("[%d : %d : %d]", lineNumber, startColumn, endColumn);
    }
}
