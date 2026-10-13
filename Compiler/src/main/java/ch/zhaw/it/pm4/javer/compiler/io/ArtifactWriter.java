package ch.zhaw.it.pm4.javer.compiler.io;

import ch.zhaw.it.pm4.javer.compiler.backend.OutputArtifact;
import ch.zhaw.it.pm4.javer.compiler.diagnostics.DiagnosticBag;
import ch.zhaw.it.pm4.javer.compiler.diagnostics.Severity;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Publishes completed artifacts. Existing output is preserved when generation fails. */
public final class ArtifactWriter {
    private ArtifactWriter() { }

    public static void write(OutputArtifact artifact, Path destination, DiagnosticBag diagnostics) {
        if (diagnostics.hasErrors() || diagnostics.isPhaseAbortRequested()) return;
        Path target = destination.toAbsolutePath();
        Path temporary = null;
        try {
            Files.createDirectories(target.getParent());
            temporary = Files.createTempFile(target.getParent(), ".javer-", ".tmp");
            Files.write(temporary, artifact.contents());
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            diagnostics.add(null, Severity.ERROR,
                    "Could not write code output file: " + target + ". " + exception.getMessage());
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException exception) {
                    diagnostics.add(null, Severity.ERROR, "Could not delete temporary output file: " + temporary + ".");
                }
            }
        }
    }
}
