package ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.declaration;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.AstNode;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.AstNodeBase;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.AstNodeVisitor;

/**
 * AST node for a single value declared inside an enum.
 */
public final class EnumItem extends AstNodeBase implements AstNode {

    private final String name;
    private Integer value;

    private EnumItem(Builder builder) {
        this.name = builder.name;
        this.value = builder.value;
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public String getName() {
        return name;
    }

    public Integer getValue() {
        return value;
    }

    public void setValue(Integer value) {
        this.value = value;
    }

    @Override
    public void accept(AstNodeVisitor visitor) {
        visitor.visit(this);
    }

    public static final class Builder {
        private final String name;
        private Integer value;

        private Builder(String name) {
            this.name = name;
        }

        public Builder value(Integer value) {
            this.value = value;
            return this;
        }

        public EnumItem build() {
            return new EnumItem(this);
        }
    }
}
