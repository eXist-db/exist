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
package org.exist.xquery.util;

import org.exist.xquery.functions.fn.FunEscapeURI;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Keystone proof for the resource-naming contract prototype (eXist-db/exist#6463, decision 2:
 * literal {@code '%'}).
 *
 * <p>This test validates the proposed END-STATE codec for a single resource/collection name:
 * {@link URIUtils#encodeForURILenient(String)} on store and {@link URIUtils#decodeForURI(String)}
 * on display. It proves three things the team needs in order to choose the contract direction with
 * full information:</p>
 *
 * <ol>
 *   <li><b>Bijectivity</b> -- the codec round-trips every name in the corpus, INCLUDING names that
 *       contain a literal percent sign ({@code 50%.xml}, {@code a%20b.xml}, {@code %25.xml}), which
 *       is the hard case decision 2 is about.</li>
 *   <li><b>The exact canonical stored form</b> -- each assertion pins the stored bytes, so the
 *       contract is documented by executable example. Sub-delimiters stay literal
 *       ({@code it's.xml}, {@code a+b.xml}) -- the eXide#824 direction -- while {@code '%'} is
 *       always escaped to {@code %25}, which is the single change that buys bijectivity.</li>
 *   <li><b>Why today's store path fails</b> -- the legacy boundary
 *       ({@code xmldbUriFor(escape=true)} == {@link FunEscapeURI#escape(CharSequence, boolean)} then
 *       {@code new URI(...)}) keeps {@code '%'} literal, so a literal-percent name is either
 *       silently corrupted ({@code a%20b} -&gt; {@code a b}, a space) or rejected outright
 *       ({@code 5%done} throws). The new codec fixes both.</li>
 * </ol>
 *
 * <p>The headline finding for #6463: a literal {@code '%'} is fully representable WITHIN the lenient
 * (sub-delimiters-literal) contract -- it needs only that {@code '%'} always be escaped to
 * {@code %25}. It does NOT force adopting the strict full-encode scheme ({@code it's -> it%27s}),
 * so supporting {@code '%'} does not imply a second migration of stored names away from the lenient
 * direction.</p>
 */
class ResourceNameCodecTest {

    /**
     * The round-trip corpus from the tasking: spaces, Unicode, sub-delimiters, and -- the hard
     * case -- literal percent. Key = the literal (display) name the user types; value = its
     * canonical stored form under the proposed lenient-bijective contract.
     */
    private static Map<String, String> corpus() {
        final Map<String, String> c = new LinkedHashMap<>();

        // --- plain ASCII (unchanged) ---
        c.put("plain.xml", "plain.xml");

        // --- space ---
        c.put("hello world.xml", "hello%20world.xml");

        // --- Unicode (percent-encoded as UTF-8 bytes) ---
        c.put("café.xml", "caf%C3%A9.xml");
        c.put("naïve.xml", "na%C3%AFve.xml");
        c.put("Москва.xml", "%D0%9C%D0%BE%D1%81%D0%BA%D0%B2%D0%B0.xml");
        c.put("日本.xml", "%E6%97%A5%E6%9C%AC.xml");

        // --- sub-delimiters: LEFT LITERAL (the eXide#824 / openapi#54 direction) ---
        c.put("it's.xml", "it's.xml");
        c.put("a+b.xml", "a+b.xml");
        c.put("Jack&Jill.xml", "Jack&Jill.xml");
        c.put("report(final).xml", "report(final).xml");
        c.put("a,b;c=d.xml", "a,b;c=d.xml");
        c.put("@home.xml", "@home.xml");

        // --- gen-delimiters that a URL path segment must escape (so REST/WebDAV escape them too,
        //     and we match, for cross-surface convergence): # ? [ ] ---
        c.put("a#b.xml", "a%23b.xml");
        c.put("q?x.xml", "q%3Fx.xml");
        c.put("[draft].xml", "%5Bdraft%5D.xml");

        // --- characters that macOS/Windows reject in FILENAMES. Most are not RFC 3986 pchar, so
        //     the codec already percent-encodes them (the encoded stored form is filesystem-safe).
        //     NOTE: ':' and '*' ARE pchar, so the codec keeps them LITERAL -- safe in eXist's native
        //     page store, but hostile if a name becomes a host filename (backup/export). Flagged in
        //     the fallout report as a contract question (encode ':'/'*' for cross-OS file safety?). ---
        c.put("a<b.xml", "a%3Cb.xml");
        c.put("a>b.xml", "a%3Eb.xml");
        c.put("a\"b.xml", "a%22b.xml");
        c.put("a\\b.xml", "a%5Cb.xml");
        c.put("a|b.xml", "a%7Cb.xml");
        c.put("a:b.xml", "a%3Ab.xml");   // pchar, but eXist's XmldbURI can't store a literal ':' -> encode
        c.put("a*b.xml", "a*b.xml");     // pchar -> kept literal (FS-hostile on Windows, but storable)

        // --- literal percent: the hard case decision 2 is about (ALWAYS escaped to %25) ---
        c.put("50%.xml", "50%25.xml");
        c.put("a%20b.xml", "a%2520b.xml");
        c.put("%25.xml", "%2525.xml");
        c.put("100%done%.xml", "100%25done%25.xml");

        // --- literal slash inside a NAME (data, not a separator) ---
        c.put("a/b.xml", "a%2Fb.xml");

        return c;
    }

    /**
     * (1) Bijectivity: {@code decodeForURI(encodeForURILenient(name)) == name} for every name,
     * including the literal-percent cases. This is the property decision 2 hinges on.
     */
    @Test
    void lenientCodecRoundTripsEveryName() {
        for (final String name : corpus().keySet()) {
            final String stored = URIUtils.encodeForURILenient(name);
            final String display = URIUtils.decodeForURI(stored);
            assertEquals(name, display, "round-trip failed for: " + name);
        }
    }

    /**
     * (2) The exact canonical stored form for every name -- the contract, documented by example.
     */
    @Test
    void lenientCodecProducesCanonicalStoredForm() {
        for (final Map.Entry<String, String> e : corpus().entrySet()) {
            assertEquals(e.getValue(), URIUtils.encodeForURILenient(e.getKey()),
                    "wrong stored form for: " + e.getKey());
        }
    }

    /**
     * (2b) Path-level round-trip: the same property holds across a whole {@code '/'}-delimited
     * collection path, segment by segment.
     */
    @Test
    void lenientCodecRoundTripsWholePath() {
        final String path = "/db/data/Reports 2026/café & crème/50%.xml";
        final String stored = URIUtils.encodePathForURILenient(path);
        assertEquals("/db/data/Reports%202026/caf%C3%A9%20&%20cr%C3%A8me/50%25.xml", stored);
        assertEquals(path, URIUtils.decodePathForURI(stored));
    }

    /**
     * (3a) Why today's store path SILENTLY CORRUPTS a literal-percent name when the percent
     * happens to be followed by two hex digits: the legacy boundary keeps {@code '%'} literal, so
     * {@code a%20b.xml} is misread as an already-encoded space and comes back as {@code "a b.xml"}.
     */
    @Test
    void legacyStorePathCorruptsLiteralPercentBeforeHex() throws URISyntaxException {
        final String name = "a%20b.xml";

        // legacy store boundary: xmldbUriFor(escape=true) == FunEscapeURI.escape(.., false) then new URI(..)
        final String legacyStored = new URI(FunEscapeURI.escape(name, false)).getRawPath();
        // '%' was kept literal, so the %20 is preserved verbatim as if it were an escape
        assertEquals("a%20b.xml", legacyStored);
        // legacy display (decode on read) then turns it into a space -- data loss
        final String legacyDisplay = URIUtils.decodeForURI(legacyStored);
        assertEquals("a b.xml", legacyDisplay);
        assertNotEquals(name, legacyDisplay, "legacy path must be shown to corrupt this name");

        // the new codec round-trips it exactly
        assertEquals(name, URIUtils.decodeForURI(URIUtils.encodeForURILenient(name)));
    }

    /**
     * (3b) Why today's store path REJECTS a literal-percent name when the percent is not followed
     * by two hex digits: {@code FunEscapeURI.escape} keeps {@code '%'} literal and
     * {@code new URI("5%done.xml")} throws on the malformed escape. The new codec stores it fine.
     */
    @Test
    void legacyStorePathRejectsBareLiteralPercent() {
        final String name = "5%done.xml";

        assertThrows(URISyntaxException.class,
                () -> new URI(FunEscapeURI.escape(name, false)),
                "legacy boundary must be shown to reject a bare literal '%'");

        // the new codec stores and recovers it
        final String stored = URIUtils.encodeForURILenient(name);
        assertEquals("5%25done.xml", stored);
        assertEquals(name, URIUtils.decodeForURI(stored));
    }

    /**
     * (4a) The strict-vs-lenient fork: {@link URIUtils#encodeForURI(String)} (strict RFC 3986) is
     * ALSO bijective with {@code decodeForURI}; it differs from the lenient encoder only in that it
     * escapes sub-delimiters too ({@code it's -> it%27s}). Both handle literal {@code '%'}
     * identically. This documents that decision 2 is orthogonal to the sub-delimiter choice.
     */
    @Test
    void strictCodecIsAlsoBijectiveAndDiffersOnlyOnSubDelimiters() {
        for (final String name : corpus().keySet()) {
            assertEquals(name, URIUtils.decodeForURI(URIUtils.encodeForURI(name)),
                    "strict codec round-trip failed for: " + name);
        }
        // the one visible difference: sub-delimiters
        assertEquals("it%27s.xml", URIUtils.encodeForURI("it's.xml"));
        assertEquals("it's.xml", URIUtils.encodeForURILenient("it's.xml"));
        // literal percent is handled the same way by both
        assertEquals("50%25.xml", URIUtils.encodeForURI("50%.xml"));
        assertEquals("50%25.xml", URIUtils.encodeForURILenient("50%.xml"));
    }

    /**
     * (4b) The encoding is intentionally NOT idempotent -- it must be applied EXACTLY ONCE at the
     * storage boundary. Encoding an already-encoded name double-escapes the percent signs, which is
     * why the contract has to nail down a single encode point per surface.
     */
    @Test
    void lenientEncodingIsNotIdempotent() {
        final String once = URIUtils.encodeForURILenient("50%.xml");
        final String twice = URIUtils.encodeForURILenient(once);
        assertEquals("50%25.xml", once);
        assertEquals("50%2525.xml", twice);
        assertNotEquals(once, twice, "encoding must be applied exactly once");
    }

    /**
     * (5) The decoder never throws and never truncates, even on inputs that {@code java.net.URI}
     * would reject or silently cut at {@code '?'}/{@code '#'}. Resource names can contain anything.
     */
    @Test
    void decoderNeverThrowsOrTruncates() {
        // a trailing or malformed percent is preserved verbatim
        assertEquals("trailing%", URIUtils.decodeForURI("trailing%"));
        assertEquals("bad%zz", URIUtils.decodeForURI("bad%zz"));
        // '?' and '#' are ordinary characters here, not query/fragment delimiters
        assertEquals("a?b#c.xml", URIUtils.decodeForURI("a?b#c.xml"));
    }
}
