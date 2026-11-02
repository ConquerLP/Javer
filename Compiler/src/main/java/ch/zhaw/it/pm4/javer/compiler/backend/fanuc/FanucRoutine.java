package ch.zhaw.it.pm4.javer.compiler.backend.fanuc;

/** Shared ABI for sequential, non-nesting instruction routines. */
enum FanucRoutine {
    PUSH, POP, LOCAL, ENTER, DUP, LOAD, STORE, AADD, FIELD, JUMPF, JUMPT, CALL, RET, NEW, NEWA, MEMCPY, BOUNDS, SIGNED, UNSIGNED, D2I, INEG, DNEG, IINV, IADD, ISUB, IMUL, IDIV, IMOD, IAND, IOR, IXOR, ISHL, ISHR, IEQ, INE, ILT, ILE, IGT, IGE, DADD, DSUB, DMUL, DDIV, DEQ, DNE, DLT, DLE, DGT, DGE;

    int parameter(int index) { return 104 + index; }
    int local(int index) { return 107 + index; }
    int returnRegister() { return 115; }
    static int memoryOffset() { return 118; }
    String routineName() { return "rt_" + name().toLowerCase(java.util.Locale.ROOT); }
    static String describe() {
        return "#100=SP; #101=FP; #102=HP; #103=error\n"
                + "#104..#105=instruction parameters/result; #106=helper parameter/result; #107..#114=shared work registers\n"
                + "#115=instruction return label; #116=computed jump target/check condition\n"
                + "#117=helper return label; #118=check error code\n";
    }
}
