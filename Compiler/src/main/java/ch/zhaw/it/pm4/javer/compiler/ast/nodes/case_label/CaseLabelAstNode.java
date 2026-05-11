package ch.zhaw.it.pm4.javer.compiler.ast.nodes.case_label;

import ch.zhaw.it.pm4.javer.compiler.ast.nodes.AstNode;

public sealed interface CaseLabelAstNode extends AstNode
        permits LiteralCaseLabel, EnumCaseLabel {
}
