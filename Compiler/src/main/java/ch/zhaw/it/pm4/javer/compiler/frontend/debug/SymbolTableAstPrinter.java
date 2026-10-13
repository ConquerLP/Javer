package ch.zhaw.it.pm4.javer.compiler.frontend.debug;

import ch.zhaw.it.pm4.javer.compiler.frontend.ast.nodes.CompilationUnit;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.scope.*;
import ch.zhaw.it.pm4.javer.compiler.frontend.ast.symbol.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Formats symbol-table information as readable tables for compiler diagnostics
 * and expert output.
 */
public final class SymbolTableAstPrinter extends AstPrinter {

    public static final String NONE = "<none>";

    @Override
    protected void writeRoot(CompilationUnit node) {
        writeSymbolTables(node.getGlobalScope());
    }

    @Override
    public void visit(CompilationUnit node) {
        writeSymbolTables(node.getGlobalScope());
    }

    private void writeSymbolTables(GlobalScope globalScope) {
        List<Map.Entry<String, SymbolEntry>> entries = sortedEntries(globalScope.getAllEntries());

        List<List<String>> globalRows = new ArrayList<>();
        globalRows.add(List.of("kind", "name", "type"));
        for (Map.Entry<String, SymbolEntry> entry : entries) {
            globalRows.add(globalRow(entry.getValue()));
        }
        writeTable("table: global", globalRows);

        for (Map.Entry<String, SymbolEntry> entry : entries) {
            SymbolEntry symbol = entry.getValue();
            if (symbol instanceof FunctionEntry function) {
                writeFunctionTable(function);
            } else if (symbol instanceof StructEntry struct) {
                writeStructTable(struct);
            } else if (symbol instanceof EnumEntry enumEntry) {
                writeEnumTable(enumEntry);
            }
        }
    }

    private List<String> globalRow(SymbolEntry entry) {
        if (entry instanceof FunctionEntry function) {
            return List.of(
                    "func",
                    function.getName(),
                    String.valueOf(function.getReturnType()));
        }
        if (entry instanceof StructEntry struct) {
            return List.of("struct", struct.getName(), "struct");
        }
        if (entry instanceof EnumEntry enumEntry) {
            return List.of("enum", enumEntry.getName(), "enum");
        }
        return List.of(entry.getClass().getSimpleName(), entry.getName(), "");
    }

    private void writeFunctionTable(FunctionEntry function) {
        FunctionScope scope = function.getScope();
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("section", "scope", "name", "type", "details"));

        if (scope != null && !scope.getParameters().isEmpty()) {
            for (ParameterEntry parameter : scope.getParameters().values()) {
                rows.add(storageRow("param", "function", parameter, parameterDetails(parameter)));
            }
        } else {
            rows.add(List.of("params", "function", NONE, "", ""));
        }

        if (scope != null && scope.getRootBlock() != null) {
            collectBlockRows(scope.getRootBlock(), "<root>", new int[]{0}, rows);
        } else {
            rows.add(List.of("blocks", NONE, "", "", ""));
        }

        writeTable("table: function " + function.getName()
                + " | return: " + function.getReturnType(), rows);
    }

    private void collectBlockRows(BlockScope blockScope, String parentId, int[] nextBlockId, List<List<String>> rows) {
        String blockId = "#" + nextBlockId[0]++;
        rows.add(List.of("block", blockId, "parent", parentId, "locals: " + blockScope.getVariables().size()));

        for (VariableEntry variable : blockScope.getVariables().values()) {
            rows.add(storageRow("local", blockId, variable, variableDetails(variable)));
        }

        for (BlockScope child : blockScope.getChildren()) {
            collectBlockRows(child, blockId, nextBlockId, rows);
        }
    }

    private String variableDetails(VariableEntry variable) {
        return "init: " + variable.hasExplicitInitializer() + ", default: " + quoteValue(variable.getDefaultValue());
    }

    private String parameterDetails(ParameterEntry parameter) {
        if (!parameter.isVariadic()) {
            return "";
        }
        return "variadic element: " + parameter.getVariadicElementType();
    }

    private List<String> storageRow(String section, String scope, StorageEntry storage, String details) {
        return List.of(
                section,
                scope,
                storage.getName(),
                String.valueOf(storage.getType()),
                details);
    }

    private void writeStructTable(StructEntry struct) {
        StructScope scope = struct.getScope();
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("kind", "name", "type"));
        if (scope == null || scope.getFields().isEmpty()) {
            rows.add(List.of("field", NONE, ""));
        } else {
            for (FieldEntry field : scope.getFields().values()) {
                rows.add(List.of(
                        "field",
                        field.getName(),
                        String.valueOf(field.getType())));
            }
        }
        writeTable("table: struct " + struct.getName(), rows);
    }

    private void writeEnumTable(EnumEntry enumEntry) {
        EnumScope scope = enumEntry.getScope();
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("kind", "name", VALUE));
        if (scope == null || scope.getValues().isEmpty()) {
            rows.add(List.of(VALUE, NONE, ""));
        } else {
            for (EnumValueEntry value : scope.getValues().values()) {
                rows.add(List.of(
                        VALUE,
                        value.getName(),
                        String.valueOf(value.getValue())));
            }
        }
        writeTable("table: enum " + enumEntry.getName(), rows);
    }

    private void writeTable(String title, List<List<String>> rows) {
        int[] widths = columnWidths(title, rows);
        String border = border(widths, '=');

        writeRawLine(border);
        writeRawLine(spanningRow(title, border.length()));
        writeRawLine(border);

        if (!rows.isEmpty()) {
            writeRows(widths, List.of(rows.getFirst()));
            writeRawLine(border(widths, '-'));
            writeRows(widths, rows.subList(1, rows.size()));
        }

        writeRawLine(border);
        writeBlankLine();
    }

    private void writeRows(int[] widths, List<List<String>> rows) {
        for (List<String> row : rows) {
            StringBuilder line = new StringBuilder("|");
            for (int i = 0; i < widths.length; i++) {
                String value = i < row.size() ? row.get(i) : "";
                line.append(' ').append(padRight(value, widths[i])).append(" |");
            }
            writeRawLine(line.toString());
        }
    }

    private int[] columnWidths(String title, List<List<String>> rows) {
        int columns = rows.stream().mapToInt(List::size).max().orElse(1);
        int[] widths = new int[columns];
        for (List<String> row : rows) {
            for (int i = 0; i < row.size(); i++) {
                widths[i] = Math.max(widths[i], row.get(i).length());
            }
        }

        int currentWidth = border(widths, '=').length();
        int requiredWidth = title.length() + 4;
        if (currentWidth < requiredWidth) {
            widths[columns - 1] += requiredWidth - currentWidth;
        }
        return widths;
    }

    private String border(int[] widths, char character) {
        int width = 1;
        for (int columnWidth : widths) {
            width += columnWidth + 3;
        }
        return String.valueOf(character).repeat(width);
    }

    private String spanningRow(String value, int width) {
        return "| " + padRight(value, width - 4) + " |";
    }

    private String padRight(String value, int width) {
        return value + " ".repeat(Math.max(0, width - value.length()));
    }

    private List<Map.Entry<String, SymbolEntry>> sortedEntries(Map<String, SymbolEntry> entries) {
        return entries.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();
    }
}
