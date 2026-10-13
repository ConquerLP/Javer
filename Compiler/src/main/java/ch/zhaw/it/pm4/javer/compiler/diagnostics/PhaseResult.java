package ch.zhaw.it.pm4.javer.compiler.diagnostics;

import java.util.List;
import java.util.Objects;

/** An explicit phase outcome. Failures always carry at least one error diagnostic. */
public sealed interface PhaseResult<T> permits PhaseResult.Success, PhaseResult.Failure {
    boolean succeeded();
    T orElseThrow();

    record Success<T>(T value) implements PhaseResult<T> {
        public Success { Objects.requireNonNull(value); }
        public boolean succeeded() { return true; }
        public T orElseThrow() { return value; }
    }
    record Failure<T>(List<Diagnostic> diagnostics) implements PhaseResult<T> {
        public Failure {
            diagnostics = List.copyOf(diagnostics);
            if (diagnostics.stream().noneMatch(d -> d.severity() == Severity.ERROR || d.severity() == Severity.SEVERE)) {
                throw new IllegalArgumentException("A failed phase must report an error");
            }
        }
        public boolean succeeded() { return false; }
        public T orElseThrow() { throw new IllegalStateException("Cannot consume a failed compiler phase"); }
    }
    static <T> PhaseResult<T> success(T value) { return new Success<>(value); }
    static <T> PhaseResult<T> failure(DiagnosticBag diagnostics) { return new Failure<>(diagnostics.getDiagnostics()); }
}
