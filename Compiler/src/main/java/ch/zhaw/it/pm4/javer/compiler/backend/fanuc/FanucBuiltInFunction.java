package ch.zhaw.it.pm4.javer.compiler.backend.fanuc;

import ch.zhaw.it.pm4.javer.compiler.frontend.BuiltInSignature;
import ch.zhaw.it.pm4.javer.compiler.ir.Ir;
import java.util.Arrays;
import java.util.List;

/** Target-only builtin names, signatures and stack instructions. */
enum FanucBuiltInFunction {
    SIN("sin"), COS("cos"), TAN("tan"), SQRT("sqrt"), ABS("abs"), FIX("fix"), FUP("fup"),
    ASIN("asin"), ACOS("acos"), ATAN("atan"), LN("ln"), EXP("exp"),
    MOVEFAST("moveFast", "x", "y", "z"),
    MOVELINEAR("moveLinear", "x", "y", "z", "feed"),
    MOVECIRCLECW("moveCircleCW", "x", "y", "z", "radius", "feed"),
    MOVECIRCLECCW("moveCircleCCW", "x", "y", "z", "radius", "feed");

    private final String name;
    private final Ir.Type returnType;
    private final List<BuiltInSignature.Parameter> parameters;

    FanucBuiltInFunction(String name) {
        this.name = name;
        returnType = Ir.Type.DOUBLE;
        parameters = List.of(new BuiltInSignature.Parameter("value", Ir.Type.DOUBLE));
    }

    FanucBuiltInFunction(String name, String... parameters) {
        this.name = name;
        returnType = Ir.Type.VOID;
        this.parameters = Arrays.stream(parameters).map(p -> new BuiltInSignature.Parameter(p, Ir.Type.DOUBLE)).toList();
    }

    BuiltInSignature signature() { return new BuiltInSignature(name, returnType, parameters); }

    static FanucBuiltInFunction find(String name) {
        for (var builtin : values()) if (builtin.name.equals(name)) return builtin;
        return null;
    }

    boolean matches(Ir.Signature signature) {
        return signature.returnType().equals(returnType)
                && signature.parameters().equals(parameters.stream().map(BuiltInSignature.Parameter::type).toList());
    }
}
