package com.citymodeler.matsim.models.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

class MatsimParseExceptionTest {

    @Test
    void messageOnlyConstructor() {
        MatsimParseException ex = new MatsimParseException("parse failed");
        assertEquals("parse failed", ex.getMessage());
        assertEquals(-1, ex.getLineNumber());
        assertNull(ex.getFilePath());
    }

    @Test
    void messageAndCauseConstructor() {
        RuntimeException cause = new RuntimeException("root cause");
        MatsimParseException ex = new MatsimParseException("parse failed", cause);
        assertEquals("parse failed", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void extendsMatsimModelException() {
        MatsimParseException ex = new MatsimParseException("test");
        assertInstanceOf(MatsimModelException.class, ex);
    }

    @Test
    void thrownForMalformedXml() {
        assertThrows(MatsimParseException.class, () -> XmlSupport.parse("<invalid"));
    }

    @Test
    void thrownForNonExistentFile() {
        assertThrows(MatsimParseException.class, () -> XmlSupport.parse(java.nio.file.Paths.get("/non/existent/file.xml")));
    }
}
