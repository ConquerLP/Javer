package ch.zhaw.it.pm4.javer.compiler.codegen;

import ch.zhaw.it.pm4.javer.compiler.ast.nodes.CompilationUnit;
import ch.zhaw.it.pm4.javer.compiler.misc.diagnostics.DiagnosticBag;
import ch.zhaw.it.pm4.javer.compiler.misc.diagnostics.Severity;
import ch.zhaw.it.pm4.javer.compiler.visitor.AstNodeVisitorBase;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Base for text emitting target generators with shared output-file handling.
 */
public abstract class TextCodeGenerator extends AstNodeVisitorBase implements TargetCodeGenerator {

    protected final DiagnosticBag diagnostics;
    protected final StringBuilder output = new StringBuilder();

    private final Path outputFile;
    private boolean outputDirectoryReady = true;
    private boolean failed;

    protected TextCodeGenerator(DiagnosticBag diagnostics, String outputFilePath) {
        this.diagnostics = diagnostics;
        this.outputFile = Path.of(outputFilePath);
        prepareOutputDirectory();
    }

    @Override
    public final void generate(CompilationUnit node) {
        output.setLength(0);
        reset();

        if (!outputDirectoryReady) {
            deleteOutputFile();
            return;
        }

        try {
            emit(node);

            if (failed || diagnostics.hasErrors()) {
                deleteOutputFile();
                return;
            }

            Files.writeString(
                    outputFile,
                    output.toString(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (IOException exception) {
            report("Could not write code output file: " + outputFile + ".");
            deleteOutputFile();
        } finally {
            cleanup();
        }
    }

    protected void reset() {
    }

    protected abstract void emit(CompilationUnit node);

    protected void cleanup() {
    }

    protected void writeLine(String line) {
        output.append(line).append(System.lineSeparator());
    }

    protected void report(String message) {
        failed = true;
        diagnostics.add(null, Severity.ERROR, message);
    }

    private void prepareOutputDirectory() {
        Path parent = outputFile.toAbsolutePath().getParent();
        if (parent == null) {
            return;
        }

        try {
            Files.createDirectories(parent);
        } catch (IOException exception) {
            outputDirectoryReady = false;
            report("Could not create code output directory: " + parent + ".");
        }
    }

    private void deleteOutputFile() {
        try {
            Files.deleteIfExists(outputFile);
        } catch (IOException exception) {
            report("Could not delete incomplete code output file: " + outputFile + ".");
        }
    }
}
