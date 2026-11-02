package ch.zhaw.it.pm4.javer.compiler.backend.fanuc;

import java.math.BigDecimal;

/** Decimal constants accepted by the target without scientific notation. */
final class FanucNumbers {
    static final String MAX_FLOAT = new BigDecimal((double) Float.MAX_VALUE).toPlainString();

    private FanucNumbers() { }

    static String format(float value) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite FANUC constant");
        // BigDecimal has no signed zero; retain its Float32 sign explicitly.
        if (Float.floatToRawIntBits(value) == Float.floatToRawIntBits(-0.0f)) return "-0.0";
        return new BigDecimal(Float.toString(value)).toPlainString();
    }
}
