package ch.zhaw.it.pm4.javer.compiler.ast.typeinfo;

/**
 * Placeholder type used when semantic analysis cannot resolve a type yet.
 */
public final class UnknownTypeInfo implements TypeInfo {

    public static final UnknownTypeInfo INSTANCE = new UnknownTypeInfo();

    private UnknownTypeInfo() {
    }

    @Override
    public String toString() {
        return "<unknown>";
    }
}
