package ch.zhaw.it.pm4.javer.compiler.backend.vm;

import ch.zhaw.it.pm4.javer.compiler.backend.*;
import ch.zhaw.it.pm4.javer.compiler.ir.Ir;
import ch.zhaw.it.pm4.javer.compiler.diagnostics.*;

final class VmPreparedProgram implements PreparedProgram {
    private final Ir.Program program;
    private final VmMetadata metadata;
    VmPreparedProgram(Ir.Program program, VmMetadata metadata) { this.program = program; this.metadata = metadata; }
    @Override public String dumpSymbolTable() { return new VmSymbolTablePrinter().print(program, metadata); }
    @Override public PhaseResult<OutputArtifact> generate(DiagnosticBag diagnostics) {
        return new VmCodeGenerator(diagnostics, program, metadata).generate();
    }
}
