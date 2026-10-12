package ch.zhaw.it.pm4.javer.compiler.ast.typeinfo;

/**
 * Resolved type information for {@code void}.
 */
public final class VoidTypeInfo implements TypeInfo {

    public static final VoidTypeInfo INSTANCE = new VoidTypeInfo();

    private VoidTypeInfo() {
    }

    @Override
    public String toString() {
        return "void";
    }
}
