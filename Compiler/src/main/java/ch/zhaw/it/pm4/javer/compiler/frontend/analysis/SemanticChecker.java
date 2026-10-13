package ch.zhaw.it.pm4.javer.compiler.frontend.analysis;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.AstNodeVisitorBase;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.CompilationUnit;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.declaration.FunctionDeclaration;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.statement.*;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.symbol.FunctionEntry;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.typeinfo.TypeInfo;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.typeinfo.VoidTypeInfo;
import ch.zhaw.it.pm4.javer.compiler.diagnostics.DiagnosticBag;
import ch.zhaw.it.pm4.javer.compiler.diagnostics.Severity;

import java.util.List;

/**
 * Performs semantic checks that depend on control-flow context, such as
 * validating break and continue placement.
 */
public class SemanticChecker extends AstNodeVisitorBase {

    private final DiagnosticBag diagnosticBag;
    private int loopDepth = 0;

    /**
     * Creates a semantic-checking pass.
     *
     * @param diagnosticBag collector for semantic diagnostics
     */
    public SemanticChecker(DiagnosticBag diagnosticBag) {
        this.diagnosticBag = diagnosticBag;
    }

    @Override
    public void visit(CompilationUnit node) {
        validateMainFunction(node);
        node.getDeclarations().forEach(d -> d.accept(this));
    }

    private void validateMainFunction(CompilationUnit node) {
        List<FunctionDeclaration> entries = node.getDeclarations().stream()
                .filter(FunctionDeclaration.class::isInstance)
                .map(FunctionDeclaration.class::cast)
                .filter(function -> "main".equals(function.getName()))
                .toList();
        if (entries.size() != 1) {
            diagnosticBag.add(node.getSourceRange().start(), Severity.ERROR,
                    "Program must declare exactly one main function with signature: fn void main().");
        } else if (!(entries.getFirst().getSymbolEntry().getReturnType() instanceof VoidTypeInfo)
                || !entries.getFirst().getParameters().isEmpty()) {
            diagnosticBag.add(entries.getFirst().getSourceRange().start(), Severity.ERROR,
                    "Main signature must be exactly: fn void main().");
        }
    }

    @Override
    public void visit(FunctionDeclaration node) {
        if (node.getBody() != null) {
            node.getBody().accept(this);
        }
        FunctionEntry function = node.getSymbolEntry();
        TypeInfo returnType = function.getReturnType();
        Completion completion = analyzeCompletion(node.getBody());
        if (!(returnType instanceof VoidTypeInfo) && completion == Completion.NORMAL) {
            diagnosticBag.add(node.getSourceRange().start(), Severity.ERROR,
                    "Missing return statement for function: " + node.getName());
        }
    }

    @Override
    public void visit(BlockStatement node) {
        node.getStatements().forEach(s -> s.accept(this));
    }

    @Override
    public void visit(IfStatement node) {
        node.getThenBranch().accept(this);
        if (node.getElseBranch() != null) {
            node.getElseBranch().accept(this);
        }
    }

    @Override
    public void visit(WhileStatement node) {
        loopDepth++;
        node.getBody().accept(this);
        loopDepth--;
    }

    @Override
    public void visit(DoWhileStatement node) {
        loopDepth++;
        node.getBody().accept(this);
        loopDepth--;
    }

    @Override
    public void visit(ForStatement node) {
        loopDepth++;
        node.getBody().accept(this);
        loopDepth--;
    }

    @Override
    public void visit(SwitchStatement node) {
        node.getCases().forEach(c -> c.accept(this));
    }

    @Override
    public void visit(SwitchCase node) {
        if (node.getStatement() != null) {
            node.getStatement().accept(this);
        }
    }

    @Override
    public void visit(BreakStatement node) {
        if (loopDepth == 0) {
            diagnosticBag.add(node.getSourceRange().start(), Severity.ERROR, "'break' used outside of a loop");
        }
    }

    @Override
    public void visit(ContinueStatement node) {
        if (loopDepth == 0) {
            diagnosticBag.add(node.getSourceRange().start(), Severity.ERROR, "'continue' used outside of a loop");
        }
    }

    private Completion analyzeCompletion(StatementAstNode statement) {
        if (statement instanceof ReturnStatement) {
            return Completion.RETURNS;
        }
        if (statement instanceof BlockStatement block) {
            return analyzeBlock(block);
        }
        if (statement instanceof IfStatement ifStatement) {
            return analyzeIf(ifStatement);
        }
        if (statement instanceof SwitchStatement switchStatement) {
            return analyzeSwitch(switchStatement);
        }
        if (statement instanceof WhileStatement whileStatement) {
            return analyzeWhile(whileStatement);
        }
        if (statement instanceof ForStatement forStatement) {
            return analyzeFor(forStatement);
        }
        if (statement instanceof DoWhileStatement doWhileStatement) {
            return analyzeDoWhile(doWhileStatement);
        }
        return Completion.NORMAL;
    }

    private Completion analyzeBlock(BlockStatement block) {
        for (StatementAstNode statement : block.getStatements()) {
            Completion completion = analyzeCompletion(statement);
            if (completion != Completion.NORMAL) {
                return completion;
            }
        }
        return Completion.NORMAL;
    }

    private Completion analyzeIf(IfStatement ifStatement) {
        if (ifStatement.getElseBranch() == null) {
            return Completion.NORMAL;
        }
        Completion thenCompletion = analyzeCompletion(ifStatement.getThenBranch());
        Completion elseCompletion = analyzeCompletion(ifStatement.getElseBranch());
        if (thenCompletion == Completion.NORMAL || elseCompletion == Completion.NORMAL) {
            return Completion.NORMAL;
        }
        if (thenCompletion == Completion.RETURNS && elseCompletion == Completion.RETURNS) {
            return Completion.RETURNS;
        }
        return Completion.DOES_NOT_COMPLETE;
    }

    private Completion analyzeSwitch(SwitchStatement switchStatement) {
        boolean hasDefault = false;
        boolean allCasesReturn = true;
        for (SwitchCase switchCase : switchStatement.getCases()) {
            if (switchCase.isDefault()) {
                hasDefault = true;
            }
            Completion completion = analyzeCompletion(switchCase.getStatement());
            if (completion == Completion.NORMAL) {
                return Completion.NORMAL;
            }
            if (completion != Completion.RETURNS) {
                allCasesReturn = false;
            }
        }
        if (!hasDefault) {
            return Completion.NORMAL;
        }
        return allCasesReturn ? Completion.RETURNS : Completion.DOES_NOT_COMPLETE;
    }

    private Completion analyzeWhile(WhileStatement statement) {
        return analyzeInfiniteLoop(
                statement.getCondition(),
                statement.getBody(),
                statement);
    }

    private Completion analyzeFor(ForStatement statement) {
        if (statement.getCondition() != null || containsBreak(statement.getBody())) {
            return Completion.NORMAL;
        }
        Completion bodyCompletion = analyzeCompletion(statement.getBody());
        if (bodyCompletion == Completion.RETURNS) {
            return Completion.RETURNS;
        }
        if (bodyCompletion == Completion.NORMAL && containsReturn(statement.getBody())) {
            warnComplexInfiniteLoop(statement);
        }
        return Completion.DOES_NOT_COMPLETE;
    }

    private Completion analyzeDoWhile(DoWhileStatement statement) {
        return analyzeInfiniteLoop(
                statement.getCondition(),
                statement.getBody(),
                statement);
    }

    private Completion analyzeInfiniteLoop(
            ExpressionAstNode condition,
            StatementAstNode body,
            StatementAstNode loopStatement) {

        if (isNotBooleanLiteralTrue(condition) || containsBreak(body)) {
            return Completion.NORMAL;
        }

        Completion bodyCompletion = analyzeCompletion(body);

        if (bodyCompletion == Completion.RETURNS) {
            return Completion.RETURNS;
        }

        if (bodyCompletion == Completion.NORMAL && containsReturn(body)) {
            warnComplexInfiniteLoop(loopStatement);
        }

        return Completion.DOES_NOT_COMPLETE;
    }

    private boolean isNotBooleanLiteralTrue(ExpressionAstNode expression) {
        return !(expression instanceof LiteralExpression<?> literal)
                || literal.getKind() != LiteralKind.BOOLEAN
                || !Boolean.TRUE.equals(literal.getValue());
    }

    private boolean containsReturn(StatementAstNode statement) {
        if (statement instanceof ReturnStatement) {
            return true;
        }
        if (statement instanceof BlockStatement block) {
            return block.getStatements().stream().anyMatch(this::containsReturn);
        }
        if (statement instanceof IfStatement ifStatement) {
            return containsReturn(ifStatement.getThenBranch())
                    || (ifStatement.getElseBranch() != null && containsReturn(ifStatement.getElseBranch()));
        }
        if (statement instanceof SwitchStatement switchStatement) {
            return switchStatement.getCases().stream()
                    .map(SwitchCase::getStatement)
                    .anyMatch(this::containsReturn);
        }
        if (statement instanceof WhileStatement whileStatement) {
            return containsReturn(whileStatement.getBody());
        }
        if (statement instanceof ForStatement forStatement) {
            return containsReturn(forStatement.getBody());
        }
        if (statement instanceof DoWhileStatement doWhileStatement) {
            return containsReturn(doWhileStatement.getBody());
        }
        return false;
    }

    private boolean containsBreak(StatementAstNode statement) {
        if (statement instanceof BreakStatement) {
            return true;
        }
        if (statement instanceof BlockStatement block) {
            return block.getStatements().stream().anyMatch(this::containsBreak);
        }
        if (statement instanceof IfStatement ifStatement) {
            return containsBreak(ifStatement.getThenBranch())
                    || (ifStatement.getElseBranch() != null && containsBreak(ifStatement.getElseBranch()));
        }
        if (statement instanceof SwitchStatement switchStatement) {
            return switchStatement.getCases().stream()
                    .map(SwitchCase::getStatement)
                    .anyMatch(this::containsBreak);
        }
        return false;
    }

    private void warnComplexInfiniteLoop(StatementAstNode statement) {
        diagnosticBag.add(statement.getSourceRange().start(), Severity.WARNING,
                "Infinite loop contains conditional return; normal completion is impossible but return is not definite.");
    }

    private enum Completion {
        NORMAL,
        RETURNS,
        DOES_NOT_COMPLETE
    }
}
