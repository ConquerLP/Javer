package ch.zhaw.it.pm4.javer.compiler.frontend;

import ch.zhaw.it.pm4.javer.compiler.CompilationPhase;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.CompilationUnit;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.declaration.DeclarationAstNode;
import ch.zhaw.it.pm4.javer.compiler.frontend.lexer.Lexer;
import ch.zhaw.it.pm4.javer.compiler.frontend.lexer.Token;
import ch.zhaw.it.pm4.javer.compiler.diagnostics.DiagnosticBag;
import ch.zhaw.it.pm4.javer.compiler.frontend.parser.Parser;
import ch.zhaw.it.pm4.javer.compiler.frontend.analysis.*;
import ch.zhaw.it.pm4.javer.compiler.frontend.debug.*;

import ch.zhaw.it.pm4.javer.compiler.diagnostics.PhaseResult;
import ch.zhaw.it.pm4.javer.compiler.diagnostics.CompilerInvariantException;
import ch.zhaw.it.pm4.javer.compiler.diagnostics.Severity;
import ch.zhaw.it.pm4.javer.compiler.ir.Ir;
import ch.zhaw.it.pm4.javer.compiler.ir.IrVerifier;
import ch.zhaw.it.pm4.javer.compiler.io.SourceFileReadException;
import ch.zhaw.it.pm4.javer.compiler.source.SourceCache;
import ch.zhaw.it.pm4.javer.compiler.source.SourceLocation;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
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
        CompilationUnit node = parse(source, null, tokenDump);
        if (failed()) return PhaseResult.failure(diagnostics);
        for (var directive : node.getImports()) {
            diagnostics.add(directive.location(), Severity.ERROR, "Imports require a source file.");
        }
        if (failed()) return PhaseResult.failure(diagnostics);
        return analyzeUnit(node, builtIns, astDump);
    }

    public PhaseResult<CheckedProgram> analyzeFiles(List<Path> inputs, List<BuiltInSignature> builtIns,
                                                    Consumer<String> tokenDump, Consumer<String> astDump) {
        Queue<SourceInput> pending = new ArrayDeque<>();
        for (Path input : inputs) {
            pending.add(new SourceInput(input.toAbsolutePath(), null));
        }
        Set<Path> loaded = new HashSet<>();
        List<DeclarationAstNode> declarations = new ArrayList<>();
        CompilationUnit firstUnit = null;
        while (!pending.isEmpty()) {
            SourceInput input = pending.remove();
            Path file;
            SourceCache source;
            diagnostics.setPhase(CompilationPhase.COMPILER_SETUP);
            try {
                file = sourceIdentity(input.path());
                if (!loaded.add(file)) continue;
                source = new SourceCache(input.path().toString());
            } catch (IOException | SourceFileReadException exception) {
                String message = input.importLocation() == null
                        ? "Failed to read source file: " : "Failed to read imported source file: ";
                diagnostics.add(input.importLocation(), Severity.ERROR, message + input.path());
                return PhaseResult.failure(diagnostics);
            }

            diagnostics.registerSource(file, source);
            CompilationUnit unit = parse(source.getSourceCode(), file, tokenDump);
            if (failed()) return PhaseResult.failure(diagnostics);
            if (firstUnit == null) firstUnit = unit;
            declarations.addAll(unit.getDeclarations());
            for (var directive : unit.getImports()) {
                pending.add(new SourceInput(input.path().resolveSibling(directive.name() + ".javer"),
                        directive.location()));
            }
        }

        CompilationUnit merged = new CompilationUnit(declarations);
        if (firstUnit != null) merged.setSourceRange(firstUnit.getSourceRange());
        return analyzeUnit(merged, builtIns, astDump);
    }

    private record SourceInput(Path path, SourceLocation importLocation) {}

    private static Path sourceIdentity(Path path) throws IOException {
        try {
            return path.toRealPath();
        } catch (AccessDeniedException exception) {
            // Resolving the real path can require access to parent directories even
            // when the source itself is readable. Let the source read decide.
            return path.normalize();
        }
    }

    private CompilationUnit parse(String source, Path file, Consumer<String> tokenDump) {
        diagnostics.setPhase(CompilationPhase.LEXING);
        List<Token> tokens = new Lexer(source, diagnostics, file).lexSourcecode();
        if (failed()) return null;
        if (tokenDump != null) {
            String header = file == null ? "" : "File: " + file + System.lineSeparator();
            tokenDump.accept(header + tokens.stream().map(Token::toString)
                    .collect(Collectors.joining(System.lineSeparator())));
        }

        diagnostics.setPhase(CompilationPhase.PARSING);
        return new Parser(tokens, diagnostics).parse();
    }

    private PhaseResult<CheckedProgram> analyzeUnit(CompilationUnit node, List<BuiltInSignature> builtIns,
                                                     Consumer<String> astDump) {
        try {
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
