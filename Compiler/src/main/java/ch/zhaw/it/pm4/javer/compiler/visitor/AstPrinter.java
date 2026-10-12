package ch.zhaw.it.pm4.javer.compiler.visitor;

import ch.zhaw.it.pm4.javer.compiler.ast.nodes.AstNode;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.CompilationUnit;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.case_label.EnumCaseLabel;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.case_label.LiteralCaseLabel;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.declaration.*;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.statement.*;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.type.ArrayType;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.type.NamedType;
import ch.zhaw.it.pm4.javer.compiler.ast.nodes.type.PrimitiveType;
import ch.zhaw.it.pm4.javer.compiler.ast.scope.BlockScope;
import ch.zhaw.it.pm4.javer.compiler.ast.scope.EnumScope;
import ch.zhaw.it.pm4.javer.compiler.ast.scope.FunctionScope;
import ch.zhaw.it.pm4.javer.compiler.ast.scope.StructScope;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.*;
import ch.zhaw.it.pm4.javer.compiler.misc.SourceRange;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Formats the AST as a human-readable tree.
 */
public class AstPrinter extends AstNodeVisitorBase {

    public static final String VALUE = "value";
    public static final String CONDITION = "condition";
    public static final String TARGET = "target";
    public static final String OPERATOR = "operator";
    public static final String SIZE_BYTES = "sizeBytes";

    private final List<Boolean> isLastStack = new ArrayList<>();
    private Appendable output;

    private static <T> void visitMany(List<T> items, VisitOne<T> visitOne) {
        for (int i = 0; i < items.size(); i++) {
            visitOne.visit(items.get(i), i == items.size() - 1);
        }
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

    /**
     * Quotes a string value for tree output.
     *
     * @param value string value, or null
     * @return quoted value or {@code <null>}
     */
    protected static String quote(String value) {
        return value == null ? "<null>" : "'" + value.replace("'", "\\'") + "'";
    }

    /**
     * Quotes string objects and formats all other values with
     * {@link String#valueOf(Object)}.
     *
     * @param value value to format
     * @return formatted value
     */
    protected static String quoteValue(Object value) {
        return value instanceof String stringValue ? quote(stringValue) : String.valueOf(value);
    }

    /**
     * Prints an AST into a string.
     *
     * @param node root compilation unit
     * @return formatted tree representation
     */
    public String printToString(CompilationUnit node) {
        StringBuilder builder = new StringBuilder();
        print(node, builder);
        return builder.toString();
    }

    /**
     * Prints an AST into an arbitrary appendable target.
     *
     * @param node   root compilation unit
     * @param output appendable that receives formatted output
     */
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

    @Override
    public void visit(CompilationUnit node) {
        nodesChild("declarations", node.getDeclarations(), true);
    }

    /**
     * Writes the root tree node.
     *
     * @param node compilation unit to print
     */
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
            children.add(isLast -> scalarChild(VALUE, node.getValue(), node.getSourceRange(), isLast));
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
        if (node.isVariadic()) {
            children.add(isLast -> scalarChild("variadic", "true", node.getSourceRange(), isLast));
        }
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
        children.add(isLast -> labeledNodeChild(CONDITION, node.getCondition(), isLast));
        children.add(isLast -> labeledNodeChild("then", node.getThenBranch(), isLast));
        if (node.getElseBranch() != null) {
            children.add(isLast -> labeledNodeChild("else", node.getElseBranch(), isLast));
        }
        visitMany(children);
    }

    @Override
    public void visit(WhileStatement node) {
        List<Consumer<Boolean>> children = new ArrayList<>();
        children.add(isLast -> labeledNodeChild(CONDITION, node.getCondition(), isLast));
        children.add(isLast -> labeledNodeChild("body", node.getBody(), isLast));
        visitMany(children);
    }

    @Override
    public void visit(DoWhileStatement node) {
        List<Consumer<Boolean>> children = new ArrayList<>();
        children.add(isLast -> labeledNodeChild("body", node.getBody(), isLast));
        children.add(isLast -> labeledNodeChild(CONDITION, node.getCondition(), isLast));
        visitMany(children);
    }

    @Override
    public void visit(ForStatement node) {
        List<Consumer<Boolean>> children = new ArrayList<>();
        if (node.getForInit() != null) {
            children.add(isLast -> labeledNodeChild("init", node.getForInit(), isLast));
        }
        if (node.getCondition() != null) {
            children.add(isLast -> labeledNodeChild(CONDITION, node.getCondition(), isLast));
        }
        children.add(isLast -> nodesChild("update", node.getUpdate(), isLast));
        children.add(isLast -> labeledNodeChild("body", node.getBody(), isLast));
        visitMany(children);
    }

    @Override
    public void visit(SwitchStatement node) {
        List<Consumer<Boolean>> children = new ArrayList<>();
        children.add(isLast -> labeledNodeChild(CONDITION, node.getCondition(), isLast));
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
        children.add(isLast -> labeledNodeChild(TARGET, node.getTarget(), isLast));
        children.add(isLast -> scalarChild(OPERATOR, node.getOperator(), node.getSourceRange(), isLast));
        children.add(isLast -> labeledNodeChild(VALUE, node.getValue(), isLast));
        visitMany(children);
    }

    @Override
    public void visit(ConditionalExpression node) {
        List<Consumer<Boolean>> children = new ArrayList<>();
        children.add(isLast -> labeledNodeChild(CONDITION, node.getCondition(), isLast));
        children.add(isLast -> labeledNodeChild("whenTrue", node.getTrueExpression(), isLast));
        children.add(isLast -> labeledNodeChild("whenFalse", node.getFalseExpression(), isLast));
        visitMany(children);
    }

    @Override
    public void visit(BinaryExpression node) {
        List<Consumer<Boolean>> children = new ArrayList<>();
        children.add(isLast -> labeledNodeChild("left", node.getLeft(), isLast));
        children.add(isLast -> scalarChild(OPERATOR, node.getOperator(), node.getSourceRange(), isLast));
        children.add(isLast -> labeledNodeChild("right", node.getRight(), isLast));
        visitMany(children);
    }

    @Override
    public void visit(CastExpression node) {
        List<Consumer<Boolean>> children = new ArrayList<>();
        children.add(isLast -> labeledNodeChild("targetType", node.getTargetType(), isLast));
        children.add(isLast -> labeledNodeChild("operand", node.getOperand(), isLast));
        visitMany(children);
    }

    @Override
    public void visit(UnaryExpression node) {
        List<Consumer<Boolean>> children = new ArrayList<>();
        children.add(isLast -> scalarChild(OPERATOR, node.getKind(), node.getSourceRange(), isLast));
        children.add(isLast -> labeledNodeChild("operand", node.getOperand(), isLast));
        visitMany(children);
    }

    @Override
    public void visit(PostfixExpression node) {
        List<Consumer<Boolean>> children = new ArrayList<>();
        children.add(isLast -> labeledNodeChild(TARGET, node.getOperand(), isLast));
        children.add(isLast -> scalarChild(OPERATOR, node.getKind(), node.getSourceRange(), isLast));
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
        children.add(isLast -> labeledNodeChild(TARGET, node.getTarget(), isLast));
        children.add(isLast -> labeledNodeChild("index", node.getIndex(), isLast));
        visitMany(children);
    }

    @Override
    public void visit(MemberAccessExpression node) {
        List<Consumer<Boolean>> children = new ArrayList<>();
        children.add(isLast -> labeledNodeChild(TARGET, node.getTarget(), isLast));
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
        children.add(isLast -> scalarChild(VALUE, quoteValue(node.getValue()), node.getSourceRange(), isLast));
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
            switch (entry) {
                case StorageEntry storage -> {
                    children.add(childIsLast -> scalarChild("type", storage.getType(), null, childIsLast));
                    children.add(childIsLast -> scalarChild(SIZE_BYTES, storage.getSizeBytes(), null, childIsLast));
                    children.add(childIsLast -> scalarChild("offsetBytes", storage.getOffsetBytes(), null, childIsLast));
                    if (entry instanceof VariableEntry variable) {
                        children.add(childIsLast -> scalarChild("hasExplicitInitializer", variable.hasExplicitInitializer(), null, childIsLast));
                        children.add(childIsLast -> scalarChild("defaultValue", quoteValue(variable.getDefaultValue()), null, childIsLast));
                    }
                }
                case FunctionEntry function -> {
                    children.add(childIsLast -> scalarChild("returnType", function.getReturnType(), null, childIsLast));
                    children.add(childIsLast -> scalarChild("label", quote(function.getLabel()), null, childIsLast));
                    children.add(childIsLast -> scalarChild("parameterBytes", function.getParameterBytes(), null, childIsLast));
                    children.add(childIsLast -> scalarChild("localBytes", function.getLocalBytes(), null, childIsLast));
                    children.add(childIsLast -> scalarChild("frameSizeBytes", function.getFrameSizeBytes(), null, childIsLast));
                    if (function.getScope() != null) {
                        children.add(childIsLast -> functionScopeChild(function.getScope(), childIsLast));
                    }
                }
                case StructEntry struct -> {
                    children.add(childIsLast -> scalarChild(SIZE_BYTES, struct.getSizeBytes(), null, childIsLast));
                    if (hasStructScopeContent(struct.getScope())) {
                        children.add(childIsLast -> structScopeChild(struct.getScope(), childIsLast));
                    }
                }
                case EnumEntry enumEntry -> {
                    children.add(childIsLast -> scalarChild("dataLabel", quote(enumEntry.getDataLabel()), null, childIsLast));
                    children.add(childIsLast -> scalarChild("elementSizeBytes", enumEntry.getElementSizeBytes(), null, childIsLast));
                    children.add(childIsLast -> scalarChild(SIZE_BYTES, enumEntry.getSizeBytes(), null, childIsLast));
                    if (hasEnumScopeContent(enumEntry.getScope())) {
                        children.add(childIsLast -> enumScopeChild(enumEntry.getScope(), childIsLast));
                    }
                }
                case EnumValueEntry enumValue -> {
                    children.add(childIsLast -> scalarChild("ownerEnum", quote(enumValue.getOwnerEnum().getName()), null, childIsLast));
                    children.add(childIsLast -> scalarChild(VALUE, enumValue.getValue(), null, childIsLast));
                    children.add(childIsLast -> scalarChild(SIZE_BYTES, enumValue.getSizeBytes(), null, childIsLast));
                    children.add(childIsLast -> scalarChild("offsetBytes", enumValue.getOffsetBytes(), null, childIsLast));
                    children.add(childIsLast -> scalarChild("dataLabel", quote(enumValue.getDataLabel()), null, childIsLast));
                }
                case LabelEntry label ->
                        children.add(childIsLast -> scalarChild("label", quote(label.getLabel()), null, childIsLast));
                case DataEntry dataEntry -> {
                    children.add(childIsLast -> scalarChild("type", dataEntry.getType(), null, childIsLast));
                    children.add(childIsLast -> scalarChild(VALUE, quoteValue(dataEntry.getValue()), null, childIsLast));
                }
                default -> throw new IllegalStateException("Invalid entry type " + entry.getClass());
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

    /**
     * Writes a scalar child branch.
     *
     * @param label  branch label
     * @param value  scalar value
     * @param range  optional source range
     * @param isLast whether this is the last sibling branch
     */
    protected void scalarChild(String label, Object value, SourceRange range, boolean isLast) {
        writeBranchLine(label + ": " + value + (range != null ? " " + range : ""), isLast);
    }

    /**
     * Writes a named AST-node child branch.
     *
     * @param label  branch label
     * @param node   child node, or null
     * @param isLast whether this is the last sibling branch
     */
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

    /**
     * Writes a list of AST nodes as one labeled child branch.
     *
     * @param label  branch label
     * @param nodes  child nodes
     * @param isLast whether this is the last sibling branch
     */
    protected void nodesChild(String label, List<? extends AstNode> nodes, boolean isLast) {
        int size = nodes == null ? 0 : nodes.size();
        writeBranchLine(label + " (" + size + ")", isLast);
        withChildren(() -> {
            if (nodes != null) {
                visitMany(nodes, this::nodeChild);
            }
        }, isLast);
    }

    /**
     * Writes one tree branch line with the current indentation.
     *
     * @param text   branch text
     * @param isLast whether this is the last sibling branch
     */
    protected void writeBranchLine(String text, boolean isLast) {
        for (boolean last : isLastStack) {
            write(last ? "    " : "│   ");
        }
        write(isLast ? "└── " : "├── ");
        write(text);
        writeLine();
    }

    /**
     * Runs output logic one indentation level deeper.
     *
     * @param body   output logic for child branches
     * @param isLast whether the parent branch is the last sibling
     */
    protected void withChildren(Runnable body, boolean isLast) {
        isLastStack.add(isLast);
        body.run();
        isLastStack.removeLast();
    }

    /**
     * Visits child writer callbacks while passing sibling-position metadata.
     *
     * @param children child writer callbacks
     */
    protected void visitMany(List<Consumer<Boolean>> children) {
        visitMany(children, Consumer::accept);
    }

    /**
     * Writes a line without tree-prefix formatting.
     *
     * @param text line text
     */
    protected void writeRawLine(String text) {
        write(text);
        writeLine();
    }

    /**
     * Writes an empty line.
     */
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

    @FunctionalInterface
    private interface VisitOne<T> {
        @SuppressWarnings("unused")
        void visit(T item, boolean isLast);
    }
}
