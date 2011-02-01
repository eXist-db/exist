package org.exist.indexing.ngram;

import org.exist.dom.Match;
import org.exist.numbering.NodeId;

public class NGramMatch extends Match {

    public NGramMatch(int contextId, NodeId nodeId, String matchTerm) {
        super(contextId, nodeId, matchTerm);
    }

    public NGramMatch(int contextId, NodeId nodeId, String matchTerm, int frequency) {
        super(contextId, nodeId, matchTerm, frequency);
    }

    public NGramMatch(Match match) {
        super(match);
    }

    @Override
    public Match createInstance(int contextId, NodeId nodeId, String matchTerm) {
        return new NGramMatch(contextId, nodeId, matchTerm);
    }

    @Override
    public Match newCopy() {
        return new NGramMatch(this);
    }

    @Override
    public String getIndexId() {
        return org.exist.indexing.ngram.NGramIndex.ID;
    }
}