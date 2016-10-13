/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.indexing.lucene;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

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

    public static byte[] createId(int docId, NodeId nodeId) {
        // build id from nodeId and docId
        byte[] data = new byte[nodeId.size() + 4];
        ByteConversion.intToByteH(docId, data, 0);
        nodeId.serialize(data, 4);

        return data;
    }

    public static byte[] createId(NodeId nodeId) {
        byte[] data = new byte[nodeId.size()];
        nodeId.serialize(data, 0);
        return data;
    }

    public static NodeId readNodeId(int doc, BinaryDocValues nodeIdValues, BrokerPool pool) {
        final BytesRef ref = nodeIdValues.get(doc);
        final int units = ByteConversion.byteToShort(ref.bytes, ref.offset);
        return pool.getNodeFactory().createFromData(units, ref.bytes, ref.offset + 2);
    }

    /**
     * Encode an element or attribute qname into a lucene field name using the
     * internal ids for namespace and local name.
     *
     * @param qname
     * @return encoded qname
     */
    public static String encodeQName(QName qname, SymbolTable symbols) {
        short namespaceId = symbols.getNSSymbol(qname.getNamespaceURI());
        short localNameId = symbols.getSymbol(qname.getLocalPart());
        long nameId = qname.getNameType() | (namespaceId & 0xFFFF) << 16 | (localNameId & 0xFFFFFFFFL) << 32;
        return Long.toHexString(nameId);
    }

    /**
     * Decode the lucene field name into an element or attribute qname.
     *
     * @param s
     * @return the qname
     */
    public static QName decodeQName(String s, SymbolTable symbols) {
        try {
            long l = Long.parseLong(s, 16);
            short namespaceId = (short) ((l >>> 16) & 0xFFFFL);
            short localNameId = (short) ((l >>> 32) & 0xFFFFL);
            byte type = (byte) (l & 0xFFL);
            String namespaceURI = symbols.getNamespace(namespaceId);
            String localName = symbols.getName(localNameId);
            return new QName(localName, namespaceURI, XMLConstants.DEFAULT_NS_PREFIX, type);
        } catch (NumberFormatException e) {
            return null;
        }
    }

	public static String[] extractFields(Query query, IndexReader reader) throws IOException {
		Map<Object, Query> map = new TreeMap<>();
		extractTerms(query, map, reader, true);
		Set<String> fields = new TreeSet<>();
		for (Object term : map.keySet()) {
			fields.add(((Term)term).field());
		}
		String[] fieldArray = new String[fields.size()];
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
     * @param query
     * @param terms
     * @throws IOException in case of an error
     * @throws UnsupportedOperationException in case of an error
     */
    public static void extractTerms(Query query, Map<Object, Query> terms, IndexReader reader, boolean includeFields) throws IOException, UnsupportedOperationException {
        if (query instanceof BooleanQuery)
            extractTermsFromBoolean((BooleanQuery)query, terms, reader, includeFields);
        else if (query instanceof TermQuery)
            extractTermsFromTerm((TermQuery) query, terms, includeFields);
        else if (query instanceof WildcardQuery)
            extractTermsFromWildcard((WildcardQuery) query,terms, reader, includeFields);
        else if (query instanceof RegexpQuery)
        	extractTermsFromRegex((RegexpQuery) query, terms, reader, includeFields);
        else if (query instanceof FuzzyQuery)
            extractTermsFromFuzzy((FuzzyQuery) query, terms, reader, includeFields);
        else if (query instanceof PrefixQuery)
            extractTermsFromPrefix((PrefixQuery) query, terms, reader, includeFields);
        else if (query instanceof PhraseQuery)
            extractTermsFromPhrase((PhraseQuery) query, terms, includeFields);
        else {
            // fallback to Lucene's Query.extractTerms if none of the
            // above matches
            Set<Term> tempSet = new TreeSet<>();
            query.extractTerms(tempSet);
            for (Term t : tempSet) {
            	if (includeFields)
            		terms.put(t, query);
            	else
            		terms.put(t.text(), query);
            }
        }
    }

    private static void extractTermsFromBoolean(BooleanQuery query, Map<Object, Query> terms, IndexReader reader, boolean includeFields) throws IOException {
        BooleanClause clauses[] = query.getClauses();
        for (BooleanClause clause : clauses) {
            extractTerms(clause.getQuery(), terms, reader, includeFields);
        }
    }

    private static void extractTermsFromTerm(TermQuery query, Map<Object, Query> terms, boolean includeFields) {
    	if (includeFields)
    		terms.put(query.getTerm(), query);
    	else
    		terms.put(query.getTerm().text(), query);
    }

    private static void extractTermsFromWildcard(WildcardQuery query, Map<Object, Query> terms, IndexReader reader, boolean includeFields) throws IOException {
        extractTermsFromMultiTerm(query, terms, reader, includeFields);
    }

    private static void extractTermsFromRegex(RegexpQuery query, Map<Object, Query> terms, IndexReader reader, boolean includeFields) throws IOException {
        extractTermsFromMultiTerm(query, terms, reader, includeFields);
    }

    private static void extractTermsFromFuzzy(FuzzyQuery query, Map<Object, Query> terms, IndexReader reader, boolean includeFields) throws IOException {
        extractTermsFromMultiTerm(query, terms, reader, includeFields);
    }

    private static void extractTermsFromPrefix(PrefixQuery query, Map<Object, Query> terms, IndexReader reader, boolean includeFields) throws IOException {
        extractTermsFromMultiTerm(query, terms, reader, includeFields);
    }

    private static void extractTermsFromPhrase(PhraseQuery query, Map<Object, Query> terms, boolean includeFields) {
        Term[] t = query.getTerms();
        for (Term t1 : t) {
            if (includeFields) {
                terms.put(t1, query);
            } else {
                terms.put(t1.text(), query);
            }
        }
    }

    private static Query rewrite(MultiTermQuery query, IndexReader reader) throws IOException {
        query.setRewriteMethod(MultiTermQuery.CONSTANT_SCORE_AUTO_REWRITE_DEFAULT);
        return query.rewrite(reader);
    }

    private static void extractTermsFromMultiTerm(MultiTermQuery query, Map<Object, Query> termsMap, IndexReader reader, boolean includeFields) throws IOException {
        TERM_EXTRACTOR.extractTerms(query, termsMap, reader, includeFields);
    }

    private static final MultiTermExtractor TERM_EXTRACTOR = new MultiTermExtractor();

    /*
     * A class for extracting MultiTerms (all of them).
     * Subclassing MultiTermQuery.RewriteMethod
     * to gain access to its protected method getTermsEnum
     */
    private static class MultiTermExtractor extends MultiTermQuery.RewriteMethod {

        public void extractTerms(MultiTermQuery query, Map<Object, Query> termsMap, IndexReader reader, boolean includeFields) throws IOException {
            IndexReaderContext topReaderContext = reader.getContext();
            for (AtomicReaderContext context : topReaderContext.leaves()) {
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

                TermsEnum termsEnum = getTermsEnum(query, terms, new AttributeSource());
                assert termsEnum != null;

                if (termsEnum == TermsEnum.EMPTY) {
                    continue;
		}

                BytesRef bytes;
                while ((bytes = termsEnum.next()) != null) {
                    Term term = new Term(query.getField(), BytesRef.deepCopyOf(bytes));
                    if (includeFields) {
                        termsMap.put(term, query);
                    } else {
                        termsMap.put(term.text(), query);
		    }
                }
            }
        }

        @Override
        public Query rewrite(IndexReader reader, MultiTermQuery query) throws IOException {
            throw new UnsupportedOperationException();
        }
    };

}
