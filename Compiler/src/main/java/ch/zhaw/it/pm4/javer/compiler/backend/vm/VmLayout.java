package ch.zhaw.it.pm4.javer.compiler.backend.vm;

import ch.zhaw.it.pm4.javer.compiler.ir.Ir;

/**
 * Centralizes the byte layout expected by the VM bytecode target.
 */
public final class VmLayout {

    public static final int BYTE_BYTES = 1;
    public static final int CHAR_BYTES = 2;
    public static final int WORD_BYTES = 4;
    public static final int DOUBLE_BYTES = 8;
    public static final int FRAME_HEADER_BYTES = 16;
    public static final int ARRAY_LENGTH_BYTES = WORD_BYTES;
    public static final int ARRAY_PAYLOAD_OFFSET_BYTES = ARRAY_LENGTH_BYTES;

    private VmLayout() {
    }

    /**
     * Returns the number of stack bytes required to represent the given type.
     *
     * @param type the type to evaluate
     * @return the number of bytes required on the stack
     * @throws IllegalArgumentException if the type has no stack representation
     */
    public static int stackBytes(Ir.Type type) {
        return switch (type.kind()) {
            case DOUBLE -> DOUBLE_BYTES;
            case VOID -> 0;
            case BOOL, CHAR, INT, STRING, ENUM, ARRAY, STRUCT, ADDRESS, NULL -> WORD_BYTES;
        };
    }

    public static int returnBytes(Ir.Type type) { return stackBytes(type); }

    public static MemoryWidth memoryWidth(Ir.Type type) {
        return switch (type.kind()) {
            case BOOL -> MemoryWidth.BYTE;
            case CHAR -> MemoryWidth.CHAR;
            case DOUBLE -> MemoryWidth.DOUBLE;
            case INT, STRING, ENUM, ARRAY, STRUCT, ADDRESS, NULL -> MemoryWidth.WORD;
            case VOID -> throw new IllegalArgumentException("Void has no storage width");
        };
    }

    public enum MemoryWidth {
        BYTE(BYTE_BYTES, "LOAD1", "STORE1"),
        CHAR(CHAR_BYTES, "LOAD2", "STORE2"),
        WORD(WORD_BYTES, "LOAD4", "STORE4"),
        DOUBLE(DOUBLE_BYTES, "LOAD8", "STORE8");

        private final int bytes;
        private final String loadInstruction;
        private final String storeInstruction;

        MemoryWidth(int bytes, String loadInstruction, String storeInstruction) {
            this.bytes = bytes;
            this.loadInstruction = loadInstruction;
            this.storeInstruction = storeInstruction;
        }

        public int bytes() {
            return bytes;
        }

        public String loadInstruction() {
            return loadInstruction;
        }

        public String storeInstruction() {
            return storeInstruction;
        }
    }
}
