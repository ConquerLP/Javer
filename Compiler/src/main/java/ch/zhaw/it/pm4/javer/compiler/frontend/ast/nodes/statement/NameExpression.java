package ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.statement;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.symbol.SymbolEntry;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.AstNodeVisitor;

/**
 * Expression node representing a named value reference.
 */
public final class NameExpression extends ExpressionAstNodeBase {

    private final String name;

    private SymbolEntry symbolEntry;

    public NameExpression(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public void accept(AstNodeVisitor visitor) {
        visitor.visit(this);
    }

    public SymbolEntry getSymbolEntry() {
        return symbolEntry;
    }

    public void setSymbolEntry(SymbolEntry symbolEntry) {
        this.symbolEntry = symbolEntry;
    }
}
