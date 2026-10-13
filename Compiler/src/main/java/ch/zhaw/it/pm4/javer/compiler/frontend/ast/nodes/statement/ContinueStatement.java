package ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.statement;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.AstNodeBase;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.AstNodeVisitor;

/**
 * Statement node representing a {@code continue} statement.
 */
public final class ContinueStatement extends AstNodeBase implements StatementAstNode {
    @Override
    public void accept(AstNodeVisitor visitor) {
        visitor.visit(this);
    }
}
