package ch.zhaw.it.pm4.javer.compiler.frontend;

import ch.zhaw.it.pm4.javer.compiler.ir.Ir.Type;
import java.util.List;
import java.util.Objects;

/** Describes a target runtime function without sharing frontend symbols or scopes. */
public record BuiltInSignature(String name, Type returnType, List<Parameter> parameters) {
    public BuiltInSignature {
        Objects.requireNonNull(name);
        Objects.requireNonNull(returnType);
        parameters = List.copyOf(parameters);
    }

    public record Parameter(String name, Type type) {
        public Parameter {
            Objects.requireNonNull(name);
            Objects.requireNonNull(type);
        }
    }
}
