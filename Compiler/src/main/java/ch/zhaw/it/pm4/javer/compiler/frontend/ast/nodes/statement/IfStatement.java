package ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.statement;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.AstNodeBase;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.AstNodeVisitor;

/**
 * Statement node representing an if/else branch.
 */
public final class IfStatement extends AstNodeBase implements StatementAstNode {

    private final ExpressionAstNode condition;
    private final StatementAstNode thenBranch;
    private final StatementAstNode elseBranch;

    private IfStatement(Builder builder) {
        this.condition = builder.condition;
        this.thenBranch = builder.thenBranch;
        this.elseBranch = builder.elseBranch;
    }

    public static Builder builder(ExpressionAstNode condition, StatementAstNode thenBranch) {
        return new Builder(condition, thenBranch);
    }

    public StatementAstNode getThenBranch() {
        return thenBranch;
    }

    public ExpressionAstNode getCondition() {
        return condition;
    }

    public StatementAstNode getElseBranch() {
        return elseBranch;
    }

    @Override
    public void accept(AstNodeVisitor visitor) {
        visitor.visit(this);
    }

    public static final class Builder {
        private final ExpressionAstNode condition;
        private final StatementAstNode thenBranch;
        private StatementAstNode elseBranch;

        private Builder(ExpressionAstNode condition, StatementAstNode thenBranch) {
            this.condition = condition;
            this.thenBranch = thenBranch;
        }

        public Builder elseBranch(StatementAstNode elseBranch) {
            this.elseBranch = elseBranch;
            return this;
        }

        public IfStatement build() {
            return new IfStatement(this);
        }
    }

}
