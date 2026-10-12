package ch.zhaw.it.pm4.javer.vm;


import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.IntBinaryOperator;

/**
 * Stack-based virtual machine for Javer bytecode.
 */
public class VM {

    public static final String CODE_SECTION = ".code";
    public static final String DATA_SECTION = ".data";
    public static final String SEPARATOR = "========================================";
    public static final String LINE = "Line ";
    public static final String SIZE = ", size=";
    public static final String INVALID_KIND = "Invalid kind: ";
    public static final String ERROR = "Error";
    private static final int DEFAULT_STACK_SIZE = 1024 * 1024;
    private static final int MAX_STACK_SIZE = 16 * 1024 * 1024;
    private static final int STACK_WINDOW = 32;
    private static final int NULL_REF = 0;
    private static final int DATA_BASE = 0x00001000;
    private static final int CODE_BASE = 0x01000000;
    private static final int HEAP_BASE = 0x10000000;
    private static final long ADDRESS_SPACE_SIZE = 1L << 32;
    private static final int FRAME_LOCAL_BYTES_OFFSET = 0;
    private static final int FRAME_ARG_BYTES_OFFSET = 4;
    private static final int FRAME_RETURN_PC_OFFSET = 8;
    private static final int FRAME_PREVIOUS_FP_OFFSET = 12;
    private static final int FRAME_HEADER_SIZE = 16;
    private static final int ARRAY_LENGTH_BYTES = 4;
    private static final int ARRAY_PAYLOAD_OFFSET_BYTES = ARRAY_LENGTH_BYTES;
    private static final long STACK_LIMIT = ADDRESS_SPACE_SIZE;
    private static final IntBinaryOperator LT = (a, b) -> a < b ? 1 : 0;
    private static final IntBinaryOperator LE = (a, b) -> a <= b ? 1 : 0;
    private static final IntBinaryOperator GT = (a, b) -> a > b ? 1 : 0;
    private static final IntBinaryOperator GE = (a, b) -> a >= b ? 1 : 0;
    private final byte[] stack;
    private final long stackBase;
    private final Map<Integer, Instruction> code = new HashMap<>();
    private final Map<Integer, Integer> instructionLineNumbers = new HashMap<>();
    private final Map<String, Integer> dataLabels = new HashMap<>();
    private final Map<String, Integer> labels = new HashMap<>();
    private final TreeMap<Integer, MemoryRegion> regions = new TreeMap<>(Integer::compareUnsigned);
    private final List<String> lines;

    private long sp;
    private long fp;
    private int pc;
    private int nextDataAddress = DATA_BASE;
    private int nextHeapAddress = HEAP_BASE;
    private int programEndAddress = CODE_BASE;
    private boolean halted;

    /**
     * Loads and parses a bytecode program with a custom VM stack size.
     *
     * @param filePath       path to the bytecode file
     * @param stackSizeBytes stack size in bytes
     * @throws IOException    if the bytecode file cannot be read
     * @throws ParseException if bytecode parsing fails
     */
    public VM(String filePath, int stackSizeBytes) throws IOException, ParseException {
        validateStackSize(stackSizeBytes);
        this.stack = new byte[stackSizeBytes];
        this.stackBase = ADDRESS_SPACE_SIZE - stackSizeBytes;
        this.sp = STACK_LIMIT;
        this.fp = 0;
        this.lines = Files.readAllLines(Paths.get(filePath), StandardCharsets.UTF_8);
        addRegion(new MemoryRegion((int) stackBase, stackSizeBytes, true, "stack", stack));
        parse();
    }

    /**
     * Starts the VM for a bytecode file.
     *
     * @param args first argument is the bytecode file path
     */
    public static void main(String[] args) {
        if (args.length == 0 || containsHelpOption(args)) {
            printUsage(System.out);
            return;
        }

        VMOptions options;
        try {
            options = parseOptions(args);
        } catch (OptionParseException e) {
            System.err.println(e.getMessage());
            printUsage(System.err);
            return;
        }

        VM vm = null;
        try {
            vm = new VM(options.filePath(), options.stackSizeBytes());
            vm.run();
        } catch (ParseException e) {
            System.err.println("Program contains parse errors. Execution aborted.");
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
        } catch (VMExecutionException e) {
            System.err.println("Runtime error: " + e.getMessage());
            if (options.dumpOnRuntimeError() && vm != null) {
                vm.dumpState();
            }
        }
    }

    private static boolean containsHelpOption(String[] args) {
        for (String arg : args) {
            if ("--help".equals(arg) || "-h".equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private static void printUsage(PrintStream out) {
        out.println("Usage: java VM [options] <filePath>");
        out.println("Options:");
        out.println("  --stack-size <size>    Stack size in bytes, K/KB or M/MB (default 1M, max 16M)");
        out.println("  --dump-on-error        Print VM state dump after a runtime error");
        out.println("  -h, --help             Show this help");
    }

    private static VMOptions parseOptions(String[] args) throws OptionParseException {
        int stackSizeBytes = DEFAULT_STACK_SIZE;
        boolean dumpOnRuntimeError = false;
        String filePath = null;

        int i = 0;
        while (i < args.length) {
            String arg = args[i];
            if ("--stack-size".equals(arg)) {
                if (i + 1 >= args.length) {
                    throw new OptionParseException("Missing value for --stack-size");
                }
                i++;
                stackSizeBytes = parseStackSize(args[i]);
            } else if (arg.startsWith("--stack-size=")) {
                stackSizeBytes = parseStackSize(arg.substring("--stack-size=".length()));
            } else if ("--dump-on-error".equals(arg)) {
                dumpOnRuntimeError = true;
            } else if (arg.startsWith("-")) {
                throw new OptionParseException("Unknown option: " + arg);
            } else if (filePath == null) {
                filePath = arg;
            } else {
                throw new OptionParseException("Unexpected argument: " + arg);
            }

            i++;
        }

        if (filePath == null) {
            throw new OptionParseException("Missing bytecode file path");
        }

        return new VMOptions(filePath, stackSizeBytes, dumpOnRuntimeError);
    }

    private static int parseStackSize(String rawValue) throws OptionParseException {
        String value = rawValue.trim().replace("_", "");
        if (value.isEmpty()) {
            throw new OptionParseException("Invalid --stack-size value: " + rawValue);
        }

        String upper = value.toUpperCase(Locale.ROOT);
        long multiplier = 1;
        String number = upper;
        String substring = upper.substring(0, upper.length() - 2);
        if (upper.endsWith("KB")) {
            multiplier = 1024;
            number = substring;
        } else if (upper.endsWith("K")) {
            multiplier = 1024;
            number = upper.substring(0, upper.length() - 1);
        } else if (upper.endsWith("MB")) {
            multiplier = 1024 * 1024L;
            number = substring;
        } else if (upper.endsWith("M")) {
            multiplier = 1024 * 1024L;
            number = upper.substring(0, upper.length() - 1);
        }

        try {
            long stackSize = Math.multiplyExact(Long.parseLong(number), multiplier);
            if (stackSize < 1 || stackSize > MAX_STACK_SIZE) {
                throw new OptionParseException(
                        "--stack-size must be between 1 and " + MAX_STACK_SIZE + " bytes"
                );
            }
            return (int) stackSize;
        } catch (NumberFormatException | ArithmeticException e) {
            throw new OptionParseException("Invalid --stack-size value: " + rawValue);
        }
    }

    private static void validateStackSize(int stackSizeBytes) {
        if (stackSizeBytes < 1 || stackSizeBytes > MAX_STACK_SIZE) {
            throw new IllegalArgumentException(
                    "stackSizeBytes must be between 1 and " + MAX_STACK_SIZE + " bytes"
            );
        }
    }

    private void parse() throws ParseException {
        List<String> errors = new ArrayList<>();
        List<PendingJumpCheck> pendingJumpChecks = new ArrayList<>();

        SectionBounds result = findSections(errors);

        if (result.codeLineIndex() == -1) {
            errors.add("Missing required '.code' section");
        }
        if (result.dataLineIndex() == -1) {
            errors.add("Missing required '.data' section");
        }
        if (!errors.isEmpty()) {
            printErrors(errors);
            throw new ParseException(errors);
        }

        programEndAddress = analyzeCodeSection(result, errors);
        allocateCodeRegion(Integer.compareUnsigned(programEndAddress, CODE_BASE) < 0
                ? 0
                : programEndAddress - CODE_BASE + 1);

        parseInstructions(result, pendingJumpChecks, errors);

        validateEnterInstructions(result.codeLineIndex(), result.dataLineIndex(), errors);

        parseDataSection(result, errors);

        validatePendingJumps(pendingJumpChecks, errors);

        finalizeProgram(errors);
    }

    private void finalizeProgram(List<String> errors) throws ParseException {
        code.put(programEndAddress, VM::halt);

        if (!errors.isEmpty()) {
            printErrors(errors);
            throw new ParseException(errors);
        }
    }

    private void validatePendingJumps(List<PendingJumpCheck> pendingJumpChecks, List<String> errors) {
        for (PendingJumpCheck check : pendingJumpChecks) {
            if (!labels.containsKey(check.labelName())) {
                errors.add(LINE + check.lineNumber() + ": unknown label '" + check.labelName() + "'");
            }
        }
    }

    private void parseDataSection(SectionBounds result, List<String> errors) {
        for (int i = result.dataLineIndex() + 1; i < lines.size(); i++) {
            handleDataLine(errors, i);
        }
    }

    private void handleDataLine(List<String> errors, int i) {
        String line = stripComment(lines.get(i)).trim();
        if (!line.isEmpty()) {
            if (line.equals(CODE_SECTION) || line.equals(DATA_SECTION)) {
                errors.add(LINE + (i + 1) + ": section marker not allowed inside data section");
            } else {
                if (isLabel(line)) {
                    errors.add(LINE + (i + 1) + ": labels are only allowed in code section");
                } else {
                    try {
                        parseDataLine(line, i + 1);
                    } catch (ParseException e) {
                        errors.addAll(e.getErrors());
                    }
                }
            }
        }
    }

    private void parseInstructions(SectionBounds result, List<PendingJumpCheck> pendingJumpChecks, List<String> errors) {
        int instructionAddress = CODE_BASE;
        for (int i = result.codeLineIndex() + 1; i < result.dataLineIndex(); i++) {
            String line = stripComment(lines.get(i)).trim();
            if (line.isEmpty() || isLabel(line)) {
                continue;
            }
            try {
                Instruction instruction = parseInstruction(line, i + 1, pendingJumpChecks);
                code.put(instructionAddress, instruction);
                instructionLineNumbers.put(instructionAddress, i + 1);
                instructionAddress++;
            } catch (ParseException e) {
                errors.addAll(e.getErrors());
            }
        }
    }

    private int analyzeCodeSection(SectionBounds result, List<String> errors) {
        int instructionAddress = CODE_BASE;
        for (int i = result.codeLineIndex() + 1; i < result.dataLineIndex(); i++) {
            String line = stripComment(lines.get(i)).trim();
            if (!line.isEmpty()) {
                if (line.equals(CODE_SECTION) || line.equals(DATA_SECTION)) {
                    errors.add(LINE + (i + 1) + ": nested section marker not allowed inside code section");
                } else if (isLabel(line)) {
                    String labelName = extractLabelName(line);
                    if (labels.containsKey(labelName)) {
                        errors.add(LINE + (i + 1) + ": duplicate label '" + labelName + "'");
                    } else {
                        labels.put(labelName, instructionAddress);
                    }
                } else {
                    instructionAddress = nextCodeAddress(instructionAddress, i + 1, errors);
                }
            }
        }
        return instructionAddress;
    }

    private SectionBounds findSections(List<String> errors) {
        int codeLineIndex = -1;
        int dataLineIndex = -1;

        for (int i = 0; i < lines.size(); i++) {
            String line = stripComment(lines.get(i)).trim();
            switch (line) {
                case CODE_SECTION -> {
                    if (codeLineIndex != -1) {
                        errors.add(LINE + (i + 1) + ": duplicate '.code' section");
                    } else {
                        codeLineIndex = i;
                    }
                }
                case DATA_SECTION -> {
                    if (codeLineIndex == -1) {
                        errors.add(LINE + (i + 1) + ": '.data' section must appear after '.code'");
                    } else if (dataLineIndex != -1) {
                        errors.add(LINE + (i + 1) + ": duplicate '.data' section");
                    } else {
                        dataLineIndex = i;
                    }
                }
                default -> {
                    // line that can be ignored.
                }
            }
        }
        return new SectionBounds(codeLineIndex, dataLineIndex);
    }

    private int nextCodeAddress(int address, int lineNumber, List<String> errors) {
        long next = Integer.toUnsignedLong(address) + 1;
        if (next >= Integer.toUnsignedLong(HEAP_BASE)) {
            errors.add(LINE + lineNumber + ": code section exceeds reserved address range");
            return address;
        }
        return (int) next;
    }

    private void allocateCodeRegion(int size) {
        if (size <= 0) {
            return;
        }
        long end = Integer.toUnsignedLong(VM.CODE_BASE) + size;
        if (end > Integer.toUnsignedLong(HEAP_BASE)) {
            throw new VMExecutionException("Code section exceeds reserved address range");
        }
        addRegion(new MemoryRegion(VM.CODE_BASE, size, false, "code", new byte[size]));
    }

    private void validateEnterInstructions(int codeLineIndex, int dataLineIndex, List<String> errors) {
        String lastLabelName = null;
        boolean lastWasLabel = false;
        String currentFunctionLabel = null;
        boolean enterSeenInCurrentFunction = false;

        for (int i = codeLineIndex + 1; i < dataLineIndex; i++) {
            String line = stripComment(lines.get(i)).trim();

            if (line.isEmpty()) {
                continue;
            }

            if (isLabel(line)) {
                lastLabelName = extractLabelName(line);
                lastWasLabel = true;

                if (lastLabelName.startsWith("_")) {
                    currentFunctionLabel = lastLabelName;
                    enterSeenInCurrentFunction = false;
                }

                continue;
            }

            enterSeenInCurrentFunction = validateEnterInstruction(
                    line,
                    i,
                    lastWasLabel,
                    lastLabelName,
                    currentFunctionLabel,
                    enterSeenInCurrentFunction,
                    errors
            );

            lastWasLabel = false;
        }
    }

    private boolean validateEnterInstruction(
            String line,
            int lineIndex,
            boolean lastWasLabel,
            String lastLabelName,
            String currentFunctionLabel,
            boolean enterSeenInCurrentFunction,
            List<String> errors
    ) {
        String instrName = line.split(",")[0]
                .trim()
                .toUpperCase(Locale.ROOT);

        if (!"ENTER".equals(instrName)) {
            return enterSeenInCurrentFunction;
        }

        if (!lastWasLabel
                || lastLabelName == null
                || !lastLabelName.startsWith("_")) {

            errors.add(LINE + (lineIndex + 1)
                    + ": ENTER must come directly after a function label (starting with _)");
        }

        if (enterSeenInCurrentFunction) {
            errors.add(LINE + (lineIndex + 1)
                    + ": multiple ENTER instructions in function '"
                    + currentFunctionLabel
                    + "' are not allowed");
        }

        return true;
    }

    private Instruction parseInstruction(String line, int lineNumber, List<PendingJumpCheck> pendingJumpChecks)
            throws ParseException {
        String[] parts = splitOperands(line);
        if (parts.length == 0 || parts[0].isBlank()) {
            throw new ParseException(LINE + lineNumber + ": empty instruction");
        }

        String instrName = parts[0].trim().toUpperCase(Locale.ROOT);
        InstructionKind kind;
        try {
            kind = InstructionKind.valueOf(instrName);
        } catch (IllegalArgumentException e) {
            throw new ParseException(LINE + lineNumber + ": unknown instruction '" + parts[0].trim() + "'");
        }

        return buildInstruction(kind, parts, instrName, lineNumber, pendingJumpChecks);
    }

    private Instruction buildInstruction(
            InstructionKind kind,
            String[] parts,
            String instrName,
            int lineNumber,
            List<PendingJumpCheck> pendingJumpChecks
    ) throws ParseException {

        return switch (kind) {
            case PUSHI, PUSHD, PUSHR, LOCAL -> pushCase(kind, parts, instrName, lineNumber);
            case LOAD1 -> noOperand(parts, instrName, lineNumber, vm -> vm.pushInt(vm.readByte(vm.popInt())));
            case LOAD2 -> noOperand(parts, instrName, lineNumber, vm -> vm.pushInt(vm.readChar(vm.popInt())));
            case LOAD4 -> noOperand(parts, instrName, lineNumber, vm -> vm.pushInt(vm.readInt(vm.popInt())));
            case LOAD8 -> noOperand(parts, instrName, lineNumber, vm -> vm.pushDouble(vm.readDouble(vm.popInt())));
            case STORE1 -> noOperand(parts, instrName, lineNumber, vm -> {
                int value = vm.popInt();
                int address = vm.popInt();
                vm.writeByte(address, (byte) value);
            });
            case STORE2 -> noOperand(parts, instrName, lineNumber, vm -> {
                int value = vm.popInt();
                int address = vm.popInt();
                vm.writeChar(address, (char) value);
            });
            case STORE4 -> noOperand(parts, instrName, lineNumber, vm -> {
                int value = vm.popInt();
                int address = vm.popInt();
                vm.writeInt(address, value);
            });
            case STORE8 -> noOperand(parts, instrName, lineNumber, vm -> {
                double value = vm.popDouble();
                int address = vm.popInt();
                vm.writeDouble(address, value);
            });
            case NEW -> noOperand(parts, instrName, lineNumber, VM::executeNew);
            case NEWA -> {
                ensureOperandCount(parts, 2, instrName, lineNumber);
                int elementSize = parsePositiveIntOperand(parts[1], instrName, lineNumber);
                yield vm -> vm.executeNewArray(elementSize);
            }
            case MEMCPY -> noOperand(parts, instrName, lineNumber, VM::executeMemcopy);
            case BOUNDS -> {
                ensureOperandCount(parts, 2, instrName, lineNumber);
                int elementSize = parsePositiveIntOperand(parts[1], instrName, lineNumber);
                yield vm -> vm.executeBoundsCheck(elementSize);
            }
            case POP -> {
                ensureOperandCount(parts, 2, instrName, lineNumber);
                int size = parseStackValueSize(parts[1], instrName, lineNumber, false);
                yield vm -> vm.popRaw(size);
            }
            case DUP -> {
                ensureOperandCount(parts, 2, instrName, lineNumber);
                int size = parseStackValueSize(parts[1], instrName, lineNumber, false);
                yield vm -> vm.pushRaw(vm.peekRaw(size));
            }
            case IADD, ISUB, IMUL, IDIV, IMOD, DADD, DSUB, DMUL, DDIV ->
                    arithmeticCase(kind, parts, instrName, lineNumber);
            case ILT, ILE, IGT, IGE, IEQ, INE -> integerComparisonCase(kind, parts, instrName, lineNumber);
            case DLT, DLE, DGT, DGE, DEQ, DNE -> doubleComparisonCase(kind, parts, instrName, lineNumber);
            case STREQ ->
                    noOperand(parts, instrName, lineNumber, vm -> vm.pushInt(vm.compareStrings(vm.popInt(), vm.popInt()) ? 1 : 0));
            case STRNE ->
                    noOperand(parts, instrName, lineNumber, vm -> vm.pushInt(!vm.compareStrings(vm.popInt(), vm.popInt()) ? 1 : 0));
            case ISHL -> noOperand(parts, instrName, lineNumber, vm -> {
                int b = vm.popInt();
                int a = vm.popInt();
                vm.pushInt(a << b);
            });
            case ISHR -> noOperand(parts, instrName, lineNumber, vm -> {
                int b = vm.popInt();
                int a = vm.popInt();
                vm.pushInt(a >> b);
            });
            case IAND -> noOperand(parts, instrName, lineNumber, vm -> vm.pushInt(vm.popInt() & vm.popInt()));
            case IOR -> noOperand(parts, instrName, lineNumber, vm -> vm.pushInt(vm.popInt() | vm.popInt()));
            case IXOR -> noOperand(parts, instrName, lineNumber, vm -> vm.pushInt(vm.popInt() ^ vm.popInt()));
            case INEG -> noOperand(parts, instrName, lineNumber, vm -> vm.pushInt(-vm.popInt()));
            case DNEG -> noOperand(parts, instrName, lineNumber, vm -> vm.pushDouble(-vm.popDouble()));
            case IINV -> noOperand(parts, instrName, lineNumber, vm -> vm.pushInt(~vm.popInt()));
            case I2D -> noOperand(parts, instrName, lineNumber, vm -> vm.pushDouble(vm.popInt()));
            case D2I -> noOperand(parts, instrName, lineNumber, vm -> vm.pushInt((int) vm.popDouble()));
            case JUMP -> parseJump(parts, instrName, lineNumber, pendingJumpChecks, false, false);
            case JUMPT -> parseJump(parts, instrName, lineNumber, pendingJumpChecks, true, true);
            case JUMPF -> parseJump(parts, instrName, lineNumber, pendingJumpChecks, true, false);
            case CALL -> {
                ensureOperandCount(parts, 3, instrName, lineNumber);
                String label = parseLabelOperand(parts[1], instrName, lineNumber);
                if (!label.startsWith("_")) {
                    throw new ParseException(LINE + lineNumber + ": CALL must target a function label (starting with _), got '" + label + "'");
                }
                int argBytes = parseIntOperand(parts[2], instrName, lineNumber);
                if (argBytes < 0) {
                    throw new ParseException(LINE + lineNumber + ": CALL argBytes must be non-negative, got " + argBytes);
                }
                pendingJumpChecks.add(new PendingJumpCheck(label, lineNumber));
                yield vm -> {
                    if (argBytes > vm.currentOperandBytes()) {
                        throw new VMExecutionException("CALL: not enough bytes on stack for " + argBytes + " argument byte(s)");
                    }
                    vm.pushFrame(vm.pc + 1, argBytes);
                    vm.pc = vm.resolveLabel(label) - 1;
                };
            }
            case ENTER -> {
                ensureOperandCount(parts, 2, instrName, lineNumber);
                int size = parseIntOperand(parts[1], instrName, lineNumber);
                if (size < 0) {
                    throw new ParseException(LINE + lineNumber + ": ENTER size must be non-negative, got " + size);
                }
                yield vm -> vm.allocateStackBytes(size);
            }
            case RET -> {
                ensureOperandCount(parts, 2, instrName, lineNumber);
                int returnBytes = parseStackValueSize(parts[1], instrName, lineNumber, true);
                yield vm -> vm.returnFromFrame(returnBytes);
            }
            case HALT -> noOperand(parts, instrName, lineNumber, VM::halt);
            case PRINTB -> noOperand(parts, instrName, lineNumber, vm -> System.out.print((char) (vm.popInt() & 0xFF)));
            case PRINTC -> noOperand(parts, instrName, lineNumber, vm -> System.out.print((char) vm.popInt()));
            case PRINTI -> noOperand(parts, instrName, lineNumber, vm -> System.out.print(vm.popInt()));
            case PRINTD -> noOperand(parts, instrName, lineNumber, vm -> System.out.print(vm.popDouble()));
            case PRINTS -> noOperand(parts, instrName, lineNumber, vm -> vm.printNullTerminatedCharString(vm.popInt()));
        };
    }

    private Instruction pushCase(InstructionKind kind,
                                 String[] parts,
                                 String instrName,
                                 int lineNumber) throws ParseException {
        return switch (kind) {
            case PUSHI -> {
                ensureOperandCount(parts, 2, instrName, lineNumber);
                int value = parseIntOperand(parts[1], instrName, lineNumber);
                yield vm -> vm.pushInt(value);
            }
            case PUSHD -> {
                ensureOperandCount(parts, 2, instrName, lineNumber);
                double value = parseDoubleOperand(parts[1], instrName, lineNumber);
                yield vm -> vm.pushDouble(value);
            }
            case PUSHR -> {
                ensureOperandCount(parts, 2, instrName, lineNumber);
                String name = parseIdentifier(parts[1], instrName, lineNumber);
                yield vm -> vm.pushInt(vm.makeDataReference(name));
            }
            case LOCAL -> {
                ensureOperandCount(parts, 2, instrName, lineNumber);
                int offset = parseIntOperand(parts[1], instrName, lineNumber);
                yield vm -> vm.pushInt(vm.frameAddress(offset, "LOCAL"));
            }
            default -> {
                yield vm -> vm.pushInt(vm.frameAddress(-1, ERROR));
            }
        };
    }

    private Instruction arithmeticCase(InstructionKind kind,
                                       String[] parts,
                                       String instrName,
                                       int lineNumber) throws ParseException {
        return switch (kind) {
            case IADD -> noOperand(parts, instrName, lineNumber, vm -> vm.pushInt(vm.popInt() + vm.popInt()));
            case ISUB -> noOperand(parts, instrName, lineNumber, vm -> {
                int b = vm.popInt();
                int a = vm.popInt();
                vm.pushInt(a - b);
            });
            case IMUL -> noOperand(parts, instrName, lineNumber, vm -> vm.pushInt(vm.popInt() * vm.popInt()));
            case IDIV -> noOperand(parts, instrName, lineNumber, vm -> {
                int b = vm.popInt();
                int a = vm.popInt();
                if (b == 0) {
                    throw new VMExecutionException("Division by zero");
                }
                vm.pushInt(a / b);
            });
            case IMOD -> noOperand(parts, instrName, lineNumber, vm -> {
                int b = vm.popInt();
                int a = vm.popInt();
                if (b == 0) {
                    throw new VMExecutionException("Modulo by zero");
                }
                vm.pushInt(a % b);
            });
            case DADD -> noOperand(parts, instrName, lineNumber, vm -> vm.pushDouble(vm.popDouble() + vm.popDouble()));
            case DSUB -> noOperand(parts, instrName, lineNumber, vm -> {
                double b = vm.popDouble();
                double a = vm.popDouble();
                vm.pushDouble(a - b);
            });
            case DMUL -> noOperand(parts, instrName, lineNumber, vm -> vm.pushDouble(vm.popDouble() * vm.popDouble()));
            case DDIV -> noOperand(parts, instrName, lineNumber, vm -> {
                double b = vm.popDouble();
                double a = vm.popDouble();
                if (b == 0.0) {
                    throw new VMExecutionException("Division by zero");
                }
                vm.pushDouble(a / b);
            });
            default -> {
                yield vm -> vm.pushInt(vm.frameAddress(-1, ERROR));
            }
        };
    }

    private Instruction integerComparisonCase(InstructionKind kind,
                                              String[] parts,
                                              String instrName,
                                              int lineNumber) throws ParseException {
        return switch (kind) {
            case ILT -> noOperand(parts, instrName, lineNumber, vm -> compareInts(vm, LT));
            case ILE -> noOperand(parts, instrName, lineNumber, vm -> compareInts(vm, LE));
            case IGT -> noOperand(parts, instrName, lineNumber, vm -> compareInts(vm, GT));
            case IGE -> noOperand(parts, instrName, lineNumber, vm -> compareInts(vm, GE));
            case IEQ -> noOperand(parts, instrName, lineNumber, vm -> vm.pushInt(vm.popInt() == vm.popInt() ? 1 : 0));
            case INE -> noOperand(parts, instrName, lineNumber, vm -> vm.pushInt(vm.popInt() != vm.popInt() ? 1 : 0));
            default -> {
                yield vm -> vm.pushInt(vm.frameAddress(-1, ERROR));
            }
        };
    }

    private void compareInts(VM vm, IntBinaryOperator op) {
        int b = vm.popInt();
        int a = vm.popInt();
        vm.pushInt(op.applyAsInt(a, b));
    }

    private Instruction doubleComparisonCase(InstructionKind kind,
                                             String[] parts,
                                             String instrName,
                                             int lineNumber) throws ParseException {
        return switch (kind) {
            case DLT -> noOperand(parts, instrName, lineNumber,
                    vm -> compareDoubles(vm, (a, b) -> a < b));

            case DLE -> noOperand(parts, instrName, lineNumber,
                    vm -> compareDoubles(vm, (a, b) -> a <= b));

            case DGT -> noOperand(parts, instrName, lineNumber,
                    vm -> compareDoubles(vm, (a, b) -> a > b));

            case DGE -> noOperand(parts, instrName, lineNumber,
                    vm -> compareDoubles(vm, (a, b) -> a >= b));
            case DEQ ->
                    noOperand(parts, instrName, lineNumber, vm -> vm.pushInt(vm.popDouble() == vm.popDouble() ? 1 : 0));
            case DNE ->
                    noOperand(parts, instrName, lineNumber, vm -> vm.pushInt(vm.popDouble() != vm.popDouble() ? 1 : 0));
            default -> {
                yield vm -> vm.pushInt(vm.frameAddress(-1, ERROR));
            }
        };
    }

    private void compareDoubles(VM vm, BiPredicate<Double, Double> op) {
        double b = vm.popDouble();
        double a = vm.popDouble();
        vm.pushInt(op.test(a, b) ? 1 : 0);
    }

    private Instruction parseJump(
            String[] parts,
            String instrName,
            int lineNumber,
            List<PendingJumpCheck> pendingJumpChecks,
            boolean conditional,
            boolean jumpOnTrue) throws ParseException {
        ensureOperandCount(parts, 2, instrName, lineNumber);
        String label = parseLabelOperand(parts[1], instrName, lineNumber);
        if (label.startsWith("_")) {
            throw new ParseException(LINE + lineNumber + ": " + instrName + " must target a normal label (without _), got '" + label + "'");
        }
        pendingJumpChecks.add(new PendingJumpCheck(label, lineNumber));
        if (!conditional) {
            return vm -> vm.pc = vm.resolveLabel(label) - 1;
        }
        return vm -> {
            boolean condition = vm.popInt() != 0;
            if (condition == jumpOnTrue) {
                vm.pc = vm.resolveLabel(label) - 1;
            }
        };
    }

    private String[] splitOperands(String line) {
        String[] raw = line.split(",");
        String[] trimmed = new String[raw.length];
        for (int i = 0; i < raw.length; i++) {
            trimmed[i] = raw[i].trim();
        }
        return trimmed;
    }

    private void parseDataLine(String line, int lineNumber) throws ParseException {
        String[] parts = line.split("\\s+");
        if (parts.length < 3) {
            throw new ParseException(LINE + lineNumber + ": invalid data declaration");
        }

        String name = parts[0];
        if (dataLabels.containsKey(name)) {
            throw new ParseException(LINE + lineNumber + ": duplicate data symbol '" + name + "'");
        }

        int size;
        try {
            size = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            throw new ParseException(LINE + lineNumber + ": invalid data element size '" + parts[1] + "'");
        }
        if (size != 1 && size != 2 && size != 4 && size != 8) {
            throw new ParseException(LINE + lineNumber + ": unsupported data element size " + size);
        }

        String[] hexes = parts[2].split(",");
        List<Byte> byteList = new ArrayList<>();
        try {
            for (String hex : hexes) {
                long value = Long.parseUnsignedLong(hex.trim(), 16);
                for (int i = 0; i < size; i++) {
                    byteList.add((byte) (value >> (8 * i)));
                }
            }
        } catch (NumberFormatException e) {
            throw new ParseException(LINE + lineNumber + ": invalid hex literal in data section");
        }

        byte[] dataBytes = new byte[byteList.size()];
        for (int i = 0; i < byteList.size(); i++) {
            dataBytes[i] = byteList.get(i);
        }
        dataLabels.put(name, allocateDataRegion(name, dataBytes));
    }

    private void ensureOperandCount(String[] parts, int expectedCount, String instrName, int lineNumber)
            throws ParseException {
        if (parts.length != expectedCount) {
            throw new ParseException(
                    LINE + lineNumber + ": instruction '" + instrName + "' expects "
                            + (expectedCount - 1) + " operand(s), got " + (parts.length - 1)
            );
        }
    }

    private Instruction noOperand(String[] parts, String instrName, int lineNumber, Instruction instruction)
            throws ParseException {
        ensureOperandCount(parts, 1, instrName, lineNumber);
        return instruction;
    }

    private int parseStackValueSize(String operand, String instrName, int lineNumber, boolean allowZero)
            throws ParseException {
        int size = parseIntOperand(operand, instrName, lineNumber);
        if (allowZero && size == 0) {
            return size;
        }
        if (size != 4 && size != 8) {
            String message = LINE + lineNumber + ": " + instrName + " size must be 4 or 8" + (allowZero ? " or 0" : "") + ", got " + size;
            throw new ParseException(message);
        }
        return size;
    }

    private int parseIntOperand(String operand, String instrName, int lineNumber) throws ParseException {
        try {
            return Integer.parseInt(operand.trim());
        } catch (NumberFormatException e) {
            throw new ParseException(LINE + lineNumber + ": invalid int operand '" + operand + "' for instruction '" + instrName + "'");
        }
    }

    private int parsePositiveIntOperand(String operand, String instrName, int lineNumber) throws ParseException {
        int value = parseIntOperand(operand, instrName, lineNumber);
        if (value <= 0) {
            throw new ParseException(LINE + lineNumber + ": " + instrName + " operand must be positive, got " + value);
        }
        return value;
    }

    private double parseDoubleOperand(String operand, String instrName, int lineNumber) throws ParseException {
        try {
            return Double.parseDouble(operand.trim());
        } catch (NumberFormatException e) {
            throw new ParseException(LINE + lineNumber + ": invalid double operand '" + operand + "' for instruction '" + instrName + "'");
        }
    }

    private String parseLabelOperand(String operand, String instrName, int lineNumber) throws ParseException {
        String value = operand.trim();
        if (value.isEmpty()) {
            throw new ParseException(LINE + lineNumber + ": missing label operand for instruction '" + instrName + "'");
        }
        return value;
    }

    private String parseIdentifier(String operand, String instrName, int lineNumber)
            throws ParseException {
        String value = operand.trim();
        if (value.isEmpty()) {
            throw new ParseException(LINE + lineNumber + ": missing data name for instruction '" + instrName + "'");
        }
        return value;
    }

    private String stripComment(String line) {
        int index = line.indexOf("//");
        return index >= 0 ? line.substring(0, index) : line;
    }

    private boolean isLabel(String line) {
        return line.matches("^(_?[a-zA-Z]\\w*):$");
    }

    private String extractLabelName(String line) {
        return line.substring(0, line.length() - 1);
    }

    private void printErrors(List<String> errors) {
        for (String error : errors) {
            System.err.println(error);
        }
    }

    /**
     * Executes the loaded program from the {@code _main} label.
     */
    public void run() {
        Integer main = labels.get("_main");
        if (main == null) {
            throw new VMExecutionException("No _main function found");
        }

        pushFrame(-1, 0);
        pc = main;

        while (!halted && code.containsKey(pc)) {
            int instructionAddress = pc;
            Instruction instruction = code.get(pc);
            try {
                instruction.execute(this);
            } catch (VMExecutionException e) {
                throw withInstructionLine(instructionAddress, e);
            }
            if (!halted) {
                pc++;
            }
        }
    }

    private VMExecutionException withInstructionLine(int instructionAddress, VMExecutionException exception) {
        Integer lineNumber = instructionLineNumbers.get(instructionAddress);
        if (lineNumber == null) {
            return exception;
        }
        return new VMExecutionException(LINE + lineNumber + ": " + exception.getMessage(), exception);
    }

    private void halt() {
        halted = true;
        pc = programEndAddress;
    }

    private int resolveLabel(String labelName) {
        Integer target = labels.get(labelName);
        if (target == null) {
            throw new VMExecutionException("Label '" + labelName + "' not found");
        }
        return target;
    }

    private int makeDataReference(String name) {
        Integer address = dataLabels.get(name);
        if (address == null) {
            throw new VMExecutionException("Data object '" + name + "' not found");
        }
        return address;
    }

    private int allocateDataRegion(String name, byte[] bytes) {
        int base = nextDataAddress;
        long end = Integer.toUnsignedLong(base) + Math.max(bytes.length, 1);
        if (end > Integer.toUnsignedLong(CODE_BASE)) {
            throw new VMExecutionException("Data section exceeds reserved address range");
        }
        addRegion(new MemoryRegion(base, bytes.length, false, "data:" + name, bytes));
        nextDataAddress = (int) end;
        return base;
    }

    private int allocateHeapRegion(int size) {
        if (size < 0) {
            throw new VMExecutionException("Negative heap allocation size: " + size);
        }
        int base = nextHeapAddress;
        long end = Integer.toUnsignedLong(base) + Math.max(size, 1);
        if (end > stackBase) {
            throw new VMExecutionException("Heap/stack address space exhausted");
        }
        addRegion(new MemoryRegion(base, size, true, "heap:" + formatAddress(base), new byte[size]));
        nextHeapAddress = (int) end;
        return base;
    }

    private void addRegion(MemoryRegion region) {
        long base = Integer.toUnsignedLong(region.base());
        long end = base + Math.max(region.size(), 0);

        Map.Entry<Integer, MemoryRegion> previous = regions.floorEntry(region.base());
        if (previous != null && previous.getKey().equals(region.base())) {
            throw new VMExecutionException("Duplicate memory region at " + formatAddress(region.base()));
        }
        if (previous != null && regionEnd(previous.getValue()) > base) {
            throw new VMExecutionException("Memory region overlap at " + formatAddress(region.base()));
        }

        Map.Entry<Integer, MemoryRegion> next = regions.ceilingEntry(region.base());
        if (next != null && end > Integer.toUnsignedLong(next.getKey())) {
            throw new VMExecutionException("Memory region overlap at " + formatAddress(region.base()));
        }

        regions.put(region.base(), region);
    }

    private long regionEnd(MemoryRegion region) {
        return Integer.toUnsignedLong(region.base()) + Math.max(region.size(), 0);
    }

    private MemoryAccess resolveRegion(int address, int size) {
        if (size < 0) {
            throw new VMExecutionException("Negative memory access size: " + size);
        }
        if (address == NULL_REF) {
            throw new VMExecutionException("Null reference");
        }

        long start = Integer.toUnsignedLong(address);
        long end = start + size;
        if (end > ADDRESS_SPACE_SIZE) {
            throw new VMExecutionException("Memory access out of bounds (address=" + formatAddress(address) + SIZE + size + ")");
        }

        Map.Entry<Integer, MemoryRegion> entry = regions.floorEntry(address);
        if (entry == null) {
            throw new VMExecutionException("Invalid memory address: " + formatAddress(address));
        }

        MemoryRegion region = entry.getValue();
        long regionBase = Integer.toUnsignedLong(region.base());
        long regionEnd = regionEnd(region);
        if (start < regionBase || end > regionEnd) {
            throw new VMExecutionException(
                    region.name() + " access out of bounds (address=" + formatAddress(address) + SIZE + size + ")"
            );
        }

        return new MemoryAccess(region, (int) (start - regionBase));
    }

    private MemoryAccess resolveWritableRegion(int address, int size) {
        MemoryAccess access = resolveRegion(address, size);
        if (!access.region().writable()) {
            throw new VMExecutionException(access.region().name() + " is read-only");
        }
        return access;
    }

    private byte readByte(int address) {
        MemoryAccess access = resolveRegion(address, 1);
        return access.region().bytes()[access.offset()];
    }

    private char readChar(int address) {
        MemoryAccess access = resolveRegion(address, 2);
        byte[] bytes = access.region().bytes();
        int offset = access.offset();
        return (char) (((bytes[offset + 1] & 0xFF) << 8) | (bytes[offset] & 0xFF));
    }

    private int readInt(int address) {
        MemoryAccess access = resolveRegion(address, 4);
        byte[] bytes = access.region().bytes();
        int offset = access.offset();
        int value = 0;
        for (int i = 0; i < 4; i++) {
            value |= (bytes[offset + i] & 0xFF) << (8 * i);
        }
        return value;
    }

    private double readDouble(int address) {
        MemoryAccess access = resolveRegion(address, 8);
        byte[] bytes = access.region().bytes();
        int offset = access.offset();
        long bits = 0;
        for (int i = 0; i < 8; i++) {
            bits |= ((long) (bytes[offset + i] & 0xFF)) << (8 * i);
        }
        return Double.longBitsToDouble(bits);
    }

    private void writeByte(int address, byte value) {
        MemoryAccess access = resolveWritableRegion(address, 1);
        access.region().bytes()[access.offset()] = value;
    }

    private void writeChar(int address, char value) {
        MemoryAccess access = resolveWritableRegion(address, 2);
        byte[] bytes = access.region().bytes();
        int offset = access.offset();
        bytes[offset] = (byte) (value & 0xFF);
        bytes[offset + 1] = (byte) ((value >> 8) & 0xFF);
    }

    private void writeInt(int address, int value) {
        MemoryAccess access = resolveWritableRegion(address, 4);
        byte[] bytes = access.region().bytes();
        int offset = access.offset();
        for (int i = 0; i < 4; i++) {
            bytes[offset + i] = (byte) (value & 0xFF);
            value >>= 8;
        }
    }

    private void writeDouble(int address, double value) {
        MemoryAccess access = resolveWritableRegion(address, 8);
        byte[] bytes = access.region().bytes();
        int offset = access.offset();
        long bits = Double.doubleToLongBits(value);
        for (int i = 0; i < 8; i++) {
            bytes[offset + i] = (byte) (bits & 0xFF);
            bits >>= 8;
        }
    }

    private void executeMemcopy() {
        int size = popInt();
        int sourceAddress = popInt();
        int targetAddress = popInt();
        if (size < 0) {
            throw new VMExecutionException("MEMCPY: negative byte count " + size);
        }
        if (size == 0) {
            return;
        }

        MemoryAccess source = resolveRegion(sourceAddress, size);
        MemoryAccess target = resolveWritableRegion(targetAddress, size);
        System.arraycopy(source.region().bytes(), source.offset(), target.region().bytes(), target.offset(), size);
    }

    private void executeNew() {
        int size = popInt();
        pushInt(allocateHeapRegion(size));
    }

    private void executeNewArray(int elementSize) {
        int length = popInt();
        if (length < 0) {
            throw new VMExecutionException("Negative array length: " + length);
        }

        long payloadBytes = (long) length * elementSize;
        long allocationBytes = ARRAY_PAYLOAD_OFFSET_BYTES + payloadBytes;
        if (allocationBytes > Integer.MAX_VALUE) {
            throw new VMExecutionException("Array allocation too large: length=" + length + ", elementSize=" + elementSize);
        }

        int address = allocateHeapRegion((int) allocationBytes);
        writeInt(address, length);
        pushInt(address);
    }

    private void executeBoundsCheck(int elementSize) {
        int index = peekInt(0);
        int baseAddress = peekInt(4);
        MemoryAccess access = resolveRegion(baseAddress, ARRAY_LENGTH_BYTES);
        if (access.offset() != 0) {
            throw new VMExecutionException("Array reference does not point to allocation start: " + formatAddress(baseAddress));
        }

        int length = readInt(baseAddress);
        long requiredBytes = ARRAY_PAYLOAD_OFFSET_BYTES + (long) length * elementSize;
        if (length < 0 || requiredBytes > access.region().size()) {
            throw new VMExecutionException("Invalid array header at " + formatAddress(baseAddress));
        }
        if (index < 0 || index >= length) {
            throw new VMExecutionException("Array index out of bounds: index=" + index + ", length=" + length);
        }
    }

    private void pushFrame(int returnPc, int argBytes) {
        pushInt((int) fp);
        pushInt(returnPc);
        pushInt(argBytes);
        pushInt(0);
        fp = sp;
    }

    private void returnFromFrame(int returnBytes) {
        byte[] returnValue = returnBytes == 0 ? new byte[0] : popRaw(returnBytes);

        int argBytes = readInt(checkedAddress(fp + FRAME_ARG_BYTES_OFFSET, "RET"));
        int returnPc = readInt(checkedAddress(fp + FRAME_RETURN_PC_OFFSET, "RET"));
        int previousFp = readInt(checkedAddress(fp + FRAME_PREVIOUS_FP_OFFSET, "RET"));

        sp = fp + FRAME_HEADER_SIZE + argBytes;
        if (sp < stackBase || sp > STACK_LIMIT) {
            throw new VMExecutionException("RET: restored stack pointer is out of bounds");
        }
        fp = Integer.toUnsignedLong(previousFp);

        if (returnPc == -1) {
            halted = true;
            return;
        }

        pushRaw(returnValue);
        pc = returnPc - 1;
    }

    private int frameAddress(int offset, String sourceName) {
        int localBytes = readInt(checkedAddress(fp + FRAME_LOCAL_BYTES_OFFSET, sourceName));
        return checkedAddress(fp - localBytes + offset, sourceName);
    }

    private int checkedAddress(long address, String sourceName) {
        if (address <= 0 || address >= ADDRESS_SPACE_SIZE) {
            throw new VMExecutionException(sourceName + ": address out of bounds: " + formatAddress(address));
        }
        return (int) address;
    }

    private void allocateStackBytes(int size) {
        writeInt(checkedAddress(fp + FRAME_LOCAL_BYTES_OFFSET, "ENTER"), size);
        if (size == 0) {
            return;
        }
        ensureStackCapacity(size);
        sp -= size;
        Arrays.fill(stack, stackOffset(sp), stackOffset(sp + size), (byte) 0);
    }

    private int stackBytesUsed() {
        long used = STACK_LIMIT - sp;
        if (used > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) used;
    }

    private int currentOperandBytes() {
        if (fp == 0) {
            return stackBytesUsed();
        }
        int localBytes = readInt(checkedAddress(fp + FRAME_LOCAL_BYTES_OFFSET, "CALL"));
        long operandBase = fp - localBytes;
        long available = operandBase - sp;
        if (available < 0) {
            return 0;
        }
        if (available > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) available;
    }

    private void ensureStackCapacity(int bytesToPush) {
        if (bytesToPush < 0) {
            throw new VMExecutionException("Negative stack push size: " + bytesToPush);
        }
        if (sp - bytesToPush < stackBase) {
            throw new VMExecutionException("Stack overflow while pushing " + bytesToPush + " byte(s)");
        }
    }

    private void ensureStackAvailable(int bytesToPop) {
        if (bytesToPop < 0) {
            throw new VMExecutionException("Negative stack pop size: " + bytesToPop);
        }
        if (sp + bytesToPop > STACK_LIMIT) {
            throw new VMExecutionException("Stack underflow while popping " + bytesToPop + " byte(s)");
        }
    }

    private void pushRaw(byte[] bytes) {
        ensureStackCapacity(bytes.length);
        sp -= bytes.length;
        System.arraycopy(bytes, 0, stack, stackOffset(sp), bytes.length);
    }

    private byte[] popRaw(int size) {
        byte[] value = peekRaw(size);
        sp += size;
        return value;
    }

    private byte[] peekRaw(int size) {
        ensureStackAvailable(size);
        byte[] value = new byte[size];
        System.arraycopy(stack, stackOffset(sp), value, 0, size);
        return value;
    }

    private void pushInt(int value) {
        byte[] bytes = new byte[4];
        for (int i = 0; i < 4; i++) {
            bytes[i] = (byte) (value & 0xFF);
            value >>= 8;
        }
        pushRaw(bytes);
    }

    private int popInt() {
        byte[] bytes = popRaw(4);
        return intFromBytes(bytes, 0);
    }

    private int peekInt(int offsetBytes) {
        ensureStackAvailable(offsetBytes + 4);
        return intFromBytes(stack, stackOffset(sp + offsetBytes));
    }

    private int intFromBytes(byte[] bytes, int offset) {
        int value = 0;
        for (int i = 0; i < 4; i++) {
            value |= (bytes[offset + i] & 0xFF) << (8 * i);
        }
        return value;
    }

    private void pushDouble(double value) {
        byte[] bytes = new byte[8];
        long bits = Double.doubleToLongBits(value);
        for (int i = 0; i < 8; i++) {
            bytes[i] = (byte) (bits & 0xFF);
            bits >>= 8;
        }
        pushRaw(bytes);
    }

    private double popDouble() {
        byte[] bytes = popRaw(8);
        long bits = 0;
        for (int i = 0; i < 8; i++) {
            bits |= ((long) (bytes[i] & 0xFF)) << (8 * i);
        }
        return Double.longBitsToDouble(bits);
    }

    private int stackOffset(long address) {
        long offset = address - stackBase;
        if (offset < 0 || offset > stack.length) {
            throw new VMExecutionException("Stack address out of bounds: " + formatAddress(address));
        }
        return (int) offset;
    }

    private void printNullTerminatedCharString(int address) {
        MemoryAccess access = resolveRegion(address, ARRAY_LENGTH_BYTES);
        if (access.offset() != 0) {
            throw new VMExecutionException("PRINTS " + formatAddress(address) + ": string reference does not point to allocation start");
        }
        int length = readInt(address);
        long payloadBytes = (long) length * Character.BYTES;
        if (length < 0 || payloadBytes > Integer.MAX_VALUE) {
            throw new VMExecutionException("PRINTS " + formatAddress(address) + ": invalid string length " + length);
        }
        resolveRegion(address + ARRAY_PAYLOAD_OFFSET_BYTES, (int) payloadBytes);
        byte[] bytes = access.region().bytes();
        int offset = ARRAY_PAYLOAD_OFFSET_BYTES;
        for (int i = 0; i < length; i++, offset += Character.BYTES) {
            char c = (char) (((bytes[offset + 1] & 0xFF) << 8) | (bytes[offset] & 0xFF));
            System.out.print(c);
        }
    }

    private boolean compareStrings(int rightAddress, int leftAddress) {
        if (leftAddress == NULL_REF || rightAddress == NULL_REF) {
            return leftAddress == rightAddress;
        }

        MemoryAccess leftAccess = resolveRegion(leftAddress, ARRAY_LENGTH_BYTES);
        MemoryAccess rightAccess = resolveRegion(rightAddress, ARRAY_LENGTH_BYTES);
        if (leftAccess.offset() != 0 || rightAccess.offset() != 0) {
            throw new VMExecutionException("String reference does not point to allocation start");
        }

        int leftLength = readInt(leftAddress);
        int rightLength = readInt(rightAddress);
        if (leftLength < 0 || rightLength < 0) {
            throw new VMExecutionException("Invalid string length");
        }
        if (leftLength != rightLength) {
            return false;
        }

        int leftOffset = ARRAY_PAYLOAD_OFFSET_BYTES;
        int rightOffset = ARRAY_PAYLOAD_OFFSET_BYTES;
        for (int i = 0; i < leftLength; i++) {
            char left = readChar(leftAddress + leftOffset);
            char right = readChar(rightAddress + rightOffset);
            if (left != right) {
                return false;
            }
            leftOffset += Character.BYTES;
            rightOffset += Character.BYTES;
        }
        return true;
    }

    private String formatAddress(int address) {
        return "0x%08X".formatted(address);
    }

    private String formatAddress(long address) {
        if (address == ADDRESS_SPACE_SIZE) {
            return "0x100000000";
        }
        return formatAddress((int) address);
    }

    private void dumpState() {
        System.err.println(SEPARATOR);
        System.err.println("VM STATE DUMP");
        System.err.println("pc = " + formatAddress(pc));
        System.err.println("sp = " + formatAddress(sp));
        System.err.println("fp = " + formatAddress(fp));
        System.err.println("halted = " + halted);
        System.err.println("programEndAddress = " + formatAddress(programEndAddress));
        System.err.println(SEPARATOR);
        dumpStackWindow();
        System.err.println();
        dumpRegions();
        System.err.println(SEPARATOR);
    }

    private void dumpStackWindow() {
        System.err.println("=== STACK WINDOW ===");
        int used = stackBytesUsed();
        if (used == 0) {
            System.err.println("<empty>");
            return;
        }

        int count = Math.min(used, STACK_WINDOW);
        int offset = stackOffset(sp);
        for (int i = 0; i < count; i++) {
            long address = sp + i;
            System.err.printf("%s 0x%02X%n", formatAddress(address), stack[offset + i] & 0xFF);
        }
    }

    private void dumpRegions() {
        System.err.println("=== MEMORY REGIONS ===");
        for (MemoryRegion region : regions.values()) {
            System.err.printf(
                    "%s %s..%s %s size=%d%n",
                    region.name(),
                    formatAddress(region.base()),
                    formatAddress(regionEnd(region)),
                    region.writable() ? "rw" : "ro",
                    region.size()
            );
        }
    }

    private enum InstructionKind {
        PUSHI, PUSHD, PUSHR,
        LOCAL,
        LOAD1, LOAD2, LOAD4, LOAD8,
        STORE1, STORE2, STORE4, STORE8,
        NEW, NEWA, MEMCPY, BOUNDS,
        POP, DUP,
        IADD, ISUB, IMUL, IDIV, IMOD,
        DADD, DSUB, DMUL, DDIV,
        ILT, ILE, IGT, IGE, IEQ, INE,
        DLT, DLE, DGT, DGE, DEQ, DNE,
        STREQ, STRNE,
        ISHL, ISHR,
        IAND, IOR, IXOR,
        INEG, DNEG, IINV,
        I2D, D2I,
        JUMP, JUMPT, JUMPF,
        CALL, ENTER, RET,
        HALT,
        PRINTB, PRINTC, PRINTI, PRINTD, PRINTS
    }

    @FunctionalInterface
    private interface Instruction {
        void execute(VM vm);
    }

    private record SectionBounds(int codeLineIndex, int dataLineIndex) {
    }

    private record PendingJumpCheck(String labelName, int lineNumber) {
    }

    private record VMOptions(String filePath, int stackSizeBytes, boolean dumpOnRuntimeError) {
    }

    private record MemoryRegion(int base, int size, boolean writable, String name, byte[] bytes) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MemoryRegion(int base1, int size1, boolean writable1, String name1, byte[] bytes1)))
                return false;

            return base == base1
                    && size == size1
                    && writable == writable1
                    && java.util.Objects.equals(name, name1)
                    && java.util.Arrays.equals(bytes, bytes1);
        }

        @Override
        public int hashCode() {
            int result = java.util.Objects.hash(base, size, writable, name);
            result = 31 * result + java.util.Arrays.hashCode(bytes);
            return result;
        }

        @Override
        public String toString() {
            return "MemoryRegion{" +
                    "base=" + base +
                    SIZE + size +
                    ", writable=" + writable +
                    ", name='" + name + '\'' +
                    ", bytes=" + java.util.Arrays.toString(bytes) +
                    '}';
        }
    }

    private record MemoryAccess(MemoryRegion region, int offset) {
    }

    public static final class ParseException extends Exception {
        private final List<String> errors;

        ParseException(String error) {
            super(error);
            this.errors = List.of(error);
        }

        ParseException(List<String> errors) {
            super(String.join(System.lineSeparator(), errors));
            this.errors = List.copyOf(errors);
        }

        List<String> getErrors() {
            return errors;
        }
    }

    private static final class OptionParseException extends Exception {
        OptionParseException(String message) {
            super(message);
        }
    }

    private static final class VMExecutionException extends RuntimeException {
        VMExecutionException(String message) {
            super(message);
        }

        VMExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
