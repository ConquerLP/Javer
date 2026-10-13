package ch.zhaw.it.pm4.javer.compiler.ast.symbol;

import ch.zhaw.it.pm4.javer.compiler.ast.scope.EnumScope;

/**
 * Symbol table entry for an enum declaration.
 */
public final class EnumEntry extends SymbolEntry {

    private EnumScope scope;

    public EnumEntry(String name) {
        super(name);
    }

    public EnumScope getScope() {
        return scope;
    }

    public void setScope(EnumScope scope) {
        this.scope = scope;
    }

}
