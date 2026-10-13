package ch.zhaw.it.pm4.javer.compiler.diagnostics;

import ch.zhaw.it.pm4.javer.compiler.source.SourceRange;

/** A broken compiler invariant, distinct from an unsupported source feature. */
public final class CompilerInvariantException extends RuntimeException {
    private final SourceRange source;
    public CompilerInvariantException(SourceRange source, String message) {
        super(message);
        this.source = source;
    }
    public SourceRange source() { return source; }
}
