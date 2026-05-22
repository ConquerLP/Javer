package ch.zhaw.it.pm4.javer.compiler.preprocessor;

import ch.zhaw.it.pm4.javer.compiler.CompilationPhase;
import ch.zhaw.it.pm4.javer.compiler.misc.SourceCache;
import ch.zhaw.it.pm4.javer.compiler.misc.diagnostics.DiagnosticBag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourcePreprocessorTest {

    @TempDir
    Path tempDir;

    @Test
    void expandsIncludeAndHonorsClassicIncludeGuard() throws Exception {
        Path root = write("input.javer", """
                # include "lib/print.javer"
                # include "lib/print.javer"

                fn void main() {
                    call helper();
                }
                """);
        write("lib/print.javer", """
                # ifndef PRINT_INC
                # define PRINT_INC

                fn void helper() {
                    call prints("ok");
                }

                # endif // PRINT_INC
                """);

        PreprocessResult result = preprocess(root);

        assertFalse(result.diagnostics().hasErrors());
        assertEquals(1, occurrences(result.source(), "fn void helper"));
        assertTrue(result.source().contains("fn void main"));
        assertFalse(result.source().contains("#"));
    }

    @Test
    void pragmaOnceSkipsRepeatedInclude() throws Exception {
        Path root = write("input.javer", """
                #include "once.javer"
                #include "once.javer"
                """);
        write("once.javer", """
                # pragma once

                fn void once() {
                }
                """);

        PreprocessResult result = preprocess(root);

        assertFalse(result.diagnostics().hasErrors());
        assertEquals(1, occurrences(result.source(), "fn void once"));
    }

    @Test
    void classicGuardsCanBreakRecursiveIncludes() throws Exception {
        Path root = write("input.javer", """
                #include "a.javer"
                """);
        write("a.javer", """
                #ifndef A_INC
                #define A_INC
                #include "b.javer"

                fn void a() {
                }

                #endif
                """);
        write("b.javer", """
                #ifndef B_INC
                #define B_INC
                #include "a.javer"

                fn void b() {
                }

                #endif
                """);

        PreprocessResult result = preprocess(root);

        assertFalse(result.diagnostics().hasErrors());
        assertEquals(1, occurrences(result.source(), "fn void a"));
        assertEquals(1, occurrences(result.source(), "fn void b"));
    }

    @Test
    void missingIncludeReportsDiagnostic() throws Exception {
        String sourceLine = "#include \"missing.javer\"";
        Path root = write("input.javer", sourceLine + "\n");

        PreprocessResult result = preprocess(root);

        assertTrue(result.diagnostics().hasErrors());
        assertLineDiagnostic(result.diagnostics().dumpReport(), root, 1, sourceLine);
    }

    @Test
    void includeRejectsNonJaverExtension() throws Exception {
        Path root = write("input.javer", """
                #include "legacy.inc"
                """);
        write("legacy.inc", """
                fn void legacy() {
                }
                """);

        PreprocessResult result = preprocess(root);

        assertTrue(result.diagnostics().hasErrors());
        assertLineDiagnostic(result.diagnostics().dumpReport(), root, 1, "#include \"legacy.inc\"");
        assertEquals("", result.source().strip());
    }

    @Test
    void unknownLocationUsesDummyLocation() throws Exception {
        Path root = tempDir.resolve("missing-root.javer");

        PreprocessResult result = preprocess(root);

        assertTrue(result.diagnostics().hasErrors());
        assertTrue(result.diagnostics().dumpReport().contains(root + ":1:1"));
    }

    private PreprocessResult preprocess(Path root) throws Exception {
        String sourceForDiagnostics = Files.exists(root)
                ? Files.readString(root, StandardCharsets.UTF_8)
                : "";
        DiagnosticBag diagnostics = new DiagnosticBag(
                root.toString(),
                50,
                CompilationPhase.PREPROCESSING,
                new SourceCache(root.toString(), sourceForDiagnostics));
        String source = new SourcePreprocessor(diagnostics).preprocess(root);
        return new PreprocessResult(source, diagnostics);
    }

    private Path write(String relativePath, String content) throws Exception {
        Path file = tempDir.resolve(relativePath);
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private int occurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private void assertLineDiagnostic(String report, Path root, int lineNumber, String line) {
        assertTrue(report.contains(root + ":" + lineNumber + ":1"));
        assertTrue(report.contains(lineNumber + " | " + line));
        assertTrue(report.contains(" | " + "^".repeat(line.length())));
    }

    private record PreprocessResult(String source, DiagnosticBag diagnostics) {
    }
}
