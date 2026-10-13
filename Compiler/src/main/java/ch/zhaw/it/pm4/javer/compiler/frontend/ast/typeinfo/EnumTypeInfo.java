package ch.zhaw.it.pm4.javer.compiler.frontend.ast.typeinfo;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.symbol.EnumEntry;

/**
 * Resolved type information for an enum value.
 *
 * @param entry symbol table entry for the enum declaration
 */
public record EnumTypeInfo(EnumEntry entry) implements TypeInfo {

    @Override
    public String toString() {
        return "enum " + (entry == null ? "<unknown>" : entry.getName());
    }
}
