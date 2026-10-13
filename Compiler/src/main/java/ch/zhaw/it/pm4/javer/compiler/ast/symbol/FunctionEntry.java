package ch.zhaw.it.pm4.javer.compiler.ast.symbol;

import ch.zhaw.it.pm4.javer.compiler.ast.scope.FunctionScope;
import ch.zhaw.it.pm4.javer.compiler.ast.typeinfo.TypeInfo;
import ch.zhaw.it.pm4.javer.compiler.ast.typeinfo.UnknownTypeInfo;

/**
 * Symbol table entry for a function and its semantic signature.
 */
public final class FunctionEntry extends SymbolEntry {

    private TypeInfo returnType = UnknownTypeInfo.INSTANCE;
    private FunctionScope scope;
    private boolean builtIn;
    private ParameterEntry variadicParameter;

    public FunctionEntry(String name) {
        super(name);
    }

    public TypeInfo getReturnType() {
        return returnType;
    }

    public void setReturnType(TypeInfo returnType) {
        this.returnType = returnType == null ? UnknownTypeInfo.INSTANCE : returnType;
    }

    public FunctionScope getScope() {
        return scope;
    }

    public void setScope(FunctionScope scope) {
        this.scope = scope;
    }

    public boolean isBuiltIn() {
        return builtIn;
    }

    public void setBuiltIn(boolean builtIn) {
        this.builtIn = builtIn;
    }

    public boolean isVariadic() {
        return variadicParameter != null;
    }

    public ParameterEntry getVariadicParameter() {
        return variadicParameter;
    }

    public void setVariadicParameter(ParameterEntry variadicParameter) {
        this.variadicParameter = variadicParameter;
    }
}
