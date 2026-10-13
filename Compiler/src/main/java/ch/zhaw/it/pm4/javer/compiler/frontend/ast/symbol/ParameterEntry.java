package ch.zhaw.it.pm4.javer.compiler.frontend.ast.symbol;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.typeinfo.TypeInfo;

/**
 * Symbol table entry for a function parameter.
 */
public final class ParameterEntry extends StorageEntry {

    private final boolean variadic;
    private final TypeInfo variadicElementType;

    public ParameterEntry(String name, TypeInfo type) {
        this(name, type, false, null);
    }

    public ParameterEntry(
            String name,
            TypeInfo type,
            boolean variadic,
            TypeInfo variadicElementType) {
        super(name, type);
        this.variadic = variadic;
        this.variadicElementType = variadicElementType;
    }

    public boolean isVariadic() {
        return variadic;
    }

    public TypeInfo getVariadicElementType() {
        return variadicElementType;
    }
}
