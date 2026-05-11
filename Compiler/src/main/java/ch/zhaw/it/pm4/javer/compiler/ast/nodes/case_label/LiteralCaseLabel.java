package ch.zhaw.it.pm4.javer.compiler.ast.nodes.case_label;

import ch.zhaw.it.pm4.javer.compiler.annotation.JacocoGenerated;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.AstNodeBase;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.LiteralExpression;
import ch.zhaw.it.pm4.javer.compiler.visitor.AstNodeVisitor;

@JacocoGenerated("Skeleton only, remove when fully implemented")
public final class LiteralCaseLabel extends AstNodeBase implements CaseLabelAstNode {

    private final LiteralExpression<?> literal;

    public LiteralCaseLabel(LiteralExpression<?> literal) {
        this.literal = literal;
    }

    public LiteralExpression<?> getLiteral() {
        return literal;
    }

    @Override
    public void accept(AstNodeVisitor visitor) {
        visitor.visit(this);
    }
}
