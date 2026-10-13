package ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.statement;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.AstNodeVisitor;

/**
 * Expression node representing a unary operation.
 */
public final class UnaryExpression extends ExpressionAstNodeBase {

    private final UnaryExpressionKind kind;
    private final ExpressionAstNode operand;

    public UnaryExpression(UnaryExpressionKind kind, ExpressionAstNode operand) {
        this.kind = kind;
        this.operand = operand;
    }

    public UnaryExpressionKind getKind() {
        return kind;
    }

    public ExpressionAstNode getOperand() {
        return operand;
    }

    @Override
    public void accept(AstNodeVisitor visitor) {
        visitor.visit(this);
    }
}
