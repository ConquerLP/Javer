package ch.zhaw.it.pm4.javer.compiler.preprocessor;

import ch.zhaw.it.pm4.javer.compiler.misc.SourceLocation;
import ch.zhaw.it.pm4.javer.compiler.misc.diagnostics.Diagnostic;
import ch.zhaw.it.pm4.javer.compiler.misc.diagnostics.DiagnosticBag;
import ch.zhaw.it.pm4.javer.compiler.misc.diagnostics.Severity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small C-style text preprocessor for file inclusion.
 */
public class SourcePreprocessor {

    private static final String SOURCE_FILE_EXTENSION = ".javer";
    private static final int MAX_INCLUDE_DEPTH = 100;
    private static final SourceLocation UNKNOWN_LOCATION = new SourceLocation(1, 1, 1);

    private static final Pattern INCLUDE_PATTERN = Pattern.compile("#\\s*include\\s+[\"<]([^\">]+)[\">]\\s*");
    private static final Pattern PRAGMA_ONCE_PATTERN = Pattern.compile("#\\s*pragma\\s+once\\s*");
    private static final Pattern ELSE_PATTERN = Pattern.compile("#\\s*else\\s*");
    private static final Pattern ENDIF_PATTERN = Pattern.compile("#\\s*endif\\s*");
    private static final Pattern SYMBOL_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern SYMBOL_DIRECTIVE_PATTERN =
            Pattern.compile("#\\s*(ifdef|ifndef|define|undef)\\s+([A-Za-z_][A-Za-z0-9_]*)(?:\\s+.*)?");

    private final DiagnosticBag diagnostics;
    private final Set<String> definedSymbols = new HashSet<>();
    private final Set<Path> pragmaOnceFiles = new HashSet<>();
    private final Deque<Path> includeStack = new ArrayDeque<>();

    /**
     * Creates a preprocessor that reports errors into the given diagnostics bag.
     *
     * @param diagnostics target diagnostics bag
     */
    public SourcePreprocessor(DiagnosticBag diagnostics) {
        this.diagnostics = diagnostics;
    }

    /**
     * Expands all include directives reachable from the given root file.
     *
     * @param rootFile source file passed to the compiler
     * @return one combined source string
     */
    public String preprocess(Path rootFile) {
        return processFile(normalizeExisting(rootFile), UNKNOWN_LOCATION);
    }

    private String processFile(Path file, SourceLocation includeLocation) {
        Path normalizedFile = normalizeExisting(file);

        if (pragmaOnceFiles.contains(normalizedFile)) {
            return "";
        }
        if (includeStack.size() >= MAX_INCLUDE_DEPTH) {
            error("Maximum include depth of " + MAX_INCLUDE_DEPTH + " exceeded: " + includeTrace(normalizedFile),
                    includeLocation);
            return "";
        }

        includeStack.push(normalizedFile);
        try {
            String source = Files.readString(normalizedFile, StandardCharsets.UTF_8);
            return processSource(normalizedFile, source);
        } catch (IOException exception) {
            error("Could not read include file: " + normalizedFile, includeLocation);
            return "";
        } finally {
            includeStack.pop();
        }
    }

    private String processSource(Path currentFile, String source) {
        StringBuilder expanded = new StringBuilder(source.length());
        Deque<ConditionalFrame> conditionals = new ArrayDeque<>();
        String[] lines = source.split("\\R", -1);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNumber = i + 1;
            String trimmed = stripTrailingLineComment(line.stripLeading());

            if (trimmed.startsWith("#")) {
                handleDirective(currentFile, lineNumber, line, trimmed, conditionals, expanded);
                continue;
            }

            if (isActive(conditionals)) {
                expanded.append(line);
            }
            appendLineBreakUnlessSyntheticLastLine(expanded, lines, i);
        }

        while (!conditionals.isEmpty()) {
            ConditionalFrame frame = conditionals.pop();
            error("Unclosed preprocessor conditional opened at " + currentFile + ":" + frame.lineNumber(),
                    frame.location());
        }

        return expanded.toString();
    }

    private void handleDirective(
            Path currentFile,
            int lineNumber,
            String line,
            String directive,
            Deque<ConditionalFrame> conditionals,
            StringBuilder expanded) {

        SourceLocation location = lineLocation(lineNumber, line);
        if (handleConditionalDirective(currentFile, lineNumber, location, directive, conditionals)) {
            return;
        }

        if (!isActive(conditionals)) {
            return;
        }

        Matcher includeMatcher = INCLUDE_PATTERN.matcher(directive);
        if (includeMatcher.matches()) {
            String includePath = includeMatcher.group(1);
            if (!hasSourceExtension(includePath)) {
                error("Included file must have extension " + SOURCE_FILE_EXTENSION
                        + " at " + currentFile + ":" + lineNumber + ": " + includePath, location);
                return;
            }
            expanded.append(processFile(resolveInclude(currentFile, includePath), location));
            ensureTrailingLineBreak(expanded);
            return;
        }

        Matcher symbolMatcher = SYMBOL_DIRECTIVE_PATTERN.matcher(directive);
        if (symbolMatcher.matches()) {
            handleSymbolDirective(symbolMatcher.group(1), symbolMatcher.group(2));
            return;
        }

        if (PRAGMA_ONCE_PATTERN.matcher(directive).matches()) {
            pragmaOnceFiles.add(normalizeExisting(currentFile));
            return;
        }

        error("Unsupported preprocessor directive at " + currentFile + ":" + lineNumber + ": " + directive, location);
    }

    private boolean handleConditionalDirective(
            Path currentFile,
            int lineNumber,
            SourceLocation location,
            String directive,
            Deque<ConditionalFrame> conditionals) {

        Matcher symbolMatcher = SYMBOL_DIRECTIVE_PATTERN.matcher(directive);
        if (symbolMatcher.matches()) {
            String kind = symbolMatcher.group(1);
            String symbol = symbolMatcher.group(2);
            boolean parentActive = isActive(conditionals);

            switch (kind) {
                case "ifdef" -> {
                    conditionals.push(new ConditionalFrame(
                            parentActive,
                            definedSymbols.contains(symbol),
                            false,
                            lineNumber,
                            location));
                    return true;
                }
                case "ifndef" -> {
                    conditionals.push(new ConditionalFrame(
                            parentActive,
                            !definedSymbols.contains(symbol),
                            false,
                            lineNumber,
                            location));
                    return true;
                }
                default -> {
                    return false;
                }
            }
        }

        if (ELSE_PATTERN.matcher(directive).matches()) {
            if (conditionals.isEmpty()) {
                error("Unexpected #else at " + currentFile + ":" + lineNumber, location);
                return true;
            }
            ConditionalFrame frame = conditionals.pop();
            if (frame.elseSeen()) {
                error("Duplicate #else at " + currentFile + ":" + lineNumber, location);
            }
            conditionals.push(new ConditionalFrame(
                    frame.parentActive(),
                    !frame.conditionActive(),
                    true,
                    frame.lineNumber(),
                    frame.location()));
            return true;
        }

        if (ENDIF_PATTERN.matcher(directive).matches()) {
            if (conditionals.isEmpty()) {
                error("Unexpected #endif at " + currentFile + ":" + lineNumber, location);
            } else {
                conditionals.pop();
            }
            return true;
        }

        if (directive.matches("#\\s*ifn?def.*")) {
            error("Malformed preprocessor directive at " + currentFile + ":" + lineNumber + ": " + directive,
                    location);
            return true;
        }

        return false;
    }

    private void handleSymbolDirective(String kind, String symbol) {
        if (!SYMBOL_PATTERN.matcher(symbol).matches()) {
            return;
        }

        switch (kind) {
            case "define" -> definedSymbols.add(symbol);
            case "undef" -> definedSymbols.remove(symbol);
            default -> {
                // Handled elsewhere.
            }
        }
    }

    private boolean isActive(Deque<ConditionalFrame> conditionals) {
        for (ConditionalFrame conditional : conditionals) {
            if (!conditional.active()) {
                return false;
            }
        }
        return true;
    }

    private Path resolveInclude(Path currentFile, String includePath) {
        Path candidate = Path.of(includePath);
        if (candidate.isAbsolute()) {
            return normalizeExisting(candidate);
        }

        Path parent = currentFile.getParent();
        if (parent == null) {
            parent = Path.of("");
        }
        return normalizeExisting(parent.resolve(candidate));
    }

    private boolean hasSourceExtension(String includePath) {
        Path fileName = Path.of(includePath).getFileName();
        return fileName != null && fileName.toString().endsWith(SOURCE_FILE_EXTENSION);
    }

    private Path normalizeExisting(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        try {
            return absolute.toRealPath();
        } catch (IOException ignored) {
            return absolute;
        }
    }

    private String includeTrace(Path repeatedFile) {
        StringBuilder trace = new StringBuilder();
        includeStack.descendingIterator().forEachRemaining(path -> {
            if (!trace.isEmpty()) {
                trace.append(" -> ");
            }
            trace.append(path);
        });
        if (!trace.isEmpty()) {
            trace.append(" -> ");
        }
        trace.append(repeatedFile);
        return trace.toString();
    }

    private String stripTrailingLineComment(String line) {
        boolean insideString = false;
        boolean insideAngleInclude = false;
        for (int i = 0; i + 1 < line.length(); i++) {
            char current = line.charAt(i);
            if (current == '"' && !insideAngleInclude) {
                insideString = !insideString;
            } else if (current == '<' && !insideString) {
                insideAngleInclude = true;
            } else if (current == '>' && insideAngleInclude) {
                insideAngleInclude = false;
            }

            if (!insideString && !insideAngleInclude && current == '/' && line.charAt(i + 1) == '/') {
                return line.substring(0, i).stripTrailing();
            }
        }
        return line.stripTrailing();
    }

    private void appendLineBreakUnlessSyntheticLastLine(StringBuilder builder, String[] lines, int index) {
        if (index + 1 < lines.length) {
            builder.append('\n');
        }
    }

    private void ensureTrailingLineBreak(StringBuilder builder) {
        if (!builder.isEmpty() && builder.charAt(builder.length() - 1) != '\n') {
            builder.append('\n');
        }
    }

    private SourceLocation lineLocation(int lineNumber, String line) {
        return new SourceLocation(1, endColumn(line), Math.max(1, lineNumber));
    }

    private int endColumn(String line) {
        if (line == null || line.isEmpty()) {
            return 1;
        }

        int column = 1;
        for (int i = 0; i < line.length(); i++) {
            char current = line.charAt(i);
            if (current == '\t') {
                column += 4 - ((column - 1) % 4);
            } else {
                column++;
            }
        }
        return Math.max(1, column - 1);
    }

    private void error(String message, SourceLocation location) {
        diagnostics.add(new Diagnostic(location == null ? UNKNOWN_LOCATION : location, Severity.ERROR, message));
    }

    private record ConditionalFrame(
            boolean parentActive,
            boolean conditionActive,
            boolean elseSeen,
            int lineNumber,
            SourceLocation location) {
        boolean active() {
            return parentActive && conditionActive;
        }
    }
}
