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
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.lucene.facet.DrillDownQuery;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.Fields;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
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

    public static final String FIELD_DOC_ID = "docId";
    public static final String FIELD_DOC_URI = "docUri";

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

    public static NodeId readNodeId(final int doc, final BinaryDocValues nodeIdValues, final BrokerPool pool) {
        final BytesRef ref = nodeIdValues.get(doc);
        final int units = ByteConversion.byteToShort(ref.bytes, ref.offset);
        return pool.getNodeFactory().createFromData(units, ref.bytes, ref.offset + 2);
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
            case TermRangeQuery termRangeQuery ->
                    extractTermsFromTermRange(termRangeQuery, terms, reader, includeFields);
            case DrillDownQuery drillDownQuery ->
                    extractTermsFromDrillDown(drillDownQuery, terms, reader, includeFields);
            case null, default -> {
                // fallback to Lucene's Query.extractTerms if none of the
                // above matches
                final Set<Term> tempSet = new TreeSet<>();
                query.extractTerms(tempSet);
                for (final Term t : tempSet) {
                    if (includeFields) {
                        terms.put(t, query);
                    } else {
                        terms.put(t.text(), query);
                    }
                }
            }
        }
    }

    private static void extractTermsFromDrillDown(DrillDownQuery query, Map<Object, Query> terms, IndexReader reader, boolean includeFields) throws IOException {
        final Query rewritten = query.rewrite(reader);
        extractTerms(rewritten, terms, reader, includeFields);
    }

    private static void extractTermsFromBoolean(final BooleanQuery query, final Map<Object, Query> terms, final IndexReader reader, final boolean includeFields) throws IOException {
        final BooleanClause clauses[] = query.getClauses();
        for (final BooleanClause clause : clauses) {
            extractTerms(clause.getQuery(), terms, reader, includeFields);
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

    private static void extractTermsFromTermRange(final TermRangeQuery query, final Map<Object, Query> terms, final IndexReader reader, boolean includeFields) throws IOException {
        TERM_EXTRACTOR.extractTerms(query, terms, reader, includeFields);
    }

    private static Query rewrite(final MultiTermQuery query, final IndexReader reader) throws IOException {
        query.setRewriteMethod(MultiTermQuery.CONSTANT_SCORE_AUTO_REWRITE_DEFAULT);
        return query.rewrite(reader);
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
            for (final AtomicReaderContext context : topReaderContext.leaves()) {
                final Fields fields = context.reader().fields();
                if (fields == null) {
                    // reader has no fields
                    continue;
                }

                final Terms terms = fields.terms(query.getField());
                if (terms == null) {
                    // field does not exist
                    continue;
                }

                final TermsEnum termsEnum = getTermsEnum(query, terms, new AttributeSource());
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
        public Query rewrite(final IndexReader reader, final MultiTermQuery query) throws IOException {
            throw new UnsupportedOperationException();
        }
    }
}
