package ch.zhaw.it.pm4.javer.compiler.frontend.analysis;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.AstNodeVisitorBase;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.AstNode;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.CompilationUnit;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.case_label.CaseLabelAstNode;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.case_label.EnumCaseLabel;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.case_label.LiteralCaseLabel;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.declaration.FunctionDeclaration;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.declaration.FunctionParameter;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.statement.*;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.type.*;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.scope.GlobalScope;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.symbol.*;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.typeinfo.*;
import ch.zhaw.it.pm4.javer.compiler.diagnostics.DiagnosticBag;
import ch.zhaw.it.pm4.javer.compiler.diagnostics.Severity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Assigns semantic type information to expression and type AST nodes.
 */
public class TypeCheckVisitor extends AstNodeVisitorBase {

    private final DiagnosticBag diagnosticBag;
    private final Set<StorageEntry> invalidInitializedVariables = new HashSet<>();
    private GlobalScope globalScope;
    private TypeInfo currentFunctionReturnType = UnknownTypeInfo.INSTANCE;

    /**
     * Creates a type-checking pass.
     *
     * @param diagnosticBag optional collector for type diagnostics
     */
    public TypeCheckVisitor(DiagnosticBag diagnosticBag) {
        this.diagnosticBag = diagnosticBag;
    }

    @Override
    public void visit(CompilationUnit node) {
        globalScope = node.getGlobalScope();
        invalidInitializedVariables.clear();
        super.visit(node);
    }

    @Override
    public void visit(LiteralExpression<?> node) {
        node.setResultingType(switch (node.getKind()) {
            case INT -> PrimitiveTypeInfo.INT;
            case DOUBLE -> PrimitiveTypeInfo.DOUBLE;
            case BOOLEAN -> PrimitiveTypeInfo.BOOL;
            case STRING -> PrimitiveTypeInfo.STRING;
            case CHAR -> PrimitiveTypeInfo.CHAR;
            case NULL -> NullTypeInfo.INSTANCE;
        });
    }

    @Override
    public void visit(NameExpression node) {
        SymbolEntry entry = node.getSymbolEntry();
        if (entry instanceof StorageEntry storageEntry) {
            if (invalidInitializedVariables.contains(storageEntry)) {
                node.setResultingType(UnknownTypeInfo.INSTANCE);
                return;
            }
            node.setResultingType(storageEntry.getType());
            return;
        }
        if (entry instanceof EnumValueEntry enumValueEntry) {
            node.setResultingType(new EnumTypeInfo(enumValueEntry.getOwnerEnum()));
            return;
        }
        node.setResultingType(UnknownTypeInfo.INSTANCE);
    }

    @Override
    public void visit(FunctionDeclaration node) {
        TypeInfo previous = currentFunctionReturnType;

        currentFunctionReturnType = node.getSymbolEntry() != null
                ? node.getSymbolEntry().getReturnType()
                : resolveType(node.getReturnType());
        super.visit(node);
        currentFunctionReturnType = previous;
    }

    @Override
    public void visit(FunctionParameter node) {
        node.getType().accept(this);

        TypeInfo resolved = resolveType(node.getType());
        if (resolved instanceof UnknownTypeInfo && diagnosticBag != null) {
            diagnosticBag.add(node.getSourceRange().start(), Severity.ERROR,
                    "Undefined type for parameter: " + node.getName());
        }
    }

    @Override
    public void visit(ReturnStatement node) {
        super.visit(node);

        TypeInfo actualReturnType = node.getExpression() == null
                ? VoidTypeInfo.INSTANCE
                : node.getExpression().getResultingType();

        TypeInfo expectedReturnType = currentFunctionReturnType == null
                ? UnknownTypeInfo.INSTANCE
                : currentFunctionReturnType;

        if (expectedReturnType instanceof VoidTypeInfo && node.getExpression() != null) {
            report(node, "Void function must not return a value.");
            return;
        }

        if (!(expectedReturnType instanceof VoidTypeInfo) && node.getExpression() == null) {
            report(node, "Missing return value. Expected: " + expectedReturnType);
            return;
        }

        if (isNotAssignable(expectedReturnType, actualReturnType)) {
            report(node, "Return type mismatch. Expected: " + expectedReturnType + ", actual: " + actualReturnType);
        }
    }

    private boolean isNotAssignable(TypeInfo expected, TypeInfo actual) {
        return !TypeRules.isAssignable(expected, actual);
    }

    private void report(AstNode node, String message) {
        if (diagnosticBag != null) {
            diagnosticBag.add(node.getSourceRange().start(), Severity.ERROR, message);
        }
    }

    @Override
    public void visit(VarDeclarationStatement node) {
        super.visit(node);

        if (node.getInitializer() == null) {
            return;
        }

        TypeInfo targetType = node.getSymbolEntry() == null
                ? resolveType(node.getType())
                : node.getSymbolEntry().getType();
        TypeInfo initializerType = node.getInitializer().getResultingType();

        if (isNotAssignable(targetType, initializerType)) {
            report(node, "Cannot initialize variable '" + node.getName() + "' of type "
                    + targetType + " with " + initializerType + ".");
            if (node.getSymbolEntry() != null) {
                invalidInitializedVariables.add(node.getSymbolEntry());
            }
        }
    }

    @Override
    public void visit(CallExpression node) {
        super.visit(node);

        FunctionEntry function = node.getResolvedFunction();
        if (function == null) {
            report(node, "Unknown function: " + node.getFunctionName());
            node.setResultingType(UnknownTypeInfo.INSTANCE);
            return;
        }

        List<ParameterEntry> parameters = new ArrayList<>(function.getScope().getParameters().values());

        if (!function.isVariadic()) {
            if (!validateFixedArguments(node, parameters)) {
                node.setResultingType(UnknownTypeInfo.INSTANCE);
                return;
            }
            node.setResultingType(function.getReturnType());
            return;
        }

        if (!validateVariadicArguments(node, parameters, function.getVariadicParameter())) {
            node.setResultingType(UnknownTypeInfo.INSTANCE);
            return;
        }
        node.setResultingType(function.getReturnType());
    }

    private boolean validateFixedArguments(CallExpression node, List<ParameterEntry> parameters) {
        if (node.getArguments().size() != parameters.size()) {
            report(node, "Argument count mismatch for function " + node.getFunctionName());
            return false;
        }

        boolean valid = true;
        for (int index = 0; index < parameters.size(); index++) {
            valid &= validateArgument(node, index, parameters.get(index).getType());
        }
        return valid;
    }

    private boolean validateVariadicArguments(
            CallExpression node,
            List<ParameterEntry> parameters,
            ParameterEntry variadicParameter) {
        int fixedCount = parameters.size() - 1;
        if (node.getArguments().size() < fixedCount) {
            report(node, "Argument count mismatch for function " + node.getFunctionName());
            return false;
        }

        boolean valid = true;
        for (int index = 0; index < fixedCount; index++) {
            valid &= validateArgument(node, index, parameters.get(index).getType());
        }

        if (isVariadicArrayPassThrough(node, fixedCount, variadicParameter)) {
            return validateArgument(node, fixedCount, variadicParameter.getType()) && valid;
        }

        TypeInfo elementType = variadicParameter.getVariadicElementType();
        for (int index = fixedCount; index < node.getArguments().size(); index++) {
            valid &= validateArgument(node, index, elementType);
        }
        return valid;
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

    private boolean validateArgument(CallExpression node, int index, TypeInfo expected) {
        TypeInfo actual = node.getArguments().get(index).getResultingType();
        if (isNotAssignable(expected, actual)) {
            report(node, "Argument " + (index + 1) + " type mismatch: expected "
                    + expected + ", got " + actual);
            return false;
        }
        return true;
    }

    @Override
    public void visit(BinaryExpression node) {
        super.visit(node);

        TypeInfo left = node.getLeft().getResultingType();
        TypeInfo right = node.getRight().getResultingType();

        TypeInfo result = switch (node.getOperator()) {
            case OR, AND -> {
                if (!TypeRules.isConditionType(left) || !TypeRules.isConditionType(right)) {
                    report(node, "Logical operator requires boolean operands.");
                    yield UnknownTypeInfo.INSTANCE;
                }
                yield PrimitiveTypeInfo.BOOL;
            }

            case EQUALS, NOT_EQUALS -> {
                if (!TypeRules.isEqualityComparable(left, right)) {
                    report(node, "Equality operator requires compatible operands.");
                    yield UnknownTypeInfo.INSTANCE;
                }
                yield PrimitiveTypeInfo.BOOL;
            }

            case LESS, LESS_EQUALS, GREATER, GREATER_EQUALS -> {
                if (!TypeRules.isOrderedComparable(left, right)) {
                    report(node, "Comparison operator requires numeric or char operands.");
                    yield UnknownTypeInfo.INSTANCE;
                }
                yield PrimitiveTypeInfo.BOOL;
            }

            case ADD, SUBTRACT, MULTIPLY, DIVIDE, MODULO -> {
                if (TypeRules.isNotBinaryOperatorAllowed(node.getOperator(), left, right)) {
                    report(node, node.getOperator() == BinaryExpressionKind.MODULO
                            ? "Modulo operator requires int operands."
                            : "Arithmetic operator requires numeric operands.");
                    yield UnknownTypeInfo.INSTANCE;
                }
                yield TypeRules.binaryResult(node.getOperator(), left, right);
            }

            case BITWISE_OR, BITWISE_AND, BITWISE_XOR, SHIFT_LEFT, SHIFT_RIGHT -> {
                if (TypeRules.isNotBinaryOperatorAllowed(node.getOperator(), left, right)) {
                    report(node, "Bitwise and shift operators require integer operands.");
                    yield UnknownTypeInfo.INSTANCE;
                }
                yield TypeRules.binaryResult(node.getOperator(), left, right);
            }

            case INVALID -> {
                report(node, "Invalid binary operator.");
                yield UnknownTypeInfo.INSTANCE;
            }
        };

        node.setResultingType(result);
    }

    @Override
    public void visit(AssignExpression node) {
        super.visit(node);

        TypeInfo targetType = node.getTarget() == null
                ? UnknownTypeInfo.INSTANCE
                : node.getTarget().getResultingType();

        TypeInfo valueType = node.getValue() == null
                ? UnknownTypeInfo.INSTANCE
                : node.getValue().getResultingType();

        if (isNotAssignableTarget(node.getTarget())) {
            report(node, "Left side of assignment is not assignable.");
            node.setResultingType(UnknownTypeInfo.INSTANCE);
            return;
        }

        boolean valid = true;

        switch (node.getOperator()) {
            case ASSIGN -> {
                if (isNotAssignable(targetType, valueType)) {
                    report(node, "Cannot assign " + valueType + " to " + targetType + ".");
                    valid = false;
                }
            }
            case ADD_ASSIGN, SUB_ASSIGN, MUL_ASSIGN, DIV_ASSIGN, MOD_ASSIGN -> {
                if (TypeRules.isNotCompoundAssignable(node.getOperator(), targetType, valueType)) {
                    report(node, node.getOperator() == AssignOperator.MOD_ASSIGN
                            ? "Modulo assignment requires int operands."
                            : "Arithmetic assignment requires numeric operands.");
                    valid = false;
                }
            }
            case BITWISE_OR_ASSIGN, BITWISE_AND_ASSIGN, BITWISE_XOR_ASSIGN,
                 LEFT_SHIFT_ASSIGN, RIGHT_SHIFT_ASSIGN -> {
                if (TypeRules.isNotCompoundAssignable(node.getOperator(), targetType, valueType)) {
                    report(node, "Bitwise/shift assignment requires integer operands.");
                    valid = false;
                }
            }
            case INVALID -> {
                report(node, "Invalid assignment operator.");
                valid = false;
            }
        }

        if (!valid) {
            node.setResultingType(UnknownTypeInfo.INSTANCE);
            return;
        }

        node.setResultingType(targetType);
    }

    private boolean isNotAssignableTarget(ExpressionAstNode target) {
        if (target instanceof IndexExpression indexExpression
                && PrimitiveTypeInfo.STRING.equals(indexExpression.getTarget().getResultingType())) {
            return true;
        }
        if (target instanceof MemberAccessExpression memberAccess
                && "length".equals(memberAccess.getMemberName())
                && (PrimitiveTypeInfo.STRING.equals(memberAccess.getTarget().getResultingType())
                || memberAccess.getTarget().getResultingType() instanceof ArrayTypeInfo)) {
            return true;
        }
        return !(target instanceof NameExpression)
                && !(target instanceof MemberAccessExpression)
                && !(target instanceof IndexExpression);
    }

    @Override
    public void visit(IfStatement node) {
        super.visit(node);
        checkConditionType(node.getCondition(), node, "If", false);
    }

    @Override
    public void visit(WhileStatement node) {
        super.visit(node);
        checkConditionType(node.getCondition(), node, "While", false);
    }

    @Override
    public void visit(DoWhileStatement node) {
        super.visit(node);
        checkConditionType(node.getCondition(), node, "Do-while", false);
    }

    @Override
    public void visit(ForStatement node) {
        super.visit(node);
        checkConditionType(node.getCondition(), node, "For", true);
    }

    @Override
    public void visit(SwitchStatement node) {
        super.visit(node);

        TypeInfo switchType = node.getCondition().getResultingType();
        if (!isSwitchType(switchType)) {
            report(node, "Switch expression type is not supported: " + switchType + ".");
            return;
        }

        boolean defaultSeen = false;
        Set<String> seenLabels = new HashSet<>();

        for (SwitchCase switchCase : node.getCases()) {
            if (switchCase.isDefault()) {
                if (defaultSeen) {
                    report(switchCase, "Switch may contain only one default case.");
                }
                defaultSeen = true;
                continue;
            }

            validateCaseLabels(switchCase, switchType, seenLabels);
        }
    }

    @Override
    public void visit(LiteralCaseLabel node) {
        node.getLiteral().accept(this);
    }

    private boolean isSwitchType(TypeInfo type) {
        return TypeRules.isSwitchType(type);
    }

    private void validateCaseLabels(SwitchCase switchCase, TypeInfo switchType, Set<String> seenLabels) {
        for (CaseLabelAstNode label : switchCase.getCaseLabels()) {
            TypeInfo labelType = caseLabelType(label);
            if (!isCaseLabelCompatible(switchType, labelType)) {
                report(label, "Case label type " + labelType + " does not exactly match switch type " + switchType + ".");
                continue;
            }

            String key = caseLabelKey(label, labelType);
            if (key != null && !seenLabels.add(key)) {
                report(label, "Duplicate switch case label: " + key + ".");
            }
        }
    }

    private TypeInfo caseLabelType(CaseLabelAstNode label) {
        if (label instanceof LiteralCaseLabel literalCaseLabel) {
            return literalCaseLabel.getLiteral().getResultingType();
        }
        if (label instanceof EnumCaseLabel enumCaseLabel && enumCaseLabel.getResolvedEnumValue() != null) {
            return new EnumTypeInfo(enumCaseLabel.getResolvedEnumValue().getOwnerEnum());
        }
        return UnknownTypeInfo.INSTANCE;
    }

    private boolean isCaseLabelCompatible(TypeInfo switchType, TypeInfo labelType) {
        if (switchType instanceof UnknownTypeInfo || labelType instanceof UnknownTypeInfo) {
            return true;
        }
        if (switchType instanceof NullTypeInfo) {
            return labelType instanceof NullTypeInfo;
        }
        if (labelType instanceof NullTypeInfo) {
            return TypeRules.isReferenceType(switchType);
        }
        return switchType.equals(labelType);
    }

    private String caseLabelKey(CaseLabelAstNode label, TypeInfo labelType) {
        if (label instanceof LiteralCaseLabel literalCaseLabel) {
            LiteralExpression<?> literal = literalCaseLabel.getLiteral();
            return labelType + ":" + literal.getKind() + ":" + literal.getValue();
        }
        if (label instanceof EnumCaseLabel enumCaseLabel && enumCaseLabel.getResolvedEnumValue() != null) {
            EnumValueEntry value = enumCaseLabel.getResolvedEnumValue();
            return "enum " + value.getOwnerEnum().getName() + ":" + value.getValue();
        }
        return null;
    }


    @Override
    public void visit(ConditionalExpression node) {
        super.visit(node);

        checkConditionType(node.getCondition(), node, "Conditional expression", false);

        TypeInfo trueType = node.getTrueExpression() == null
                ? UnknownTypeInfo.INSTANCE
                : node.getTrueExpression().getResultingType();
        TypeInfo falseType = node.getFalseExpression() == null
                ? UnknownTypeInfo.INSTANCE
                : node.getFalseExpression().getResultingType();

        TypeInfo result = TypeRules.conditionalResult(trueType, falseType);
        if (result instanceof UnknownTypeInfo && !(trueType instanceof UnknownTypeInfo) && !(falseType instanceof UnknownTypeInfo)) {
            report(node, "Conditional branches must have compatible types: " + trueType + " and " + falseType + ".");
        }
        node.setResultingType(result);
    }


    private boolean isConditionType(TypeInfo type) {
        return TypeRules.isConditionType(type);
    }

    private void checkConditionType(ExpressionAstNode condition, AstNode owner, String context, boolean allowMissingCondition) {
        if (condition == null) {
            if (!allowMissingCondition) {
                report(owner, context + " condition is missing.");
            }
            return;
        }

        TypeInfo conditionType = condition.getResultingType();
        if (!isConditionType(conditionType)) {
            report(owner, context + " condition must be boolean, but was: " + conditionType);
        }
    }


    @Override
    public void visit(CastExpression node) {
        super.visit(node);
        node.setResultingType(resolveType(node.getTargetType()));
    }

    @Override
    public void visit(UnaryExpression node) {
        super.visit(node);

        TypeInfo operandType = node.getOperand() == null
                ? UnknownTypeInfo.INSTANCE
                : node.getOperand().getResultingType();

        TypeInfo result = switch (node.getKind()) {
            case LOGICAL_NOT -> {
                if (!TypeRules.isConditionType(operandType)) {
                    report(node, "Logical not requires a boolean operand.");
                    yield UnknownTypeInfo.INSTANCE;
                }
                yield PrimitiveTypeInfo.BOOL;
            }

            case BITWISE_NOT -> {
                if (!TypeRules.isInteger(operandType) && !(operandType instanceof UnknownTypeInfo)) {
                    report(node, "Bitwise not requires integer operand.");
                    yield UnknownTypeInfo.INSTANCE;
                }
                yield PrimitiveTypeInfo.INT;
            }

            case MINUS, PLUS -> {
                if (!TypeRules.isNumeric(operandType) && !(operandType instanceof UnknownTypeInfo)) {
                    report(node, "Unary " + node.getKind() + " requires int or double operand.");
                    yield UnknownTypeInfo.INSTANCE;
                }
                yield operandType;
            }

            case PRE_INCREMENT, PRE_DECREMENT -> {
                if (!TypeRules.isNumeric(operandType) && !(operandType instanceof UnknownTypeInfo)) {
                    report(node, "Pre increment/decrement requires int or double operand.");
                    yield UnknownTypeInfo.INSTANCE;
                }
                if (isNotAssignableTarget(node.getOperand())) {
                    report(node, "Pre increment/decrement requires assignable operand.");
                    yield UnknownTypeInfo.INSTANCE;
                }
                yield operandType;
            }

            case INVALID -> {
                report(node, "Invalid unary operator.");
                yield UnknownTypeInfo.INSTANCE;
            }
        };

        node.setResultingType(result);
    }


    @Override
    public void visit(PostfixExpression node) {
        super.visit(node);

        TypeInfo operandType = node.getOperand() == null
                ? UnknownTypeInfo.INSTANCE
                : node.getOperand().getResultingType();

        TypeInfo result = switch (node.getKind()) {
            case INCREMENT, DECREMENT -> {
                if (!TypeRules.isNumeric(operandType) && !(operandType instanceof UnknownTypeInfo)) {
                    report(node, "Post increment/decrement requires int or double operand.");
                    yield UnknownTypeInfo.INSTANCE;
                }
                if (isNotAssignableTarget(node.getOperand())) {
                    report(node, "Post increment/decrement requires assignable operand.");
                    yield UnknownTypeInfo.INSTANCE;
                }
                yield operandType;
            }

            case INVALID -> {
                report(node, "Invalid postfix operator.");
                yield UnknownTypeInfo.INSTANCE;
            }
        };

        node.setResultingType(result);
    }

    @Override
    public void visit(IndexExpression node) {
        super.visit(node);

        TypeInfo targetType = node.getTarget().getResultingType();
        TypeInfo indexType = node.getIndex().getResultingType();

        if (targetType instanceof UnknownTypeInfo) {
            node.setResultingType(UnknownTypeInfo.INSTANCE);
            return;
        }

        TypeInfo elementType;
        if (targetType instanceof ArrayTypeInfo(TypeInfo arrayElementType)) {
            elementType = arrayElementType;
        } else if (PrimitiveTypeInfo.STRING.equals(targetType)) {
            elementType = PrimitiveTypeInfo.CHAR;
        } else {
            report(node, "Indexing is only allowed on arrays and strings.");
            node.setResultingType(UnknownTypeInfo.INSTANCE);
            return;
        }

        if (!PrimitiveTypeInfo.INT.equals(indexType) && !(indexType instanceof UnknownTypeInfo)) {
            report(node, "Index must be of type int.");
            node.setResultingType(UnknownTypeInfo.INSTANCE);
            return;
        }

        node.setResultingType(elementType);
    }


    @Override
    public void visit(MemberAccessExpression node) {
        super.visit(node);

        if (node.getResolvedEnumValue() != null) {
            node.setResultingType(new EnumTypeInfo(node.getResolvedEnumValue().getOwnerEnum()));
            return;
        }

        if (node.getResolvedField() != null) {
            node.setResultingType(node.getResolvedField().getType());
            return;
        }

        TypeInfo targetType = node.getTarget().getResultingType();
        if ("length".equals(node.getMemberName())
                && (targetType instanceof ArrayTypeInfo || PrimitiveTypeInfo.STRING.equals(targetType))) {
            node.setResultingType(PrimitiveTypeInfo.INT);
            return;
        }

        if (targetType instanceof StructTypeInfo(StructEntry structEntry) && structEntry != null) {
            FieldEntry field = structEntry.getScope().resolveField(node.getMemberName());
            if (field == null) {
                report(node, "Struct has no field: " + node.getMemberName());
                node.setResultingType(UnknownTypeInfo.INSTANCE);
                return;
            }
            node.setResolvedField(field);
            node.setResultingType(field.getType());
            return;
        }

        if (!(targetType instanceof UnknownTypeInfo)) {
            report(node, "Member access requires a struct value.");
        }
        node.setResultingType(UnknownTypeInfo.INSTANCE);
    }


    @Override
    public void visit(NewExpression node) {
        super.visit(node);
        TypeInfo type = resolveType(node.getType());

        boolean isArray = !node.getDimensions().isEmpty() || node.getArrayInit() != null;
        boolean isStruct = type instanceof StructTypeInfo;

        if (isArray) {
            for (ExpressionAstNode dimension : node.getDimensions()) {
                TypeInfo dimensionType = dimension.getResultingType();
                if (!PrimitiveTypeInfo.INT.equals(dimensionType) && !(dimensionType instanceof UnknownTypeInfo)) {
                    report(dimension, "Array dimension must be of type int.");
                }
            }
            TypeInfo arrayType = TypeRules.arrayType(type, node.getDimensions().size());
            if (node.getArrayInit() != null) {
                validateArrayInitializer(node.getArrayInit(), type, node.getDimensions(), 0);
            }
            node.setResultingType(arrayType);
            return;
        }

        if (!isStruct) {
            report(node, "'new' can only be used with struct types.");
            node.setResultingType(UnknownTypeInfo.INSTANCE);
            return;
        }

        node.setResultingType(type);
    }


    @Override
    public void visit(ArrayInitExpression node) {
        super.visit(node);

        boolean isEmpty = node.getElements().isEmpty();
        if (isEmpty) {
            node.setResultingType(new ArrayTypeInfo(UnknownTypeInfo.INSTANCE));
            return;
        }

        TypeInfo firstElementType = node.getElements().getFirst().getResultingType();
        boolean allSameType = true;

        for (ExpressionAstNode element : node.getElements()) {
            TypeInfo currentType = element.getResultingType();
            TypeInfo merged = TypeRules.conditionalResult(firstElementType, currentType);
            if (merged instanceof UnknownTypeInfo) {
                report(node, "All array elements must have the same type. Found: " + firstElementType + " and " + currentType);
                allSameType = false;
                break;
            }
            firstElementType = merged;
        }

        TypeInfo arrayType = allSameType ? firstElementType : UnknownTypeInfo.INSTANCE;
        node.setResultingType(new ArrayTypeInfo(arrayType));
    }

    private void validateArrayInitializer(
            ArrayInitExpression initializer,
            TypeInfo leafElementType,
            java.util.List<ExpressionAstNode> dimensions,
            int depth) {
        if (depth < dimensions.size()) {
            Integer staticLength = intLiteralValue(dimensions.get(depth));
            if (staticLength != null && initializer.getElements().size() != staticLength) {
                report(initializer, "Array initializer length " + initializer.getElements().size()
                        + " does not match dimension " + staticLength + ".");
            }
        }

        TypeInfo expectedElementType = TypeRules.arrayType(leafElementType, Math.max(0, dimensions.size() - depth - 1));
        for (ExpressionAstNode element : initializer.getElements()) {
            if (element instanceof ArrayInitExpression nested && depth + 1 < dimensions.size()) {
                validateArrayInitializer(nested, leafElementType, dimensions, depth + 1);
                nested.setResultingType(expectedElementType);
                continue;
            }
            TypeInfo actualType = element.getResultingType();
            if (!TypeRules.isAssignable(expectedElementType, actualType)) {
                report(element, "Array initializer element type mismatch. Expected: "
                        + expectedElementType + ", actual: " + actualType + ".");
            }
        }
    }

    private Integer intLiteralValue(ExpressionAstNode expression) {
        if (expression instanceof LiteralExpression<?> literal
                && literal.getKind() == LiteralKind.INT
                && literal.getValue() instanceof Integer value) {
            return value;
        }
        return null;
    }

    @Override
    protected TypeInfo resolveNamedType(NamedType namedType) {
        SymbolEntry resolvedEntry = namedType.getResolvedEntry();
        if (resolvedEntry instanceof StructEntry structEntry) {
            return new StructTypeInfo(structEntry);
        }
        if (resolvedEntry instanceof EnumEntry enumEntry) {
            return new EnumTypeInfo(enumEntry);
        }

        if (globalScope == null) {
            return UnknownTypeInfo.INSTANCE;
        }

        return switch (namedType.getKind()) {
            case STRUCT -> {
                StructEntry entry = globalScope.resolveStruct(namedType.getName());
                yield entry == null ? UnknownTypeInfo.INSTANCE : new StructTypeInfo(entry);
            }
            case ENUM -> {
                EnumEntry entry = globalScope.resolveEnum(namedType.getName());
                yield entry == null ? UnknownTypeInfo.INSTANCE : new EnumTypeInfo(entry);
            }
            case INVALID -> UnknownTypeInfo.INSTANCE;
        };
    }
}
