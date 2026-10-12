package ch.zhaw.it.pm4.javer.compiler.codegen.vm;

import ch.zhaw.it.pm4.javer.compiler.ast.nodes.CompilationUnit;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.declaration.DeclarationAstNode;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.declaration.EnumDeclaration;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.declaration.FunctionDeclaration;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.declaration.StructDeclaration;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.NewExpression;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.VarDeclarationStatement;
import ch.zhaw.it.pm4.javer.compiler.ast.scope.FunctionScope;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.FieldEntry;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.FunctionEntry;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.ParameterEntry;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.EnumEntry;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.EnumValueEntry;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.VariableEntry;
import ch.zhaw.it.pm4.javer.compiler.bytecode.VmLayout;
import ch.zhaw.it.pm4.javer.compiler.visitor.AstNodeVisitorBase;

/**
 * Computes VM ABI metadata after semantic analysis and before bytecode emission.
 */
final class VmPreparationVisitor extends AstNodeVisitorBase {

    private FunctionEntry currentFunction;

    @Override
    public void visit(CompilationUnit node) {
        node.getGlobalScope().getFunctions().values().forEach(this::layoutFunctionHeader);
        for (DeclarationAstNode declaration : node.getDeclarations()) {
            if (declaration instanceof EnumDeclaration) {
                declaration.accept(this);
            }
        }
        for (DeclarationAstNode declaration : node.getDeclarations()) {
            if (declaration instanceof StructDeclaration) {
                declaration.accept(this);
            }
        }
        for (DeclarationAstNode declaration : node.getDeclarations()) {
            if (declaration instanceof FunctionDeclaration) {
                declaration.accept(this);
            }
        }
    }

    @Override
    public void visit(EnumDeclaration node) {
        EnumEntry entry = node.getSymbolEntry();
        if (entry == null || entry.getScope() == null) {
            return;
        }

        int offsetBytes = 0;
        for (EnumValueEntry value : entry.getScope().getValues().values()) {
            value.setSizeBytes(VmLayout.WORD_BYTES);
            value.setOffsetBytes(offsetBytes);
            offsetBytes += VmLayout.WORD_BYTES;
        }
        entry.setSizeBytes(offsetBytes);
    }

    @Override
    public void visit(StructDeclaration node) {
        if (node.getStructScope() == null) {
            return;
        }

        int offsetBytes = 0;
        for (FieldEntry field : node.getStructScope().getFields().values()) {
            int sizeBytes = VmLayout.memoryWidth(field.getType()).bytes();
            field.setSizeBytes(sizeBytes);
            field.setOffsetBytes(offsetBytes);
            offsetBytes += sizeBytes;
        }
        node.getStructScope().setSizeBytes(offsetBytes);
    }

    @Override
    public void visit(FunctionDeclaration node) {
        FunctionEntry previousFunction = currentFunction;
        currentFunction = node.getSymbolEntry();
        if (currentFunction != null) {
            layoutFunctionHeader(currentFunction);
        }
        super.visit(node);
        currentFunction = previousFunction;
    }

    @Override
    public void visit(VarDeclarationStatement node) {
        VariableEntry entry = node.getSymbolEntry();
        if (entry != null && currentFunction != null) {
            int sizeBytes = VmLayout.memoryWidth(entry.getType()).bytes();
            entry.setSizeBytes(sizeBytes);
            entry.setOffsetBytes(currentFunction.allocateLocalBytes(sizeBytes));
        }
        super.visit(node);
    }

    @Override
    public void visit(NewExpression node) {
        if (currentFunction != null
                && node.getArrayInit() == null
                && node.getDimensions().size() > 1
                && node.getJaggedArrayTempLayout() == null) {
            node.setJaggedArrayTempLayout(allocateJaggedArrayTempLayout(node.getDimensions().size()));
        }
        super.visit(node);
    }

    private void layoutFunctionHeader(FunctionEntry function) {
        function.resetFrameLayout();
        layoutParameters(function.getScope(), function);
    }

    private void layoutParameters(FunctionScope functionScope, FunctionEntry function) {
        if (functionScope == null) {
            return;
        }

        int parameterBytes = functionScope.getParameters().values().stream()
                .map(ParameterEntry::getType)
                .mapToInt(VmLayout::stackBytes)
                .sum();
        function.setParameterBytes(parameterBytes);

        int offsetBytes = VmLayout.FRAME_HEADER_BYTES + parameterBytes;
        for (ParameterEntry parameter : functionScope.getParameters().values()) {
            int sizeBytes = VmLayout.stackBytes(parameter.getType());
            offsetBytes -= sizeBytes;
            parameter.setSizeBytes(sizeBytes);
            parameter.setOffsetBytes(offsetBytes);
        }
    }

    private NewExpression.JaggedArrayTempLayout allocateJaggedArrayTempLayout(int dimensionCount) {
        int[] dimensionOffsets = allocateTempOffsets(dimensionCount);
        int[] baseOffsets = allocateTempOffsets(dimensionCount - 1);
        int[] indexOffsets = allocateTempOffsets(dimensionCount - 1);
        return new NewExpression.JaggedArrayTempLayout(dimensionOffsets, baseOffsets, indexOffsets);
    }

    private int[] allocateTempOffsets(int count) {
        int[] offsets = new int[count];
        for (int i = 0; i < count; i++) {
            offsets[i] = currentFunction.allocateLocalBytes(VmLayout.WORD_BYTES);
        }
        return offsets;
    }
}
