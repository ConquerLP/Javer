package ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.declaration.DeclarationAstNode;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.scope.GlobalScope;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.AstNodeVisitor;

import java.util.List;

/**
 * Root node of a parsed Javer program.
 *
 * <p>The compilation unit owns the top-level declarations and the semantic
 * structures that are populated by later compiler phases.</p>
 */
public final class CompilationUnit extends AstNodeBase implements AstNode {
    private final GlobalScope globalScope;
    private final List<DeclarationAstNode> declarations;

    public CompilationUnit(List<DeclarationAstNode> declarations) {
        this.declarations = declarations;
        this.globalScope = new GlobalScope();
    }

    public List<DeclarationAstNode> getDeclarations() {
        return declarations;
    }

    public GlobalScope getGlobalScope() {
        return globalScope;
    }

    @Override
    public void accept(AstNodeVisitor visitor) {
        visitor.visit(this);
    }
}
