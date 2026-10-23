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
package org.exist.indexing.lucene;

import org.apache.lucene.index.Term;
import org.apache.lucene.queries.spans.SpanNearQuery;
import org.apache.lucene.queries.spans.SpanOrQuery;
import org.apache.lucene.queries.spans.SpanTermQuery;
import org.apache.lucene.search.MultiPhraseQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class PhraseAsNearRewriterTest {

    private static Term term(final String text) {
        return new Term("f", text);
    }

    @Test
    public void sloppyPhraseKeepsTheGapsBetweenItsTerms() {
        final PhraseQuery phrase = new PhraseQuery.Builder()
                .add(term("fair"), 0)
                .add(term("foul"), 2)
                .setSlop(1)
                .build();
        final Query expected = SpanNearQuery.newOrderedNearQuery("f")
                .addClause(new SpanTermQuery(term("fair")))
                .addGap(1)
                .addClause(new SpanTermQuery(term("foul")))
                .setSlop(1)
                .build();
        assertEquals(expected, PhraseAsNearRewriter.rewrite(phrase));
    }

    @Test
    public void sloppyMultiPhraseMatchesAnyTermOfAPosition() {
        final MultiPhraseQuery phrase = new MultiPhraseQuery.Builder()
                .add(term("fair"))
                .add(new Term[] { term("foul"), term("vile") })
                .setSlop(2)
                .build();
        final Query expected = SpanNearQuery.newOrderedNearQuery("f")
                .addClause(new SpanTermQuery(term("fair")))
                .addClause(new SpanOrQuery(new SpanTermQuery(term("foul")), new SpanTermQuery(term("vile"))))
                .setSlop(2)
                .build();
        assertEquals(expected, PhraseAsNearRewriter.rewrite(phrase));
    }

    @Test
    public void exactPhraseIsLeftAsIs() {
        final PhraseQuery phrase = new PhraseQuery("f", "fair", "foul");
        assertSame(phrase, PhraseAsNearRewriter.rewrite(phrase));
    }
}
