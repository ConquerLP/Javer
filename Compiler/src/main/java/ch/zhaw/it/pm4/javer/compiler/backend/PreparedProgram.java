package ch.zhaw.it.pm4.javer.compiler.backend;

import ch.zhaw.it.pm4.javer.compiler.diagnostics.DiagnosticBag;
import java.util.Optional;

/** Owns one checked program and its target preparation; no separate prepare call is needed. */
public interface PreparedProgram {
    String dumpSymbolTable();

    Optional<OutputArtifact> generate(DiagnosticBag diagnostics);
}
