package ch.zhaw.it.pm4.javer.compiler.ast.symbol;

/**
 * Symbol table entry for a value declared inside an enum.
 */
public final class EnumValueEntry extends SymbolEntry {

    private final EnumEntry ownerEnum;
    private final int value;
    private int sizeBytes;
    private int offsetBytes;
    private final String dataLabel;

    public EnumValueEntry(String name, EnumEntry ownerEnum, int value, int sizeBytes, int offsetBytes, String dataLabel) {
        super(name);
        this.ownerEnum = ownerEnum;
        this.value = value;
        this.sizeBytes = sizeBytes;
        this.offsetBytes = offsetBytes;
        this.dataLabel = dataLabel;
    }

    public EnumEntry getOwnerEnum() {
        return ownerEnum;
    }

    public int getValue() {
        return value;
    }

    public int getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(int sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public int getOffsetBytes() {
        return offsetBytes;
    }

    public void setOffsetBytes(int offsetBytes) {
        this.offsetBytes = offsetBytes;
    }

    public String getDataLabel() {
        return dataLabel;
    }
}
