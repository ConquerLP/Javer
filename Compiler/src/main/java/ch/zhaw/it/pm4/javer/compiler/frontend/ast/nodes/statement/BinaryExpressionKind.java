package ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.statement;

/**
 * Operators supported by binary expressions.
 */
public enum BinaryExpressionKind {

    OR, AND, BITWISE_OR, BITWISE_AND, BITWISE_XOR, EQUALS, NOT_EQUALS, LESS, LESS_EQUALS, GREATER, GREATER_EQUALS,
    SHIFT_LEFT, SHIFT_RIGHT,
    ADD, SUBTRACT, MULTIPLY, DIVIDE, MODULO,
    INVALID

}
