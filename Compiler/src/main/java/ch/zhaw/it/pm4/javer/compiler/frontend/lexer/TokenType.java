package ch.zhaw.it.pm4.javer.compiler.frontend.lexer;

/**
 * Defines all token types produced by the lexer.
 *
 * <p>Each token type represents either a literal, identifier, keyword, operator,
 * delimiter, or special lexer token used during parsing and diagnostics.
 */
public enum TokenType {
    // Literals
    LITERAL_INTEGER("Literal: integer"),
    LITERAL_DOUBLE("Literal: double"),
    LITERAL_HEX("Literal: hex"),
    LITERAL_BINARY("Literal: binary"),
    LITERAL_OCTAL("Literal: octal"),
    LITERAL_STRING("Literal: string"),
    LITERAL_CHAR("Literal: char"),
    LITERAL_BOOLEAN("Literal: boolean", "true", "false"),
    LITERAL_NULL("null"),

    // Identifiers
    ID_IDENTIFIER("Identifier"),


    // Keywords
    KEYWORD_IF("if"),
    KEYWORD_ELSE("else"),
    KEYWORD_WHILE("while"),
    KEYWORD_DO("do"),
    KEYWORD_FOR("for"),
    KEYWORD_RETURN("return"),
    KEYWORD_FUNCTION("fn"),
    KEYWORD_BREAK("break"),
    KEYWORD_CONTINUE("continue"),
    KEYWORD_SWITCH("switch"),
    KEYWORD_CASE("case"),
    KEYWORD_DEFAULT("default"),
    KEYWORD_LET("let"),
    KEYWORD_CALL("call"),
    KEYWORD_CAST("cast"),
    KEYWORD_NEW("new"),

    //Types and Return types
    TYPE_STRUCT("struct"),
    TYPE_INTEGER("int"),
    TYPE_DOUBLE("double"),
    TYPE_BOOLEAN("boolean"),
    TYPE_STRING("string"),
    TYPE_CHARACTER("char"),
    TYPE_VOID("void"),
    TYPE_ENUM("enum"),


    // Operators
    OPERATOR_PLUS("+"),
    OPERATOR_MINUS("-"),
    OPERATOR_MULTIPLY("*"),
    OPERATOR_DIVIDE("/"),
    OPERATOR_MODULO("%"),
    OPERATOR_ASSIGN("="),
    OPERATOR_PLUS_ASSIGN("+="),
    OPERATOR_MINUS_ASSIGN("-="),
    OPERATOR_MULTIPLY_ASSIGN("*="),
    OPERATOR_DIVIDE_ASSIGN("/="),
    OPERATOR_MODULO_ASSIGN("%="),
    OPERATOR_EQUALS("=="),
    OPERATOR_NOT_EQUALS("!="),
    OPERATOR_LESS_THAN("<"),
    OPERATOR_GREATER_THAN(">"),
    OPERATOR_LESS_EQUAL("<="),
    OPERATOR_GREATER_EQUAL(">="),
    OPERATOR_LOGICAL_NOT("!", "!", "not"),
    OPERATOR_INCREMENT("++"),
    OPERATOR_DECREMENT("--"),
    OPERATOR_OR("||", "||", "or"),
    OPERATOR_AND("&&", "&&", "and"),
    OPERATOR_BITWISE_AND("&"),
    OPERATOR_BITWISE_OR("|"),
    OPERATOR_BITWISE_XOR("^"),
    OPERATOR_BITWISE_NOT("~"),
    OPERATOR_BITSHIFT_LEFT("<<"),
    OPERATOR_BITSHIFT_RIGHT(">>"),
    OPERATOR_BITWISE_OR_ASSIGN("|="),
    OPERATOR_BITWISE_AND_ASSIGN("&="),
    OPERATOR_BITWISE_XOR_ASSIGN("^="),
    OPERATOR_BITSHIFT_LEFT_ASSIGN("<<="),
    OPERATOR_BITSHIFT_RIGHT_ASSIGN(">>="),

    // Delimiters
    SYMBOL_LEFT_PARENTHESIS("("),
    SYMBOL_RIGHT_PARENTHESIS(")"),
    SYMBOL_LEFT_BRACE("{"),
    SYMBOL_RIGHT_BRACE("}"),
    SYMBOL_LEFT_BRACKET("["),
    SYMBOL_RIGHT_BRACKET("]"),
    SYMBOL_SEMICOLON(";"),
    SYMBOL_COMMA(","),
    SYMBOL_ELLIPSIS("..."),
    SYMBOL_DOT("."),
    SYMBOL_COLON(":"),
    SYMBOL_QUESTION_MARK("?"),

    // Special Tokens
    SPECIAL_END_OF_FILE("end of file"),
    SPECIAL_UNKNOWN("unknown token");

    private final String diagnosticName;
    private final String[] lexemes;

    TokenType(String diagnosticName, String... lexemes) {
        this.diagnosticName = diagnosticName;
        this.lexemes = lexemes;
    }

    /**
     * Resolves a word-based lexeme to a token type.
     *
     * @param lexeme the input lexeme
     * @return matching TokenType or {@link #ID_IDENTIFIER} if none matches
     */
    public static TokenType fromWordLexeme(String lexeme) {
        for (TokenType tokenType : values()) {
            if (tokenType.isWordToken() && tokenType.matchesLexeme(lexeme)) {
                return tokenType;
            }
        }
        return ID_IDENTIFIER;
    }

    /**
     * Finds the best matching fixed token at the given source position.
     *
     * @param source     the source code being scanned
     * @param startIndex position in the source string
     * @return the longest matching fixed token, or null if none found
     */
    public static FixedTokenMatch fixedTokenAt(String source, int startIndex) {
        FixedTokenMatch bestMatch = null;
        for (TokenType tokenType : values()) {
            if (!tokenType.isFixedToken()) {
                continue;
            }
            int length = tokenType.matchLengthAt(source, startIndex);
            if (length > 0 && (bestMatch == null || length > bestMatch.length())) {
                bestMatch = new FixedTokenMatch(tokenType, length);
            }
        }
        return bestMatch;
    }

    /**
     * Returns the human-readable diagnostic name of this token type.
     *
     * @return diagnostic name used in error messages and reporting
     */
    public String diagnosticName() {
        return diagnosticName;
    }

    private boolean isWordToken() {
        return name().startsWith("KEYWORD_")
                || name().startsWith("TYPE_")
                || this == LITERAL_BOOLEAN
                || this == LITERAL_NULL
                || this == OPERATOR_AND
                || this == OPERATOR_OR
                || this == OPERATOR_LOGICAL_NOT;
    }

    private boolean isFixedToken() {
        return name().startsWith("OPERATOR_") || name().startsWith("SYMBOL_");
    }

    private boolean matchesLexeme(String lexeme) {
        if (lexemes.length == 0) {
            return diagnosticName.equals(lexeme);
        }
        for (String tokenLexeme : lexemes) {
            if (tokenLexeme.equals(lexeme)) {
                return true;
            }
        }
        return false;
    }

    private int matchLengthAt(String source, int startIndex) {
        if (lexemes.length == 0) {
            return source.startsWith(diagnosticName, startIndex) ? diagnosticName.length() : 0;
        }
        for (String tokenLexeme : lexemes) {
            if (source.startsWith(tokenLexeme, startIndex)) {
                return tokenLexeme.length();
            }
        }
        return 0;
    }

    public record FixedTokenMatch(TokenType tokenType, int length) {
    }

}
