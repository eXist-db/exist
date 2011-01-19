package org.exist.indexing.lucene;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

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
    public static void extractTerms(Query query, Map<String, Query> terms, IndexReader reader) throws IOException {
        if (query instanceof BooleanQuery)
            extractTermsFromBoolean((BooleanQuery)query, terms, reader);
        else if (query instanceof TermQuery)
            extractTermsFromTerm((TermQuery) query, terms);
        else if (query instanceof WildcardQuery)
            extractTermsFromWildcard((WildcardQuery) query,terms, reader);
        else if (query instanceof RegexQuery)
        	extractTermsFromRegex((RegexQuery) query, terms, reader);
        else if (query instanceof FuzzyQuery)
            extractTermsFromFuzzy((FuzzyQuery) query, terms, reader);
        else if (query instanceof PrefixQuery)
            extractTermsFromPrefix((PrefixQuery) query, terms, reader);
        else if (query instanceof PhraseQuery)
            extractTermsFromPhrase((PhraseQuery) query, terms);
        else {
            // fallback to Lucene's Query.extractTerms if none of the
            // above matches
            Set<Term> tempSet = new TreeSet<Term>();
            query.extractTerms(tempSet);
            for (Term t : tempSet) {
                terms.put(t.text(), query);
            }
        }
    }

    private static void extractTermsFromBoolean(BooleanQuery query, Map<String, Query> terms, IndexReader reader) throws IOException {
        BooleanClause clauses[] = query.getClauses();
        for (int i = 0; i < clauses.length; i++) {
            extractTerms(clauses[i].getQuery(), terms, reader);
        }
    }

    private static void extractTermsFromTerm(TermQuery query, Map<String, Query> terms) {
        terms.put(query.getTerm().text(), query);
    }

    private static void extractTermsFromWildcard(WildcardQuery query, Map<String, Query> terms, IndexReader reader) throws IOException {
        extractTerms(rewrite(query, reader), terms, reader);
    }

    private static void extractTermsFromRegex(RegexQuery query, Map<String, Query> terms, IndexReader reader) throws IOException {
        extractTerms(rewrite(query, reader), terms, reader);
    }

    private static void extractTermsFromFuzzy(FuzzyQuery query, Map<String, Query> terms, IndexReader reader) throws IOException {
        extractTerms(query.rewrite(reader), terms, reader);
    }

    private static void extractTermsFromPrefix(PrefixQuery query, Map<String, Query> terms, IndexReader reader) throws IOException {
    	extractTerms(rewrite(query, reader), terms, reader);
    }

    private static void extractTermsFromPhrase(PhraseQuery query, Map<String, Query> terms) {
        Term[] t = query.getTerms();
        for (int i = 0; i < t.length; i++) {
            terms.put(t[i].text(), query);
        }
    }

    private static Query rewrite(MultiTermQuery query, IndexReader reader) throws IOException {
        query.setRewriteMethod(MultiTermQuery.SCORING_BOOLEAN_QUERY_REWRITE);
        return query.rewrite(reader);
    }
}
