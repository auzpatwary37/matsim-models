package com.citymodeler.matsim.models.io;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * Bounded-prefix guarantees for the legacy DOCTYPE stripper: only a bounded
 * prolog prefix is ever buffered; the document body passes through the
 * original stream without a full-file copy.
 */
class LegacyDoctypeStripperTest {

    private static final String PREFIX = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";
    private static final String DOCTYPE_LINE =
            "<!DOCTYPE network SYSTEM \"http://www.matsim.org/files/dtd/network_v2.dtd\">\n";
    private static final String BARE_DOCTYPE = "<!DOCTYPE network SYSTEM \"x.dtd\">";
    private static final String BODY = """
            <network>
              <nodes><node id="n1" x="0" y="0"/><node id="n2" x="1" y="1"/></nodes>
              <links><link id="l1" from="n1" to="n2" length="1" freespeed="1" capacity="1" permlanes="1"/></links>
            </network>
            """;

    @Test
    void removesLeadingLegacyDoctype() throws IOException {
        InputStream stream = LegacyDoctypeStripper.open(
                new ByteArrayInputStream((PREFIX + DOCTYPE_LINE + BODY).getBytes(StandardCharsets.UTF_8)));
        // Only the declaration is excised; the DOCTYPE line's trailing newline is kept.
        assertEquals(PREFIX + "\n" + BODY, drain(stream));
    }

    @Test
    void passesThroughStreamWithoutDoctypeUnchanged() throws IOException {
        byte[] raw = (PREFIX + BODY).getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(raw, drainBytes(LegacyDoctypeStripper.open(new ByteArrayInputStream(raw))));
    }

    @Test
    void openBuffersOnlyABoundedPrefix() throws IOException {
        byte[] bigBody = new byte[1_048_576];
        java.util.Arrays.fill(bigBody, (byte) 'x');
        CountingStream counter = counting(concat(PREFIX.getBytes(StandardCharsets.UTF_8),
                DOCTYPE_LINE.getBytes(StandardCharsets.UTF_8), bigBody));

        LegacyDoctypeStripper.open(counter);

        assertTrue(counter.bytesRead <= LegacyDoctypeStripper.MAX_PREFIX_BYTES + 4096,
                "open must buffer at most the bounded prolog prefix, but read " + counter.bytesRead + " bytes");
    }

    @Test
    void readsUnderlyingLazilyAfterPrologDecision() throws IOException {
        byte[] bigBody = new byte[1_048_576];
        java.util.Arrays.fill(bigBody, (byte) 'x');
        CountingStream counter = counting(concat(PREFIX.getBytes(StandardCharsets.UTF_8),
                DOCTYPE_LINE.getBytes(StandardCharsets.UTF_8), bigBody));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        InputStream stream = LegacyDoctypeStripper.open(counter);
        long afterOpen = counter.bytesRead;
        for (int i = 0; i < 16; i++) {
            out.write(stream.read());
        }

        assertEquals(afterOpen, counter.bytesRead,
                "body bytes must stream lazily; 16 output bytes came from the buffered prefix");
    }

    @Test
    void preservesLargeBodyContentByteForByte() throws IOException {
        byte[] bigBody = new byte[4_194_304];
        new java.util.Random(42).nextBytes(bigBody);
        bigBody[0] = '<';
        bigBody[bigBody.length - 1] = '>';

        byte[] drained = drainBytes(LegacyDoctypeStripper.open(new ByteArrayInputStream(
                concat(BARE_DOCTYPE.getBytes(StandardCharsets.UTF_8), bigBody))));
        assertArrayEquals(bigBody, drained, "body must pass through byte-for-byte");
    }

    @Test
    void chunkedReadsMatchSingleByteReads() throws IOException {
        byte[] raw = (PREFIX + DOCTYPE_LINE + BODY).getBytes(StandardCharsets.UTF_8);
        byte[] single = drain(LegacyDoctypeStripper.open(new ByteArrayInputStream(raw)))
                .getBytes(StandardCharsets.UTF_8);
        byte[] chunked = drainBytes(LegacyDoctypeStripper.open(new ByteArrayInputStream(raw)), 3);
        assertArrayEquals(single, chunked);
    }

    @Test
    void rejectsInternalSubsetFromStream() {
        byte[] raw = ("<!DOCTYPE network [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>" + BODY)
                .getBytes(StandardCharsets.UTF_8);
        MatsimParseException exception = assertThrows(MatsimParseException.class, () -> {
            InputStream stream = LegacyDoctypeStripper.open(new ByteArrayInputStream(raw));
            drain(stream);
        });
        assertTrue(exception.getMessage().toLowerCase(java.util.Locale.ROOT).contains("dtd"),
                "rejection must name the DTD problem, was: " + exception.getMessage());
    }

    @Test
    void rejectsUnterminatedDeclarationAtEndOfStream() {
        byte[] raw = ("<!DOCTYPE network SYSTEM \"unterminated").getBytes(StandardCharsets.UTF_8);
        MatsimParseException exception = assertThrows(MatsimParseException.class, () -> {
            InputStream stream = LegacyDoctypeStripper.open(new ByteArrayInputStream(raw));
            drain(stream);
        });
        assertTrue(exception.getMessage().toLowerCase(java.util.Locale.ROOT).contains("doctype"),
                "rejection must mention the malformed DOCTYPE, was: " + exception.getMessage());
    }

    @Test
    void handlesPrologCommentAndProcessingInstruction() throws IOException {
        byte[] raw = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<!-- legacy -->\n<?pi x=\"y\"?>\n"
                + "<!doctype network system \"./dtd/v1.dtd\">\n"
                + BODY).getBytes(StandardCharsets.UTF_8);
        // Only the declaration is excised; its trailing newline is kept.
        String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!-- legacy -->\n<?pi x=\"y\"?>\n\n" + BODY;
        assertEquals(expected, drain(LegacyDoctypeStripper.open(new ByteArrayInputStream(raw))));
    }

    @Test
    void doctypeKeywordSplitAcrossChunkBoundaryIsStillRemoved() throws IOException {
        // The '<' of the DOCTYPE lands three bytes before the end of the first
        // read chunk, so the first read contains only "<!D" of the keyword.
        int doctypeOffset = LegacyDoctypeStripper.READ_CHUNK - 3;
        String pad = "\n".repeat(doctypeOffset);
        byte[] raw = (pad + BARE_DOCTYPE + BODY).getBytes(StandardCharsets.UTF_8);
        assertTrue(new String(raw, doctypeOffset, 3, StandardCharsets.UTF_8).equals("<!D"),
                "test must place a partial DOCTYPE keyword at the chunk boundary");

        String result = drain(LegacyDoctypeStripper.open(new ByteArrayInputStream(raw)));

        assertEquals(pad + BODY, result, "partial keyword at chunk boundary must not abort the scan");
    }

    @Test
    void loneOpeningBraceAtChunkBoundaryIsNotAMisread() throws IOException {
        // The root element's '<' is the very last byte of the first chunk; the
        // scanner must wait for the next byte instead of deciding either way.
        int braceOffset = LegacyDoctypeStripper.READ_CHUNK - 1;
        String pad = " ".repeat(braceOffset);
        byte[] raw = (pad + BODY).getBytes(StandardCharsets.UTF_8);

        byte[] result = drainBytes(LegacyDoctypeStripper.open(new ByteArrayInputStream(raw)));

        assertArrayEquals(raw, result, "a '<' at the chunk boundary must pass through unchanged");
    }

    @Test
    void doctypeAtEndOfStreamIsHandled() throws IOException {
        byte[] raw = BARE_DOCTYPE.getBytes(StandardCharsets.UTF_8);
        assertEquals("", drain(LegacyDoctypeStripper.open(new ByteArrayInputStream(raw))));
    }

    private static String drain(InputStream stream) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int b;
        while ((b = stream.read()) != -1) {
            out.write(b);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private static byte[] drainBytes(InputStream stream) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int r;
        while ((r = stream.read(buffer, 0, buffer.length)) != -1) {
            out.write(buffer, 0, r);
        }
        return out.toByteArray();
    }

    private static byte[] drainBytes(InputStream stream, int chunk) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[chunk];
        int r;
        while ((r = stream.read(buffer, 0, buffer.length)) != -1) {
            out.write(buffer, 0, r);
        }
        return out.toByteArray();
    }

    private static CountingStream counting(byte[] data) {
        return new CountingStream(data);
    }

    private static byte[] concat(byte[]... arrays) {
        int total = 0;
        for (byte[] array : arrays) {
            total += array.length;
        }
        byte[] out = new byte[total];
        int offset = 0;
        for (byte[] array : arrays) {
            System.arraycopy(array, 0, out, offset, array.length);
            offset += array.length;
        }
        return out;
    }

    private static final class CountingStream extends InputStream {
        private final InputStream delegate;
        private long bytesRead = 0;

        CountingStream(byte[] data) {
            this.delegate = new ByteArrayInputStream(data);
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b >= 0) {
                bytesRead++;
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int r = delegate.read(b, off, len);
            if (r > 0) {
                bytesRead += r;
            }
            return r;
        }
    }
}
