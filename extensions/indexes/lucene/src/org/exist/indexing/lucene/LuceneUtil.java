package org.exist.indexing.lucene;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.lucene.document.Field;
import org.apache.lucene.document.Fieldable;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.search.regex.RegexQuery;

public class LuceneUtil {

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
     */
    public static void extractTerms(Query query, Map<Object, Query> terms, IndexReader reader, boolean includeFields) throws IOException {
        if (query instanceof BooleanQuery)
            extractTermsFromBoolean((BooleanQuery)query, terms, reader, includeFields);
        else if (query instanceof TermQuery)
            extractTermsFromTerm((TermQuery) query, terms, includeFields);
        else if (query instanceof WildcardQuery)
            extractTermsFromWildcard((WildcardQuery) query,terms, reader, includeFields);
        else if (query instanceof RegexQuery)
        	extractTermsFromRegex((RegexQuery) query, terms, reader, includeFields);
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

    private static void extractTermsFromRegex(RegexQuery query, Map<Object, Query> terms, IndexReader reader, boolean includeFields) throws IOException {
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
        query.setRewriteMethod(MultiTermQuery.SCORING_BOOLEAN_QUERY_REWRITE);
        return query.rewrite(reader);
    }
}
