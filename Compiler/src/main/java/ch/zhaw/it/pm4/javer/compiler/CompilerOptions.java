package ch.zhaw.it.pm4.javer.compiler;

import java.nio.file.Path;

/**
 * Validated command-line configuration for one compiler run.
 */
public class CompilerOptions {

    private static final String SOURCE_FILE_EXTENSION = ".javer";

    // Configuration for a single compilation run.
    //
    // - inputFilePath
    // - outputFilePath
    // - dumpLexer
    // - dumpAst
    // - dumpSymbolTable

    private final String inputFilePath;
    private final String outputFilePath;
    private final CompilationTarget target;
    private final boolean dumpLexer;
    private final boolean dumpAst;
    private final boolean dumpSymbolTable;

    private CompilerOptions(
            String inputFilePath,
            String outputFilePath,
            CompilationTarget target,
            boolean dumpLexer,
            boolean dumpAst,
            boolean dumpSymbolTable) {

        this.inputFilePath = inputFilePath;
        this.outputFilePath = outputFilePath;
        this.target = target;
        this.dumpLexer = dumpLexer;
        this.dumpAst = dumpAst;
        this.dumpSymbolTable = dumpSymbolTable;
    }

    /**
     * Parses and validates compiler command-line arguments.
     *
     * @param args command-line options
     * @return validated compiler options
     * @throws CompilerOptionException if an option is unknown, missing, or has
     *                                 an invalid path value
     */
    public static CompilerOptions create(String... args) {
        String inputFilePath = null;
        String outputFilePath = null;
        CompilationTarget target = CompilationTarget.VM;
        boolean dumpLexer = false;
        boolean dumpAst = false;
        boolean dumpSymbolTable = false;

        if (args.length == 2 && !args[0].startsWith("-") && !args[1].startsWith("-")) {
            inputFilePath = args[0];
            outputFilePath = args[1];
        } else {
            int i = 0;
            while (i < args.length) {
                String arg = args[i];
                switch (arg) {
                    case "--in-file", "-i" -> {
                        i++;
                        inputFilePath = readRequiredValue(args, i, arg);
                    }
                    case "--out-file", "-o" -> {
                        i++;
                        outputFilePath = readRequiredValue(args, i, arg);
                    }
                    case "--target" -> {
                        i++;
                        target = CompilationTarget.parse(readRequiredValue(args, i, arg));
                    }
                    case "--dump-lexer" -> dumpLexer = true;
                    case "--dump-ast" -> dumpAst = true;
                    case "--dump-symboltable" -> dumpSymbolTable = true;
                    default -> {
                        throw new CompilerOptionException("Unknown compiler option: " + arg);
                    }
                }
                i++;
            }
        }

        if (inputFilePath == null || outputFilePath == null) {
            throw new CompilerOptionException("Missing input or output files.");
        }

        inputFilePath = validateInputFilePath(inputFilePath);
        outputFilePath = normalizeOutputFilePath(outputFilePath, target);

        return new CompilerOptions(
                inputFilePath,
                outputFilePath,
                target,
                dumpLexer,
                dumpAst,
                dumpSymbolTable
        );
    }

    private static String readRequiredValue(String[] args, int index, String optionName) {
        if (index >= args.length || args[index].startsWith("-")) {
            throw new CompilerOptionException("Missing value for compiler option: " + optionName);
        }
        return args[index];
    }

    private static String validateInputFilePath(String inputFilePath) {
        String fileName = fileName(inputFilePath);
        if (!fileName.endsWith(SOURCE_FILE_EXTENSION)) {
            throw new CompilerOptionException(
                    "Input file must have extension " + SOURCE_FILE_EXTENSION + ": " + inputFilePath);
        }
        return inputFilePath;
    }

    private static String normalizeOutputFilePath(String outputFilePath, CompilationTarget target) {
        String fileName = fileName(outputFilePath);
        String outputExtension = target.getOutputExtension();
        if (fileName.endsWith(outputExtension)) {
            throw new CompilerOptionException(
                    "Output path must be provided without " + outputExtension
                            + " extension: " + outputFilePath);
        }
        if (fileName.contains(".")) {
            throw new CompilerOptionException(
                    "Output path must be provided without file extension: " + outputFilePath);
        }
        return outputFilePath + outputExtension;
    }

    private static String fileName(String path) {
        Path fileName = Path.of(path).getFileName();
        return fileName == null ? path : fileName.toString();
    }

    /**
     * @return source file path with a {@code .javer} extension
     */
    public String getInputFilePath() {
        return inputFilePath;
    }

    /**
     * @return normalized bytecode output file path with a {@code .jbc}
     * extension
     */
    public String getOutputFilePath() {
        return outputFilePath;
    }

    /**
     * @return selected compiler output target
     */
    public CompilationTarget getTarget() {
        return target;
    }

    /**
     * @return true when token dump output should be printed
     */
    public boolean isDumpLexer() {
        return dumpLexer;
    }

    /**
     * @return true when AST dump output should be printed
     */
    public boolean isDumpAst() {
        return dumpAst;
    }

    /**
     * @return true when symbol-table dump output should be printed
     */
    public boolean isDumpSymbolTable() {
        return dumpSymbolTable;
    }

    public static String usage() {
        return "Usage: compiler --in-file <source.javer> --out-file <output-path-without-extension> "
                + "[--target vm|fanuc-macro-b] [--dump-lexer] [--dump-ast] [--dump-symboltable]";
    }

    public static class CompilerOptionException extends IllegalArgumentException {
        public CompilerOptionException(String message) {
            super(message);
        }
    }
}
