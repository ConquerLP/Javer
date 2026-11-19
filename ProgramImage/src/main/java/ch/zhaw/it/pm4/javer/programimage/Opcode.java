package ch.zhaw.it.pm4.javer.programimage;

/** Fixed version-1 wire IDs. PRINT IDs are reserved and cannot be emitted. */
public enum Opcode {
    PUSHI(1),
    PUSHD(2),
    PUSHR(3),
    LOCAL(4),
    LOAD1(5),
    LOAD2(6),
    LOAD4(7),
    LOAD8(8),
    STORE1(9),
    STORE2(10),
    STORE4(11),
    STORE8(12),
    NEW(13),
    NEWA(14),
    MEMCPY(15),
    BOUNDS(16),
    POP(17),
    DUP(18),
    IADD(19),
    ISUB(20),
    IMUL(21),
    IDIV(22),
    IMOD(23),
    DADD(24),
    DSUB(25),
    DMUL(26),
    DDIV(27),
    ILT(28),
    ILE(29),
    IGT(30),
    IGE(31),
    IEQ(32),
    INE(33),
    DLT(34),
    DLE(35),
    DGT(36),
    DGE(37),
    DEQ(38),
    DNE(39),
    STREQ(40),
    STRNE(41),
    ISHL(42),
    ISHR(43),
    IAND(44),
    IOR(45),
    IXOR(46),
    INEG(47),
    DNEG(48),
    IINV(49),
    I2D(50),
    D2I(51),
    JUMP(52),
    JUMPT(53),
    JUMPF(54),
    CALL(55),
    ENTER(56),
    RET(57),
    HALT(58),
    PRINTB(59),
    PRINTC(60),
    PRINTI(61),
    PRINTD(62),
    PRINTS(63),
    MOVE_FAST(64),
    MOVE_LINEAR(65),
    MOVE_CW(66),
    MOVE_CCW(67),
    WAIT_MS(68),
    BDRIVE(69),
    IDRIVE(70),
    DDRIVE(71),
    BREAD(72),
    IREAD(73),
    DREAD(74),
    // Math instructions: pop one binary64 value, push one binary64 result; A = B = 0.
    // Domain errors and overflow are handled by the SPS runtime.
    EXP(75), // e raised to the argument.
    SQRT(76),
    ABS(77),
    ROUND(78), // Nearest integer; ties away from zero. Result remains binary64.
    FLOOR(79), // Toward negative infinity; result remains binary64.
    CEIL(80), // Toward positive infinity; result remains binary64.
    LN(81), // Natural logarithm.
    SIN(82), // Argument in radians.
    COS(83), // Argument in radians.
    TAN(84), // Argument in radians.
    ACOS(85), // Result in radians.
    ASIN(86), // Result in radians.
    ATAN(87); // Result in radians.

    public final int id;
    Opcode(int id) { this.id = id; }
}
