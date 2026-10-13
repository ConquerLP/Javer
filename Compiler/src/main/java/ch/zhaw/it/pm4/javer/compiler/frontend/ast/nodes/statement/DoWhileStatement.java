package ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.statement;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.AstNodeBase;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.AstNodeVisitor;

/**
 * Statement node representing a do-while loop.
 */
public final class DoWhileStatement extends AstNodeBase implements StatementAstNode {

    private final ExpressionAstNode condition;
    private final BlockStatement block;

    public DoWhileStatement(ExpressionAstNode condition, BlockStatement block) {
        this.condition = condition;
        this.block = block;
    }

    public ExpressionAstNode getCondition() {
        return condition;
    }

    public BlockStatement getBody() {
        return block;
    }

    @Override
    public void accept(AstNodeVisitor visitor) {
        visitor.visit(this);
    }
}
