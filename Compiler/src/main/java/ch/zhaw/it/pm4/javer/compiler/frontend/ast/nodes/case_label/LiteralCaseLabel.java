package ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.case_label;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.AstNodeBase;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.statement.LiteralExpression;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.AstNodeVisitor;

/**
 * Switch case label backed by a literal expression.
 */
public final class LiteralCaseLabel extends AstNodeBase implements CaseLabelAstNode {

    private final LiteralExpression<?> literal;

    public LiteralCaseLabel(LiteralExpression<?> literal) {
        this.literal = literal;
    }

    @SuppressWarnings("java:S1452")
    // Type parameter cannot be concretized due to heterogeneous literal types in AST design.
    public LiteralExpression<?> getLiteral() {
        return literal;
    }

    @Override
    public void accept(AstNodeVisitor visitor) {
        visitor.visit(this);
    }
}
