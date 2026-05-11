package ch.zhaw.it.pm4.javer.compiler.visitor;

import java.util.Set;

import ch.zhaw.it.pm4.javer.compiler.ast.nodes.CompilationUnit;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.ArrayInitExpression;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.AssignExpression;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.BinaryExpression;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.CallExpression;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.ConditionalExpression;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.IndexExpression;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.LiteralExpression;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.MemberAccessExpression;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.NameExpression;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.NewExpression;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.PostfixExpression;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.UnaryExpression;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.UnaryExpressionKind;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.type.ArrayType;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.type.NamedType;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.type.PrimitiveType;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.type.TypeAstNode;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.type.VoidType;
import ch.zhaw.it.pm4.javer.compiler.ast.scope.GlobalScope;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.EnumEntry;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.EnumValueEntry;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.StorageEntry;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.StructEntry;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.SymbolEntry;
import ch.zhaw.it.pm4.javer.compiler.ast.typeinfo.ArrayTypeInfo;
import ch.zhaw.it.pm4.javer.compiler.ast.typeinfo.EnumTypeInfo;
import ch.zhaw.it.pm4.javer.compiler.ast.typeinfo.PrimitiveTypeInfo;
import ch.zhaw.it.pm4.javer.compiler.ast.typeinfo.StructTypeInfo;
import ch.zhaw.it.pm4.javer.compiler.ast.typeinfo.TypeInfo;
import ch.zhaw.it.pm4.javer.compiler.ast.typeinfo.UnknownTypeInfo;
import ch.zhaw.it.pm4.javer.compiler.ast.typeinfo.VoidTypeInfo;
import ch.zhaw.it.pm4.javer.compiler.misc.diagnostics.DiagnosticBag;

public class TypeCheckVisitor extends AstNodeVisitorBase {

    private static final Set<String> VOID_BUILT_INS = Set.of("printi", "prints", "println");

    private final DiagnosticBag diagnosticBag;
    private GlobalScope globalScope;

    public TypeCheckVisitor() {
        this(null);
    }

    public TypeCheckVisitor(DiagnosticBag diagnosticBag) {
        this.diagnosticBag = diagnosticBag;
    }

    @Override
    public void visit(CompilationUnit node) {
        globalScope = node.getGlobalScope();
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
            case NULL -> UnknownTypeInfo.INSTANCE;
        });
    }

    @Override
    public void visit(NameExpression node) {
        SymbolEntry entry = node.getSymbolEntry();
        if (entry instanceof StorageEntry storageEntry) {
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
    public void visit(CallExpression node) {
        super.visit(node);
        if (node.getResolvedFunction() != null) {
            node.setResultingType(node.getResolvedFunction().getReturnType());
            return;
        }
        if (VOID_BUILT_INS.contains(node.getFunctionName())) {
            node.setResultingType(VoidTypeInfo.INSTANCE);
            return;
        }
        node.setResultingType(UnknownTypeInfo.INSTANCE);
    }

    @Override
    public void visit(BinaryExpression node) {
        super.visit(node);
        TypeInfo left = node.getLeft().getResultingType();
        TypeInfo right = node.getRight().getResultingType();

        node.setResultingType(switch (node.getOperator()) {
            case OR, AND, EQUALS, NOT_EQUALS, LESS, LESS_EQUALS, GREATER, GREATER_EQUALS -> PrimitiveTypeInfo.BOOL;
            case ADD, SUBTRACT, MULTIPLY, DIVIDE, MODULO, BITWISE_OR, BITWISE_AND, BITWISE_XOR, SHIFT_LEFT, SHIFT_RIGHT ->
                    numericResult(left, right);
            case INVALID -> UnknownTypeInfo.INSTANCE;
        });
    }

    @Override
    public void visit(AssignExpression node) {
        super.visit(node);
        node.setResultingType(node.getTarget() == null ? UnknownTypeInfo.INSTANCE : node.getTarget().getResultingType());
    }

    @Override
    public void visit(ConditionalExpression node) {
        super.visit(node);
        TypeInfo trueType = node.getTrueExpression() == null ? UnknownTypeInfo.INSTANCE : node.getTrueExpression().getResultingType();
        TypeInfo falseType = node.getFalseExpression() == null ? UnknownTypeInfo.INSTANCE : node.getFalseExpression().getResultingType();
        node.setResultingType(trueType.equals(falseType) ? trueType : UnknownTypeInfo.INSTANCE);
    }

    @Override
    public void visit(UnaryExpression node) {
        super.visit(node);
        if (node.getKind() == UnaryExpressionKind.LOGICAL_NOT) {
            node.setResultingType(PrimitiveTypeInfo.BOOL);
            return;
        }
        node.setResultingType(node.getOperand().getResultingType());
    }

    @Override
    public void visit(PostfixExpression node) {
        super.visit(node);
        node.setResultingType(node.getOperand().getResultingType());
    }

    @Override
    public void visit(IndexExpression node) {
        super.visit(node);
        if (node.getTarget().getResultingType() instanceof ArrayTypeInfo arrayType) {
            node.setResultingType(arrayType.elementType());
            return;
        }
        node.setResultingType(UnknownTypeInfo.INSTANCE);
    }

    @Override
    public void visit(MemberAccessExpression node) {
        super.visit(node);
        if (node.getResolvedField() != null) {
            node.setResultingType(node.getResolvedField().getType());
            return;
        }
        if (node.getResolvedEnumValue() != null) {
            node.setResultingType(new EnumTypeInfo(node.getResolvedEnumValue().getOwnerEnum()));
            return;
        }
        node.setResultingType(UnknownTypeInfo.INSTANCE);
    }

    @Override
    public void visit(NewExpression node) {
        super.visit(node);
        TypeInfo type = resolveType(node.getType());
        if (!node.getDimensions().isEmpty() || node.getArrayInit() != null) {
            node.setResultingType(new ArrayTypeInfo(type));
            return;
        }
        node.setResultingType(type);
    }

    @Override
    public void visit(ArrayInitExpression node) {
        super.visit(node);
        TypeInfo elementType = node.getElements().isEmpty()
                ? UnknownTypeInfo.INSTANCE
                : node.getElements().getFirst().getResultingType();
        node.setResultingType(new ArrayTypeInfo(elementType));
    }

    private TypeInfo numericResult(TypeInfo left, TypeInfo right) {
        if (PrimitiveTypeInfo.DOUBLE.equals(left) || PrimitiveTypeInfo.DOUBLE.equals(right)) {
            return PrimitiveTypeInfo.DOUBLE;
        }
        if (left instanceof UnknownTypeInfo || right instanceof UnknownTypeInfo) {
            return UnknownTypeInfo.INSTANCE;
        }
        return PrimitiveTypeInfo.INT;
    }

    private TypeInfo resolveType(TypeAstNode type) {
        if (type instanceof PrimitiveType primitiveType) {
            return PrimitiveTypeInfo.of(primitiveType.getKind());
        }
        if (type instanceof ArrayType arrayType) {
            return new ArrayTypeInfo(resolveType(arrayType.getBaseType()));
        }
        if (type instanceof VoidType) {
            return VoidTypeInfo.INSTANCE;
        }
        if (type instanceof NamedType namedType) {
            return resolveNamedType(namedType);
        }
        return UnknownTypeInfo.INSTANCE;
    }

    private TypeInfo resolveNamedType(NamedType namedType) {
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
