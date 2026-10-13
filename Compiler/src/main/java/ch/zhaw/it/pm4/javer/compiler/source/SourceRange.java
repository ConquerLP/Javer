package ch.zhaw.it.pm4.javer.compiler.source;

/**
 * Inclusive source span from a start location to an end location.
 *
 * @param start first source location in the span
 * @param end   last source location in the span
 */
public record SourceRange(SourceLocation start, SourceLocation end) {

    /**
     * Fallback range for nodes that have not been assigned parser locations.
     */
    public static final SourceRange UNKNOWN = new SourceRange(
            new SourceLocation(1, 1, 1),
            new SourceLocation(1, 1, 1));

    @Override
    public String toString() {
        return "[%d:%d][%d:%d]".formatted(
                start.lineNumber(),
                start.startColumn(),
                end.lineNumber(),
                end.endColumn());
    }
}
