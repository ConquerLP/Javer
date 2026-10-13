package ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.statement;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.symbol.EnumValueEntry;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.symbol.FieldEntry;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.AstNodeVisitor;

/**
 * Expression node representing access to a member on a receiver expression.
 */
public final class MemberAccessExpression extends ExpressionAstNodeBase {

    private final ExpressionAstNode target;
    private final String memberName;
    private FieldEntry resolvedField;
    private EnumValueEntry resolvedEnumValue;

    public MemberAccessExpression(ExpressionAstNode target, String memberName) {
        this.target = target;
        this.memberName = memberName;
    }

    public ExpressionAstNode getTarget() {
        return target;
    }

    public String getMemberName() {
        return memberName;
    }

    @Override
    public void accept(AstNodeVisitor visitor) {
        visitor.visit(this);
    }

    public FieldEntry getResolvedField() {
        return resolvedField;
    }

    public void setResolvedField(FieldEntry resolvedField) {
        this.resolvedField = resolvedField;
    }

    public EnumValueEntry getResolvedEnumValue() {
        return resolvedEnumValue;
    }

    public void setResolvedEnumValue(EnumValueEntry resolvedEnumValue) {
        this.resolvedEnumValue = resolvedEnumValue;
    }
}
