package ch.zhaw.it.pm4.javer.compiler.backend.fanuc;

import ch.zhaw.it.pm4.javer.compiler.ir.Ir;

import java.util.*;

/**
 * Static register values initialized before main executes.
 */
final class FanucDataSection {
    private final Map<String, List<String>> values = new LinkedHashMap<>();

    void addEnums(Ir.Program program, FanucMetadata metadata) {
        for (var e : program.enums())
            values.put(metadata.enumLabel(e.id()), e.values().stream().map(v -> Integer.toString(v.value())).toList());
    }

    Optional<Entry> arrayTemplate(Ir.ArrayNew array) {
        if (!array.initialized() || !array.dimensions().isEmpty() || array.elements().isEmpty())
            return Optional.empty();
        List<String> elements = new ArrayList<>();
        for (var expression : array.elements()) {
            Object value = constant(expression);
            if (value == null) return Optional.empty();
            elements.add(value instanceof Boolean b ? (b ? "1" : "0") : value.toString());
        }
        String label = "array_template_" + values.size();
        values.put(label, elements);
        return Optional.of(new Entry(label));
    }

    private Object constant(Ir.Expression e) {
        if (e instanceof Ir.Literal l) {
            if (l.value() instanceof Double d) return d.floatValue();
            return l.value();
        }
        if (e instanceof Ir.Convert c && c.conversion() == Ir.Conversion.NUMERIC) {
            Object v = constant(c.operand());
            if (v instanceof Number n) {
                if (c.type().equals(Ir.Type.DOUBLE)) return n.floatValue();
                float f = n.floatValue();
                float truncated = f - f % 1;
                return (int) (short) (int) (truncated % 65536);
            }
        }
        return null;
    }

    record Entry(String label) {
    }

    Map<String, List<String>> values() {
        return values;
    }
}
