package ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.statement;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.AstNodeBase;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.scope.BlockScope;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.AstNodeVisitor;

import java.util.List;

/**
 * Statement node representing a {@code for} loop.
 */
public final class ForStatement extends AstNodeBase implements StatementAstNode {

    private final ForInit forInit;
    private final ExpressionAstNode condition;
    private final List<ExpressionAstNode> update;
    private final StatementAstNode body;
    private BlockScope blockScope;

    private ForStatement(Builder builder) {
        this.forInit = builder.forInit;
        this.condition = builder.condition;
        this.update = builder.update;
        this.body = builder.body;
    }

    public static Builder builder(StatementAstNode body) {
        return new Builder(body);
    }

    public StatementAstNode getBody() {
        return body;
    }

    public ForInit getForInit() {
        return forInit;
    }

    public ExpressionAstNode getCondition() {
        return condition;
    }

    public List<ExpressionAstNode> getUpdate() {
        return update;
    }

    public BlockScope getBlockScope() {
        return blockScope;
    }

    public void setBlockScope(BlockScope blockScope) {
        this.blockScope = blockScope;
    }

    @Override
    public void accept(AstNodeVisitor visitor) {
        visitor.visit(this);
    }

    public static final class Builder {
        private final StatementAstNode body;
        private ForInit forInit;
        private ExpressionAstNode condition;
        private List<ExpressionAstNode> update;

        private Builder(StatementAstNode body) {
            this.body = body;
        }

        public Builder forInit(ForInit forInit) {
            this.forInit = forInit;
            return this;
        }

        public Builder condition(ExpressionAstNode condition) {
            this.condition = condition;
            return this;
        }

        public Builder update(List<ExpressionAstNode> update) {
            this.update = update;
            return this;
        }

        public ForStatement build() {
            return new ForStatement(this);
        }
    }
}
