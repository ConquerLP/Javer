package ch.zhaw.it.pm4.javer.compiler.backend.fanuc;

import ch.zhaw.it.pm4.javer.compiler.ir.Ir;
import ch.zhaw.it.pm4.javer.compiler.ir.IrWalker;

import java.util.*;

import ch.zhaw.it.pm4.javer.compiler.diagnostics.CompilerInvariantException;
import ch.zhaw.it.pm4.javer.compiler.source.SourceRange;

/**
 * Frozen FANUC ABI metadata for exactly one immutable IR program.
 */
final class FanucMetadata {
    record FunctionLayout(String label, int parameterCells, int localCells) {
    }

    record StorageLayout(int sizeCells, int offsetCells, boolean parameter) {
    }

    record ArrayTemps(List<Integer> dimensions, List<Integer> bases, List<Integer> indices) {
        ArrayTemps {
            dimensions = List.copyOf(dimensions);
            bases = List.copyOf(bases);
            indices = List.copyOf(indices);
        }
    }

    private final Map<Integer, FunctionLayout> functions;
    private final Map<Integer, StorageLayout> storage;
    private final Map<Integer, Integer> structs;
    private final Map<Integer, String> enumLabels;
    private final Map<Integer, Integer> enumOffsets;
    private final Map<Ir.ArrayNew, ArrayTemps> arrays;

    private FanucMetadata(Map<Integer, FunctionLayout> functions, Map<Integer, StorageLayout> storage,
                          Map<Integer, Integer> structs, Map<Integer, String> enumLabels,
                          Map<Integer, Integer> enumOffsets, Map<Ir.ArrayNew, ArrayTemps> arrays) {
        this.functions = Map.copyOf(functions);
        this.storage = Map.copyOf(storage);
        this.structs = Map.copyOf(structs);
        this.enumLabels = Map.copyOf(enumLabels);
        this.enumOffsets = Map.copyOf(enumOffsets);
        this.arrays = Collections.unmodifiableMap(new IdentityHashMap<>(arrays));
    }

    static FanucMetadata prepare(Ir.Program program) {
        Map<Integer, FunctionLayout> functions = new HashMap<>();
        Map<Integer, StorageLayout> storage = new HashMap<>();
        Map<Integer, Integer> structs = new HashMap<>();
        Map<Integer, String> labels = new HashMap<>();
        Map<Integer, Integer> offsets = new HashMap<>();
        Map<Ir.ArrayNew, ArrayTemps> arrays = new IdentityHashMap<>();
        for (Ir.Signature signature : program.signatures()) {
            int parameterCells = signature.parameters().stream().mapToInt(FanucLayout::stackCells).sum();
            functions.put(signature.id(), new FunctionLayout(signature.builtIn() ? signature.name() : "_" + signature.name(), parameterCells, 0));
        }
        for (Ir.Struct struct : program.structs()) {
            int offset = 0;
            for (Ir.Storage field : struct.fields()) {
                int width = FanucLayout.memoryWidth(field.type()).cells();
                storage.put(field.id(), new StorageLayout(width, offset, false));
                offset = Math.addExact(offset, width);
            }
            structs.put(struct.id(), offset);
        }
        for (Ir.Enumeration enumeration : program.enums()) {
            labels.put(enumeration.id(), "enum_" + enumeration.name() + "_values");
            for (int i = 0; i < enumeration.values().size(); i++)
                offsets.put(enumeration.values().get(i).id(), Math.multiplyExact(i, FanucLayout.WORD_CELLS));
        }
        for (Ir.Function function : program.functions()) {
            FunctionLayout header = functions.get(function.signature().id());
            int parameterOffset = -header.parameterCells() - FanucLayout.FRAME_HEADER_CELLS;
            for (Ir.Storage parameter : function.parameters()) {
                int width = FanucLayout.stackCells(parameter.type());
                storage.put(parameter.id(), new StorageLayout(width, parameterOffset, true));
                parameterOffset += width;
            }
            int[] localCells = {0};
            for (Ir.Storage local : function.locals()) {
                int width = FanucLayout.memoryWidth(local.type()).cells();
                storage.put(local.id(), new StorageLayout(width, localCells[0], false));
                localCells[0] = Math.addExact(localCells[0], width);
            }
            IrWalker.walk(function.body(), node -> {
                if (node instanceof Ir.ArrayNew array && !arrays.containsKey(array)) {
                    int depth = array.initialized() ? 0 : Math.max(0, array.dimensions().size() - 1);
                    arrays.put(array, new ArrayTemps(allocate(array.dimensions().size(), localCells), allocate(depth, localCells), allocate(depth, localCells)));
                }
            });
            functions.put(function.signature().id(), new FunctionLayout(header.label(), header.parameterCells(), localCells[0]));
        }
        return new FanucMetadata(functions, storage, structs, labels, offsets, arrays);
    }

    private static List<Integer> allocate(int count, int[] localCells) {
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            offsets.add(localCells[0]);
            localCells[0] = Math.addExact(localCells[0], FanucLayout.WORD_CELLS);
        }
        return offsets;
    }

    private <T> T require(T value, String message) {
        if (value == null) throw new CompilerInvariantException(SourceRange.UNKNOWN, message);
        return value;
    }

    FunctionLayout function(int id) {
        return require(functions.get(id), "Missing function layout");
    }

    StorageLayout storage(int id) {
        return require(storage.get(id), "Missing storage layout");
    }

    int structCells(int id) {
        return require(structs.get(id), "Missing struct layout");
    }

    String enumLabel(int id) {
        return require(enumLabels.get(id), "Missing enum layout");
    }

    int enumOffset(int id) {
        return require(enumOffsets.get(id), "Missing enum constant layout");
    }

    ArrayTemps array(Ir.ArrayNew node) {
        return require(arrays.get(node), "Missing array scratch layout");
    }

    String describe(Ir.Program program) {
        var data = new FanucDataSection();
        data.addEnums(program, this);
        for (var function : program.functions())
            IrWalker.walk(function.body(), node -> {
                if (node instanceof Ir.ArrayNew array) data.arrayTemplate(array);
            });
        int rom = data.values().values().stream().mapToInt(List::size).sum();
        return FanucRoutine.describe()
                + "physical register = " + FanucRoutine.memoryOffset() + " + logical address; null=0\n"
                + "ROM: base=1, registers=" + rom + "\n"
                + "heap: base=" + (rom + 1) + ", registers=16384 (including allocation headers)\n"
                + "stack: base=" + (rom + 16385) + ", registers=16384\n"
                + new FanucSymbolTablePrinter().print(program, this);
    }
}
