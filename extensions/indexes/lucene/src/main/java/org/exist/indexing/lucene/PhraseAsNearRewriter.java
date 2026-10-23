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
import org.apache.lucene.queries.spans.SpanQuery;
import org.apache.lucene.queries.spans.SpanTermQuery;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.MultiPhraseQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;

/**
 * Rewrites the sloppy {@link PhraseQuery} and {@link MultiPhraseQuery} clauses that Lucene's classic
 * string query syntax produces ({@code "a b"~n}) into an ordered {@link SpanNearQuery}, the query
 * eXist's XML {@code <near slop="n">} form produces.
 *
 * <p>{@code PhraseQuery}'s slop is an <em>edit distance</em> that tolerates some reordering of the
 * terms (see {@link PhraseQuery#getSlop()}), whereas an ordered {@code SpanNearQuery} counts the
 * positions between the terms and never matches them out of order. Both are intended Lucene
 * semantics, so a string slop query and an XML {@code <near>} query with the same slop can match
 * different documents. The opt-in {@code phrase-as-near} query option (see
 * {@link org.exist.xquery.modules.lucene.QueryOptions}) makes the string form match like
 * {@code <near>}, giving up the reordering tolerance.
 *
 * <p>A phrase at a top level, or a clause of a {@link BooleanQuery} or {@link BoostQuery}, is rewritten.
 * The positions between the phrase's terms, such as those of stopwords the analyzer dropped, are kept
 * as gaps. A phrase with slop 0 is left as is: it already matches its terms in order only.
 *
 * @see <a href="https://github.com/eXist-db/exist/issues/833">GitHub issue #833</a>
 */
public final class PhraseAsNearRewriter {

    private PhraseAsNearRewriter() {
    }

    /**
     * @param query the parsed query
     * @return {@code query} with every sloppy phrase replaced by an ordered {@link SpanNearQuery}
     */
    public static Query rewrite(final Query query) {
        if (query instanceof PhraseQuery phraseQuery) {
            return rewritePhrase(phraseQuery);
        }
        if (query instanceof MultiPhraseQuery multiPhraseQuery) {
            return rewriteMultiPhrase(multiPhraseQuery);
        }
        if (query instanceof BoostQuery boostQuery) {
            final Query rewritten = rewrite(boostQuery.getQuery());
            return rewritten == boostQuery.getQuery() ? query : new BoostQuery(rewritten, boostQuery.getBoost());
        }
        if (query instanceof BooleanQuery booleanQuery) {
            return rewriteBoolean(booleanQuery);
        }
        return query;
    }

    private static Query rewritePhrase(final PhraseQuery query) {
        if (query.getSlop() == 0 || query.getTerms().length < 2) {
            return query;
        }
        return toSpanNear(query.getField(), clauses(query), query.getPositions(), query.getSlop());
    }

    private static Query rewriteMultiPhrase(final MultiPhraseQuery query) {
        final Term[][] termArrays = query.getTermArrays();
        if (query.getSlop() == 0 || termArrays.length < 2) {
            return query;
        }
        return toSpanNear(termArrays[0][0].field(), clauses(query), query.getPositions(), query.getSlop());
    }

    private static Query rewriteBoolean(final BooleanQuery query) {
        final BooleanQuery.Builder builder = new BooleanQuery.Builder();
        boolean changed = false;
        for (final BooleanClause clause : query.clauses()) {
            final Query rewritten = rewrite(clause.query());
            changed |= rewritten != clause.query();
            builder.add(rewritten, clause.occur());
        }
        builder.setMinimumNumberShouldMatch(query.getMinimumNumberShouldMatch());
        return changed ? builder.build() : query;
    }

    private static SpanQuery[] clauses(final PhraseQuery query) {
        final Term[] terms = query.getTerms();
        final SpanQuery[] clauses = new SpanQuery[terms.length];
        for (int i = 0; i < terms.length; i++) {
            clauses[i] = new SpanTermQuery(terms[i]);
        }
        return clauses;
    }

    private static SpanQuery[] clauses(final MultiPhraseQuery query) {
        final Term[][] termArrays = query.getTermArrays();
        final SpanQuery[] clauses = new SpanQuery[termArrays.length];
        for (int i = 0; i < termArrays.length; i++) {
            final SpanQuery[] alternatives = new SpanQuery[termArrays[i].length];
            for (int j = 0; j < alternatives.length; j++) {
                alternatives[j] = new SpanTermQuery(termArrays[i][j]);
            }
            clauses[i] = alternatives.length == 1 ? alternatives[0] : new SpanOrQuery(alternatives);
        }
        return clauses;
    }

    private static SpanNearQuery toSpanNear(final String field, final SpanQuery[] clauses, final int[] positions,
            final int slop) {
        final SpanNearQuery.Builder builder = SpanNearQuery.newOrderedNearQuery(field).setSlop(slop);
        for (int i = 0; i < clauses.length; i++) {
            if (i > 0 && positions[i] - positions[i - 1] > 1) {
                builder.addGap(positions[i] - positions[i - 1] - 1);
            }
            builder.addClause(clauses[i]);
        }
        return builder.build();
    }
}
