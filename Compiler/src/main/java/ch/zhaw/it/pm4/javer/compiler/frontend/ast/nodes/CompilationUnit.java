package ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.declaration.DeclarationAstNode;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.scope.GlobalScope;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.AstNodeVisitor;

import ch.zhaw.it.pm4.javer.compiler.source.SourceLocation;

import java.util.List;

/**
 * Root node of a parsed Javer program.
 *
 * <p>The compilation unit owns the top-level declarations and the semantic
 * structures that are populated by later compiler phases.</p>
 */
public final class CompilationUnit extends AstNodeBase implements AstNode {
    public record ImportDirective(String name, SourceLocation location) {}

    private final List<ImportDirective> imports;
    private final GlobalScope globalScope;
    private final List<DeclarationAstNode> declarations;

    public CompilationUnit(List<DeclarationAstNode> declarations) {
        this(declarations, List.of());
    }

    public CompilationUnit(List<DeclarationAstNode> declarations, List<ImportDirective> imports) {
        this.declarations = declarations;
        this.imports = List.copyOf(imports);
        this.globalScope = new GlobalScope();
    }

    public List<DeclarationAstNode> getDeclarations() {
        return declarations;
    }

    public List<ImportDirective> getImports() {
        return imports;
    }

    public GlobalScope getGlobalScope() {
        return globalScope;
    }

    @Override
    public void accept(AstNodeVisitor visitor) {
        visitor.visit(this);
    }
}
