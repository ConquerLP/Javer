package ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.type;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.AstNode;

/**
 * Marker interface for syntax nodes that describe a type.
 */
public sealed interface TypeAstNode extends AstNode
        permits ArrayType, NamedType, PrimitiveType, VoidType {
}
