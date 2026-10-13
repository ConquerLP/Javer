package ch.zhaw.it.pm4.javer.compiler.ir;

import java.util.function.Consumer;

/** Exhaustive IR traversal for analysis; emitters use their own exhaustive switches. */
public final class IrWalker {
    private IrWalker() { }
    public static void walk(Ir.Node node, Consumer<Ir.Node> action) {
        action.accept(node);
        switch (node) {
            case Ir.Block block -> block.statements().forEach(n -> walk(n, action));
            case Ir.Store store -> { walk(store.address(), action); walk(store.value(), action); }
            case Ir.Evaluate evaluation -> walk(evaluation.expression(), action);
            case Ir.If branch -> { walk(branch.condition(), action); walk(branch.thenBranch(), action); walk(branch.elseBranch(), action); }
            case Ir.While loop -> { walk(loop.condition(), action); walk(loop.body(), action); }
            case Ir.DoWhile loop -> { walk(loop.body(), action); walk(loop.condition(), action); }
            case Ir.For loop -> { walk(loop.init(), action); walk(loop.condition(), action); walk(loop.update(), action); walk(loop.body(), action); }
            case Ir.Switch selection -> { walk(selection.condition(), action); selection.cases().forEach(n -> walk(n, action)); }
            case Ir.Case branch -> { branch.labels().forEach(n -> walk(n, action)); walk(branch.body(), action); }
            case Ir.Return returning -> { if (returning.value() != null) walk(returning.value(), action); }
            case Ir.IndexAddress index -> { walk(index.target(), action); walk(index.index(), action); }
            case Ir.FieldAddress field -> walk(field.target(), action);
            case Ir.Load load -> walk(load.address(), action);
            case Ir.Binary binary -> { walk(binary.left(), action); walk(binary.right(), action); }
            case Ir.Unary unary -> walk(unary.operand(), action);
            case Ir.Convert conversion -> walk(conversion.operand(), action);
            case Ir.Conditional branch -> { walk(branch.condition(), action); walk(branch.whenTrue(), action); walk(branch.whenFalse(), action); }
            case Ir.Call call -> call.arguments().forEach(n -> walk(n, action));
            case Ir.ArrayNew array -> { array.dimensions().forEach(n -> walk(n, action)); array.elements().forEach(n -> walk(n, action)); }
            case Ir.Length length -> walk(length.target(), action);
            case Ir.Sequence sequence -> { walk(sequence.effects(), action); walk(sequence.value(), action); }
            case Ir.Break ignored -> { }
            case Ir.Continue ignored -> { }
            case Ir.Literal ignored -> { }
            case Ir.EnumConstant ignored -> { }
            case Ir.LocalAddress ignored -> { }
            case Ir.StructNew ignored -> { }
        }
    }
}
