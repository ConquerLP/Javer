package ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes;

import ch.zhaw.it.pm4.javer.compiler.source.SourceRange;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.AstNodeVisitor;

/**
 * Common contract for every AST node in the compiler.
 */
public interface AstNode {

    /**
     * Dispatches this node to the matching visitor method.
     *
     * @param visitor visitor that should process this node
     */
    void accept(AstNodeVisitor visitor);

    /**
     * Returns the source range covered by this node.
     *
     * @return source range covered by this AST node
     */
    SourceRange getSourceRange();

    /**
     * Sets the source range assigned by the parser.
     *
     * @param sourceRange source range covered by this AST node
     */
    void setSourceRange(SourceRange sourceRange);

}
