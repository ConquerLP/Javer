package ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.statement;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.AstNodeBase;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.AstNodeVisitor;

/**
 * Statement node representing a {@code return} statement.
 */
public final class ReturnStatement extends AstNodeBase implements StatementAstNode {

    private final ExpressionAstNode expression;

    private ReturnStatement(Builder builder) {
        this.expression = builder.expression;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(ExpressionAstNode expression) {
        return new Builder().expression(expression);
    }

    public ExpressionAstNode getExpression() {
        return expression;
    }

    @Override
    public void accept(AstNodeVisitor visitor) {
        visitor.visit(this);
    }

    public static final class Builder {
        private ExpressionAstNode expression;

        public Builder expression(ExpressionAstNode expression) {
            this.expression = expression;
            return this;
        }

        public ReturnStatement build() {
            return new ReturnStatement(this);
        }
    }
}
