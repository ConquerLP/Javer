package ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.statement;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.AstNodeVisitor;

/**
 * Expression node representing a binary operation.
 */
public final class BinaryExpression extends ExpressionAstNodeBase {

    private final BinaryExpressionKind operator;
    private final ExpressionAstNode left;
    private final ExpressionAstNode right;

    public BinaryExpression(BinaryExpressionKind operator, ExpressionAstNode left, ExpressionAstNode right) {
        this.operator = operator;
        this.left = left;
        this.right = right;
    }

    public BinaryExpressionKind getOperator() {
        return operator;
    }

    public ExpressionAstNode getLeft() {
        return left;
    }

    public ExpressionAstNode getRight() {
        return right;
    }

    @Override
    public void accept(AstNodeVisitor visitor) {
        visitor.visit(this);
    }
}
