package ch.zhaw.it.pm4.javer.compiler.backend.vm;

import ch.zhaw.it.pm4.javer.compiler.backend.OutputArtifact;
import ch.zhaw.it.pm4.javer.compiler.backend.PreparedProgram;
import ch.zhaw.it.pm4.javer.compiler.frontend.CheckedProgram;
import ch.zhaw.it.pm4.javer.compiler.diagnostics.DiagnosticBag;
import java.util.Optional;

/** Binds VM layout data to exactly the program it was computed for. */
final class VmPreparedProgram implements PreparedProgram {
    private final CheckedProgram program;
    private final VmMetadata metadata;

    VmPreparedProgram(CheckedProgram program, VmMetadata metadata) {
        this.program = program;
        this.metadata = metadata;
    }

    @Override
    public String dumpSymbolTable() {
        return new VmSymbolTableAstPrinter(metadata).printToString(program.compilationUnit());
    }

    @Override
    public Optional<OutputArtifact> generate(DiagnosticBag diagnostics) {
        return new VmCodeGenerator(diagnostics, metadata).generate(program);
    }
}
