package ch.zhaw.it.pm4.javer.compiler.backend;

import ch.zhaw.it.pm4.javer.compiler.diagnostics.PhaseResult;

/** An emitter bound to its prepared program; no caller can substitute another tree. */
public interface TargetCodeGenerator {
    PhaseResult<OutputArtifact> generate();
}
