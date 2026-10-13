package ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.statement;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.AstNodeBase;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.AstNodeVisitor;

/**
 * For-loop initializer consisting of a variable declaration.
 */
public final class ForInitVarDeclaration extends AstNodeBase implements ForInit {

    private final VarDeclarationStatement varDeclaration;

    public ForInitVarDeclaration(VarDeclarationStatement varDeclaration) {
        this.varDeclaration = varDeclaration;
    }

    public VarDeclarationStatement getVarDeclaration() {
        return varDeclaration;
    }

    @Override
    public void accept(AstNodeVisitor visitor) {
        visitor.visit(this);
    }
}
