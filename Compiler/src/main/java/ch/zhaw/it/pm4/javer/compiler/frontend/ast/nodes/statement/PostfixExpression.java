package ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.statement;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.AstNodeVisitor;

/**
 * Expression node representing a postfix operation.
 */
public final class PostfixExpression extends ExpressionAstNodeBase {

    private final ExpressionAstNode operand;
    private final PostfixOperationKind kind;

    public PostfixExpression(ExpressionAstNode operand, PostfixOperationKind kind) {
        this.operand = operand;
        this.kind = kind;
    }

    public ExpressionAstNode getOperand() {
        return operand;
    }

    public PostfixOperationKind getKind() {
        return kind;
    }

    @Override
    public void accept(AstNodeVisitor visitor) {
        visitor.visit(this);
    }
}
