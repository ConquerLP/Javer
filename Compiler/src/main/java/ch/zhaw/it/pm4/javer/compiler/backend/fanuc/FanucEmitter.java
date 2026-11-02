package ch.zhaw.it.pm4.javer.compiler.backend.fanuc;

import ch.zhaw.it.pm4.javer.compiler.diagnostics.*;

import java.util.*;
import java.util.function.Consumer;

/**
 * Lowers stack operations to register assignments and computed GOTO only.
 */
final class FanucEmitter {
    private final Consumer<String> out;
    private final DiagnosticBag diagnostics;
    private final Map<String, Integer> labels = new LinkedHashMap<>(), data = new LinkedHashMap<>();
    private final Map<String, Integer> parameters = new HashMap<>();
    private int next = 1, heap, stack, limit, args;
    private final int end = 65535;
    private FanucRoutine current;
    private boolean emittingHelper;
    private enum Helper { CHECK, PUSH, POP, FINITE, ACCESS }

    FanucEmitter(Consumer<String> out, DiagnosticBag diagnostics) {
        this.out = out;
        this.diagnostics = diagnostics;
    }

    private void line(String s) { out.accept(s); }

    private void comment(String text) {
        if (!text.endsWith("-start") && !text.endsWith("-end")) return;
        line("(" + text + ")");
        if (text.endsWith("-end")) line("");
    }

    private int label(String s) {
        return labels.computeIfAbsent(s, k -> fresh());
    }

    private int fresh() {
        if (next >= end) throw new ArithmeticException("Too many FANUC labels");
        return next++;
    }

    private void mark(int n) {
        line("N" + n);
    }

    private void set(int r, String v) {
        line("#" + r + " = [" + v + "]");
    }

    private void set(int r, String v, String comment) {
        line("#" + r + " = [" + v + "]" + " (" + comment + ")");
    }

    private String mem(String a) {
        return "#[" + FanucRoutine.memoryOffset() + " + " + a + "]";
    }

    private void jump(int n) {
        line("GOTO " + n);
    }

    private void branch(String condition, int yes, int no) {
        set(116, no + " + [" + condition + "] * " + (yes - no));
        line("GOTO #116");
    }

    private void section(String title) {
        line(""); line("");
        line("(******************** " + title + " ********************)");
        line(""); line("");
    }

    private void helper(Helper helper) {
        if (emittingHelper) throw new IllegalStateException("Nested runtime helper: " + helper);
        int back = fresh();
        set(117, Integer.toString(back));
        jump(label("helper_" + helper.name()));
        mark(back);
    }

    private void check(String bad, int code) {
        set(116, bad);
        set(118, Integer.toString(code));
        helper(Helper.CHECK);
    }

    private void inlineCheck(String bad, int code) {
        int fail = fresh(), ok = fresh();
        branch(bad, fail, ok);
        mark(fail); set(103, Integer.toString(code)); jump(end); mark(ok);
    }

    private void push(String value) {
        set(106, value);
        helper(Helper.PUSH);
    }

    private void pop(int register) {
        helper(Helper.POP);
        set(register, "#106");
    }

    private void wrap() {
        set(107, "#107 MOD 65536");
        set(107, "#107 + [#107 LT 0] * 65536");
        set(107, "#107 - [#107 GE 32768] * 65536");
    }

    private void trunc() { set(107, "#107 - [#107 MOD 1]"); }

    private void finite() { helper(Helper.FINITE); }

    void emit(List<String> code, Map<String, List<String>> constants) {
        int cursor = 1;
        for (var entry : constants.entrySet()) {
            data.put(entry.getKey(), cursor);
            cursor += entry.getValue().size();
        }
        heap = cursor;
        stack = heap + 16384;
        limit = stack + 16384;
        if (limit > 65536) {
            diagnostics.add(null, Severity.ERROR, "FANUC ROM, heap and stack exceed the 16-bit address space.");
            return;
        }
        for (String s : code) {
            if (s.endsWith(":")) label(s.substring(0, s.length() - 1));
            if (s.startsWith("CALL, ")) {
                String[] p = s.split(",\\s*");
                parameters.put(p[1], Integer.parseInt(p[2]));
            }
        }
        section("Initialization");
        set(101, "0", "FP - frame pointer");
        set(103, "0", "Error code");
        comment("Memory initialization - logical stack and heap bases");
        set(100, "" + stack, "SP - stack pointer");
        set(102, "" + heap, "HP - heap pointer");
        if (!constants.isEmpty()) comment("ROM initialization - enums and constant array templates");
        for (var entry : constants.entrySet()) {
            int a = data.get(entry.getKey());
            for (String v : entry.getValue()) line(mem("" + a++) + " = " + v);
        }
        set(FanucRoutine.CALL.parameter(0), Integer.toString(label("_main")), "CALL argument - main entry label");
        set(FanucRoutine.CALL.parameter(1), "0", "CALL argument - parameter count");
        set(FanucRoutine.CALL.returnRegister(), Integer.toString(end), "Return label - program exit");
        line("GOTO " + label(FanucRoutine.CALL.routineName()) + " (CALL routine entry)");
        section("User code");
        for (String s : code) instruction(s);
        jump(end);
        library();
        helpers();
        section("Program exit");
        comment("Shared program exit - normal completion or runtime error");
        mark(end);
        line("M30");
    }

    private void call(String target, int count, int back) {
        invokeWithContinuation(FanucRoutine.CALL, back, Integer.toString(label(target)), Integer.toString(count));
    }

    private void access(boolean write) {
        set(106, write ? "1" : "0");
        helper(Helper.ACCESS);
    }

    private void allocate(boolean array) {
        comment(array ? "Allocate array - length header and elements" : "Allocate struct - field storage");
        pop(107);
        check("#107 LT 0", 6);
        if (array) set(107, "#107 + 1");
        set(108, "#107");
        check("[#102 + #108 + 1] GT " + stack, 5);
        comment("Write allocation header - advance heap pointer");
        line(mem("#102") + " = #108");
        set(109, "#102 + 1");
        set(102, "#102 + #108 + 1");
        comment("Zero initialize allocated cells");
        set(110, "#109");
        int loop = fresh(), body = fresh(), done = fresh();
        mark(loop);
        branch("#110 LT #102", body, done);
        mark(body);
        line(mem("#110") + " = 0");
        set(110, "#110 + 1");
        jump(loop);
        mark(done);
        if (array) line(mem("#109") + " = [#108 - 1]");
        push("#109");
    }

    private void body(String s) {
        if (s.isBlank() || s.equals(".code")) return;
        if (s.startsWith("(") && s.endsWith(")")) {
            line(s);
            if (s.endsWith("-end)")) line("");
            return;
        }
        if (s.endsWith(":")) {
            String name = s.substring(0, s.length() - 1);
            if (name.startsWith("_")) args = parameters.getOrDefault(name, 0);
            mark(label(name));
            return;
        }
        String[] p = s.split(",\\s*");
        String op = p[0];
        switch (op) {
            case "LOCAL" -> push("#101 + " + p[1]);
            case "AADD" -> {
                pop(108);
                pop(107);
                set(107, "#107 + #108");
                check("[#107 LE 0] OR [#107 GE 65536]", 7);
                push("#107");
            }
            case "FIELD" -> {
                pop(107);
                access(false);
                check("[#107 LT " + heap + "] OR [#107 GE " + stack + "]", 7);
                check("#107 NE [#113 + 1]", 7);
                push("#107");
            }
            case "ENTER" -> {
                comment("Function entry - check and reserve local variable cells");
                String n = p[1];
                check("[#100 + " + n + "] GT " + limit, 3);
                set(100, "#100 + " + n);
            }
            case "DUP" -> {
                pop(107);
                push("#107");
                push("#107");
            }
            case "LOAD", "LOAD4" -> {
                pop(107);
                access(false);
                set(107, mem("#107"));
                push("#107");
            }
            case "STORE", "STORE4" -> {
                pop(108);
                pop(107);
                access(true);
                line(mem("#107") + " = #108");
            }
            case "NEW" -> allocate(false);
            case "NEWA" -> allocate(true);
            case "MEMCPY" -> {
                comment("Copy constant array template to allocated storage");
                pop(109);
                pop(110);
                pop(111);
                int loop = fresh(), body = fresh(), done = fresh();
                mark(loop);
                branch("#109 GT 0", body, done);
                mark(body);
                set(107, "#110");
                access(false);
                set(108, mem("#107"));
                set(107, "#111");
                access(true);
                line(mem("#107") + " = #108");
                set(110, "#110 + 1");
                set(111, "#111 + 1");
                set(109, "#109 - 1");
                jump(loop);
                mark(done);
            }
            case "BOUNDS" -> {
                comment("Validate array allocation header and index");
                pop(108);
                pop(107);
                access(false);
                check("[#107 LT " + heap + "] OR [#107 GE " + stack + "]", 7);
                // access() leaves the containing allocation header in #113.
                check("#107 NE [#113 + 1]", 7);
                set(109, mem("#107"));
                check("[#109 LT 0] OR [[#109 + 1] GT #114]", 7);
                check("[#108 LT 0] OR [#108 GE #109]", 9);
                push("#107");
                push("#108");
            }
            case "I2D" -> {
            }
            case "SIGNED" -> {
                pop(107);
                wrap();
                push("#107");
            }
            case "UNSIGNED" -> {
                pop(107);
                set(107, "#107 MOD 65536");
                set(107, "#107 + [#107 LT 0] * 65536");
                push("#107");
            }
            case "D2I" -> {
                pop(107);
                finite();
                trunc();
                wrap();
                push("#107");
            }
            case "INEG", "DNEG", "IINV" -> {
                pop(107);
                set(107, op.equals("IINV") ? "65535 - [#107 + [#107 LT 0] * 65536]" : "0 - #107");
                if (!op.equals("DNEG")) wrap();
                else finite();
                push("#107");
            }
            default -> binary(op);
        }
    }


    private String reg(int register) { return "#" + register; }
    private String parameter(int index) { return reg(current.parameter(index)); }

    private void invoke(FanucRoutine routine, String... values) {
        int back = fresh();
        invokeWithContinuation(routine, back, values);
        mark(back);
    }

    private void invokeWithContinuation(FanucRoutine routine, int back, String... values) {
        if (values.length > 3) throw new IllegalArgumentException("Too many runtime parameters");
        if (current != null) throw new IllegalStateException("Nested instruction call: " + routine);
        comment("Call " + routine.routineName());
        for (int i = 0; i < values.length; i++) set(routine.parameter(i), values[i]);
        set(routine.returnRegister(), Integer.toString(back));
        jump(label(routine.routineName()));
    }

    /** Call sites contain parameters and a continuation, never the operation body. */
    private void instruction(String text) {
        if (text.isBlank() || text.equals(".code")) return;
        if (text.startsWith("(") || text.endsWith(":")) { body(text); return; }
        String[] parts = text.split(",\\s*");
        String op = parts[0];
        switch (op) {
            case "JUMP" -> jump(label(parts[1]));
            case "I2D" -> { }
            case "PUSHI", "PUSHD" -> invoke(FanucRoutine.PUSH, parts[1]);
            case "PUSHR" -> invoke(FanucRoutine.PUSH, Integer.toString(data.get(parts[1])));
            case "CALL" -> {
                int back = fresh();
                call(parts[1], Integer.parseInt(parts[2]), back);
                mark(back);
            }
            case "RET" -> invoke(FanucRoutine.RET, parts[1].equals("0") ? "0" : "1", Integer.toString(args));
            case "JUMPF", "JUMPT" -> invoke(FanucRoutine.valueOf(op), Integer.toString(label(parts[1])));
            case "LOCAL", "ENTER" -> invoke(FanucRoutine.valueOf(op), parts[1]);
            case "LOAD4", "LOAD" -> invoke(FanucRoutine.LOAD);
            case "STORE4", "STORE" -> invoke(FanucRoutine.STORE);
            case "POP" -> { if (!parts[1].equals("0")) invoke(FanucRoutine.POP); }
            default -> invoke(FanucRoutine.valueOf(op));
        }
    }

    private void library() {
        section("Runtime functions");
        for (var routine : FanucRoutine.values()) {
            current = routine;
            comment(routine.routineName() + "-start");
            mark(label(routine.routineName()));
            routineBody(routine);
            if (routine != FanucRoutine.CALL && routine != FanucRoutine.RET
                    && routine != FanucRoutine.JUMPF && routine != FanucRoutine.JUMPT)
                line("GOTO " + reg(routine.returnRegister()));
            comment(routine.routineName() + "-end");
        }
        current = null;
    }

    private void routineBody(FanucRoutine routine) {
        switch (routine) {
            case PUSH -> push(parameter(0));
            case POP -> pop(routine.parameter(0));
            case CALL -> {
                set(107, parameter(0));
                push(reg(routine.returnRegister()));
                push("#101");
                set(101, "#100");
                line("GOTO #107");
            }
            case RET -> {
                set(110, parameter(0));
                set(109, parameter(1));
                int take = fresh(), ready = fresh();
                branch("#110 NE 0", take, ready);
                mark(take); pop(107); mark(ready);
                set(108, mem("#101 - 2"));
                set(113, mem("#101 - 1"));
                set(100, "#101 - 2 - #109");
                set(101, "#113");
                int put = fresh(), done = fresh();
                branch("#110 NE 0", put, done);
                mark(put); push("#107"); mark(done);
                line("GOTO #108");
            }
            case JUMPF, JUMPT -> {
                pop(107);
                set(116, reg(routine.returnRegister()) + " + [#107 " + (routine == FanucRoutine.JUMPF ? "EQ" : "NE")
                        + " 0] * [" + parameter(0) + " - " + reg(routine.returnRegister()) + "]");
                line("GOTO #116");
            }
            case LOCAL, ENTER -> body(routine.name() + ", " + parameter(0));
            default -> body(routine.name());
        }
    }

    private void helpers() {
        section("Runtime helpers");
        emittingHelper = true;
        for (var helper : Helper.values()) {
            String name = "rt_helper_" + helper.name().toLowerCase(Locale.ROOT);
            comment(name + "-start");
            mark(label("helper_" + helper.name()));
            switch (helper) {
                case CHECK -> {
                    int fail = fresh(), ok = fresh();
                    branch("#116 NE 0", fail, ok);
                    mark(fail); set(103, "#118"); jump(end); mark(ok);
                }
                case PUSH -> {
                    inlineCheck("#100 GE " + limit, 3);
                    line(mem("#100") + " = #106");
                    set(100, "#100 + 1");
                }
                case POP -> {
                    inlineCheck("#100 LE " + stack, 4);
                    set(100, "#100 - 1");
                    set(106, mem("#100"));
                }
                case FINITE -> inlineCheck("[#107 NE #107] OR [#107 GT 3.4028234663852886E38] OR [#107 LT -3.4028234663852886E38]", 2);
                case ACCESS -> accessHelper();
            }
            line("GOTO #117");
            comment(name + "-end");
        }
        emittingHelper = false;
    }

    private void accessHelper() {
        inlineCheck("[#107 LE 0] OR [#107 GE 65536]", 7);
        int valid = fresh(), scan = fresh(), rom = fresh(), advance = fresh(), bad = fresh();
        branch("[#107 GE " + stack + "] AND [#107 LT #100]", valid, scan);
        mark(scan);
        branch("#107 LT " + heap, rom, advance);
        mark(rom);
        int readonly = fresh();
        branch("#106 NE 0", readonly, valid);
        mark(readonly); set(103, "8"); jump(end);
        mark(advance);
        set(113, Integer.toString(heap));
        int loop = fresh(), header = fresh(), step = fresh();
        mark(loop); branch("#113 LT #102", header, bad);
        mark(header); set(114, mem("#113"));
        branch("[#107 GT #113] AND [#107 LE [#113 + #114]]", valid, step);
        mark(step); set(113, "#113 + #114 + 1"); jump(loop);
        mark(bad); set(103, "7"); jump(end);
        mark(valid);
    }

    private void binary(String op) {
        pop(108);
        pop(107);
        boolean integer = op.startsWith("I");
        String kind = op.substring(1);
        switch (kind) {
            case "ADD" -> set(107, "#107 + #108");
            case "SUB" -> set(107, "#107 - #108");
            case "MUL" -> {
                if (integer) {
                    // Base-256 limbs keep every product and sum exactly representable in Float32.
                    set(109, "#107 + [#107 LT 0] * 65536");
                    set(110, "#108 + [#108 LT 0] * 65536");
                    set(111, "#109 MOD 256");
                    set(112, "#110 MOD 256");
                    set(107, "#111 * #112 + [[[[#109 - #111] / 256] * #112 + [[#110 - #112] / 256] * #111] MOD 256] * 256");
                } else set(107, "#107 * #108");
            }
            case "DIV", "MOD" -> {
                check("#108 EQ 0", 1);
                set(107, "#107 " + (kind.equals("MOD") ? "MOD" : "/") + " #108");
                if (integer && kind.equals("DIV")) trunc();
            }
            case "AND", "OR", "XOR" -> {
                set(107, "#107 + [#107 LT 0] * 65536");
                set(108, "#108 + [#108 LT 0] * 65536");
                set(107, "#107 " + kind + " #108");
            }
            case "SHL", "SHR" -> {
                set(108, "#108 MOD 32");
                set(108, "#108 + [#108 LT 0] * 32");
                int loop = fresh(), body = fresh(), done = fresh();
                mark(loop);
                branch("#108 GT 0", body, done);
                mark(body);
                if (kind.equals("SHL")) {
                    set(107, "#107 * 2");
                    wrap();
                } else {
                    set(109, "#107 MOD 2");
                    set(107, "[#107 - #109] / 2 - [#109 LT 0]");
                }
                set(108, "#108 - 1");
                jump(loop);
                mark(done);
            }
            case "EQ", "NE", "LT", "LE", "GT", "GE" -> {
                set(107, "#107 " + kind + " #108");
                push("#107");
                return;
            }
            default -> throw new IllegalArgumentException("Unsupported FANUC lowering: " + op);
        }
        if (integer) wrap();
        else finite();
        push("#107");
    }
}
