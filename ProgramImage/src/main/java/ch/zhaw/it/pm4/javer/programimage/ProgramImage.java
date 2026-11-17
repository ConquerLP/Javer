package ch.zhaw.it.pm4.javer.programimage;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Converts textual Javer bytecode into fixed-table PLC program images. */
public final class ProgramImage {
    private static final long DATA_BASE = 0x1000, CODE_BASE = 0x1000000, HEAP_BASE = 0x10000000;
    private final Path input;
    private final Map<String, Long> labels = new HashMap<>(), dataLabels = new HashMap<>();
    private final List<Source> sources = new ArrayList<>();
    private final List<Instruction> instructions = new ArrayList<>();
    private final List<DataObject> objects = new ArrayList<>();
    private long nextData = DATA_BASE;
    private long entry;

    private record Source(String text, int line) { }
    private record Instruction(Opcode opcode, long a, long b, int line) { }
    private record DataObject(long address, byte[] bytes) { }

    private ProgramImage(Path input) { this.input = input; }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: javer-program-image <input.jbc> <output.txt>");
            System.exit(2);
            return;
        }
        try {
            convert(Path.of(args[0]), Path.of(args[1]));
        } catch (IOException | RuntimeException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    /** Publishes only a complete image; existing output is preserved on conversion failure. */
    public static void convert(Path input, Path output) throws IOException {
        Path source = input.toRealPath();
        Path target = output.toAbsolutePath().normalize();
        if (source.equals(target) || (Files.exists(target) && Files.isSameFile(source, target))) {
            throw new IOException(input + ": input and output must be different files");
        }
        ProgramImage image = new ProgramImage(input);
        image.parse(Files.readAllLines(source, StandardCharsets.UTF_8));
        Path temporary = Files.createTempFile(target.getParent(), ".javer-image-", ".tmp");
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                image.write(writer);
            }
            // No non-atomic fallback: failure must not damage an existing image.
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private IOException error(int line, String message) {
        return new IOException(input + (line == 0 ? "" : ":" + line) + ": " + message);
    }

    private static boolean isLabel(String text) { return text.matches("^(_?[a-zA-Z]\\w*):$"); }

    private void parse(List<String> lines) throws IOException {
        int section = 0;
        String lastLabel = null;
        boolean enterSeen = false;
        for (int index = 0; index < lines.size(); index++) {
            int line = index + 1;
            String text = lines.get(index).split("//", 2)[0].trim();
            if (text.isEmpty()) continue;
            if (text.equals(".code")) {
                if (section != 0) throw error(line, "duplicate or misplaced .code section");
                section = 1;
                continue;
            }
            if (text.equals(".data")) {
                if (section != 1) throw error(line, "duplicate or misplaced .data section");
                section = 2;
                continue;
            }
            if (section == 0) continue; // Matches the VM's treatment of preamble lines.
            if (section == 2) {
                parseData(text, line);
            } else if (isLabel(text)) {
                lastLabel = text.substring(0, text.length() - 1);
                if (labels.putIfAbsent(lastLabel, CODE_BASE + sources.size()) != null) {
                    throw error(line, "duplicate label '" + lastLabel + "'");
                }
                if (lastLabel.startsWith("_")) enterSeen = false;
            } else {
                String name = text.split(",", -1)[0].trim().toUpperCase(Locale.ROOT);
                if (name.equals("ENTER")) {
                    if (lastLabel == null || !lastLabel.startsWith("_")) {
                        throw error(line, "ENTER must come directly after a function label (starting with _)");
                    }
                    if (enterSeen) throw error(line, "multiple ENTER instructions in function");
                    enterSeen = true;
                }
                lastLabel = null;
                sources.add(new Source(text, line));
                if (CODE_BASE + sources.size() >= HEAP_BASE) throw error(line, "code address range exhausted");
            }
        }
        if (section != 2) throw error(0, "required .code and .data sections missing");
        Long main = labels.get("_main");
        if (main == null) throw error(0, "missing _main function label");
        entry = main;
        for (Source source : sources) instructions.add(parseInstruction(source));
        instructions.add(new Instruction(Opcode.HALT, 0, 0, 0));
    }

    private void parseData(String text, int line) throws IOException {
        String[] parts = text.split("\\s+");
        if (parts.length < 3) throw error(line, "invalid data declaration");
        if (dataLabels.containsKey(parts[0])) throw error(line, "duplicate data object '" + parts[0] + "'");
        try {
            int size = Integer.parseInt(parts[1]);
            if (size != 1 && size != 2 && size != 4 && size != 8) throw error(line, "unsupported data element size " + size);
            // Deliberately matches the VM: comma-separated hexadecimal words in field 3.
            String[] values = parts[2].split(",");
            byte[] bytes = new byte[Math.multiplyExact(values.length, size)];
            for (int i = 0; i < values.length; i++) {
                long value = Long.parseUnsignedLong(values[i].trim(), 16);
                for (int b = 0; b < size; b++) bytes[i * size + b] = (byte) (value >> (8 * b));
            }
            long end = nextData + Math.max(bytes.length, 1);
            if (end > CODE_BASE) throw error(line, "data address range exhausted");
            dataLabels.put(parts[0], nextData);
            objects.add(new DataObject(nextData, bytes));
            nextData = end;
        } catch (NumberFormatException | ArithmeticException e) {
            throw error(line, "invalid numeric data declaration");
        }
    }

    private Instruction parseInstruction(Source source) throws IOException {
        String[] parts = Arrays.stream(source.text.split(",", -1)).map(String::trim).toArray(String[]::new);
        String name = parts[0].toUpperCase(Locale.ROOT);
        Opcode opcode;
        try { opcode = Opcode.valueOf(name); }
        catch (IllegalArgumentException e) { throw error(source.line, "unknown or unsupported instruction '" + name + "'"); }
        if (name.startsWith("PRINT")) throw error(source.line, "PRINT instructions are not supported by the PLC profile");
        int count = switch (opcode) {
            case CALL -> 3;
            case PUSHI, PUSHD, PUSHR, LOCAL, JUMP, JUMPT, JUMPF, ENTER, RET, NEWA, BOUNDS, POP, DUP -> 2;
            default -> 1;
        };
        if (parts.length != count) throw error(source.line, name + " expects " + (count - 1) + " operand(s)");
        long a = 0, b = 0;
        try {
            switch (opcode) {
                case PUSHD -> a = Double.doubleToRawLongBits(Double.parseDouble(parts[1]));
                case PUSHR -> a = resolve(dataLabels, parts[1], source.line, "data object");
                case CALL, JUMP, JUMPT, JUMPF -> {
                    boolean function = parts[1].startsWith("_");
                    if (function != (opcode == Opcode.CALL)) throw error(source.line, name + " has invalid target kind '" + parts[1] + "'");
                    a = resolve(labels, parts[1], source.line, "label");
                    if (opcode == Opcode.CALL) {
                        int value = Integer.parseInt(parts[2]);
                        if (value < 0) throw error(source.line, "CALL argument bytes must be non-negative");
                        b = value;
                    }
                }
                case PUSHI, LOCAL, ENTER, RET, NEWA, BOUNDS, POP, DUP -> {
                    int value = Integer.parseInt(parts[1]);
                    if (opcode == Opcode.ENTER && value < 0) throw error(source.line, "ENTER bytes must be non-negative");
                    if ((opcode == Opcode.NEWA || opcode == Opcode.BOUNDS) && value <= 0) throw error(source.line, name + " element size must be positive");
                    if ((opcode == Opcode.POP || opcode == Opcode.DUP || opcode == Opcode.RET)
                            && value != 4 && value != 8 && !(opcode == Opcode.RET && value == 0)) {
                        throw error(source.line, name + " size must be 4 or 8" + (opcode == Opcode.RET ? " or 0" : ""));
                    }
                    a = Integer.toUnsignedLong(value);
                }
                default -> { }
            }
        } catch (NumberFormatException e) { throw error(source.line, "invalid numeric operand for " + name); }
        return new Instruction(opcode, a, b, source.line);
    }

    private long resolve(Map<String, Long> symbols, String name, int line, String kind) throws IOException {
        Long address = symbols.get(name);
        if (address == null) throw error(line, "unknown " + kind + " '" + name + "'");
        return address;
    }

    private static String hex(long value, int width) { return String.format(Locale.ROOT, "%0" + width + "X", value); }
    private static void row(BufferedWriter out, String text) throws IOException { out.write(text); out.write('\n'); }

    private void write(BufferedWriter out) throws IOException {
        row(out, "HEADER 00000001");
        row(out, "ENTRY " + hex(entry, 8));
        row(out, "CODE " + hex(instructions.size(), 8));
        for (int i = 0; i < instructions.size(); i++) {
            Instruction instruction = instructions.get(i);
            row(out, "INSTR " + hex(CODE_BASE + i, 8) + " " + hex(instruction.opcode.id, 4)
                    + " " + hex(instruction.a, 16) + " " + hex(instruction.b, 16) + " " + hex(instruction.line, 8));
        }
        row(out, "OBJECTS " + hex(objects.size(), 8));
        long total = 0;
        for (DataObject object : objects) {
            row(out, "OBJECT " + hex(object.address, 8) + " " + hex(object.bytes.length, 8));
            total += object.bytes.length;
        }
        row(out, "DATA " + hex(total, 8));
        for (DataObject object : objects) {
            for (int offset = 0; offset < object.bytes.length; offset += 32) {
                StringBuilder bytes = new StringBuilder();
                for (int i = offset; i < Math.min(offset + 32, object.bytes.length); i++) bytes.append(hex(object.bytes[i] & 255, 2));
                row(out, "BYTES " + hex(object.address + offset, 8) + " " + bytes);
            }
        }
        row(out, "END");
    }
}
