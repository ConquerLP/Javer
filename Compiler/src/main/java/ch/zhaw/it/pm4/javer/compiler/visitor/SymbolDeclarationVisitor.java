package ch.zhaw.it.pm4.javer.compiler.visitor;

import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import ch.zhaw.it.pm4.javer.compiler.ast.nodes.CompilationUnit;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.declaration.DeclarationAstNode;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.declaration.EnumDeclaration;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.declaration.EnumItem;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.declaration.FunctionDeclaration;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.declaration.FunctionParameter;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.declaration.StructDeclaration;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.declaration.StructField;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.BlockStatement;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.ForStatement;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.NewExpression;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.VarDeclarationStatement;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.type.*;
import ch.zhaw.it.pm4.javer.compiler.ast.scope.BlockScope;
import ch.zhaw.it.pm4.javer.compiler.ast.scope.EnumScope;
import ch.zhaw.it.pm4.javer.compiler.ast.scope.FunctionScope;
import ch.zhaw.it.pm4.javer.compiler.ast.scope.GlobalScope;
import ch.zhaw.it.pm4.javer.compiler.ast.scope.StructScope;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.EnumEntry;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.EnumValueEntry;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.FieldEntry;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.FunctionEntry;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.ParameterEntry;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.StructEntry;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.VariableEntry;
import ch.zhaw.it.pm4.javer.compiler.ast.typeinfo.ArrayTypeInfo;
import ch.zhaw.it.pm4.javer.compiler.ast.typeinfo.EnumTypeInfo;
import ch.zhaw.it.pm4.javer.compiler.ast.typeinfo.PrimitiveTypeInfo;
import ch.zhaw.it.pm4.javer.compiler.ast.typeinfo.StructTypeInfo;
import ch.zhaw.it.pm4.javer.compiler.ast.typeinfo.TypeInfo;
import ch.zhaw.it.pm4.javer.compiler.ast.typeinfo.TypeRules;
import ch.zhaw.it.pm4.javer.compiler.ast.typeinfo.UnknownTypeInfo;
import ch.zhaw.it.pm4.javer.compiler.ast.typeinfo.VoidTypeInfo;
import ch.zhaw.it.pm4.javer.compiler.builtin.BuiltInFunction;
import ch.zhaw.it.pm4.javer.compiler.builtin.BuiltInTypes;
import ch.zhaw.it.pm4.javer.compiler.bytecode.VmLayout;
import ch.zhaw.it.pm4.javer.compiler.misc.diagnostics.DiagnosticBag;
import ch.zhaw.it.pm4.javer.compiler.misc.diagnostics.Severity;

/**
 * Builds scopes and symbol-table entries for declarations before name
 * resolution.
 */
public class SymbolDeclarationVisitor extends AstNodeVisitorBase {

    private final DiagnosticBag diagnosticBag;
    private GlobalScope globalScope;
    private FunctionScope currentFunctionScope;
    private FunctionEntry currentFunction;
    private BlockScope currentBlock;
    private int nextVariableDeclarationOrder;
    private final Map<StructEntry, StructDeclaration> structDeclarations = new IdentityHashMap<>();
    private final Set<StructEntry> completedStructLayouts = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<StructEntry> structLayoutsInProgress = Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * Creates a declaration pass.
     *
     * @param diagnosticBag collector for duplicate symbols and invalid
     *                      declarations
     */
    public SymbolDeclarationVisitor(DiagnosticBag diagnosticBag) {
        this.diagnosticBag = diagnosticBag;
    }

    @Override
    public void visit(CompilationUnit node) {
        globalScope = node.getGlobalScope();
        structDeclarations.clear();
        completedStructLayouts.clear();
        structLayoutsInProgress.clear();

        registerBuiltInFunctions();
        BuiltInTypes.register(globalScope);
        for (DeclarationAstNode declaration : node.getDeclarations()) {
            registerTopLevelDeclaration(declaration);
        }
        validateMainFunction(node);
        for (DeclarationAstNode declaration : node.getDeclarations()) {
            if (declaration instanceof EnumDeclaration) {
                declaration.accept(this);
            }
        }
        for (DeclarationAstNode declaration : node.getDeclarations()) {
            if (declaration instanceof StructDeclaration struct) {
                declareStructLayout(struct);
            }
        }
        for (DeclarationAstNode declaration : node.getDeclarations()) {
            if (declaration instanceof FunctionDeclaration) {
                declaration.accept(this);
            }
        }
    }

    private void registerBuiltInFunctions() {
        for (BuiltInFunction builtIn : BuiltInFunction.all()) {
            globalScope.defineFunction(builtIn.createSymbol());
        }
    }

    private void registerTopLevelDeclaration(DeclarationAstNode declaration) {
        if (declaration instanceof FunctionDeclaration function) {
            FunctionEntry entry = new FunctionEntry(function.getName());
            FunctionScope scope = new FunctionScope(entry);
            entry.setScope(scope);
            function.setSymbolEntry(entry);
            function.setFunctionScope(scope);
            if (!globalScope.defineFunction(entry)) {
                String message = BuiltInFunction.find(function.getName()) != null
                        ? "Built-in function cannot be overwritten: " + function.getName()
                        : "Duplicate function: " + function.getName();
                diagnosticBag.add(function.getSourceRange().start(), Severity.ERROR, message);
            }
            return;
        }

        if (declaration instanceof StructDeclaration struct) {
            StructEntry entry = new StructEntry(struct.getName());
            StructScope scope = new StructScope();
            entry.setScope(scope);
            struct.setSymbolEntry(entry);
            struct.setStructScope(scope);
            structDeclarations.put(entry, struct);
            if (!globalScope.defineStruct(entry)) {
                String message = BuiltInTypes.isBuiltInStructName(struct.getName())
                        ? "Built-in struct cannot be overwritten: " + struct.getName()
                        : "Duplicate struct: " + struct.getName();
                diagnosticBag.add(struct.getSourceRange().start(), Severity.ERROR, message);
            }
            return;
        }

        if (declaration instanceof EnumDeclaration enumDeclaration) {
            EnumEntry entry = new EnumEntry(enumDeclaration.getName());
            EnumScope scope = new EnumScope();
            entry.setScope(scope);
            enumDeclaration.setSymbolEntry(entry);
            enumDeclaration.setEnumScope(scope);
            if (!globalScope.defineEnum(entry)) {
                String message = BuiltInTypes.isBuiltInEnumName(enumDeclaration.getName())
                        ? "Built-in enum cannot be overwritten: " + enumDeclaration.getName()
                        : "Duplicate enum: " + enumDeclaration.getName();
                diagnosticBag.add(enumDeclaration.getSourceRange().start(), Severity.ERROR, message);
            }
        }
    }

    private void validateMainFunction(CompilationUnit node) {
        int mainCount = 0;
        FunctionDeclaration mainDeclaration = null;
        for (DeclarationAstNode declaration : node.getDeclarations()) {
            if (declaration instanceof FunctionDeclaration function && "main".equals(function.getName())) {
                mainCount++;
                mainDeclaration = function;
            }
        }

        if (mainCount != 1) {
            diagnosticBag.add(node.getSourceRange().start(), Severity.ERROR,
                    "Program must declare exactly one main function with signature: fn void main().");
            return;
        }

        TypeInfo returnType = resolveType(mainDeclaration.getReturnType());
        if (!(returnType instanceof VoidTypeInfo) || !mainDeclaration.getParameters().isEmpty()) {
            diagnosticBag.add(mainDeclaration.getSourceRange().start(), Severity.ERROR,
                    "Main signature must be exactly: fn void main().");
        }
    }

    @Override
    public void visit(EnumDeclaration node) {
        EnumEntry entry = node.getSymbolEntry();
        EnumScope scope = node.getEnumScope();

        Set<Integer> usedValues = new HashSet<>();
        int nextValue = 0;
        int offsetBytes = 0;

        for (EnumItem item : node.getItems()) {
            int value = item.getValue() != null ? item.getValue() : nextValue;
            if (usedValues.contains(value)) {
                diagnosticBag.add(item.getSourceRange().start(), Severity.ERROR, "Duplicate enum value: " + value);
            }
            usedValues.add(value);

            item.setValue(value);
            EnumValueEntry valueEntry = new EnumValueEntry(
                    item.getName(),
                    entry,
                    value,
                    entry.getElementSizeBytes(),
                    offsetBytes,
                    entry.getDataLabel());
            item.setSymbolEntry(valueEntry);

            if (!scope.defineEnumValue(valueEntry)) {
                diagnosticBag.add(item.getSourceRange().start(), Severity.ERROR, "Duplicate enum item: " + item.getName());
            }

            nextValue = value + 1;
            offsetBytes += entry.getElementSizeBytes();
        }

        entry.setSizeBytes(offsetBytes);
    }

    @Override
    public void visit(FunctionDeclaration node) {
        FunctionScope previousFunctionScope = currentFunctionScope;
        FunctionEntry previousFunction = currentFunction;
        BlockScope previousBlock = currentBlock;
        int previousVariableDeclarationOrder = nextVariableDeclarationOrder;

        currentFunctionScope = node.getFunctionScope();
        currentFunction = node.getSymbolEntry();
        currentBlock = null;
        nextVariableDeclarationOrder = 0;
        currentFunction.setReturnType(resolveType(node.getReturnType()));

        defineParameters(node, currentFunctionScope, currentFunction);
        node.getBody().accept(this);

        currentFunctionScope = previousFunctionScope;
        currentFunction = previousFunction;
        currentBlock = previousBlock;
        nextVariableDeclarationOrder = previousVariableDeclarationOrder;
    }

    private void defineParameters(FunctionDeclaration node, FunctionScope functionScope, FunctionEntry function) {
        int parameterBytes = node.getParameters().stream()
                .map(this::parameterType)
                .mapToInt(VmLayout::stackBytes)
                .sum();
        function.setParameterBytes(parameterBytes);

        int offsetBytes = VmLayout.FRAME_HEADER_BYTES + parameterBytes;
        for (int i = 0; i < node.getParameters().size(); i++) {
            FunctionParameter parameter = node.getParameters().get(i);
            boolean validVariadic = parameter.isVariadic() && i == node.getParameters().size() - 1;
            if (parameter.isVariadic() && !validVariadic) {
                diagnosticBag.add(parameter.getSourceRange().start(), Severity.ERROR,
                        "Variadic parameter must be the last parameter: " + parameter.getName());
            }

            TypeInfo elementType = resolveType(parameter.getType());
            TypeInfo type = parameter.isVariadic() ? new ArrayTypeInfo(elementType) : elementType;
            int sizeBytes = VmLayout.stackBytes(type);
            offsetBytes -= sizeBytes;
            ParameterEntry entry = new ParameterEntry(
                    parameter.getName(),
                    type,
                    sizeBytes,
                    offsetBytes,
                    validVariadic,
                    validVariadic ? elementType : null);
            parameter.setSymbolEntry(entry);
            if (validVariadic) {
                function.setVariadicParameter(entry);
            }

            if (!functionScope.defineParameter(entry)) {
                diagnosticBag.add(parameter.getSourceRange().start(), Severity.ERROR, "Duplicate symbol: " + parameter.getName());
            }
        }
    }

    private TypeInfo parameterType(FunctionParameter parameter) {
        TypeInfo type = resolveType(parameter.getType());
        return parameter.isVariadic() ? new ArrayTypeInfo(type) : type;
    }

    @Override
    public void visit(StructDeclaration node) {
        declareStructLayout(node);
    }

    private void declareStructLayout(StructDeclaration node) {
        StructEntry structEntry = node.getSymbolEntry();
        if (structEntry == null || completedStructLayouts.contains(structEntry)) {
            return;
        }
        if (structLayoutsInProgress.contains(structEntry)) {
            diagnosticBag.add(node.getSourceRange().start(), Severity.ERROR,
                    "Cyclic struct inheritance involving: " + node.getName());
            return;
        }

        structLayoutsInProgress.add(structEntry);
        StructScope structScope = node.getStructScope();

        StructEntry baseStruct = resolveBaseStruct(node);
        if (baseStruct != null) {
            StructDeclaration baseDeclaration = structDeclarations.get(baseStruct);
            if (baseDeclaration != null) {
                declareStructLayout(baseDeclaration);
            }
            structEntry.setBaseStruct(baseStruct);
            inheritFields(node, baseStruct);
        }

        int offsetBytes = structScope.getSizeBytes();
        for (StructField field : node.getFields()) {
            TypeInfo type = resolveType(field.getType());
            int sizeBytes = type.sizeBytes();
            FieldEntry entry = new FieldEntry(field.getName(), type, sizeBytes, offsetBytes);
            field.setSymbolEntry(entry);

            if (!structScope.defineField(entry)) {
                diagnosticBag.add(field.getSourceRange().start(), Severity.ERROR, "Duplicate field: " + field.getName());
            } else {
                offsetBytes += sizeBytes;
            }
        }
        structScope.setSizeBytes(offsetBytes);
        structLayoutsInProgress.remove(structEntry);
        completedStructLayouts.add(structEntry);
    }

    private StructEntry resolveBaseStruct(StructDeclaration node) {
        if (node.getBaseType() == null) {
            return null;
        }

        TypeInfo baseType = resolveType(node.getBaseType());
        if (!(baseType instanceof StructTypeInfo(StructEntry baseStruct)) || baseStruct == null) {
            diagnosticBag.add(node.getBaseType().getSourceRange().start(), Severity.ERROR,
                    "Struct inheritance requires a base struct.");
            return null;
        }

        StructEntry structEntry = node.getSymbolEntry();
        if (baseStruct == structEntry) {
            diagnosticBag.add(node.getBaseType().getSourceRange().start(), Severity.ERROR,
                    "Struct cannot inherit from itself: " + node.getName());
            return null;
        }

        if (structLayoutsInProgress.contains(baseStruct)) {
            diagnosticBag.add(node.getBaseType().getSourceRange().start(), Severity.ERROR,
                    "Cyclic struct inheritance involving: " + node.getName());
            return null;
        }

        return baseStruct;
    }

    private void inheritFields(StructDeclaration node, StructEntry baseStruct) {
        StructScope baseScope = baseStruct.getScope();
        StructScope structScope = node.getStructScope();
        if (baseScope == null || structScope == null) {
            return;
        }

        for (FieldEntry inheritedField : baseScope.getFields().values()) {
            if (!structScope.defineField(inheritedField)) {
                diagnosticBag.add(node.getSourceRange().start(), Severity.ERROR,
                        "Inherited duplicate field: " + inheritedField.getName());
            }
        }
    }

    @Override
    public void visit(BlockStatement node) {
        BlockScope previousBlock = currentBlock;
        BlockScope blockScope = new BlockScope(currentBlock, currentFunctionScope);
        node.setBlockScope(blockScope);

        if (currentBlock == null) {
            currentFunctionScope.setRootBlock(blockScope);
        } else {
            currentBlock.addChild(blockScope);
        }

        currentBlock = blockScope;
        super.visit(node);
        currentBlock = previousBlock;
    }

    @Override
    public void visit(ForStatement node) {
        BlockScope previousBlock = currentBlock;
        BlockScope forScope = new BlockScope(currentBlock, currentFunctionScope);
        node.setBlockScope(forScope);

        if (currentBlock == null) {
            currentFunctionScope.setRootBlock(forScope);
        } else {
            currentBlock.addChild(forScope);
        }

        currentBlock = forScope;
        if (node.getForInit() != null) {
            node.getForInit().accept(this);
        }
        if (node.getCondition() != null) {
            node.getCondition().accept(this);
        }
        if (node.getUpdate() != null) {
            node.getUpdate().forEach(expression -> expression.accept(this));
        }
        node.getBody().accept(this);
        currentBlock = previousBlock;
    }

    @Override
    public void visit(VarDeclarationStatement node) {
        if (node.getInitializer() != null) {
            node.getInitializer().accept(this);
        }

        TypeInfo type = resolveType(node.getType());
        int sizeBytes = VmLayout.memoryWidth(type).bytes();
        int offsetBytes = currentFunction != null ? currentFunction.allocateLocalBytes(sizeBytes) : 0;
        VariableEntry entry = new VariableEntry(
                node.getName(),
                type,
                sizeBytes,
                offsetBytes,
                node.getInitializer() != null,
                defaultValueOf(type),
                nextVariableDeclarationOrder++);
        node.setSymbolEntry(entry);

        if (currentBlock != null && !currentBlock.defineVariable(entry)) {
            diagnosticBag.add(node.getSourceRange().start(), Severity.ERROR, "Duplicate symbol: " + node.getName());
        }
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

    private TypeInfo resolveType(TypeAstNode type) {
        if (type instanceof PrimitiveType primitiveType) {
            return PrimitiveTypeInfo.of(primitiveType.getKind());
        }
        if (type instanceof ArrayType arrayType) {
            return new ArrayTypeInfo(resolveType(arrayType.getBaseType()));
        }
        if (type instanceof VoidType) {
            return VoidTypeInfo.INSTANCE;
        }
        if (type instanceof NamedType namedType) {
            return resolveNamedType(namedType);
        }
        return UnknownTypeInfo.INSTANCE;
    }

    private TypeInfo resolveNamedType(NamedType namedType) {
        return switch (namedType.getKind()) {
            case STRUCT -> {
                StructEntry entry = globalScope.resolveStruct(namedType.getName());
                if (entry != null) {
                    namedType.setResolvedEntry(entry);
                    yield new StructTypeInfo(entry);
                }
                yield UnknownTypeInfo.INSTANCE;
            }
            case ENUM -> {
                EnumEntry entry = globalScope.resolveEnum(namedType.getName());
                if (entry != null) {
                    namedType.setResolvedEntry(entry);
                    yield new EnumTypeInfo(entry);
                }
                yield UnknownTypeInfo.INSTANCE;
            }
            case INVALID -> UnknownTypeInfo.INSTANCE;
        };
    }

    private static Object defaultValueOf(TypeInfo type) {
        return TypeRules.defaultValue(type);
    }
}
