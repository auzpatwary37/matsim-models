package com.citymodeler.matsim.models.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;

/**
 * Streaming removal of a leading legacy MATSIM {@code <!DOCTYPE ...>}
 * declaration. A bounded prolog prefix (at most {@link #MAX_PREFIX_BYTES})
 * is scanned to locate the declaration; only that prefix is buffered. The
 * resulting stream is the kept prefix followed by the original stream, so
 * arbitrarily large document bodies pass through without a full-file copy.
 *
 * <p>The referenced DTD is never fetched — the declaration is excised before
 * the hardened parser sees it. Internal DTD subsets and malformed
 * declarations are refused with a loud {@link MatsimParseException}.</p>
 *
 * <p>If the verdict cannot be reached within the prefix budget (a prolog
 * longer than the budget, which does not occur in real MATSIM exports), no
 * stripping is attempted and the hardened parser decides.</p>
 */
final class LegacyDoctypeStripper {

    static final int MAX_PREFIX_BYTES = 64 * 1024;
    static final int READ_CHUNK = 4096;

    private LegacyDoctypeStripper() {
    }

    /**
     * Wraps the given stream so that a leading legacy DOCTYPE declaration is
     * not visible to the reader. Must be called before the stream is read.
     */
    static InputStream open(InputStream in) throws IOException {
        ByteArrayOutputStream prefix = new ByteArrayOutputStream();
        byte[] chunk = new byte[READ_CHUNK];
        int total;
        boolean eof = false;
        while (true) {
            total = in.read(chunk);
            if (total == -1) {
                eof = true;
                break;
            }
            prefix.write(chunk, 0, total);
            Verdict verdict = scanPrefix(prefix.toByteArray());
            if (verdict.decided) {
                return assemble(prefix.toByteArray(), verdict, in);
            }
            if (prefix.size() >= MAX_PREFIX_BYTES) {
                break;
            }
        }
        if (eof) {
            // Whole file was scanned: a declaration left dangling at EOF is
            // malformed and is refused loudly, while a plain prolog end is
            // left to the hardened parser.
            Verdict verdict = scanPrefix(prefix.toByteArray());
            if (verdict.kind == Kind.INCOMPLETE_DECLARATION) {
                throw verdict.failure;
            }
        }
        // Budget ran out without a verdict: leave the document untouched.
        return new SequenceInputStream(new ByteArrayInputStream(prefix.toByteArray()), in);
    }

    private static InputStream assemble(byte[] prefix, Verdict verdict, InputStream rest) {
        if (verdict.kind == Kind.REFUSED) {
            throw verdict.failure;
        }
        byte[] kept;
        if (verdict.kind == Kind.REMOVED) {
            kept = new byte[prefix.length - (verdict.spanEnd - verdict.spanStart)];
            System.arraycopy(prefix, 0, kept, 0, verdict.spanStart);
            System.arraycopy(prefix, verdict.spanEnd, kept, verdict.spanStart,
                    prefix.length - verdict.spanEnd);
        } else {
            kept = prefix;
        }
        return new SequenceInputStream(new ByteArrayInputStream(kept), rest);
    }

    private enum Kind {
        NONE,
        REMOVED,
        REFUSED,
        INCOMPLETE_DECLARATION
    }

    private static final class Verdict {
        final Kind kind;
        final boolean decided;
        final int spanStart;
        final int spanEnd;
        final MatsimParseException failure;

        private Verdict(Kind kind, boolean decided, int spanStart, int spanEnd, MatsimParseException failure) {
            this.kind = kind;
            this.decided = decided;
            this.spanStart = spanStart;
            this.spanEnd = spanEnd;
            this.failure = failure;
        }

        static Verdict none() {
            return new Verdict(Kind.NONE, false, -1, -1, null);
        }

        static Verdict noDoctype() {
            return new Verdict(Kind.NONE, true, -1, -1, null);
        }

        static Verdict removed(int start, int end) {
            return new Verdict(Kind.REMOVED, true, start, end, null);
        }

        static Verdict refused(MatsimParseException failure) {
            return new Verdict(Kind.REFUSED, true, -1, -1, failure);
        }

        static Verdict incompleteDeclaration(MatsimParseException failure) {
            return new Verdict(Kind.INCOMPLETE_DECLARATION, false, -1, -1, failure);
        }
    }

    /**
     * Scans a prolog prefix for a complete leading legacy DOCTYPE
     * declaration. Returns REMOVED with the span offsets when a complete
     * declaration is present, NO_DOCTYPE when a non-prolog token (typically
     * the root element) appears first, REFUSED for declarations this library
     * will not rewrite, INCOMPLETE_DECLARATION when the prefix ends inside a
     * declaration, or NONE when the prefix ends mid-prolog token.
     */
    static Verdict scanPrefix(byte[] prefix) {
        int i = 0;
        int n = prefix.length;
        if (n >= 3 && (prefix[0] & 0xFF) == 0xEF && (prefix[1] & 0xFF) == 0xBB && (prefix[2] & 0xFF) == 0xBF) {
            i = 3;
        }
        while (i < n) {
            int b = prefix[i] & 0xFF;
            if (isXmlWhitespace(b)) {
                i++;
                continue;
            }
            if (b != '<') {
                return Verdict.noDoctype();
            }
            if (startsWith(prefix, i, "<!--")) {
                int close = indexOfAscii(prefix, i + 4, "-->");
                if (close < 0) {
                    return Verdict.none();
                }
                i = close + 3;
                continue;
            }
            if (startsWith(prefix, i, "<?")) {
                int close = indexOfAscii(prefix, i + 2, "?>");
                if (close < 0) {
                    return Verdict.none();
                }
                i = close + 2;
                continue;
            }
            if (startsWithCaseInsensitive(prefix, i, "<!DOCTYPE")) {
                return scanDeclarationSpan(prefix, i);
            }
            if (couldBePrologTokenPrefix(prefix, i)) {
                // A prolog token may start here but the prefix ends before the
                // token is complete (e.g. the DOCTYPE keyword is split across
                // read chunks). Need more bytes before deciding.
                return Verdict.none();
            }
            return Verdict.noDoctype();
        }
        return Verdict.none();
    }

    /**
     * True when the bytes from {@code at} to the end of the prefix form a
     * proper prefix of one of the prolog token starts ({@code <!--},
     * {@code <?}, {@code <!DOCTYPE}), i.e. more bytes are needed before the
     * token can be classified.
     */
    private static boolean couldBePrologTokenPrefix(byte[] data, int at) {
        int rem = data.length - at;
        return isProperPrefixOf(data, at, rem, "<!--", false)
                || isProperPrefixOf(data, at, rem, "<?", false)
                || isProperPrefixOf(data, at, rem, "<!DOCTYPE", true);
    }

    private static boolean isProperPrefixOf(byte[] data, int at, int rem, String token, boolean caseInsensitive) {
        if (rem <= 0 || rem >= token.length()) {
            return false;
        }
        for (int k = 0; k < rem; k++) {
            if (caseInsensitive) {
                if (Character.toUpperCase(data[at + k] & 0xFF) != Character.toUpperCase(token.charAt(k))) {
                    return false;
                }
            } else if ((data[at + k] & 0xFF) != (byte) token.charAt(k)) {
                return false;
            }
        }
        return true;
    }

    private static Verdict scanDeclarationSpan(byte[] prefix, int start) {
        int i = start + "<!DOCTYPE".length();
        int n = prefix.length;
        while (i < n && isXmlWhitespace(prefix[i] & 0xFF)) {
            i++;
        }
        if (i >= n) {
            return Verdict.incompleteDeclaration(new MatsimParseException(
                    "Malformed legacy DOCTYPE declaration: missing closing '>'."));
        }
        if (!isNameStart(prefix[i] & 0xFF)) {
            return Verdict.refused(new MatsimParseException(
                    "Malformed legacy DOCTYPE declaration: missing document element name."));
        }
        i++;
        while (i < n && isNameChar(prefix[i] & 0xFF)) {
            i++;
        }
        while (i < n) {
            int b = prefix[i] & 0xFF;
            if (isXmlWhitespace(b)) {
                i++;
            } else if (b == '>') {
                return Verdict.removed(start, i + 1);
            } else if (b == '"' || b == '\'') {
                int close = i + 1;
                while (close < n && (prefix[close] & 0xFF) != b) {
                    close++;
                }
                if (close >= n) {
                    return Verdict.incompleteDeclaration(new MatsimParseException(
                            "Malformed legacy DOCTYPE declaration: unterminated quoted string."));
                }
                i = close + 1;
            } else if (b == '[') {
                return Verdict.refused(new MatsimParseException(
                        "Unsupported legacy DOCTYPE declaration: internal DTD subset found. "
                                + "Remove the DTD block from the file; external DTDs are never loaded."));
            } else if (b == ']') {
                return Verdict.refused(new MatsimParseException(
                        "Malformed legacy DOCTYPE declaration: unexpected ']' outside an internal subset."));
            } else if (isNameStart(b)) {
                i++;
                while (i < n && isNameChar(prefix[i] & 0xFF)) {
                    i++;
                }
            } else {
                return Verdict.refused(new MatsimParseException(
                        "Malformed legacy DOCTYPE declaration: unexpected character."));
            }
        }
        return Verdict.incompleteDeclaration(new MatsimParseException(
                "Malformed legacy DOCTYPE declaration: missing closing '>'."));
    }

    private static boolean isXmlWhitespace(int b) {
        return b == ' ' || b == '\t' || b == '\r' || b == '\n';
    }

    private static boolean isNameStart(int b) {
        return (b >= 'A' && b <= 'Z') || (b >= 'a' && b <= 'z') || b == '_';
    }

    private static boolean isNameChar(int b) {
        return isNameStart(b) || (b >= '0' && b <= '9') || b == '.' || b == '-' || b == ':';
    }

    private static boolean startsWith(byte[] data, int at, String token) {
        if (at + token.length() > data.length) {
            return false;
        }
        for (int k = 0; k < token.length(); k++) {
            if ((data[at + k] & 0xFF) != (byte) token.charAt(k)) {
                return false;
            }
        }
        return true;
    }

    private static boolean startsWithCaseInsensitive(byte[] data, int at, String asciiToken) {
        if (at + asciiToken.length() > data.length) {
            return false;
        }
        for (int k = 0; k < asciiToken.length(); k++) {
            if (Character.toUpperCase(data[at + k] & 0xFF) != asciiToken.charAt(k)) {
                return false;
            }
        }
        return true;
    }

    private static int indexOfAscii(byte[] data, int from, String token) {
        for (int i = from; i <= data.length - token.length(); i++) {
            boolean matched = true;
            for (int k = 0; k < token.length(); k++) {
                if ((data[i + k] & 0xFF) != (byte) token.charAt(k)) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                return i;
            }
        }
        return -1;
    }
}
