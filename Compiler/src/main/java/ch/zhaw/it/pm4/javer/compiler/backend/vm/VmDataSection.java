package ch.zhaw.it.pm4.javer.compiler.backend.vm;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.type.PrimitiveTypeKind;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.symbol.EnumEntry;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.symbol.EnumValueEntry;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.typeinfo.PrimitiveTypeInfo;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.typeinfo.TypeInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects static data entries encoded for the VM bytecode target.
 */
final class VmDataSection {

    private final Map<String, VmDataEntry> entries = new LinkedHashMap<>();
    private final Map<String, VmDataEntry> strings = new LinkedHashMap<>();
    private int nextArrayTemplateId;

    private static List<String> encodeString(String value) {
        List<String> values = new ArrayList<>();
        appendIntBytes(values, value.length());
        for (char c : value.toCharArray()) {
            appendCharBytes(values, c);
        }
        return values;
    }

    private static void appendIntBytes(List<String> values, int value) {
        for (int i = 0; i < VmLayout.WORD_BYTES; i++) {
            values.add("%02X".formatted((value >> (i * Byte.SIZE)) & 0xFF));
        }
    }

    private static void appendCharBytes(List<String> values, char value) {
        for (int i = 0; i < VmLayout.CHAR_BYTES; i++) {
            values.add("%02X".formatted((value >> (i * Byte.SIZE)) & 0xFF));
        }
    }

    private static String encodeValue(Object value, int width) {
        return switch (value) {
            case Boolean bool -> unsignedHex(bool ? 1 : 0, width);
            case Character character -> unsignedHex(character, width);
            case Double doubleValue -> "%016X".formatted(Double.doubleToLongBits(doubleValue));
            case Number number -> unsignedHex(number.longValue(), width);
            case null, default -> unsignedHex(0, width);
        };
    }

    private static String unsignedHex(long value, int width) {
        long mask = width >= Long.BYTES ? -1L : (1L << (width * Byte.SIZE)) - 1L;
        String format = "%0" + (width * 2) + "X";
        return String.format(format, value & mask);
    }

    public VmDataEntry internString(String value) {
        VmDataEntry existing = strings.get(value);
        if (existing != null) {
            return existing;
        }

        String label = "string_" + strings.size();
        List<String> encoded = encodeString(value);
        VmDataEntry entry = addConstant(label, PrimitiveTypeInfo.of(PrimitiveTypeKind.CHAR), encoded, VmLayout.BYTE_BYTES);
        strings.put(value, entry);
        return entry;
    }

    public VmDataEntry addConstant(String label, TypeInfo type, Object value, int elementSizeBytes) {
        VmDataEntry entry = new VmDataEntry(label, type, value, elementSizeBytes);
        entries.put(label, entry);
        return entry;
    }

    public void addEnumValues(EnumEntry enumEntry, VmMetadata.VmEnumLayout layout) {
        if (entries.containsKey(layout.dataLabel())) {
            return;
        }
        if (enumEntry.getScope() == null || enumEntry.getScope().getValues().isEmpty()) {
            return;
        }

        List<String> values = enumEntry.getScope().getValues().values().stream()
                .map(EnumValueEntry::getValue)
                .map(value -> unsignedHex(value, VmLayout.WORD_BYTES))
                .toList();
        addConstant(layout.dataLabel(), PrimitiveTypeInfo.INT, values, layout.elementSizeBytes());
    }

    public VmDataEntry addArrayTemplate(TypeInfo elementType, List<Object> values) {
        String label = "array_template_" + nextArrayTemplateId++;
        int width = VmLayout.memoryWidth(elementType).bytes();
        List<String> encoded = values.stream()
                .map(value -> encodeValue(value, width))
                .toList();
        return addConstant(label, elementType, encoded, width);
    }

    public Map<String, VmDataEntry> getEntries() {
        return entries;
    }
}
