package ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.statement;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.AstNodeBase;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.AstNodeVisitor;

import java.util.List;

/**
 * Statement node representing a switch over an expression.
 */
public final class SwitchStatement extends AstNodeBase implements StatementAstNode {

    private final ExpressionAstNode condition;
    private final List<SwitchCase> cases;

    public SwitchStatement(ExpressionAstNode condition, List<SwitchCase> cases) {
        this.condition = condition;
        this.cases = cases;
    }

    public ExpressionAstNode getCondition() {
        return condition;
    }

    public List<SwitchCase> getCases() {
        return cases;
    }

    @Override
    public void accept(AstNodeVisitor visitor) {
        visitor.visit(this);
    }
}
