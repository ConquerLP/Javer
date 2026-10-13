package ch.zhaw.it.pm4.javer.compiler.ir;

/** Target-independent inspection of the frozen program. */
public final class IrPrinter {
    private IrPrinter() { }
    public static String symbols(Ir.Program program) {
        StringBuilder text = new StringBuilder();
        program.signatures().forEach(s -> text.append("function #").append(s.id()).append(' ').append(s.name())
                .append(s.parameters()).append(" -> ").append(s.returnType()).append(s.builtIn() ? " [runtime]" : "").append('\n'));
        program.structs().forEach(s -> text.append("struct #").append(s.id()).append(' ').append(s.name()).append(' ').append(s.fields()).append('\n'));
        program.enums().forEach(e -> text.append("enum #").append(e.id()).append(' ').append(e.name()).append(' ').append(e.values()).append('\n'));
        return text.toString();
    }
    public static String print(Ir.Program program) {
        StringBuilder text = new StringBuilder(symbols(program));
        for (Ir.Function function : program.functions()) {
            text.append("body: ").append(function.signature().name()).append('\n');
            IrWalker.walk(function.body(), node -> text.append("  ").append(node.getClass().getSimpleName())
                    .append(details(node))
                    .append(node instanceof Ir.Expression expression ? " : " + expression.type() : "")
                    .append(" at ").append(node.source()).append('\n'));
        }
        return text.toString();
    }
    private static String details(Ir.Node node) {
        return switch (node) {
            case Ir.Literal literal -> " " + literal.value();
            case Ir.LocalAddress slot -> " #" + slot.storageId();
            case Ir.FieldAddress field -> " #" + field.fieldId();
            case Ir.EnumConstant value -> " enum#" + value.enumId() + " value#" + value.valueId();
            case Ir.Call call -> " function#" + call.functionId();
            case Ir.Binary binary -> " " + binary.op();
            case Ir.Unary unary -> " " + unary.op();
            case Ir.Convert conversion -> " " + conversion.conversion();
            default -> "";
        };
    }
}
