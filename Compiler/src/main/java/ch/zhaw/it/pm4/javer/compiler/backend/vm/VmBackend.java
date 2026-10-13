package ch.zhaw.it.pm4.javer.compiler.backend.vm;

import ch.zhaw.it.pm4.javer.compiler.backend.*;
import ch.zhaw.it.pm4.javer.compiler.frontend.BuiltInSignature;
import ch.zhaw.it.pm4.javer.compiler.ir.Ir;
import ch.zhaw.it.pm4.javer.compiler.diagnostics.*;
import java.util.*;

/** VM target for the fixed Javer numeric and evaluation semantics. */
public final class VmBackend extends TargetBackend {
    @Override public List<BuiltInSignature> builtInSignatures() {
        return Arrays.stream(VmBuiltInFunction.values()).map(VmBuiltInFunction::signature).toList();
    }
    @Override protected void validate(Ir.Program program, DiagnosticBag diagnostics) {
        if (!program.semantics().equals(Ir.Semantics.JAVER)) {
            diagnostics.add(null, Severity.ERROR, "VM target does not support the requested language semantics.");
        }
        for (Ir.Signature signature : program.signatures()) {
            if (signature.builtIn()) {
                VmBuiltInFunction implementation = VmBuiltInFunction.find(signature.name());
                if (implementation == null || !signature.returnType().equals(Ir.Type.VOID)
                        || !signature.parameters().equals(List.of(implementation.parameterType()))) {
                    diagnostics.add(null, Severity.ERROR, "VM runtime function not supported: " + signature.name());
                }
            }
        }
    }
    @Override protected PreparedProgram prepareValidated(Ir.Program program, DiagnosticBag diagnostics) {
        return new VmPreparedProgram(program, VmMetadata.prepare(program));
    }
}
