package ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.statement;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.symbol.FunctionEntry;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.AstNodeVisitor;

import java.util.List;

/**
 * Expression node representing a function call and its arguments.
 */
public final class CallExpression extends ExpressionAstNodeBase {

    private final String functionName;
    private final List<ExpressionAstNode> arguments;
    private FunctionEntry resolvedFunction;

    public CallExpression(String functionName, List<ExpressionAstNode> arguments) {
        this.functionName = functionName;
        this.arguments = arguments;
    }

    public String getFunctionName() {
        return functionName;
    }

    public List<ExpressionAstNode> getArguments() {
        return arguments;
    }

    public FunctionEntry getResolvedFunction() {
        return resolvedFunction;
    }

    public void setResolvedFunction(FunctionEntry resolvedFunction) {
        this.resolvedFunction = resolvedFunction;
    }

    @Override
    public void accept(AstNodeVisitor visitor) {
        visitor.visit(this);
    }
}
