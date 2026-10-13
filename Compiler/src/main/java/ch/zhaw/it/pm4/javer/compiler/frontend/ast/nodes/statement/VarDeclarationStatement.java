package ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.statement;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.AstNodeBase;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.type.TypeAstNode;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.symbol.VariableEntry;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.AstNodeVisitor;

/**
 * Statement node representing a variable declaration with an optional initializer.
 */
public final class VarDeclarationStatement extends AstNodeBase implements StatementAstNode {

    private final TypeAstNode type;
    private final String name;
    private final ExpressionAstNode initializer;
    private VariableEntry symbolEntry;

    private VarDeclarationStatement(Builder builder) {
        this.type = builder.type;
        this.name = builder.name;
        this.initializer = builder.initializer;
    }

    public static Builder builder(TypeAstNode type, String name) {
        return new Builder(type, name);
    }

    public TypeAstNode getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public ExpressionAstNode getInitializer() {
        return initializer;
    }

    public VariableEntry getSymbolEntry() {
        return symbolEntry;
    }

    public void setSymbolEntry(VariableEntry symbolEntry) {
        this.symbolEntry = symbolEntry;
    }

    @Override
    public void accept(AstNodeVisitor visitor) {
        visitor.visit(this);
    }

    public static final class Builder {
        private final TypeAstNode type;
        private final String name;
        private ExpressionAstNode initializer;

        private Builder(TypeAstNode type, String name) {
            this.type = type;
            this.name = name;
        }

        public Builder initializer(ExpressionAstNode initializer) {
            this.initializer = initializer;
            return this;
        }

        public VarDeclarationStatement build() {
            return new VarDeclarationStatement(this);
        }
    }
}
