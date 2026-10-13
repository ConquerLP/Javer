package ch.zhaw.it.pm4.javer.compiler.backend;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.CompilationUnit;
import ch.zhaw.it.pm4.javer.compiler.frontend.CheckedProgram;
import ch.zhaw.it.pm4.javer.compiler.diagnostics.DiagnosticBag;
import ch.zhaw.it.pm4.javer.compiler.diagnostics.Severity;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.AstNodeVisitorBase;
import java.util.Optional;

/** Base for generators that produce text artifacts entirely in memory. */
public abstract class TextCodeGenerator extends AstNodeVisitorBase implements TargetCodeGenerator {
    protected final DiagnosticBag diagnostics;
    protected final StringBuilder output = new StringBuilder();
    private boolean failed;

    protected TextCodeGenerator(DiagnosticBag diagnostics) {
        this.diagnostics = diagnostics;
    }

    @Override
    public final Optional<OutputArtifact> generate(CheckedProgram program) {
        output.setLength(0);
        failed = false;
        try {
            reset();
            if (diagnostics.hasErrors() || diagnostics.isPhaseAbortRequested()) {
                return Optional.empty();
            }
            emit(program.compilationUnit());
            if (failed || diagnostics.hasErrors() || diagnostics.isPhaseAbortRequested()) {
                return Optional.empty();
            }
            return Optional.of(OutputArtifact.text(output.toString()));
        } finally {
            cleanup();
        }
    }

    protected void reset() { }

    protected abstract void emit(CompilationUnit node);

    protected void cleanup() { }

    protected void writeLine(String line) {
        output.append(line).append(System.lineSeparator());
    }

    protected void report(String message) {
        failed = true;
        diagnostics.add(null, Severity.ERROR, message);
    }
}
