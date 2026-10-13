package ch.zhaw.it.pm4.javer.compiler.ast.symbol;

/**
 * Symbol table entry for a value declared inside an enum.
 */
public final class EnumValueEntry extends SymbolEntry {

    private final EnumEntry ownerEnum;
    private final int value;

    public EnumValueEntry(String name, EnumEntry ownerEnum, int value) {
        super(name);
        this.ownerEnum = ownerEnum;
        this.value = value;
    }

    public EnumEntry getOwnerEnum() {
        return ownerEnum;
    }

    public int getValue() {
        return value;
    }

}
