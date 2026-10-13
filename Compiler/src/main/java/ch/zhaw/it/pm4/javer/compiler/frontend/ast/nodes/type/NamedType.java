package ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.type;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.AstNodeBase;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.symbol.SymbolEntry;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.AstNodeVisitor;

/**
 * AST node for a type referenced by name, such as a struct or enum type.
 */
public final class NamedType extends AstNodeBase implements TypeAstNode {

    private final NameTypeKind kind;
    private final String name;
    private SymbolEntry resolvedEntry;

    public NamedType(NameTypeKind kind, String name) {
        this.kind = kind;
        this.name = name;
    }

    public NameTypeKind getKind() {
        return kind;
    }

    public String getName() {
        return name;
    }

    public SymbolEntry getResolvedEntry() {
        return resolvedEntry;
    }

    public void setResolvedEntry(SymbolEntry resolvedEntry) {
        this.resolvedEntry = resolvedEntry;
    }

    @Override
    public void accept(AstNodeVisitor visitor) {
        visitor.visit(this);
    }
}
