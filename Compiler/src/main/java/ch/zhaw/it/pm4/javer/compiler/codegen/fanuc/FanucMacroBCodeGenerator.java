package ch.zhaw.it.pm4.javer.compiler.codegen.fanuc;

import ch.zhaw.it.pm4.javer.compiler.ast.nodes.CompilationUnit;
import ch.zhaw.it.pm4.javer.compiler.codegen.TextCodeGenerator;
import ch.zhaw.it.pm4.javer.compiler.misc.diagnostics.DiagnosticBag;

/**
 * Bare minimum FANUC Macro B target placeholder.
 */
public final class FanucMacroBCodeGenerator extends TextCodeGenerator {

    public FanucMacroBCodeGenerator(DiagnosticBag diagnostics, String outputFilePath) {
        super(diagnostics, outputFilePath);
    }

    @Override
    protected void emit(CompilationUnit node) {
    }
}
