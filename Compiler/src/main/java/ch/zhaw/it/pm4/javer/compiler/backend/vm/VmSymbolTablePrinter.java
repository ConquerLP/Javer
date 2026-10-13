package ch.zhaw.it.pm4.javer.compiler.backend.vm;

import ch.zhaw.it.pm4.javer.compiler.ir.Ir;

/** Dumps frozen IR symbol identities together with VM layouts. */
final class VmSymbolTablePrinter {
    String print(Ir.Program program, VmMetadata metadata) {
        StringBuilder text = new StringBuilder("target: vm\n");
        for (Ir.Signature signature : program.signatures()) {
            VmMetadata.FunctionLayout layout = metadata.function(signature.id());
            text.append("function ").append(signature.name()).append(" #").append(signature.id())
                    .append(" | return: ").append(signature.returnType()).append(" | label: ").append(layout.label())
                    .append(" | parameter bytes: ").append(layout.parameterBytes()).append(" | local bytes: ").append(layout.localBytes()).append('\n');
        }
        for (Ir.Function function : program.functions()) {
            text.append("frame: ").append(function.signature().name()).append('\n');
            function.parameters().forEach(slot -> storage(text, slot, metadata));
            function.locals().forEach(slot -> storage(text, slot, metadata));
        }
        for (Ir.Struct struct : program.structs()) {
            text.append("struct ").append(struct.name()).append(" | size bytes: ").append(metadata.structBytes(struct.id())).append('\n');
            struct.fields().forEach(slot -> storage(text, slot, metadata));
        }
        for (Ir.Enumeration enumeration : program.enums()) {
            text.append("enum ").append(enumeration.name()).append(" | data label: ").append(metadata.enumLabel(enumeration.id())).append('\n');
            enumeration.values().forEach(v -> text.append("  ").append(v.name()).append(" = ").append(v.value())
                    .append(" | offset bytes: ").append(metadata.enumOffset(v.id())).append('\n'));
        }
        return text.toString();
    }
    private void storage(StringBuilder text, Ir.Storage slot, VmMetadata metadata) {
        VmMetadata.StorageLayout layout = metadata.storage(slot.id());
        text.append("  ").append(slot.kind()).append(' ').append(slot.name()).append(" #").append(slot.id())
                .append(" | type: ").append(slot.type()).append(" | size bytes: ").append(layout.sizeBytes())
                .append(" | offset bytes: ").append(layout.offsetBytes()).append('\n');
    }
}
