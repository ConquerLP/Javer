package ch.zhaw.it.pm4.javer.compiler.backend.vm;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.typeinfo.TypeInfo;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.typeinfo.UnknownTypeInfo;

import java.util.List;

/**
 * A static data constant encoded for VM output.
 */
public final class VmDataEntry  {

    private final String label;
    private final TypeInfo type;
    private final Object value;
    private final int elementSizeBytes;

    public VmDataEntry(String label, TypeInfo type, Object value, int elementSizeBytes) {
        this.label = label;
        this.type = type == null ? UnknownTypeInfo.INSTANCE : type;
        this.value = value;
        this.elementSizeBytes = Math.max(elementSizeBytes, 1);
    }

    public String getLabel() {
        return label;
    }

    public TypeInfo getType() {
        return type;
    }

    public Object getValue() {
        return value;
    }

    @Override
    public String toString() {
        if (value instanceof List<?> values) {
            return "%s %d %s".formatted(getLabel(), elementSizeBytes, String.join(",", values.stream()
                    .map(String::valueOf)
                    .toList()));
        }
        return "%s %d %s".formatted(getLabel(), elementSizeBytes, value);
    }
}
