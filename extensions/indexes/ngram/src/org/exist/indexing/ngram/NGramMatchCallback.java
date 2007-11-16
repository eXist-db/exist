package org.exist.indexing.ngram;

import org.exist.dom.NodeProxy;
import org.exist.util.serializer.Receiver;
import org.exist.xquery.XPathException;
import org.xml.sax.SAXException;

/**
 * Callback interface used by the NGram {@link org.exist.indexing.MatchListener} to report matching
 * text sequences. Pass to
 * {@link NGramIndexWorker#getMatchListener(org.exist.dom.NodeProxy, NGramMatchCallback)}
 * to get informed of matches.
 */
public interface NGramMatchCallback {

    /**
     * Called by the NGram {@link org.exist.indexing.MatchListener} whenever it encounters
     * a match object while traversing the node tree.
     *
     * @param receiver the receiver to which the MatchListener is currently writing.
     * @param matchingText the matching text sequence
     * @param node the text node containing the match
     */
    public void match(Receiver receiver, String matchingText, NodeProxy node) throws XPathException, SAXException;
}
