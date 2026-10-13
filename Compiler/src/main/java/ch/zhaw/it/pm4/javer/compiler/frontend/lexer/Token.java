package ch.zhaw.it.pm4.javer.compiler.frontend.lexer;

import ch.zhaw.it.pm4.javer.compiler.source.SourceLocation;

import java.util.Objects;

/**
 * Immutable class representing a token extracted from source code.
 * A token consists of a type, its string value, and its position in the source.
 * String and char literal values are stored without delimiters and with escape
 * sequences already resolved. Based integer literal values are stored without
 * their radix prefix.
 */
public class Token {
    private static final int TOKEN_TYPE_WIDTH = 35;
    private static final int VALUE_WIDTH = 15;
    private static final int POSITION_WIDTH = 3;

    private final TokenType type;
    private final String value;
    private final SourceLocation position;

    /**
     * Creates a new Token with the specified type, value, and position.
     *
     * @param type     the type of the token (must not be null)
     * @param value    the string value of the token
     * @param position the position of the token in the source (must not be null)
     * @throws NullPointerException if type or position is null
     */
    public Token(TokenType type, String value, SourceLocation position) {
        this.type = Objects.requireNonNull(type, "Token type cannot be null");
        this.position = Objects.requireNonNull(position, "Token position cannot be null");
        this.value = value;
    }

    /**
     * Gets the type of this token.
     *
     * @return the TokenType of this token
     */
    public TokenType getTokenType() {
        return type;
    }

    /**
     * Gets the string value of this token.
     *
     * @return the token's value
     */
    public String getValue() {
        return value;
    }

    /**
     * Gets the position of this token in the source code.
     * This is useful for error reporting and diagnostics.
     *
     * @return the position of the token
     */
    public SourceLocation getPosition() {
        return position;
    }

    @Override
    public String toString() {
        return String.format(
                "TokenType: %" + TOKEN_TYPE_WIDTH + "s, value: '%" + VALUE_WIDTH
                        + "s', position: [%" + POSITION_WIDTH + "d : %" + POSITION_WIDTH
                        + "d : %" + POSITION_WIDTH + "d ]",
                type,
                value,
                position.lineNumber(),
                position.startColumn(),
                position.endColumn()
        );
    }
}
