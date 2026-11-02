package ch.zhaw.it.pm4.javer.compiler.backend.fanuc;

import ch.zhaw.it.pm4.javer.compiler.ir.Ir;

/**
 * All supported values occupy one physical register. Frame headers hold return label and old FP.
 */
final class FanucLayout {
    static final int WORD_CELLS = 1;
    static final int FRAME_HEADER_CELLS = 2;
    static final int ARRAY_PAYLOAD_OFFSET_CELLS = 1;

    private FanucLayout() {
    }

    static int stackCells(Ir.Type type) {
        return switch (type.kind()) {
            case VOID -> 0;
            case CHAR, STRING -> throw new IllegalArgumentException("Unsupported FANUC type " + type.kind());
            default -> 1;
        };
    }

    static MemoryWidth memoryWidth(Ir.Type type) {
        if (stackCells(type) != 1) throw new IllegalArgumentException("Void has no storage width");
        return MemoryWidth.CELL;
    }

    enum MemoryWidth {
        CELL;

        int cells() {
            return 1;
        }

        String loadInstruction() {
            return "LOAD";
        }

        String storeInstruction() {
            return "STORE";
        }
    }
}
