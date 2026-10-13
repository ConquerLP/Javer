package ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.declaration;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.AstNodeBase;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.scope.EnumScope;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.symbol.EnumEntry;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.AstNodeVisitor;

import java.util.List;

/**
 * AST node representing an enum declaration and its declared values.
 */
public final class EnumDeclaration extends AstNodeBase implements DeclarationAstNode {

    private final String name;
    private final List<EnumItem> items;
    private EnumScope enumScope;
    private EnumEntry symbolEntry;

    public EnumDeclaration(String name, List<EnumItem> items) {
        this.name = name;
        this.items = items;
    }

    public String getName() {
        return name;
    }

    public List<EnumItem> getItems() {
        return items;
    }

    public EnumScope getEnumScope() {
        return enumScope;
    }

    public void setEnumScope(EnumScope enumScope) {
        this.enumScope = enumScope;
    }

    public EnumEntry getSymbolEntry() {
        return symbolEntry;
    }

    public void setSymbolEntry(EnumEntry symbolEntry) {
        this.symbolEntry = symbolEntry;
    }

    @Override
    public void accept(AstNodeVisitor visitor) {
        visitor.visit(this);
    }

}
