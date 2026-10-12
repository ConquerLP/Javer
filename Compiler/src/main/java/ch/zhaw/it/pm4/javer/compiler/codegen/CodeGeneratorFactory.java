package ch.zhaw.it.pm4.javer.compiler.codegen;

import ch.zhaw.it.pm4.javer.compiler.CompilationTarget;
import ch.zhaw.it.pm4.javer.compiler.codegen.fanuc.FanucMacroBCodeGenerator;
import ch.zhaw.it.pm4.javer.compiler.codegen.vm.VmCodeGenerator;
import ch.zhaw.it.pm4.javer.compiler.misc.diagnostics.DiagnosticBag;

/**
 * Creates the code generator for the selected compilation target.
 */
public final class CodeGeneratorFactory {

    private CodeGeneratorFactory() {
    }

    public static TargetCodeGenerator create(
            CompilationTarget target,
            DiagnosticBag diagnostics,
            String outputFilePath) {

        return switch (target) {
            case VM -> new VmCodeGenerator(diagnostics, outputFilePath);
            case FANUC_MACRO_B -> new FanucMacroBCodeGenerator(diagnostics, outputFilePath);
        };
    }
}
