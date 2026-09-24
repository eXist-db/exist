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
import org.apache.lucene.queries.spans.SpanQuery;
import org.apache.lucene.queries.spans.SpanTermQuery;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;

/**
 * Rewrites {@link PhraseQuery} clauses (produced by Lucene's classic string query syntax,
 * e.g. {@code "a b"~n}) into an equivalent ordered {@link SpanNearQuery}, i.e. the same query
 * shape eXist's XML {@code <near slop="n">} form produces.
 *
 * <p>This exists because {@code PhraseQuery}'s slop is an <em>edit distance</em> that tolerates
 * some amount of term reordering (see {@link PhraseQuery#getSlop()}), whereas {@code SpanNearQuery}
 * (ordered) uses a plain positional gap and never matches out-of-order terms. The two are
 * different, both intentional, Lucene semantics — not a bug in either — so string-syntax slop
 * queries and XML {@code <near>} queries can genuinely disagree on which documents match for the
 * same nominal slop value when word order in the indexed text varies. Applying this rewriter
 * (via the opt-in {@code phrase-as-near} query option, see {@link org.exist.xquery.modules.lucene.QueryOptions})
 * makes the string form match with the same ordered, non-reordering-tolerant semantics as
 * {@code <near>}, at the cost of losing PhraseQuery's reordering tolerance.
 *
 * <p><b>Scope:</b> only rewrites a top-level {@link PhraseQuery} and {@link PhraseQuery} clauses
 * directly inside a {@link BooleanQuery} or {@link BoostQuery} (mirroring the shapes
 * {@link AnalyzingQueryRewriter}, which runs immediately before this in the query pipeline,
 * already unwraps). Assumes the phrase's terms are at consecutive positions
 * (the common case); a phrase whose query analyzer drops an interior token (e.g. a stopword),
 * leaving a position gap recorded via {@link PhraseQuery#getPositions()}, is rewritten as if that
 * gap were the default 1, which is slightly more lenient than the original phrase query.
 *
 * @see <a href="https://github.com/eXist-db/exist/issues/833">GitHub issue #833</a>
 */
public final class PhraseAsNearRewriter {

    private PhraseAsNearRewriter() {
    }

    /**
     * @param query the parsed query
     * @return {@code query} with every (rewritable) {@link PhraseQuery} replaced by an
     *     equivalent ordered {@link SpanNearQuery}
     */
    public static Query rewrite(final Query query) {
        if (query instanceof PhraseQuery phraseQuery) {
            return toSpanNear(phraseQuery);
        }
        if (query instanceof BoostQuery boostQuery) {
            final Query rewritten = rewrite(boostQuery.getQuery());
            return rewritten == boostQuery.getQuery() ? query : new BoostQuery(rewritten, boostQuery.getBoost());
        }
        if (query instanceof BooleanQuery booleanQuery) {
            final BooleanQuery.Builder builder = new BooleanQuery.Builder();
            boolean changed = false;
            for (final BooleanClause clause : booleanQuery.clauses()) {
                final Query rewritten = rewrite(clause.query());
                changed |= rewritten != clause.query();
                builder.add(rewritten, clause.occur());
            }
            builder.setMinimumNumberShouldMatch(booleanQuery.getMinimumNumberShouldMatch());
            return changed ? builder.build() : query;
        }
        return query;
    }

    private static Query toSpanNear(final PhraseQuery query) {
        final Term[] terms = query.getTerms();
        // SpanNearQuery requires at least 2 clauses; a single-term "phrase" is just a term match.
        if (terms.length < 2) {
            return query;
        }
        final SpanQuery[] clauses = new SpanQuery[terms.length];
        for (int i = 0; i < terms.length; i++) {
            clauses[i] = new SpanTermQuery(terms[i]);
        }
        return new SpanNearQuery(clauses, query.getSlop(), true);
    }
}
