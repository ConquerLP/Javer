package ch.zhaw.it.pm4.javer.compiler.codegen;

import ch.zhaw.it.pm4.javer.compiler.ast.nodes.CompilationUnit;

/**
 * Generates target-specific code from a checked compilation unit.
 */
public interface TargetCodeGenerator {

    void generate(CompilationUnit node);
}
