package ch.zhaw.it.pm4.javer.compiler.visitor;

import ch.zhaw.it.pm4.javer.compiler.annotation.JacocoGenerated;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.CompilationUnit;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.caseLabel.CaseLabelAstNode;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.caseLabel.EnumCaseLabel;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.caseLabel.LiteralCaseLabel;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.declaration.*;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.*;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.type.PrimitiveTypeKind;
import ch.zhaw.it.pm4.javer.compiler.ast.scope.DataSection;
import ch.zhaw.it.pm4.javer.compiler.ast.scope.GlobalScope;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.*;
import ch.zhaw.it.pm4.javer.compiler.ast.typeinfo.ArrayTypeInfo;
import ch.zhaw.it.pm4.javer.compiler.ast.typeinfo.EnumTypeInfo;
import ch.zhaw.it.pm4.javer.compiler.ast.typeinfo.PrimitiveTypeInfo;
import ch.zhaw.it.pm4.javer.compiler.ast.typeinfo.StructTypeInfo;
import ch.zhaw.it.pm4.javer.compiler.ast.typeinfo.TypeInfo;
import ch.zhaw.it.pm4.javer.compiler.ast.typeinfo.TypeRules;
import ch.zhaw.it.pm4.javer.compiler.ast.typeinfo.VoidTypeInfo;
import ch.zhaw.it.pm4.javer.compiler.builtin.BuiltInFunction;
import ch.zhaw.it.pm4.javer.compiler.builtin.BuiltInTypes;
import ch.zhaw.it.pm4.javer.compiler.bytecode.VmLayout;
import ch.zhaw.it.pm4.javer.compiler.misc.diagnostics.DiagnosticBag;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Emits VM bytecode from a semantically checked AST.
 */
@JacocoGenerated("jacoco-ignore")
public class CodeGenerator extends AstNodeVisitorBase {

    private final DiagnosticBag diagnostics;
    private final Path outputFile;
    private final StringBuilder output = new StringBuilder();
    private DataSection dataSection;
    private FunctionEntry currentFunction;
    private final Deque<LoopContext> loopContexts = new ArrayDeque<>();
    private int nextLabelId;
    private boolean outputDirectoryReady = true;
    private boolean failed;
    private GlobalScope globalScope;

    /**
     * Creates a code generator that reports invariant failures as diagnostics.
     */
    public CodeGenerator(DiagnosticBag diagnostics, String outputFilePath) {
        this.diagnostics = diagnostics;
        this.outputFile = Path.of(outputFilePath);
        prepareOutputDirectory();
    }

    /**
     * Generates bytecode for a complete compilation unit and writes it to disk.
     *
     * @param node root compilation unit
     */
    public boolean generate(CompilationUnit node) {
        output.setLength(0);
        loopContexts.clear();
        nextLabelId = 0;

        if (!outputDirectoryReady) {
            deleteOutputFile();
            return false;
        }

        try {
            node.accept(this);

            if (failed || diagnostics.hasErrors()) {
                deleteOutputFile();
                return false;
            }

            Files.writeString(
                    outputFile,
                    output.toString(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            return true;
        } catch (IOException exception) {
            report("Could not write bytecode output file: " + outputFile + ".");
            deleteOutputFile();
            return false;
        } finally {
            dataSection = null;
            globalScope = null;
            currentFunction = null;
        }
    }

    protected void writeLine(String line) {
        output.append(line).append(System.lineSeparator());
    }

    private void writeLabel(String label) {
        writeLine(label + ":");
    }

    private String nextLabel(String prefix) {
        return prefix + "_" + nextLabelId++;
    }

    private void prepareOutputDirectory() {
        Path parent = outputFile.toAbsolutePath().getParent();
        if (parent == null) {
            return;
        }

        try {
            Files.createDirectories(parent);
        } catch (IOException exception) {
            outputDirectoryReady = false;
            report("Could not create bytecode output directory: " + parent + ".");
        }
    }

    private void deleteOutputFile() {
        try {
            Files.deleteIfExists(outputFile);
        } catch (IOException exception) {
            report("Could not delete incomplete bytecode output file: " + outputFile + ".");
        }
    }

    private void report(String message) {
        failed = true;
        System.err.println(message);
    }

    @Override
    public void visit(CompilationUnit node) {
        dataSection = node.getDataSection();
        globalScope = node.getGlobalScope();
        writeLine(".code");
        for (DeclarationAstNode declaration : node.getDeclarations()) {
            declaration.accept(this);
        }
        writeLine("");
        writeLine(".data");
        dataSection.getEntries().values().forEach(entry -> writeLine(entry.toString()));
        dataSection = null;
        globalScope = null;
    }

    @Override
    public void visit(EnumDeclaration node) {
        dataSection.addEnumValues(node.getSymbolEntry());
    }

    @Override
    public void visit(FunctionDeclaration node) {
        FunctionEntry function = node.getSymbolEntry();
        currentFunction = function;
        writeLine("_" + node.getName() + ":");
        writeLine("ENTER, " + function.getFrameSizeBytes());
        node.getBody().accept(this);
        if (!endsWithReturn(node.getBody())) {
            emitFallthroughReturn(function);
        }
        currentFunction = null;
    }

    private boolean endsWithReturn(BlockStatement body) {
        List<StatementAstNode> statements = body.getStatements();
        if (statements.isEmpty()) {
            return false;
        }
        return statements.getLast() instanceof ReturnStatement;
    }

    private void emitFallthroughReturn(FunctionEntry function) {
        TypeInfo returnType = function.getReturnType();
        if (isVoidLike(returnType)) {
            emitReturn(VoidTypeInfo.INSTANCE);
        }
    }

    @Override
    public void visit(FunctionParameter node) {
        super.visit(node);
    }

    @Override
    public void visit(StructDeclaration node) {
        super.visit(node);
    }

    @Override
    public void visit(StructField node) {
        super.visit(node);
    }

    @Override
    public void visit(BlockStatement node) {
        for (StatementAstNode statement : node.getStatements()) {
            emitStatement(statement);
        }
    }

    private void emitStatement(StatementAstNode statement) {
        if (statement instanceof ExpressionAstNode expression) {
            expression.accept(this);
            discardExpressionResult(expression);
            return;
        }
        statement.accept(this);
    }

    private void discardExpressionResult(ExpressionAstNode expression) {
        TypeInfo type = expression.getResultingType();
        if (!leavesValueOnStack(expression, type)) {
            return;
        }
        emitPop(type);
    }

    private boolean leavesValueOnStack(ExpressionAstNode expression, TypeInfo type) {
        if (isVoidLike(type)) {
            return false;
        }
        return !(expression instanceof CallExpression call) || !isVoidReturningCall(call);
    }

    private boolean isVoidReturningCall(CallExpression call) {
        FunctionEntry function = call.getResolvedFunction();
        return isVoidLike(function.getReturnType());
    }

    @Override
    public void visit(IfStatement node) {
        String elseLabel = nextLabel("if_else");
        String endLabel = nextLabel("if_end");
        emitAsBoolean(node.getCondition());
        writeLine("JUMPF, " + elseLabel);
        node.getThenBranch().accept(this);
        boolean elseBranchPossible = node.getElseBranch() != null;
        if (elseBranchPossible) {
            writeLine("JUMP, " + endLabel);
        }
        writeLabel(elseLabel);
        if (elseBranchPossible) {
            node.getElseBranch().accept(this);
            writeLabel(endLabel);
        }
    }

    @Override
    public void visit(WhileStatement node) {
        String conditionLabel = nextLabel("while_condition");
        String endLabel = nextLabel("while_end");
        writeLabel(conditionLabel);
        emitAsBoolean(node.getCondition());
        writeLine("JUMPF, " + endLabel);
        loopContexts.push(new LoopContext(endLabel, conditionLabel));
        node.getBody().accept(this);
        loopContexts.pop();
        writeLine("JUMP, " + conditionLabel);
        writeLabel(endLabel);
    }

    @Override
    public void visit(DoWhileStatement node) {
        String bodyLabel = nextLabel("do_body");
        String conditionLabel = nextLabel("do_condition");
        String endLabel = nextLabel("do_end");
        writeLabel(bodyLabel);
        loopContexts.push(new LoopContext(endLabel, conditionLabel));
        node.getBody().accept(this);
        loopContexts.pop();
        writeLabel(conditionLabel);
        emitAsBoolean(node.getCondition());
        writeLine("JUMPT, " + bodyLabel);
        writeLabel(endLabel);
    }

    @Override
    public void visit(ForStatement node) {
        String conditionLabel = nextLabel("for_condition");
        String updateLabel = nextLabel("for_update");
        String endLabel = nextLabel("for_end");
        if (node.getForInit() != null) {
            node.getForInit().accept(this);
        }
        writeLabel(conditionLabel);
        if (node.getCondition() != null) {
            emitAsBoolean(node.getCondition());
        } else {
            writeLine("PUSHI, 1");
        }
        writeLine("JUMPF, " + endLabel);
        loopContexts.push(new LoopContext(endLabel, updateLabel));
        node.getBody().accept(this);
        loopContexts.pop();
        writeLabel(updateLabel);
        if (node.getUpdate() != null) {
            for (ExpressionAstNode update : node.getUpdate()) {
                emitStatement(update);
            }
        }
        writeLine("JUMP, " + conditionLabel);
        writeLabel(endLabel);
    }

    @Override
    public void visit(SwitchStatement node) {
        String endLabel = nextLabel("switch_end");
        boolean hasDefault = node.getCases().stream().anyMatch(SwitchCase::isDefault);
        String defaultLabel = hasDefault ? nextLabel("switch_default") : nextLabel("switch_no_match");
        TypeInfo switchType = node.getCondition().getResultingType();

        node.getCondition().accept(this);

        List<SwitchTarget> targets = new ArrayList<>();
        for (SwitchCase switchCase : node.getCases()) {
            String caseLabel = switchCase.isDefault() ? defaultLabel : nextLabel("switch_case");
            targets.add(new SwitchTarget(switchCase, caseLabel));
            if (!switchCase.isDefault()) {
                for (CaseLabelAstNode label : switchCase.getCaseLabels()) {
                    emitSwitchComparison(switchType, label);
                    writeLine("JUMPT, " + caseLabel);
                }
            }
        }

        writeLine("JUMP, " + defaultLabel);
        for (SwitchTarget target : targets) {
            writeLabel(target.label());
            emitPop(switchType);
            target.switchCase().getStatement().accept(this);
            writeLine("JUMP, " + endLabel);
        }
        if (!hasDefault) {
            writeLabel(defaultLabel);
            emitPop(switchType);
            writeLine("JUMP, " + endLabel);
        }
        writeLabel(endLabel);
    }

    @Override
    public void visit(SwitchCase node) {
        super.visit(node);
    }

    private void emitSwitchComparison(TypeInfo switchType, CaseLabelAstNode label) {
        emitDup(switchType);
        emitCaseLabelValue(label);
        emitEqualityCompare(switchType, caseLabelType(label));
    }

    private void emitCaseLabelValue(CaseLabelAstNode label) {
        if (label instanceof LiteralCaseLabel literalCaseLabel) {
            emitLiteralPush(literalCaseLabel.getLiteral());
            return;
        }
        emitEnumValue(((EnumCaseLabel) label).getResolvedEnumValue());
    }

    private TypeInfo caseLabelType(CaseLabelAstNode label) {
        if (label instanceof LiteralCaseLabel literalCaseLabel) {
            return literalCaseLabel.getLiteral().getResultingType();
        }
        return new EnumTypeInfo(((EnumCaseLabel) label).getResolvedEnumValue().getOwnerEnum());
    }

    @Override
    public void visit(BreakStatement node) {
        writeLine("JUMP, " + loopContexts.peek().breakLabel());
    }

    @Override
    public void visit(ContinueStatement node) {
        writeLine("JUMP, " + loopContexts.peek().continueLabel());
    }

    @Override
    public void visit(ReturnStatement node) {
        ExpressionAstNode expression = node.getExpression();
        TypeInfo returnType = currentFunction.getReturnType();
        if (expression == null) {
            emitReturn(VoidTypeInfo.INSTANCE);
            return;
        }
        emitTyped(expression, returnType);
        emitReturn(returnType);
    }

    @Override
    public void visit(VarDeclarationStatement node) {
        StorageEntry storage = node.getSymbolEntry();
        if (node.getInitializer() == null) {
            emitDefaultInitialization(storage);
            return;
        }
        emitStorageAddress(storage);
        emitTyped(node.getInitializer(), storage.getType());
        emitStore(storage.getType());
    }

    private void emitDefaultInitialization(StorageEntry storage) {
        Object defaultValue = storage instanceof VariableEntry variable
                ? variable.getDefaultValue()
                : TypeRules.defaultValue(storage.getType());
        if (isZeroDefault(defaultValue)) {
            return;
        }
        emitStorageAddress(storage);
        emitDefaultValue(storage.getType(), defaultValue);
        emitStore(storage.getType());
    }

    private boolean isZeroDefault(Object value) {
        return value == null
                || Boolean.FALSE.equals(value)
                || Character.valueOf('\0').equals(value)
                || Integer.valueOf(0).equals(value)
                || Double.valueOf(0.0).equals(value);
    }

    @Override
    public void visit(AssignExpression node) {
        TypeInfo type = node.getTarget().getResultingType();
        AssignOperator operator = node.getOperator();
        if (operator == AssignOperator.ASSIGN) {
            emitAddress(node.getTarget());
            emitDup(PrimitiveTypeInfo.INT);
            emitTyped(node.getValue(), type);
        } else {
            TypeInfo valueType = node.getValue().getResultingType();
            TypeInfo calculationType = TypeRules.compoundCalculationType(operator, type, valueType);
            emitAddress(node.getTarget());
            emitDup(PrimitiveTypeInfo.INT);
            emitDup(PrimitiveTypeInfo.INT);
            emitLoad(type);
            emitConversion(type, calculationType);
            emitTyped(node.getValue(), calculationType);
            BinaryExpressionKind binaryOperator = compoundAssignToBinary(node);
            emitBinaryOp(binaryOperator, calculationType);
            emitConversion(calculationType, type);
        }
        emitStore(type);
        emitLoad(type);
    }

    private BinaryExpressionKind compoundAssignToBinary(AssignExpression node) {
        return switch (node.getOperator()) {
            case ADD_ASSIGN -> BinaryExpressionKind.ADD;
            case SUB_ASSIGN -> BinaryExpressionKind.SUBTRACT;
            case MUL_ASSIGN -> BinaryExpressionKind.MULTIPLY;
            case DIV_ASSIGN -> BinaryExpressionKind.DIVIDE;
            case MOD_ASSIGN -> BinaryExpressionKind.MODULO;
            case BITWISE_OR_ASSIGN -> BinaryExpressionKind.BITWISE_OR;
            case BITWISE_AND_ASSIGN -> BinaryExpressionKind.BITWISE_AND;
            case BITWISE_XOR_ASSIGN -> BinaryExpressionKind.BITWISE_XOR;
            case LEFT_SHIFT_ASSIGN -> BinaryExpressionKind.SHIFT_LEFT;
            case RIGHT_SHIFT_ASSIGN -> BinaryExpressionKind.SHIFT_RIGHT;
            case ASSIGN, INVALID -> throw new IllegalStateException("Unexpected compound assignment operator: " + node.getOperator());
        };
    }

    @Override
    public void visit(ConditionalExpression node) {
        String falseLabel = nextLabel("cond_false");
        String endLabel = nextLabel("cond_end");
        TypeInfo resultType = node.getResultingType();
        emitAsBoolean(node.getCondition());
        writeLine("JUMPF, " + falseLabel);
        emitTyped(node.getTrueExpression(), resultType);
        writeLine("JUMP, " + endLabel);
        writeLabel(falseLabel);
        emitTyped(node.getFalseExpression(), resultType);
        writeLabel(endLabel);
    }

    @Override
    public void visit(BinaryExpression node) {
        BinaryExpressionKind operator = node.getOperator();
        TypeInfo operandType = binaryOperandType(operator, node);
        if (isLogical(operator)) {
            emitBinaryOp(node, operandType);
            return;
        }
        if (isStringEquality(node)) {
            node.getLeft().accept(this);
            node.getRight().accept(this);
            writeLine(operator == BinaryExpressionKind.EQUALS ? "STREQ" : "STRNE");
            return;
        }
        emitTyped(node.getLeft(), operandType);
        emitTyped(node.getRight(), operandType);
        emitBinaryOp(operator, operandType);
        emitConversion(binaryResultType(operator, operandType), node.getResultingType());
    }

    private TypeInfo binaryOperandType(BinaryExpressionKind operator, BinaryExpression node) {
        if (isLogical(operator)) {
            return PrimitiveTypeInfo.BOOL;
        }
        if (isIntegerOnlyBinary(operator)) {
            return PrimitiveTypeInfo.INT;
        }
        if (operator == BinaryExpressionKind.EQUALS || operator == BinaryExpressionKind.NOT_EQUALS) {
            return TypeRules.equalityOperandType(node.getLeft().getResultingType(), node.getRight().getResultingType());
        }
        TypeInfo left = node.getLeft().getResultingType();
        TypeInfo right = node.getRight().getResultingType();
        if (isComparison(operator) && PrimitiveTypeInfo.CHAR.equals(left) && PrimitiveTypeInfo.CHAR.equals(right)) {
            return PrimitiveTypeInfo.CHAR;
        }
        if (PrimitiveTypeInfo.DOUBLE.equals(left) || PrimitiveTypeInfo.DOUBLE.equals(right)) {
            return PrimitiveTypeInfo.DOUBLE;
        }
        return PrimitiveTypeInfo.INT;
    }

    private TypeInfo binaryResultType(BinaryExpressionKind operator, TypeInfo operandType) {
        if (isLogical(operator) || isComparison(operator)) {
            return PrimitiveTypeInfo.BOOL;
        }
        return operandType;
    }

    private boolean isStringEquality(BinaryExpression node) {
        BinaryExpressionKind operator = node.getOperator();
        if (operator != BinaryExpressionKind.EQUALS && operator != BinaryExpressionKind.NOT_EQUALS) {
            return false;
        }
        TypeInfo left = node.getLeft().getResultingType();
        TypeInfo right = node.getRight().getResultingType();
        return PrimitiveTypeInfo.STRING.equals(left) || PrimitiveTypeInfo.STRING.equals(right);
    }

    private boolean isLogical(BinaryExpressionKind operator) {
        return switch (operator) {
            case AND, OR -> true;
            case ADD, SUBTRACT, MULTIPLY, DIVIDE, MODULO,
                 BITWISE_AND, BITWISE_OR, BITWISE_XOR, SHIFT_LEFT, SHIFT_RIGHT,
                 EQUALS, NOT_EQUALS, LESS, LESS_EQUALS, GREATER, GREATER_EQUALS -> false;
            case INVALID -> throw new IllegalStateException("Unexpected binary operator: " + operator);
        };
    }

    private boolean isIntegerOnlyBinary(BinaryExpressionKind operator) {
        return switch (operator) {
            case MODULO, BITWISE_AND, BITWISE_OR, BITWISE_XOR, SHIFT_LEFT, SHIFT_RIGHT -> true;
            case ADD, SUBTRACT, MULTIPLY, DIVIDE,
                 AND, OR, EQUALS, NOT_EQUALS, LESS, LESS_EQUALS, GREATER, GREATER_EQUALS -> false;
            case INVALID -> throw new IllegalStateException("Unexpected binary operator: " + operator);
        };
    }

    private boolean isComparison(BinaryExpressionKind operator) {
        return switch (operator) {
            case EQUALS, NOT_EQUALS, LESS, LESS_EQUALS, GREATER, GREATER_EQUALS -> true;
            case ADD, SUBTRACT, MULTIPLY, DIVIDE, MODULO,
                 BITWISE_AND, BITWISE_OR, BITWISE_XOR, SHIFT_LEFT, SHIFT_RIGHT,
                 AND, OR -> false;
            case INVALID -> throw new IllegalStateException("Unexpected binary operator: " + operator);
        };
    }

    private void emitLogicalAnd(BinaryExpression node) {
        String falseLabel = nextLabel("and_false");
        String endLabel = nextLabel("and_end");
        emitAsBoolean(node.getLeft());
        writeLine("JUMPF, " + falseLabel);
        emitAsBoolean(node.getRight());
        writeLine("JUMP, " + endLabel);
        writeLabel(falseLabel);
        writeLine("PUSHI, 0");
        writeLabel(endLabel);
    }

    private void emitLogicalOr(BinaryExpression node) {
        String trueLabel = nextLabel("or_true");
        String endLabel = nextLabel("or_end");
        emitAsBoolean(node.getLeft());
        writeLine("JUMPT, " + trueLabel);
        emitAsBoolean(node.getRight());
        writeLine("JUMP, " + endLabel);
        writeLabel(trueLabel);
        writeLine("PUSHI, 1");
        writeLabel(endLabel);
    }

    @Override
    public void visit(CastExpression node) {
        node.getOperand().accept(this);
        emitUncheckedCast(node.getOperand().getResultingType(), node.getResultingType());
    }

    @Override
    public void visit(UnaryExpression node) {
        switch (node.getKind()) {
            case PLUS -> node.getOperand().accept(this);
            case MINUS -> {
                node.getOperand().accept(this);
                TypeInfo type = node.getOperand().getResultingType();
                writeLine(PrimitiveTypeInfo.DOUBLE.equals(type) ? "DNEG" : "INEG");
            }
            case BITWISE_NOT -> {
                node.getOperand().accept(this);
                writeLine("IINV");
            }
            case LOGICAL_NOT -> {
                emitAsBoolean(node.getOperand());
                String falseLabel = nextLabel("not_false");
                String endLabel = nextLabel("not_end");
                writeLine("JUMPF, " + falseLabel);
                writeLine("PUSHI, 0");
                writeLine("JUMP, " + endLabel);
                writeLabel(falseLabel);
                writeLine("PUSHI, 1");
                writeLabel(endLabel);
            }
            case PRE_INCREMENT -> emitPrefixStep(node.getOperand(), 1);
            case PRE_DECREMENT -> emitPrefixStep(node.getOperand(), -1);
            case INVALID -> throw new IllegalStateException("Unexpected unary operator: " + node.getKind());
        }
    }

    private void emitPrefixStep(ExpressionAstNode target, int delta) {
        TypeInfo type = target.getResultingType();
        emitAddress(target);
        emitAddress(target);
        emitLoad(type);
        emitNumericLiteralPush(type, delta);
        emitBinaryOp(BinaryExpressionKind.ADD, type);
        emitStore(type);
        emitAddress(target);
        emitLoad(type);
    }

    @Override
    public void visit(PostfixExpression node) {
        ExpressionAstNode target = node.getOperand();
        int delta = switch (node.getKind()) {
            case INCREMENT -> 1;
            case DECREMENT -> -1;
            case INVALID -> throw new IllegalStateException("Unexpected postfix operator: " + node.getKind());
        };
        TypeInfo type = target.getResultingType();
        emitAddress(target);
        emitLoad(type);
        emitAddress(target);
        emitAddress(target);
        emitLoad(type);
        emitNumericLiteralPush(type, delta);
        emitBinaryOp(BinaryExpressionKind.ADD, type);
        emitStore(type);
    }

    @Override
    public void visit(CallExpression node) {
        FunctionEntry function = node.getResolvedFunction();
        if (function.isBuiltIn()) {
            emitPrintBuiltin(node);
            return;
        }
        List<ParameterEntry> parameters = new ArrayList<>(function.getScope().getParameters().values());
        if (function.isVariadic()) {
            emitVariadicArguments(node, parameters, function.getVariadicParameter());
        } else {
            emitFixedArguments(node, parameters);
        }
        writeLine("CALL, " + function.getLabel() + ", " + function.getParameterBytes());
    }

    private void emitFixedArguments(CallExpression node, List<ParameterEntry> parameters) {
        for (int i = 0; i < node.getArguments().size(); i++) {
            emitTyped(node.getArguments().get(i), parameters.get(i).getType());
        }
    }

    private void emitVariadicArguments(
            CallExpression node,
            List<ParameterEntry> parameters,
            ParameterEntry variadicParameter) {
        int fixedCount = parameters.size() - 1;
        for (int i = 0; i < fixedCount; i++) {
            emitTyped(node.getArguments().get(i), parameters.get(i).getType());
        }

        if (isVariadicArrayPassThrough(node, fixedCount, variadicParameter)) {
            emitTyped(node.getArguments().get(fixedCount), variadicParameter.getType());
            return;
        }

        emitVariadicArray(node.getArguments(), fixedCount, variadicParameter.getVariadicElementType());
    }

    private boolean isVariadicArrayPassThrough(
            CallExpression node,
            int fixedCount,
            ParameterEntry variadicParameter) {
        return node.getArguments().size() == fixedCount + 1
                && TypeRules.isAssignable(
                        variadicParameter.getType(),
                        node.getArguments().get(fixedCount).getResultingType());
    }

    private void emitVariadicArray(List<ExpressionAstNode> arguments, int firstVariadicIndex, TypeInfo elementType) {
        int elementSize = memoryBytes(elementType);
        int length = arguments.size() - firstVariadicIndex;
        writeLine("PUSHI, " + length);
        writeLine("NEWA, " + elementSize);

        for (int i = 0; i < length; i++) {
            emitDup(PrimitiveTypeInfo.INT);
            writeLine("PUSHI, " + (VmLayout.ARRAY_PAYLOAD_OFFSET_BYTES + i * elementSize));
            writeLine("IADD");
            emitTyped(arguments.get(firstVariadicIndex + i), elementType);
            emitStore(elementType);
        }
    }

    @Override
    public void visit(IndexExpression node) {
        emitAddress(node);
        emitLoad(node.getResultingType());
    }

    @Override
    public void visit(MemberAccessExpression node) {
        if (node.getResolvedEnumValue() != null) {
            emitEnumValue(node.getResolvedEnumValue());
            return;
        }
        if (isLengthAccess(node)) {
            node.getTarget().accept(this);
            writeLine("LOAD4");
            return;
        }
        emitAddress(node);
        emitLoad(node.getResolvedField().getType());
    }

    @Override
    public void visit(NewExpression node) {
        if (node.getResultingType() instanceof StructTypeInfo(
                StructEntry entry
        )) {
            int sizeBytes = entry.getSizeBytes();
            writeLine("PUSHI, " + sizeBytes);
            writeLine("NEW");
            return;
        }
        ArrayTypeInfo arrayType = (ArrayTypeInfo) node.getResultingType();
        TypeInfo elementType = arrayType.elementType();
        ArrayInitExpression init = node.getArrayInit();

        if (init == null && node.getDimensions().size() > 1) {
            emitJaggedArrayAllocation(node, TypeRules.leafElementType(node.getResultingType()));
            return;
        }

        int elementSize = memoryBytes(elementType);
        emitArrayAllocation(node.getDimensions(), init, elementSize);

        if (init != null) {
            DataEntry template = dataSection.addArrayTemplate(elementType, arrayTemplateValues(init.getElements(), elementType));
            emitDup(PrimitiveTypeInfo.INT);
            emitArrayPayloadOffset();
            writeLine("PUSHR, " + template.getLabel());
            writeLine("PUSHI, " + (init.getElements().size() * elementSize));
            writeLine("MEMCPY");
            initializeArrayElements(init.getElements(), elementType, elementSize);
        }
    }

    @Override
    public void visit(ArrayInitExpression node) {
        ArrayTypeInfo arrayType = (ArrayTypeInfo) node.getResultingType();
        TypeInfo elementType = arrayType.elementType();
        int elementSize = memoryBytes(elementType);
        DataEntry template = dataSection.addArrayTemplate(elementType, arrayTemplateValues(node.getElements(), elementType));
        writeLine("PUSHI, " + node.getElements().size());
        writeLine("NEWA, " + elementSize);
        emitDup(PrimitiveTypeInfo.INT);
        emitArrayPayloadOffset();
        writeLine("PUSHR, " + template.getLabel());
        writeLine("PUSHI, " + (node.getElements().size() * elementSize));
        writeLine("MEMCPY");
        initializeArrayElements(node.getElements(), elementType, elementSize);
    }

    private void initializeArrayElements(List<ExpressionAstNode> elements, TypeInfo elementType, int elementSize) {
        for (int i = 0; i < elements.size(); i++) {
            if (staticArrayTemplateValue(elements.get(i), elementType).isPresent()) {
                continue;
            }
            emitDup(PrimitiveTypeInfo.INT);
            writeLine("PUSHI, " + (VmLayout.ARRAY_PAYLOAD_OFFSET_BYTES + i * elementSize));
            writeLine("IADD");
            emitTyped(elements.get(i), elementType);
            emitStore(elementType);
        }
    }

    private void emitArrayAllocation(List<ExpressionAstNode> dimensions, ArrayInitExpression init, int elementSize) {
        if (dimensions.isEmpty()) {
            writeLine("PUSHI, " + init.getElements().size());
            writeLine("NEWA, " + elementSize);
            return;
        }

        emitTyped(dimensions.getFirst(), PrimitiveTypeInfo.INT);
        writeLine("NEWA, " + elementSize);
    }

    private void emitJaggedArrayAllocation(NewExpression node, TypeInfo leafType) {
        NewExpression.JaggedArrayTempLayout temps = node.getJaggedArrayTempLayout();
        if (temps == null) {
            throw new IllegalStateException("Missing temporary storage for jagged array allocation.");
        }

        List<ExpressionAstNode> dimensions = node.getDimensions();
        for (int i = 0; i < dimensions.size(); i++) {
            writeLine("LOCAL, " + temps.dimensionOffsets()[i]);
            emitTyped(dimensions.get(i), PrimitiveTypeInfo.INT);
            writeLine("STORE4");
        }
        emitJaggedArrayLevel(0, dimensions.size(), leafType, temps);
    }

    private void emitJaggedArrayLevel(
            int depth,
            int dimensionCount,
            TypeInfo leafType,
            NewExpression.JaggedArrayTempLayout temps) {
        int elementSize = depth + 1 == dimensionCount ? memoryBytes(leafType) : VmLayout.WORD_BYTES;
        if (depth + 1 == dimensionCount) {
            emitLoadTemp(temps.dimensionOffsets()[depth]);
            writeLine("NEWA, " + elementSize);
            return;
        }

        int baseOffset = temps.baseOffsets()[depth];
        int indexOffset = temps.indexOffsets()[depth];
        String loopLabel = nextLabel("jagged_alloc_loop");
        String endLabel = nextLabel("jagged_alloc_end");

        writeLine("LOCAL, " + baseOffset);
        emitLoadTemp(temps.dimensionOffsets()[depth]);
        writeLine("NEWA, " + elementSize);
        writeLine("STORE4");

        writeLine("LOCAL, " + indexOffset);
        writeLine("PUSHI, 0");
        writeLine("STORE4");

        writeLabel(loopLabel);
        emitLoadTemp(indexOffset);
        emitLoadTemp(temps.dimensionOffsets()[depth]);
        writeLine("ILT");
        writeLine("JUMPF, " + endLabel);

        emitLoadTemp(baseOffset);
        emitLoadTemp(indexOffset);
        emitScaleTopBy(VmLayout.WORD_BYTES);
        emitArrayPayloadOffset();
        writeLine("IADD");
        emitJaggedArrayLevel(depth + 1, dimensionCount, leafType, temps);
        writeLine("STORE4");

        writeLine("LOCAL, " + indexOffset);
        emitLoadTemp(indexOffset);
        writeLine("PUSHI, 1");
        writeLine("IADD");
        writeLine("STORE4");
        writeLine("JUMP, " + loopLabel);

        writeLabel(endLabel);
        emitLoadTemp(baseOffset);
    }

    private void emitLoadTemp(int offset) {
        writeLine("LOCAL, " + offset);
        writeLine("LOAD4");
    }

    private void emitScaleTopBy(int size) {
        if (size != VmLayout.BYTE_BYTES) {
            writeLine("PUSHI, " + size);
            writeLine("IMUL");
        }
    }

    private void emitArrayPayloadOffset() {
        writeLine("PUSHI, " + VmLayout.ARRAY_PAYLOAD_OFFSET_BYTES);
        writeLine("IADD");
    }

    private List<Object> arrayTemplateValues(List<ExpressionAstNode> elements, TypeInfo elementType) {
        return elements.stream()
                .map(element -> staticArrayTemplateValue(element, elementType).orElse(TypeRules.defaultValue(elementType)))
                .toList();
    }

    private Optional<Object> staticArrayTemplateValue(ExpressionAstNode expression, TypeInfo elementType) {
        if (expression instanceof LiteralExpression<?> literal) {
            if (literal.getResultingType().equals(elementType) && isPrimitiveTemplateValue(literal)) {
                return Optional.ofNullable(literal.getValue());
            }
        }
        return Optional.empty();
    }

    private boolean isPrimitiveTemplateValue(LiteralExpression<?> literal) {
        return switch (literal.getKind()) {
            case INT, DOUBLE, BOOLEAN, CHAR -> true;
            case STRING, NULL -> false;
        };
    }

    @Override
    public void visit(NameExpression node) {
        SymbolEntry entry = node.getSymbolEntry();
        if (entry instanceof EnumValueEntry enumValue) {
            emitEnumValue(enumValue);
            return;
        }
        emitFrameLoad((StorageEntry) entry);
    }

    private void emitEnumValue(EnumValueEntry enumValue) {
        dataSection.addEnumValues(enumValue.getOwnerEnum());
        writeLine("PUSHR, " + enumValue.getDataLabel());
        if (enumValue.getOffsetBytes() != 0) {
            writeLine("PUSHI, " + enumValue.getOffsetBytes());
            writeLine("IADD");
        }
    }

    @Override
    public void visit(LiteralExpression<?> node) {
        emitLiteralPush(node);
    }

    @Override
    public void visit(ForInitExpressionList node) {
        for (ExpressionAstNode expression : node.getExpressions()) {
            emitStatement(expression);
        }
    }

    private void emitPrintBuiltin(CallExpression node) {
        FunctionEntry function = node.getResolvedFunction();
        BuiltInFunction builtIn = BuiltInFunction.find(function.getName());
        emitTyped(node.getArguments().getFirst(), builtIn.getParameterType());
        writeLine(builtIn.getVmInstruction());
    }

    private void emitTyped(ExpressionAstNode expression, TypeInfo expectedType) {
        if (BuiltInTypes.isBoxingConversion(expression.getResultingType(), expectedType)) {
            emitBoxedExpression(expression);
            return;
        }
        expression.accept(this);
        emitConversion(expression.getResultingType(), expectedType);
    }

    private void emitBoxedExpression(ExpressionAstNode expression) {
        TypeInfo sourceType = expression.getResultingType();
        StructEntry wrapper = BuiltInTypes.wrapperStructForPrimitive(globalScope, sourceType);
        EnumValueEntry dataTypeValue = BuiltInTypes.dataTypeValueForPrimitive(globalScope, sourceType);
        if (wrapper == null || dataTypeValue == null) {
            throw new IllegalStateException("Missing built-in wrapper for " + sourceType);
        }

        writeLine("PUSHI, " + wrapper.getSizeBytes());
        writeLine("NEW");
        emitBoxedField(wrapper, BuiltInTypes.TYPE_FIELD_NAME, () -> emitEnumValue(dataTypeValue));
        emitBoxedField(wrapper, BuiltInTypes.VALUE_FIELD_NAME, () -> expression.accept(this));
    }

    private void emitBoxedField(StructEntry wrapper, String fieldName, Runnable emitValue) {
        FieldEntry field = wrapper.getScope().resolveField(fieldName);
        if (field == null) {
            throw new IllegalStateException("Missing built-in wrapper field: " + wrapper.getName() + "." + fieldName);
        }

        emitDup(PrimitiveTypeInfo.INT);
        emitOffset(field.getOffsetBytes());
        emitValue.run();
        emitStore(field.getType());
    }

    private void emitConversion(TypeInfo from, TypeInfo to) {
        if (!TypeRules.needsConversion(from, to)) {
            return;
        }
        PrimitiveTypeKind kind = ((PrimitiveTypeInfo) from).kind();
        PrimitiveTypeKind kind1 = ((PrimitiveTypeInfo) to).kind();
        if (kind == kind1) {
            return;
        }
        switch (kind) {
            case INT -> {
                if (kind1 == PrimitiveTypeKind.DOUBLE) {
                    writeLine("I2D");
                } else {
                    throw new IllegalStateException("Unexpected conversion from " + from + " to " + to);
                }
            }
            case DOUBLE -> {
                if (kind1 == PrimitiveTypeKind.INT) {
                    writeLine("D2I");
                } else {
                    throw new IllegalStateException("Unexpected conversion from " + from + " to " + to);
                }
            }
            default -> throw new IllegalStateException("Unexpected conversion from " + from + " to " + to);
        }
    }

    private void emitUncheckedCast(TypeInfo from, TypeInfo to) {
        if (from instanceof VoidTypeInfo) {
            emitDefaultValue(to, TypeRules.defaultValue(to));
            return;
        }

        int sourceBytes = stackBytes(from);
        int targetBytes = stackBytes(to);
        if (sourceBytes == targetBytes) {
            return;
        }
        if (sourceBytes == VmLayout.WORD_BYTES && targetBytes == VmLayout.DOUBLE_BYTES) {
            writeLine("I2D");
            return;
        }
        if (sourceBytes == VmLayout.DOUBLE_BYTES && targetBytes == VmLayout.WORD_BYTES) {
            writeLine("D2I");
            return;
        }
        throw new IllegalStateException("Unexpected unchecked cast from " + from + " to " + to);
    }

    private void emitAsBoolean(ExpressionAstNode expression) {
        expression.accept(this);
        TypeInfo type = expression.getResultingType();
        if (PrimitiveTypeInfo.DOUBLE.equals(type)) {
            String falseLabel = nextLabel("bool_false");
            String endLabel = nextLabel("bool_end");
            writeLine("DUP, " + VmLayout.DOUBLE_BYTES);
            writeLine("PUSHD, 0.0");
            writeLine("DEQ");
            writeLine("JUMPT, " + falseLabel);
            writeLine("DUP, " + VmLayout.DOUBLE_BYTES);
            writeLine("DEQ");
            writeLine("JUMP, " + endLabel);
            writeLabel(falseLabel);
            writeLine("POP, " + VmLayout.DOUBLE_BYTES);
            writeLine("PUSHI, 0");
            writeLabel(endLabel);
        }
    }

    private void emitLiteralPush(LiteralExpression<?> node) {
        switch (node.getKind()) {
            case INT -> writeLine("PUSHI, " + node.getValue());
            case DOUBLE -> writeLine("PUSHD, " + formatDouble((Double) node.getValue()));
            case BOOLEAN -> writeLine("PUSHI, " + (Boolean.TRUE.equals(node.getValue()) ? "1" : "0"));
            case CHAR -> writeLine("PUSHI, " + (int) (Character) node.getValue());
            case STRING -> {
                var entry = dataSection.internString((String) node.getValue());
                writeLine("PUSHR, " + entry.getLabel());
            }
            case NULL -> writeLine("PUSHI, 0");
        }
    }

    private String formatDouble(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.format(Locale.ROOT, "%.1f", value);
        }
        return Double.toString(value);
    }

    private void emitNumericLiteralPush(TypeInfo type, int value) {
        if (PrimitiveTypeInfo.DOUBLE.equals(type)) {
            writeLine("PUSHD, " + formatDouble(value));
            return;
        }
        writeLine("PUSHI, " + value);
    }

    private void emitDefaultValue(TypeInfo type, Object value) {
        if (type instanceof EnumTypeInfo) {
            writeLine("PUSHI, 0");
            return;
        }
        if (PrimitiveTypeInfo.DOUBLE.equals(type)) {
            writeLine("PUSHD, " + formatDouble((Double) value));
            return;
        }
        if (PrimitiveTypeInfo.BOOL.equals(type)) {
            writeLine("PUSHI, " + (Boolean.TRUE.equals(value) ? 1 : 0));
            return;
        }
        if (PrimitiveTypeInfo.CHAR.equals(type)) {
            writeLine("PUSHI, " + (int) (Character) value);
            return;
        }
        if (value instanceof Number number) {
            writeLine("PUSHI, " + number.intValue());
            return;
        }
        writeLine("PUSHI, 0");
    }

    private void emitBinaryOp(BinaryExpressionKind operator, TypeInfo operandType) {
        boolean isDouble = PrimitiveTypeInfo.DOUBLE.equals(operandType);
        switch (operator) {
            case ADD -> writeLine(isDouble ? "DADD" : "IADD");
            case SUBTRACT -> writeLine(isDouble ? "DSUB" : "ISUB");
            case MULTIPLY -> writeLine(isDouble ? "DMUL" : "IMUL");
            case DIVIDE -> writeLine(isDouble ? "DDIV" : "IDIV");
            case MODULO -> writeLine("IMOD");
            case BITWISE_AND -> writeLine("IAND");
            case BITWISE_OR -> writeLine("IOR");
            case BITWISE_XOR -> writeLine("IXOR");
            case SHIFT_LEFT -> writeLine("ISHL");
            case SHIFT_RIGHT -> writeLine("ISHR");
            case LESS -> writeLine(isDouble ? "DLT" : "ILT");
            case LESS_EQUALS -> writeLine(isDouble ? "DLE" : "ILE");
            case GREATER -> writeLine(isDouble ? "DGT" : "IGT");
            case GREATER_EQUALS -> writeLine(isDouble ? "DGE" : "IGE");
            case EQUALS -> writeLine(isDouble ? "DEQ" : "IEQ");
            case NOT_EQUALS -> writeLine(isDouble ? "DNE" : "INE");
            case AND, OR -> throw new IllegalStateException("Logical operators require expression-level emission.");
            case INVALID -> throw new IllegalStateException("Unexpected binary operator: " + operator);
        }
    }

    private void emitBinaryOp(BinaryExpression node, TypeInfo operandType) {
        switch (node.getOperator()) {
            case AND -> emitLogicalAnd(node);
            case OR -> emitLogicalOr(node);
            case ADD, SUBTRACT, MULTIPLY, DIVIDE, MODULO,
                 BITWISE_AND, BITWISE_OR, BITWISE_XOR, SHIFT_LEFT, SHIFT_RIGHT,
                 LESS, LESS_EQUALS, GREATER, GREATER_EQUALS, EQUALS, NOT_EQUALS ->
                    emitBinaryOp(node.getOperator(), operandType);
            case INVALID -> throw new IllegalStateException("Unexpected binary operator: " + node.getOperator());
        }
    }

    private void emitEqualityCompare(TypeInfo leftType, TypeInfo rightType) {
        if (PrimitiveTypeInfo.STRING.equals(leftType) || PrimitiveTypeInfo.STRING.equals(rightType)) {
            writeLine("STREQ");
            return;
        }
        TypeInfo operandType = TypeRules.equalityOperandType(leftType, rightType);
        emitBinaryOp(BinaryExpressionKind.EQUALS, operandType);
    }

    private void emitFrameLoad(StorageEntry storage) {
        emitStorageAddress(storage);
        emitLoad(storage.getType());
    }

    private void emitAddress(ExpressionAstNode expression) {
        if (expression instanceof NameExpression name && name.getSymbolEntry() instanceof StorageEntry storage) {
            emitStorageAddress(storage);
            return;
        }
        if (expression instanceof IndexExpression index) {
            emitIndexAddress(index);
            return;
        }
        emitMemberAddress((MemberAccessExpression) expression);
    }

    private void emitStorageAddress(StorageEntry storage) {
        int offset = storage.getOffsetBytes();
        if (storage instanceof ParameterEntry) {
            offset += currentFunction.getLocalBytes();
        }
        writeLine("LOCAL, " + offset);
    }

    private void emitIndexAddress(IndexExpression node) {
        TypeInfo targetType = node.getTarget().getResultingType();
        TypeInfo elementType = PrimitiveTypeInfo.STRING.equals(targetType)
                ? PrimitiveTypeInfo.CHAR
                : ((ArrayTypeInfo) targetType).elementType();
        node.getTarget().accept(this);
        emitTyped(node.getIndex(), PrimitiveTypeInfo.INT);
        int elementSize = memoryBytes(elementType);
        writeLine("BOUNDS, " + elementSize);
        if (elementSize != VmLayout.BYTE_BYTES) {
            writeLine("PUSHI, " + elementSize);
            writeLine("IMUL");
        }
        emitArrayPayloadOffset();
        writeLine("IADD");
    }

    private void emitMemberAddress(MemberAccessExpression node) {
        FieldEntry field = node.getResolvedField();
        node.getTarget().accept(this);
        emitOffset(field.getOffsetBytes());
    }

    private void emitOffset(int offsetBytes) {
        if (offsetBytes != 0) {
            writeLine("PUSHI, " + offsetBytes);
            writeLine("IADD");
        }
    }

    private boolean isLengthAccess(MemberAccessExpression node) {
        if (!"length".equals(node.getMemberName())) {
            return false;
        }
        TypeInfo targetType = node.getTarget().getResultingType();
        return PrimitiveTypeInfo.STRING.equals(targetType) || targetType instanceof ArrayTypeInfo;
    }

    private void emitLoad(TypeInfo type) {
        writeLine(memoryWidth(type).loadInstruction());
    }

    private void emitStore(TypeInfo type) {
        writeLine(memoryWidth(type).storeInstruction());
    }

    private void emitDup(TypeInfo type) {
        writeLine("DUP, " + stackBytes(type));
    }

    private void emitPop(TypeInfo type) {
        writeLine("POP, " + stackBytes(type));
    }

    private void emitReturn(TypeInfo type) {
        writeLine("RET, " + returnBytes(type));
    }

    private int memoryBytes(TypeInfo type) {
        return memoryWidth(type).bytes();
    }

    private VmLayout.MemoryWidth memoryWidth(TypeInfo type) {
        return VmLayout.memoryWidth(type);
    }

    private int stackBytes(TypeInfo type) {
        return VmLayout.stackBytes(type);
    }

    private int returnBytes(TypeInfo type) {
        if (isVoidLike(type)) {
            return 0;
        }
        return stackBytes(type);
    }

    private boolean isVoidLike(TypeInfo type) {
        return type instanceof VoidTypeInfo;
    }

    private record LoopContext(String breakLabel, String continueLabel) {
    }

    private record SwitchTarget(SwitchCase switchCase, String label) {
    }

}
