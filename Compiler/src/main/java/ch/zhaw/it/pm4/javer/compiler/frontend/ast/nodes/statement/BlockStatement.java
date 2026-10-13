package ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.statement;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.AstNodeBase;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.scope.BlockScope;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.AstNodeVisitor;

import java.util.List;

/**
 * Statement node representing a block with its own nested statements.
 */
public final class BlockStatement extends AstNodeBase implements StatementAstNode {

    private final List<StatementAstNode> statements;
    private BlockScope blockScope;

    public BlockStatement(List<StatementAstNode> statements) {
        this.statements = statements;
    }

    public List<StatementAstNode> getStatements() {
        return statements;
    }

    @Override
    public void accept(AstNodeVisitor visitor) {
        visitor.visit(this);
    }

    public BlockScope getBlockScope() {
        return blockScope;
    }

    public void setBlockScope(BlockScope blockScope) {
        this.blockScope = blockScope;
    }
}
