package ch.zhaw.it.pm4.javer.compiler.frontend;

import ch.zhaw.it.pm4.javer.compiler.ir.Ir;
import java.util.Objects;

/** Immutable, verified semantic snapshot. Mutable parser trees never cross this boundary. */
public final class CheckedProgram {
    private final Ir.Program ir;
    CheckedProgram(Ir.Program ir) { this.ir = Objects.requireNonNull(ir); }
    public Ir.Program ir() { return ir; }
}
