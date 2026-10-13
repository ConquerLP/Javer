package ch.zhaw.it.pm4.javer.compiler.frontend.analysis;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.AstNodeVisitorBase;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.type.*;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.typeinfo.*;

/** Frontend-only type resolution, separate from generic syntax-tree traversal. */
abstract class TypeResolvingVisitor extends AstNodeVisitorBase {
    protected TypeInfo resolveType(TypeAstNode type) {
        if (type instanceof PrimitiveType primitiveType) {
            return PrimitiveTypeInfo.of(primitiveType.getKind());
        }
        if (type instanceof ArrayType arrayType) {
            return new ArrayTypeInfo(resolveType(arrayType.getBaseType()));
        }
        if (type instanceof VoidType) {
            return VoidTypeInfo.INSTANCE;
        }
        if (type instanceof NamedType namedType) {
            return resolveNamedType(namedType);
        }
        return UnknownTypeInfo.INSTANCE;
    }

    @SuppressWarnings("java:S1172") // default method, overwritten when resolveType is actually used.
    protected TypeInfo resolveNamedType(NamedType type){
        return UnknownTypeInfo.INSTANCE;
    }
}
