package ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.statement;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.AstNodeVisitor;

import java.util.Objects;

/**
 * Expression node representing a literal value.
 *
 * @param <V> Java value type stored for the literal
 */
public final class LiteralExpression<V> extends ExpressionAstNodeBase {

    private final LiteralKind kind;
    private final V value;

    /**
     * Constructs a new LiteralExpression with the specified kind and value.
     *
     * @param kind  the literal kind
     * @param value the literal's value
     */
    public LiteralExpression(LiteralKind kind, V value) {
        this.kind = kind;
        this.value = value;
    }

    /**
     * Retrieves the kind of this literal expression.
     *
     * @return the literal kind
     */
    public LiteralKind getKind() {
        return kind;
    }

    /**
     * Retrieves the value of this literal expression.
     *
     * @return the literal's value
     */
    public V getValue() {
        return value;
    }

    @Override
    public void accept(AstNodeVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LiteralExpression<?> that = (LiteralExpression<?>) o;
        return kind == that.kind && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, value);
    }
}
