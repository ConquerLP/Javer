package ch.zhaw.it.pm4.javer.compiler.backend;

import ch.zhaw.it.pm4.javer.compiler.CompilationPhase;
import ch.zhaw.it.pm4.javer.compiler.frontend.BuiltInSignature;
import ch.zhaw.it.pm4.javer.compiler.frontend.CheckedProgram;
import ch.zhaw.it.pm4.javer.compiler.ir.Ir;
import ch.zhaw.it.pm4.javer.compiler.diagnostics.*;
import java.util.List;

/** Stateless target contract. Validation always precedes construction of preparation state. */
public abstract class TargetBackend {
    public List<BuiltInSignature> builtInSignatures() { return List.of(); }

    public final PhaseResult<PreparedProgram> prepare(CheckedProgram checked, DiagnosticBag diagnostics) {
        if (diagnostics.hasErrors()) return PhaseResult.failure(diagnostics);
        try {
            diagnostics.setPhase(CompilationPhase.TARGET_VALIDATION);
            validate(checked.ir(), diagnostics);
            if (diagnostics.hasErrors()) return PhaseResult.failure(diagnostics);
            diagnostics.setPhase(CompilationPhase.LAYOUT);
            PreparedProgram prepared = prepareValidated(checked.ir(), diagnostics);
            return diagnostics.hasErrors() ? PhaseResult.failure(diagnostics) : PhaseResult.success(prepared);
        } catch (CompilerInvariantException exception) {
            diagnostics.add(exception.source().start(), Severity.SEVERE, "Internal compiler error: " + exception.getMessage());
            return PhaseResult.failure(diagnostics);
        } catch (ArithmeticException exception) {
            diagnostics.add(null, Severity.ERROR, "Target layout exceeds supported size.");
            return PhaseResult.failure(diagnostics);
        }
    }

    /** Report unsupported operations, types or numeric semantics with their IR source location. */
    protected abstract void validate(Ir.Program program, DiagnosticBag diagnostics);
    protected abstract PreparedProgram prepareValidated(Ir.Program program, DiagnosticBag diagnostics);
}
