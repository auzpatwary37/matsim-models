package com.citymodeler.matsim.models.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

class MatsimModelExceptionTest {

    @Test
    void messageOnlyConstructorHasNoContext() {
        MatsimModelException ex = new MatsimModelException("test message");
        assertEquals("test message", ex.getMessage());
        assertNull(ex.getFilePath());
        assertEquals(-1, ex.getLineNumber());
        assertNull(ex.getElementName());
    }

    @Test
    void messageAndCauseConstructorHasNoContext() {
        RuntimeException cause = new RuntimeException("root cause");
        MatsimModelException ex = new MatsimModelException("test message", cause);
        assertEquals("test message", ex.getMessage());
        assertSame(cause, ex.getCause());
        assertNull(ex.getFilePath());
        assertEquals(-1, ex.getLineNumber());
        assertNull(ex.getElementName());
    }

    @Test
    void formatMessageWithFilePath() {
        Path path = Paths.get("/data/population.xml");
        MatsimModelException ex = new MatsimModelException("parse error", path, 42, "person");
        assertEquals("parse error (file=/data/population.xml, line=42, element=person)", ex.getMessage());
        assertSame(path, ex.getFilePath());
        assertEquals(42, ex.getLineNumber());
        assertEquals("person", ex.getElementName());
    }

    @Test
    void formatMessageWithLineNumberOnly() {
        MatsimModelException ex = new MatsimModelException("parse error", null, 42, "person");
        assertEquals("parse error (line=42, element=person)", ex.getMessage());
    }

    @Test
    void formatMessageWithElementNameOnly() {
        MatsimModelException ex = new MatsimModelException("parse error", null, -1, "person");
        assertEquals("parse error (element=person)", ex.getMessage());
    }

    @Test
    void formatMessageWithNoContext() {
        MatsimModelException ex = new MatsimModelException("simple error");
        assertEquals("simple error", ex.getMessage());
    }

    @Test
    void formatMessageWithBlankElementNameTreatedAsNull() {
        MatsimModelException ex = new MatsimModelException("error", null, 10, "   ");
        assertEquals("error (line=10)", ex.getMessage());
    }
}
