package ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.statement;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.AstNodeVisitor;

import java.util.List;

/**
 * Expression node representing an array initializer.
 */
public final class ArrayInitExpression extends ExpressionAstNodeBase {

    private final List<ExpressionAstNode> elements;

    public ArrayInitExpression(List<ExpressionAstNode> elements) {
        this.elements = elements;
    }

    public List<ExpressionAstNode> getElements() {
        return elements;
    }

    @Override
    public void accept(AstNodeVisitor visitor) {
        visitor.visit(this);
    }
}
