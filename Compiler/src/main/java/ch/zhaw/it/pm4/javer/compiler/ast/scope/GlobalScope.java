package ch.zhaw.it.pm4.javer.compiler.ast.scope;

import java.util.LinkedHashMap;
import java.util.Map;

import ch.zhaw.it.pm4.javer.compiler.ast.symbol.EnumEntry;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.EnumValueEntry;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.FunctionEntry;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.StructEntry;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.SymbolEntry;

public final class GlobalScope {

    private final Map<String, FunctionEntry> functions = new LinkedHashMap<>();
    private final Map<String, StructEntry> structs = new LinkedHashMap<>();
    private final Map<String, EnumEntry> enums = new LinkedHashMap<>();

    public boolean defineFunction(FunctionEntry entry) {
        return putIfAbsent(functions, entry);
    }

    public boolean defineStruct(StructEntry entry) {
        return putIfAbsent(structs, entry);
    }

    public boolean defineEnum(EnumEntry entry) {
        return putIfAbsent(enums, entry);
    }

    private static <T extends SymbolEntry> boolean putIfAbsent(Map<String, T> entries, T entry) {
        if (entries.containsKey(entry.getName())) {
            return false;
        }
        entries.put(entry.getName(), entry);
        return true;
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

    public Map<String, FunctionEntry> getFunctions() {
        return functions;
    }

    public Map<String, StructEntry> getStructs() {
        return structs;
    }

    public Map<String, EnumEntry> getEnums() {
        return enums;
    }

    public Map<String, SymbolEntry> getAllEntries() {
        Map<String, SymbolEntry> entries = new LinkedHashMap<>();
        functions.forEach((name, entry) -> entries.put("function " + name, entry));
        structs.forEach((name, entry) -> entries.put("struct " + name, entry));
        enums.forEach((name, entry) -> entries.put("enum " + name, entry));
        return entries;
    }
}
