package ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.statement;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.AstNode;

/**
 * Marker interface for initializer forms that can appear in a for loop.
 */
public sealed interface ForInit extends AstNode
        permits ForInitVarDeclaration, ForInitExpressionList {
}
