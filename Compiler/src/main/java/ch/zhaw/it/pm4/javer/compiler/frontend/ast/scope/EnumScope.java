package ch.zhaw.it.pm4.javer.compiler.frontend.ast.scope;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.symbol.EnumValueEntry;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.symbol.SymbolEntry;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Symbol scope containing the values declared by a single enum.
 */
public final class EnumScope {

    private final Map<String, EnumValueEntry> values = new LinkedHashMap<>();

    public boolean defineEnumValue(EnumValueEntry entry) {
        if (values.containsKey(entry.getName())) {
            return false;
        }
        values.put(entry.getName(), entry);
        return true;
    }

    public EnumValueEntry resolveEnumValue(String name) {
        return values.get(name);
    }

    public Map<String, EnumValueEntry> getValues() {
        return values;
    }

    public Map<String, SymbolEntry> getAllEntries() {
        Map<String, SymbolEntry> entries = new LinkedHashMap<>();
        values.forEach((name, entry) -> entries.put("enum-value " + name, entry));
        return entries;
    }
}
