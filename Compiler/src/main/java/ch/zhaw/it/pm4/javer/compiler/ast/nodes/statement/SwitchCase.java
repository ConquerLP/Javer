package ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement;

import ch.zhaw.it.pm4.javer.compiler.ast.nodes.AstNodeBase;

import ch.zhaw.it.pm4.javer.compiler.annotation.JacocoGenerated;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.AstNode;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.case_label.CaseLabelAstNode;
import ch.zhaw.it.pm4.javer.compiler.visitor.AstNodeVisitor;

import java.util.List;

@JacocoGenerated("Skeleton only, remove when fully implemented")
public final class SwitchCase extends AstNodeBase implements AstNode {

    private final boolean isDefault;
    private final List<CaseLabelAstNode> caseLabels;
    private final StatementAstNode statement;

    public SwitchCase(boolean isDefault, List<CaseLabelAstNode> caseLabels, StatementAstNode statement) {
        this.isDefault = isDefault;
        this.caseLabels = caseLabels;
        this.statement = statement;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public List<CaseLabelAstNode> getCaseLabels() {
        return caseLabels;
    }

    public StatementAstNode getStatement() {
        return statement;
    }

    @Override
    public void accept(AstNodeVisitor visitor) {
        visitor.visit(this);
    }
}
