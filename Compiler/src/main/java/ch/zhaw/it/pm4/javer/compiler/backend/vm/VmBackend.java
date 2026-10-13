package ch.zhaw.it.pm4.javer.compiler.backend.vm;

import ch.zhaw.it.pm4.javer.compiler.backend.TargetBackend;
import ch.zhaw.it.pm4.javer.compiler.backend.PreparedProgram;
import ch.zhaw.it.pm4.javer.compiler.frontend.BuiltInSignature;
import ch.zhaw.it.pm4.javer.compiler.frontend.CheckedProgram;
import ch.zhaw.it.pm4.javer.compiler.diagnostics.DiagnosticBag;
import java.util.Arrays;
import java.util.List;

/** Stateless VM target. Every preparation owns fresh VM metadata. */
public final class VmBackend implements TargetBackend {
    @Override
    public List<BuiltInSignature> builtInSignatures() {
        return Arrays.stream(VmBuiltInFunction.values()).map(VmBuiltInFunction::signature).toList();
    }

    @Override
    public PreparedProgram prepare(CheckedProgram program, DiagnosticBag diagnostics) {
        VmMetadata metadata = new VmMetadata();
        new VmPreparationVisitor(metadata).visit(program.compilationUnit());
        return new VmPreparedProgram(program, metadata);
    }
}
