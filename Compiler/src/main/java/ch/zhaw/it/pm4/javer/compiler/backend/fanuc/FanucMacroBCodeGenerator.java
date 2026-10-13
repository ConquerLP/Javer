package ch.zhaw.it.pm4.javer.compiler.backend.fanuc;

import ch.zhaw.it.pm4.javer.compiler.backend.TextCodeGenerator;
import ch.zhaw.it.pm4.javer.compiler.diagnostics.DiagnosticBag;

/** Deliberately emits an empty artifact until FANUC implementation begins. */
final class FanucMacroBCodeGenerator extends TextCodeGenerator {
    FanucMacroBCodeGenerator(DiagnosticBag diagnostics) { super(diagnostics); }
    @Override protected void emit() { }
}
