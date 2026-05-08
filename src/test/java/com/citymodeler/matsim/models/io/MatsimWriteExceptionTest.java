package com.citymodeler.matsim.models.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

class MatsimWriteExceptionTest {

    @Test
    void messageOnlyConstructor() {
        MatsimWriteException ex = new MatsimWriteException("write failed");
        assertEquals("write failed", ex.getMessage());
        assertEquals(-1, ex.getLineNumber());
        assertNull(ex.getFilePath());
    }

    @Test
    void messageAndCauseConstructor() {
        RuntimeException cause = new RuntimeException("root cause");
        MatsimWriteException ex = new MatsimWriteException("write failed", cause);
        assertEquals("write failed", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void extendsMatsimModelException() {
        MatsimWriteException ex = new MatsimWriteException("test");
        assertInstanceOf(MatsimModelException.class, ex);
    }

    @Test
    void thrownForUnwritableDirectory() {
        Document doc = XmlSupport.newDocument();
        Path unwritable = Paths.get("/non/writable/directory/file.xml");
        assertThrows(MatsimWriteException.class, () -> XmlSupport.write(doc, unwritable));
    }
}
