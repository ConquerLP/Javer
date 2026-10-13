package ch.zhaw.it.pm4.javer.compiler.codegen;

import ch.zhaw.it.pm4.javer.compiler.ast.nodes.CompilationUnit;
import ch.zhaw.it.pm4.javer.compiler.misc.diagnostics.DiagnosticBag;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.FunctionEntry;
import ch.zhaw.it.pm4.javer.compiler.visitor.SymbolTableAstPrinter;
import java.util.List;

/**
 * Owns target-specific preparation and code generation.
 */
public interface TargetBackend {

    /** Creates fresh semantic signatures for the runtime functions of this target. */
    default List<FunctionEntry> createBuiltInSymbols() {
        return List.of();
    }

    /** Prepares backend-owned state from a checked AST without modifying frontend data. */
    void prepare(CompilationUnit node, DiagnosticBag diagnostics);

    default String dumpSymbolTable(CompilationUnit node) {
        return new SymbolTableAstPrinter().printToString(node);
    }

    TargetCodeGenerator createCodeGenerator(DiagnosticBag diagnostics, String outputFilePath);
}
