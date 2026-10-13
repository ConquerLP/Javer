package ch.zhaw.it.pm4.javer.compiler.frontend;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.CompilationUnit;
import java.util.Objects;

/** A program that passed all frontend checks. Only the frontend can create it.
 * Backends must treat its semantic tree as read-only. */
public final class CheckedProgram {
    private final CompilationUnit compilationUnit;

    CheckedProgram(CompilationUnit compilationUnit) {
        this.compilationUnit = Objects.requireNonNull(compilationUnit);
    }

    public CompilationUnit compilationUnit() {
        return compilationUnit;
    }
}
