package ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.statement;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.AstNodeVisitor;

/**
 * Expression node representing assignment to a target expression.
 */
public final class AssignExpression extends ExpressionAstNodeBase {

    private final ExpressionAstNode target;
    private final AssignOperator operator;
    private final ExpressionAstNode value;

    private AssignExpression(Builder builder) {
        this.target = builder.target;
        this.operator = builder.operator;
        this.value = builder.value;
    }

    public static Builder builder(ExpressionAstNode target) {
        return new Builder(target);
    }

    public ExpressionAstNode getTarget() {
        return target;
    }

    public AssignOperator getOperator() {
        return operator;
    }

    public ExpressionAstNode getValue() {
        return value;
    }

    @Override
    public void accept(AstNodeVisitor visitor) {
        visitor.visit(this);
    }

    public static final class Builder {
        private final ExpressionAstNode target;
        private AssignOperator operator;
        private ExpressionAstNode value;

        private Builder(ExpressionAstNode target) {
            this.target = target;
        }

        public Builder operator(AssignOperator operator) {
            this.operator = operator;
            return this;
        }

        public Builder value(ExpressionAstNode value) {
            this.value = value;
            return this;
        }

        public AssignExpression build() {
            return new AssignExpression(this);
        }
    }
}
