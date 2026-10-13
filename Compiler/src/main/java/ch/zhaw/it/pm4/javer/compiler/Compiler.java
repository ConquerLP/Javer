package ch.zhaw.it.pm4.javer.compiler;

import ch.zhaw.it.pm4.javer.compiler.frontend.CheckedProgram;
import ch.zhaw.it.pm4.javer.compiler.backend.PreparedProgram;
import ch.zhaw.it.pm4.javer.compiler.backend.OutputArtifact;
import ch.zhaw.it.pm4.javer.compiler.io.ArtifactWriter;
import java.nio.file.Path;
import ch.zhaw.it.pm4.javer.compiler.ir.IrPrinter;
import ch.zhaw.it.pm4.javer.compiler.diagnostics.PhaseResult;
import ch.zhaw.it.pm4.javer.compiler.backend.TargetBackend;
import ch.zhaw.it.pm4.javer.compiler.backend.TargetBackendFactory;
import ch.zhaw.it.pm4.javer.compiler.io.SourceFileReadException;
import ch.zhaw.it.pm4.javer.compiler.source.SourceCache;
import ch.zhaw.it.pm4.javer.compiler.diagnostics.DiagnosticBag;
import ch.zhaw.it.pm4.javer.compiler.diagnostics.Severity;
import ch.zhaw.it.pm4.javer.compiler.frontend.Frontend;

/**
 * Coordinates one complete compiler invocation from command-line options to
 * target-specific output.
 */
public class Compiler {

    // Orchestrates the full compilation pipeline.
    //
    // Responsibilities:
    // - initialize context
    // - execute phases in order
    // - stop or continue pipeline based on diagnostics
    //
    // Pipeline:
    // args -> frontend analysis -> backend preparation -> code generation

    private final CompilerOptions options;
    private final CompilationContext context;
    private final TargetBackend targetBackend;
    private boolean phaseAbortRequested;

    /**
     * Creates a compiler configured for one source input and output target.
     *
     * @param options validated compiler options
     */
    public Compiler(CompilerOptions options) {
        this.options = options;
        this.targetBackend = TargetBackendFactory.create(options.getTarget());
        SourceCache sourceCache = new SourceCache(options.getInputFilePath());
        this.context = new CompilationContext(
                new DiagnosticBag(options.getInputFilePath(), 50, CompilationPhase.COMPILER_SETUP, sourceCache),
                sourceCache);
        this.context.getDiagnosticBag().addPhaseAbortListener(phase -> phaseAbortRequested = true);
        enterPhase(CompilationPhase.ARGUMENT_PARSING);
    }

    /**
     * Parses command-line options and runs the compiler.
     *
     * @param args compiler command-line arguments
     */
    public static void main(String[] args) {
        System.setProperty("MODULE", "compiler");
        try {
            CompilerOptions options = CompilerOptions.create(args);
            Compiler compiler = new Compiler(options);
            compiler.compile();
            if (compiler.context.getDiagnosticBag().hasErrors()) {
                System.exit(1);
            }
        } catch (CompilerOptions.CompilerOptionException exception) {
            System.err.println(exception.getMessage());
            System.err.println(CompilerOptions.usage());
            System.exit(2);
        } catch (SourceFileReadException exception) {
            DiagnosticBag diagnostics = new DiagnosticBag(
                    exception.getFilePath(),
                    50,
                    CompilationPhase.COMPILER_SETUP,
                    null);
            diagnostics.add(null, Severity.ERROR, exception.getMessage());
            System.err.print(diagnostics.dumpReport());
            System.exit(1);
        }
    }

    private static void printSection(String title, String content) {
        System.out.println("=== " + title + " ===");
        System.out.println(content);
    }

    /** Runs frontend analysis followed by preparation and emission for the selected target. */
    public void compile() {
        enterPhase(CompilationPhase.ARGUMENT_PARSING);
        PhaseResult<CheckedProgram> result = new Frontend(context.getDiagnosticBag()).analyze(
                context.getSourceCache().getSourceCode(),
                targetBackend.builtInSignatures(),
                options.isDumpLexer() ? dump -> printSection("LEXER TOKENS", dump) : null,
                options.isDumpAst() ? dump -> printSection("AST", dump) : null);
        if (stopAfterPhase() || !result.succeeded()) return;

        if (options.isDumpIr()) printSection("NORMALIZED IR", IrPrinter.print(result.orElseThrow().ir()));

        enterPhase(CompilationPhase.TARGET_VALIDATION);
        PhaseResult<PreparedProgram> preparation = targetBackend.prepare(result.orElseThrow(), context.getDiagnosticBag());
        if (stopAfterPhase() || !preparation.succeeded()) return;
        PreparedProgram prepared = preparation.orElseThrow();
        if (options.isDumpSymbolTable()) {
            printSection("SYMBOL TABLE", prepared.dumpSymbolTable());
        }

        enterPhase(CompilationPhase.CODE_GENERATION);
        PhaseResult<OutputArtifact> artifact = prepared.generate(context.getDiagnosticBag());
        if (stopAfterPhase() || !artifact.succeeded()) return;

        enterPhase(CompilationPhase.OUTPUT_WRITING);
        PhaseResult<OutputArtifact> publication = ArtifactWriter.write(artifact.orElseThrow(), Path.of(options.getOutputFilePath()), context.getDiagnosticBag());
        if (stopAfterPhase() || !publication.succeeded()) return;
        if (!context.getDiagnosticBag().getDiagnostics().isEmpty()) System.err.print(context.getDiagnosticBag().dumpReport());
        System.out.println("Compilation Successful");
    }

    private boolean stopOnErrors() {
        if (!context.getDiagnosticBag().hasErrors()) {
            return false;
        }

        System.err.print(context.getDiagnosticBag().dumpReport());
        return true;
    }

    private boolean stopAfterPhase() {
        if (stopOnPhaseAbort()) {
            return true;
        }
        return stopOnErrors();
    }

    private boolean stopOnPhaseAbort() {
        if (!phaseAbortRequested && !context.getDiagnosticBag().isPhaseAbortRequested()) {
            return false;
        }

        phaseAbortRequested = false;
        if (context.getDiagnosticBag().hasErrors()) {
            System.err.print(context.getDiagnosticBag().dumpReport());
        }
        return true;
    }

    private void enterPhase(CompilationPhase nextPhase) {
        phaseAbortRequested = false;
        context.getDiagnosticBag().setPhase(nextPhase);
    }

}
