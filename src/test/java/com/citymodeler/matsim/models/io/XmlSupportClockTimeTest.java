package com.citymodeler.matsim.models.io;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class XmlSupportClockTimeTest {

    @Test
    void plainSecondsRoundTripFormAccepted() {
        assertEquals(3600.0, XmlSupport.parseClockTimeOrSeconds("3600.0"));
        assertEquals(0.0, XmlSupport.parseClockTimeOrSeconds("0"));
        // Negative plain seconds are used by stop offsets and are written by
        // the library's own Double.toString serializer.
        assertEquals(-5.0, XmlSupport.parseClockTimeOrSeconds("-5.0"));
        // Double.toString emits scientific notation for large magnitudes; a
        // round-trip of such a value must keep working.
        assertEquals(9.0E9, XmlSupport.parseClockTimeOrSeconds("9.0E9"));
        // Surrounding whitespace is tolerated.
        assertEquals(60.0, XmlSupport.parseClockTimeOrSeconds(" 60.0 "));
    }

    @ParameterizedTest
    @ValueSource(strings = {"NaN", "Infinity", "-Infinity", "abc"})
    void plainSecondsRejectsNonNumeric(String value) {
        assertThrows(MatsimModelException.class, () -> XmlSupport.parseClockTimeOrSeconds(value));
    }

    @Test
    void canonicalMatSimClockFormsAccepted() {
        assertEquals(17 * 3600 + 31 * 60, XmlSupport.parseClockTimeOrSeconds("17:31:00"));
        assertEquals(30.0, XmlSupport.parseClockTimeOrSeconds("00:00:30"));
        assertEquals(60.0, XmlSupport.parseClockTimeOrSeconds("00:01:00"));
        assertEquals(25 * 3600 + 5 * 60 + 7.5, XmlSupport.parseClockTimeOrSeconds("25:05:07.5"));
        assertEquals(26 * 3600.0, XmlSupport.parseClockTimeOrSeconds("26:00:00"));
        assertEquals(5 * 60.0, XmlSupport.parseClockTimeOrSeconds("0:05"));
        // Three-digit hours (multi-day horizons) must stay valid.
        assertEquals(123 * 3600 + 59 * 60 + 59, XmlSupport.parseClockTimeOrSeconds("123:59:59"));
        // Fractional part with full precision.
        assertEquals(0.25, XmlSupport.parseClockTimeOrSeconds("0:00:00.25"));
    }

    @Test
    void minutesAndSecondsAtBoundaryAccepted() {
        assertEquals(59 * 60 + 59, XmlSupport.parseClockTimeOrSeconds("0:59:59"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "12:75:00",   // minutes out of range
            "12:01:99",   // seconds out of range
            "12:60",      // minutes out of range, MM:SS form
            "12:01:30:05",// extra trailing field
            "12:01:",     // empty seconds
            "12:",        // truncated
            ":01",        // missing hours
            "12:01:30.",  // empty fraction
            "-12:01:00",  // signed hours
            "12:-1:00",   // signed minutes
            "12 : 01",    // internal whitespace
            "12:01.5",    // fraction belongs to seconds, not minutes
            "0:00.25",    // fraction without the seconds field
            "12:1",       // single-digit minutes
            "12:01:1",    // single-digit seconds
            "12:01:30:45:56", // two extra fields
            "0x1:02:03",  // non-decimal hours
    })
    void clockTimeRejectsMalformedGrammars(String value) {
        MatsimModelException ex = assertThrows(
                MatsimModelException.class, () -> XmlSupport.parseClockTimeOrSeconds(value));
        assertTrue(ex.getMessage().contains(value),
                "error message should echo the offending value: " + ex.getMessage());
    }

    @Test
    void errorMentionsExpectedGrammar() {
        MatsimModelException ex = assertThrows(
                MatsimModelException.class, () -> XmlSupport.parseClockTimeOrSeconds("12:75:00"));
        assertTrue(ex.getMessage().contains("HH:MM:SS"), ex.getMessage());
    }
}
