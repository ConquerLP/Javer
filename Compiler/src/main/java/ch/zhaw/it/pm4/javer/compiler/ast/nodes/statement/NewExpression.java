package ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement;

import ch.zhaw.it.pm4.javer.compiler.ast.nodes.type.TypeAstNode;
import ch.zhaw.it.pm4.javer.compiler.visitor.AstNodeVisitor;

import java.util.List;

/**
 * Expression node representing allocation with {@code new}.
 */
public final class NewExpression extends ExpressionAstNodeBase {

    private final TypeAstNode type;
    private final List<ExpressionAstNode> dimensions;
    private final ArrayInitExpression arrayInit;

    private NewExpression(Builder builder) {
        this.type = builder.type;
        this.dimensions = builder.dimensions;
        this.arrayInit = builder.arrayInit;
    }

    public static Builder builder(TypeAstNode type) {
        return new Builder(type);
    }

    public TypeAstNode getType() {
        return type;
    }

    public List<ExpressionAstNode> getDimensions() {
        return dimensions;
    }

    public ArrayInitExpression getArrayInit() {
        return arrayInit;
    }

    @Override
    public void accept(AstNodeVisitor visitor) {
        visitor.visit(this);
    }

    public static final class Builder {
        private final TypeAstNode type;
        private List<ExpressionAstNode> dimensions = List.of();
        private ArrayInitExpression arrayInit;

        private Builder(TypeAstNode type) {
            this.type = type;
        }

        public Builder dimensions(List<ExpressionAstNode> dimensions) {
            this.dimensions = dimensions;
            return this;
        }

        public Builder arrayInit(ArrayInitExpression arrayInit) {
            this.arrayInit = arrayInit;
            return this;
        }

        public NewExpression build() {
            return new NewExpression(this);
        }
    }

}
