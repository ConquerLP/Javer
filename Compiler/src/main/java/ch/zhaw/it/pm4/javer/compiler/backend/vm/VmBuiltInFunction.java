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
    PRINTS("prints", Ir.Type.STRING, "PRINTS");

    private final String name;
    private final Ir.Type parameterType;
    private final String instruction;

    VmBuiltInFunction(String name, Ir.Type parameterType, String instruction) {
        this.name = name;
        this.parameterType = parameterType;
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

    BuiltInSignature signature() {
        return new BuiltInSignature(name, Ir.Type.VOID,
                List.of(new BuiltInSignature.Parameter("value", parameterType)));
    }

    public Ir.Type parameterType() {
        return parameterType;
    }

    public String instruction() {
        return instruction;
    }
}
