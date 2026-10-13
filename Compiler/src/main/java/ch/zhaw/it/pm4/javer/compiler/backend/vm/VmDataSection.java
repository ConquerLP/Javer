package ch.zhaw.it.pm4.javer.compiler.backend.vm;

import ch.zhaw.it.pm4.javer.compiler.ir.Ir;
import ch.zhaw.it.pm4.javer.compiler.diagnostics.CompilerInvariantException;
import java.util.*;

final class VmDataSection {
    private final Map<String, VmDataEntry> entries = new LinkedHashMap<>();
    private final Map<String, VmDataEntry> strings = new HashMap<>();
    private int nextTemplate;
    VmDataEntry internString(String value) {
        return strings.computeIfAbsent(value, text -> {
            List<String> bytes = new ArrayList<>();
            appendBytes(bytes, text.length(), VmLayout.WORD_BYTES);
            for (char c : text.toCharArray()) appendBytes(bytes, c, VmLayout.CHAR_BYTES);
            VmDataEntry entry = new VmDataEntry("string_" + strings.size(), VmLayout.BYTE_BYTES, bytes);
            entries.put(entry.label(), entry); return entry;
        });
    }
    void addEnums(Ir.Program program, VmMetadata metadata) {
        for (Ir.Enumeration enumeration : program.enums()) {
            if (enumeration.values().isEmpty()) continue;
            VmDataEntry entry = new VmDataEntry(metadata.enumLabel(enumeration.id()), VmLayout.WORD_BYTES,
                    enumeration.values().stream().map(v -> "%08X".formatted(v.value())).toList());
            entries.put(entry.label(), entry);
        }
    }
    Optional<VmDataEntry> arrayTemplate(Ir.ArrayNew array) {
        if (!array.initialized() || !array.dimensions().isEmpty() || array.elements().isEmpty()) return Optional.empty();
        List<String> values = new ArrayList<>();
        int width = VmLayout.memoryWidth(array.type().element()).bytes();
        for (Ir.Expression expression : array.elements()) {
            Object value = constant(expression);
            if (value == null) return Optional.empty();
            values.add(switch (value) {
                case Double number -> "%016X".formatted(Double.doubleToRawLongBits(number));
                case Integer number -> "%08X".formatted(number);
                case Character character -> "%04X".formatted((int) character);
                case Boolean bool -> bool ? "01" : "00";
                default -> throw new CompilerInvariantException(expression.source(), "Unexpected constant template value");
            });
        }
        VmDataEntry template = new VmDataEntry("array_template_" + nextTemplate++, width, values);
        entries.put(template.label(), template);
        return Optional.of(template);
    }
    private Object constant(Ir.Expression expression) {
        if (expression instanceof Ir.Literal literal && (literal.value() instanceof Number
                || literal.value() instanceof Character || literal.value() instanceof Boolean)) return literal.value();
        if (expression instanceof Ir.Convert conversion && conversion.conversion() == Ir.Conversion.NUMERIC) {
            Object value = constant(conversion.operand());
            if (value instanceof Number number) {
                if (conversion.type().equals(Ir.Type.DOUBLE)) return number.doubleValue();
                if (conversion.type().equals(Ir.Type.INT)) return number.intValue();
            }
        }
        return null;
    }
    private void appendBytes(List<String> bytes, int value, int width) {
        for (int i = 0; i < width; i++) bytes.add("%02X".formatted((value >>> (i * 8)) & 255));
    }
    Collection<VmDataEntry> entries() { return entries.values(); }
}
