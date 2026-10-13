package ch.zhaw.it.pm4.javer.compiler.backend;

import ch.zhaw.it.pm4.javer.compiler.frontend.CheckedProgram;
import java.util.Optional;

/** Emits an in-memory artifact from a checked program; performs no filesystem operations. */
public interface TargetCodeGenerator {
    Optional<OutputArtifact> generate(CheckedProgram program);
}
