package ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.statement;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.AstNodeVisitor;

/**
 * Expression node representing a ternary conditional expression.
 */
public final class ConditionalExpression extends ExpressionAstNodeBase {

    private final ExpressionAstNode condition;
    private final ExpressionAstNode trueExpression;
    private final ExpressionAstNode falseExpression;

    private ConditionalExpression(Builder builder) {
        this.condition = builder.condition;
        this.trueExpression = builder.trueExpression;
        this.falseExpression = builder.falseExpression;
    }

    public static Builder builder(ExpressionAstNode condition) {
        return new Builder(condition);
    }

    public ExpressionAstNode getCondition() {
        return condition;
    }

    public ExpressionAstNode getTrueExpression() {
        return trueExpression;
    }

    public ExpressionAstNode getFalseExpression() {
        return falseExpression;
    }

    @Override
    public void accept(AstNodeVisitor visitor) {
        visitor.visit(this);
    }

    public static final class Builder {
        private final ExpressionAstNode condition;
        private ExpressionAstNode trueExpression;
        private ExpressionAstNode falseExpression;

        private Builder(ExpressionAstNode condition) {
            this.condition = condition;
        }

        public Builder whenTrue(ExpressionAstNode trueExpression) {
            this.trueExpression = trueExpression;
            return this;
        }

        public Builder whenFalse(ExpressionAstNode falseExpression) {
            this.falseExpression = falseExpression;
            return this;
        }

        public ConditionalExpression build() {
            return new ConditionalExpression(this);
        }
    }
}
