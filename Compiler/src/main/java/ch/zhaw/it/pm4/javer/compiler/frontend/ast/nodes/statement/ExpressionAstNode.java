package ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.statement;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.AstNode;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.typeinfo.TypeInfo;

/**
 * Marker interface for AST nodes that produce expression values.
 */
public sealed interface ExpressionAstNode extends AstNode, StatementAstNode
        permits ExpressionAstNodeBase {

    TypeInfo getResultingType();

    void setResultingType(TypeInfo resultingType);
}
