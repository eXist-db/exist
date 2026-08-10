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
package org.exist.http;

import org.exist.http.AcceptHeader.MediaRange;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AcceptHeaderTest {

    @Test
    public void parseOrdersByQualityThenSpecificity() {
        final List<MediaRange> ranges = AcceptHeader.parse("text/*;q=0.5, text/html, application/json;q=0.9, */*;q=0.1");
        assertEquals(4, ranges.size());
        assertEquals("text/html", ranges.getFirst().mediaType());        // q=1.0
        assertEquals("application/json", ranges.get(1).mediaType());  // q=0.9
        assertEquals("text/*", ranges.get(2).mediaType());           // q=0.5
        assertEquals("*/*", ranges.get(3).mediaType());              // q=0.1
    }

    @Test
    public void parseDefaultsQualityToOne() {
        final List<MediaRange> ranges = AcceptHeader.parse("text/html");
        assertEquals(1, ranges.size());
        assertEquals(1.0, ranges.getFirst().quality(), 0.0);
    }

    @Test
    public void parseExtractsParametersExcludingQ() {
        final List<MediaRange> ranges = AcceptHeader.parse("text/html;level=1;q=0.8");
        assertEquals(1, ranges.size());
        final MediaRange range = ranges.getFirst();
        assertEquals(0.8, range.quality(), 0.0);
        assertEquals(1, range.parameters().size());
        assertEquals("1", range.parameters().get("level"));
    }

    @Test
    public void parseRetainsExplicitQZero() {
        final List<MediaRange> ranges = AcceptHeader.parse("text/html;q=0");
        assertEquals(1, ranges.size());
        assertEquals(0.0, ranges.getFirst().quality(), 0.0);
    }

    @Test
    public void parseSkipsMalformedEntries() {
        final List<MediaRange> ranges = AcceptHeader.parse("text/html, garbage, , application/json");
        assertEquals(2, ranges.size());
    }

    @Test
    public void parseEmptyOrNullYieldsEmptyList() {
        assertTrue(AcceptHeader.parse(null).isEmpty());
        assertTrue(AcceptHeader.parse("").isEmpty());
        assertTrue(AcceptHeader.parse("   ").isEmpty());
    }

    @Test
    public void negotiatePicksHighestQuality() {
        final Optional<String> best = AcceptHeader.negotiate(
                "text/html, application/xhtml+xml, application/json;q=0.9, */*;q=0.8",
                asList("application/json", "application/xml"));
        assertEquals(Optional.of("application/json"), best);  // json q=0.9 beats xml via */* q=0.8
    }

    @Test
    public void negotiatePrefersExactOverWildcardMatch() {
        final Optional<String> best = AcceptHeader.negotiate(
                "text/html, application/json;q=0.9, */*;q=0.8",
                asList("text/html", "application/json"));
        assertEquals(Optional.of("text/html"), best);  // q=1.0
    }

    @Test
    public void negotiateNoMatchYieldsEmpty() {
        assertFalse(AcceptHeader.negotiate("application/json", singletonList("application/xml")).isPresent());
    }

    @Test
    public void negotiateNoAcceptHeaderYieldsFirstOffer() {
        assertEquals(Optional.of("application/json"),
                AcceptHeader.negotiate(null, asList("application/json", "application/xml")));
        assertEquals(Optional.of("application/json"),
                AcceptHeader.negotiate("", asList("application/json", "application/xml")));
    }

    @Test
    public void negotiateWildcardAcceptYieldsFirstOffer() {
        assertEquals(Optional.of("application/json"),
                AcceptHeader.negotiate("*/*", asList("application/json", "application/xml")));
    }

    @Test
    public void negotiateHonorsQZeroAsRejection() {
        final Optional<String> best = AcceptHeader.negotiate(
                "text/html;q=0, application/json",
                asList("text/html", "application/json"));
        assertEquals(Optional.of("application/json"), best);
    }

    @Test
    public void negotiateSupportsTypeWildcard() {
        assertEquals(Optional.of("text/html"),
                AcceptHeader.negotiate("text/*", asList("application/json", "text/html")));
    }

    @Test
    public void negotiateEmptyOffersYieldsEmpty() {
        assertFalse(AcceptHeader.negotiate("text/html", emptyList()).isPresent());
    }

    @Test
    public void negotiateTieBreaksByOfferOrder() {
        // both acceptable only via */* at equal quality and specificity -> first offer wins
        assertEquals(Optional.of("application/xml"),
                AcceptHeader.negotiate("*/*", asList("application/xml", "application/json")));
    }
}
