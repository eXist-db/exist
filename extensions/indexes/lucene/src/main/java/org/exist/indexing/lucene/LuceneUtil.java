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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.facet.DrillDownQuery;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.Fields;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.queries.function.FunctionScoreQuery;
import org.apache.lucene.queries.spans.SpanNearQuery;
import org.apache.lucene.queries.spans.SpanQuery;
import org.apache.lucene.queries.spans.SpanTermQuery;
import org.apache.lucene.search.*;
import org.apache.lucene.util.AttributeSource;
import org.apache.lucene.util.BytesRef;

import org.exist.dom.QName;
import org.exist.dom.persistent.SymbolTable;
import org.exist.numbering.NodeId;
import org.exist.storage.BrokerPool;
import org.exist.util.ByteConversion;

import javax.xml.XMLConstants;

public class LuceneUtil {

    public static final String FIELD_NODE_ID = "nodeId";
    /** DocValues-only field for nodeId; avoids conflict with indexed Field of same name (LUCENE-6019). */
    public static final String FIELD_NODE_ID_DV = "nodeId_dv";

    public static final String FIELD_DOC_ID = "docId";
    public static final String FIELD_DOC_URI = "docUri";
    /** Per-document boost for attribute/config-based scoring (Lucene 10: index-time boost via FloatDocValues) */
    public static final String FIELD_BOOST = "_boost";
    /** Identifies which index config created the Lucene doc; used to filter when multiple indexes share a collection. */
    public static final String FIELD_INDEX_TYPE = "_idx";

    public static byte[] createId(final int docId, final NodeId nodeId) {
        // build id from nodeId and docId
        final byte[] data = new byte[nodeId.size() + 4];
        ByteConversion.intToByteH(docId, data, 0);
        nodeId.serialize(data, 4);

        return data;
    }

    public static byte[] createId(final NodeId nodeId) {
        final byte[] data = new byte[nodeId.size()];
        nodeId.serialize(data, 0);
        return data;
    }

    public static NodeId readNodeId(final int doc, final BinaryDocValues nodeIdValues, final BrokerPool pool) throws IOException {
        if (nodeIdValues.advanceExact(doc)) {
            final BytesRef ref = nodeIdValues.binaryValue();
            final int units = ByteConversion.byteToShortH(ref.bytes, ref.offset);
            return pool.getNodeFactory().createFromData(units, ref.bytes, ref.offset + 2);
        }
        return null;
    }

    /**
     * Encode an element or attribute qname into a lucene field name using the
     * internal ids for namespace and local name.
     *
     * @param qname the name
     * @param symbols the symbol table
     *
     * @return the encoded qname
     */
    public static String encodeQName(final QName qname, final SymbolTable symbols) {
        final short namespaceId = symbols.getNSSymbol(qname.getNamespaceURI());
        final short localNameId = symbols.getSymbol(qname.getLocalPart());
        final long nameId = qname.getNameType() | (namespaceId & 0xFFFF) << 16 | (localNameId & 0xFFFFFFFFL) << 32;
        return Long.toHexString(nameId);
    }

    /**
     * Decode the lucene field name into an element or attribute qname.
     *
     * @param s the encoded qname
     * @param symbols the symbol table
     *
     * @return the qname
     */
    public static QName decodeQName(final String s, final SymbolTable symbols) {
        try {
            final long l = Long.parseLong(s, 16);
            final short namespaceId = (short) ((l >>> 16) & 0xFFFFL);
            final short localNameId = (short) ((l >>> 32) & 0xFFFFL);
            final byte type = (byte) (l & 0xFFL);
            final String namespaceURI = symbols.getNamespace(namespaceId);
            final String localName = symbols.getName(localNameId);
            return new QName(localName, namespaceURI, XMLConstants.DEFAULT_NS_PREFIX, type);
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    public static String[] extractFields(final Query query, final IndexReader reader) throws IOException {
        final Map<Object, Query> map = new TreeMap<>();
        extractTerms(query, map, reader, true);
        final Set<String> fields = new TreeSet<>();
        for (final Object term : map.keySet()) {
            fields.add(((Term)term).field());
        }
        final String[] fieldArray = new String[fields.size()];
        return fields.toArray(fieldArray);
    }

    /**
     * Extract all terms which would be matched by a given query.
     * The terms are put into a map with the term as key and the
     * corresponding query object as value.
     *
     * This method is used by {@link LuceneMatchListener}
     * to highlight matches in the search results.
     *
     * @param query the query
     * @param terms the terms
     * @param reader the index reader
     * @param includeFields true to include fields, false to exclude
     *
     * @throws IOException if an I/O error occurs
     * @throws UnsupportedOperationException if the query type is not supported
     */
    public static void extractTerms(final Query query, final Map<Object, Query> terms, final IndexReader reader, final boolean includeFields) throws IOException, UnsupportedOperationException {
        switch (query) {
            case BooleanQuery booleanClauses -> extractTermsFromBoolean(booleanClauses, terms, reader, includeFields);
            case TermQuery termQuery -> extractTermsFromTerm(termQuery, terms, includeFields);
            case WildcardQuery wildcardQuery -> extractTermsFromWildcard(wildcardQuery, terms, reader, includeFields);
            case RegexpQuery regexpQuery -> extractTermsFromRegex(regexpQuery, terms, reader, includeFields);
            case FuzzyQuery fuzzyQuery -> extractTermsFromFuzzy(fuzzyQuery, terms, reader, includeFields);
            case PrefixQuery prefixQuery -> extractTermsFromPrefix(prefixQuery, terms, reader, includeFields);
            case PhraseQuery phraseQuery -> extractTermsFromPhrase(phraseQuery, terms, includeFields);
            case SpanNearQuery spanNearQuery -> extractTermsFromSpanNear(spanNearQuery, terms, includeFields);
            case TermRangeQuery termRangeQuery ->
                    extractTermsFromTermRange(termRangeQuery, terms, reader, includeFields);
            case DrillDownQuery drillDownQuery ->
                    extractTermsFromDrillDown(drillDownQuery, terms, reader, includeFields);
            case FunctionScoreQuery functionScoreQuery ->
                    extractTerms(functionScoreQuery.getWrappedQuery(), terms, reader, includeFields);
            case null, default -> {
                query.visit(new QueryVisitor() {
                    @Override
                    public void consumeTerms(Query query, Term... termsArray) {
                        for (Term t : termsArray) {
                            if (includeFields) {
                                terms.put(t, query);
                            } else {
                                terms.put(t.text(), query);
                            }
                        }
                    }
                });
            }
        }
    }

    private static void extractTermsFromDrillDown(DrillDownQuery query, Map<Object, Query> terms, IndexReader reader, boolean includeFields) throws IOException {
        // Extract terms from the base (content) query only. Rewriting a DrillDownQuery expands it
        // into a BooleanQuery that also carries the internal dimension-filter clauses (e.g.
        // $facets:kind$para), whose terms don't appear in document text and prevent correct
        // highlight extraction. getBaseQuery() returns the content query directly.
        extractTerms(query.getBaseQuery(), terms, reader, includeFields);
    }

    private static void extractTermsFromBoolean(final BooleanQuery query, final Map<Object, Query> terms, final IndexReader reader, final boolean includeFields) throws IOException {
        for (final BooleanClause clause : query.clauses()) {
            extractTerms(clause.query(), terms, reader, includeFields);
        }
    }

    private static void extractTermsFromTerm(final TermQuery query, final Map<Object, Query> terms, final boolean includeFields) {
        if (includeFields) {
            terms.put(query.getTerm(), query);
        } else {
            terms.put(query.getTerm().text(), query);
        }
    }

    private static void extractTermsFromWildcard(final WildcardQuery query, final Map<Object, Query> terms, final IndexReader reader, final boolean includeFields) throws IOException {
        extractTermsFromMultiTerm(query, terms, reader, includeFields);
    }

    private static void extractTermsFromRegex(final RegexpQuery query, final Map<Object, Query> terms, final IndexReader reader, final boolean includeFields) throws IOException {
        extractTermsFromMultiTerm(query, terms, reader, includeFields);
    }

    private static void extractTermsFromFuzzy(final FuzzyQuery query, final Map<Object, Query> terms, final IndexReader reader, final boolean includeFields) throws IOException {
        extractTermsFromMultiTerm(query, terms, reader, includeFields);
    }

    private static void extractTermsFromPrefix(final PrefixQuery query, final Map<Object, Query> terms, final IndexReader reader, final boolean includeFields) throws IOException {
        extractTermsFromMultiTerm(query, terms, reader, includeFields);
    }

    private static void extractTermsFromPhrase(final PhraseQuery query, final Map<Object, Query> terms, boolean includeFields) {
        final Term[] t = query.getTerms();
        for (final Term t1 : t) {
            if (includeFields) {
                terms.put(t1, query);
            } else {
                terms.put(t1.text(), query);
            }
        }
    }

    /**
     * Extract terms from a near/proximity query (e.g. `<near slop="n">`). Where the query's
     * clauses are simple terms (optionally nested inside further `near` clauses), each term is
     * mapped to the enclosing {@code query} itself, exactly as {@link #extractTermsFromPhrase}
     * does for phrase queries, so that {@link LuceneMatchListener} and {@link PlainTextHighlighter}
     * can recognise the proximity constraint and merge the matched terms into a single highlight
     * span instead of highlighting each term independently (see #833).
     *
     * <p>When the clauses are not simple terms (e.g. wildcard/regex/first clauses), we fall back
     * to the generic per-term extraction so each matched term is still highlighted on its own.</p>
     *
     * @see <a href="https://github.com/eXist-db/exist/issues/833">GitHub issue #833</a>
     */
    private static void extractTermsFromSpanNear(final SpanNearQuery query, final Map<Object, Query> terms, final boolean includeFields) {
        final List<Term> flatTerms = new ArrayList<>();
        if (flattenSpanNearTerms(query, flatTerms)) {
            for (final Term t1 : flatTerms) {
                if (includeFields) {
                    terms.put(t1, query);
                } else {
                    terms.put(t1.text(), query);
                }
            }
        } else {
            query.visit(new QueryVisitor() {
                @Override
                public void consumeTerms(final Query q, final Term... termsArray) {
                    for (final Term t : termsArray) {
                        if (includeFields) {
                            terms.put(t, q);
                        } else {
                            terms.put(t.text(), q);
                        }
                    }
                }
            });
        }
    }

    /**
     * Recursively collect the terms of a near query's clauses, as long as every clause is
     * either a plain term or a nested near query built from plain terms.
     *
     * @return true if all clauses could be flattened into {@code out}
     */
    private static boolean flattenSpanNearTerms(final SpanQuery query, final List<Term> out) {
        if (query instanceof SpanTermQuery termQuery) {
            out.add(termQuery.getTerm());
            return true;
        }
        if (query instanceof SpanNearQuery nearQuery) {
            for (final SpanQuery clause : nearQuery.getClauses()) {
                if (!flattenSpanNearTerms(clause, out)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Ordered term texts of a phrase/near query, together with its slop budget and whether the
     * terms must occur in order, extracted uniformly from {@link PhraseQuery} and
     * {@link SpanNearQuery} so that highlighting code can merge matched terms into a single span
     * regardless of which query form (string syntax or XML `&lt;near&gt;`) produced the match.
     *
     * @see <a href="https://github.com/eXist-db/exist/issues/833">GitHub issue #833</a>
     */
    public record ProximityTerms(List<String> terms, int slop, boolean inOrder) {}

    /**
     * @param query the query mapped to a matched term by {@link #extractTerms}
     * @return the query's proximity term/slop/order data, or {@code null} if {@code query} is not
     *     a phrase or (flattenable) near query
     */
    public static ProximityTerms asProximityTerms(final Query query) {
        if (query instanceof PhraseQuery phraseQuery) {
            final Term[] t = phraseQuery.getTerms();
            final List<String> texts = new ArrayList<>(t.length);
            for (final Term term : t) {
                texts.add(term.text());
            }
            return new ProximityTerms(texts, phraseQuery.getSlop(), true);
        }
        if (query instanceof SpanNearQuery spanNearQuery) {
            final List<Term> flatTerms = new ArrayList<>();
            if (flattenSpanNearTerms(spanNearQuery, flatTerms)) {
                final List<String> texts = new ArrayList<>(flatTerms.size());
                for (final Term term : flatTerms) {
                    texts.add(term.text());
                }
                return new ProximityTerms(texts, spanNearQuery.getSlop(), spanNearQuery.isInOrder());
            }
        }
        return null;
    }

    /**
     * Attempt to complete a proximity match starting at the token stream's current position,
     * which the caller has already matched against one of {@code proximity.terms()} (at index
     * {@code firstMatchedIndex}). Consumes further tokens from {@code stream}, tolerating up to
     * {@code proximity.slop()} tokens that match none of the remaining terms, honouring term
     * order when {@code proximity.inOrder()} is true.
     *
     * <p>On success, returns the captured token states for every matched term, in stream
     * (i.e. offset) order, so callers can merge them into a single highlight span running from
     * the first to the last matched term. Returns {@code null} if the remaining terms could not
     * all be found within the slop budget; some tokens beyond the entry token will still have
     * been consumed from {@code stream} in that case.</p>
     *
     * @see <a href="https://github.com/eXist-db/exist/issues/833">GitHub issue #833</a>
     */
    public static List<AttributeSource.State> matchProximityWindow(final MarkableTokenFilter stream,
            final ProximityTerms proximity, final int firstMatchedIndex) throws IOException {
        final List<String> terms = proximity.terms();
        final List<AttributeSource.State> matched = new ArrayList<>(terms.size());
        matched.add(stream.captureState());
        int budget = proximity.slop();
        if (proximity.inOrder()) {
            int next = firstMatchedIndex + 1;
            while (matched.size() < terms.size() && stream.incrementToken()) {
                final String text = stream.getAttribute(CharTermAttribute.class).toString();
                if (next < terms.size() && text.equals(terms.get(next))) {
                    matched.add(stream.captureState());
                    next++;
                } else if (budget > 0) {
                    budget--;
                } else {
                    break;
                }
            }
        } else {
            final List<String> remaining = new ArrayList<>(terms);
            remaining.remove(firstMatchedIndex);
            while (!remaining.isEmpty() && stream.incrementToken()) {
                final String text = stream.getAttribute(CharTermAttribute.class).toString();
                final int idx = remaining.indexOf(text);
                if (idx >= 0) {
                    remaining.remove(idx);
                    matched.add(stream.captureState());
                } else if (budget > 0) {
                    budget--;
                } else {
                    break;
                }
            }
        }
        return matched.size() == terms.size() ? matched : null;
    }

    private static void extractTermsFromTermRange(final TermRangeQuery query, final Map<Object, Query> terms, final IndexReader reader, boolean includeFields) throws IOException {
        TERM_EXTRACTOR.extractTerms(query, terms, reader, includeFields);
    }

    private static Query rewrite(final MultiTermQuery query, final IndexReader reader) throws IOException {
        return query.rewrite(new IndexSearcher(reader));
    }

    private static void extractTermsFromMultiTerm(final MultiTermQuery query, final Map<Object, Query> termsMap, final IndexReader reader, final boolean includeFields) throws IOException {
        TERM_EXTRACTOR.extractTerms(query, termsMap, reader, includeFields);
    }

    private static final MultiTermExtractor TERM_EXTRACTOR = new MultiTermExtractor();

    /**
     * A class for extracting MultiTerms (all of them).
     * Subclassing MultiTermQuery.RewriteMethod
     * to gain access to its protected method getTermsEnum
     */
    private static class MultiTermExtractor extends MultiTermQuery.RewriteMethod {

        public void extractTerms(final MultiTermQuery query, final Map<Object, Query> termsMap, final IndexReader reader, final boolean includeFields) throws IOException {
            final IndexReaderContext topReaderContext = reader.getContext();
            for (final LeafReaderContext context : topReaderContext.leaves()) {
                final Terms terms = context.reader().terms(query.getField());
                if (terms == null) {
                    // field does not exist
                    continue;
                }

                final TermsEnum termsEnum = query.getTermsEnum(terms);
                assert termsEnum != null;

                if (termsEnum == TermsEnum.EMPTY) {
                    continue;
                }

                BytesRef bytes;
                while ((bytes = termsEnum.next()) != null) {
                    final Term term = new Term(query.getField(), BytesRef.deepCopyOf(bytes));
                    if (includeFields) {
                        termsMap.put(term, query);
                    } else {
                        termsMap.put(term.text(), query);
                    }
                }
            }
        }

        @Override
        public Query rewrite(final IndexSearcher searcher, final MultiTermQuery query) throws IOException {
            throw new UnsupportedOperationException();
        }
    }
}
