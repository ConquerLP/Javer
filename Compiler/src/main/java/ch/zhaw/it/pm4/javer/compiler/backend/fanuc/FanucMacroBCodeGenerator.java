package ch.zhaw.it.pm4.javer.compiler.backend.fanuc;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.CompilationUnit;
import ch.zhaw.it.pm4.javer.compiler.backend.TextCodeGenerator;
import ch.zhaw.it.pm4.javer.compiler.diagnostics.DiagnosticBag;

/**
 * Bare minimum FANUC Macro B target placeholder.
 */
public final class FanucMacroBCodeGenerator extends TextCodeGenerator {

    public FanucMacroBCodeGenerator(DiagnosticBag diagnostics) {
        super(diagnostics);
    }

    @Override
    protected void emit(CompilationUnit node) {
    }
}
