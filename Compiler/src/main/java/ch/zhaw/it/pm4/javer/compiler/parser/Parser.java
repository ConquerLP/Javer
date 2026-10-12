package ch.zhaw.it.pm4.javer.compiler.parser;

import ch.zhaw.it.pm4.javer.compiler.ast.nodes.AstNode;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.CompilationUnit;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.case_label.CaseLabelAstNode;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.case_label.EnumCaseLabel;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.case_label.LiteralCaseLabel;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.declaration.*;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.*;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.type.*;
import ch.zhaw.it.pm4.javer.compiler.lexer.Token;
import ch.zhaw.it.pm4.javer.compiler.lexer.TokenType;
import ch.zhaw.it.pm4.javer.compiler.misc.SourceLocation;
import ch.zhaw.it.pm4.javer.compiler.misc.SourceRange;
import ch.zhaw.it.pm4.javer.compiler.misc.diagnostics.DiagnosticBag;
import ch.zhaw.it.pm4.javer.compiler.misc.diagnostics.Severity;

import java.util.*;

/**
 * Recursive-descent parser for Javer source code. It consumes lexer tokens,
 * builds the AST, and records recoverable syntax diagnostics in the supplied
 * diagnostic bag.
 */
public class Parser {

    private static final Set<TokenType> EOF = EnumSet.of(TokenType.SPECIAL_END_OF_FILE);
    private static final Set<TokenType> FIRST_TOP_LEVEL = EnumSet.of(
            TokenType.TYPE_ENUM, TokenType.TYPE_STRUCT, TokenType.KEYWORD_FUNCTION
    );
    private static final Set<TokenType> FIRST_PRIMITIVE_TYPE = EnumSet.of(
            TokenType.TYPE_INTEGER, TokenType.TYPE_DOUBLE, TokenType.TYPE_BOOLEAN,
            TokenType.TYPE_STRING, TokenType.TYPE_CHARACTER
    );
    private static final Set<TokenType> FIRST_INTEGER_LITERAL = EnumSet.of(
            TokenType.LITERAL_INTEGER, TokenType.LITERAL_HEX, TokenType.LITERAL_BINARY, TokenType.LITERAL_OCTAL
    );
    private static final Set<TokenType> FIRST_TYPE = EnumSet.of(
            TokenType.TYPE_INTEGER, TokenType.TYPE_DOUBLE, TokenType.TYPE_BOOLEAN,
            TokenType.TYPE_STRING, TokenType.TYPE_CHARACTER, TokenType.TYPE_STRUCT, TokenType.TYPE_ENUM
    );
    private static final Set<TokenType> FIRST_RETURN_TYPE = enumSet(FIRST_TYPE, TokenType.TYPE_VOID);
    private static final Set<TokenType> FIRST_ASSIGN_OPERATOR = EnumSet.of(
            TokenType.OPERATOR_ASSIGN, TokenType.OPERATOR_PLUS_ASSIGN, TokenType.OPERATOR_MINUS_ASSIGN,
            TokenType.OPERATOR_MULTIPLY_ASSIGN, TokenType.OPERATOR_DIVIDE_ASSIGN, TokenType.OPERATOR_MODULO_ASSIGN,
            TokenType.OPERATOR_BITWISE_OR_ASSIGN, TokenType.OPERATOR_BITWISE_AND_ASSIGN,
            TokenType.OPERATOR_BITWISE_XOR_ASSIGN, TokenType.OPERATOR_BITSHIFT_LEFT_ASSIGN,
            TokenType.OPERATOR_BITSHIFT_RIGHT_ASSIGN
    );
    private static final Set<TokenType> FIRST_EXPRESSION = EnumSet.of(
            TokenType.OPERATOR_MINUS, TokenType.OPERATOR_DECREMENT, TokenType.OPERATOR_LOGICAL_NOT,
            TokenType.SYMBOL_LEFT_PARENTHESIS, TokenType.OPERATOR_PLUS, TokenType.OPERATOR_INCREMENT,
            TokenType.OPERATOR_BITWISE_NOT, TokenType.LITERAL_BOOLEAN, TokenType.KEYWORD_CALL,
            TokenType.KEYWORD_CAST,
            TokenType.LITERAL_CHAR, TokenType.LITERAL_DOUBLE, TokenType.ID_IDENTIFIER,
            TokenType.LITERAL_INTEGER, TokenType.KEYWORD_NEW, TokenType.LITERAL_NULL, TokenType.LITERAL_STRING,
            TokenType.LITERAL_HEX, TokenType.LITERAL_BINARY, TokenType.LITERAL_OCTAL
    );
    private static final Set<TokenType> FIRST_INITIALIZER = enumSet(FIRST_EXPRESSION, TokenType.SYMBOL_LEFT_BRACE);
    private static final Set<TokenType> FIRST_STATEMENT = enumSet(FIRST_EXPRESSION,
            TokenType.SYMBOL_LEFT_BRACE, TokenType.KEYWORD_IF, TokenType.KEYWORD_WHILE,
            TokenType.KEYWORD_DO, TokenType.KEYWORD_FOR, TokenType.KEYWORD_SWITCH,
            TokenType.KEYWORD_BREAK, TokenType.KEYWORD_CONTINUE, TokenType.KEYWORD_RETURN, TokenType.KEYWORD_LET
    );
    private static final Set<TokenType> FOLLOW_STATEMENT = enumSet(FIRST_STATEMENT, TokenType.SYMBOL_RIGHT_BRACE, TokenType.KEYWORD_ELSE, TokenType.KEYWORD_CASE, TokenType.KEYWORD_DEFAULT, TokenType.SPECIAL_END_OF_FILE);
    private static final Set<TokenType> FIRST_CASE = EnumSet.of(TokenType.KEYWORD_CASE, TokenType.KEYWORD_DEFAULT);
    private static final Set<TokenType> FIRST_CASE_LABEL = EnumSet.of(
            TokenType.LITERAL_BOOLEAN, TokenType.LITERAL_CHAR, TokenType.LITERAL_DOUBLE,
            TokenType.ID_IDENTIFIER, TokenType.LITERAL_INTEGER, TokenType.LITERAL_NULL, TokenType.LITERAL_STRING,
            TokenType.LITERAL_HEX, TokenType.LITERAL_BINARY, TokenType.LITERAL_OCTAL
    );
    private static final Set<TokenType> FOLLOW_TOP_LEVEL = enumSet(FIRST_TOP_LEVEL, TokenType.SPECIAL_END_OF_FILE);
    private static final Set<TokenType> FOLLOW_DECLARATION = FOLLOW_TOP_LEVEL;
    private static final Set<TokenType> FOLLOW_TYPE = EnumSet.of(TokenType.ID_IDENTIFIER);
    private static final Set<TokenType> FOLLOW_RETURN_TYPE = EnumSet.of(TokenType.ID_IDENTIFIER);
    private static final Set<TokenType> FOLLOW_PARAM = EnumSet.of(TokenType.SYMBOL_COMMA, TokenType.SYMBOL_RIGHT_PARENTHESIS);
    private static final Set<TokenType> FOLLOW_STRUCT_FIELD = enumSet(FIRST_TYPE, TokenType.SYMBOL_RIGHT_BRACE);
    private static final Set<TokenType> FOLLOW_ENUM_ITEM = EnumSet.of(TokenType.SYMBOL_COMMA, TokenType.SYMBOL_RIGHT_BRACE);
    private static final Set<TokenType> FOLLOW_EXPRESSION = EnumSet.of(TokenType.SYMBOL_COMMA, TokenType.SYMBOL_SEMICOLON, TokenType.SYMBOL_COLON, TokenType.SYMBOL_RIGHT_PARENTHESIS, TokenType.SYMBOL_RIGHT_BRACKET, TokenType.SYMBOL_RIGHT_BRACE);
    private static final Set<TokenType> FOLLOW_CASE = EnumSet.of(TokenType.KEYWORD_CASE, TokenType.KEYWORD_DEFAULT, TokenType.SYMBOL_RIGHT_BRACE);
    private static final Set<TokenType> FOLLOW_STATEMENT_END = EnumSet.of(
            TokenType.SYMBOL_SEMICOLON,
            TokenType.SYMBOL_RIGHT_BRACE,
            TokenType.KEYWORD_ELSE,
            TokenType.KEYWORD_CASE,
            TokenType.KEYWORD_DEFAULT,
            TokenType.SPECIAL_END_OF_FILE
    );
    private final List<Token> tokens;
    private final DiagnosticBag diagnosticBag;
    private int currentPosition = 0;

    /**
     * Creates a parser for a token stream.
     *
     * @param tokens        token stream ending with {@code SPECIAL_END_OF_FILE}
     * @param diagnosticBag collector used for syntax diagnostics
     */
    public Parser(List<Token> tokens, DiagnosticBag diagnosticBag) {
        this.tokens = tokens;
        this.diagnosticBag = diagnosticBag;
    }

    private static Set<TokenType> enumSet(Set<TokenType> base, TokenType... additional) {
        Set<TokenType> result = EnumSet.copyOf(base);
        result.addAll(Arrays.asList(additional));
        return result;
    }

    /**
     * Parses the complete token stream as a compilation unit.
     *
     * @return parsed compilation unit
     */
    public CompilationUnit parse() {
        return parseCompilationUnit();
    }

    private Token currentToken() {
        return peek(0);
    }

    private Token lookahead() {
        return peek(1);
    }

    private Token previousToken() {
        return peek(currentPosition > 0 ? -1 : 0);
    }

    private Token peek(int offset) {
        int index = currentPosition + offset;
        if (index < 0) index = 0;
        return index >= tokens.size() ? tokens.getLast() : tokens.get(index);
    }

    private boolean matchCurrentToken(TokenType tokenType) {
        return currentToken().getTokenType() == tokenType;
    }

    private boolean matchNextTokenWithDot() {
        return lookahead().getTokenType() == TokenType.SYMBOL_DOT;
    }

    private boolean currentIs(Set<TokenType> tokenTypes) {
        return tokenTypes.contains(currentToken().getTokenType());
    }

    private boolean isNotAtEnd() {
        return !matchCurrentToken(TokenType.SPECIAL_END_OF_FILE);
    }

    private void consumeToken() {
        if (currentPosition < tokens.size()) currentPosition++;
    }

    private <T extends AstNode> T located(T node, Token startToken) {
        node.setSourceRange(new SourceRange(startToken.getPosition(), previousToken().getPosition()));
        return node;
    }

    private <T extends AstNode> T located(T node, SourceLocation startLocation) {
        node.setSourceRange(new SourceRange(startLocation, previousToken().getPosition()));
        return node;
    }

    private void match(TokenType expected) {
        if (matchCurrentToken(expected)) {
            consumeToken();
            return;
        }
        reportExpectedToken(expected);
        if (isNotAtEnd()) consumeToken();
    }

    private Token expectTokenType(TokenType expected) {
        Token token = currentToken();
        if (token.getTokenType() == expected) {
            consumeToken();
            return token;
        }
        SourceLocation missingLocation = expectedTokenLocation();
        reportExpectedToken(expected);
        if (isNotAtEnd()) consumeToken();
        return new Token(expected, expected.diagnosticName(), missingLocation);
    }

    private Token expectTokenTypes(Set<TokenType> expected) {
        Token token = currentToken();
        if (expected.contains(token.getTokenType())) {
            consumeToken();
            return token;
        }
        reportExpectedTokens(expected);
        if (isNotAtEnd()) consumeToken();
        TokenType dummy = expected.iterator().next();
        return new Token(dummy, dummy.diagnosticName(), token.getPosition());
    }

    private boolean skipErrors(Set<TokenType> first, Set<TokenType> follow, boolean epsilonAllowed) {
        if (currentIs(first)) return true;
        if (epsilonAllowed && currentIs(follow)) return false;
        reportExpectedTokens(first);
        Set<TokenType> sync = enumSet(first, TokenType.SPECIAL_END_OF_FILE);
        sync.addAll(follow);
        while (!currentIs(sync) && isNotAtEnd()) consumeToken();
        if (epsilonAllowed && currentIs(follow)) return false;
        return currentIs(first);
    }

    private void synchronize() {
        while (!currentIs(Parser.FOLLOW_STATEMENT_END) && isNotAtEnd()) consumeToken();
    }

    private boolean matchStatementSemicolon() {
        if (matchCurrentToken(TokenType.SYMBOL_SEMICOLON)) {
            consumeToken();
            return true;
        }
        reportExpectedToken(TokenType.SYMBOL_SEMICOLON);
        synchronize();
        if (matchCurrentToken(TokenType.SYMBOL_SEMICOLON)) consumeToken();
        return false;
    }

    private StatementAstNode dummyStatement(Token startToken) {
        return located(new BlockStatement(List.of()), startToken);
    }

    private void reportExpectedToken(TokenType expected) {
        reportExpectedTokens(EnumSet.of(expected), expectedTokenLocation());
    }

    private void reportExpectedTokens(Set<TokenType> expected) {
        reportExpectedTokens(expected, diagnosticLocation(currentToken()));
    }

    private void reportExpectedTokens(Set<TokenType> expected, SourceLocation location) {
        Token current = currentToken();
        String message = expected.size() == 1
                ? String.format("Expected: %s; but found: %s.", formatExpectedTokens(expected), formatFoundToken(current))
                : String.format("Expected one of: %s; but found: %s.", formatExpectedTokens(expected), formatFoundToken(current));
        diagnosticBag.add(location, Severity.ERROR, message);
    }

    private void reportInvalidTokenMapping(Token token, String context) {
        String message = "Invalid " + context + ": " + formatFoundToken(token) + ".";
        diagnosticBag.add(diagnosticLocation(token), Severity.ERROR, message);
    }

    private void reportSyntaxError(Token token, String message) {
        diagnosticBag.add(diagnosticLocation(token), Severity.ERROR, message);
    }

    private SourceLocation expectedTokenLocation() {
        Token current = currentToken();
        if (current.getTokenType() == TokenType.SPECIAL_END_OF_FILE || currentPosition == 0) {
            return diagnosticLocation(current);
        }

        SourceLocation previous = previousToken().getPosition();
        int insertionColumn = previous.endColumn() + 1;
        return new SourceLocation(insertionColumn, insertionColumn, previous.lineNumber());
    }

    private SourceLocation diagnosticLocation(Token token) {
        if (token.getTokenType() != TokenType.SPECIAL_END_OF_FILE || currentPosition == 0) {
            return token.getPosition();
        }

        SourceLocation previous = previousToken().getPosition();
        int insertionColumn = previous.endColumn() + 1;
        return new SourceLocation(insertionColumn, insertionColumn, previous.lineNumber());
    }

    private String formatExpectedTokens(Set<TokenType> expected) {
        List<String> literalNames = new ArrayList<>();
        List<String> tokenNames = new ArrayList<>();

        for (TokenType tokenType : expected) {
            String name = tokenType.diagnosticName();
            if (name.startsWith("Literal: ")) {
                literalNames.add(quote(name.substring("Literal: ".length())));
            } else {
                tokenNames.add(quote(name));
            }
        }

        if (!literalNames.isEmpty()) {
            tokenNames.addFirst("Literal: (" + String.join(", ", literalNames) + ")");
        }

        return String.join(", ", tokenNames);
    }

    private String quote(String value) {
        return "'" + value + "'";
    }

    private String formatFoundToken(Token token) {
        String value = token.getValue();
        if (value != null && !value.isEmpty()) {
            return printable(value);
        }
        return token.getTokenType().diagnosticName();
    }

    private String printable(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\0", "\\0");
    }

    private CompilationUnit parseCompilationUnit() {
        Token startToken = currentToken();
        List<DeclarationAstNode> declarations = new ArrayList<>();
        while (isNotAtEnd()) {
            if (!skipErrors(FIRST_TOP_LEVEL, EOF, true)) break;
            declarations.add(parseDeclaration());
        }
        match(TokenType.SPECIAL_END_OF_FILE);
        return located(new CompilationUnit(declarations), startToken);
    }

    private DeclarationAstNode parseDeclaration() {
        Token startToken = currentToken();
        if (!skipErrors(FIRST_TOP_LEVEL, FOLLOW_DECLARATION, false))
            return located(new StructDeclaration("<error>", List.of()), startToken);
        return switch (currentToken().getTokenType()) {
            case TYPE_ENUM -> parseEnumDeclaration();
            case KEYWORD_FUNCTION -> parseFunctionDeclaration();
            case TYPE_STRUCT -> parseStructDeclaration();
            default -> located(new StructDeclaration("<error>", List.of()), startToken);
        };
    }

    private EnumDeclaration parseEnumDeclaration() {
        Token startToken = expectTokenType(TokenType.TYPE_ENUM);
        String name = expectTokenType(TokenType.ID_IDENTIFIER).getValue();
        match(TokenType.SYMBOL_LEFT_BRACE);
        List<EnumItem> items = parseEnumItems();
        match(TokenType.SYMBOL_RIGHT_BRACE);
        return located(new EnumDeclaration(name, items), startToken);
    }

    private List<EnumItem> parseEnumItems() {
        List<EnumItem> items = new ArrayList<>();
        Set<TokenType> first = EnumSet.of(TokenType.ID_IDENTIFIER);
        while (!matchCurrentToken(TokenType.SYMBOL_RIGHT_BRACE) && isNotAtEnd()) {
            if (!skipErrors(first, EnumSet.of(TokenType.SYMBOL_RIGHT_BRACE), true)) return items;
            items.add(parseEnumItem());
            if (!matchCurrentToken(TokenType.SYMBOL_COMMA)) return items;
            match(TokenType.SYMBOL_COMMA);
            if (matchCurrentToken(TokenType.SYMBOL_RIGHT_BRACE)) return items;
        }
        return items;
    }

    private EnumItem parseEnumItem() {
        Token name = expectTokenType(TokenType.ID_IDENTIFIER);
        EnumItem.Builder builder = EnumItem.builder(name.getValue());
        if (matchCurrentToken(TokenType.OPERATOR_ASSIGN)) {
            match(TokenType.OPERATOR_ASSIGN);
            Token value = expectTokenTypes(FIRST_INTEGER_LITERAL);
            builder.value(parseIntegerConstant(value));
        }
        skipErrors(FOLLOW_ENUM_ITEM, FOLLOW_ENUM_ITEM, true);
        return located(builder.build(), name);
    }

    private StructDeclaration parseStructDeclaration() {
        Token startToken = expectTokenType(TokenType.TYPE_STRUCT);
        String name = expectTokenType(TokenType.ID_IDENTIFIER).getValue();
        match(TokenType.SYMBOL_LEFT_BRACE);
        List<StructField> fields = parseStructFields();
        match(TokenType.SYMBOL_RIGHT_BRACE);
        return located(new StructDeclaration(name, fields), startToken);
    }

    private List<StructField> parseStructFields() {
        List<StructField> fields = new ArrayList<>();
        while (!matchCurrentToken(TokenType.SYMBOL_RIGHT_BRACE) && isNotAtEnd()) {
            if (!skipErrors(FIRST_TYPE, EnumSet.of(TokenType.SYMBOL_RIGHT_BRACE), true)) break;
            fields.add(parseStructField());
        }
        return fields;
    }

    private StructField parseStructField() {
        Token startToken = currentToken();
        TypeAstNode type = parseType();
        String name = expectTokenType(TokenType.ID_IDENTIFIER).getValue();
        matchStatementSemicolon();
        skipErrors(FOLLOW_STRUCT_FIELD, FOLLOW_STRUCT_FIELD, true);
        return located(new StructField(type, name), startToken);
    }

    private FunctionDeclaration parseFunctionDeclaration() {
        Token startToken = expectTokenType(TokenType.KEYWORD_FUNCTION);
        TypeAstNode returnType = parseReturnType();
        String functionName = expectTokenType(TokenType.ID_IDENTIFIER).getValue();
        match(TokenType.SYMBOL_LEFT_PARENTHESIS);
        List<FunctionParameter> parameters = parseFunctionParameters();
        match(TokenType.SYMBOL_RIGHT_PARENTHESIS);
        BlockStatement block = parseBlock();
        return located(new FunctionDeclaration(returnType, functionName, parameters, block), startToken);
    }

    private List<FunctionParameter> parseFunctionParameters() {
        List<FunctionParameter> parameters = new ArrayList<>();
        while (!matchCurrentToken(TokenType.SYMBOL_RIGHT_PARENTHESIS) && isNotAtEnd()) {
            if (!skipErrors(FIRST_TYPE, EnumSet.of(TokenType.SYMBOL_RIGHT_PARENTHESIS), true)) return parameters;
            parameters.add(parseFunctionParameter());
            if (!matchCurrentToken(TokenType.SYMBOL_COMMA)) return parameters;
            match(TokenType.SYMBOL_COMMA);
            if (matchCurrentToken(TokenType.SYMBOL_RIGHT_PARENTHESIS)) return parameters;
        }
        return parameters;
    }

    private FunctionParameter parseFunctionParameter() {
        Token startToken = currentToken();
        TypeAstNode type = parseType();
        boolean variadic = matchCurrentToken(TokenType.SYMBOL_ELLIPSIS);
        if (variadic) {
            match(TokenType.SYMBOL_ELLIPSIS);
        }
        String name = expectTokenType(TokenType.ID_IDENTIFIER).getValue();
        skipErrors(FOLLOW_PARAM, FOLLOW_PARAM, true);
        return located(new FunctionParameter(name, type, variadic), startToken);
    }

    private TypeAstNode parseReturnType() {
        Token startToken = currentToken();
        if (!skipErrors(FIRST_RETURN_TYPE, FOLLOW_RETURN_TYPE, false)) return located(new VoidType(), startToken);
        if (matchCurrentToken(TokenType.TYPE_VOID)) return parseVoidType();
        return parseType();
    }

    private TypeAstNode parseType() {
        Token startToken = currentToken();
        if (!skipErrors(FIRST_TYPE, FOLLOW_TYPE, false))
            return located(new PrimitiveType(PrimitiveTypeKind.INVALID), startToken);
        TypeAstNode type = parseTypeHead();
        while (matchCurrentToken(TokenType.SYMBOL_LEFT_BRACKET)) {
            match(TokenType.SYMBOL_LEFT_BRACKET);
            match(TokenType.SYMBOL_RIGHT_BRACKET);
            type = located(new ArrayType(type), startToken);
        }
        return type;
    }

    private TypeAstNode parseTypeHead() {
        if (currentIs(FIRST_PRIMITIVE_TYPE)) return parsePrimitiveType();
        return parseNamedType();
    }

    private PrimitiveType parsePrimitiveType() {
        Token token = expectTokenTypes(FIRST_PRIMITIVE_TYPE);
        return located(new PrimitiveType(toPrimitiveTypeKind(token)), token);
    }

    private NamedType parseNamedType() {
        Token typeToken = expectTokenTypes(EnumSet.of(TokenType.TYPE_STRUCT, TokenType.TYPE_ENUM));
        Token nameToken = expectTokenType(TokenType.ID_IDENTIFIER);
        return located(new NamedType(toNameTypeKind(typeToken), nameToken.getValue()), typeToken);
    }

    private VoidType parseVoidType() {
        Token startToken = expectTokenType(TokenType.TYPE_VOID);
        return located(new VoidType(), startToken);
    }

    private BlockStatement parseBlock() {
        Token startToken = currentToken();
        List<StatementAstNode> statements = new ArrayList<>();
        match(TokenType.SYMBOL_LEFT_BRACE);
        while (!matchCurrentToken(TokenType.SYMBOL_RIGHT_BRACE) && isNotAtEnd()) {
            if (!skipErrors(FIRST_STATEMENT, EnumSet.of(TokenType.SYMBOL_RIGHT_BRACE), true)) break;
            statements.add(parseStatement());
        }
        match(TokenType.SYMBOL_RIGHT_BRACE);
        return located(new BlockStatement(statements), startToken);
    }

    private StatementAstNode parseStatement() {
        Token startToken = currentToken();
        if (!skipErrors(FIRST_STATEMENT, FOLLOW_STATEMENT, false)) return dummyStatement(startToken);
        return switch (currentToken().getTokenType()) {
            case SYMBOL_LEFT_BRACE -> parseBlock();
            case KEYWORD_IF -> parseIfStatement();
            case KEYWORD_WHILE -> parseWhileStatement();
            case KEYWORD_DO -> parseDoWhileStatement();
            case KEYWORD_FOR -> parseForStatement();
            case KEYWORD_SWITCH -> parseSwitchStatement();
            case KEYWORD_RETURN, KEYWORD_BREAK, KEYWORD_CONTINUE -> parseJumpStatement();
            case KEYWORD_LET -> parseVarDeclarationStatement(true);
            default -> parseExpressionStatement();
        };
    }

    private ExpressionAstNode parseCheck() {
        match(TokenType.SYMBOL_LEFT_PARENTHESIS);
        ExpressionAstNode expression = parseExpression();
        match(TokenType.SYMBOL_RIGHT_PARENTHESIS);
        return expression;
    }

    private IfStatement parseIfStatement() {
        Token startToken = expectTokenType(TokenType.KEYWORD_IF);
        ExpressionAstNode condition = parseCheck();
        StatementAstNode thenBranch = parseStatement();
        IfStatement.Builder builder = IfStatement.builder(condition, thenBranch);
        if (matchCurrentToken(TokenType.KEYWORD_ELSE)) {
            match(TokenType.KEYWORD_ELSE);
            builder.elseBranch(parseStatement());
        }
        return located(builder.build(), startToken);
    }

    private WhileStatement parseWhileStatement() {
        Token startToken = expectTokenType(TokenType.KEYWORD_WHILE);
        ExpressionAstNode condition = parseCheck();
        StatementAstNode body = parseStatement();
        return located(new WhileStatement(condition, body), startToken);
    }

    private DoWhileStatement parseDoWhileStatement() {
        Token startToken = expectTokenType(TokenType.KEYWORD_DO);
        BlockStatement block = parseBlock();
        match(TokenType.KEYWORD_WHILE);
        match(TokenType.SYMBOL_LEFT_PARENTHESIS);
        ExpressionAstNode condition = parseExpression();
        match(TokenType.SYMBOL_RIGHT_PARENTHESIS);
        matchStatementSemicolon();
        return located(new DoWhileStatement(condition, block), startToken);
    }

    private ForStatement parseForStatement() {
        Token startToken = expectTokenType(TokenType.KEYWORD_FOR);
        match(TokenType.SYMBOL_LEFT_PARENTHESIS);
        ForInit init = null;
        if (!matchCurrentToken(TokenType.SYMBOL_SEMICOLON)) init = parseForInit();
        match(TokenType.SYMBOL_SEMICOLON);
        ExpressionAstNode condition = null;
        if (!matchCurrentToken(TokenType.SYMBOL_SEMICOLON)) condition = parseExpression();
        match(TokenType.SYMBOL_SEMICOLON);
        List<ExpressionAstNode> update = new ArrayList<>();
        if (!matchCurrentToken(TokenType.SYMBOL_RIGHT_PARENTHESIS)) update = parseExpressionList();
        match(TokenType.SYMBOL_RIGHT_PARENTHESIS);
        StatementAstNode body = parseStatement();
        return located(ForStatement.builder(body).forInit(init).condition(condition).update(update).build(), startToken);
    }

    private ForInit parseForInit() {
        Token startToken = currentToken();
        if (matchCurrentToken(TokenType.KEYWORD_LET))
            return located(new ForInitVarDeclaration(parseVarDeclarationStatement(false)), startToken);
        return located(new ForInitExpressionList(parseExpressionList()), startToken);
    }

    private SwitchStatement parseSwitchStatement() {
        Token startToken = expectTokenType(TokenType.KEYWORD_SWITCH);
        ExpressionAstNode condition = parseCheck();
        match(TokenType.SYMBOL_LEFT_BRACE);
        List<SwitchCase> cases = parseSwitchCases();
        match(TokenType.SYMBOL_RIGHT_BRACE);
        return located(new SwitchStatement(condition, cases), startToken);
    }

    private List<SwitchCase> parseSwitchCases() {
        List<SwitchCase> cases = new ArrayList<>();
        while (!matchCurrentToken(TokenType.SYMBOL_RIGHT_BRACE) && isNotAtEnd()) {
            if (!skipErrors(FIRST_CASE, EnumSet.of(TokenType.SYMBOL_RIGHT_BRACE), true)) break;
            cases.add(parseSwitchCase());
        }
        return cases;
    }

    private SwitchCase parseSwitchCase() {
        if (matchCurrentToken(TokenType.KEYWORD_DEFAULT)) {
            Token startToken = currentToken();
            match(TokenType.KEYWORD_DEFAULT);
            match(TokenType.SYMBOL_COLON);
            return located(new SwitchCase(true, new ArrayList<>(), parseStatement()), startToken);
        }
        Token startToken = expectTokenType(TokenType.KEYWORD_CASE);
        List<CaseLabelAstNode> labels = parseCaseLabels();
        match(TokenType.SYMBOL_COLON);
        StatementAstNode statement = parseStatement();
        skipErrors(FOLLOW_CASE, FOLLOW_CASE, true);
        return located(new SwitchCase(false, labels, statement), startToken);
    }

    private List<CaseLabelAstNode> parseCaseLabels() {
        List<CaseLabelAstNode> labels = new ArrayList<>();
        while (!matchCurrentToken(TokenType.SYMBOL_COLON) && isNotAtEnd()) {
            if (!skipErrors(FIRST_CASE_LABEL, EnumSet.of(TokenType.SYMBOL_COLON), true)) return labels;
            labels.add(parseCaseLabel());
            if (!matchCurrentToken(TokenType.SYMBOL_COMMA)) return labels;
            match(TokenType.SYMBOL_COMMA);
        }
        return labels;
    }

    private CaseLabelAstNode parseCaseLabel() {
        if (matchCurrentToken(TokenType.ID_IDENTIFIER) && matchNextTokenWithDot())
            return parseEnumCaseLabel();
        return parseLiteralCaseLabel();
    }

    private LiteralCaseLabel parseLiteralCaseLabel() {
        Token startToken = currentToken();
        return located(new LiteralCaseLabel(parseLiteralExpression()), startToken);
    }

    private EnumCaseLabel parseEnumCaseLabel() {
        Token startToken = currentToken();
        String enumName = expectTokenType(TokenType.ID_IDENTIFIER).getValue();
        match(TokenType.SYMBOL_DOT);
        String valueName = expectTokenType(TokenType.ID_IDENTIFIER).getValue();
        return located(new EnumCaseLabel(enumName, valueName), startToken);
    }

    private StatementAstNode parseJumpStatement() {
        if (matchCurrentToken(TokenType.KEYWORD_RETURN)) return parseReturnStatement();
        if (matchCurrentToken(TokenType.KEYWORD_BREAK)) return parseBreakStatement();
        return parseContinueStatement();
    }

    private BreakStatement parseBreakStatement() {
        Token startToken = expectTokenType(TokenType.KEYWORD_BREAK);
        matchStatementSemicolon();
        return located(new BreakStatement(), startToken);
    }

    private ContinueStatement parseContinueStatement() {
        Token startToken = expectTokenType(TokenType.KEYWORD_CONTINUE);
        matchStatementSemicolon();
        return located(new ContinueStatement(), startToken);
    }

    private ReturnStatement parseReturnStatement() {
        Token startToken = expectTokenType(TokenType.KEYWORD_RETURN);
        if (matchCurrentToken(TokenType.SYMBOL_SEMICOLON)) {
            consumeToken();
            return located(ReturnStatement.builder().build(), startToken);
        }
        ExpressionAstNode expression = parseExpression();
        matchStatementSemicolon();
        return located(ReturnStatement.builder(expression).build(), startToken);
    }

    private VarDeclarationStatement parseVarDeclarationStatement(boolean expectSemicolon) {
        Token startToken = expectTokenType(TokenType.KEYWORD_LET);
        TypeAstNode type = parseType();
        String name = expectTokenType(TokenType.ID_IDENTIFIER).getValue();
        VarDeclarationStatement.Builder builder = VarDeclarationStatement.builder(type, name);
        if (matchCurrentToken(TokenType.OPERATOR_ASSIGN)) {
            match(TokenType.OPERATOR_ASSIGN);
            builder.initializer(parseVarInitializer());
        }
        if (expectSemicolon) matchStatementSemicolon();
        return located(builder.build(), startToken);
    }

    private StatementAstNode parseExpressionStatement() {
        Token startToken = currentToken();
        ExpressionAstNode expression = parseExpression();
        if (!matchStatementSemicolon()) return dummyStatement(startToken);
        return expression;
    }

    private ExpressionAstNode parseVarInitializer() {
        if (!skipErrors(FIRST_INITIALIZER, EnumSet.of(TokenType.SYMBOL_SEMICOLON, TokenType.SYMBOL_COMMA, TokenType.SYMBOL_RIGHT_BRACE), false))
            return errorExpression();
        if (matchCurrentToken(TokenType.SYMBOL_LEFT_BRACE)) return parseArrayInitExpression();
        return parseExpression();
    }

    private ArrayInitExpression parseArrayInitExpression() {
        Token startToken = expectTokenType(TokenType.SYMBOL_LEFT_BRACE);
        List<ExpressionAstNode> elements = new ArrayList<>();
        while (!matchCurrentToken(TokenType.SYMBOL_RIGHT_BRACE) && isNotAtEnd()) {
            if (!skipErrors(FIRST_INITIALIZER, EnumSet.of(TokenType.SYMBOL_RIGHT_BRACE), true)) {
                match(TokenType.SYMBOL_RIGHT_BRACE);
                return located(new ArrayInitExpression(elements), startToken);
            }

            elements.add(parseVarInitializer());
            if (!matchCurrentToken(TokenType.SYMBOL_COMMA)) {
                match(TokenType.SYMBOL_RIGHT_BRACE);
                return located(new ArrayInitExpression(elements), startToken);
            }
            match(TokenType.SYMBOL_COMMA);
        }
        match(TokenType.SYMBOL_RIGHT_BRACE);
        return located(new ArrayInitExpression(elements), startToken);
    }

    private List<ExpressionAstNode> parseExpressionList() {
        List<ExpressionAstNode> expressions = new ArrayList<>();
        expressions.add(parseExpression());
        while (matchCurrentToken(TokenType.SYMBOL_COMMA)) {
            match(TokenType.SYMBOL_COMMA);
            expressions.add(parseExpression());
        }
        return expressions;
    }

    private List<ExpressionAstNode> parseOptionalExpressionList() {
        if (!currentIs(FIRST_EXPRESSION)) return new ArrayList<>();
        return parseExpressionList();
    }

    private ExpressionAstNode parseExpression() {
        if (!skipErrors(FIRST_EXPRESSION, FOLLOW_EXPRESSION, false)) return errorExpression();
        return parseAssignment();
    }

    private ExpressionAstNode parseAssignment() {
        ExpressionAstNode left = parseConditional();
        if (currentIs(FIRST_ASSIGN_OPERATOR)) {
            AssignOperator operator = parseAssignOperator();
            ExpressionAstNode value = parseAssignment();
            return located(AssignExpression.builder(left).operator(operator).value(value).build(), left.getSourceRange().start());
        }
        return left;
    }

    private AssignOperator parseAssignOperator() {
        Token token = expectTokenTypes(FIRST_ASSIGN_OPERATOR);
        return toAssignOperator(token);
    }

    private ExpressionAstNode parseConditional() {
        ExpressionAstNode condition = parseLogicalOr();
        if (matchCurrentToken(TokenType.SYMBOL_QUESTION_MARK)) {
            match(TokenType.SYMBOL_QUESTION_MARK);
            ExpressionAstNode whenTrue = parseExpression();
            match(TokenType.SYMBOL_COLON);
            ExpressionAstNode whenFalse = parseConditional();
            return located(ConditionalExpression.builder(condition).whenTrue(whenTrue).whenFalse(whenFalse).build(), condition.getSourceRange().start());
        }
        return condition;
    }

    private ExpressionAstNode parseLogicalOr() {
        return parseBinaryLeftAssociative(this::parseLogicalAnd, EnumSet.of(TokenType.OPERATOR_OR));
    }

    private ExpressionAstNode parseLogicalAnd() {
        return parseBinaryLeftAssociative(this::parseInclusiveOr, EnumSet.of(TokenType.OPERATOR_AND));
    }

    private ExpressionAstNode parseInclusiveOr() {
        return parseBinaryLeftAssociative(this::parseExclusiveOr, EnumSet.of(TokenType.OPERATOR_BITWISE_OR));
    }

    private ExpressionAstNode parseExclusiveOr() {
        return parseBinaryLeftAssociative(this::parseAndExpression, EnumSet.of(TokenType.OPERATOR_BITWISE_XOR));
    }

    private ExpressionAstNode parseAndExpression() {
        return parseBinaryLeftAssociative(this::parseEqualityExpression, EnumSet.of(TokenType.OPERATOR_BITWISE_AND));
    }

    private ExpressionAstNode parseEqualityExpression() {
        return parseBinaryLeftAssociative(this::parseRelationalExpression, EnumSet.of(TokenType.OPERATOR_EQUALS, TokenType.OPERATOR_NOT_EQUALS));
    }

    private ExpressionAstNode parseRelationalExpression() {
        return parseBinaryLeftAssociative(this::parseShiftExpression, EnumSet.of(TokenType.OPERATOR_LESS_THAN, TokenType.OPERATOR_LESS_EQUAL, TokenType.OPERATOR_GREATER_THAN, TokenType.OPERATOR_GREATER_EQUAL));
    }

    private ExpressionAstNode parseShiftExpression() {
        return parseBinaryLeftAssociative(this::parseAdditiveExpression, EnumSet.of(TokenType.OPERATOR_BITSHIFT_LEFT, TokenType.OPERATOR_BITSHIFT_RIGHT));
    }

    private ExpressionAstNode parseAdditiveExpression() {
        return parseBinaryLeftAssociative(this::parseMultiplicativeExpression, EnumSet.of(TokenType.OPERATOR_PLUS, TokenType.OPERATOR_MINUS));
    }

    private ExpressionAstNode parseMultiplicativeExpression() {
        return parseBinaryLeftAssociative(this::parseUnaryExpression, EnumSet.of(TokenType.OPERATOR_MULTIPLY, TokenType.OPERATOR_DIVIDE, TokenType.OPERATOR_MODULO));
    }

    private ExpressionAstNode parseBinaryLeftAssociative(ExpressionParser operandParser, Set<TokenType> operators) {
        ExpressionAstNode left = operandParser.parse();
        while (currentIs(operators)) {
            Token operator = currentToken();
            consumeToken();
            ExpressionAstNode right = operandParser.parse();
            left = located(new BinaryExpression(toBinaryExpressionKind(operator), left, right), left.getSourceRange().start());
        }
        return left;
    }

    private ExpressionAstNode parseUnaryExpression() {
        if (matchCurrentToken(TokenType.KEYWORD_CAST)) {
            return parseCastExpression();
        }

        Set<TokenType> preOps = EnumSet.of(
                TokenType.OPERATOR_LOGICAL_NOT, TokenType.OPERATOR_BITWISE_NOT,
                TokenType.OPERATOR_PLUS, TokenType.OPERATOR_MINUS,
                TokenType.OPERATOR_DECREMENT, TokenType.OPERATOR_INCREMENT
        );
        if (currentIs(preOps)) {
            Token operator = currentToken();
            consumeToken();
            return located(new UnaryExpression(toUnaryExpressionKind(operator), parseUnaryExpression()), operator);
        }
        return parsePostfixExpression();
    }

    private ExpressionAstNode parseCastExpression() {
        Token startToken = expectTokenType(TokenType.KEYWORD_CAST);
        match(TokenType.SYMBOL_LEFT_PARENTHESIS);
        TypeAstNode targetType = parseTypeHead();
        match(TokenType.SYMBOL_RIGHT_PARENTHESIS);
        ExpressionAstNode operand = parseUnaryExpression();
        return located(new CastExpression(targetType, operand), startToken);
    }

    private ExpressionAstNode parsePostfixExpression() {
        ExpressionAstNode expression = parsePrimaryExpression();
        while (true) {
            if (matchCurrentToken(TokenType.SYMBOL_LEFT_BRACKET)) expression = parseIndexExpression(expression);
            else if (matchCurrentToken(TokenType.SYMBOL_DOT)) expression = parseMemberAccessExpression(expression);
            else break;
        }
        if (matchCurrentToken(TokenType.OPERATOR_INCREMENT) || matchCurrentToken(TokenType.OPERATOR_DECREMENT)) {
            Token operator = currentToken();
            consumeToken();
            expression = located(new PostfixExpression(expression, toPostfixOperationKind(operator)), expression.getSourceRange().start());
        }
        return expression;
    }

    private ExpressionAstNode parsePrimaryExpression() {
        if (!skipErrors(EnumSet.of(TokenType.SYMBOL_LEFT_PARENTHESIS, TokenType.KEYWORD_CALL, TokenType.LITERAL_BOOLEAN, TokenType.LITERAL_CHAR, TokenType.LITERAL_DOUBLE, TokenType.ID_IDENTIFIER, TokenType.LITERAL_INTEGER, TokenType.KEYWORD_NEW, TokenType.LITERAL_NULL, TokenType.LITERAL_STRING, TokenType.LITERAL_HEX, TokenType.LITERAL_BINARY, TokenType.LITERAL_OCTAL), FOLLOW_EXPRESSION, false))
            return errorExpression();
        return switch (currentToken().getTokenType()) {
            case SYMBOL_LEFT_PARENTHESIS -> {
                match(TokenType.SYMBOL_LEFT_PARENTHESIS);
                ExpressionAstNode expression = parseExpression();
                match(TokenType.SYMBOL_RIGHT_PARENTHESIS);
                yield expression;
            }
            case KEYWORD_CALL -> parseCallExpression();
            case ID_IDENTIFIER -> parseNameExpression();
            case KEYWORD_NEW -> parseNewExpression();
            default -> parseLiteralExpression();
        };
    }

    private ExpressionAstNode parseCallExpression() {
        Token startToken = expectTokenType(TokenType.KEYWORD_CALL);
        String name = expectTokenType(TokenType.ID_IDENTIFIER).getValue();
        match(TokenType.SYMBOL_LEFT_PARENTHESIS);
        List<ExpressionAstNode> arguments = parseOptionalExpressionList();
        match(TokenType.SYMBOL_RIGHT_PARENTHESIS);
        return located(new CallExpression(name, arguments), startToken);
    }

    private ExpressionAstNode parseNameExpression() {
        Token startToken = expectTokenType(TokenType.ID_IDENTIFIER);
        return located(new NameExpression(startToken.getValue()), startToken);
    }

    private IndexExpression parseIndexExpression(ExpressionAstNode target) {
        SourceLocation startLocation = target.getSourceRange().start();
        match(TokenType.SYMBOL_LEFT_BRACKET);
        ExpressionAstNode index = parseExpression();
        match(TokenType.SYMBOL_RIGHT_BRACKET);
        return located(new IndexExpression(target, index), startLocation);
    }

    private MemberAccessExpression parseMemberAccessExpression(ExpressionAstNode target) {
        SourceLocation startLocation = target.getSourceRange().start();
        match(TokenType.SYMBOL_DOT);
        String memberName = expectTokenType(TokenType.ID_IDENTIFIER).getValue();
        return located(new MemberAccessExpression(target, memberName), startLocation);
    }

    private NewExpression parseNewExpression() {
        Token startToken = expectTokenType(TokenType.KEYWORD_NEW);
        TypeAstNode type = parseTypeHead();
        List<ExpressionAstNode> dimensions = parseNewArrayDimensions();
        NewExpression.Builder builder = NewExpression.builder(type).dimensions(dimensions);
        if (matchCurrentToken(TokenType.SYMBOL_LEFT_BRACE)) builder.arrayInit(parseArrayInitExpression());
        NewExpression expression = builder.build();
        validateNewExpression(expression, startToken);
        return located(expression, startToken);
    }

    private List<ExpressionAstNode> parseNewArrayDimensions() {
        List<ExpressionAstNode> dimensions = new ArrayList<>();
        while (matchCurrentToken(TokenType.SYMBOL_LEFT_BRACKET)) {
            match(TokenType.SYMBOL_LEFT_BRACKET);
            if (matchCurrentToken(TokenType.SYMBOL_RIGHT_BRACKET)) {
                reportSyntaxError(currentToken(), "Array dimensions in a new expression must contain an expression.");
                dimensions.add(errorExpression());
                match(TokenType.SYMBOL_RIGHT_BRACKET);
                continue;
            }
            dimensions.add(parseExpression());
            match(TokenType.SYMBOL_RIGHT_BRACKET);
        }
        return dimensions;
    }

    private void validateNewExpression(NewExpression expression, Token startToken) {
        TypeAstNode type = expression.getType();
        boolean hasDimensions = !expression.getDimensions().isEmpty();
        boolean hasInitializer = expression.getArrayInit() != null;

        if (hasInitializer && !hasDimensions) {
            if (isStructType(type)) {
                reportSyntaxError(startToken, "Struct initializers are not allowed; initializers only apply to arrays.");
            } else {
                reportSyntaxError(startToken, "Array initializer in a new expression requires explicit dimensions.");
            }
            return;
        }

        if (hasDimensions) {
            return;
        }

        if (isPrimitiveType(type)) {
            PrimitiveType primitiveType = (PrimitiveType) type;
            if (primitiveType.getKind() == PrimitiveTypeKind.STRING) {
                reportSyntaxError(startToken, "'new string' is not allowed.");
            } else {
                reportSyntaxError(startToken, "'new' for primitive values requires array dimensions.");
            }
            return;
        }

        if (isEnumType(type)) {
            reportSyntaxError(startToken, "'new' can only allocate structs or arrays, not enum values.");
        }
    }

    private boolean isPrimitiveType(TypeAstNode type) {
        return type instanceof PrimitiveType;
    }

    private boolean isStructType(TypeAstNode type) {
        return type instanceof NamedType namedType && namedType.getKind() == NameTypeKind.STRUCT;
    }

    private boolean isEnumType(TypeAstNode type) {
        return type instanceof NamedType namedType && namedType.getKind() == NameTypeKind.ENUM;
    }

    private LiteralExpression<?> parseLiteralExpression() {
        return switch (currentToken().getTokenType()) {
            case LITERAL_INTEGER, LITERAL_DOUBLE, LITERAL_HEX, LITERAL_BINARY, LITERAL_OCTAL -> parseNumberLiteral();
            case LITERAL_BOOLEAN -> parseBooleanLiteral();
            case LITERAL_STRING -> parseStringLiteral();
            case LITERAL_CHAR -> parseCharLiteral();
            case LITERAL_NULL -> parseNullLiteral();
            default -> {
                reportExpectedTokens(FIRST_CASE_LABEL);
                yield errorExpression();
            }
        };
    }

    private LiteralExpression<?> parseNumberLiteral() {
        if (matchCurrentToken(TokenType.LITERAL_DOUBLE)) {
            Token token = expectTokenType(TokenType.LITERAL_DOUBLE);
            return located(new LiteralExpression<>(LiteralKind.DOUBLE, parseDouble(token)), token);
        }
        if (matchCurrentToken(TokenType.LITERAL_HEX)) {
            Token token = expectTokenType(TokenType.LITERAL_HEX);
            return located(new LiteralExpression<>(LiteralKind.INT, parseIntegerConstant(token)), token);
        }
        if (matchCurrentToken(TokenType.LITERAL_BINARY)) {
            Token token = expectTokenType(TokenType.LITERAL_BINARY);
            return located(new LiteralExpression<>(LiteralKind.INT, parseIntegerConstant(token)), token);
        }
        if (matchCurrentToken(TokenType.LITERAL_OCTAL)) {
            Token token = expectTokenType(TokenType.LITERAL_OCTAL);
            return located(new LiteralExpression<>(LiteralKind.INT, parseIntegerConstant(token)), token);
        }
        Token token = expectTokenType(TokenType.LITERAL_INTEGER);
        return located(new LiteralExpression<>(LiteralKind.INT, parseIntegerConstant(token)), token);
    }

    private LiteralExpression<Boolean> parseBooleanLiteral() {
        Token token = expectTokenType(TokenType.LITERAL_BOOLEAN);
        return located(new LiteralExpression<>(LiteralKind.BOOLEAN, Boolean.parseBoolean(token.getValue())), token);
    }

    private LiteralExpression<String> parseStringLiteral() {
        Token token = expectTokenType(TokenType.LITERAL_STRING);
        return located(new LiteralExpression<>(LiteralKind.STRING, token.getValue()), token);
    }

    private LiteralExpression<Character> parseCharLiteral() {
        Token token = expectTokenType(TokenType.LITERAL_CHAR);
        String value = token.getValue();
        return located(new LiteralExpression<>(LiteralKind.CHAR, value.isEmpty() ? '\0' : value.charAt(0)), token);
    }

    private LiteralExpression<Void> parseNullLiteral() {
        Token token = expectTokenType(TokenType.LITERAL_NULL);
        return located(new LiteralExpression<>(LiteralKind.NULL, null), token);
    }

    private LiteralExpression<Integer> errorExpression() {
        return located(new LiteralExpression<>(LiteralKind.INT, 0), currentToken());
    }

    private int parseIntegerConstant(Token token) {
        int radix = switch (token.getTokenType()) {
            case LITERAL_HEX -> 16;
            case LITERAL_BINARY -> 2;
            case LITERAL_OCTAL -> 8;
            default -> 10;
        };

        try {
            if (radix == 10) {
                return Integer.parseInt(token.getValue());
            }
            return Integer.parseUnsignedInt(token.getValue(), radix);
        } catch (NumberFormatException ignored) {
            reportSyntaxError(token, "Invalid integer literal: " + formatFoundToken(token) + ".");
            return 0;
        }
    }

    private double parseDouble(Token token) {
        try {
            return Double.parseDouble(token.getValue());
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    private BinaryExpressionKind toBinaryExpressionKind(Token token) {
        return switch (token.getTokenType()) {
            case OPERATOR_PLUS -> BinaryExpressionKind.ADD;
            case OPERATOR_MINUS -> BinaryExpressionKind.SUBTRACT;
            case OPERATOR_MULTIPLY -> BinaryExpressionKind.MULTIPLY;
            case OPERATOR_DIVIDE -> BinaryExpressionKind.DIVIDE;
            case OPERATOR_MODULO -> BinaryExpressionKind.MODULO;
            case OPERATOR_AND -> BinaryExpressionKind.AND;
            case OPERATOR_OR -> BinaryExpressionKind.OR;
            case OPERATOR_BITWISE_OR -> BinaryExpressionKind.BITWISE_OR;
            case OPERATOR_BITWISE_AND -> BinaryExpressionKind.BITWISE_AND;
            case OPERATOR_BITWISE_XOR -> BinaryExpressionKind.BITWISE_XOR;
            case OPERATOR_EQUALS -> BinaryExpressionKind.EQUALS;
            case OPERATOR_NOT_EQUALS -> BinaryExpressionKind.NOT_EQUALS;
            case OPERATOR_LESS_THAN -> BinaryExpressionKind.LESS;
            case OPERATOR_LESS_EQUAL -> BinaryExpressionKind.LESS_EQUALS;
            case OPERATOR_GREATER_THAN -> BinaryExpressionKind.GREATER;
            case OPERATOR_GREATER_EQUAL -> BinaryExpressionKind.GREATER_EQUALS;
            case OPERATOR_BITSHIFT_LEFT -> BinaryExpressionKind.SHIFT_LEFT;
            case OPERATOR_BITSHIFT_RIGHT -> BinaryExpressionKind.SHIFT_RIGHT;
            default -> {
                reportInvalidTokenMapping(token, "binary operator");
                yield BinaryExpressionKind.INVALID;
            }
        };
    }

    private UnaryExpressionKind toUnaryExpressionKind(Token token) {
        return switch (token.getTokenType()) {
            case OPERATOR_PLUS -> UnaryExpressionKind.PLUS;
            case OPERATOR_MINUS -> UnaryExpressionKind.MINUS;
            case OPERATOR_LOGICAL_NOT -> UnaryExpressionKind.LOGICAL_NOT;
            case OPERATOR_BITWISE_NOT -> UnaryExpressionKind.BITWISE_NOT;
            case OPERATOR_INCREMENT -> UnaryExpressionKind.PRE_INCREMENT;
            case OPERATOR_DECREMENT -> UnaryExpressionKind.PRE_DECREMENT;
            default -> {
                reportInvalidTokenMapping(token, "unary operator");
                yield UnaryExpressionKind.INVALID;
            }
        };
    }

    private PostfixOperationKind toPostfixOperationKind(Token token) {
        return switch (token.getTokenType()) {
            case OPERATOR_INCREMENT -> PostfixOperationKind.INCREMENT;
            case OPERATOR_DECREMENT -> PostfixOperationKind.DECREMENT;
            default -> {
                reportInvalidTokenMapping(token, "postfix operator");
                yield PostfixOperationKind.INVALID;
            }
        };
    }

    private AssignOperator toAssignOperator(Token token) {
        return switch (token.getTokenType()) {
            case OPERATOR_ASSIGN -> AssignOperator.ASSIGN;
            case OPERATOR_PLUS_ASSIGN -> AssignOperator.ADD_ASSIGN;
            case OPERATOR_MINUS_ASSIGN -> AssignOperator.SUB_ASSIGN;
            case OPERATOR_MULTIPLY_ASSIGN -> AssignOperator.MUL_ASSIGN;
            case OPERATOR_DIVIDE_ASSIGN -> AssignOperator.DIV_ASSIGN;
            case OPERATOR_MODULO_ASSIGN -> AssignOperator.MOD_ASSIGN;
            case OPERATOR_BITWISE_OR_ASSIGN -> AssignOperator.BITWISE_OR_ASSIGN;
            case OPERATOR_BITWISE_AND_ASSIGN -> AssignOperator.BITWISE_AND_ASSIGN;
            case OPERATOR_BITWISE_XOR_ASSIGN -> AssignOperator.BITWISE_XOR_ASSIGN;
            case OPERATOR_BITSHIFT_LEFT_ASSIGN -> AssignOperator.LEFT_SHIFT_ASSIGN;
            case OPERATOR_BITSHIFT_RIGHT_ASSIGN -> AssignOperator.RIGHT_SHIFT_ASSIGN;
            default -> {
                reportInvalidTokenMapping(token, "assignment operator");
                yield AssignOperator.INVALID;
            }
        };
    }

    private PrimitiveTypeKind toPrimitiveTypeKind(Token token) {
        return switch (token.getTokenType()) {
            case TYPE_INTEGER -> PrimitiveTypeKind.INT;
            case TYPE_DOUBLE -> PrimitiveTypeKind.DOUBLE;
            case TYPE_BOOLEAN -> PrimitiveTypeKind.BOOL;
            case TYPE_STRING -> PrimitiveTypeKind.STRING;
            case TYPE_CHARACTER -> PrimitiveTypeKind.CHAR;
            default -> {
                reportInvalidTokenMapping(token, "primitive type");
                yield PrimitiveTypeKind.INVALID;
            }
        };
    }

    private NameTypeKind toNameTypeKind(Token token) {
        return switch (token.getTokenType()) {
            case TYPE_STRUCT -> NameTypeKind.STRUCT;
            case TYPE_ENUM -> NameTypeKind.ENUM;
            default -> {
                reportInvalidTokenMapping(token, "named type");
                yield NameTypeKind.INVALID;
            }
        };
    }

    @FunctionalInterface
    private interface ExpressionParser {
        ExpressionAstNode parse();
    }
}
