package ch.zhaw.it.pm4.javer.compiler.codegen.vm;

import ch.zhaw.it.pm4.javer.compiler.ast.nodes.CompilationUnit;
import ch.zhaw.it.pm4.javer.compiler.codegen.TargetBackend;
import ch.zhaw.it.pm4.javer.compiler.codegen.TargetCodeGenerator;
import ch.zhaw.it.pm4.javer.compiler.misc.diagnostics.DiagnosticBag;

/**
 * VM backend with VM-specific layout preparation.
 */
public final class VmBackend implements TargetBackend {

    private final VmMetadata metadata = new VmMetadata();

    @Override
    public void prepare(CompilationUnit node, DiagnosticBag diagnostics) {
        new VmPreparationVisitor(metadata).visit(node);
    }

    @Override
    public String dumpSymbolTable(CompilationUnit node) {
        return new VmSymbolTableAstPrinter(metadata).printToString(node);
    }

    @Override
    public TargetCodeGenerator createCodeGenerator(DiagnosticBag diagnostics, String outputFilePath) {
        return new VmCodeGenerator(diagnostics, outputFilePath, metadata);
    }
}
