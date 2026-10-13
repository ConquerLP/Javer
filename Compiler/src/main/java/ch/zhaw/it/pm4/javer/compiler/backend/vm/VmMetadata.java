package ch.zhaw.it.pm4.javer.compiler.backend.vm;

import ch.zhaw.it.pm4.javer.compiler.ir.Ir;
import ch.zhaw.it.pm4.javer.compiler.ir.IrWalker;
import java.util.*;
import ch.zhaw.it.pm4.javer.compiler.diagnostics.CompilerInvariantException;
import ch.zhaw.it.pm4.javer.compiler.source.SourceRange;

/** Frozen VM ABI metadata for exactly one immutable IR program. */
final class VmMetadata {
    record FunctionLayout(String label, int parameterBytes, int localBytes) { }
    record StorageLayout(int sizeBytes, int offsetBytes, boolean parameter) { }
    record ArrayTemps(List<Integer> dimensions, List<Integer> bases, List<Integer> indices) {
        ArrayTemps { dimensions = List.copyOf(dimensions); bases = List.copyOf(bases); indices = List.copyOf(indices); }
    }
    private final Map<Integer, FunctionLayout> functions;
    private final Map<Integer, StorageLayout> storage;
    private final Map<Integer, Integer> structs;
    private final Map<Integer, String> enumLabels;
    private final Map<Integer, Integer> enumOffsets;
    private final Map<Ir.ArrayNew, ArrayTemps> arrays;

    private VmMetadata(Map<Integer, FunctionLayout> functions, Map<Integer, StorageLayout> storage,
                       Map<Integer, Integer> structs, Map<Integer, String> enumLabels,
                       Map<Integer, Integer> enumOffsets, Map<Ir.ArrayNew, ArrayTemps> arrays) {
        this.functions = Map.copyOf(functions); this.storage = Map.copyOf(storage); this.structs = Map.copyOf(structs);
        this.enumLabels = Map.copyOf(enumLabels); this.enumOffsets = Map.copyOf(enumOffsets);
        this.arrays = Collections.unmodifiableMap(new IdentityHashMap<>(arrays));
    }
    static VmMetadata prepare(Ir.Program program) {
        Map<Integer, FunctionLayout> functions = new HashMap<>();
        Map<Integer, StorageLayout> storage = new HashMap<>();
        Map<Integer, Integer> structs = new HashMap<>();
        Map<Integer, String> labels = new HashMap<>();
        Map<Integer, Integer> offsets = new HashMap<>();
        Map<Ir.ArrayNew, ArrayTemps> arrays = new IdentityHashMap<>();
        for (Ir.Signature signature : program.signatures()) {
            int parameterBytes = signature.parameters().stream().mapToInt(VmLayout::stackBytes).sum();
            functions.put(signature.id(), new FunctionLayout(signature.builtIn() ? signature.name() : "_" + signature.name(), parameterBytes, 0));
        }
        for (Ir.Struct struct : program.structs()) {
            int offset = 0;
            for (Ir.Storage field : struct.fields()) {
                int width = VmLayout.memoryWidth(field.type()).bytes();
                storage.put(field.id(), new StorageLayout(width, offset, false));
                offset = Math.addExact(offset, width);
            }
            structs.put(struct.id(), offset);
        }
        for (Ir.Enumeration enumeration : program.enums()) {
            labels.put(enumeration.id(), "enum_" + enumeration.name() + "_values");
            for (int i = 0; i < enumeration.values().size(); i++) offsets.put(enumeration.values().get(i).id(), Math.multiplyExact(i, VmLayout.WORD_BYTES));
        }
        for (Ir.Function function : program.functions()) {
            FunctionLayout header = functions.get(function.signature().id());
            int parameterOffset = Math.addExact(VmLayout.FRAME_HEADER_BYTES, header.parameterBytes());
            for (Ir.Storage parameter : function.parameters()) {
                int width = VmLayout.stackBytes(parameter.type()); parameterOffset -= width;
                storage.put(parameter.id(), new StorageLayout(width, parameterOffset, true));
            }
            int[] localBytes = {0};
            for (Ir.Storage local : function.locals()) {
                int width = VmLayout.memoryWidth(local.type()).bytes();
                storage.put(local.id(), new StorageLayout(width, localBytes[0], false));
                localBytes[0] = Math.addExact(localBytes[0], width);
            }
            IrWalker.walk(function.body(), node -> {
                if (node instanceof Ir.ArrayNew array && !arrays.containsKey(array)) {
                    int depth = array.initialized() ? 0 : Math.max(0, array.dimensions().size() - 1);
                    arrays.put(array, new ArrayTemps(allocate(array.dimensions().size(), localBytes), allocate(depth, localBytes), allocate(depth, localBytes)));
                }
            });
            functions.put(function.signature().id(), new FunctionLayout(header.label(), header.parameterBytes(), localBytes[0]));
        }
        return new VmMetadata(functions, storage, structs, labels, offsets, arrays);
    }
    private static List<Integer> allocate(int count, int[] localBytes) {
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < count; i++) { offsets.add(localBytes[0]); localBytes[0] = Math.addExact(localBytes[0], VmLayout.WORD_BYTES); }
        return offsets;
    }
    private <T> T require(T value, String message) {
        if (value == null) throw new CompilerInvariantException(SourceRange.UNKNOWN, message);
        return value;
    }
    FunctionLayout function(int id) { return require(functions.get(id), "Missing function layout"); }
    StorageLayout storage(int id) { return require(storage.get(id), "Missing storage layout"); }
    int structBytes(int id) { return require(structs.get(id), "Missing struct layout"); }
    String enumLabel(int id) { return require(enumLabels.get(id), "Missing enum layout"); }
    int enumOffset(int id) { return require(enumOffsets.get(id), "Missing enum constant layout"); }
    ArrayTemps array(Ir.ArrayNew node) { return require(arrays.get(node), "Missing array scratch layout"); }
}
