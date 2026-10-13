package ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.statement;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.AstNode;

/**
 * Marker interface for executable statement nodes.
 */
public sealed interface StatementAstNode extends AstNode
        permits ExpressionAstNode,
        BlockStatement,
        BreakStatement, ContinueStatement,
        DoWhileStatement, ForStatement,
        IfStatement, ReturnStatement, SwitchStatement,
        VarDeclarationStatement, WhileStatement {

}
