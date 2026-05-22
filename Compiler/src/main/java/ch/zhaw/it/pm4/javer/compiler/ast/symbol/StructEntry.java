package ch.zhaw.it.pm4.javer.compiler.ast.symbol;

import ch.zhaw.it.pm4.javer.compiler.ast.scope.StructScope;

/**
 * Symbol table entry for a struct declaration.
 */
public final class StructEntry extends SymbolEntry {

    private StructScope scope;
    private StructEntry baseStruct;

    public StructEntry(String name) {
        super(name);
    }

    public StructScope getScope() {
        return scope;
    }

    public void setScope(StructScope scope) {
        this.scope = scope;
    }

    public StructEntry getBaseStruct() {
        return baseStruct;
    }

    public void setBaseStruct(StructEntry baseStruct) {
        this.baseStruct = baseStruct;
    }

    public int getSizeBytes() {
        return scope == null ? 0 : scope.getSizeBytes();
    }

    public boolean derivesFrom(StructEntry candidateBase) {
        StructEntry current = baseStruct;
        while (current != null) {
            if (current == candidateBase) {
                return true;
            }
            current = current.getBaseStruct();
        }
        return false;
    }
}
