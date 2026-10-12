package ch.zhaw.it.pm4.javer.compiler.ast.scope;

import ch.zhaw.it.pm4.javer.compiler.ast.symbol.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Top-level symbol scope for functions, structs, and enums.
 */
public final class GlobalScope {

    private final Map<String, FunctionEntry> functions = new LinkedHashMap<>();
    private final Map<String, StructEntry> structs = new LinkedHashMap<>();
    private final Map<String, EnumEntry> enums = new LinkedHashMap<>();

    private static <T extends SymbolEntry> boolean putIfAbsent(Map<String, T> entries, T entry) {
        if (entries.containsKey(entry.getName())) {
            return false;
        }
        entries.put(entry.getName(), entry);
        return true;
    }

    public boolean defineFunction(FunctionEntry entry) {
        return putIfAbsent(functions, entry);
    }

    public boolean defineStruct(StructEntry entry) {
        return putIfAbsent(structs, entry);
    }

    public boolean defineEnum(EnumEntry entry) {
        return putIfAbsent(enums, entry);
    }

    public FunctionEntry resolveFunction(String name) {
        return functions.get(name);
    }

    public StructEntry resolveStruct(String name) {
        return structs.get(name);
    }

    public EnumEntry resolveEnum(String name) {
        return enums.get(name);
    }

    public Map<String, FunctionEntry> getFunctions() {
        return functions;
    }

    public EnumValueEntry resolveUniqueEnumValue(String name) {
        EnumValueEntry found = null;
        for (EnumEntry enumEntry : enums.values()) {
            if (enumEntry.getScope() != null) {
                EnumValueEntry candidate = enumEntry.getScope().resolveEnumValue(name);
                if (candidate != null) {
                    if (found != null) {
                        return null;
                    }
                    found = candidate;
                }
            }
        }
        return found;
    }

    public boolean isAmbiguousEnumValue(String name) {
        boolean found = false;
        for (EnumEntry enumEntry : enums.values()) {
            if (enumEntry.getScope() == null || enumEntry.getScope().resolveEnumValue(name) == null) {
                continue;
            }
            if (found) {
                return true;
            }
            found = true;
        }
        return false;
    }

    public Map<String, SymbolEntry> getAllEntries() {
        Map<String, SymbolEntry> entries = new LinkedHashMap<>();
        functions.forEach((name, entry) -> entries.put("function " + name, entry));
        structs.forEach((name, entry) -> entries.put("struct " + name, entry));
        enums.forEach((name, entry) -> entries.put("enum " + name, entry));
        return entries;
    }
}
