package ch.zhaw.it.pm4.javer.compiler.backend.vm;

import ch.zhaw.it.pm4.javer.compiler.ir.Ir;
import ch.zhaw.it.pm4.javer.compiler.frontend.BuiltInSignature;
import java.util.List;

/**
 * VM instruction mapping for semantic built-in functions.
 */
enum VmBuiltInFunction {
    PRINTB("printb", Ir.Type.BOOL, "PRINTB"),
    PRINTC("printc", Ir.Type.CHAR, "PRINTC"),
    PRINTI("printi", Ir.Type.INT, "PRINTI"),
    PRINTD("printd", Ir.Type.DOUBLE, "PRINTD"),
    PRINTS("prints", Ir.Type.STRING, "PRINTS"),
    MOVE_FAST("move_fast", "MOVE_FAST", Ir.Type.VOID, Ir.Type.DOUBLE, Ir.Type.DOUBLE, Ir.Type.DOUBLE),
    MOVE_LINEAR("move_linear", "MOVE_LINEAR", Ir.Type.VOID, Ir.Type.DOUBLE, Ir.Type.DOUBLE, Ir.Type.DOUBLE, Ir.Type.INT),
    MOVE_CW("move_cw", "MOVE_CW", Ir.Type.VOID, Ir.Type.DOUBLE, Ir.Type.DOUBLE, Ir.Type.DOUBLE, Ir.Type.DOUBLE, Ir.Type.INT),
    MOVE_CCW("move_ccw", "MOVE_CCW", Ir.Type.VOID, Ir.Type.DOUBLE, Ir.Type.DOUBLE, Ir.Type.DOUBLE, Ir.Type.DOUBLE, Ir.Type.INT),
    WAIT_MS("wait_ms", "WAIT_MS", Ir.Type.VOID, Ir.Type.INT),
    DRIVE_BOOL("driveBool", "DRIVE, BOOL", Ir.Type.VOID, Ir.Type.STRING, Ir.Type.STRING, Ir.Type.BOOL),
    DRIVE_INT("driveInt", "DRIVE, INT", Ir.Type.VOID, Ir.Type.STRING, Ir.Type.STRING, Ir.Type.INT),
    DRIVE_DOUBLE("driveDouble", "DRIVE, DOUBLE", Ir.Type.VOID, Ir.Type.STRING, Ir.Type.STRING, Ir.Type.DOUBLE),
    READ_BOOL("readBool", "READ, BOOL", Ir.Type.BOOL, Ir.Type.STRING, Ir.Type.STRING),
    READ_INT("readInt", "READ, INT", Ir.Type.INT, Ir.Type.STRING, Ir.Type.STRING),
    READ_DOUBLE("readDouble", "READ, DOUBLE", Ir.Type.DOUBLE, Ir.Type.STRING, Ir.Type.STRING);

    private final String name;
    private final List<Ir.Type> parameterTypes;
    private final Ir.Type returnType;
    private final String instruction;

    VmBuiltInFunction(String name, Ir.Type parameterType, String instruction) {
        this.name = name;
        this.parameterTypes = List.of(parameterType);
        this.returnType = Ir.Type.VOID;
        this.instruction = instruction;
    }

    static VmBuiltInFunction find(String name) {
        for (VmBuiltInFunction function : values()) {
            if (function.name.equals(name)) {
                return function;
            }
        }
        return null;
    }

    VmBuiltInFunction(String name, String instruction, Ir.Type returnType, Ir.Type... parameters) {
        this.name = name;
        this.instruction = instruction;
        this.returnType = returnType;
        this.parameterTypes = List.of(parameters);
    }

    boolean supported(boolean projectX) { return projectX || ordinal() <= PRINTS.ordinal(); }

    boolean matches(Ir.Signature signature) {
        return returnType.equals(signature.returnType()) && parameterTypes.equals(signature.parameters());
    }

    BuiltInSignature signature() {
        return new BuiltInSignature(name, returnType,
                java.util.stream.IntStream.range(0, parameterTypes.size())
                        .mapToObj(i -> new BuiltInSignature.Parameter("arg" + i, parameterTypes.get(i))).toList());
    }
    public String instruction() {
        return instruction;
    }
}
