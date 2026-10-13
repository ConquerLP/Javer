package ch.zhaw.it.pm4.javer.compiler.frontend.ast.typeinfo;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.symbol.StructEntry;

/**
 * Resolved type information for a struct value.
 *
 * @param entry symbol table entry for the struct declaration
 */
public record StructTypeInfo(StructEntry entry) implements TypeInfo {

    @Override
    public String toString() {
        return "struct " + (entry == null ? "<unknown>" : entry.getName());
    }
}
