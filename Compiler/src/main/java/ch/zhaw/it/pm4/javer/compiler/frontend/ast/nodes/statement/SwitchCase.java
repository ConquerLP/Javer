package ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.statement;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.AstNode;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.AstNodeBase;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.case_label.CaseLabelAstNode;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.AstNodeVisitor;

import java.util.List;

/**
 * AST node representing one case block inside a switch statement.
 */
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
