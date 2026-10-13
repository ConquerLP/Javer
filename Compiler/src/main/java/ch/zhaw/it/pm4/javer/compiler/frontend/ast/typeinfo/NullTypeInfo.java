package ch.zhaw.it.pm4.javer.compiler.frontend.ast.typeinfo;

/**
 * Type of the null literal. It can be assigned to reference types.
 */
public final class NullTypeInfo implements TypeInfo {

    public static final NullTypeInfo INSTANCE = new NullTypeInfo();

    private NullTypeInfo() {
    }

    @Override
    public String toString() {
        return "null";
    }
}
