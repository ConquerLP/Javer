package ch.zhaw.it.pm4.javer.compiler.visitor;

import ch.zhaw.it.pm4.javer.compiler.ast.nodes.CompilationUnit;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.declaration.*;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.BlockStatement;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.ForStatement;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.NewExpression;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.VarDeclarationStatement;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.type.*;
import ch.zhaw.it.pm4.javer.compiler.ast.scope.*;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.*;
import ch.zhaw.it.pm4.javer.compiler.ast.typeinfo.*;
import ch.zhaw.it.pm4.javer.compiler.builtin.BuiltInFunction;
import ch.zhaw.it.pm4.javer.compiler.misc.diagnostics.DiagnosticBag;
import ch.zhaw.it.pm4.javer.compiler.misc.diagnostics.Severity;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

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

    /**
     * Creates a declaration pass.
     *
     * @param diagnosticBag collector for duplicate symbols and invalid
     *                      declarations
     */
    public SymbolDeclarationVisitor(DiagnosticBag diagnosticBag) {
        this.diagnosticBag = diagnosticBag;
    }

    private static Object defaultValueOf(TypeInfo type) {
        return TypeRules.defaultValue(type);
    }

    @Override
    public void visit(CompilationUnit node) {
        globalScope = node.getGlobalScope();

        registerBuiltInFunctions();
        for (DeclarationAstNode declaration : node.getDeclarations()) {
            registerTopLevelDeclaration(declaration);
        }
        validateMainFunction(node);
        for (DeclarationAstNode declaration : node.getDeclarations()) {
            if (declaration instanceof EnumDeclaration || declaration instanceof StructDeclaration) {
                declaration.accept(this);
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
            FunctionScope scope = new FunctionScope();
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
            if (!globalScope.defineStruct(entry)) {
                diagnosticBag.add(struct.getSourceRange().start(), Severity.ERROR, "Duplicate struct: " + struct.getName());
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
                diagnosticBag.add(enumDeclaration.getSourceRange().start(), Severity.ERROR, "Duplicate enum: " + enumDeclaration.getName());
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
                    0,
                    0,
                    entry.getDataLabel());

            if (!scope.defineEnumValue(valueEntry)) {
                diagnosticBag.add(item.getSourceRange().start(), Severity.ERROR, "Duplicate enum item: " + item.getName());
            }

            nextValue = value + 1;
        }
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
        for (int i = 0; i < node.getParameters().size(); i++) {
            FunctionParameter parameter = node.getParameters().get(i);
            boolean validVariadic = parameter.isVariadic() && i == node.getParameters().size() - 1;
            if (parameter.isVariadic() && !validVariadic) {
                diagnosticBag.add(parameter.getSourceRange().start(), Severity.ERROR,
                        "Variadic parameter must be the last parameter: " + parameter.getName());
            }

            TypeInfo elementType = resolveType(parameter.getType());
            TypeInfo type = parameter.isVariadic() ? new ArrayTypeInfo(elementType) : elementType;
            ParameterEntry entry = new ParameterEntry(
                    parameter.getName(),
                    type,
                    0,
                    0,
                    validVariadic,
                    validVariadic ? elementType : null);
            if (validVariadic) {
                function.setVariadicParameter(entry);
            }

            if (!functionScope.defineParameter(entry)) {
                diagnosticBag.add(parameter.getSourceRange().start(), Severity.ERROR, "Duplicate symbol: " + parameter.getName());
            }
        }
    }

    @Override
    public void visit(StructDeclaration node) {
        StructScope structScope = node.getStructScope();

        for (StructField field : node.getFields()) {
            TypeInfo type = resolveType(field.getType());
            FieldEntry entry = new FieldEntry(field.getName(), type, 0, 0);

            if (!structScope.defineField(entry)) {
                diagnosticBag.add(field.getSourceRange().start(), Severity.ERROR, "Duplicate field: " + field.getName());
            }
        }
    }

    @Override
    public void visit(BlockStatement node) {
        visitWithNewBlockScope(
                node::setBlockScope,
                () -> super.visit(node));
    }

    @Override
    public void visit(ForStatement node) {
        visitWithNewBlockScope(
                node::setBlockScope,
                () -> super.visit(node));
    }

    private void visitWithNewBlockScope(
            Consumer<BlockScope> scopeSetter,
            Runnable visitBody) {

        BlockScope previousBlock = currentBlock;
        BlockScope blockScope = new BlockScope(currentBlock, currentFunctionScope);

        scopeSetter.accept(blockScope);

        if (currentBlock == null) {
            currentFunctionScope.setRootBlock(blockScope);
        } else {
            currentBlock.addChild(blockScope);
        }

        currentBlock = blockScope;
        try {
            visitBody.run();
        } finally {
            currentBlock = previousBlock;
        }
    }

    @Override
    public void visit(VarDeclarationStatement node) {
        if (node.getInitializer() != null) {
            node.getInitializer().accept(this);
        }

        TypeInfo type = resolveType(node.getType());
        VariableEntry entry = new VariableEntry(
                node.getName(),
                type,
                0,
                0,
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
        super.visit(node);
    }

    @Override
    protected  TypeInfo resolveNamedType(NamedType namedType) {
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
}
