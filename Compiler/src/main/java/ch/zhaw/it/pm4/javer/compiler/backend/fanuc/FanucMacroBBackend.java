package ch.zhaw.it.pm4.javer.compiler.backend.fanuc;

import ch.zhaw.it.pm4.javer.compiler.backend.*;
import ch.zhaw.it.pm4.javer.compiler.ir.Ir;
import ch.zhaw.it.pm4.javer.compiler.ir.IrPrinter;
import ch.zhaw.it.pm4.javer.compiler.diagnostics.*;

/** Deliberately empty FANUC placeholder. Add target validation and preparation here later. */
public final class FanucMacroBBackend extends TargetBackend {
    @Override protected void validate(Ir.Program program, DiagnosticBag diagnostics) { }
    @Override protected PreparedProgram prepareValidated(Ir.Program program, DiagnosticBag diagnostics) {
        return new PreparedProgram() {
            @Override public String dumpSymbolTable() { return IrPrinter.symbols(program); }
            @Override public PhaseResult<OutputArtifact> generate(DiagnosticBag generationDiagnostics) {
                return new FanucMacroBCodeGenerator(generationDiagnostics).generate();
            }
        };
    }
}
