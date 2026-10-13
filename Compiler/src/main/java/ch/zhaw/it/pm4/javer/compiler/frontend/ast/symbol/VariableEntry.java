package ch.zhaw.it.pm4.javer.compiler.frontend.ast.symbol;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.typeinfo.TypeInfo;

/**
 * Symbol table entry for a local variable.
 */
public final class VariableEntry extends StorageEntry {

    private final boolean hasExplicitInitializer;
    private final Object defaultValue;
    private final int declarationOrder;

    public VariableEntry(
            String name,
            TypeInfo type,
            boolean hasExplicitInitializer,
            Object defaultValue,
            int declarationOrder) {
        super(name, type);
        this.hasExplicitInitializer = hasExplicitInitializer;
        this.defaultValue = defaultValue;
        this.declarationOrder = declarationOrder;
    }

    public boolean hasExplicitInitializer() {
        return hasExplicitInitializer;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public int getDeclarationOrder() {
        return declarationOrder;
    }
}
