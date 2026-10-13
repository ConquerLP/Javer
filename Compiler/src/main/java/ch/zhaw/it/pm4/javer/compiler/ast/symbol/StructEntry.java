package ch.zhaw.it.pm4.javer.compiler.ast.symbol;

import ch.zhaw.it.pm4.javer.compiler.ast.scope.StructScope;

/**
 * Symbol table entry for a struct declaration.
 */
public final class StructEntry extends SymbolEntry {

    private StructScope scope;

    public StructEntry(String name) {
        super(name);
    }

    public StructScope getScope() {
        return scope;
    }

    public void setScope(StructScope scope) {
        this.scope = scope;
    }

}
