package ch.zhaw.it.pm4.javer.compiler.frontend.ast.scope;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.symbol.ParameterEntry;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.symbol.SymbolEntry;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Symbol scope for parameters, labels, and the root block of a function.
 */
public final class FunctionScope {

    private final Map<String, ParameterEntry> parameters = new LinkedHashMap<>();
    private BlockScope rootBlock;

    public boolean defineParameter(ParameterEntry entry) {
        if (parameters.containsKey(entry.getName())) {
            return false;
        }
        parameters.put(entry.getName(), entry);
        return true;
    }

    public ParameterEntry resolveParameter(String name) {
        return parameters.get(name);
    }

    public BlockScope getRootBlock() {
        return rootBlock;
    }

    public void setRootBlock(BlockScope rootBlock) {
        this.rootBlock = rootBlock;
    }

    public Map<String, ParameterEntry> getParameters() {
        return parameters;
    }

    public Map<String, SymbolEntry> getAllEntries() {
        Map<String, SymbolEntry> entries = new LinkedHashMap<>();
        parameters.forEach((name, entry) -> entries.put("parameter " + name, entry));
        return entries;
    }
}
