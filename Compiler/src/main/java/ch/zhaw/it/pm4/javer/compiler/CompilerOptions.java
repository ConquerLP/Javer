package ch.zhaw.it.pm4.javer.compiler;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Validated command-line configuration for one compiler run.
 */
public class CompilerOptions {

    private static final String SOURCE_FILE_EXTENSION = ".javer";

    private final List<Path> inputPaths;
    private final String outputFilePath;
    private final CompilationTarget target;
    private final boolean dumpLexer;
    private final boolean dumpAst;
    private final boolean dumpIr;
    private final boolean dumpSymbolTable;

    private CompilerOptions(
            List<Path> inputPaths,
            String outputFilePath,
            CompilationTarget target,
            boolean dumpLexer,
            boolean dumpAst,
            boolean dumpIr,
            boolean dumpSymbolTable) {

        this.inputPaths = List.copyOf(inputPaths);
        this.outputFilePath = outputFilePath;
        this.target = target;
        this.dumpLexer = dumpLexer;
        this.dumpAst = dumpAst;
        this.dumpIr = dumpIr;
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
        List<String> inputArguments = new ArrayList<>();
        String outputFilePath = null;
        CompilationTarget target = CompilationTarget.VM;
        boolean dumpLexer = false;
        boolean dumpAst = false;
        boolean dumpIr = false;
        boolean dumpSymbolTable = false;

        if (args.length == 2 && !args[0].startsWith("-") && !args[1].startsWith("-")) {
            inputArguments.add(args[0]);
            outputFilePath = args[1];
        } else {
            int i = 0;
            while (i < args.length) {
                String arg = args[i];
                switch (arg) {
                    case "--in-file", "-i" -> {
                        i++;
                        inputArguments.add(readRequiredValue(args, i, arg));
                        while (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                            inputArguments.add(args[++i]);
                        }
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
                    case "--dump-ir" -> dumpIr = true;
                    case "--dump-symboltable" -> dumpSymbolTable = true;
                    default -> {
                        throw new CompilerOptionException("Unknown compiler option: " + arg);
                    }
                }
                i++;
            }
        }

        if (inputArguments.isEmpty() || outputFilePath == null) {
            throw new CompilerOptionException("Missing input or output files.");
        }

        List<Path> inputPaths = new ArrayList<>();
        for (String argument : inputArguments) {
            inputPaths.addAll(expandInput(argument));
        }
        outputFilePath = normalizeOutputFilePath(outputFilePath, target);

        return new CompilerOptions(
                inputPaths,
                outputFilePath,
                target,
                dumpLexer,
                dumpAst,
                dumpIr,
                dumpSymbolTable
        );
    }

    private static String readRequiredValue(String[] args, int index, String optionName) {
        if (index >= args.length || args[index].startsWith("-")) {
            throw new CompilerOptionException("Missing value for compiler option: " + optionName);
        }
        return args[index];
    }

    private static List<Path> expandInput(String argument) {
        if (!argument.endsWith(SOURCE_FILE_EXTENSION)) {
            throw new CompilerOptionException(
                    "Input file must have extension " + SOURCE_FILE_EXTENSION + ": " + argument);
        }
        try {
            if (!argument.contains("*")) {
                return List.of(Path.of(argument));
            }

            // Windows Path rejects '*', so separate the directory before creating a Path.
            int separator = argument.lastIndexOf('/');
            if (java.io.File.separatorChar == '\\') {
                separator = Math.max(separator, argument.lastIndexOf('\\'));
            }
            String directory = argument.substring(0, separator + 1);
            if (directory.contains("*")) {
                throw new CompilerOptionException("Wildcards are only supported in file names: " + argument);
            }
            Path parent = Path.of(directory.isEmpty() ? "." : directory);
            String filename = argument.substring(separator + 1);
            Pattern pattern = Pattern.compile(Arrays.stream(filename.split("\\*", -1))
                    .map(Pattern::quote)
                    .collect(Collectors.joining(".*")), Pattern.DOTALL);
            List<Path> matches;
            try (var entries = Files.list(parent)) {
                matches = entries
                        .filter(path -> pattern.matcher(path.getFileName().toString()).matches())
                        .filter(Files::isRegularFile)
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .toList();
            }
            if (matches.isEmpty()) {
                throw new CompilerOptionException("No source files match: " + argument);
            }
            return matches;
        } catch (InvalidPathException exception) {
            throw new CompilerOptionException("Invalid input path: " + argument);
        } catch (IOException | UncheckedIOException exception) {
            throw new CompilerOptionException("Cannot list source files for pattern: " + argument);
        }
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
        return inputPaths.getFirst().toString();
    }

    /** @return input paths in command-line order after expanding patterns */
    public List<Path> getInputPaths() {
        return inputPaths;
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

    public boolean isDumpIr() { return dumpIr; }

    public static String usage() {
        return "Usage: compiler --in-file <source.javer|pattern>... --out-file <output-path-without-extension> "
                + "[--target vm|fanuc-macro-b] [--dump-lexer] [--dump-ast] [--dump-ir] [--dump-symboltable]";
    }

    public static class CompilerOptionException extends IllegalArgumentException {
        public CompilerOptionException(String message) {
            super(message);
        }
    }
}
