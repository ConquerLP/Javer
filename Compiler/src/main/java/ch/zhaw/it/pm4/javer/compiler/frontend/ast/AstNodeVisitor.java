package ch.zhaw.it.pm4.javer.compiler.frontend.ast;


import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.CompilationUnit;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.case_label.EnumCaseLabel;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.case_label.LiteralCaseLabel;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.declaration.*;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.statement.*;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.type.ArrayType;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.type.NamedType;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.type.PrimitiveType;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.type.VoidType;

/**
 * Visitor interface for all concrete AST node types.
 *
 * <p>Implementations override the node types they need and can extend
 * {@link AstNodeVisitorBase} to inherit traversal behavior.</p>
 */
public interface AstNodeVisitor {

    /**
     * Visits a compilation unit node.
     *
     * @param node node to visit
     */
    void visit(CompilationUnit node);

    /**
     * Visits an enum declaration node.
     *
     * @param node node to visit
     */
    void visit(EnumDeclaration node);

    /**
     * Visits an enum item node.
     *
     * @param node node to visit
     */
    void visit(EnumItem node);

    /**
     * Visits a function declaration node.
     *
     * @param node node to visit
     */
    void visit(FunctionDeclaration node);

    /**
     * Visits a function parameter node.
     *
     * @param node node to visit
     */
    void visit(FunctionParameter node);

    /**
     * Visits a struct declaration node.
     *
     * @param node node to visit
     */
    void visit(StructDeclaration node);

    /**
     * Visits a struct field node.
     *
     * @param node node to visit
     */
    void visit(StructField node);

    /**
     * Visits a block statement node.
     *
     * @param node node to visit
     */
    void visit(BlockStatement node);

    /**
     * Visits an if statement node.
     *
     * @param node node to visit
     */
    void visit(IfStatement node);

    /**
     * Visits a while statement node.
     *
     * @param node node to visit
     */
    void visit(WhileStatement node);

    /**
     * Visits a do-while statement node.
     *
     * @param node node to visit
     */
    void visit(DoWhileStatement node);

    /**
     * Visits a for statement node.
     *
     * @param node node to visit
     */
    void visit(ForStatement node);

    /**
     * Visits a switch statement node.
     *
     * @param node node to visit
     */
    void visit(SwitchStatement node);

    /**
     * Visits a switch case node.
     *
     * @param node node to visit
     */
    void visit(SwitchCase node);

    /**
     * Visits a break statement node.
     *
     * @param node node to visit
     */
    void visit(BreakStatement node);

    /**
     * Visits a continue statement node.
     *
     * @param node node to visit
     */
    void visit(ContinueStatement node);

    /**
     * Visits a return statement node.
     *
     * @param node node to visit
     */
    void visit(ReturnStatement node);

    /**
     * Visits a variable declaration statement node.
     *
     * @param node node to visit
     */
    void visit(VarDeclarationStatement node);

    /**
     * Visits an assignment expression node.
     *
     * @param node node to visit
     */
    void visit(AssignExpression node);

    /**
     * Visits a conditional expression node.
     *
     * @param node node to visit
     */
    void visit(ConditionalExpression node);

    /**
     * Visits a binary expression node.
     *
     * @param node node to visit
     */
    void visit(BinaryExpression node);

    /**
     * Visits a cast expression node.
     *
     * @param node node to visit
     */
    void visit(CastExpression node);

    /**
     * Visits a unary expression node.
     *
     * @param node node to visit
     */
    void visit(UnaryExpression node);

    /**
     * Visits a postfix expression node.
     *
     * @param node node to visit
     */
    void visit(PostfixExpression node);

    /**
     * Visits a call expression node.
     *
     * @param node node to visit
     */
    void visit(CallExpression node);

    /**
     * Visits an index expression node.
     *
     * @param node node to visit
     */
    void visit(IndexExpression node);

    /**
     * Visits a member access expression node.
     *
     * @param node node to visit
     */
    void visit(MemberAccessExpression node);

    /**
     * Visits a new expression node.
     *
     * @param node node to visit
     */
    void visit(NewExpression node);

    /**
     * Visits an array initializer expression node.
     *
     * @param node node to visit
     */
    void visit(ArrayInitExpression node);

    /**
     * Visits a name expression node.
     *
     * @param node node to visit
     */
    void visit(NameExpression node);

    /**
     * Visits a literal expression node.
     *
     * @param node node to visit
     */
    void visit(LiteralExpression<?> node);

    /**
     * Visits a literal case label node.
     *
     * @param node node to visit
     */
    void visit(LiteralCaseLabel node);

    /**
     * Visits an enum case label node.
     *
     * @param node node to visit
     */
    void visit(EnumCaseLabel node);

    /**
     * Visits an array type node.
     *
     * @param node node to visit
     */
    void visit(ArrayType node);

    /**
     * Visits a named type node.
     *
     * @param node node to visit
     */
    void visit(NamedType node);

    /**
     * Visits a primitive type node.
     *
     * @param node node to visit
     */
    void visit(PrimitiveType node);

    /**
     * Visits a void type node.
     *
     * @param node node to visit
     */
    void visit(VoidType node);

    /**
     * Visits a for-loop variable initializer node.
     *
     * @param node node to visit
     */
    void visit(ForInitVarDeclaration node);

    /**
     * Visits a for-loop expression-list initializer node.
     *
     * @param node node to visit
     */
    void visit(ForInitExpressionList node);

}
