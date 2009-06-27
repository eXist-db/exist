package org.exist.indexing.lucene;

import org.apache.lucene.search.Query;

public class TermInPhrase implements Comparable {

    private Query query;
    private String term;

    public TermInPhrase(Query query, String term) {
        this.query = query;
        this.term = term;
    }

    public boolean equals(Object obj) {
        return term.equals(((TermInPhrase)obj).term);
    }

    public int compareTo(Object obj) {
        return term.compareTo(((TermInPhrase) obj).term);
    }
}
