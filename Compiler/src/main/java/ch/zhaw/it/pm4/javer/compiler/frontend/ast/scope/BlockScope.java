package ch.zhaw.it.pm4.javer.compiler.frontend.ast.scope;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.symbol.SymbolEntry;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.symbol.VariableEntry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Symbol scope for local variables declared inside a block.
 */
public final class BlockScope {

    private final BlockScope parent;
    private final FunctionScope functionScope;
    private final Map<String, VariableEntry> variables = new LinkedHashMap<>();
    private final List<BlockScope> children = new ArrayList<>();

    public BlockScope(BlockScope parent, FunctionScope functionScope) {
        this.parent = parent;
        this.functionScope = functionScope;
    }

    public boolean canDefine(String name) {
        return functionScope.resolveParameter(name) == null
                && resolveVisibleVariable(name) == null;
    }

    public boolean defineVariable(VariableEntry entry) {
        if (!canDefine(entry.getName())) {
            return false;
        }
        variables.put(entry.getName(), entry);
        return true;
    }

    public VariableEntry resolveVisibleVariable(String name) {
        VariableEntry variable = variables.get(name);
        if (variable != null) {
            return variable;
        }
        return parent == null ? null : parent.resolveVisibleVariable(name);
    }

    public VariableEntry resolveVisibleVariable(String name, int currentDeclarationOrder) {
        VariableEntry variable = variables.get(name);
        if (variable != null && isVisibleAt(variable, currentDeclarationOrder)) {
            return variable;
        }
        return parent == null ? null : parent.resolveVisibleVariable(name, currentDeclarationOrder);
    }

    private boolean isVisibleAt(VariableEntry variable, int currentDeclarationOrder) {
        return variable.getDeclarationOrder() < currentDeclarationOrder;
    }

    public void addChild(BlockScope scope) {
        children.add(scope);
    }

    public Map<String, VariableEntry> getVariables() {
        return variables;
    }

    public List<BlockScope> getChildren() {
        return children;
    }

    public Map<String, SymbolEntry> getAllEntries() {
        Map<String, SymbolEntry> entries = new LinkedHashMap<>();
        variables.forEach((name, entry) -> entries.put("local " + name, entry));
        return entries;
    }
}
