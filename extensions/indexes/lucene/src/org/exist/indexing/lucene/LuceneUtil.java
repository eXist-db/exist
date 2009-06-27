package org.exist.indexing.lucene;

import org.apache.lucene.search.*;
import org.apache.lucene.index.Term;

import java.util.*;

public class LuceneUtil {

    public static Map extractTerms(Query query) {
        Map terms = new TreeMap();
        extractTerms(query, terms);
        return terms;
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
     */
    public static void extractTerms(Query query, Map terms) {
        if (query instanceof BooleanQuery)
            extractTermsFromBoolean((BooleanQuery)query, terms);
        else if (query instanceof TermQuery)
            extractTermsFromTerm((TermQuery) query, terms);
        else if (query instanceof WildcardQuery)
            extractTermsFromWildcard((WildcardQuery) query, terms);
        else if (query instanceof FuzzyQuery)
            extractTermsFromFuzzy((FuzzyQuery) query, terms);
        else if (query instanceof PrefixQuery)
            extractTermsFromPrefix((PrefixQuery) query, terms);
        else if (query instanceof PhraseQuery)
            extractTermsFromPhrase((PhraseQuery) query, terms);
        else {
            // fallback to Lucene's Query.extractTerms if none of the
            // above matches
            Set tempSet = new TreeSet();
            query.extractTerms(tempSet);
            for (Iterator i = tempSet.iterator(); i.hasNext();) {
                Term t = (Term) i.next();
                terms.put(t.text(), query);
            }
        }
    }

    private static void extractTermsFromBoolean(BooleanQuery query, Map terms) {
        BooleanClause clauses[] = query.getClauses();
        for (int i = 0; i < clauses.length; i++) {
            extractTerms(clauses[i].getQuery(), terms);
        }
    }

    private static void extractTermsFromTerm(TermQuery query, Map terms) {
        terms.put(query.getTerm().text(), query);
    }

    private static void extractTermsFromWildcard(WildcardQuery query, Map terms) {
        terms.put(query.getTerm().text(), query);
    }

    private static void extractTermsFromFuzzy(FuzzyQuery query, Map terms) {
        terms.put(query.getTerm().text(), query);
    }

    private static void extractTermsFromPrefix(PrefixQuery query, Map terms) {
        terms.put(query.getPrefix().text(), query);
    }

    private static void extractTermsFromPhrase(PhraseQuery query, Map terms) {
        Term[] t = query.getTerms();
        for (int i = 0; i < t.length; i++) {
            terms.put(t[i].text(), query);
        }
    }
}
