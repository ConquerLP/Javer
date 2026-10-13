package ch.zhaw.it.pm4.javer.compiler.backend;

import ch.zhaw.it.pm4.javer.compiler.diagnostics.DiagnosticBag;
import ch.zhaw.it.pm4.javer.compiler.diagnostics.PhaseResult;

/** An immutable IR program bound to its validated target preparation. */
public interface PreparedProgram {
    String dumpSymbolTable();
    PhaseResult<OutputArtifact> generate(DiagnosticBag diagnostics);
}
