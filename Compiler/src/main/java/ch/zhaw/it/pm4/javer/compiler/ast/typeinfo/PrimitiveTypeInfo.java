package ch.zhaw.it.pm4.javer.compiler.ast.typeinfo;

import ch.zhaw.it.pm4.javer.compiler.ast.nodes.type.PrimitiveTypeKind;

/**
 * Resolved type information for a primitive value.
 *
 * @param kind primitive type kind
 */
public record PrimitiveTypeInfo(PrimitiveTypeKind kind) implements TypeInfo {

    public static final PrimitiveTypeInfo INT = new PrimitiveTypeInfo(PrimitiveTypeKind.INT);
    public static final PrimitiveTypeInfo DOUBLE = new PrimitiveTypeInfo(PrimitiveTypeKind.DOUBLE);
    public static final PrimitiveTypeInfo BOOL = new PrimitiveTypeInfo(PrimitiveTypeKind.BOOL);
    public static final PrimitiveTypeInfo CHAR = new PrimitiveTypeInfo(PrimitiveTypeKind.CHAR);
    public static final PrimitiveTypeInfo STRING = new PrimitiveTypeInfo(PrimitiveTypeKind.STRING);
    public static final PrimitiveTypeInfo INVALID = new PrimitiveTypeInfo(PrimitiveTypeKind.INVALID);

    public static PrimitiveTypeInfo of(PrimitiveTypeKind kind) {
        return switch (kind) {
            case INT -> INT;
            case DOUBLE -> DOUBLE;
            case BOOL -> BOOL;
            case CHAR -> CHAR;
            case STRING -> STRING;
            case INVALID -> INVALID;
        };
    }

    @Override
    public String toString() {
        if (kind == PrimitiveTypeKind.BOOL) {
            return "boolean";
        }
        return kind.name().toLowerCase();
    }
}
