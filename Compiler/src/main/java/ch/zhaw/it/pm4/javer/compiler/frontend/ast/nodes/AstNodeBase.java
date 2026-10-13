package ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes;

import ch.zhaw.it.pm4.javer.compiler.source.SourceRange;

/**
 * Base implementation that stores source-range metadata for concrete AST
 * nodes.
 */
public abstract class AstNodeBase implements AstNode {

    private SourceRange sourceRange = SourceRange.UNKNOWN;

    /**
     * Creates a node with an unknown source range until the parser assigns one.
     */
    protected AstNodeBase() {
    }

    @Override
    public SourceRange getSourceRange() {
        return sourceRange;
    }

    @Override
    public void setSourceRange(SourceRange sourceRange) {
        this.sourceRange = sourceRange;
    }
}
