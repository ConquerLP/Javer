package ch.zhaw.it.pm4.javer.compiler.frontend;

import ch.zhaw.it.pm4.javer.compiler.CompilationPhase;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.CompilationUnit;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.FunctionEntry;
import ch.zhaw.it.pm4.javer.compiler.lexer.Lexer;
import ch.zhaw.it.pm4.javer.compiler.lexer.Token;
import ch.zhaw.it.pm4.javer.compiler.misc.diagnostics.DiagnosticBag;
import ch.zhaw.it.pm4.javer.compiler.parser.Parser;
import ch.zhaw.it.pm4.javer.compiler.visitor.*;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** Performs target-independent analysis using externally supplied runtime signatures. */
public final class Frontend {
    private final DiagnosticBag diagnostics;

    public Frontend(DiagnosticBag diagnostics) {
        this.diagnostics = diagnostics;
    }

    public CompilationUnit analyze(String source, List<FunctionEntry> builtIns) {
        return analyze(source, builtIns, null, null);
    }

    /** Returns a checked semantic AST, or null when analysis fails. */
    public CompilationUnit analyze(String source, List<FunctionEntry> builtIns,
                                   Consumer<String> tokenDump, Consumer<String> astDump) {
        diagnostics.setPhase(CompilationPhase.LEXING);
        List<Token> tokens = new Lexer(source, diagnostics).lexSourcecode();
        if (failed()) return null;
        if (tokenDump != null) {
            tokenDump.accept(tokens.stream().map(Token::toString)
                    .collect(Collectors.joining(System.lineSeparator())));
        }

        diagnostics.setPhase(CompilationPhase.PARSING);
        CompilationUnit node = new Parser(tokens, diagnostics).parse();
        if (failed()) return null;
        if (astDump != null) {
            astDump.accept(new AstPrinter().printToString(node));
        }

        diagnostics.setPhase(CompilationPhase.SYMBOL_TABLE_CREATION);
        new SymbolDeclarationVisitor(diagnostics, builtIns).visit(node);
        if (failed()) return null;
        diagnostics.setPhase(CompilationPhase.NAME_RESOLUTION);
        new NameResolutionVisitor(diagnostics).visit(node);
        if (failed()) return null;
        diagnostics.setPhase(CompilationPhase.TYPE_CHECKING);
        new TypeCheckVisitor(diagnostics).visit(node);
        if (failed()) return null;
        diagnostics.setPhase(CompilationPhase.SEMANTIC_ANALYSIS);
        new SemanticChecker(diagnostics).visit(node);
        return failed() ? null : node;
    }

    private boolean failed() {
        return diagnostics.hasErrors() || diagnostics.isPhaseAbortRequested();
    }
}
