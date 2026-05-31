/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.util.serializer;

import org.exist.dom.QName;
import org.junit.Test;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.io.Writer;
import java.util.Properties;

import static org.junit.Assert.assertTrue;

/**
 * Microbenchmark for HTML serialization that exercises the writeChars/writeCharSeq
 * hot path. Builds a representative HTML document with paragraphs of plain text
 * (no special chars in the safe runs) and serializes it many times.
 *
 * Compares two configurations:
 *   - bulk writes via {@link Writer#write(char[], int, int)} (current code)
 *   - per-char writes via {@link Writer#write(int)} (the previous behaviour)
 *
 * The "per-char" baseline is simulated by wrapping the writer in one that
 * counts only charAt-based calls — this lets us prove the algorithmic
 * improvement without having to revert the patch.
 */
public class HtmlSerializerBenchmarkTest {

    private static final String LOREM =
            "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do " +
            "eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim " +
            "ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut " +
            "aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit " +
            "in voluptate velit esse cillum dolore eu fugiat nulla pariatur.";

    private static final int PARAGRAPH_COUNT = 80;
    private static final int ITERATIONS = 200;

    /**
     * Counts both bulk and per-char writes so we can verify the hot path is
     * actually using bulk operations.
     */
    private static final class CountingWriter extends Writer {
        long bulkWriteCalls;
        long bulkCharsWritten;
        long perCharWriteCalls;
        long stringWriteCalls;
        long stringCharsWritten;

        @Override
        public void write(int c) {
            perCharWriteCalls++;
        }

        @Override
        public void write(char[] cbuf, int off, int len) {
            bulkWriteCalls++;
            bulkCharsWritten += len;
        }

        @Override
        public void write(String str, int off, int len) {
            stringWriteCalls++;
            stringCharsWritten += len;
        }

        @Override
        public void flush() { /* no-op: metrics live in fields, nothing to flush */ }

        @Override
        public void close() { /* no-op: counting writer holds no resources */ }
    }

    /**
     * Forwards every write to the underlying writer one char at a time,
     * simulating a writer that has no efficient bulk path. Wrapping a
     * {@link java.io.StringWriter} in this is the closest we can come
     * to measuring the *previous* writeCharSeq behaviour without reverting.
     */
    private static final class PerCharWriter extends Writer {
        private final Writer delegate;
        PerCharWriter(final Writer delegate) { this.delegate = delegate; }
        @Override public void write(int c) throws IOException { delegate.write(c); }
        @Override public void write(char[] cbuf, int off, int len) throws IOException {
            for (int i = 0; i < len; i++) delegate.write(cbuf[off + i]);
        }
        @Override public void write(String str, int off, int len) throws IOException {
            for (int i = 0; i < len; i++) delegate.write(str.charAt(off + i));
        }
        @Override public void flush() throws IOException { delegate.flush(); }
        @Override public void close() throws IOException { delegate.close(); }
    }

    /** Discards bytes — simulates a network sink with no I/O cost. */
    private static final class NullOutputStream extends java.io.OutputStream {
        @Override public void write(int b) { /* no-op sink: byte intentionally discarded */ }
        @Override public void write(byte[] b, int off, int len) { /* no-op sink: bytes intentionally discarded */ }
    }

    private static java.io.OutputStreamWriter newProductionLikeWriter() {
        // Mirrors the typical HTTP-response chain: OutputStreamWriter(UTF-8) over
        // a stream sink. No BufferedWriter — eXist's serializer pipeline does its
        // own buffering at higher levels.
        return new java.io.OutputStreamWriter(new NullOutputStream(), java.nio.charset.StandardCharsets.UTF_8);
    }

    @Test
    public void rawTextFastPath() throws TransformerException, IOException {
        // Compare per-char writes between an empty <script> and a <script> with
        // many '<' chars in its body. Without the raw-text fast path each '<'
        // breaks the bulk run and emits a per-char write; with the fast path
        // both runs should have identical per-char counts (the difference is
        // all in bulk string writes).
        final String script =
                "function compare(a, b) { return a < b; }\n" +
                "for (let i = 0; i < items.length; i++) { if (items[i] < threshold) found = true; }\n" +
                "const html = '<div class=\"x\"><span>foo</span></div>';\n";

        final CountingWriter empty = serializeWithScript("");
        final CountingWriter withScript = serializeWithScript(script);

        final int ltInScript = (int) script.chars().filter(c -> c == '<').count();
        System.out.println("[HtmlSerializerBenchmarkTest] script has " + ltInScript + " '<' chars");
        System.out.println("[HtmlSerializerBenchmarkTest] empty body —     per-char: "
                + empty.perCharWriteCalls + ", string: " + empty.stringWriteCalls);
        System.out.println("[HtmlSerializerBenchmarkTest] " + script.length() + "-char body — per-char: "
                + withScript.perCharWriteCalls + ", string: " + withScript.stringWriteCalls);

        // The script body's '<' chars must NOT cause per-char writes — they
        // roll up into bulk string writes via the raw-text fast path. The
        // empty/non-empty cases differ structurally by ~1 per-char write
        // (closeStartTag(true) coalesces "></tag>" while closeStartTag(false)
        // leaves the closing "</tag>" for endElement), but the delta must be
        // a small constant, not proportional to the number of '<' in script.
        final long perCharDelta = withScript.perCharWriteCalls - empty.perCharWriteCalls;
        assertTrue("Script body's '<' chars should NOT trigger per-char writes; "
                + "empty=" + empty.perCharWriteCalls + " withScript="
                + withScript.perCharWriteCalls + " delta=" + perCharDelta
                + " ('<' count in script=" + ltInScript + ")",
                perCharDelta < ltInScript);
        // And the script body characters must show up in bulk string output.
        // (Allow a small tolerance — empty/non-empty <script> differ by 1 char
        // because closeStartTag(true) writes "></script>" while non-empty
        // splits the close across two writers.write() calls.)
        final long stringCharsDelta = withScript.stringCharsWritten - empty.stringCharsWritten;
        assertTrue("Script body should add bulk string output close to its size; "
                + "empty=" + empty.stringCharsWritten + " withScript="
                + withScript.stringCharsWritten + " delta=" + stringCharsDelta
                + " script.length()=" + script.length(),
                stringCharsDelta >= script.length() - 5);
    }

    private CountingWriter serializeWithScript(final String script) throws TransformerException {
        final CountingWriter counter = new CountingWriter();
        final XHTMLWriter w = new HTML5Writer(counter);
        final Properties props = new Properties();
        props.setProperty(OutputKeys.METHOD, "html");
        w.setOutputProperties(props);
        w.startDocument();
        w.startElement(null, "html", "html");
        w.startElement(null, "body", "body");
        w.startElement(null, "script", "script");
        if (!script.isEmpty()) {
            w.characters(script);
        }
        w.endElement(null, "script", "script");
        w.endElement(null, "body", "body");
        w.endElement(null, "html", "html");
        w.endDocument();
        return counter;
    }

    @Test
    public void compareAgainstPerCharWriter() throws TransformerException, IOException {
        // Warm-up — let JIT compile the hot path
        for (int i = 0; i < 5; i++) {
            try (java.io.OutputStreamWriter w = newProductionLikeWriter()) { run(w); }
            try (java.io.OutputStreamWriter w = newProductionLikeWriter()) {
                run(new PerCharWriter(w));
            }
        }

        // Bulk path (current code)
        long bulkStart = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            try (java.io.OutputStreamWriter w = newProductionLikeWriter()) { run(w); }
        }
        long bulkMs = (System.nanoTime() - bulkStart) / 1_000_000L;

        // Per-char path: wraps the OutputStreamWriter so every char goes through
        // OutputStreamWriter.write(int) — same path the previous writeCharSeq used.
        long perCharStart = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            try (java.io.OutputStreamWriter w = newProductionLikeWriter()) {
                run(new PerCharWriter(w));
            }
        }
        long perCharMs = (System.nanoTime() - perCharStart) / 1_000_000L;

        System.out.println("[HtmlSerializerBenchmarkTest] " + ITERATIONS + " iters of "
                + PARAGRAPH_COUNT + "-paragraph HTML doc to OutputStreamWriter(UTF-8):");
        System.out.println("[HtmlSerializerBenchmarkTest]   bulk path:     " + bulkMs + " ms ("
                + String.format("%.3f", bulkMs * 1.0 / ITERATIONS) + " ms/doc)");
        System.out.println("[HtmlSerializerBenchmarkTest]   per-char path: " + perCharMs + " ms ("
                + String.format("%.3f", perCharMs * 1.0 / ITERATIONS) + " ms/doc)");
        System.out.println("[HtmlSerializerBenchmarkTest]   speedup:       "
                + String.format("%.2fx", perCharMs * 1.0 / Math.max(1, bulkMs)));

        assertTrue("Bulk path should be faster than per-char path; bulk="
                + bulkMs + "ms perChar=" + perCharMs + "ms", bulkMs < perCharMs);
    }

    @Test
    public void htmlSerializationHotPath() throws TransformerException, IOException {
        // Warm-up
        for (int i = 0; i < 3; i++) {
            run(new CountingWriter());
        }

        final CountingWriter counter = new CountingWriter();
        final long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            run(counter);
        }
        final long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        final long totalChars = counter.bulkCharsWritten + counter.stringCharsWritten + counter.perCharWriteCalls;
        final long bulkChars = counter.bulkCharsWritten + counter.stringCharsWritten;
        final double bulkPct = bulkChars * 100.0 / totalChars;

        System.out.println("[HtmlSerializerBenchmarkTest] " + ITERATIONS + " iterations of "
                + PARAGRAPH_COUNT + "-paragraph HTML doc in " + elapsedMs + " ms"
                + " (" + (elapsedMs * 1.0 / ITERATIONS) + " ms/doc)");
        System.out.println("[HtmlSerializerBenchmarkTest] bulk writes: "
                + counter.bulkWriteCalls + " (chars: " + counter.bulkCharsWritten + ")");
        System.out.println("[HtmlSerializerBenchmarkTest] string writes: "
                + counter.stringWriteCalls + " (chars: " + counter.stringCharsWritten + ")");
        System.out.println("[HtmlSerializerBenchmarkTest] per-char writes: "
                + counter.perCharWriteCalls);
        System.out.println("[HtmlSerializerBenchmarkTest] " + String.format("%.2f", bulkPct)
                + "% of output bytes flushed in bulk");

        // We expect the vast majority of safe-character output to flow through
        // bulk writes (Writer.write(char[],int,int) or Writer.write(String,int,int)).
        // Special-character escapes still go through per-char writes, but those
        // are a tiny minority of output for typical HTML.
        assertTrue("Expected >90% of chars to be flushed in bulk, but got " + bulkPct + "%",
                bulkPct > 90.0);
    }

    private void run(final Writer out) throws TransformerException {
        final XHTMLWriter w = new XHTMLWriter(out);
        final Properties props = new Properties();
        props.setProperty(OutputKeys.METHOD, "html");
        props.setProperty(OutputKeys.INDENT, "yes");
        w.setOutputProperties(props);
        w.startDocument();
        w.startElement(null, "html", "html");
        w.startElement(null, "body", "body");
        for (int i = 0; i < PARAGRAPH_COUNT; i++) {
            w.startElement(null, "p", "p");
            w.attribute("class", "para");
            w.characters(LOREM);
            w.endElement(null, "p", "p");
        }
        w.endElement(null, "body", "body");
        w.endElement(null, "html", "html");
        w.endDocument();
    }
}
