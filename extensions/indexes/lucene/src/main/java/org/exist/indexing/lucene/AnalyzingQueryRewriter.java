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
 * but WITHOUT ANY WARRANTY. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.indexing.lucene;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.WildcardQuery;

import java.io.IOException;
import java.io.StringReader;

/**
 * Rewrites PrefixQuery and WildcardQuery terms through an analyzer so they match
 * index terms produced by the same analyzer (e.g. diacritic stripping, lowercasing).
 * StandardQueryParser does not analyze multi-term query terms; this rewriter fixes that.
 */
public final class AnalyzingQueryRewriter {

    private AnalyzingQueryRewriter() {
    }

    /**
     * Rewrites PrefixQuery and simple-prefix WildcardQuery terms through the analyzer.
     *
     * @param query  the parsed query
     * @param analyzer the index analyzer (null = no rewrite)
     * @return rewritten query
     */
    public static Query rewrite(Query query, Analyzer analyzer) {
        if (analyzer == null || query == null) {
            return query;
        }
        return rewriteQuery(query, analyzer);
    }

    private static Query rewriteQuery(Query query, Analyzer analyzer) {
        if (query instanceof BooleanQuery bool) {
            BooleanQuery.Builder builder = new BooleanQuery.Builder();
            for (BooleanClause clause : bool.clauses()) {
                builder.add(rewriteQuery(clause.query(), analyzer), clause.occur());
            }
            builder.setMinimumNumberShouldMatch(bool.getMinimumNumberShouldMatch());
            return builder.build();
        }
        if (query instanceof BoostQuery boost) {
            return new BoostQuery(rewriteQuery(boost.getQuery(), analyzer), boost.getBoost());
        }
        if (query instanceof PrefixQuery pq) {
            Term t = pq.getPrefix();
            String norm = normalizeTerm(t.field(), t.text(), analyzer);
            if (norm != null) {
                return new PrefixQuery(new Term(t.field(), norm));
            }
            return query;
        }
        if (query instanceof WildcardQuery wq) {
            Term t = wq.getTerm();
            String pattern = t.text();
            int lastStar = pattern.lastIndexOf('*');
            int lastQ = pattern.lastIndexOf('?');
            boolean simplePrefix = lastStar == pattern.length() - 1 && lastQ < 0 && pattern.indexOf('*') == lastStar;
            if (simplePrefix) {
                String stem = pattern.substring(0, lastStar);
                String norm = normalizeTerm(t.field(), stem, analyzer);
                if (norm != null) {
                    return new WildcardQuery(new Term(t.field(), norm + "*"));
                }
            }
            return query;
        }
        return query;
    }

    static String normalizeTerm(String field, String text, Analyzer analyzer) {
        try (TokenStream stream = analyzer.tokenStream(field, new StringReader(text))) {
            CharTermAttribute termAttr = stream.addAttribute(CharTermAttribute.class);
            stream.reset();
            if (stream.incrementToken()) {
                return termAttr.toString();
            }
            stream.end();
        } catch (IOException e) {
            return null;
        }
        return null;
    }
}
