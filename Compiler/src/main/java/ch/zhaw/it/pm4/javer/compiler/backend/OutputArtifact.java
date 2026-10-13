package ch.zhaw.it.pm4.javer.compiler.backend;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** An immutable output artifact independent of its destination file. */
public final class OutputArtifact {
    private final byte[] contents;

    public OutputArtifact(byte[] contents) {
        this.contents = Objects.requireNonNull(contents).clone();
    }

    public static OutputArtifact text(String text) {
        return new OutputArtifact(text.getBytes(StandardCharsets.UTF_8));
    }

    public byte[] contents() {
        return contents.clone();
    }
}
