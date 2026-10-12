package ch.zhaw.it.pm4.javer.compiler.ast.typeinfo;

/**
 * Common contract for resolved semantic types.
 */
public sealed interface TypeInfo
        permits PrimitiveTypeInfo, ArrayTypeInfo, StructTypeInfo, EnumTypeInfo, NullTypeInfo, VoidTypeInfo, UnknownTypeInfo {
}
