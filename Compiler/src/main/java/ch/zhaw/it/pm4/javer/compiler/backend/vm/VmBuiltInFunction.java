package ch.zhaw.it.pm4.javer.compiler.backend.vm;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.typeinfo.PrimitiveTypeInfo;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.typeinfo.TypeInfo;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.typeinfo.VoidTypeInfo;
import ch.zhaw.it.pm4.javer.compiler.frontend.BuiltInSignature;
import java.util.List;

/**
 * VM instruction mapping for semantic built-in functions.
 */
enum VmBuiltInFunction {
    PRINTB("printb", PrimitiveTypeInfo.BOOL, "PRINTB"),
    PRINTC("printc", PrimitiveTypeInfo.CHAR, "PRINTC"),
    PRINTI("printi", PrimitiveTypeInfo.INT, "PRINTI"),
    PRINTD("printd", PrimitiveTypeInfo.DOUBLE, "PRINTD"),
    PRINTS("prints", PrimitiveTypeInfo.STRING, "PRINTS");

    private final String name;
    private final TypeInfo parameterType;
    private final String instruction;

    VmBuiltInFunction(String name, TypeInfo parameterType, String instruction) {
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
        return new BuiltInSignature(name, VoidTypeInfo.INSTANCE,
                List.of(new BuiltInSignature.Parameter("value", parameterType)));
    }

    public TypeInfo parameterType() {
        return parameterType;
    }

    public String instruction() {
        return instruction;
    }
}
