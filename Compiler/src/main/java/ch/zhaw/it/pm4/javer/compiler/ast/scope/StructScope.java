package ch.zhaw.it.pm4.javer.compiler.ast.scope;

import ch.zhaw.it.pm4.javer.compiler.ast.symbol.FieldEntry;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.SymbolEntry;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Symbol scope containing fields declared by a single struct.
 */
public final class StructScope {

    private final Map<String, FieldEntry> fields = new LinkedHashMap<>();

    public boolean defineField(FieldEntry entry) {
        if (fields.containsKey(entry.getName())) {
            return false;
        }
        fields.put(entry.getName(), entry);
        return true;
    }

    public FieldEntry resolveField(String name) {
        return fields.get(name);
    }

    public Map<String, FieldEntry> getFields() {
        return fields;
    }

    public Map<String, SymbolEntry> getAllEntries() {
        Map<String, SymbolEntry> entries = new LinkedHashMap<>();
        fields.forEach((name, entry) -> entries.put("field " + name, entry));
        return entries;
    }
}
