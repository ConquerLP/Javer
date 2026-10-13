package ch.zhaw.it.pm4.javer.compiler.frontend.ast;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.AstNode;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.CompilationUnit;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.case_label.CaseLabelAstNode;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.case_label.EnumCaseLabel;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.case_label.LiteralCaseLabel;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.declaration.*;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.statement.*;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.type.*;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.typeinfo.*;

/**
 * Default AST visitor implementation that recursively traverses child nodes and
 * delegates leaf behavior to {@link #visitDefault(AstNode)}.
 */
public abstract class AstNodeVisitorBase implements AstNodeVisitor {

    /**
     * Creates a visitor with default recursive traversal behavior.
     */
    protected AstNodeVisitorBase() {
    }

    /**
     * Handles leaf nodes or nodes without specialized behavior in a subclass.
     *
     * @param node visited node
     */
    @SuppressWarnings({"EmptyMethod", "unused"}) // empty method is intentional hook
    protected void visitDefault(AstNode node) {
    }

    @Override
    public void visit(CompilationUnit node) {
        for (DeclarationAstNode declaration : node.getDeclarations()) {
            declaration.accept(this);
        }
    }

    @Override
    public void visit(EnumDeclaration node) {
        for (EnumItem item : node.getItems()) {
            item.accept(this);
        }
    }

    @Override
    public void visit(EnumItem node) {
        visitDefault(node);
    }

    @Override
    public void visit(FunctionDeclaration node) {
        for (FunctionParameter param : node.getParameters()) {
            param.accept(this);
        }
        if (node.getBody() != null) {
            node.getBody().accept(this);
        }
    }

    @Override
    public void visit(FunctionParameter node) {
        visitDefault(node);
    }

    @Override
    public void visit(StructDeclaration node) {
        for (StructField field : node.getFields()) {
            field.accept(this);
        }
    }

    @Override
    public void visit(StructField node) {
        visitDefault(node);
    }

    @Override
    public void visit(BlockStatement node) {
        for (StatementAstNode statement : node.getStatements()) {
            statement.accept(this);
        }
    }

    @Override
    public void visit(IfStatement node) {
        node.getCondition().accept(this);
        node.getThenBranch().accept(this);
        if (node.getElseBranch() != null) {
            node.getElseBranch().accept(this);
        }
    }

    @Override
    public void visit(WhileStatement node) {
        node.getCondition().accept(this);
        node.getBody().accept(this);
    }

    @Override
    public void visit(DoWhileStatement node) {
        node.getCondition().accept(this);
        node.getBody().accept(this);
    }

    @Override
    public void visit(ForStatement node) {
        if (node.getForInit() != null) {
            node.getForInit().accept(this);
        }
        if (node.getCondition() != null) {
            node.getCondition().accept(this);
        }
        if (node.getUpdate() != null) {
            for (ExpressionAstNode updateExpr : node.getUpdate()) {
                updateExpr.accept(this);
            }
        }
        node.getBody().accept(this);
    }

    @Override
    public void visit(SwitchStatement node) {
        node.getCondition().accept(this);
        for (SwitchCase switchCase : node.getCases()) {
            switchCase.accept(this);
        }
    }

    @Override
    public void visit(SwitchCase node) {
        for (CaseLabelAstNode caseLabel : node.getCaseLabels()) {
            caseLabel.accept(this);
        }
        if (node.getStatement() != null) {
            node.getStatement().accept(this);
        }
    }

    @Override
    public void visit(BreakStatement node) {
        visitDefault(node);
    }

    @Override
    public void visit(ContinueStatement node) {
        visitDefault(node);
    }

    @Override
    public void visit(ReturnStatement node) {
        if (node.getExpression() != null) {
            node.getExpression().accept(this);
        }
    }

    @Override
    public void visit(VarDeclarationStatement node) {
        if (node.getInitializer() != null) {
            node.getInitializer().accept(this);
        }
    }

    @Override
    public void visit(AssignExpression node) {
        if (node.getTarget() != null) {
            node.getTarget().accept(this);
        }
        if (node.getValue() != null) {
            node.getValue().accept(this);
        }
    }

    @Override
    public void visit(ConditionalExpression node) {
        node.getCondition().accept(this);
        if (node.getTrueExpression() != null) {
            node.getTrueExpression().accept(this);
        }
        if (node.getFalseExpression() != null) {
            node.getFalseExpression().accept(this);
        }
    }

    @Override
    public void visit(BinaryExpression node) {
        node.getLeft().accept(this);
        node.getRight().accept(this);
    }

    @Override
    public void visit(CastExpression node) {
        node.getTargetType().accept(this);
        node.getOperand().accept(this);
    }

    @Override
    public void visit(UnaryExpression node) {
        node.getOperand().accept(this);
    }

    @Override
    public void visit(PostfixExpression node) {
        node.getOperand().accept(this);
    }

    @Override
    public void visit(CallExpression node) {
        for (ExpressionAstNode argument : node.getArguments()) {
            argument.accept(this);
        }
    }

    @Override
    public void visit(IndexExpression node) {
        node.getTarget().accept(this);
        node.getIndex().accept(this);
    }

    @Override
    public void visit(MemberAccessExpression node) {
        node.getTarget().accept(this);
    }

    @Override
    public void visit(NewExpression node) {
        for (ExpressionAstNode dimension : node.getDimensions()) {
            dimension.accept(this);
        }
        if (node.getArrayInit() != null) {
            node.getArrayInit().accept(this);
        }
    }

    @Override
    public void visit(ArrayInitExpression node) {
        for (ExpressionAstNode element : node.getElements()) {
            element.accept(this);
        }
    }

    @Override
    public void visit(NameExpression node) {
        visitDefault(node);
    }

    @Override
    public void visit(LiteralExpression<?> node) {
        visitDefault(node);
    }

    @Override
    public void visit(LiteralCaseLabel node) {
        visitDefault(node);
    }

    @Override
    public void visit(EnumCaseLabel node) {
        visitDefault(node);
    }

    @Override
    public void visit(ArrayType node) {
        visitDefault(node);
    }

    @Override
    public void visit(NamedType node) {
        visitDefault(node);
    }

    @Override
    public void visit(PrimitiveType node) {
        visitDefault(node);
    }

    @Override
    public void visit(VoidType node) {
        visitDefault(node);
    }

    @Override
    public void visit(ForInitVarDeclaration node) {
        node.getVarDeclaration().accept(this);
    }

    @Override
    public void visit(ForInitExpressionList node) {
        for (ExpressionAstNode expr : node.getExpressions()) {
            expr.accept(this);
        }
    }

    protected TypeInfo resolveType(TypeAstNode type) {
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

    @SuppressWarnings("java:S1172") // default method, overwritten when resolveType is actually used.
    protected TypeInfo resolveNamedType(NamedType type){
        return UnknownTypeInfo.INSTANCE;
    }
}
