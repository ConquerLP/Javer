package ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.declaration;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.AstNode;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.AstNodeBase;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.type.TypeAstNode;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.AstNodeVisitor;

/**
 * AST node for a field declared inside a struct.
 */
public final class StructField extends AstNodeBase implements AstNode {

    private final TypeAstNode type;
    private final String name;

    public StructField(TypeAstNode type, String name) {
        this.type = type;
        this.name = name;
    }

    public TypeAstNode getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    @Override
    public void accept(AstNodeVisitor visitor) {
        visitor.visit(this);
    }
}
