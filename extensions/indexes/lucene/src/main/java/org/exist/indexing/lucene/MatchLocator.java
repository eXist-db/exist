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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.facet.DrillDownQuery;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.memory.MemoryIndex;
import org.apache.lucene.queries.function.FunctionScoreQuery;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.DisjunctionMaxQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Matches;
import org.apache.lucene.search.MatchesIterator;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Weight;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds where queries match a text, as Lucene matches them: the text is indexed as one document in memory, with the
 * analyzer that indexed it, and Lucene's {@link Matches} give the offsets of each match.
 * <p>
 * A phrase, a phrase with slop, a span or near query, and a wildcard, prefix or fuzzy term match what they match in
 * a search, and a phrase or near match is one span from its first term to its last. A wildcard is expanded against
 * the terms of the text itself, so no expansion against the index has to be kept.
 * <p>
 * Each part of a query that matches text is located on its own, so that a document found by a boolean query is
 * highlighted where any of its parts matches, even where a part on another field is what found it. Parts that exclude
 * or only filter, {@code MUST_NOT} and {@code FILTER} clauses, are not located.
 */
public final class MatchLocator {

    /**
     * A match in the text.
     *
     * @param start the offset of its first character
     * @param end the offset after its last character
     */
    public record Span(int start, int end) {
    }

    /** a part of the queries that matches text, and the fields it searches */
    private record Part(Query query, Set<String> fields) {
    }

    private final List<Part> parts = new ArrayList<>();

    /**
     * @param queries the queries to locate the matches of
     */
    public MatchLocator(final Collection<Query> queries) {
        for (final Query query : queries) {
            addParts(query);
        }
    }

    private void addParts(final Query query) {
        if (query instanceof BooleanQuery bool) {
            for (final BooleanClause clause : bool.clauses()) {
                if (clause.occur() != BooleanClause.Occur.MUST_NOT && clause.occur() != BooleanClause.Occur.FILTER) {
                    addParts(clause.query());
                }
            }
        } else if (query instanceof BoostQuery boost) {
            addParts(boost.getQuery());
        } else if (query instanceof ConstantScoreQuery constant) {
            addParts(constant.getQuery());
        } else if (query instanceof FunctionScoreQuery function) {
            addParts(function.getWrappedQuery());
        } else if (query instanceof DrillDownQuery drillDown) {
            addParts(drillDown.getBaseQuery());
        } else if (query instanceof DisjunctionMaxQuery disjunction) {
            for (final Query disjunct : disjunction.getDisjuncts()) {
                addParts(disjunct);
            }
        } else if (query != null) {
            parts.add(new Part(query, fieldsOf(query)));
        }
    }

    /**
     * @return the fields the queries' parts search
     */
    public Set<String> fields() {
        final Set<String> fields = new HashSet<>();
        for (final Part part : parts) {
            fields.addAll(part.fields());
        }
        return fields;
    }

    private static Set<String> fieldsOf(final Query query) {
        final Set<String> fields = new HashSet<>();
        query.visit(new QueryVisitor() {
            @Override
            public boolean acceptField(final String field) {
                fields.add(field);
                return false;
            }

            @Override
            public QueryVisitor getSubVisitor(final BooleanClause.Occur occur, final Query parent) {
                return occur == BooleanClause.Occur.MUST_NOT ? EMPTY_VISITOR : this;
            }
        });
        return fields;
    }

    /**
     * Locates the matches in a text indexed in the given fields.
     *
     * @param fields the fields the text is indexed in; parts of the queries that search other fields are not located
     * @param text the text, as it was indexed
     * @param analyzer the analyzer it was indexed with
     * @return the matches, in the order of the text; overlapping matches are merged into one
     * @throws IOException if the text cannot be analyzed
     */
    public List<Span> locate(final Collection<String> fields, final String text, final Analyzer analyzer) throws IOException {
        final List<Part> searching = new ArrayList<>(parts.size());
        for (final Part part : parts) {
            if (part.fields().stream().anyMatch(fields::contains)) {
                searching.add(part);
            }
        }
        final List<Span> spans = new ArrayList<>();
        if (searching.isEmpty() || text.isEmpty()) {
            return spans;
        }
        final MemoryIndex memory = new MemoryIndex(true);
        for (final String field : fields) {
            memory.addField(field, text, analyzer);
        }
        final IndexSearcher searcher = memory.createSearcher();
        final LeafReaderContext leaf = searcher.getIndexReader().leaves().get(0);
        for (final Part part : searching) {
            addSpans(searcher, leaf, part.query(), fields, spans);
        }
        return merged(spans);
    }

    private static void addSpans(final IndexSearcher searcher, final LeafReaderContext leaf, final Query query,
            final Collection<String> fields, final List<Span> spans) throws IOException {
        final Weight weight = searcher.createWeight(searcher.rewrite(query), ScoreMode.COMPLETE_NO_SCORES, 1);
        final Matches matches = weight.matches(leaf, 0);
        if (matches == null) {
            return;
        }
        for (final String field : fields) {
            final MatchesIterator iterator = matches.getMatches(field);
            if (iterator == null) {
                continue;
            }
            while (iterator.next()) {
                if (iterator.startOffset() >= 0 && iterator.endOffset() > iterator.startOffset()) {
                    spans.add(new Span(iterator.startOffset(), iterator.endOffset()));
                }
            }
        }
    }

    private static List<Span> merged(final List<Span> spans) {
        spans.sort(Comparator.comparingInt(Span::start).thenComparingInt(Span::end));
        final List<Span> merged = new ArrayList<>(spans.size());
        for (final Span span : spans) {
            final Span last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
            if (last != null && span.start() < last.end()) {
                merged.set(merged.size() - 1, new Span(last.start(), Math.max(last.end(), span.end())));
            } else {
                merged.add(span);
            }
        }
        return merged;
    }
}
