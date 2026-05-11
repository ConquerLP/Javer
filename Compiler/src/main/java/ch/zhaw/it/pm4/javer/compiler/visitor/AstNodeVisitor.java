package ch.zhaw.it.pm4.javer.compiler.visitor;


import ch.zhaw.it.pm4.javer.compiler.ast.nodes.*;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.declaration.*;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.case_label.*;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.*;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.ArrayInitExpression;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.AssignExpression;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.BinaryExpression;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.CallExpression;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.ConditionalExpression;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.IndexExpression;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.LiteralExpression;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.MemberAccessExpression;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.NameExpression;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.NewExpression;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.PostfixExpression;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.UnaryExpression;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.type.*;

public interface AstNodeVisitor {

    void visit(CompilationUnit node);
    void visit(EnumDeclaration node);
    void visit(EnumItem node);
    void visit(FunctionDeclaration node);
    void visit(FunctionParameter node);
    void visit(StructDeclaration node);
    void visit(StructField node);

    void visit(BlockStatement node);
    void visit(IfStatement node);
    void visit(WhileStatement node);
    void visit(DoWhileStatement node);
    void visit(ForStatement node);
    void visit(SwitchStatement node);
    void visit(SwitchCase node);
    void visit(BreakStatement node);
    void visit(ContinueStatement node);
    void visit(ReturnStatement node);
    void visit(VarDeclarationStatement node);

    void visit(AssignExpression node);
    void visit(ConditionalExpression node);
    void visit(BinaryExpression node);
    void visit(UnaryExpression node);
    void visit(PostfixExpression node);
    void visit(CallExpression node);
    void visit(IndexExpression node);
    void visit(MemberAccessExpression node);
    void visit(NewExpression node);
    void visit(ArrayInitExpression node);
    void visit(NameExpression node);
    void visit(LiteralExpression<?> node);

    void visit(LiteralCaseLabel node);
    void visit(EnumCaseLabel node);

    void visit(ArrayType node);
    void visit(NamedType node);
    void visit(PrimitiveType node);
    void visit(VoidType node);

    void visit(ForInitVarDeclaration node);
    void visit(ForInitExpressionList node);

}
