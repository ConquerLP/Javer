package ch.zhaw.it.pm4.javer.compiler.visitor;

import java.util.Set;

import ch.zhaw.it.pm4.javer.compiler.ast.nodes.CompilationUnit;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.case_label.EnumCaseLabel;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.declaration.FunctionDeclaration;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.declaration.FunctionParameter;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.declaration.StructDeclaration;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.declaration.StructField;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.BlockStatement;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.CallExpression;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.ForStatement;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.MemberAccessExpression;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.NameExpression;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.VarDeclarationStatement;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.type.NamedType;
import ch.zhaw.it.pm4.javer.compiler.ast.scope.BlockScope;
import ch.zhaw.it.pm4.javer.compiler.ast.scope.FunctionScope;
import ch.zhaw.it.pm4.javer.compiler.ast.scope.GlobalScope;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.EnumEntry;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.EnumValueEntry;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.FieldEntry;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.FunctionEntry;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.ParameterEntry;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.StorageEntry;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.StructEntry;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.SymbolEntry;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.VariableEntry;
import ch.zhaw.it.pm4.javer.compiler.ast.typeinfo.StructTypeInfo;
import ch.zhaw.it.pm4.javer.compiler.misc.diagnostics.DiagnosticBag;
import ch.zhaw.it.pm4.javer.compiler.misc.diagnostics.Severity;

public class NameResolutionVisitor extends AstNodeVisitorBase {

    private static final Set<String> BUILT_IN_FUNCTIONS = Set.of("printb", "printc", "printi", "printd", "prints");

    private final DiagnosticBag diagnosticBag;
    private GlobalScope globalScope;
    private FunctionScope currentFunctionScope;
    private BlockScope currentBlock;
    private int currentVariableDeclarationOrder;

    public NameResolutionVisitor(DiagnosticBag diagnosticBag) {
        this.diagnosticBag = diagnosticBag;
    }

    @Override
    public void visit(CompilationUnit node) {
        globalScope = node.getGlobalScope();
        super.visit(node);
    }

    @Override
    public void visit(FunctionDeclaration node) {
        node.getReturnType().accept(this);

        FunctionScope previousFunctionScope = currentFunctionScope;
        BlockScope previousBlock = currentBlock;
        int previousVariableDeclarationOrder = currentVariableDeclarationOrder;

        currentFunctionScope = node.getFunctionScope();
        currentBlock = currentFunctionScope.getRootBlock();
        currentVariableDeclarationOrder = 0;

        for (FunctionParameter parameter : node.getParameters()) {
            parameter.accept(this);
        }
        if (node.getBody() != null) {
            node.getBody().accept(this);
        }

        currentFunctionScope = previousFunctionScope;
        currentBlock = previousBlock;
        currentVariableDeclarationOrder = previousVariableDeclarationOrder;
    }

    @Override
    public void visit(FunctionParameter node) {
        node.getType().accept(this);
    }

    @Override
    public void visit(StructDeclaration node) {
        for (StructField field : node.getFields()) {
            field.accept(this);
        }
    }

    @Override
    public void visit(StructField node) {
        node.getType().accept(this);
    }

    @Override
    public void visit(BlockStatement node) {
        BlockScope previousBlock = currentBlock;
        currentBlock = node.getBlockScope();
        super.visit(node);
        currentBlock = previousBlock;
    }

    @Override
    public void visit(ForStatement node) {
        BlockScope previousBlock = currentBlock;
        currentBlock = node.getBlockScope();

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
        node.getType().accept(this);
        if (node.getInitializer() != null) {
            node.getInitializer().accept(this);
        }
        if (node.getSymbolEntry() != null) {
            currentVariableDeclarationOrder = Math.max(
                    currentVariableDeclarationOrder,
                    node.getSymbolEntry().getDeclarationOrder() + 1);
        }
    }

    @Override
    public void visit(NamedType node) {
        SymbolEntry entry = switch (node.getKind()) {
            case STRUCT -> globalScope.resolveStruct(node.getName());
            case ENUM -> globalScope.resolveEnum(node.getName());
            case INVALID -> null;
        };

        if (entry == null) {
            diagnosticBag.add(node.getSourceRange().start(), Severity.ERROR, "Undefined type: " + node.getName());
            return;
        }

        node.setResolvedEntry(entry);
    }

    @Override
    public void visit(NameExpression node) {
        StorageEntry storageEntry = resolveStorage(node);
        if (storageEntry != null) {
            node.setSymbolEntry(storageEntry);
            return;
        }

        if (globalScope.isAmbiguousEnumValue(node.getName())) {
            diagnosticBag.add(node.getSourceRange().start(), Severity.ERROR, "Ambiguous enum value: " + node.getName());
            return;
        }

        EnumValueEntry enumValue = globalScope.resolveUniqueEnumValue(node.getName());
        if (enumValue != null) {
            node.setSymbolEntry(enumValue);
            return;
        }

        diagnosticBag.add(node.getSourceRange().start(), Severity.ERROR, "Undefined symbol: " + node.getName());
    }

    private StorageEntry resolveStorage(NameExpression node) {
        if (currentBlock != null) {
            VariableEntry variable = currentBlock.resolveVisibleVariable(node.getName(), currentVariableDeclarationOrder);
            if (variable != null) {
                return variable;
            }
        }
        if (currentFunctionScope != null) {
            ParameterEntry parameter = currentFunctionScope.resolveParameter(node.getName());
            if (parameter != null) {
                return parameter;
            }
        }
        return null;
    }

    @Override
    public void visit(CallExpression node) {
        for (var argument : node.getArguments()) {
            argument.accept(this);
        }

        FunctionEntry function = globalScope.resolveFunction(node.getFunctionName());
        if (function != null) {
            node.setResolvedFunction(function);
            return;
        }

        if (!BUILT_IN_FUNCTIONS.contains(node.getFunctionName())) {
            diagnosticBag.add(node.getSourceRange().start(), Severity.ERROR, "Undefined function: " + node.getFunctionName());
        }
    }

    @Override
    public void visit(MemberAccessExpression node) {
        if (node.getTarget() instanceof NameExpression targetName) {
            EnumEntry enumEntry = globalScope.resolveEnum(targetName.getName());
            if (enumEntry != null) {
                resolveEnumMember(node, enumEntry);
                return;
            }
        }

        node.getTarget().accept(this);

        FieldEntry field = resolveStructField(node);
        if (field != null) {
            node.setResolvedField(field);
        }
    }

    private void resolveEnumMember(MemberAccessExpression node, EnumEntry enumEntry) {
        EnumValueEntry valueEntry = enumEntry.getScope().resolveEnumValue(node.getMemberName());
        if (valueEntry == null) {
            diagnosticBag.add(node.getSourceRange().start(), Severity.ERROR, "Enum has no value: " + node.getMemberName());
            return;
        }

        node.setResolvedEnumValue(valueEntry);
        node.setValue(valueEntry.getValue());
    }

    private FieldEntry resolveStructField(MemberAccessExpression node) {
        if (!(node.getTarget() instanceof NameExpression targetName)) {
            return null;
        }

        SymbolEntry targetEntry = targetName.getSymbolEntry();
        if (!(targetEntry instanceof StorageEntry storageEntry)) {
            return null;
        }

        if (!(storageEntry.getType() instanceof StructTypeInfo structType) || structType.entry() == null) {
            return null;
        }

        StructEntry structEntry = structType.entry();
        FieldEntry field = structEntry.getScope().resolveField(node.getMemberName());
        if (field == null) {
            diagnosticBag.add(node.getSourceRange().start(), Severity.ERROR, "Struct has no field: " + node.getMemberName());
        }
        return field;
    }

    @Override
    public void visit(EnumCaseLabel node) {
        EnumEntry enumEntry = globalScope.resolveEnum(node.getEnumTypeName());
        if (enumEntry == null) {
            diagnosticBag.add(node.getSourceRange().start(), Severity.ERROR, "Undefined enum: " + node.getEnumTypeName());
            return;
        }

        EnumValueEntry valueEntry = enumEntry.getScope().resolveEnumValue(node.getEnumValueName());
        if (valueEntry == null) {
            diagnosticBag.add(node.getSourceRange().start(), Severity.ERROR, "Enum has no value: " + node.getEnumValueName());
            return;
        }

        node.setResolvedEnumValue(valueEntry);
    }
}
