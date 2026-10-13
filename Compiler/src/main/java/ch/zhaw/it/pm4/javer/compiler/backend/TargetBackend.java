package ch.zhaw.it.pm4.javer.compiler.backend;

import ch.zhaw.it.pm4.javer.compiler.frontend.BuiltInSignature;
import ch.zhaw.it.pm4.javer.compiler.frontend.CheckedProgram;
import ch.zhaw.it.pm4.javer.compiler.diagnostics.DiagnosticBag;
import java.util.List;

/** Describes a target and creates isolated preparation state for each checked program. */
public interface TargetBackend {
    default List<BuiltInSignature> builtInSignatures() {
        return List.of();
    }

    PreparedProgram prepare(CheckedProgram program, DiagnosticBag diagnostics);
}
