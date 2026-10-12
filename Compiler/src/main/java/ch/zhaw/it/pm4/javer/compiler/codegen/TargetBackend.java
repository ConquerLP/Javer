package ch.zhaw.it.pm4.javer.compiler.codegen;

import ch.zhaw.it.pm4.javer.compiler.ast.nodes.CompilationUnit;
import ch.zhaw.it.pm4.javer.compiler.misc.diagnostics.DiagnosticBag;

/**
 * Owns target-specific preparation and code generation.
 */
public interface TargetBackend {

    void prepare(CompilationUnit node, DiagnosticBag diagnostics);

    default String dumpSymbolTable(CompilationUnit node) {
        return "";
    }

    TargetCodeGenerator createCodeGenerator(DiagnosticBag diagnostics, String outputFilePath);
}
