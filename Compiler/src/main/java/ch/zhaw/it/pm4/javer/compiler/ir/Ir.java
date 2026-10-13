package ch.zhaw.it.pm4.javer.compiler.ir;

import ch.zhaw.it.pm4.javer.compiler.source.SourceRange;
import java.util.List;
import java.util.Objects;

/** Immutable, typed, structured IR. Operands and arguments evaluate left to right.
 * Sequences explicitly bind side effects; stores execute whenever reached.
 * No frontend node, mutable symbol, target address, or ABI layout is retained. */
public final class Ir {
    private Ir() { }

    public enum Kind { VOID, BOOL, CHAR, INT, DOUBLE, STRING, NULL, ARRAY, STRUCT, ENUM, ADDRESS }
    public record Type(Kind kind, int nominalId, Type element) {
        public Type {
            Objects.requireNonNull(kind);
            if ((kind == Kind.STRUCT || kind == Kind.ENUM) != (nominalId >= 0)) {
                throw new IllegalArgumentException("Invalid nominal identity for " + kind);
            }
            if (element != null && (element.kind() == Kind.VOID || (kind == Kind.ARRAY && (element.kind() == Kind.NULL || element.kind() == Kind.ADDRESS)))) {
                throw new IllegalArgumentException("Invalid contained type for " + kind);
            }
            if ((kind == Kind.ARRAY || kind == Kind.ADDRESS) != (element != null)) {
                throw new IllegalArgumentException("Invalid element type for " + kind);
            }
        }
        public static final Type VOID = scalar(Kind.VOID), BOOL = scalar(Kind.BOOL),
                CHAR = scalar(Kind.CHAR), INT = scalar(Kind.INT), DOUBLE = scalar(Kind.DOUBLE),
                STRING = scalar(Kind.STRING), NULL = scalar(Kind.NULL);
        public static Type scalar(Kind kind) { return new Type(kind, -1, null); }
        public static Type array(Type element) { return new Type(Kind.ARRAY, -1, element); }
        public static Type address(Type element) { return new Type(Kind.ADDRESS, -1, element); }
        public boolean reference() { return switch (kind) {
            case ARRAY, STRUCT, ENUM, STRING, NULL, ADDRESS -> true;
            default -> false;
        }; }
        public boolean numeric() { return kind == Kind.INT || kind == Kind.DOUBLE; }
    }

    /** This language contract is fixed, rather than chosen by individual targets. */
    public record Semantics(int integerBits, boolean integerWraps, int floatingBits,
                            boolean leftToRight, boolean initializeEveryDeclaration) {
        public static final Semantics JAVER = new Semantics(32, true, 64, true, true);
    }
    public enum StorageKind { PARAMETER, LOCAL, TEMPORARY, FIELD }
    public record Storage(int id, String name, Type type, StorageKind kind, SourceRange source) {
        public Storage { Objects.requireNonNull(name); Objects.requireNonNull(type); Objects.requireNonNull(kind); Objects.requireNonNull(source); }
    }
    public record Signature(int id, String name, Type returnType, List<Type> parameters, boolean builtIn) {
        public Signature { Objects.requireNonNull(name); Objects.requireNonNull(returnType); parameters = List.copyOf(parameters); }
    }
    public record Function(Signature signature, List<Storage> parameters, List<Storage> locals, Block body, SourceRange source) {
        public Function { parameters = List.copyOf(parameters); locals = List.copyOf(locals); Objects.requireNonNull(body); }
    }
    public record Struct(int id, String name, List<Storage> fields, SourceRange source) {
        public Struct { fields = List.copyOf(fields); }
    }
    public record EnumValue(int id, String name, int value, SourceRange source) { }
    public record Enumeration(int id, String name, List<EnumValue> values, SourceRange source) {
        public Enumeration { values = List.copyOf(values); }
    }
    public record Program(Semantics semantics, List<Signature> signatures, List<Function> functions,
                          List<Struct> structs, List<Enumeration> enums) {
        public Program {
            Objects.requireNonNull(semantics);
            signatures = List.copyOf(signatures); functions = List.copyOf(functions);
            structs = List.copyOf(structs); enums = List.copyOf(enums);
        }
    }

    public sealed interface Node permits Statement, Expression, Case { SourceRange source(); }
    public sealed interface Statement extends Node permits Block, Store, Evaluate, If, While, DoWhile, For, Switch, Break, Continue, Return { }
    public record Block(List<Statement> statements, SourceRange source) implements Statement {
        public Block { statements = List.copyOf(statements); }
    }
    public record Store(Expression address, Expression value, SourceRange source) implements Statement { }
    public record Evaluate(Expression expression, SourceRange source) implements Statement { }
    public record If(Expression condition, Statement thenBranch, Statement elseBranch, SourceRange source) implements Statement { }
    public record While(Expression condition, Statement body, SourceRange source) implements Statement { }
    public record DoWhile(Statement body, Expression condition, SourceRange source) implements Statement { }
    public record For(Block init, Expression condition, Block update, Statement body, SourceRange source) implements Statement { }
    public record Case(List<Expression> labels, boolean isDefault, Statement body, SourceRange source) implements Node {
        public Case { labels = List.copyOf(labels); }
    }
    public record Switch(Expression condition, List<Case> cases, SourceRange source) implements Statement {
        public Switch { cases = List.copyOf(cases); }
    }
    public record Break(SourceRange source) implements Statement { }
    public record Continue(SourceRange source) implements Statement { }
    public record Return(Expression value, SourceRange source) implements Statement { }

    public enum BinaryOp { ADD, SUBTRACT, MULTIPLY, DIVIDE, MODULO, BITWISE_AND, BITWISE_OR,
        BITWISE_XOR, SHIFT_LEFT, SHIFT_RIGHT, EQUALS, NOT_EQUALS, LESS, LESS_EQUALS, GREATER, GREATER_EQUALS }
    public enum UnaryOp { NEGATE, BITWISE_NOT, LOGICAL_NOT }
    public enum Conversion { NUMERIC, CAST, BOOLEAN, REFERENCE }
    public sealed interface Expression extends Node permits Literal, EnumConstant, LocalAddress, IndexAddress,
            FieldAddress, Load, Binary, Unary, Convert, Conditional, Call, ArrayNew, StructNew, Length, Sequence {
        Type type();
    }
    public record Literal(Object value, Type type, SourceRange source) implements Expression {
        public Literal {
            if (value != null && !(value instanceof Integer || value instanceof Double || value instanceof Boolean
                    || value instanceof Character || value instanceof String)) throw new IllegalArgumentException("Mutable/invalid literal");
        }
    }
    public record EnumConstant(int enumId, int valueId, Type type, SourceRange source) implements Expression { }
    public record LocalAddress(int storageId, Type type, SourceRange source) implements Expression { }
    public record IndexAddress(Expression target, Expression index, Type type, SourceRange source) implements Expression { }
    public record FieldAddress(Expression target, int fieldId, Type type, SourceRange source) implements Expression { }
    public record Load(Expression address, Type type, SourceRange source) implements Expression { }
    public record Binary(BinaryOp op, Expression left, Expression right, Type type, SourceRange source) implements Expression { }
    public record Unary(UnaryOp op, Expression operand, Type type, SourceRange source) implements Expression { }
    public record Convert(Conversion conversion, Expression operand, Type type, SourceRange source) implements Expression { }
    public record Conditional(Expression condition, Expression whenTrue, Expression whenFalse, Type type, SourceRange source) implements Expression { }
    public record Call(int functionId, List<Expression> arguments, Type type, SourceRange source) implements Expression {
        public Call { arguments = List.copyOf(arguments); }
    }
    /** Dimensions evaluate once, left to right; elements are initialized in source order.
     * initialized distinguishes an empty initializer from allocation without an initializer. */
    public record ArrayNew(List<Expression> dimensions, List<Expression> elements, boolean initialized, Type type, SourceRange source) implements Expression {
        public ArrayNew { dimensions = List.copyOf(dimensions); elements = List.copyOf(elements); }
    }
    public record StructNew(int structId, Type type, SourceRange source) implements Expression { }
    public record Length(Expression target, Type type, SourceRange source) implements Expression { }
    public record Sequence(Block effects, Expression value, Type type, SourceRange source) implements Expression { }
}
