package ch.zhaw.it.pm4.javer.compiler.misc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class SourceLocationTest {

    @Test
    void recordExposesCoordinatesAndFormatsToString() {
        SourceLocation location = new SourceLocation(2, 8, 3);

        assertEquals(2, location.startColumn());
        assertEquals(8, location.endColumn());
        assertEquals(3, location.lineNumber());
        assertEquals("[3 : 2 : 8]", location.toString());
    }

    /*
    @Test
    @DisplayName("Should create SourceLocation with valid values")
    void testValidSourceLocation() {
        SourceLocation pos = new SourceLocation(1, 5, 1);
        assertEquals(1, pos.startColumn());
        assertEquals(5, pos.endColumn());
        assertEquals(1, pos.lineNumber());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when startColumn is less than 1")
    void testStartColumnLessThanOne() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            new SourceLocation(0, 5, 1);
        });
        assertTrue(exception.getMessage().contains("Start column must be at least 1"));
        assertTrue(exception.getMessage().contains("0"));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when endColumn is less than startColumn")
    void testEndColumnLessThanStart() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            new SourceLocation(5, 3, 1);
        });
        assertTrue(exception.getMessage().contains("End column cannot be less than start column"));
        assertTrue(exception.getMessage().contains("3 < 5"));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when lineNumber is less than 1")
    void testLineNumberLessThanOne() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            new SourceLocation(1, 5, 0);
        });
        assertTrue(exception.getMessage().contains("Line number must be at least 1"));
        assertTrue(exception.getMessage().contains("0"));
    }

    @Test
    @DisplayName("Should allow startColumn and endColumn to be equal")
    void testStartAndEndEqual() {
        assertDoesNotThrow(() -> {
            new SourceLocation(5, 5, 1);
        });
    }

    @Test
    @DisplayName("Should allow minimum valid values")
    void testMinimumValidValues() {
        assertDoesNotThrow(() -> {
            new SourceLocation(1, 1, 1);
        });
    }

    @Test
    @DisplayName("Should have correct toString output")
    void testToString() {
        SourceLocation pos = new SourceLocation(1, 10, 2);
        assertEquals("[1 : 10 : 2]", pos.toString());
    }
    */
}

