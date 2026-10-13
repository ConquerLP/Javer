package ch.zhaw.it.pm4.javer.compiler.ir;

import ch.zhaw.it.pm4.javer.compiler.diagnostics.CompilerInvariantException;
import ch.zhaw.it.pm4.javer.compiler.source.SourceRange;
import java.util.*;

/** Checks identities, storage ownership and normalized operand types at the IR boundary. */
public final class IrVerifier {
    private final Map<Integer, Ir.Signature> signatures = new HashMap<>();
    private final Map<Integer, Ir.Struct> structs = new HashMap<>();
    private final Map<Integer, Ir.Enumeration> enums = new HashMap<>();
    private final Map<Integer, Ir.Storage> fields = new HashMap<>();
    private final Map<Integer, Integer> fieldOwners = new HashMap<>();
    private Map<Integer, Ir.Storage> frame;
    private Ir.Type returnType;

    public void verify(Ir.Program program) {
        require(program.semantics().equals(Ir.Semantics.JAVER), SourceRange.UNKNOWN, "Unknown language semantics");
        for (Ir.Signature signature : program.signatures()) unique(signatures, signature.id(), signature, SourceRange.UNKNOWN);
        for (Ir.Struct struct : program.structs()) {
            unique(structs, struct.id(), struct, struct.source());
            for (Ir.Storage field : struct.fields()) {
                unique(fields, field.id(), field, field.source());
                fieldOwners.put(field.id(), struct.id());
            }
        }
        for (Ir.Enumeration enumeration : program.enums()) unique(enums, enumeration.id(), enumeration, enumeration.source());
        for (Ir.Signature signature : program.signatures()) {
            verifyType(signature.returnType(), SourceRange.UNKNOWN);
            require(signature.returnType().kind() != Ir.Kind.ADDRESS && signature.returnType().kind() != Ir.Kind.NULL,
                    SourceRange.UNKNOWN, "Invalid function return type");
            for (Ir.Type parameter : signature.parameters()) {
                verifyType(parameter, SourceRange.UNKNOWN);
                require(parameter.kind() != Ir.Kind.VOID && parameter.kind() != Ir.Kind.NULL && parameter.kind() != Ir.Kind.ADDRESS,
                        SourceRange.UNKNOWN, "Invalid function parameter type");
            }
        }
        Set<Integer> definitions = new HashSet<>();
        for (Ir.Function function : program.functions()) {
            require(definitions.add(function.signature().id()), function.source(), "Duplicate function definition");
            require(function.signature().equals(signatures.get(function.signature().id())) && !function.signature().builtIn(), function.source(), "Invalid function signature");
            frame = new HashMap<>();
            for (Ir.Storage parameter : function.parameters()) unique(frame, parameter.id(), parameter, parameter.source());
            for (Ir.Storage local : function.locals()) unique(frame, local.id(), local, local.source());
            require(function.parameters().stream().map(Ir.Storage::type).toList().equals(function.signature().parameters()), function.source(), "Parameter signature mismatch");
            frame.values().forEach(slot -> require(slot.type().kind() != Ir.Kind.VOID && slot.type().kind() != Ir.Kind.NULL, slot.source(), "Invalid storage type"));
            returnType = function.signature().returnType();
            IrWalker.walk(function.body(), this::verifyNode);
            controlFlow(function.body(), 0);
        }
        for (Ir.Signature signature : signatures.values()) require(signature.builtIn() || definitions.contains(signature.id()), SourceRange.UNKNOWN, "Function has no definition");
    }

    private void verifyNode(Ir.Node node) {
        SourceRange source = node.source();
        require(source != null, SourceRange.UNKNOWN, "IR node has no source range");
        if (node instanceof Ir.Expression expression) verifyType(expression.type(), source);
        switch (node) {
            case Ir.LocalAddress local -> {
                Ir.Storage slot = frame.get(local.storageId());
                require(slot != null && local.type().equals(Ir.Type.address(slot.type())), source, "Unknown or mistyped frame slot");
            }
            case Ir.Load load -> address(load.address(), load.type(), source);
            case Ir.Store store -> address(store.address(), store.value().type(), source);
            case Ir.Call call -> {
                Ir.Signature signature = signatures.get(call.functionId());
                require(signature != null, source, "Unresolved IR call");
                require(signature.returnType().equals(call.type()) && signature.parameters().equals(call.arguments().stream().map(Ir.Expression::type).toList()), source, "Call signature mismatch");
            }
            case Ir.IndexAddress index -> {
                require(index.index().type().equals(Ir.Type.INT), source, "Index is not int");
                Ir.Type target = index.target().type();
                require(target.kind() == Ir.Kind.ARRAY || target.kind() == Ir.Kind.STRING, source, "Cannot index target");
                Ir.Type element = target.kind() == Ir.Kind.STRING ? Ir.Type.CHAR : target.element();
                require(index.type().equals(Ir.Type.address(element)), source, "Index address type mismatch");
            }
            case Ir.FieldAddress field -> {
                Ir.Storage slot = fields.get(field.fieldId());
                require(slot != null && field.target().type().kind() == Ir.Kind.STRUCT
                        && fieldOwners.get(field.fieldId()).equals(field.target().type().nominalId())
                        && field.type().equals(Ir.Type.address(slot.type())), source, "Field owner/type mismatch");
            }
            case Ir.EnumConstant constant -> require(enums.containsKey(constant.enumId()) && constant.type().kind() == Ir.Kind.ENUM
                    && constant.type().nominalId() == constant.enumId()
                    && enums.get(constant.enumId()).values().stream().anyMatch(v -> v.id() == constant.valueId()), source, "Unknown enum constant");
            case Ir.StructNew allocation -> require(allocation.type().kind() == Ir.Kind.STRUCT && allocation.type().nominalId() == allocation.structId()
                    && structs.containsKey(allocation.structId()), source, "Unknown struct allocation");
            case Ir.ArrayNew allocation -> {
                require(allocation.type().kind() == Ir.Kind.ARRAY, source, "Array allocation type mismatch");
                allocation.dimensions().forEach(d -> require(d.type().equals(Ir.Type.INT), source, "Array dimension is not int"));
                allocation.elements().forEach(e -> require(e.type().equals(allocation.type().element()), e.source(), "Array element type mismatch"));
                require(allocation.initialized() || !allocation.dimensions().isEmpty(), source, "Array allocation lacks dimensions");
            }
            case Ir.Binary binary -> {
                require(binary.left().type().equals(binary.right().type()), source, "Binary operands have implicit conversions");
                Ir.Type operand = binary.left().type();
                switch (binary.op()) {
                    case ADD, SUBTRACT, MULTIPLY, DIVIDE -> require(operand.numeric() && binary.type().equals(operand), source, "Numeric operation type mismatch");
                    case MODULO, BITWISE_AND, BITWISE_OR, BITWISE_XOR, SHIFT_LEFT, SHIFT_RIGHT -> require(operand.equals(Ir.Type.INT) && binary.type().equals(Ir.Type.INT), source, "Integer operation type mismatch");
                    case EQUALS, NOT_EQUALS, LESS, LESS_EQUALS, GREATER, GREATER_EQUALS -> require(binary.type().equals(Ir.Type.BOOL), source, "Comparison must return bool");
                }
            }
            case Ir.Conditional branch -> {
                bool(branch.condition());
                require(branch.type().equals(branch.whenTrue().type()) && branch.type().equals(branch.whenFalse().type()), source, "Conditional branches have implicit conversions");
            }
            case Ir.If branch -> bool(branch.condition());
            case Ir.While loop -> bool(loop.condition());
            case Ir.DoWhile loop -> bool(loop.condition());
            case Ir.For loop -> bool(loop.condition());
            case Ir.Return returning -> require(returning.value() == null ? returnType.equals(Ir.Type.VOID) : returning.value().type().equals(returnType), source, "Return type mismatch");
            case Ir.Sequence sequence -> require(sequence.type().equals(sequence.value().type()), source, "Sequence result mismatch");
            case Ir.Length length -> require(length.type().equals(Ir.Type.INT) && (length.target().type().kind() == Ir.Kind.ARRAY
                    || length.target().type().kind() == Ir.Kind.STRING), source, "Length type mismatch");
            case Ir.Convert conversion -> {
                if (conversion.conversion() == Ir.Conversion.NUMERIC) require(conversion.type().numeric() && conversion.operand().type().numeric(), source, "Non-numeric conversion");
                if (conversion.conversion() == Ir.Conversion.BOOLEAN) bool(conversion);
                if (conversion.conversion() == Ir.Conversion.REFERENCE) require(conversion.type().reference() && conversion.operand().type().reference(), source, "Invalid reference conversion");
            }
            case Ir.Unary unary -> {
                switch (unary.op()) {
                    case NEGATE -> require(unary.type().numeric() && unary.type().equals(unary.operand().type()), source, "Unary numeric type mismatch");
                    case BITWISE_NOT -> require(unary.type().equals(Ir.Type.INT) && unary.operand().type().equals(Ir.Type.INT), source, "Unary integer type mismatch");
                    case LOGICAL_NOT -> { bool(unary); bool(unary.operand()); }
                }
            }
            case Ir.Literal literal -> require(literal.value() == null ? literal.type().reference()
                    : switch (literal.type().kind()) {
                        case INT -> literal.value() instanceof Integer; case DOUBLE -> literal.value() instanceof Double;
                        case BOOL -> literal.value() instanceof Boolean; case CHAR -> literal.value() instanceof Character;
                        case STRING -> literal.value() instanceof String; default -> false;
                    }, source, "Literal value/type mismatch");
            case Ir.Block ignored -> { }
            case Ir.Evaluate ignored -> { }
            case Ir.Switch selection -> {
                require(selection.cases().stream().filter(Ir.Case::isDefault).count() <= 1, source, "Duplicate switch default");
                selection.cases().forEach(branch -> branch.labels().forEach(label -> require(label.type().equals(selection.condition().type()), label.source(), "Switch label has implicit conversion")));
            }
            case Ir.Case ignored -> { }
            case Ir.Break ignored -> { }
            case Ir.Continue ignored -> { }
        }
    }

    private void controlFlow(Ir.Statement statement, int loopDepth) {
        switch (statement) {
            case Ir.Block block -> block.statements().forEach(s -> controlFlow(s, loopDepth));
            case Ir.If branch -> { controlFlow(branch.thenBranch(), loopDepth); controlFlow(branch.elseBranch(), loopDepth); }
            case Ir.While loop -> controlFlow(loop.body(), loopDepth + 1);
            case Ir.DoWhile loop -> controlFlow(loop.body(), loopDepth + 1);
            case Ir.For loop -> { controlFlow(loop.init(), loopDepth); controlFlow(loop.update(), loopDepth); controlFlow(loop.body(), loopDepth + 1); }
            case Ir.Switch selection -> selection.cases().forEach(c -> controlFlow(c.body(), loopDepth));
            case Ir.Break exit -> require(loopDepth > 0, exit.source(), "Break has no owning loop");
            case Ir.Continue exit -> require(loopDepth > 0, exit.source(), "Continue has no owning loop");
            case Ir.Store ignored -> { }
            case Ir.Evaluate ignored -> { }
            case Ir.Return ignored -> { }
        }
    }
    private void verifyType(Ir.Type type, SourceRange source) {
        if (type.kind() == Ir.Kind.STRUCT) require(structs.containsKey(type.nominalId()), source, "Unknown nominal struct type");
        if (type.kind() == Ir.Kind.ENUM) require(enums.containsKey(type.nominalId()), source, "Unknown nominal enum type");
        if (type.element() != null) verifyType(type.element(), source);
    }
    private void address(Ir.Expression expression, Ir.Type value, SourceRange source) {
        require(expression.type().equals(Ir.Type.address(value)), source, "Store/load address type mismatch");
    }
    private void bool(Ir.Expression expression) { require(expression.type().equals(Ir.Type.BOOL), expression.source(), "Condition is not bool"); }
    private <T> void unique(Map<Integer, T> map, int id, T value, SourceRange source) { require(map.putIfAbsent(id, value) == null, source, "Duplicate IR identity"); }
    private void require(boolean condition, SourceRange source, String message) {
        if (!condition) throw new CompilerInvariantException(source, message);
    }
}
