package ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.statement;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.AstNodeBase;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.AstNodeVisitor;

import java.util.List;

/**
 * For-loop initializer consisting of one or more expressions.
 */
public final class ForInitExpressionList extends AstNodeBase implements ForInit {

    private final List<ExpressionAstNode> expressions;

    public ForInitExpressionList(List<ExpressionAstNode> expressions) {
        this.expressions = expressions;
    }

    public List<ExpressionAstNode> getExpressions() {
        return expressions;
    }

    @Override
    public void accept(AstNodeVisitor visitor) {
        visitor.visit(this);
    }
}
