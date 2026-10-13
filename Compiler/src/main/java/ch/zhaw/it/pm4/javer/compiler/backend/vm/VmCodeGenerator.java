package ch.zhaw.it.pm4.javer.compiler.backend.vm;

import ch.zhaw.it.pm4.javer.compiler.backend.TextCodeGenerator;
import ch.zhaw.it.pm4.javer.compiler.diagnostics.CompilerInvariantException;
import ch.zhaw.it.pm4.javer.compiler.diagnostics.DiagnosticBag;
import ch.zhaw.it.pm4.javer.compiler.ir.Ir;
import ch.zhaw.it.pm4.javer.compiler.source.SourceRange;
import java.util.*;

/** Exhaustive IR-to-VM emission. No parser AST, frontend type rules or mutable symbols. */
final class VmCodeGenerator extends TextCodeGenerator {
    private final Ir.Program program;
    private final VmMetadata metadata;
    private final Map<Integer, Ir.Signature> signatures = new HashMap<>();
    private final Deque<Loop> loops = new ArrayDeque<>();
    private VmDataSection data;
    private int currentFunction;
    private int labelId;
    private record Loop(String exit, String next) { }

    VmCodeGenerator(DiagnosticBag diagnostics, Ir.Program program, VmMetadata metadata) {
        super(diagnostics); this.program = program; this.metadata = metadata;
        program.signatures().forEach(s -> signatures.put(s.id(), s));
    }
    @Override protected void emit() {
        data = new VmDataSection();
        data.addEnums(program, metadata);
        line(".code");
        for (Ir.Function function : program.functions()) {
            currentFunction = function.signature().id();
            line(metadata.function(currentFunction).label() + ":");
            line("ENTER, " + metadata.function(currentFunction).localBytes());
            statement(function.body());
            if (function.signature().returnType().equals(Ir.Type.VOID)) line("RET, 0");
        }
        line(""); line(".data"); data.entries().forEach(e -> line(e.toString()));
    }
    private void statement(Ir.Statement node) {
        switch (node) {
            case Ir.Block block -> block.statements().forEach(this::statement);
            case Ir.Store store -> { expression(store.address()); expression(store.value()); store(store.value().type()); }
            case Ir.Evaluate evaluation -> { expression(evaluation.expression()); pop(evaluation.expression().type()); }
            case Ir.Return returning -> {
                if (returning.value() != null) expression(returning.value());
                line("RET, " + (returning.value() == null ? 0 : VmLayout.stackBytes(returning.value().type())));
            }
            case Ir.If branch -> {
                String other = label("if_else"), end = label("if_end");
                expression(branch.condition()); line("JUMPF, " + other);
                statement(branch.thenBranch()); line("JUMP, " + end);
                mark(other); statement(branch.elseBranch()); mark(end);
            }
            case Ir.While loop -> {
                String condition = label("while_condition"), end = label("while_end");
                mark(condition); expression(loop.condition()); line("JUMPF, " + end);
                loops.push(new Loop(end, condition)); statement(loop.body()); loops.pop();
                line("JUMP, " + condition); mark(end);
            }
            case Ir.DoWhile loop -> {
                String body = label("do_body"), condition = label("do_condition"), end = label("do_end");
                mark(body); loops.push(new Loop(end, condition)); statement(loop.body()); loops.pop();
                mark(condition); expression(loop.condition()); line("JUMPT, " + body); mark(end);
            }
            case Ir.For loop -> {
                String condition = label("for_condition"), update = label("for_update"), end = label("for_end");
                statement(loop.init()); mark(condition); expression(loop.condition()); line("JUMPF, " + end);
                loops.push(new Loop(end, update)); statement(loop.body()); loops.pop();
                mark(update); statement(loop.update()); line("JUMP, " + condition); mark(end);
            }
            case Ir.Switch selection -> selection(selection);
            case Ir.Break exit -> { require(!loops.isEmpty(), exit.source(), "Break without loop"); line("JUMP, " + loops.peek().exit()); }
            case Ir.Continue next -> { require(!loops.isEmpty(), next.source(), "Continue without loop"); line("JUMP, " + loops.peek().next()); }
        }
    }
    private void selection(Ir.Switch selection) {
        String end = label("switch_end"), noMatch = label("switch_no_match");
        List<String> targets = selection.cases().stream().map(c -> label("switch_case")).toList();
        String fallback = noMatch;
        expression(selection.condition());
        for (int i = 0; i < selection.cases().size(); i++) {
            Ir.Case branch = selection.cases().get(i);
            if (branch.isDefault()) { fallback = targets.get(i); continue; }
            for (Ir.Expression value : branch.labels()) {
                dup(selection.condition().type());
                expression(value);
                binary(Ir.BinaryOp.EQUALS, selection.condition().type()); line("JUMPT, " + targets.get(i));
            }
        }
        line("JUMP, " + fallback);
        for (int i = 0; i < selection.cases().size(); i++) {
            mark(targets.get(i)); pop(selection.condition().type()); statement(selection.cases().get(i).body()); line("JUMP, " + end);
        }
        if (fallback.equals(noMatch)) { mark(noMatch); pop(selection.condition().type()); }
        mark(end);
    }
    private void expression(Ir.Expression node) {
        switch (node) {
            case Ir.Literal literal -> literal(literal);
            case Ir.LocalAddress local -> {
                VmMetadata.StorageLayout layout = metadata.storage(local.storageId());
                line("LOCAL, " + (layout.offsetBytes() + (layout.parameter() ? metadata.function(currentFunction).localBytes() : 0)));
            }
            case Ir.Load load -> { expression(load.address()); line(VmLayout.memoryWidth(load.type()).loadInstruction()); }
            case Ir.IndexAddress index -> {
                expression(index.target()); expression(index.index());
                int width = VmLayout.memoryWidth(index.type().element()).bytes();
                line("BOUNDS, " + width); scale(width); line("PUSHI, " + VmLayout.ARRAY_PAYLOAD_OFFSET_BYTES); line("IADD"); line("IADD");
            }
            case Ir.FieldAddress field -> {
                expression(field.target()); int offset = metadata.storage(field.fieldId()).offsetBytes();
                if (offset != 0) { line("PUSHI, " + offset); line("IADD"); }
            }
            case Ir.EnumConstant constant -> {
                line("PUSHR, " + metadata.enumLabel(constant.enumId())); int offset = metadata.enumOffset(constant.valueId());
                if (offset != 0) { line("PUSHI, " + offset); line("IADD"); }
            }
            case Ir.Binary operation -> {
                expression(operation.left()); expression(operation.right()); binary(operation.op(), operation.left().type());
            }
            case Ir.Unary operation -> {
                expression(operation.operand());
                switch (operation.op()) {
                    case NEGATE -> line(operation.type().equals(Ir.Type.DOUBLE) ? "DNEG" : "INEG");
                    case BITWISE_NOT -> line("IINV");
                    case LOGICAL_NOT -> { line("PUSHI, 0"); line("IEQ"); }
                }
            }
            case Ir.Convert conversion -> conversion(conversion);
            case Ir.Conditional branch -> {
                String other = label("conditional_false"), end = label("conditional_end");
                expression(branch.condition()); line("JUMPF, " + other); expression(branch.whenTrue()); line("JUMP, " + end);
                mark(other); expression(branch.whenFalse()); mark(end);
            }
            case Ir.Call call -> {
                Ir.Signature signature = signatures.get(call.functionId());
                call.arguments().forEach(this::expression);
                if (signature.builtIn()) {
                    VmBuiltInFunction function = VmBuiltInFunction.find(signature.name());
                    require(function != null, call.source(), "Unknown VM runtime function " + signature.name());
                    line(function.instruction());
                } else {
                    VmMetadata.FunctionLayout function = metadata.function(call.functionId());
                    line("CALL, " + function.label() + ", " + function.parameterBytes());
                }
            }
            case Ir.ArrayNew array -> array(array);
            case Ir.StructNew allocation -> { line("PUSHI, " + metadata.structBytes(allocation.structId())); line("NEW"); }
            case Ir.Length length -> { expression(length.target()); line("LOAD4"); }
            case Ir.Sequence sequence -> { statement(sequence.effects()); expression(sequence.value()); }
        }
    }
    private void array(Ir.ArrayNew array) {
        VmMetadata.ArrayTemps temps = metadata.array(array);
        for (int i = 0; i < array.dimensions().size(); i++) {
            line("LOCAL, " + temps.dimensions().get(i)); expression(array.dimensions().get(i)); line("STORE4");
        }
        if (!array.initialized() && array.dimensions().size() > 1) { jagged(array, 0, array.type()); return; }
        Ir.Type element = array.type().element(); int width = VmLayout.memoryWidth(element).bytes();
        if (array.dimensions().isEmpty()) line("PUSHI, " + array.elements().size()); else temp(temps.dimensions().getFirst());
        line("NEWA, " + width);
        Optional<VmDataEntry> template = data.arrayTemplate(array);
        if (template.isPresent()) {
            dup(Ir.Type.INT); line("PUSHI, " + VmLayout.ARRAY_PAYLOAD_OFFSET_BYTES); line("IADD");
            line("PUSHR, " + template.orElseThrow().label());
            line("PUSHI, " + Math.multiplyExact(array.elements().size(), width)); line("MEMCPY");
            return;
        }
        for (int i = 0; i < array.elements().size(); i++) {
            dup(Ir.Type.INT); line("PUSHI, " + i); line("BOUNDS, " + width);
            scale(width); line("PUSHI, " + VmLayout.ARRAY_PAYLOAD_OFFSET_BYTES); line("IADD"); line("IADD");
            expression(array.elements().get(i)); store(element);
        }
    }
    private void jagged(Ir.ArrayNew array, int depth, Ir.Type arrayType) {
        VmMetadata.ArrayTemps temps = metadata.array(array);
        int width = VmLayout.memoryWidth(arrayType.element()).bytes();
        if (depth == array.dimensions().size() - 1) { temp(temps.dimensions().get(depth)); line("NEWA, " + width); return; }
        int base = temps.bases().get(depth), index = temps.indices().get(depth);
        line("LOCAL, " + base); temp(temps.dimensions().get(depth)); line("NEWA, " + width); line("STORE4");
        line("LOCAL, " + index); line("PUSHI, 0"); line("STORE4");
        String loop = label("array_loop"), end = label("array_end");
        mark(loop); temp(index); temp(temps.dimensions().get(depth)); line("ILT"); line("JUMPF, " + end);
        temp(base); temp(index); scale(width); line("PUSHI, " + VmLayout.ARRAY_PAYLOAD_OFFSET_BYTES); line("IADD"); line("IADD");
        jagged(array, depth + 1, arrayType.element()); store(arrayType.element());
        line("LOCAL, " + index); temp(index); line("PUSHI, 1"); line("IADD"); line("STORE4"); line("JUMP, " + loop);
        mark(end); temp(base);
    }
    private void conversion(Ir.Convert conversion) {
        expression(conversion.operand());
        Ir.Type from = conversion.operand().type(), to = conversion.type();
        switch (conversion.conversion()) {
            case NUMERIC -> numericConversion(from, to);
            case REFERENCE -> { }
            case CAST -> {
                if (from.equals(Ir.Type.VOID)) defaultValue(to);
                else if (VmLayout.stackBytes(from) != VmLayout.stackBytes(to)) line(from.equals(Ir.Type.DOUBLE) ? "D2I" : "I2D");
            }
            case BOOLEAN -> {
                if (from.equals(Ir.Type.DOUBLE)) {
                    String zero = label("bool_false"), end = label("bool_end");
                    dup(from); line("PUSHD, 0.0"); line("DEQ"); line("JUMPT, " + zero);
                    dup(from); line("DEQ"); line("JUMP, " + end);
                    mark(zero); pop(from); line("PUSHI, 0"); mark(end);
                } else { line("PUSHI, 0"); line("INE"); }
            }
        }
    }
    private void numericConversion(Ir.Type from, Ir.Type to) {
        if (from.equals(to)) return;
        if (from.equals(Ir.Type.INT) && to.equals(Ir.Type.DOUBLE)) line("I2D");
        else if (from.equals(Ir.Type.DOUBLE) && to.equals(Ir.Type.INT)) line("D2I");
        else if (!(from.reference() && to.reference())) require(false, SourceRange.UNKNOWN, "Unsupported numeric conversion " + from + " -> " + to);
    }
    private void binary(Ir.BinaryOp op, Ir.Type operand) {
        boolean floating = operand.equals(Ir.Type.DOUBLE);
        String instruction = switch (op) {
            case ADD -> floating ? "DADD" : "IADD"; case SUBTRACT -> floating ? "DSUB" : "ISUB";
            case MULTIPLY -> floating ? "DMUL" : "IMUL"; case DIVIDE -> floating ? "DDIV" : "IDIV";
            case MODULO -> "IMOD"; case BITWISE_AND -> "IAND"; case BITWISE_OR -> "IOR"; case BITWISE_XOR -> "IXOR";
            case SHIFT_LEFT -> "ISHL"; case SHIFT_RIGHT -> "ISHR";
            case LESS -> floating ? "DLT" : "ILT"; case LESS_EQUALS -> floating ? "DLE" : "ILE";
            case GREATER -> floating ? "DGT" : "IGT"; case GREATER_EQUALS -> floating ? "DGE" : "IGE";
            case EQUALS -> operand.equals(Ir.Type.STRING) ? "STREQ" : floating ? "DEQ" : "IEQ";
            case NOT_EQUALS -> operand.equals(Ir.Type.STRING) ? "STRNE" : floating ? "DNE" : "INE";
        };
        line(instruction);
    }
    private void literal(Ir.Literal value) {
        if (value.value() == null) { line("PUSHI, 0"); return; }
        switch (value.type().kind()) {
            case DOUBLE -> line("PUSHD, " + Double.toString((Double) value.value()));
            case STRING -> line("PUSHR, " + data.internString((String) value.value()).label());
            case BOOL -> line("PUSHI, " + (Boolean.TRUE.equals(value.value()) ? 1 : 0));
            case CHAR -> line("PUSHI, " + (int) (Character) value.value());
            case INT -> line("PUSHI, " + value.value());
            default -> throw new CompilerInvariantException(value.source(), "Unexpected literal type " + value.type());
        }
    }
    private void defaultValue(Ir.Type type) { line(type.equals(Ir.Type.DOUBLE) ? "PUSHD, 0.0" : "PUSHI, 0"); }
    private void temp(int offset) { line("LOCAL, " + offset); line("LOAD4"); }
    private void scale(int width) { if (width != 1) { line("PUSHI, " + width); line("IMUL"); } }
    private void store(Ir.Type type) { line(VmLayout.memoryWidth(type).storeInstruction()); }
    private void dup(Ir.Type type) { line("DUP, " + VmLayout.stackBytes(type)); }
    private void pop(Ir.Type type) { if (!type.equals(Ir.Type.VOID)) line("POP, " + VmLayout.stackBytes(type)); }
    private String label(String name) { return name + "_" + labelId++; }
    private void mark(String label) { line(label + ":"); }
    private void line(String text) { writeLine(text); }
    private void require(boolean condition, SourceRange source, String message) { if (!condition) throw new CompilerInvariantException(source, message); }
}
