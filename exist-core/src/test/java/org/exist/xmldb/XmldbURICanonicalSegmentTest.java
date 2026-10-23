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
package org.exist.xmldb;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link XmldbURI#isCanonicalSegment(String)} admits only segments that {@link XmldbURI#create(String)} leaves as
 * they are, so that taking a segment as it is, without parsing it, gives the same URI; and
 * {@link XmldbURI#getPathSegments()} gives the segments it gave before, also for names not stored in that form.
 */
class XmldbURICanonicalSegmentTest {

    /** Each printable ASCII character, and a few others: a segment with it is canonical only if create keeps it. */
    @Test
    void aCanonicalSegmentIsOneThatCreateKeeps() {
        final StringBuilder chars = new StringBuilder();
        for (char c = 0x20; c < 0x7f; c++) {
            chars.append(c);
        }
        chars.append("éü€ \t");
        for (int i = 0; i < chars.length(); i++) {
            final String segment = "a" + chars.charAt(i) + "b";
            if (XmldbURI.isCanonicalSegment(segment)) {
                assertEquals(segment, XmldbURI.create(segment).getRawCollectionPath(), "create keeps '" + segment + "'");
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"plain.xml", "caf%C3%A9", "a%2520b", "a+b", "x~y", "a!$&'()*,;=@b", "-._~"})
    void canonical(final String segment) {
        assertTrue(XmldbURI.isCanonicalSegment(segment));
        assertEquals(segment, XmldbURI.create(segment).getRawCollectionPath());
    }

    @ParameterizedTest
    @ValueSource(strings = {"My Coll", "ü.xml", "back\\slash", "trailing ", " leading", "100%", "[bracket]", "a%zzb",
            "caf%c3%a9", "%4", "a/b", "with:colon"})
    void notCanonical(final String segment) {
        assertFalse(XmldbURI.isCanonicalSegment(segment));
    }

    /** Names stored as given, without encoding: their segments are parsed, and so encoded, as they were before. */
    @ParameterizedTest
    @ValueSource(strings = {"plain.xml", "caf%C3%A9", "a%2520b", "a+b", "x~y", "My Coll", "ü.xml", "back\\slash",
            "trailing ", " leading"})
    void pathSegmentsAreThoseOfCreate(final String raw) {
        final XmldbURI[] segments = XmldbURI.createInternal("/db/" + raw + "/doc.xml").getPathSegments();
        assertArrayEquals(new XmldbURI[] {XmldbURI.create("db"), XmldbURI.create(raw), XmldbURI.create("doc.xml")}, segments);
        assertArrayEquals(segments, XmldbURI.create("/db/" + raw + "/doc.xml").getPathSegments(),
                "the same segments for the stored path and the parsed one");
    }

    /** A stored name that create doesn't accept fails as it did before. */
    @ParameterizedTest
    @ValueSource(strings = {"100%", "[bracket]", "a%zzb"})
    void pathSegmentsThatCreateRejectsAreStillRejected(final String raw) {
        assertThrows(IllegalArgumentException.class, () -> XmldbURI.createInternal("/db/" + raw).getPathSegments());
    }

    @Test
    void canonicalPath() {
        assertTrue(XmldbURI.isCanonicalPath("db/caf%C3%A9/sub"));
        assertFalse(XmldbURI.isCanonicalPath("db/My Coll/sub"));
    }
}
