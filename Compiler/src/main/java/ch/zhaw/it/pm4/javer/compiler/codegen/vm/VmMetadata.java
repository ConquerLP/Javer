package ch.zhaw.it.pm4.javer.compiler.codegen.vm;

import ch.zhaw.it.pm4.javer.compiler.ast.symbol.EnumEntry;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.EnumValueEntry;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.FunctionEntry;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.StorageEntry;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.StructEntry;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * VM-specific layout metadata produced before bytecode generation.
 */
final class VmMetadata {

    private final Map<FunctionEntry, VmFunctionLayout> functions = new IdentityHashMap<>();
    private final Map<StorageEntry, VmStorageLayout> storage = new IdentityHashMap<>();
    private final Map<StructEntry, VmStructLayout> structs = new IdentityHashMap<>();
    private final Map<EnumEntry, VmEnumLayout> enums = new IdentityHashMap<>();
    private final Map<EnumValueEntry, VmEnumValueLayout> enumValues = new IdentityHashMap<>();

    public void putFunction(FunctionEntry entry, VmFunctionLayout layout) {
        functions.put(entry, layout);
    }

    public VmFunctionLayout function(FunctionEntry entry) {
        return functions.get(entry);
    }

    public void putStorage(StorageEntry entry, VmStorageLayout layout) {
        storage.put(entry, layout);
    }

    public VmStorageLayout storage(StorageEntry entry) {
        return storage.get(entry);
    }

    public void putStruct(StructEntry entry, VmStructLayout layout) {
        structs.put(entry, layout);
    }

    public VmStructLayout struct(StructEntry entry) {
        return structs.get(entry);
    }

    public void putEnum(EnumEntry entry, VmEnumLayout layout) {
        enums.put(entry, layout);
    }

    public VmEnumLayout enumLayout(EnumEntry entry) {
        return enums.get(entry);
    }

    public void putEnumValue(EnumValueEntry entry, VmEnumValueLayout layout) {
        enumValues.put(entry, layout);
    }

    public VmEnumValueLayout enumValue(EnumValueEntry entry) {
        return enumValues.get(entry);
    }

    public record VmFunctionLayout(String label, int parameterBytes, int localBytes, int frameSizeBytes) {
    }

    public record VmStorageLayout(int sizeBytes, int offsetBytes) {
    }

    public record VmStructLayout(int sizeBytes) {
    }

    public record VmEnumLayout(String dataLabel, int elementSizeBytes, int sizeBytes) {
    }

    public record VmEnumValueLayout(int sizeBytes, int offsetBytes, String dataLabel) {
    }
}
