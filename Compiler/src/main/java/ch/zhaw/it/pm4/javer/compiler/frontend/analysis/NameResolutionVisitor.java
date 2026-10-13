package ch.zhaw.it.pm4.javer.compiler.frontend.analysis;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.AstNodeVisitorBase;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.CompilationUnit;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.case_label.EnumCaseLabel;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.declaration.FunctionDeclaration;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.declaration.FunctionParameter;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.declaration.StructField;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.statement.*;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.type.NamedType;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.scope.BlockScope;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.scope.FunctionScope;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.scope.GlobalScope;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.symbol.*;
import ch.zhaw.it.pm4.javer.compiler.diagnostics.DiagnosticBag;
import ch.zhaw.it.pm4.javer.compiler.diagnostics.Severity;

/**
 * Resolves names in expressions and types to previously declared symbols.
 */
public class NameResolutionVisitor extends AstNodeVisitorBase {

    private final DiagnosticBag diagnosticBag;
    private GlobalScope globalScope;
    private FunctionScope currentFunctionScope;
    private BlockScope currentBlock;
    private int currentVariableDeclarationOrder;

    /**
     * Creates a name-resolution pass.
     *
     * @param diagnosticBag collector for unresolved or ambiguous names
     */
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

        super.visit(node);

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
            return currentFunctionScope.resolveParameter(node.getName());
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

        diagnosticBag.add(node.getSourceRange().start(), Severity.ERROR, "Undefined function: " + node.getFunctionName());
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
    }

    private void resolveEnumMember(MemberAccessExpression node, EnumEntry enumEntry) {
        EnumValueEntry valueEntry = enumEntry.getScope().resolveEnumValue(node.getMemberName());
        if (valueEntry == null) {
            diagnosticBag.add(node.getSourceRange().start(), Severity.ERROR, "Enum has no value: " + node.getMemberName());
            return;
        }

        node.setResolvedEnumValue(valueEntry);
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
