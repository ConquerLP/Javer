package ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.case_label;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.AstNode;

/**
 * Marker interface for labels that can appear on a switch case.
 */
public sealed interface CaseLabelAstNode extends AstNode
        permits LiteralCaseLabel, EnumCaseLabel {
}
