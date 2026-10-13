package ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.case_label;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.AstNodeBase;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.symbol.EnumValueEntry;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.AstNodeVisitor;

/**
 * Switch case label that references an enum value by name.
 */
public final class EnumCaseLabel extends AstNodeBase implements CaseLabelAstNode {

    private final String enumTypeName;
    private final String enumValueName;
    private EnumValueEntry resolvedEnumValue;

    public EnumCaseLabel(String enumTypeName, String enumValueName) {
        this.enumTypeName = enumTypeName;
        this.enumValueName = enumValueName;
    }

    public String getEnumTypeName() {
        return enumTypeName;
    }

    public String getEnumValueName() {
        return enumValueName;
    }

    public EnumValueEntry getResolvedEnumValue() {
        return resolvedEnumValue;
    }

    public void setResolvedEnumValue(EnumValueEntry resolvedEnumValue) {
        this.resolvedEnumValue = resolvedEnumValue;
    }

    @Override
    public void accept(AstNodeVisitor visitor) {
        visitor.visit(this);
    }
}
