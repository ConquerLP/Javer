package ch.zhaw.it.pm4.javer.compiler.codegen;

import ch.zhaw.it.pm4.javer.compiler.CompilationTarget;
import ch.zhaw.it.pm4.javer.compiler.codegen.fanuc.FanucMacroBBackend;
import ch.zhaw.it.pm4.javer.compiler.codegen.vm.VmBackend;

/**
 * Creates the backend for the selected compilation target.
 */
public final class TargetBackendFactory {

    private TargetBackendFactory() {
    }

    public static TargetBackend create(CompilationTarget target) {
        return switch (target) {
            case VM -> new VmBackend();
            case FANUC_MACRO_B -> new FanucMacroBBackend();
        };
    }
}
