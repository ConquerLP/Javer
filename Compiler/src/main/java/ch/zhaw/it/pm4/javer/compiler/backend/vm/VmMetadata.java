package ch.zhaw.it.pm4.javer.compiler.backend.vm;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.symbol.EnumEntry;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.symbol.EnumValueEntry;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.symbol.FunctionEntry;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.symbol.StorageEntry;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.symbol.StructEntry;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.statement.NewExpression;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * VM-specific layout metadata produced before bytecode generation.
 */
final class VmMetadata {

    private final Map<NewExpression, JaggedArrayTempLayout> arrayTemps = new IdentityHashMap<>();

    public void putArrayTemps(NewExpression node, JaggedArrayTempLayout layout) {
        arrayTemps.put(node, layout);
    }

    public JaggedArrayTempLayout arrayTemps(NewExpression node) {
        return arrayTemps.get(node);
    }

    public record JaggedArrayTempLayout(int[] dimensionOffsets, int[] baseOffsets, int[] indexOffsets) {

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof JaggedArrayTempLayout(int[] offsets, int[] baseOffsets1, int[] indexOffsets1)))
                return false;

            return Arrays.equals(dimensionOffsets, offsets)
                    && Arrays.equals(baseOffsets, baseOffsets1)
                    && Arrays.equals(indexOffsets, indexOffsets1);
        }

        @Override
        public int hashCode() {
            int result = Arrays.hashCode(dimensionOffsets);
            result = 31 * result + Arrays.hashCode(baseOffsets);
            result = 31 * result + Arrays.hashCode(indexOffsets);
            return result;
        }

        @Override
        public String toString() {
            return "JaggedArrayTempLayout{" +
                    "dimensionOffsets=" + Arrays.toString(dimensionOffsets) +
                    ", baseOffsets=" + Arrays.toString(baseOffsets) +
                    ", indexOffsets=" + Arrays.toString(indexOffsets) +
                    '}';
        }
    }

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
