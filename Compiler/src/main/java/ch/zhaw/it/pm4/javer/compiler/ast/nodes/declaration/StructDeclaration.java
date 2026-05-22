package ch.zhaw.it.pm4.javer.compiler.ast.nodes.declaration;

import ch.zhaw.it.pm4.javer.compiler.ast.nodes.AstNodeBase;

import java.util.List;

import ch.zhaw.it.pm4.javer.compiler.annotation.JacocoGenerated;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.type.NamedType;
import ch.zhaw.it.pm4.javer.compiler.ast.scope.StructScope;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.StructEntry;
import ch.zhaw.it.pm4.javer.compiler.visitor.AstNodeVisitor;

/**
 * AST node for a struct declaration and its fields.
 */
@JacocoGenerated("Skeleton only, remove when fully implemented")
public final class StructDeclaration extends AstNodeBase implements DeclarationAstNode {

    private final String name;
    private final NamedType baseType;
    private final List<StructField> fields;
    private StructScope structScope;
    private StructEntry symbolEntry;

    public StructDeclaration(String name, List<StructField> fields) {
        this(name, null, fields);
    }

    public StructDeclaration(String name, NamedType baseType, List<StructField> fields) {
        this.name = name;
        this.baseType = baseType;
        this.fields = fields;
    }

    public String getName() {
        return name;
    }

    public NamedType getBaseType() {
        return baseType;
    }

    public List<StructField> getFields() {
        return fields;
    }

    public StructScope getStructScope() {
        return structScope;
    }

    public void setStructScope(StructScope structScope) {
        this.structScope = structScope;
    }

    public StructEntry getSymbolEntry() {
        return symbolEntry;
    }

    public void setSymbolEntry(StructEntry symbolEntry) {
        this.symbolEntry = symbolEntry;
    }

    @Override
    public void accept(AstNodeVisitor visitor) {
        visitor.visit(this);
    }
}
