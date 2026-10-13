package ch.zhaw.it.pm4.javer.compiler.frontend.ast.typeinfo;

/**
 * Common contract for resolved semantic types.
 */
public sealed interface TypeInfo
        permits PrimitiveTypeInfo, ArrayTypeInfo, StructTypeInfo, EnumTypeInfo, NullTypeInfo, VoidTypeInfo, UnknownTypeInfo {
}
