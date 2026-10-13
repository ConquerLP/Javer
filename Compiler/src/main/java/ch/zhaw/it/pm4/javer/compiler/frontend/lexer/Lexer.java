package ch.zhaw.it.pm4.javer.compiler.frontend.lexer;

import ch.zhaw.it.pm4.javer.compiler.source.SourceLocation;
import ch.zhaw.it.pm4.javer.compiler.diagnostics.DiagnosticBag;
import ch.zhaw.it.pm4.javer.compiler.diagnostics.Severity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * The Lexer class is responsible for converting the raw source code into a
 * sequence of tokens that can be used by the parser.
 * It reads the source code character by character, identifies valid tokens
 * (like keywords, identifiers, literals, and symbols), and handles any lexical
 * errors by reporting them through the DiagnosticBag.
 * The lexer also keeps track of the current position in the source code (line
 * and column) to provide accurate error messages and token positions.
 */

public class Lexer {
    public static final String UNTERMINATED_CHAR_LITERAL = "Unterminated char literal";
    private final String sourceCode;
    private final DiagnosticBag diagnostics;

    private int indexInSourceCode = 0;
    private int line = 1;
    private int column = 1;

    private int tokenStartIndex = 0;
    private int tokenStartLine = 1;
    private int tokenStartColumn = 1;

    /**
     * @param sourceCode  the raw source code to be tokenized
     * @param diagnostics the diagnostic bag to report lexical errors to (must not be
     *                    null)
     */
    public Lexer(String sourceCode, DiagnosticBag diagnostics) {
        this.sourceCode = sourceCode == null ? "" : sourceCode;
        this.diagnostics = Objects.requireNonNull(diagnostics, "DiagnosticBag must not be null");
    }

    /**
     * Lexes the entire source code and returns a list of tokens. This method will
     * repeatedly call nextToken() until the end of the source code is reached,
     * collecting all tokens into a list.
     * If any lexical errors are encountered during tokenization, they will be
     * reported through the DiagnosticBag.
     * The returned list always ends with a single SPECIAL_END_OF_FILE token, which
     * the parser relies on as a sentinel.
     */
    public List<Token> lexSourcecode() {
        List<Token> tokens = new ArrayList<>();
        while (true) {
            Token token = nextToken();
            tokens.add(token);
            if (token.getTokenType() == TokenType.SPECIAL_END_OF_FILE) {
                return tokens;
            }
        }
    }

    /**
     * This method will read characters from the source code, identify the type of
     * token (e.g., keyword, identifier, literal, symbol), and return a Token object
     * representing it.
     *
     * @return The next token from the source code.
     */
    private Token nextToken() {
        skipWhitespaceAndComments();

        // Snapshot the start of the next token AFTER skipping trivia.
        tokenStartIndex = indexInSourceCode;
        tokenStartLine = line;
        tokenStartColumn = column;

        if (indexInSourceCode >= sourceCode.length()) {
            return makeToken(TokenType.SPECIAL_END_OF_FILE);
        }

        char currentChar = currentChar();

        if (isIdentifierStart(currentChar)) {
            return lexIdentifierOrKeyword();
        }
        if (isDecimalDigit(currentChar)) {
            return lexNumber();
        }
        if (currentChar == '"') {
            return lexString();
        }
        if (currentChar == '\'') {
            return lexChar();
        }
        return lexSymbol();
    }

    /**
     * @param tokenType the type of token to create (e.g., keyword, identifier,
     *                  literal, symbol)
     * @return A new Token object with the specified type and the current position
     * in the source code.
     */
    private Token makeToken(TokenType tokenType) {
        return makeToken(tokenType, sourceCode.substring(tokenStartIndex, indexInSourceCode));
    }

    private Token makeToken(TokenType tokenType, String value) {
        SourceLocation location = defineSourceLocation();
        return new Token(tokenType, value, location);
    }

    /**
     * @return A new SourceLocation object representing the current position in the
     * source code, using the start column, end column, and line number.
     * This is used for accurate error reporting and token metadata.
     */
    private SourceLocation defineSourceLocation() {
        int safeStart = Math.max(1, tokenStartColumn);
        int endCol;
        if (line == tokenStartLine) {
            endCol = Math.max(safeStart, column - 1);
        } else {
            // Multi-line token (e.g. block string): SourceLocation is single-line, so
            // collapse to the token's start line.
            endCol = safeStart;
        }
        int safeLine = Math.max(1, tokenStartLine);
        return new SourceLocation(safeStart, endCol, safeLine);
    }

    /**
     * @param message The error message to report
     *                This method will report a lexical error with the given
     *                message, including the current position in the source code for
     *                accurate diagnostics.
     *                The error will be added to the DiagnosticBag for later
     *                retrieval and reporting to the user.
     */
    private void error(String message) {
        SourceLocation location = defineSourceLocation();
        diagnostics.add(location, Severity.ERROR, message);
    }

    /**
     * This method skips whitespace characters (like spaces, tabs, and newlines) and
     * comments in the source code. It will advance the current position until it
     * encounters a non-whitespace, non-comment character or reaches the end of the
     * source code.
     * This ensures that the lexer only processes meaningful tokens and ignores
     * irrelevant characters.
     * The method should also handle different types of comments (e.g., single-line
     * comments starting with // and multi-line comments enclosed in slash-star and
     * star-slash) and report any unterminated comment errors if necessary.
     */
    private void skipWhitespaceAndComments() {
        while (indexInSourceCode < sourceCode.length()) {
            char currentChar = currentChar();

            boolean skipped =
                    skipWhitespace(currentChar)
                            || skipLineComment(currentChar)
                            || skipBlockComment(currentChar);

            if (!skipped) {
                break;
            }
        }
    }

    private boolean skipWhitespace(char currentChar) {
        if (Character.isWhitespace(currentChar)) {
            advance();
            return true;
        }
        return false;
    }

    private boolean skipLineComment(char currentChar) {
        if (currentChar == '/' && peek() == '/') {
            while (indexInSourceCode < sourceCode.length()
                    && !isLineTerminator(currentChar())) {
                advance();
            }
            return true;
        }
        return false;
    }

    private boolean skipBlockComment(char currentChar) {
        if (!(currentChar == '/' && peek() == '*')) {
            return false;
        }

        tokenStartIndex = indexInSourceCode;
        tokenStartLine = line;
        tokenStartColumn = column;

        advance();
        advance();

        boolean closed = false;

        while (indexInSourceCode < sourceCode.length()) {
            if (currentChar() == '*' && peek() == '/') {
                advance();
                advance();
                closed = true;
                break;
            }
            advance();
        }

        if (!closed) {
            error("Unterminated block comment");
        }

        return true;
    }

    /**
     * @return A Token object representing the next token in the source code, which
     * is a number literal.
     * Supports decimal integers/doubles (with optional exponent), hex
     * (0x..), octal (0o..) and binary (0b..) literals.
     */
    private Token lexNumber() {
        Token prefixed = lexPrefixedNumber();
        if (prefixed != null) {
            return prefixed;
        }

        // Decimal integer / double
        consumeDigitsForBase(10);

        // "10..2"
        if (isMalformedRangeLiteral()) {
            return lexMalformedRangeLiteral();
        }

        boolean isDouble = lexFractionalPart();

        if (isMalformedDoubleLiteral(isDouble)) {
            recoverMalformedDoubleLiteral();
        }

        return makeToken(isDouble ? TokenType.LITERAL_DOUBLE : TokenType.LITERAL_INTEGER);
    }

    private Token lexPrefixedNumber() {
        if (currentChar() != '0') {
            return null;
        }

        return switch (peek()) {
            case 'x', 'X' -> lexBasedLiteral(
                    16,
                    TokenType.LITERAL_HEX,
                    "Hexadecimal literal must have at least one digit",
                    this::isHexDigit
            );

            case 'o', 'O' -> lexBasedLiteral(
                    8,
                    TokenType.LITERAL_OCTAL,
                    "Octal literal must have at least one digit",
                    this::isOctalDigit
            );

            case 'b', 'B' -> lexBasedLiteral(
                    2,
                    TokenType.LITERAL_BINARY,
                    "Binary literal must have at least one digit",
                    this::isBinaryDigit
            );

            default -> null;
        };
    }

    private Token lexBasedLiteral(
            int base,
            TokenType tokenType,
            String errorMessage,
            Predicate<Character> digitValidator
    ) {
        advance();
        advance();

        int digitsStartIndex = indexInSourceCode;

        if (!digitValidator.test(currentChar())) {
            error(errorMessage);
        }

        consumeDigitsForBase(base);

        return makeToken(
                tokenType,
                sourceCode.substring(digitsStartIndex, indexInSourceCode)
        );
    }

    private boolean isMalformedRangeLiteral() {
        return currentChar() == '.' && peek() == '.';
    }

    private Token lexMalformedRangeLiteral() {
        error("Malformed number literal: consecutive decimal points");

        advance();
        advance();

        consumeDigitsForBase(10);

        return makeToken(TokenType.LITERAL_DOUBLE);
    }

    private boolean lexFractionalPart() {
        if (currentChar() == '.' && isDecimalDigit(peek())) {
            advance();
            consumeDigitsForBase(10);
            return true;
        }
        return false;
    }

    private boolean isMalformedDoubleLiteral(boolean isDouble) {
        return isDouble && currentChar() == '.';
    }

    private void recoverMalformedDoubleLiteral() {
        error("Malformed number literal: too many decimal points");

        while (currentChar() == '.') {
            advance();
            consumeDigitsForBase(10);
        }
    }

    /**
     * @return A Token object representing the next token in the source
     * code, which is a string literal.
     * Handles common escape sequences (\n, \t, \r, \\, \", \', \0, \b, \f).
     */
    private Token lexString() {
        StringBuilder value = new StringBuilder();
        advance();
        while (indexInSourceCode < sourceCode.length()) {
            char currentChar = currentChar();
            if (currentChar == '"') {
                advance();
                return makeToken(TokenType.LITERAL_STRING, value.toString());
            }
            if (isLineTerminator(currentChar)) {
                error("Unterminated string literal");
                return makeToken(TokenType.SPECIAL_UNKNOWN);
            }
            if (currentChar == '\\') {
                advance();
                if (indexInSourceCode >= sourceCode.length()) {
                    break;
                }
                char esc = currentChar();
                if (isInvalidEscape(esc)) {
                    error("Invalid escape sequence: \\" + esc);
                    value.append(esc);
                } else {
                    value.append(resolveEscape(esc));
                }
                advance();
            } else {
                value.append(currentChar);
                advance();
            }
        }
        error("Unterminated string literal");
        return makeToken(TokenType.SPECIAL_UNKNOWN);
    }

    /**
     * @return A Token object representing the next token in the source
     * code, which is a char literal.
     * Supports single character or an escaped character between single
     * quotes.
     */
    private Token lexChar() {
        advance();
        if (indexInSourceCode >= sourceCode.length() || isLineTerminator(currentChar())) {
            error(UNTERMINATED_CHAR_LITERAL);
            return makeToken(TokenType.SPECIAL_UNKNOWN);
        }
        if (currentChar() == '\'') {
            error("Empty char literal");
            advance();
            return makeToken(TokenType.SPECIAL_UNKNOWN);
        }

        String value = currentChar() == '\\'
                ? lexEscapedChar()
                : lexRegularChar();

        if (isMissingClosingQuote()) {
            recoverUnterminatedCharLiteral();
            return makeToken(TokenType.SPECIAL_UNKNOWN);
        }

        advance();

        return makeToken(TokenType.LITERAL_CHAR, value);
    }

    private String lexEscapedChar() {
        advance();

        if (indexInSourceCode >= sourceCode.length()) {
            error(UNTERMINATED_CHAR_LITERAL);
            return "";
        }

        char esc = currentChar();

        String value;

        if (isInvalidEscape(esc)) {
            error("Invalid escape sequence: \\" + esc);
            value = String.valueOf(esc);
        } else {
            value = String.valueOf(resolveEscape(esc));
        }

        advance();

        return value;
    }

    private String lexRegularChar() {
        String value = String.valueOf(currentChar());
        advance();
        return value;
    }

    private boolean isMissingClosingQuote() {
        return indexInSourceCode >= sourceCode.length()
                || currentChar() != '\'';
    }

    private void recoverUnterminatedCharLiteral() {
        error(UNTERMINATED_CHAR_LITERAL);

        while (indexInSourceCode < sourceCode.length()
                && currentChar() != '\''
                && !isLineTerminator(currentChar())) {
            advance();
        }

        if (indexInSourceCode < sourceCode.length()
                && currentChar() == '\'') {
            advance();
        }
    }

    /**
     * @return A Token object representing the next token in the source
     * code, which is a symbol literal.
     * Handles all operators and delimiters defined in TokenType.
     * Multi-character operators are matched greedily.
     */
    private Token lexSymbol() {
        TokenType.FixedTokenMatch match = TokenType.fixedTokenAt(sourceCode, indexInSourceCode);
        if (match != null) {
            advance(match.length());
            return makeToken(match.tokenType());
        }

        char currentChar = currentChar();
        error("Unexpected character: '" + currentChar + "'");
        advance();
        return makeToken(TokenType.SPECIAL_UNKNOWN);
    }

    private void advance(int count) {
        for (int i = 0; i < count; i++) {
            advance();
        }
    }

    /**
     * @return A Token object representing the next token in the source
     * code, which is a keyword or an identifier.
     * The method will read characters until it encounters a non-identifier
     * character, then check if the resulting string matches any known
     * keywords.
     * If it does, it will return a keyword token; otherwise, it will return
     * an identifier token.
     * The literals {@code true}, {@code false} and {@code null} are
     * recognised here and emitted as LITERAL_BOOLEAN / LITERAL_NULL.
     */
    private Token lexIdentifierOrKeyword() {
        while (isIdentifierPart(currentChar())) {
            advance();
        }
        String keywordText = sourceCode.substring(tokenStartIndex, indexInSourceCode);
        return makeToken(TokenType.fromWordLexeme(keywordText));
    }

    /**
     * @return The current character in the source code at the current
     * index. If the index is out of bounds (i.e., past the end of the
     * source code), it returns a null character ('\0') to indicate the end
     * of input.
     * This method is used to safely access characters in the source code
     * without risking an IndexOutOfBoundsException, and it allows the lexer
     * to detect when it has reached the end of the source code.
     */
    private char currentChar() {
        return indexInSourceCode < sourceCode.length() ? sourceCode.charAt(indexInSourceCode) : '\0';
    }

    /**
     * @return The character at the specified offset from the current index in the
     * source code. If the resulting index is out of bounds, it returns a
     * null character ('\0').
     * This method allows the lexer to look ahead in the source code without
     * advancing the current position, which is useful for making decisions
     * based on upcoming characters (e.g., distinguishing between '=' and
     * '==').
     */
    private char peek() {
        return indexInSourceCode + 1 < sourceCode.length() ? sourceCode.charAt(indexInSourceCode + 1) : '\0';
    }

    /**
     * This method advances the current index in the source code by one character,
     * updating the line and column counters accordingly. If the current character
     * is a newline ('\n'), it increments the line number and resets the column to
     * one; otherwise, it increments the column number.
     * This method is essential for moving through the source code while keeping
     * accurate track of the current position for error reporting and token
     * metadata.
     */
    private void advance() {
        if (indexInSourceCode < sourceCode.length()) {
            char currentChar = sourceCode.charAt(indexInSourceCode);
            indexInSourceCode++;
            if (currentChar == '\n') {
                line++;
                column = 1;
            } else if (currentChar == '\r') {
                // no double counting 
                if (indexInSourceCode < sourceCode.length() && sourceCode.charAt(indexInSourceCode) == '\n') {
                    column++;
                } else {
                    line++;
                    column = 1;
                }
            } else if (currentChar == '\t') {
                column = column + (4 - ((column - 1) % 4));
            } else {
                column++;
            }
        }
    }

    /**
     * @return True if the given character is a valid starting character for
     * an identifier (i.e., a letter or an underscore), and false otherwise.
     * This method is used to determine if the lexer should start lexing an
     * identifier or keyword when it encounters a character.
     * In many programming languages, identifiers must start with a letter
     * (a-z, A-Z) or an underscore (_), and cannot start with a digit or
     * other special characters. This method enforces that rule during
     * tokenization.
     */
    private boolean isIdentifierStart(char c) {
        return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c == '_';
    }

    /**
     * @return True if the given character is a valid continuation character
     * for an identifier (letter, digit, or underscore).
     * Identifier continuation characters are broader than start characters
     * since digits are allowed after the first character.
     */
    private boolean isIdentifierPart(char c) {
        return isIdentifierStart(c) || isDecimalDigit(c);
    }

    /**
     * @return True if the given character is a valid hexadecimal digit
     * (i.e., 0-9, a-f, A-F), and false otherwise. This method is used to
     * determine if a character can be part of a hexadecimal number literal
     * during tokenization.
     * Hexadecimal digits include the numbers 0 through 9 and the letters A
     * through F (both uppercase and lowercase), which represent the values
     * 10 through 15. This method checks for those valid characters when
     * lexing hexadecimal literals.
     * For example, in a hexadecimal literal like "0x1A3F", the characters
     * '1', 'A', '3', and 'F' would all return true when passed to this
     * method.
     */
    private boolean isHexDigit(char c) {
        return c >= '0' && c <= '9' || c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F';
    }

    /**
     * @return True if the given character is a valid decimal digit (i.e.,
     * 0-9), and false otherwise. This method is used to determine if a
     * character can be part of a decimal number literal during
     * tokenization.
     * Decimal digits include the numbers 0 through 9. This method checks
     * for those valid characters when lexing decimal literals.
     * For example, in a decimal literal like "12345", the characters '1',
     * '2', '3', '4', and '5' would all return true when passed to this
     * method.
     */
    private boolean isDecimalDigit(char c) {
        return c >= '0' && c <= '9';
    }

    /**
     * @return True if the given character is a valid octal digit (i.e.,
     * 0-7), and false otherwise. This method is used to determine if a
     * character can be part of an octal number literal during tokenization.
     * Octal digits include the numbers 0 through 7. This method checks for
     * those valid characters when lexing octal literals.
     * For example, in an octal literal like "0o0755", the characters '0',
     * '7', '5', and '5' would all return true when passed to this method,
     * while '8' or '9' would return false.
     */
    private boolean isOctalDigit(char c) {
        return c >= '0' && c <= '7';
    }

    /**
     * @return True if the given character is a valid binary digit (i.e., 0
     * or 1), and false otherwise. This method is used to determine if a
     * character can be part of a binary number literal during tokenization.
     * Binary digits include only the numbers 0 and 1. This method checks
     * for those valid characters when lexing binary literals.
     * For example, in a binary literal like "0b1010", the characters '1'
     * and '0' would return true when passed to this method, while any other
     * character would return false.
     */
    private boolean isBinaryDigit(char c) {
        return c == '0' || c == '1';
    }

    /**
     * @param c    the character to check
     * @param base the base of the number system (only 2, 8, 10, 16)
     * @return True if the given character is a valid digit for the
     * specified base (2, 8, 10, or 16), and false otherwise. This method is
     * used to determine if a character can be part of a number literal in
     * the given base during tokenization.
     * The method uses a switch statement to check the base and calls the
     * appropriate helper method (isBinaryDigit, isOctalDigit,
     * isDecimalDigit, or isHexDigit) to validate the character based on the
     * specified base.
     * For example, if the base is 16, this method will return true for
     * characters '0' through '9', 'a' through 'f', and 'A' through 'F',
     * while it will return false for any other character.
     */
    private boolean isDigitForBase(char c, int base) {
        return switch (base) {
            case 2 -> isBinaryDigit(c);
            case 8 -> isOctalDigit(c);
            case 10 -> isDecimalDigit(c);
            case 16 -> isHexDigit(c);
            default -> false;
        };
    }

    /**
     * Consumes a run of digits for the given base, advancing the cursor past
     * each valid digit. Stops at the first non-digit character.
     *
     * @param base the numeric base (2, 8, 10, or 16)
     */
    private void consumeDigitsForBase(int base) {
        while (isDigitForBase(currentChar(), base)) {
            advance();
        }
    }

    /**
     * @return returns true if the given character is not a recognised escape sequence
     * character inside a string or char literal.
     * Recognised escapes: n, r, t, b, f, 0, ", ', \.
     */
    private boolean isInvalidEscape(char c) {
        return c != 'n' && c != 'r' && c != 't' && c != 'b' && c != 'f'
                && c != '0' && c != '"' && c != '\'' && c != '\\';
    }

    private char resolveEscape(char c) {
        return switch (c) {
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            case 'b' -> '\b';
            case 'f' -> '\f';
            case '0' -> '\0';
            case '"' -> '"';
            case '\'' -> '\'';
            case '\\' -> '\\';
            default -> c;
        };
    }

    /**
     * @return True if the given character is a line terminator (\n or \r).
     */
    private boolean isLineTerminator(char c) {
        return c == '\n' || c == '\r';
    }

}
