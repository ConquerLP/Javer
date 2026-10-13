package ch.zhaw.it.pm4.javer.compiler.codegen.vm;

import ch.zhaw.it.pm4.javer.compiler.ast.nodes.CompilationUnit;
import ch.zhaw.it.pm4.javer.compiler.codegen.TargetBackend;
import ch.zhaw.it.pm4.javer.compiler.codegen.TargetCodeGenerator;
import ch.zhaw.it.pm4.javer.compiler.misc.diagnostics.DiagnosticBag;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.FunctionEntry;
import java.util.Arrays;
import java.util.List;

/**
 * VM backend with VM-specific layout preparation.
 */
public final class VmBackend implements TargetBackend {

    private VmMetadata metadata;

    @Override
    public List<FunctionEntry> createBuiltInSymbols() {
        return Arrays.stream(VmBuiltInFunction.values()).map(VmBuiltInFunction::createSymbol).toList();
    }

    @Override
    public void prepare(CompilationUnit node, DiagnosticBag diagnostics) {
        metadata = new VmMetadata();
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
