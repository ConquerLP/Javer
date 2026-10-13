package ch.zhaw.it.pm4.javer.compiler.frontend.analysis;

import ch.zhaw.it.pm4.javer.compiler.diagnostics.CompilerInvariantException;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.*;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.case_label.*;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.declaration.*;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.statement.*;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.symbol.*;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.typeinfo.*;
import ch.zhaw.it.pm4.javer.compiler.ir.Ir;
import ch.zhaw.it.pm4.javer.compiler.source.SourceRange;
import java.util.*;

/** The single language-to-IR boundary. Copies semantic data and normalizes evaluation,
 * casts, initialization, variadic arguments and read/modify/write expressions. */
public final class IrLowering {
    private final Map<SymbolEntry, Integer> ids = new IdentityHashMap<>();
    private final Map<StorageEntry, Ir.Storage> storage = new IdentityHashMap<>();
    private final Map<FunctionEntry, Ir.Signature> signatures = new IdentityHashMap<>();
    private List<Ir.Storage> locals;
    private FunctionEntry currentFunction;
    private int nextId;

    public Ir.Program lower(CompilationUnit root) {
        // Assign nominal identities before resolving recursive struct/array types.
        for (DeclarationAstNode declaration : root.getDeclarations()) {
            if (declaration instanceof StructDeclaration node) id(node.getSymbolEntry());
            if (declaration instanceof EnumDeclaration node) id(node.getSymbolEntry());
        }
        List<Ir.Struct> structs = new ArrayList<>();
        List<Ir.Enumeration> enums = new ArrayList<>();
        for (DeclarationAstNode declaration : root.getDeclarations()) {
            if (declaration instanceof StructDeclaration node) {
                List<Ir.Storage> fields = new ArrayList<>();
                for (FieldEntry field : node.getStructScope().getFields().values()) {
                    fields.add(define(field, Ir.StorageKind.FIELD, node.getSourceRange()));
                }
                structs.add(new Ir.Struct(id(node.getSymbolEntry()), node.getName(), fields, node.getSourceRange()));
            } else if (declaration instanceof EnumDeclaration node) {
                List<Ir.EnumValue> values = node.getEnumScope().getValues().values().stream()
                        .map(v -> new Ir.EnumValue(id(v), v.getName(), v.getValue(), node.getSourceRange())).toList();
                enums.add(new Ir.Enumeration(id(node.getSymbolEntry()), node.getName(), values, node.getSourceRange()));
            }
        }
        List<Ir.Signature> allSignatures = new ArrayList<>();
        for (FunctionEntry function : root.getGlobalScope().getFunctions().values()) {
            Ir.Signature signature = new Ir.Signature(id(function), function.getName(), type(function.getReturnType()),
                    function.getScope().getParameters().values().stream().map(p -> type(p.getType())).toList(), function.isBuiltIn());
            signatures.put(function, signature);
            allSignatures.add(signature);
        }
        List<Ir.Function> functions = new ArrayList<>();
        for (DeclarationAstNode declaration : root.getDeclarations()) {
            if (declaration instanceof FunctionDeclaration node) {
                currentFunction = node.getSymbolEntry();
                locals = new ArrayList<>();
                List<Ir.Storage> parameters = currentFunction.getScope().getParameters().values().stream()
                        .map(p -> define(p, Ir.StorageKind.PARAMETER, node.getSourceRange())).toList();
                Ir.Block body = (Ir.Block) statement(node.getBody());
                functions.add(new Ir.Function(signatures.get(currentFunction), parameters, locals, body, node.getSourceRange()));
            }
        }
        return new Ir.Program(Ir.Semantics.JAVER, allSignatures, functions, structs, enums);
    }

    private int id(SymbolEntry symbol) {
        if (symbol == null) throw broken(SourceRange.UNKNOWN, "Resolved symbol required at lowering boundary");
        return ids.computeIfAbsent(symbol, ignored -> nextId++);
    }

    private Ir.Type type(TypeInfo type) {
        return switch (type) {
            case PrimitiveTypeInfo primitive -> switch (primitive.kind()) {
                case BOOL -> Ir.Type.BOOL;
                case CHAR -> Ir.Type.CHAR;
                case INT -> Ir.Type.INT;
                case DOUBLE -> Ir.Type.DOUBLE;
                case STRING -> Ir.Type.STRING;
                case INVALID -> throw broken(SourceRange.UNKNOWN, "Invalid primitive type after checking");
            };
            case ArrayTypeInfo array -> Ir.Type.array(type(array.elementType()));
            case StructTypeInfo struct -> new Ir.Type(Ir.Kind.STRUCT, id(struct.entry()), null);
            case EnumTypeInfo enumeration -> new Ir.Type(Ir.Kind.ENUM, id(enumeration.entry()), null);
            case NullTypeInfo ignored -> Ir.Type.NULL;
            case VoidTypeInfo ignored -> Ir.Type.VOID;
            case UnknownTypeInfo ignored -> throw broken(SourceRange.UNKNOWN, "Unresolved type after checking");
        };
    }

    private Ir.Storage define(StorageEntry entry, Ir.StorageKind kind, SourceRange source) {
        return storage.computeIfAbsent(entry, ignored -> new Ir.Storage(id(entry), entry.getName(), type(entry.getType()), kind, source));
    }

    private Ir.Statement statement(StatementAstNode node) {
        SourceRange source = node.getSourceRange();
        return switch (node) {
            case ExpressionAstNode expression -> new Ir.Evaluate(expression(expression), source);
            case BlockStatement block -> new Ir.Block(block.getStatements().stream().map(this::statement).toList(), source);
            case VarDeclarationStatement variable -> {
                Ir.Storage slot = define(variable.getSymbolEntry(), Ir.StorageKind.LOCAL, source);
                locals.add(slot);
                Ir.Expression value = variable.getInitializer() == null ? defaultValue(slot.type(), source)
                        : convert(expression(variable.getInitializer()), slot.type());
                yield new Ir.Store(address(slot, source), value, source);
            }
            case IfStatement conditional -> new Ir.If(booleanValue(expression(conditional.getCondition())),
                    statement(conditional.getThenBranch()), conditional.getElseBranch() == null ? empty(source) : statement(conditional.getElseBranch()), source);
            case WhileStatement loop -> new Ir.While(booleanValue(expression(loop.getCondition())), statement(loop.getBody()), source);
            case DoWhileStatement loop -> new Ir.DoWhile(statement(loop.getBody()), booleanValue(expression(loop.getCondition())), source);
            case ForStatement loop -> {
                Ir.Block init = switch (loop.getForInit()) {
                    case null -> empty(source);
                    case ForInitVarDeclaration variable -> new Ir.Block(List.of(statement(variable.getVarDeclaration())), source);
                    case ForInitExpressionList expressions -> new Ir.Block(expressions.getExpressions().stream()
                            .map(e -> (Ir.Statement) new Ir.Evaluate(expression(e), e.getSourceRange())).toList(), source);
                };
                Ir.Block update = new Ir.Block(loop.getUpdate() == null ? List.of() : loop.getUpdate().stream()
                        .map(e -> (Ir.Statement) new Ir.Evaluate(expression(e), e.getSourceRange())).toList(), source);
                yield new Ir.For(init, loop.getCondition() == null ? new Ir.Literal(true, Ir.Type.BOOL, source)
                        : booleanValue(expression(loop.getCondition())), update, statement(loop.getBody()), source);
            }
            case SwitchStatement selection -> {
                TypeInfo common = selection.getCondition().getResultingType();
                for (SwitchCase branch : selection.getCases()) {
                    for (CaseLabelAstNode label : branch.getCaseLabels()) {
                        TypeInfo labelType = label instanceof LiteralCaseLabel literal ? literal.getLiteral().getResultingType()
                                : new EnumTypeInfo(((EnumCaseLabel) label).getResolvedEnumValue().getOwnerEnum());
                        common = TypeRules.equalityOperandType(common, labelType);
                    }
                }
                Ir.Type comparisonType = type(common);
                yield new Ir.Switch(convert(expression(selection.getCondition()), comparisonType), selection.getCases().stream()
                        .map(c -> new Ir.Case(c.getCaseLabels().stream().map(label -> convert(caseLabel(label), comparisonType)).toList(),
                                c.isDefault(), statement(c.getStatement()), c.getSourceRange())).toList(), source);
            }
            case BreakStatement ignored -> new Ir.Break(source);
            case ContinueStatement ignored -> new Ir.Continue(source);
            case ReturnStatement returning -> new Ir.Return(returning.getExpression() == null ? null
                    : convert(expression(returning.getExpression()), type(currentFunction.getReturnType())), source);
            default -> throw broken(source, "Unhandled statement: " + node.getClass().getSimpleName());
        };
    }

    private Ir.Expression caseLabel(CaseLabelAstNode label) {
        return switch (label) {
            case LiteralCaseLabel literal -> expression(literal.getLiteral());
            case EnumCaseLabel enumeration -> enumConstant(enumeration.getResolvedEnumValue(), label.getSourceRange());
        };
    }

    private Ir.Expression expression(ExpressionAstNode node) {
        SourceRange source = node.getSourceRange();
        Ir.Type result = type(node.getResultingType());
        return switch (node) {
            case LiteralExpression<?> literal -> new Ir.Literal(literal.getValue(), result, source);
            case NameExpression name -> name.getSymbolEntry() instanceof EnumValueEntry enumeration
                    ? enumConstant(enumeration, source) : new Ir.Load(lvalue(name), result, source);
            case IndexExpression index -> new Ir.Load(lvalue(index), result, source);
            case MemberAccessExpression member -> {
                if (member.getResolvedEnumValue() != null) yield enumConstant(member.getResolvedEnumValue(), source);
                if (member.getResolvedField() == null && "length".equals(member.getMemberName())) {
                    yield new Ir.Length(expression(member.getTarget()), Ir.Type.INT, source);
                }
                yield new Ir.Load(lvalue(member), result, source);
            }
            case AssignExpression assignment -> assignment(assignment);
            case UnaryExpression unary -> switch (unary.getKind()) {
                case PLUS -> expression(unary.getOperand());
                case MINUS -> new Ir.Unary(Ir.UnaryOp.NEGATE, expression(unary.getOperand()), result, source);
                case BITWISE_NOT -> new Ir.Unary(Ir.UnaryOp.BITWISE_NOT, expression(unary.getOperand()), result, source);
                case LOGICAL_NOT -> new Ir.Unary(Ir.UnaryOp.LOGICAL_NOT, booleanValue(expression(unary.getOperand())), result, source);
                case PRE_INCREMENT -> step(unary.getOperand(), 1, false, source);
                case PRE_DECREMENT -> step(unary.getOperand(), -1, false, source);
                case INVALID -> throw broken(source, "Invalid unary operator");
            };
            case PostfixExpression postfix -> switch (postfix.getKind()) {
                case INCREMENT -> step(postfix.getOperand(), 1, true, source);
                case DECREMENT -> step(postfix.getOperand(), -1, true, source);
                case INVALID -> throw broken(source, "Invalid postfix operator");
            };
            case BinaryExpression binary -> binary(binary);
            case ConditionalExpression conditional -> new Ir.Conditional(booleanValue(expression(conditional.getCondition())),
                    convert(expression(conditional.getTrueExpression()), result), convert(expression(conditional.getFalseExpression()), result), result, source);
            case CastExpression cast -> new Ir.Convert(Ir.Conversion.CAST, expression(cast.getOperand()), result, source);
            case CallExpression call -> call(call);
            case NewExpression allocation -> {
                if (result.kind() == Ir.Kind.STRUCT) yield new Ir.StructNew(result.nominalId(), result, source);
                List<Ir.Expression> dimensions = allocation.getDimensions().stream().map(e -> convert(expression(e), Ir.Type.INT)).toList();
                ArrayInitExpression init = allocation.getArrayInit();
                yield new Ir.ArrayNew(dimensions, init == null ? List.of() : elements(init.getElements(), result.element()), init != null, result, source);
            }
            case ArrayInitExpression array -> new Ir.ArrayNew(List.of(), elements(array.getElements(), result.element()), true, result, source);
            default -> throw broken(source, "Unhandled expression: " + node.getClass().getSimpleName());
        };
    }

    private List<Ir.Expression> elements(List<ExpressionAstNode> elements, Ir.Type elementType) {
        return elements.stream().map(e -> convert(expression(e), elementType)).toList();
    }

    private Ir.Expression call(CallExpression node) {
        FunctionEntry function = node.getResolvedFunction();
        List<ParameterEntry> parameters = new ArrayList<>(function.getScope().getParameters().values());
        List<Ir.Expression> arguments = new ArrayList<>();
        int fixed = function.isVariadic() ? parameters.size() - 1 : parameters.size();
        for (int i = 0; i < fixed; i++) arguments.add(convert(expression(node.getArguments().get(i)), type(parameters.get(i).getType())));
        if (function.isVariadic()) {
            ParameterEntry parameter = parameters.getLast();
            if (node.getArguments().size() == parameters.size() && TypeRules.isAssignable(parameter.getType(), node.getArguments().get(fixed).getResultingType())) {
                arguments.add(convert(expression(node.getArguments().get(fixed)), type(parameter.getType())));
            } else {
                arguments.add(new Ir.ArrayNew(List.of(), elements(node.getArguments().subList(fixed, node.getArguments().size()),
                        type(parameter.getVariadicElementType())), true, type(parameter.getType()), node.getSourceRange()));
            }
        }
        return new Ir.Call(id(function), arguments, type(function.getReturnType()), node.getSourceRange());
    }

    private Ir.Expression binary(BinaryExpression node) {
        Ir.Expression left = expression(node.getLeft()), right = expression(node.getRight());
        SourceRange source = node.getSourceRange();
        if (node.getOperator() == BinaryExpressionKind.AND) {
            return new Ir.Conditional(booleanValue(left), booleanValue(right), new Ir.Literal(false, Ir.Type.BOOL, source), Ir.Type.BOOL, source);
        }
        if (node.getOperator() == BinaryExpressionKind.OR) {
            return new Ir.Conditional(booleanValue(left), new Ir.Literal(true, Ir.Type.BOOL, source), booleanValue(right), Ir.Type.BOOL, source);
        }
        Ir.BinaryOp op = Ir.BinaryOp.valueOf(node.getOperator().name());
        Ir.Type operand;
        if (op == Ir.BinaryOp.EQUALS || op == Ir.BinaryOp.NOT_EQUALS) {
            operand = type(TypeRules.equalityOperandType(node.getLeft().getResultingType(), node.getRight().getResultingType()));
        } else if (left.type().equals(Ir.Type.CHAR) && right.type().equals(Ir.Type.CHAR)) {
            operand = Ir.Type.CHAR;
        } else {
            operand = left.type().equals(Ir.Type.DOUBLE) || right.type().equals(Ir.Type.DOUBLE) ? Ir.Type.DOUBLE : Ir.Type.INT;
        }
        return new Ir.Binary(op, convert(left, operand), convert(right, operand), type(node.getResultingType()), source);
    }

    private Ir.Expression lvalue(ExpressionAstNode node) {
        Ir.Type valueType = type(node.getResultingType());
        SourceRange source = node.getSourceRange();
        return switch (node) {
            case NameExpression name when name.getSymbolEntry() instanceof StorageEntry slot -> {
                Ir.Storage defined = storage.get(slot);
                if (defined == null) throw broken(source, "Storage used before its declaration");
                yield address(defined, source);
            }
            case IndexExpression index -> new Ir.IndexAddress(expression(index.getTarget()), convert(expression(index.getIndex()), Ir.Type.INT), Ir.Type.address(valueType), source);
            case MemberAccessExpression member when member.getResolvedField() != null -> new Ir.FieldAddress(expression(member.getTarget()), id(member.getResolvedField()), Ir.Type.address(valueType), source);
            default -> throw broken(source, "Not an assignable target");
        };
    }

    private Ir.Expression assignment(AssignExpression node) {
        SourceRange source = node.getSourceRange();
        List<Ir.Statement> effects = new ArrayList<>();
        Ir.Expression captured = capture(lvalue(node.getTarget()), effects);
        Ir.Type target = captured.type().element();
        Ir.Expression value;
        if (node.getOperator() == AssignOperator.ASSIGN) {
            value = convert(expression(node.getValue()), target);
        } else {
            Ir.Type calculation = type(TypeRules.compoundCalculationType(node.getOperator(), node.getTarget().getResultingType(), node.getValue().getResultingType()));
            Ir.Expression previous = convert(new Ir.Load(captured, target, source), calculation);
            Ir.BinaryOp op = switch (node.getOperator()) {
                case ADD_ASSIGN -> Ir.BinaryOp.ADD; case SUB_ASSIGN -> Ir.BinaryOp.SUBTRACT;
                case MUL_ASSIGN -> Ir.BinaryOp.MULTIPLY; case DIV_ASSIGN -> Ir.BinaryOp.DIVIDE;
                case MOD_ASSIGN -> Ir.BinaryOp.MODULO; case BITWISE_AND_ASSIGN -> Ir.BinaryOp.BITWISE_AND;
                case BITWISE_OR_ASSIGN -> Ir.BinaryOp.BITWISE_OR; case BITWISE_XOR_ASSIGN -> Ir.BinaryOp.BITWISE_XOR;
                case LEFT_SHIFT_ASSIGN -> Ir.BinaryOp.SHIFT_LEFT; case RIGHT_SHIFT_ASSIGN -> Ir.BinaryOp.SHIFT_RIGHT;
                default -> throw broken(source, "Invalid compound assignment");
            };
            value = convert(new Ir.Binary(op, previous, convert(expression(node.getValue()), calculation), calculation, source), target);
        }
        // Capture the result before the store so assignment produces its value without reloading/mutating the target.
        Ir.Expression result = capture(value, effects);
        effects.add(new Ir.Store(captured, result, source));
        return new Ir.Sequence(new Ir.Block(effects, source), result, target, source);
    }

    private Ir.Expression step(ExpressionAstNode target, int delta, boolean postfix, SourceRange source) {
        List<Ir.Statement> effects = new ArrayList<>();
        Ir.Expression captured = capture(lvalue(target), effects);
        Ir.Type valueType = captured.type().element();
        Ir.Expression oldValue = capture(new Ir.Load(captured, valueType, source), effects);
        Object amount = valueType.equals(Ir.Type.DOUBLE) ? (Object) (double) delta : delta;
        Ir.Expression newValue = capture(new Ir.Binary(Ir.BinaryOp.ADD, oldValue, new Ir.Literal(amount, valueType, source), valueType, source), effects);
        effects.add(new Ir.Store(captured, newValue, source));
        return new Ir.Sequence(new Ir.Block(effects, source), postfix ? oldValue : newValue, valueType, source);
    }

    private Ir.Expression capture(Ir.Expression value, List<Ir.Statement> effects) {
        Ir.Storage temporary = new Ir.Storage(nextId++, "$temporary" + locals.size(), value.type(), Ir.StorageKind.TEMPORARY, value.source());
        locals.add(temporary);
        Ir.Expression address = address(temporary, value.source());
        effects.add(new Ir.Store(address, value, value.source()));
        return new Ir.Load(address, value.type(), value.source());
    }

    private Ir.Expression enumConstant(EnumValueEntry value, SourceRange source) {
        return new Ir.EnumConstant(id(value.getOwnerEnum()), id(value), type(new EnumTypeInfo(value.getOwnerEnum())), source);
    }
    private Ir.Expression address(Ir.Storage slot, SourceRange source) { return new Ir.LocalAddress(slot.id(), Ir.Type.address(slot.type()), source); }
    private Ir.Block empty(SourceRange source) { return new Ir.Block(List.of(), source); }
    private Ir.Expression defaultValue(Ir.Type type, SourceRange source) {
        Object value = switch (type.kind()) {
            case BOOL -> false; case CHAR -> '\0'; case INT -> 0; case DOUBLE -> 0.0;
            case STRING, ARRAY, STRUCT, ENUM -> null;
            default -> throw broken(source, "No language default for " + type);
        };
        return new Ir.Literal(value, type, source);
    }
    private Ir.Expression booleanValue(Ir.Expression value) {
        return value.type().equals(Ir.Type.BOOL) ? value : new Ir.Convert(Ir.Conversion.BOOLEAN, value, Ir.Type.BOOL, value.source());
    }
    private Ir.Expression convert(Ir.Expression value, Ir.Type expected) {
        if (value.type().equals(expected)) return value;
        Ir.Conversion conversion = value.type().numeric() && expected.numeric() ? Ir.Conversion.NUMERIC : Ir.Conversion.REFERENCE;
        return new Ir.Convert(conversion, value, expected, value.source());
    }
    private CompilerInvariantException broken(SourceRange source, String message) { return new CompilerInvariantException(source, message); }
}
