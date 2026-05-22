package ch.zhaw.it.pm4.javer.compiler.misc.diagnostics;

import java.util.ArrayList;
import java.util.List;

import ch.zhaw.it.pm4.javer.compiler.CompilationPhase;
import ch.zhaw.it.pm4.javer.compiler.annotation.JacocoGenerated;
import ch.zhaw.it.pm4.javer.compiler.misc.SourceCache;
import ch.zhaw.it.pm4.javer.compiler.misc.SourceLocation;

/**
 * Collects diagnostics for one compilation run and formats user-facing error
 * reports with source excerpts.
 */
@JacocoGenerated("Skeleton only, remove when fully implemented")
public class DiagnosticBag {

    private static final int TAB_WIDTH = 4;

    private final int errorLimit;
    private final String filePath;
    private SourceCache sourceCache;

    private CompilationPhase phase;
    private final List<Diagnostic> diagnostics;
    private final List<PhaseAbortListener> phaseAbortListeners = new ArrayList<>();
    private int errorCount;
    private boolean errorLimitReached;
    private boolean phaseAbortRequested;

    /**
     * Initializes a new DiagnosticBag.
     *
     * @param filePath The path of the file being compiled.
     * @param errorLimit The maximum number of errors before compilation aborts.
     * @param compilationPhase initial compiler phase
     * @param sourceCache The cache holding the raw source code text.
     */
    public DiagnosticBag(String filePath, int errorLimit, CompilationPhase compilationPhase, SourceCache sourceCache) {
        this.filePath = filePath;
        this.errorLimit = errorLimit;
        this.sourceCache = sourceCache;
        this.diagnostics = new ArrayList<>();
        this.phase = compilationPhase;
    }

    /**
     * Updates the phase that will be printed in diagnostic reports.
     *
     * @param phase current compiler phase
     */
    public void setPhase(CompilationPhase phase) {
        this.phase = phase;
        if (!errorLimitReached) {
            phaseAbortRequested = false;
        }
    }

    /**
     * Registers a listener that is notified when the current phase should stop
     * immediately, for example after the diagnostic error limit was reached.
     *
     * @param listener listener to notify on phase abort
     */
    public void addPhaseAbortListener(PhaseAbortListener listener) {
        phaseAbortListeners.add(listener);
    }

    /**
     * Replaces the source cache used for excerpts in later diagnostics.
     *
     * @param sourceCache source text cache for diagnostics
     */
    public void setSourceCache(SourceCache sourceCache) {
        this.sourceCache = sourceCache;
    }

    /**
     * Adds a newly created diagnostic to the bag and enforces the error limit.
     *
     * @param diagnostic The diagnostic to add.
     */
    public void add(Diagnostic diagnostic) {
        if (errorLimitReached) {
            return;
        }
        diagnostics.add(diagnostic);
        enforceErrorLimit(diagnostic);
    }

    /**
     * Adds a diagnostic at the given source location.
     *
     * @param location source location associated with the diagnostic
     * @param severity diagnostic severity
     * @param message user-facing message
     */
    public void add(SourceLocation location, Severity severity, String message) {
        add(new Diagnostic(location, severity, message));
    }

    private void enforceErrorLimit(Diagnostic diagnostic) {
        if (!isError(diagnostic)) {
            return;
        }

        errorCount++;
        if (errorLimit <= 0 || errorCount < errorLimit) {
            return;
        }

        errorLimitReached = true;
        diagnostics.add(new Diagnostic(
                null,
                Severity.SEVERE,
                "Diagnostic limit of " + errorLimit + " error(s) reached; further diagnostics suppressed."));
        requestPhaseAbort();
    }

    private boolean isError(Diagnostic diagnostic) {
        Severity severity = diagnostic.getSeverity();
        return severity == Severity.ERROR || severity == Severity.SEVERE;
    }

    private void requestPhaseAbort() {
        if (phaseAbortRequested) {
            return;
        }
        phaseAbortRequested = true;
        for (PhaseAbortListener listener : List.copyOf(phaseAbortListeners)) {
            listener.phaseAbortRequested(phase);
        }
    }

    /**
     * Indicates whether the current phase has requested an abort.
     *
     * @return true once the current phase should stop
     */
    public boolean isPhaseAbortRequested() {
        return phaseAbortRequested;
    }

    /**
     * Checks if there are any diagnostics with ERROR or SEVERE severity in the bag.
     *
     * @return true if there are errors, false otherwise.
     */
    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(d -> d.getSeverity() == Severity.ERROR || d.getSeverity() == Severity.SEVERE);
    }

    /**
     * Generates a formatted, human-readable report of all diagnostics in the current phase.
     * Uses the SourceCache to print the exact line of code where the error occurred.
     *
     * @return The formatted report string.
     */
    public String dumpReport() {
        StringBuilder sb = new StringBuilder();

        sb.append("=== Error Report ===\n");
        sb.append("File: ").append(filePath).append("\n");
        sb.append("Phase: ").append(phase).append("\n\n");

        if (diagnostics.isEmpty()) {
            sb.append("No diagnostics.\n");
            return sb.toString();
        }

        for (int i = 0; i < diagnostics.size(); i++) {
            sb.append(formatDiagnostic(diagnostics.get(i)));
            if (i + 1 < diagnostics.size()) {
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    private String formatDiagnostic(Diagnostic diagnostic) {
        SourceLocation location = diagnostic.getLocation();
        StringBuilder sb = new StringBuilder();

        sb.append("[")
                .append(diagnostic.getSeverity().name())
                .append("] ")
                .append(diagnostic.getMessage())
                .append("\n");

        if (location == null) {
            return sb.toString();
        }

        sb.append("  --> ")
                .append(filePath)
                .append(":")
                .append(location.lineNumber())
                .append(":")
                .append(location.startColumn())
                .append("\n");

        if (sourceCache != null) {
            appendSourceExcerpt(sb, location);
        }

        return sb.toString();
    }

    private void appendSourceExcerpt(StringBuilder sb, SourceLocation location) {
        String rawLine = sourceCache.getLine(location.lineNumber());
        String line = expandTabs(rawLine);
        String lineNumber = Integer.toString(location.lineNumber());
        String gutterPadding = " ".repeat(lineNumber.length());

        sb.append(gutterPadding).append(" |").append("\n");
        sb.append(lineNumber).append(" | ").append(line).append("\n");
        sb.append(gutterPadding).append(" | ").append(markerFor(location)).append("\n");
    }

    private String markerFor(SourceLocation location) {
        int start = Math.max(1, location.startColumn());
        int end = Math.max(start, location.endColumn());
        return " ".repeat(start - 1) + "^".repeat(end - start + 1);
    }

    private String expandTabs(String line) {
        StringBuilder expanded = new StringBuilder();
        int column = 1;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\t') {
                int spaces = TAB_WIDTH - ((column - 1) % TAB_WIDTH);
                expanded.repeat(" ", spaces);
                column += spaces;
            } else {
                expanded.append(c);
                column++;
            }
        }
        return expanded.toString();
    }

    /**
     * Clears all diagnostics from the bag, preparing it for the next compiler phase.
     */
    public void flush() {
        diagnostics.clear();
        errorCount = 0;
        errorLimitReached = false;
        phaseAbortRequested = false;
    }

    /**
     * Listener for diagnostic-driven phase abort requests.
     */
    @FunctionalInterface
    public interface PhaseAbortListener {
        void phaseAbortRequested(CompilationPhase phase);
    }
}
