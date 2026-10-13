package ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.statement;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.AstNodeBase;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.AstNodeVisitor;

/**
 * Statement node representing a while loop.
 */
public final class WhileStatement extends AstNodeBase implements StatementAstNode {

    private final ExpressionAstNode condition;
    private final StatementAstNode statement;

    public WhileStatement(ExpressionAstNode condition, StatementAstNode statement) {
        this.condition = condition;
        this.statement = statement;
    }

    public ExpressionAstNode getCondition() {
        return condition;
    }

    public StatementAstNode getBody() {
        return statement;
    }

    @Override
    public void accept(AstNodeVisitor visitor) {
        visitor.visit(this);
    }
}
