package ch.zhaw.it.pm4.javer.compiler.frontend;

import ch.zhaw.it.pm4.javer.compiler.CompilationPhase;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.CompilationUnit;
import ch.zhaw.it.pm4.javer.compiler.frontend.lexer.Lexer;
import ch.zhaw.it.pm4.javer.compiler.frontend.lexer.Token;
import ch.zhaw.it.pm4.javer.compiler.diagnostics.DiagnosticBag;
import ch.zhaw.it.pm4.javer.compiler.frontend.parser.Parser;
import ch.zhaw.it.pm4.javer.compiler.frontend.analysis.*;
import ch.zhaw.it.pm4.javer.compiler.frontend.debug.*;

import java.util.List;
import ch.zhaw.it.pm4.javer.compiler.diagnostics.PhaseResult;
import ch.zhaw.it.pm4.javer.compiler.diagnostics.CompilerInvariantException;
import ch.zhaw.it.pm4.javer.compiler.diagnostics.Severity;
import ch.zhaw.it.pm4.javer.compiler.ir.Ir;
import ch.zhaw.it.pm4.javer.compiler.ir.IrVerifier;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** Performs target-independent analysis using externally supplied runtime signatures. */
public final class Frontend {
    private final DiagnosticBag diagnostics;

    public Frontend(DiagnosticBag diagnostics) {
        this.diagnostics = diagnostics;
    }

    public PhaseResult<CheckedProgram> analyze(String source, List<BuiltInSignature> builtIns) {
        return analyze(source, builtIns, null, null);
    }

    /** Returns a checked program only when every frontend phase succeeds. */
    public PhaseResult<CheckedProgram> analyze(String source, List<BuiltInSignature> builtIns,
                                   Consumer<String> tokenDump, Consumer<String> astDump) {
        try {
            diagnostics.setPhase(CompilationPhase.LEXING);
            List<Token> tokens = new Lexer(source, diagnostics).lexSourcecode();
            if (failed()) return PhaseResult.failure(diagnostics);
            if (tokenDump != null) {
                tokenDump.accept(tokens.stream().map(Token::toString)
                        .collect(Collectors.joining(System.lineSeparator())));
            }

            diagnostics.setPhase(CompilationPhase.PARSING);
            CompilationUnit node = new Parser(tokens, diagnostics).parse();
            if (failed()) return PhaseResult.failure(diagnostics);
            if (astDump != null) {
                astDump.accept(new AstPrinter().printToString(node));
            }

            diagnostics.setPhase(CompilationPhase.SYMBOL_TABLE_CREATION);
            new SymbolDeclarationVisitor(diagnostics, builtIns).visit(node);
            if (failed()) return PhaseResult.failure(diagnostics);
            diagnostics.setPhase(CompilationPhase.NAME_RESOLUTION);
            new NameResolutionVisitor(diagnostics).visit(node);
            if (failed()) return PhaseResult.failure(diagnostics);
            diagnostics.setPhase(CompilationPhase.TYPE_CHECKING);
            new TypeCheckVisitor(diagnostics).visit(node);
            if (failed()) return PhaseResult.failure(diagnostics);
            diagnostics.setPhase(CompilationPhase.SEMANTIC_ANALYSIS);
            new SemanticChecker(diagnostics).visit(node);
            if (failed()) return PhaseResult.failure(diagnostics);
            diagnostics.setPhase(CompilationPhase.IR_LOWERING);
            Ir.Program program = new IrLowering().lower(node);
            new IrVerifier().verify(program);
            return PhaseResult.success(new CheckedProgram(program));
        } catch (CompilerInvariantException exception) {
            diagnostics.add(exception.source().start(), Severity.SEVERE, "Internal compiler error: " + exception.getMessage());
            return PhaseResult.failure(diagnostics);
        }
    }

    private boolean failed() {
        return diagnostics.hasErrors() || diagnostics.isPhaseAbortRequested();
    }
}
