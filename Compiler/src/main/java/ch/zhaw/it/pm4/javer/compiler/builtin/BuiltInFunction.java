package ch.zhaw.it.pm4.javer.compiler.builtin;

import ch.zhaw.it.pm4.javer.compiler.ast.scope.FunctionScope;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.FunctionEntry;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.ParameterEntry;
import ch.zhaw.it.pm4.javer.compiler.ast.typeinfo.PrimitiveTypeInfo;
import ch.zhaw.it.pm4.javer.compiler.ast.typeinfo.TypeInfo;
import ch.zhaw.it.pm4.javer.compiler.ast.typeinfo.VoidTypeInfo;

import java.util.Arrays;
import java.util.List;

/**
 * Registry of VM-backed functions provided by the language runtime.
 */
public enum BuiltInFunction {
    PRINTB("printb", PrimitiveTypeInfo.BOOL, "PRINTB"),
    PRINTC("printc", PrimitiveTypeInfo.CHAR, "PRINTC"),
    PRINTI("printi", PrimitiveTypeInfo.INT, "PRINTI"),
    PRINTD("printd", PrimitiveTypeInfo.DOUBLE, "PRINTD"),
    PRINTS("prints", PrimitiveTypeInfo.STRING, "PRINTS");

    private static final String PARAMETER_NAME = "value";

    private final String name;
    private final TypeInfo parameterType;
    private final String vmInstruction;

    BuiltInFunction(String name, TypeInfo parameterType, String vmInstruction) {
        this.name = name;
        this.parameterType = parameterType;
        this.vmInstruction = vmInstruction;
    }

    /**
     * @return all built-in functions.
     */
    public static List<BuiltInFunction> all() {
        return Arrays.asList(values());
    }

    /**
     * Finds a built-in function by name.
     *
     * @param name the function name
     * @return the matching BuiltInFunction or {@code null} if not found
     */
    public static BuiltInFunction find(String name) {
        for (BuiltInFunction builtInFunction : values()) {
            if (builtInFunction.getName().equals(name)) return builtInFunction;
        }
        return null;
    }

    /**
     * Creates a function symbol representing this built-in function in the compiler symbol table.
     * The symbol includes semantic parameter metadata. Target-specific layout
     * is added by the selected backend preparation pass.
     *
     * @return a fully initialized FunctionEntry for this built-in function
     */
    public FunctionEntry createSymbol() {
        FunctionEntry entry = new FunctionEntry(name, name);
        entry.setReturnType(VoidTypeInfo.INSTANCE);
        entry.setBuiltIn(true);

        FunctionScope scope = new FunctionScope();
        entry.setScope(scope);

        scope.defineParameter(new ParameterEntry(
                PARAMETER_NAME,
                parameterType,
                0,
                0));

        return entry;
    }

    public String getName() {
        return name;
    }

    public TypeInfo getParameterType() {
        return parameterType;
    }

    public String getVmInstruction() {
        return vmInstruction;
    }
}
