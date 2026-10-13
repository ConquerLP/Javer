package ch.zhaw.it.pm4.javer.compiler.ast.symbol;

import ch.zhaw.it.pm4.javer.compiler.ast.typeinfo.TypeInfo;
import ch.zhaw.it.pm4.javer.compiler.ast.typeinfo.UnknownTypeInfo;

/**
 * Semantic symbol entry for a typed variable, parameter, or field.
 */
public abstract class StorageEntry extends SymbolEntry {

    private final TypeInfo type;

    protected StorageEntry(String name, TypeInfo type) {
        super(name);
        this.type = type == null ? UnknownTypeInfo.INSTANCE : type;
    }

    public TypeInfo getType() {
        return type;
    }

}
