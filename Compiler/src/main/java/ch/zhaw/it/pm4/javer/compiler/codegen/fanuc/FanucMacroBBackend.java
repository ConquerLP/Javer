package ch.zhaw.it.pm4.javer.compiler.codegen.fanuc;

import ch.zhaw.it.pm4.javer.compiler.ast.nodes.CompilationUnit;
import ch.zhaw.it.pm4.javer.compiler.codegen.TargetBackend;
import ch.zhaw.it.pm4.javer.compiler.codegen.TargetCodeGenerator;
import ch.zhaw.it.pm4.javer.compiler.misc.diagnostics.DiagnosticBag;

/**
 * FANUC backend hook. Target-specific preparation can be added here later.
 */
public final class FanucMacroBBackend implements TargetBackend {

    @Override
    public void prepare(CompilationUnit node, DiagnosticBag diagnostics) {
    }

    @Override
    public TargetCodeGenerator createCodeGenerator(DiagnosticBag diagnostics, String outputFilePath) {
        return new FanucMacroBCodeGenerator(diagnostics, outputFilePath);
    }
}
