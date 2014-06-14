package org.exist.indexing.lucene;

import org.apache.lucene.search.Query;

public class TermInPhrase implements Comparable<TermInPhrase> {

    @SuppressWarnings("unused")
    private final Query query;
    private final String term;

    public TermInPhrase(Query query, String term) {
        this.query = query;
        this.term = term;
    }

    // DW: missing hashCode() ?

    @Override
    public boolean equals(Object obj) {
        // DW: parameter 'obj' is not checked for type.
        return term.equals(((TermInPhrase)obj).term);
    }

    public int compareTo(TermInPhrase obj) {
        return term.compareTo(obj.term);
    }
}
