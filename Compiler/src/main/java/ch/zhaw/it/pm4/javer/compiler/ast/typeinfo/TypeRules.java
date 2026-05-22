package ch.zhaw.it.pm4.javer.compiler.ast.typeinfo;

import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.AssignOperator;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.BinaryExpressionKind;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.type.PrimitiveTypeKind;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.StructEntry;
import ch.zhaw.it.pm4.javer.compiler.builtin.BuiltInTypes;

/**
 * Central semantic type rules shared by semantic analysis and code generation.
 */
public final class TypeRules {

    private TypeRules() {
    }

    public static boolean isUnknown(TypeInfo type) {
        return type instanceof UnknownTypeInfo;
    }

    public static boolean isVoid(TypeInfo type) {
        return type instanceof VoidTypeInfo;
    }

    public static boolean isNull(TypeInfo type) {
        return type instanceof NullTypeInfo;
    }

    public static boolean isReferenceType(TypeInfo type) {
        return PrimitiveTypeInfo.STRING.equals(type)
                || type instanceof ArrayTypeInfo
                || type instanceof StructTypeInfo
                || type instanceof EnumTypeInfo;
    }

    public static boolean isNumeric(TypeInfo type) {
        return PrimitiveTypeInfo.INT.equals(type) || PrimitiveTypeInfo.DOUBLE.equals(type);
    }

    public static boolean isInteger(TypeInfo type) {
        return PrimitiveTypeInfo.INT.equals(type);
    }

    public static boolean isChar(TypeInfo type) {
        return PrimitiveTypeInfo.CHAR.equals(type);
    }

    public static boolean isConditionType(TypeInfo type) {
        return isUnknown(type) || !isVoid(type);
    }

    public static boolean isAssignable(TypeInfo target, TypeInfo source) {
        if (isUnknown(target) || isUnknown(source)) {
            return true;
        }
        if (isNull(source)) {
            return isReferenceType(target);
        }
        if (BuiltInTypes.isBoxingConversion(source, target)) {
            return true;
        }
        if (target.equals(source)) {
            return true;
        }
        if (isStructUpcast(target, source)) {
            return true;
        }
        return PrimitiveTypeInfo.DOUBLE.equals(target) && PrimitiveTypeInfo.INT.equals(source);
    }

    public static boolean needsConversion(TypeInfo from, TypeInfo to) {
        if (isStructUpcast(to, from)) {
            return false;
        }
        return !from.equals(to) && !(isNull(from) && isReferenceType(to));
    }

    public static boolean isNotCompoundAssignable(AssignOperator operator, TypeInfo target, TypeInfo value) {
        if (isUnknown(target) || isUnknown(value)) {
            return false;
        }
        return !switch (operator) {
            case ASSIGN -> isAssignable(target, value);
            case ADD_ASSIGN, SUB_ASSIGN, MUL_ASSIGN, DIV_ASSIGN -> isNumeric(target) && isNumeric(value);
            case MOD_ASSIGN -> isInteger(target) && isInteger(value);
            case BITWISE_OR_ASSIGN, BITWISE_AND_ASSIGN, BITWISE_XOR_ASSIGN,
                 LEFT_SHIFT_ASSIGN, RIGHT_SHIFT_ASSIGN -> isInteger(target) && isInteger(value);
            case INVALID -> false;
        };
    }

    public static TypeInfo compoundCalculationType(AssignOperator operator, TypeInfo target, TypeInfo value) {
        return switch (operator) {
            case ADD_ASSIGN, SUB_ASSIGN, MUL_ASSIGN, DIV_ASSIGN -> numericResult(target, value);
            case MOD_ASSIGN, BITWISE_OR_ASSIGN, BITWISE_AND_ASSIGN, BITWISE_XOR_ASSIGN,
                 LEFT_SHIFT_ASSIGN, RIGHT_SHIFT_ASSIGN -> PrimitiveTypeInfo.INT;
            case ASSIGN, INVALID -> target;
        };
    }

    public static TypeInfo binaryResult(BinaryExpressionKind operator, TypeInfo left, TypeInfo right) {
        if (isUnknown(left) || isUnknown(right)) {
            return UnknownTypeInfo.INSTANCE;
        }
        return switch (operator) {
            case OR, AND, EQUALS, NOT_EQUALS, LESS, LESS_EQUALS, GREATER, GREATER_EQUALS -> PrimitiveTypeInfo.BOOL;
            case ADD, SUBTRACT, MULTIPLY, DIVIDE -> numericResult(left, right);
            case MODULO, BITWISE_OR, BITWISE_AND, BITWISE_XOR, SHIFT_LEFT, SHIFT_RIGHT -> PrimitiveTypeInfo.INT;
            case INVALID -> UnknownTypeInfo.INSTANCE;
        };
    }

    public static boolean isNotBinaryOperatorAllowed(BinaryExpressionKind operator, TypeInfo left, TypeInfo right) {
        if (isUnknown(left) || isUnknown(right)) {
            return false;
        }
        return !switch (operator) {
            case OR, AND -> isConditionType(left) && isConditionType(right);
            case EQUALS, NOT_EQUALS -> isEqualityComparable(left, right);
            case LESS, LESS_EQUALS, GREATER, GREATER_EQUALS -> isOrderedComparable(left, right);
            case ADD, SUBTRACT, MULTIPLY, DIVIDE -> isNumeric(left) && isNumeric(right);
            case MODULO, BITWISE_OR, BITWISE_AND, BITWISE_XOR, SHIFT_LEFT, SHIFT_RIGHT ->
                    isInteger(left) && isInteger(right);
            case INVALID -> false;
        };
    }

    public static boolean isEqualityComparable(TypeInfo left, TypeInfo right) {
        if (isUnknown(left) || isUnknown(right)) {
            return true;
        }
        if (isNull(left) || isNull(right)) {
            return (isNull(left) && isNull(right))
                    || (isNull(left) && isReferenceType(right))
                    || (isNull(right) && isReferenceType(left));
        }
        if (left instanceof EnumTypeInfo || right instanceof EnumTypeInfo) {
            return left.equals(right);
        }
        if (isReferenceType(left) || isReferenceType(right)) {
            return left.equals(right) || areStructsRelated(left, right);
        }
        if (isNumeric(left) && isNumeric(right)) {
            return true;
        }
        return left.equals(right);
    }

    public static boolean isOrderedComparable(TypeInfo left, TypeInfo right) {
        return (isNumeric(left) && isNumeric(right)) || (isChar(left) && isChar(right));
    }

    public static TypeInfo equalityOperandType(TypeInfo left, TypeInfo right) {
        if (PrimitiveTypeInfo.DOUBLE.equals(left) || PrimitiveTypeInfo.DOUBLE.equals(right)) {
            return PrimitiveTypeInfo.DOUBLE;
        }
        if (isNull(left)) {
            return right;
        }
        if (isNull(right)) {
            return left;
        }
        if (left.equals(right)) {
            return left;
        }
        TypeInfo commonStructBase = commonStructBase(left, right);
        if (commonStructBase != null) {
            return commonStructBase;
        }
        return PrimitiveTypeInfo.INT;
    }

    public static TypeInfo numericResult(TypeInfo left, TypeInfo right) {
        if (isUnknown(left) || isUnknown(right)) {
            return UnknownTypeInfo.INSTANCE;
        }
        if (PrimitiveTypeInfo.DOUBLE.equals(left) || PrimitiveTypeInfo.DOUBLE.equals(right)) {
            return PrimitiveTypeInfo.DOUBLE;
        }
        return PrimitiveTypeInfo.INT;
    }

    public static TypeInfo conditionalResult(TypeInfo trueType, TypeInfo falseType) {
        if (trueType.equals(falseType)) {
            return trueType;
        }
        if (isUnknown(trueType) || isUnknown(falseType)) {
            return UnknownTypeInfo.INSTANCE;
        }
        if (isNull(trueType) && isReferenceType(falseType)) {
            return falseType;
        }
        if (isNull(falseType) && isReferenceType(trueType)) {
            return trueType;
        }
        TypeInfo commonStructBase = commonStructBase(trueType, falseType);
        if (commonStructBase != null) {
            return commonStructBase;
        }
        if (PrimitiveTypeInfo.DOUBLE.equals(trueType) && PrimitiveTypeInfo.INT.equals(falseType)) {
            return PrimitiveTypeInfo.DOUBLE;
        }
        if (PrimitiveTypeInfo.INT.equals(trueType) && PrimitiveTypeInfo.DOUBLE.equals(falseType)) {
            return PrimitiveTypeInfo.DOUBLE;
        }
        return UnknownTypeInfo.INSTANCE;
    }

    public static Object defaultValue(TypeInfo type) {
        if (type instanceof PrimitiveTypeInfo(PrimitiveTypeKind kind)) {
            return switch (kind) {
                case BOOL -> false;
                case CHAR -> '\0';
                case INT -> 0;
                case DOUBLE -> 0.0;
                case STRING, INVALID -> null;
            };
        }
        if (type instanceof EnumTypeInfo) {
            return null;
        }
        return null;
    }

    public static TypeInfo arrayType(TypeInfo elementType, int dimensions) {
        TypeInfo result = elementType;
        for (int i = 0; i < dimensions; i++) {
            result = new ArrayTypeInfo(result);
        }
        return result;
    }

    public static TypeInfo leafElementType(TypeInfo type) {
        TypeInfo current = type;
        while (current instanceof ArrayTypeInfo(TypeInfo elementType)) {
            current = elementType;
        }
        return current;
    }

    private static boolean isStructUpcast(TypeInfo target, TypeInfo source) {
        if (!(target instanceof StructTypeInfo(StructEntry targetStruct))
                || !(source instanceof StructTypeInfo(StructEntry sourceStruct))
                || targetStruct == null
                || sourceStruct == null) {
            return false;
        }
        return sourceStruct.derivesFrom(targetStruct);
    }

    private static boolean areStructsRelated(TypeInfo left, TypeInfo right) {
        return commonStructBase(left, right) != null;
    }

    private static TypeInfo commonStructBase(TypeInfo left, TypeInfo right) {
        if (!(left instanceof StructTypeInfo(StructEntry leftStruct))
                || !(right instanceof StructTypeInfo(StructEntry rightStruct))
                || leftStruct == null
                || rightStruct == null) {
            return null;
        }

        StructEntry current = leftStruct;
        while (current != null) {
            if (rightStruct == current || rightStruct.derivesFrom(current)) {
                return new StructTypeInfo(current);
            }
            current = current.getBaseStruct();
        }
        return null;
    }
}
