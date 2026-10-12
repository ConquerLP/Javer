package ch.zhaw.it.pm4.javer.compiler.misc;

import ch.zhaw.it.pm4.javer.compiler.io.SourceFileReadException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Holds the full source code of the single input file and maintains
 * an internal cache of the lines for fast O(1) lookups during error reporting.
 */
public class SourceCache {

    private final String sourceCode;
    private final List<String> cachedLines;

    /**
     * Initializes the cache by reading the source code from the given file path
     * and building the internal line lookup table.
     *
     * @param filePath The path to the source file to be read.
     */
    public SourceCache(String filePath) {
        try {
            this.sourceCode = Files.readString(Path.of(filePath), StandardCharsets.UTF_8);
            this.cachedLines = buildCache(this.sourceCode);
        } catch (IOException e) {
            throw new SourceFileReadException(filePath, e);
        }
    }

    /**
     * Internal helper to pre-compute the lines of the source code.
     *
     * @param source The raw source string.
     * @return A list of individual lines.
     */
    private List<String> buildCache(String source) {
        return Arrays.asList(source.split("\\R", -1));
    }

    /**
     * Retrieves the entire raw source code.
     * Primarily used by the Lexer to begin tokenization.
     *
     * @return The full source code string.
     */
    public String getSourceCode() {
        return sourceCode;
    }

    /**
     * Retrieves a specific line of code based on its line number (1-indexed)
     * using the pre-built cache.
     *
     * @param lineNumber The 1-indexed line number to fetch.
     * @return The string content of that specific line, or a fallback message if out of bounds.
     */
    public String getLine(int lineNumber) {
        int index = lineNumber - 1;
        if (index < 0 || index >= cachedLines.size()) {
            return "";
        }
        return cachedLines.get(index);
    }
}
