package ch.zhaw.it.pm4.javer.compiler.codegen.vm;

import ch.zhaw.it.pm4.javer.compiler.ast.typeinfo.PrimitiveTypeInfo;
import ch.zhaw.it.pm4.javer.compiler.ast.typeinfo.TypeInfo;
import ch.zhaw.it.pm4.javer.compiler.ast.typeinfo.VoidTypeInfo;
import ch.zhaw.it.pm4.javer.compiler.ast.scope.FunctionScope;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.FunctionEntry;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.ParameterEntry;

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

    FunctionEntry createSymbol() {
        FunctionEntry entry = new FunctionEntry(name);
        entry.setReturnType(VoidTypeInfo.INSTANCE);
        entry.setBuiltIn(true);
        FunctionScope scope = new FunctionScope();
        scope.defineParameter(new ParameterEntry("value", parameterType));
        entry.setScope(scope);
        return entry;
    }

    public TypeInfo parameterType() {
        return parameterType;
    }

    public String instruction() {
        return instruction;
    }
}
