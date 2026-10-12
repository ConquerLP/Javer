package ch.zhaw.it.pm4.javer.compiler;

/**
 * Supported compiler output targets.
 */
public enum CompilationTarget {
    VM("vm", ".jbc"),
    FANUC_MACRO_B("fanuc-macro-b", ".nc");

    private final String optionValue;
    private final String outputExtension;

    CompilationTarget(String optionValue, String outputExtension) {
        this.optionValue = optionValue;
        this.outputExtension = outputExtension;
    }

    public String getOptionValue() {
        return optionValue;
    }

    public String getOutputExtension() {
        return outputExtension;
    }

    public static CompilationTarget parse(String value) {
        for (CompilationTarget target : values()) {
            if (target.optionValue.equals(value)) {
                return target;
            }
        }
        throw new CompilerOptions.CompilerOptionException("Unknown compiler target: " + value);
    }
}
