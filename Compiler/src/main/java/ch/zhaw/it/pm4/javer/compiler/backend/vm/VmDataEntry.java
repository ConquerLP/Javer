package ch.zhaw.it.pm4.javer.compiler.backend.vm;

import java.util.List;

/** Frozen static data encoded for the textual VM bytecode format. */
record VmDataEntry(String label, int elementBytes, List<String> values) {
    VmDataEntry { values = List.copyOf(values); }
    @Override public String toString() { return label + " " + elementBytes + " " + String.join(",", values); }
}
