package org.exist.indexing.lucene;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.util.BytesRef;
import org.exist.dom.QName;
import org.exist.dom.SymbolTable;
import org.exist.numbering.NodeId;
import org.exist.storage.BrokerPool;
import org.exist.util.ByteConversion;

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
        final byte[] buf = new byte[1024];
        BytesRef ref = new BytesRef(buf);
        nodeIdValues.get(doc, ref);
        int units = ByteConversion.byteToShort(ref.bytes, ref.offset);
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
        short localNameId = symbols.getSymbol(qname.getLocalName());
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
            QName qname = new QName(localName, namespaceURI, "");
            qname.setNameType(type);
            return qname;
        } catch (NumberFormatException e) {
            return null;
        }
    }

	public static String[] extractFields(Query query, IndexReader reader) throws IOException {
		Map<Object, Query> map = new TreeMap<Object, Query>();
		extractTerms(query, map, reader, true);
		Set<String> fields = new TreeSet<String>();
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
            Set<Term> tempSet = new TreeSet<Term>();
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
        for (int i = 0; i < clauses.length; i++) {
            extractTerms(clauses[i].getQuery(), terms, reader, includeFields);
        }
    }

    private static void extractTermsFromTerm(TermQuery query, Map<Object, Query> terms, boolean includeFields) {
    	if (includeFields)
    		terms.put(query.getTerm(), query);
    	else
    		terms.put(query.getTerm().text(), query);
    }

    private static void extractTermsFromWildcard(WildcardQuery query, Map<Object, Query> terms, IndexReader reader, boolean includeFields) throws IOException {
        extractTerms(rewrite(query, reader), terms, reader, includeFields);
    }

    private static void extractTermsFromRegex(RegexpQuery query, Map<Object, Query> terms, IndexReader reader, boolean includeFields) throws IOException {
        extractTerms(rewrite(query, reader), terms, reader, includeFields);
    }

    private static void extractTermsFromFuzzy(FuzzyQuery query, Map<Object, Query> terms, IndexReader reader, boolean includeFields) throws IOException {
        extractTerms(query.rewrite(reader), terms, reader, includeFields);
    }

    private static void extractTermsFromPrefix(PrefixQuery query, Map<Object, Query> terms, IndexReader reader, boolean includeFields) throws IOException {
    	extractTerms(rewrite(query, reader), terms, reader, includeFields);
    }

    private static void extractTermsFromPhrase(PhraseQuery query, Map<Object, Query> terms, boolean includeFields) {
        Term[] t = query.getTerms();
        for (int i = 0; i < t.length; i++) {
        	if (includeFields)
        		terms.put(t[i], query);
        	else
        		terms.put(t[i].text(), query);
        }
    }

    private static Query rewrite(MultiTermQuery query, IndexReader reader) throws IOException {
        query.setRewriteMethod(MultiTermQuery.CONSTANT_SCORE_AUTO_REWRITE_DEFAULT);
        return query.rewrite(reader);
    }
}
