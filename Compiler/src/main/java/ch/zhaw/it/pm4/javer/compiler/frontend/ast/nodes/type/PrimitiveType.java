package ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.type;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.AstNodeBase;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.AstNodeVisitor;

/**
 * AST node representing a built-in primitive type.
 */
public final class PrimitiveType extends AstNodeBase implements TypeAstNode {

    private final PrimitiveTypeKind kind;

    public PrimitiveType(PrimitiveTypeKind kind) {
        this.kind = kind;
    }

    public PrimitiveTypeKind getKind() {
        return kind;
    }

    @Override
    public void accept(AstNodeVisitor visitor) {
        visitor.visit(this);
    }
}
