package ch.zhaw.it.pm4.javer.compiler.backend.fanuc;

import ch.zhaw.it.pm4.javer.compiler.backend.*;
import ch.zhaw.it.pm4.javer.compiler.ir.*;
import ch.zhaw.it.pm4.javer.compiler.diagnostics.*;
import ch.zhaw.it.pm4.javer.compiler.source.SourceRange;

import java.util.*;

/**
 * Target-specific validation and register layout; the VM language contract stays unchanged.
 */
public final class FanucMacroBBackend extends TargetBackend {
    private void validateType(Ir.Type type, SourceRange source, DiagnosticBag diagnostics) {
        if (type.kind() == Ir.Kind.CHAR || type.kind() == Ir.Kind.STRING)
            diagnostics.add(source.start(), Severity.ERROR, "FANUC does not support " + type.kind() + ".");
        if (type.element() != null) validateType(type.element(), source, diagnostics);
    }

    @Override
    protected void validate(Ir.Program program, DiagnosticBag diagnostics) {
        for (var signature : program.signatures()) {
            validateType(signature.returnType(), SourceRange.UNKNOWN, diagnostics);
            signature.parameters().forEach(t -> validateType(t, SourceRange.UNKNOWN, diagnostics));
        }
        program.structs().forEach(s -> s.fields().forEach(f -> validateType(f.type(), f.source(), diagnostics)));
        program.enums().forEach(e -> e.values().forEach(v -> {
            if (v.value() < Short.MIN_VALUE || v.value() > Short.MAX_VALUE)
                diagnostics.add(v.source().start(), Severity.ERROR, "FANUC enum value exceeds int16.");
        }));
        for (var function : program.functions()) {
            function.locals().forEach(s -> validateType(s.type(), s.source(), diagnostics));
            // The lexer represents -32768 as unary minus applied to the positive literal 32768.
            Set<Ir.Node> minimumOperands = Collections.newSetFromMap(new IdentityHashMap<>());
            IrWalker.walk(function.body(), node -> {
                if (node instanceof Ir.Unary u && u.op() == Ir.UnaryOp.NEGATE
                        && u.operand() instanceof Ir.Literal l && Integer.valueOf(32768).equals(l.value()))
                    minimumOperands.add(l);
            });
            IrWalker.walk(function.body(), node -> {
                if (node instanceof Ir.Expression e) validateType(e.type(), e.source(), diagnostics);
                if (node instanceof Ir.Literal literal) {
                    if (literal.value() instanceof Integer i && (i < Short.MIN_VALUE || i > Short.MAX_VALUE)
                            && !minimumOperands.contains(literal))
                        diagnostics.add(literal.source().start(), Severity.ERROR, "FANUC integer literal exceeds int16.");
                    if (literal.value() instanceof Double d && !Float.isFinite(d.floatValue()))
                        diagnostics.add(literal.source().start(), Severity.ERROR, "FANUC floating literal exceeds Float32.");
                }
            });
        }
    }

    @Override
    protected PreparedProgram prepareValidated(Ir.Program program, DiagnosticBag diagnostics) {
        var metadata = FanucMetadata.prepare(program);
        return new PreparedProgram() {
            public String dumpSymbolTable() {
                return metadata.describe(program);
            }

            public PhaseResult<OutputArtifact> generate(DiagnosticBag generationDiagnostics) {
                return new FanucMacroBCodeGenerator(generationDiagnostics, program, metadata).generate();
            }
        };
    }
}
