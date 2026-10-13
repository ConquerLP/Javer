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
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** Performs target-independent analysis using externally supplied runtime signatures. */
public final class Frontend {
    private final DiagnosticBag diagnostics;

    public Frontend(DiagnosticBag diagnostics) {
        this.diagnostics = diagnostics;
    }

    public Optional<CheckedProgram> analyze(String source, List<BuiltInSignature> builtIns) {
        return analyze(source, builtIns, null, null);
    }

    /** Returns a checked program only when every frontend phase succeeds. */
    public Optional<CheckedProgram> analyze(String source, List<BuiltInSignature> builtIns,
                                   Consumer<String> tokenDump, Consumer<String> astDump) {
        diagnostics.setPhase(CompilationPhase.LEXING);
        List<Token> tokens = new Lexer(source, diagnostics).lexSourcecode();
        if (failed()) return Optional.empty();
        if (tokenDump != null) {
            tokenDump.accept(tokens.stream().map(Token::toString)
                    .collect(Collectors.joining(System.lineSeparator())));
        }

        diagnostics.setPhase(CompilationPhase.PARSING);
        CompilationUnit node = new Parser(tokens, diagnostics).parse();
        if (failed()) return Optional.empty();
        if (astDump != null) {
            astDump.accept(new AstPrinter().printToString(node));
        }

        diagnostics.setPhase(CompilationPhase.SYMBOL_TABLE_CREATION);
        new SymbolDeclarationVisitor(diagnostics, builtIns).visit(node);
        if (failed()) return Optional.empty();
        diagnostics.setPhase(CompilationPhase.NAME_RESOLUTION);
        new NameResolutionVisitor(diagnostics).visit(node);
        if (failed()) return Optional.empty();
        diagnostics.setPhase(CompilationPhase.TYPE_CHECKING);
        new TypeCheckVisitor(diagnostics).visit(node);
        if (failed()) return Optional.empty();
        diagnostics.setPhase(CompilationPhase.SEMANTIC_ANALYSIS);
        new SemanticChecker(diagnostics).visit(node);
        return failed() ? Optional.empty() : Optional.of(new CheckedProgram(node));
    }

    private boolean failed() {
        return diagnostics.hasErrors() || diagnostics.isPhaseAbortRequested();
    }
}
