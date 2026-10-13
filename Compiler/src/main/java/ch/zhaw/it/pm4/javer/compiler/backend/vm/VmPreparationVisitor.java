package ch.zhaw.it.pm4.javer.compiler.backend.vm;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.CompilationUnit;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.declaration.DeclarationAstNode;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.declaration.EnumDeclaration;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.declaration.FunctionDeclaration;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.declaration.StructDeclaration;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.statement.NewExpression;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.statement.VarDeclarationStatement;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.scope.FunctionScope;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.symbol.FieldEntry;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.symbol.FunctionEntry;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.symbol.ParameterEntry;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.symbol.EnumEntry;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.symbol.EnumValueEntry;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.symbol.VariableEntry;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.AstNodeVisitorBase;

/**
 * Computes VM ABI metadata after semantic analysis and before bytecode emission.
 */
final class VmPreparationVisitor extends AstNodeVisitorBase {

    private final VmMetadata metadata;
    private FunctionEntry currentFunction;
    private int parameterBytes;
    private int localBytes;

    VmPreparationVisitor(VmMetadata metadata) {
        this.metadata = metadata;
    }

    @Override
    public void visit(CompilationUnit node) {
        node.getGlobalScope().getFunctions().values().forEach(function -> {
            layoutFunctionHeader(function);
            putFunctionLayout(function);
        });
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

            metadata.putEnumValue(value, new VmMetadata.VmEnumValueLayout(
                    VmLayout.WORD_BYTES,
                    offsetBytes,
                    enumDataLabel(entry)));
            offsetBytes += VmLayout.WORD_BYTES;
        }

        metadata.putEnum(entry, new VmMetadata.VmEnumLayout(enumDataLabel(entry), VmLayout.WORD_BYTES, offsetBytes));
    }

    @Override
    public void visit(StructDeclaration node) {
        if (node.getStructScope() == null) {
            return;
        }

        int offsetBytes = 0;
        for (FieldEntry field : node.getStructScope().getFields().values()) {
            int sizeBytes = VmLayout.memoryWidth(field.getType()).bytes();

            metadata.putStorage(field, new VmMetadata.VmStorageLayout(sizeBytes, offsetBytes));
            offsetBytes += sizeBytes;
        }
        if (node.getSymbolEntry() != null) {
            metadata.putStruct(node.getSymbolEntry(), new VmMetadata.VmStructLayout(offsetBytes));
        }
    }

    @Override
    public void visit(FunctionDeclaration node) {
        FunctionEntry previousFunction = currentFunction;
        currentFunction = node.getSymbolEntry();
        if (currentFunction != null) {
            layoutFunctionHeader(currentFunction);
        }
        super.visit(node);
        if (currentFunction != null) {
            putFunctionLayout(currentFunction);
        }
        currentFunction = previousFunction;
    }

    @Override
    public void visit(VarDeclarationStatement node) {
        VariableEntry entry = node.getSymbolEntry();
        if (entry != null && currentFunction != null) {
            int sizeBytes = VmLayout.memoryWidth(entry.getType()).bytes();

            int offsetBytes = allocateLocalBytes(sizeBytes);

            metadata.putStorage(entry, new VmMetadata.VmStorageLayout(sizeBytes, offsetBytes));
        }
        super.visit(node);
    }

    @Override
    public void visit(NewExpression node) {
        if (currentFunction != null
                && node.getArrayInit() == null
                && node.getDimensions().size() > 1) {
            metadata.putArrayTemps(node, allocateJaggedArrayTempLayout(node.getDimensions().size()));
        }
        super.visit(node);
    }

    private void layoutFunctionHeader(FunctionEntry function) {
        parameterBytes = 0;
        localBytes = 0;
        layoutParameters(function.getScope());
    }

    private void putFunctionLayout(FunctionEntry function) {
        metadata.putFunction(function, new VmMetadata.VmFunctionLayout(
                functionLabel(function),
                parameterBytes,
                localBytes,
                localBytes));
    }

    private String functionLabel(FunctionEntry function) {
        return function.isBuiltIn() ? function.getName() : "_" + function.getName();
    }

    private String enumDataLabel(EnumEntry entry) {
        return "enum_" + entry.getName() + "_values";
    }

    private void layoutParameters(FunctionScope functionScope) {
        if (functionScope == null) {
            return;
        }

        parameterBytes = functionScope.getParameters().values().stream()
                .map(ParameterEntry::getType)
                .mapToInt(VmLayout::stackBytes)
                .sum();

        int offsetBytes = VmLayout.FRAME_HEADER_BYTES + parameterBytes;
        for (ParameterEntry parameter : functionScope.getParameters().values()) {
            int sizeBytes = VmLayout.stackBytes(parameter.getType());
            offsetBytes -= sizeBytes;

            metadata.putStorage(parameter, new VmMetadata.VmStorageLayout(sizeBytes, offsetBytes));
        }
    }

    private VmMetadata.JaggedArrayTempLayout allocateJaggedArrayTempLayout(int dimensionCount) {
        int[] dimensionOffsets = allocateTempOffsets(dimensionCount);
        int[] baseOffsets = allocateTempOffsets(dimensionCount - 1);
        int[] indexOffsets = allocateTempOffsets(dimensionCount - 1);
        return new VmMetadata.JaggedArrayTempLayout(dimensionOffsets, baseOffsets, indexOffsets);
    }

    private int allocateLocalBytes(int sizeBytes) {
        int offset = localBytes;
        localBytes += sizeBytes;
        return offset;
    }

    private int[] allocateTempOffsets(int count) {
        int[] offsets = new int[count];
        for (int i = 0; i < count; i++) {
            offsets[i] = allocateLocalBytes(VmLayout.WORD_BYTES);
        }
        return offsets;
    }
}
