package ch.zhaw.it.pm4.javer.compiler.ast.symbol;

import ch.zhaw.it.pm4.javer.compiler.ast.typeinfo.TypeInfo;

/**
 * Symbol table entry for a struct field.
 */
public final class FieldEntry extends StorageEntry {

    public FieldEntry(String name, TypeInfo type) {
        super(name, type);
    }
}
