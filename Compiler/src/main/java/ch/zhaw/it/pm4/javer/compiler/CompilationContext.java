package ch.zhaw.it.pm4.javer.compiler;

import ch.zhaw.it.pm4.javer.compiler.source.SourceCache;
import ch.zhaw.it.pm4.javer.compiler.diagnostics.DiagnosticBag;

/**
 * Mutable context shared by compiler phases during one compilation run.
 */
public class CompilationContext {

    private final DiagnosticBag diagnostics;
    private final SourceCache sourceCache;

    /**
     * Creates a compilation context from the phase-shared objects.
     *
     * @param diagnostics diagnostic collector for this run
     * @param sourceCache cached source text and source file metadata
     */
    public CompilationContext(
            DiagnosticBag diagnostics,
            SourceCache sourceCache) {

        this.diagnostics = diagnostics;
        this.sourceCache = sourceCache;
    }

    public DiagnosticBag getDiagnosticBag() {
        return diagnostics;
    }

    public SourceCache getSourceCache() {
        return sourceCache;
    }
}
