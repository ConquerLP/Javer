package ch.zhaw.it.pm4.javer.compiler.backend.fanuc;

import ch.zhaw.it.pm4.javer.compiler.backend.*;
import ch.zhaw.it.pm4.javer.compiler.frontend.CheckedProgram;
import ch.zhaw.it.pm4.javer.compiler.diagnostics.DiagnosticBag;
import ch.zhaw.it.pm4.javer.compiler.frontend.debug.SymbolTableAstPrinter;
import java.util.Optional;

/** Empty FANUC target placeholder. */
public final class FanucMacroBBackend implements TargetBackend {
    @Override
    public PreparedProgram prepare(CheckedProgram program, DiagnosticBag diagnostics) {
        return new PreparedProgram() {
            @Override
            public String dumpSymbolTable() {
                return new SymbolTableAstPrinter().printToString(program.compilationUnit());
            }

            @Override
            public Optional<OutputArtifact> generate(DiagnosticBag generationDiagnostics) {
                return new FanucMacroBCodeGenerator(generationDiagnostics).generate(program);
            }
        };
    }
}
