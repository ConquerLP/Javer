package ch.zhaw.it.pm4.javer.compiler.backend.vm;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.type.PrimitiveTypeKind;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.typeinfo.*;

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
    public static int stackBytes(TypeInfo type) {
        if (PrimitiveTypeInfo.DOUBLE.equals(type)) {
            return DOUBLE_BYTES;
        }
        if (isWordStackType(type)) {
            return WORD_BYTES;
        }
        throw new IllegalArgumentException("Type has no stack representation: " + type);
    }

    /**
     * Returns the number of bytes used for returning the given type from a function.
     *
     * @param type the return type to evaluate
     * @return number of bytes used for returning the value (0 for void)
     */
    public static int returnBytes(TypeInfo type) {
        if (type instanceof VoidTypeInfo) {
            return 0;
        }
        return stackBytes(type);
    }

    /**
     * Maps a high-level type to its corresponding VM memory width.
     *
     * @param type the type to evaluate
     * @return the memory width used for load/store operations
     * @throws IllegalArgumentException if the type has no memory representation
     */
    public static MemoryWidth memoryWidth(TypeInfo type) {
        if (type instanceof PrimitiveTypeInfo(PrimitiveTypeKind kind)) {
            return switch (kind) {
                case BOOL -> MemoryWidth.BYTE;
                case CHAR -> MemoryWidth.CHAR;
                case DOUBLE -> MemoryWidth.DOUBLE;
                case INT, STRING -> MemoryWidth.WORD;
                case INVALID -> throw new IllegalArgumentException("Invalid primitive type has no memory width.");
            };
        }
        if (type instanceof EnumTypeInfo || type instanceof ArrayTypeInfo || type instanceof StructTypeInfo) {
            return MemoryWidth.WORD;
        }
        throw new IllegalArgumentException("Type has no memory width: " + type);
    }

    private static boolean isWordStackType(TypeInfo type) {
        if (type instanceof PrimitiveTypeInfo(PrimitiveTypeKind kind)) {
            return switch (kind) {
                case BOOL, CHAR, INT, STRING -> true;
                case DOUBLE, INVALID -> false;
            };
        }
        return type instanceof EnumTypeInfo
                || type instanceof ArrayTypeInfo
                || type instanceof StructTypeInfo
                || type instanceof NullTypeInfo;
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
