package ch.zhaw.it.pm4.javer.compiler;

/**
 * Ordered compiler phases used to label diagnostics with the pipeline stage
 * that produced them.
 */
public enum CompilationPhase {
    COMPILER_SETUP, ARGUMENT_PARSING, LEXING, PARSING, SYMBOL_TABLE_CREATION, NAME_RESOLUTION, TYPE_CHECKING, SEMANTIC_ANALYSIS, LAYOUT, CODE_GENERATION, OUTPUT_WRITING
}
