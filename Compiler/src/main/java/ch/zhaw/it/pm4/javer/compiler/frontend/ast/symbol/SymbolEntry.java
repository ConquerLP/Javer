package ch.zhaw.it.pm4.javer.compiler.frontend.ast.symbol;

/**
 * Base class for named entries stored in compiler symbol tables.
 */
public abstract class SymbolEntry {

    private final String name;

    protected SymbolEntry(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
