package ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.statement;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.type.TypeAstNode;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.AstNodeVisitor;

/**
 * Expression node representing an unchecked cast.
 */
public final class CastExpression extends ExpressionAstNodeBase {

    private final TypeAstNode targetType;
    private final ExpressionAstNode operand;

    public CastExpression(TypeAstNode targetType, ExpressionAstNode operand) {
        this.targetType = targetType;
        this.operand = operand;
    }

    public TypeAstNode getTargetType() {
        return targetType;
    }

    public ExpressionAstNode getOperand() {
        return operand;
    }

    @Override
    public void accept(AstNodeVisitor visitor) {
        visitor.visit(this);
    }
}
