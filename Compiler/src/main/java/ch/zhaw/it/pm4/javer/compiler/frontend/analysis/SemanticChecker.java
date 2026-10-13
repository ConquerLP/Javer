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
import java.util.EnumSet;

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
        EnumSet<Completion> completion = analyzeCompletion(node.getBody());
        if (!(returnType instanceof VoidTypeInfo) && completion.contains(Completion.NORMAL)) {
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

    /** Possible exits of reachable paths; blocks stop following paths after an abrupt exit. */
    private EnumSet<Completion> analyzeCompletion(StatementAstNode statement) {
        return switch (statement) {
            case ReturnStatement ignored -> EnumSet.of(Completion.RETURNS);
            case BreakStatement ignored -> EnumSet.of(Completion.BREAKS);
            case ContinueStatement ignored -> EnumSet.of(Completion.CONTINUES);
            case BlockStatement block -> {
                EnumSet<Completion> result = EnumSet.of(Completion.NORMAL);
                for (StatementAstNode child : block.getStatements()) {
                    if (!result.remove(Completion.NORMAL)) break;
                    result.addAll(analyzeCompletion(child));
                }
                yield result;
            }
            case IfStatement branch -> {
                Boolean constant = booleanConstant(branch.getCondition());
                if (Boolean.TRUE.equals(constant)) yield analyzeCompletion(branch.getThenBranch());
                EnumSet<Completion> other = branch.getElseBranch() == null ? EnumSet.of(Completion.NORMAL) : analyzeCompletion(branch.getElseBranch());
                if (Boolean.FALSE.equals(constant)) yield other;
                other.addAll(analyzeCompletion(branch.getThenBranch()));
                yield other;
            }
            case SwitchStatement selection -> {
                EnumSet<Completion> result = EnumSet.noneOf(Completion.class);
                boolean hasDefault = false;
                for (SwitchCase branch : selection.getCases()) {
                    hasDefault |= branch.isDefault();
                    result.addAll(analyzeCompletion(branch.getStatement()));
                }
                if (!hasDefault) result.add(Completion.NORMAL);
                yield result;
            }
            case WhileStatement loop -> analyzeLoop(loop.getCondition(), loop.getBody(), false);
            case DoWhileStatement loop -> analyzeLoop(loop.getCondition(), loop.getBody(), true);
            case ForStatement loop -> analyzeLoop(loop.getCondition(), loop.getBody(), false);
            default -> EnumSet.of(Completion.NORMAL);
        };
    }

    private EnumSet<Completion> analyzeLoop(ExpressionAstNode condition, StatementAstNode body, boolean atLeastOnce) {
        Boolean constant = condition == null ? Boolean.TRUE : booleanConstant(condition);
        if (!atLeastOnce && Boolean.FALSE.equals(constant)) return EnumSet.of(Completion.NORMAL);
        EnumSet<Completion> result = EnumSet.noneOf(Completion.class);
        if (!atLeastOnce && !Boolean.TRUE.equals(constant)) result.add(Completion.NORMAL);
        EnumSet<Completion> bodyExits = analyzeCompletion(body);
        if (bodyExits.contains(Completion.RETURNS)) result.add(Completion.RETURNS);
        if (bodyExits.contains(Completion.DIVERGES)) result.add(Completion.DIVERGES);
        if (bodyExits.contains(Completion.BREAKS)) result.add(Completion.NORMAL);
        if (bodyExits.contains(Completion.NORMAL) || bodyExits.contains(Completion.CONTINUES)) {
            if (!Boolean.TRUE.equals(constant)) result.add(Completion.NORMAL);
            if (!Boolean.FALSE.equals(constant)) result.add(Completion.DIVERGES);
        }
        return result;
    }

    private Boolean booleanConstant(ExpressionAstNode expression) {
        return expression instanceof LiteralExpression<?> literal && literal.getKind() == LiteralKind.BOOLEAN
                ? (Boolean) literal.getValue() : null;
    }

    private enum Completion { NORMAL, RETURNS, BREAKS, CONTINUES, DIVERGES }
}
