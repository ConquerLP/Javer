package ch.zhaw.it.pm4.javer.compiler.visitor;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import ch.zhaw.it.pm4.javer.compiler.ast.nodes.AstNode;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.CompilationUnit;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.case_label.EnumCaseLabel;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.case_label.LiteralCaseLabel;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.declaration.EnumDeclaration;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.declaration.EnumItem;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.declaration.FunctionDeclaration;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.declaration.FunctionParameter;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.declaration.StructDeclaration;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.declaration.StructField;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.ArrayInitExpression;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.AssignExpression;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.BinaryExpression;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.BlockStatement;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.CallExpression;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.ConditionalExpression;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.DoWhileStatement;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.ForInitExpressionList;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.ForInitVarDeclaration;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.ForStatement;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.IfStatement;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.IndexExpression;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.LiteralExpression;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.MemberAccessExpression;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.NameExpression;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.NewExpression;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.PostfixExpression;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.ReturnStatement;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.SwitchCase;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.SwitchStatement;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.UnaryExpression;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.VarDeclarationStatement;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.WhileStatement;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.type.ArrayType;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.type.NamedType;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.type.PrimitiveType;
import ch.zhaw.it.pm4.javer.compiler.ast.scope.BlockScope;
import ch.zhaw.it.pm4.javer.compiler.ast.scope.EnumScope;
import ch.zhaw.it.pm4.javer.compiler.ast.scope.FunctionScope;
import ch.zhaw.it.pm4.javer.compiler.ast.scope.GlobalScope;
import ch.zhaw.it.pm4.javer.compiler.ast.scope.StructScope;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.DataEntry;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.EnumEntry;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.EnumValueEntry;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.FunctionEntry;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.LabelEntry;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.StorageEntry;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.StructEntry;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.SymbolEntry;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.VariableEntry;
import ch.zhaw.it.pm4.javer.compiler.misc.SourceRange;

public class AstPrinter extends AstNodeVisitorBase {

    private final List<Boolean> isLastStack = new ArrayList<>();
    private Appendable output;

    public String printToString(CompilationUnit node) {
        StringBuilder builder = new StringBuilder();
        print(node, builder);
        return builder.toString();
    }

    public void print(CompilationUnit node, Appendable output) {
        this.output = output;
        isLastStack.clear();

        try {
            writeRoot(node);
        } finally {
            this.output = null;
            isLastStack.clear();
        }
    }

    public void printToFile(CompilationUnit node, String outputFilePath) {
        printToFile(node, Path.of(outputFilePath));
    }

    public void printToFile(CompilationUnit node, Path outputFile) {
        prepareOutputDirectory(outputFile);

        try (BufferedWriter writer = Files.newBufferedWriter(
                outputFile,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {

            print(node, writer);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write AST dump to " + outputFile, exception);
        }
    }

    @Override
    public void visit(CompilationUnit node) {
        nodesChild("declarations", node.getDeclarations(), true);
    }

    protected void writeRoot(CompilationUnit node) {
        write(nodeTitle(node));
        writeLine();
        node.accept(this);
    }

    @Override
    public void visit(EnumDeclaration node) {
        List<Consumer<Boolean>> children = new ArrayList<>();
        children.add(isLast -> scalarChild("name", quote(node.getName()), node.getSourceRange(), isLast));
        children.add(isLast -> nodesChild("items", node.getItems(), isLast));
        visitMany(children);
    }

    @Override
    public void visit(EnumItem node) {
        List<Consumer<Boolean>> children = new ArrayList<>();
        children.add(isLast -> scalarChild("name", quote(node.getName()), node.getSourceRange(), isLast));
        if (node.getValue() != null) {
            children.add(isLast -> scalarChild("value", node.getValue(), node.getSourceRange(), isLast));
        }
        visitMany(children);
    }

    @Override
    public void visit(FunctionDeclaration node) {
        List<Consumer<Boolean>> children = new ArrayList<>();
        children.add(isLast -> scalarChild("name", quote(node.getName()), node.getSourceRange(), isLast));
        children.add(isLast -> labeledNodeChild("returnType", node.getReturnType(), isLast));
        children.add(isLast -> nodesChild("parameters", node.getParameters(), isLast));
        if (node.getBody() != null) {
            children.add(isLast -> labeledNodeChild("body", node.getBody(), isLast));
        }
        visitMany(children);
    }

    @Override
    public void visit(FunctionParameter node) {
        List<Consumer<Boolean>> children = new ArrayList<>();
        children.add(isLast -> labeledNodeChild("type", node.getType(), isLast));
        children.add(isLast -> scalarChild("name", quote(node.getName()), node.getSourceRange(), isLast));
        visitMany(children);
    }

    @Override
    public void visit(StructDeclaration node) {
        List<Consumer<Boolean>> children = new ArrayList<>();
        children.add(isLast -> scalarChild("name", quote(node.getName()), node.getSourceRange(), isLast));
        children.add(isLast -> nodesChild("fields", node.getFields(), isLast));
        visitMany(children);
    }

    @Override
    public void visit(StructField node) {
        List<Consumer<Boolean>> children = new ArrayList<>();
        children.add(isLast -> labeledNodeChild("type", node.getType(), isLast));
        children.add(isLast -> scalarChild("name", quote(node.getName()), node.getSourceRange(), isLast));
        visitMany(children);
    }

    @Override
    public void visit(BlockStatement node) {
        nodesChild("statements", node.getStatements(), true);
    }

    @Override
    public void visit(IfStatement node) {
        List<Consumer<Boolean>> children = new ArrayList<>();
        children.add(isLast -> labeledNodeChild("condition", node.getCondition(), isLast));
        children.add(isLast -> labeledNodeChild("then", node.getThenBranch(), isLast));
        if (node.getElseBranch() != null) {
            children.add(isLast -> labeledNodeChild("else", node.getElseBranch(), isLast));
        }
        visitMany(children);
    }

    @Override
    public void visit(WhileStatement node) {
        List<Consumer<Boolean>> children = new ArrayList<>();
        children.add(isLast -> labeledNodeChild("condition", node.getCondition(), isLast));
        children.add(isLast -> labeledNodeChild("body", node.getBody(), isLast));
        visitMany(children);
    }

    @Override
    public void visit(DoWhileStatement node) {
        List<Consumer<Boolean>> children = new ArrayList<>();
        children.add(isLast -> labeledNodeChild("body", node.getBody(), isLast));
        children.add(isLast -> labeledNodeChild("condition", node.getCondition(), isLast));
        visitMany(children);
    }

    @Override
    public void visit(ForStatement node) {
        List<Consumer<Boolean>> children = new ArrayList<>();
        if (node.getForInit() != null) {
            children.add(isLast -> labeledNodeChild("init", node.getForInit(), isLast));
        }
        if (node.getCondition() != null) {
            children.add(isLast -> labeledNodeChild("condition", node.getCondition(), isLast));
        }
        children.add(isLast -> nodesChild("update", node.getUpdate(), isLast));
        children.add(isLast -> labeledNodeChild("body", node.getBody(), isLast));
        visitMany(children);
    }

    @Override
    public void visit(SwitchStatement node) {
        List<Consumer<Boolean>> children = new ArrayList<>();
        children.add(isLast -> labeledNodeChild("condition", node.getCondition(), isLast));
        children.add(isLast -> nodesChild("cases", node.getCases(), isLast));
        visitMany(children);
    }

    @Override
    public void visit(SwitchCase node) {
        List<Consumer<Boolean>> children = new ArrayList<>();
        children.add(isLast -> scalarChild("default", node.isDefault(), node.getSourceRange(), isLast));
        children.add(isLast -> nodesChild("labels", node.getCaseLabels(), isLast));
        if (node.getStatement() != null) {
            children.add(isLast -> labeledNodeChild("body", node.getStatement(), isLast));
        }
        visitMany(children);
    }

    @Override
    public void visit(ReturnStatement node) {
        if (node.getExpression() != null) {
            labeledNodeChild("expression", node.getExpression(), true);
        }
    }

    @Override
    public void visit(VarDeclarationStatement node) {
        List<Consumer<Boolean>> children = new ArrayList<>();
        children.add(isLast -> labeledNodeChild("type", node.getType(), isLast));
        children.add(isLast -> scalarChild("name", quote(node.getName()), node.getSourceRange(), isLast));
        if (node.getInitializer() != null) {
            children.add(isLast -> labeledNodeChild("initializer", node.getInitializer(), isLast));
        }
        visitMany(children);
    }

    @Override
    public void visit(AssignExpression node) {
        List<Consumer<Boolean>> children = new ArrayList<>();
        children.add(isLast -> labeledNodeChild("target", node.getTarget(), isLast));
        children.add(isLast -> scalarChild("operator", node.getOperator(), node.getSourceRange(), isLast));
        children.add(isLast -> labeledNodeChild("value", node.getValue(), isLast));
        visitMany(children);
    }

    @Override
    public void visit(ConditionalExpression node) {
        List<Consumer<Boolean>> children = new ArrayList<>();
        children.add(isLast -> labeledNodeChild("condition", node.getCondition(), isLast));
        children.add(isLast -> labeledNodeChild("whenTrue", node.getTrueExpression(), isLast));
        children.add(isLast -> labeledNodeChild("whenFalse", node.getFalseExpression(), isLast));
        visitMany(children);
    }

    @Override
    public void visit(BinaryExpression node) {
        List<Consumer<Boolean>> children = new ArrayList<>();
        children.add(isLast -> labeledNodeChild("left", node.getLeft(), isLast));
        children.add(isLast -> scalarChild("operator", node.getOperator(), node.getSourceRange(), isLast));
        children.add(isLast -> labeledNodeChild("right", node.getRight(), isLast));
        visitMany(children);
    }

    @Override
    public void visit(UnaryExpression node) {
        List<Consumer<Boolean>> children = new ArrayList<>();
        children.add(isLast -> scalarChild("operator", node.getKind(), node.getSourceRange(), isLast));
        children.add(isLast -> labeledNodeChild("operand", node.getOperand(), isLast));
        visitMany(children);
    }

    @Override
    public void visit(PostfixExpression node) {
        List<Consumer<Boolean>> children = new ArrayList<>();
        children.add(isLast -> labeledNodeChild("target", node.getOperand(), isLast));
        children.add(isLast -> scalarChild("operator", node.getKind(), node.getSourceRange(), isLast));
        visitMany(children);
    }

    @Override
    public void visit(CallExpression node) {
        List<Consumer<Boolean>> children = new ArrayList<>();
        children.add(isLast -> scalarChild("functionName", quote(node.getFunctionName()), node.getSourceRange(), isLast));
        children.add(isLast -> nodesChild("arguments", node.getArguments(), isLast));
        visitMany(children);
    }

    @Override
    public void visit(IndexExpression node) {
        List<Consumer<Boolean>> children = new ArrayList<>();
        children.add(isLast -> labeledNodeChild("target", node.getTarget(), isLast));
        children.add(isLast -> labeledNodeChild("index", node.getIndex(), isLast));
        visitMany(children);
    }

    @Override
    public void visit(MemberAccessExpression node) {
        List<Consumer<Boolean>> children = new ArrayList<>();
        children.add(isLast -> labeledNodeChild("target", node.getTarget(), isLast));
        children.add(isLast -> scalarChild("member", quote(node.getMemberName()), node.getSourceRange(), isLast));
        visitMany(children);
    }

    @Override
    public void visit(NewExpression node) {
        List<Consumer<Boolean>> children = new ArrayList<>();
        children.add(isLast -> labeledNodeChild("type", node.getType(), isLast));
        children.add(isLast -> nodesChild("dimensions", node.getDimensions(), isLast));
        if (node.getArrayInit() != null) {
            children.add(isLast -> labeledNodeChild("arrayInit", node.getArrayInit(), isLast));
        }
        visitMany(children);
    }

    @Override
    public void visit(ArrayInitExpression node) {
        nodesChild("elements", node.getElements(), true);
    }

    @Override
    public void visit(NameExpression node) {
        scalarChild("name", quote(node.getName()), node.getSourceRange(), true);
    }

    @Override
    public void visit(LiteralExpression<?> node) {
        List<Consumer<Boolean>> children = new ArrayList<>();
        children.add(isLast -> scalarChild("kind", node.getKind(), node.getSourceRange(), isLast));
        children.add(isLast -> scalarChild("value", quoteValue(node.getValue()), node.getSourceRange(), isLast));
        visitMany(children);
    }

    @Override
    public void visit(LiteralCaseLabel node) {
        labeledNodeChild("literal", node.getLiteral(), true);
    }

    @Override
    public void visit(EnumCaseLabel node) {
        List<Consumer<Boolean>> children = new ArrayList<>();
        children.add(isLast -> scalarChild("enumTypeName", quote(node.getEnumTypeName()), node.getSourceRange(), isLast));
        children.add(isLast -> scalarChild("enumValueName", quote(node.getEnumValueName()), node.getSourceRange(), isLast));
        visitMany(children);
    }

    @Override
    public void visit(ArrayType node) {
        labeledNodeChild("baseType", node.getBaseType(), true);
    }

    @Override
    public void visit(NamedType node) {
        List<Consumer<Boolean>> children = new ArrayList<>();
        children.add(isLast -> scalarChild("kind", node.getKind(), node.getSourceRange(), isLast));
        children.add(isLast -> scalarChild("name", quote(node.getName()), node.getSourceRange(), isLast));
        visitMany(children);
    }

    @Override
    public void visit(PrimitiveType node) {
        scalarChild("kind", node.getKind(), node.getSourceRange(), true);
    }

    @Override
    public void visit(ForInitVarDeclaration node) {
        labeledNodeChild("varDeclaration", node.getVarDeclaration(), true);
    }

    @Override
    public void visit(ForInitExpressionList node) {
        nodesChild("expressions", node.getExpressions(), true);
    }

    protected void globalScopeChild(GlobalScope globalScope, boolean isLast) {
        Map<String, SymbolEntry> entries = globalScope.getAllEntries();
        writeBranchLine("globalScope (" + entries.size() + ")", isLast);
        withChildren(() -> symbolEntriesChildren(entries), isLast);
    }

    private void functionScopeChild(FunctionScope scope, boolean isLast) {
        Map<String, SymbolEntry> entries = scope.getAllEntries();
        writeBranchLine("functionScope (" + entries.size() + ")", isLast);
        withChildren(() -> {
            List<Consumer<Boolean>> children = symbolEntryConsumers(entries);
            if (hasBlockScopeContent(scope.getRootBlock())) {
                children.add(childIsLast -> blockScopeChild(scope.getRootBlock(), childIsLast));
            }
            visitMany(children);
        }, isLast);
    }

    private void blockScopeChild(BlockScope scope, boolean isLast) {
        Map<String, SymbolEntry> entries = scope.getAllEntries();
        if (!hasBlockScopeContent(scope)) {
            return;
        }

        writeBranchLine("blockScope (" + entries.size() + ")", isLast);
        withChildren(() -> {
            List<Consumer<Boolean>> children = symbolEntryConsumers(entries);
            scope.getChildren().stream()
                    .filter(this::hasBlockScopeContent)
                    .forEach(childScope -> children.add(childIsLast -> blockScopeChild(childScope, childIsLast)));
            visitMany(children);
        }, isLast);
    }

    private void structScopeChild(StructScope scope, boolean isLast) {
        Map<String, SymbolEntry> entries = scope.getAllEntries();
        if (entries.isEmpty()) {
            return;
        }

        writeBranchLine("structScope (" + entries.size() + ")", isLast);
        withChildren(() -> symbolEntriesChildren(entries), isLast);
    }

    private void enumScopeChild(EnumScope scope, boolean isLast) {
        Map<String, SymbolEntry> entries = scope.getAllEntries();
        if (entries.isEmpty()) {
            return;
        }

        writeBranchLine("enumScope (" + entries.size() + ")", isLast);
        withChildren(() -> symbolEntriesChildren(entries), isLast);
    }

    private void symbolEntriesChildren(Map<String, SymbolEntry> entries) {
        visitMany(symbolEntryConsumers(entries));
    }

    private List<Consumer<Boolean>> symbolEntryConsumers(Map<String, SymbolEntry> entries) {
        List<Map.Entry<String, SymbolEntry>> sortedEntries = entries.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();
        List<Consumer<Boolean>> children = new ArrayList<>();
        sortedEntries.forEach(entry -> children.add(childIsLast -> symbolEntryChild(entry.getValue(), childIsLast)));
        return children;
    }

    private void symbolEntryChild(SymbolEntry entry, boolean isLast) {
        writeBranchLine(entry.getName() + ": " + entry.getClass().getSimpleName(), isLast);
        withChildren(() -> {
            List<Consumer<Boolean>> children = new ArrayList<>();
            if (entry instanceof StorageEntry storage) {
                children.add(childIsLast -> scalarChild("type", storage.getType(), null, childIsLast));
                children.add(childIsLast -> scalarChild("sizeBytes", storage.getSizeBytes(), null, childIsLast));
                children.add(childIsLast -> scalarChild("offsetBytes", storage.getOffsetBytes(), null, childIsLast));
                if (entry instanceof VariableEntry variable) {
                    children.add(childIsLast -> scalarChild("hasExplicitInitializer", variable.hasExplicitInitializer(), null, childIsLast));
                    children.add(childIsLast -> scalarChild("defaultValue", quoteValue(variable.getDefaultValue()), null, childIsLast));
                }
            } else if (entry instanceof FunctionEntry function) {
                children.add(childIsLast -> scalarChild("returnType", function.getReturnType(), null, childIsLast));
                children.add(childIsLast -> scalarChild("label", quote(function.getLabel()), null, childIsLast));
                children.add(childIsLast -> scalarChild("parameterBytes", function.getParameterBytes(), null, childIsLast));
                children.add(childIsLast -> scalarChild("localBytes", function.getLocalBytes(), null, childIsLast));
                children.add(childIsLast -> scalarChild("frameSizeBytes", function.getFrameSizeBytes(), null, childIsLast));
                if (function.getScope() != null) {
                    children.add(childIsLast -> functionScopeChild(function.getScope(), childIsLast));
                }
            } else if (entry instanceof StructEntry struct) {
                children.add(childIsLast -> scalarChild("sizeBytes", struct.getSizeBytes(), null, childIsLast));
                if (hasStructScopeContent(struct.getScope())) {
                    children.add(childIsLast -> structScopeChild(struct.getScope(), childIsLast));
                }
            } else if (entry instanceof EnumEntry enumEntry) {
                children.add(childIsLast -> scalarChild("dataLabel", quote(enumEntry.getDataLabel()), null, childIsLast));
                children.add(childIsLast -> scalarChild("elementSizeBytes", enumEntry.getElementSizeBytes(), null, childIsLast));
                children.add(childIsLast -> scalarChild("sizeBytes", enumEntry.getSizeBytes(), null, childIsLast));
                if (hasEnumScopeContent(enumEntry.getScope())) {
                    children.add(childIsLast -> enumScopeChild(enumEntry.getScope(), childIsLast));
                }
            } else if (entry instanceof EnumValueEntry enumValue) {
                children.add(childIsLast -> scalarChild("ownerEnum", quote(enumValue.getOwnerEnum().getName()), null, childIsLast));
                children.add(childIsLast -> scalarChild("value", enumValue.getValue(), null, childIsLast));
                children.add(childIsLast -> scalarChild("sizeBytes", enumValue.getSizeBytes(), null, childIsLast));
                children.add(childIsLast -> scalarChild("offsetBytes", enumValue.getOffsetBytes(), null, childIsLast));
                children.add(childIsLast -> scalarChild("dataLabel", quote(enumValue.getDataLabel()), null, childIsLast));
            } else if (entry instanceof LabelEntry label) {
                children.add(childIsLast -> scalarChild("label", quote(label.getLabel()), null, childIsLast));
            } else if (entry instanceof DataEntry dataEntry) {
                children.add(childIsLast -> scalarChild("type", dataEntry.getType(), null, childIsLast));
                children.add(childIsLast -> scalarChild("value", quoteValue(dataEntry.getValue()), null, childIsLast));
            }
            visitMany(children);
        }, isLast);
    }

    private boolean hasBlockScopeContent(BlockScope scope) {
        return scope != null
                && (!scope.getAllEntries().isEmpty() || scope.getChildren().stream().anyMatch(this::hasBlockScopeContent));
    }

    private boolean hasStructScopeContent(StructScope scope) {
        return scope != null && !scope.getAllEntries().isEmpty();
    }

    private boolean hasEnumScopeContent(EnumScope scope) {
        return scope != null && !scope.getAllEntries().isEmpty();
    }

    protected void scalarChild(String label, Object value, SourceRange range, boolean isLast) {
        writeBranchLine(label + ": " + value + (range != null ? " " + range : ""), isLast);
    }

    protected void labeledNodeChild(String label, AstNode node, boolean isLast) {
        if (node == null) {
            writeBranchLine(label + ": <null>", isLast);
            return;
        }

        writeBranchLine(label + " " + nodeTitle(node), isLast);
        withChildren(() -> node.accept(this), isLast);
    }

    private void nodeChild(AstNode node, boolean isLast) {
        writeBranchLine(nodeTitle(node), isLast);
        withChildren(() -> node.accept(this), isLast);
    }

    protected void nodesChild(String label, List<? extends AstNode> nodes, boolean isLast) {
        int size = nodes == null ? 0 : nodes.size();
        writeBranchLine(label + " (" + size + ")", isLast);
        withChildren(() -> {
            if (nodes != null) {
                visitMany(nodes, this::nodeChild);
            }
        }, isLast);
    }

    protected void writeBranchLine(String text, boolean isLast) {
        for (boolean last : isLastStack) {
            write(last ? "    " : "\u2502   ");
        }
        write(isLast ? "\u2514\u2500\u2500 " : "\u251c\u2500\u2500 ");
        write(text);
        writeLine();
    }

    protected void withChildren(Runnable body, boolean isLast) {
        isLastStack.add(isLast);
        body.run();
        isLastStack.remove(isLastStack.size() - 1);
    }

    private static <T> void visitMany(List<T> items, VisitOne<T> visitOne) {
        for (int i = 0; i < items.size(); i++) {
            visitOne.visit(items.get(i), i == items.size() - 1);
        }
    }

    protected void visitMany(List<Consumer<Boolean>> children) {
        visitMany(children, Consumer::accept);
    }

    private static String nodeName(AstNode node) {
        return removeAstNodeSuffix(node.getClass().getSimpleName());
    }

    private static String nodeTitle(AstNode node) {
        return nodeName(node) + " " + node.getSourceRange();
    }

    private static String removeAstNodeSuffix(String typeName) {
        return typeName.endsWith("AstNode") ? typeName.substring(0, typeName.length() - "AstNode".length()) : typeName;
    }

    protected static String quote(String value) {
        return value == null ? "<null>" : "'" + value.replace("'", "\\'") + "'";
    }

    protected static String quoteValue(Object value) {
        return value instanceof String stringValue ? quote(stringValue) : String.valueOf(value);
    }

    protected void writeRawLine(String text) {
        write(text);
        writeLine();
    }

    protected void writeBlankLine() {
        writeLine();
    }

    private void writeLine() {
        write(System.lineSeparator());
    }

    private void write(String text) {
        try {
            output.append(text);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write AST dump.", exception);
        }
    }

    private static void prepareOutputDirectory(Path outputFile) {
        Path parent = outputFile.toAbsolutePath().getParent();
        if (parent == null) {
            return;
        }

        try {
            Files.createDirectories(parent);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not create AST dump directory " + parent, exception);
        }
    }

    @FunctionalInterface
    private interface VisitOne<T> {
        void visit(T item, boolean isLast);
    }
}
