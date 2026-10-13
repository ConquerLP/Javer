package ch.zhaw.it.pm4.javer.compiler.backend;

import ch.zhaw.it.pm4.javer.compiler.diagnostics.*;
import ch.zhaw.it.pm4.javer.compiler.source.SourceRange;

/** Filesystem-independent text emission. Each prepared program creates a fresh emitter. */
public abstract class TextCodeGenerator implements TargetCodeGenerator {
    protected final DiagnosticBag diagnostics;
    private final StringBuilder output = new StringBuilder();

    protected TextCodeGenerator(DiagnosticBag diagnostics) { this.diagnostics = diagnostics; }

    @Override public final PhaseResult<OutputArtifact> generate() {
        if (diagnostics.hasErrors()) return PhaseResult.failure(diagnostics);
        output.setLength(0);
        try {
            emit();
            return diagnostics.hasErrors() ? PhaseResult.failure(diagnostics)
                    : PhaseResult.success(OutputArtifact.text(output.toString()));
        } catch (CompilerInvariantException exception) {
            diagnostics.add(exception.source().start(), Severity.SEVERE, "Internal compiler error: " + exception.getMessage());
            return PhaseResult.failure(diagnostics);
        } catch (ArithmeticException exception) {
            diagnostics.add(null, Severity.ERROR, "Generated target output exceeds supported size.");
            return PhaseResult.failure(diagnostics);
        }
    }
    protected abstract void emit();
    protected final void writeLine(String line) { output.append(line).append('\n'); }
    protected final void report(SourceRange source, String message) { diagnostics.add(source.start(), Severity.ERROR, message); }
}
