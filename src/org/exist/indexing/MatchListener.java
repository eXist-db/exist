package org.exist.indexing;

import org.exist.util.serializer.Receiver;

/**
 * Highlight matches in query results. Indexes can implement
 * this interface to filter the output produced by the serializer
 * when serializing query results. See
 * {@link org.exist.indexing.IndexWorker#getMatchListener(org.exist.dom.NodeProxy)}.
 * The interface basically extends {@link org.exist.util.serializer.Receiver}. The
 * additional methods are used to chain multiple MatchListeners. Implementations should
 * forward all events to the next receiver in the chain (if there is one).
 * Class {@link org.exist.indexing.AbstractMatchListener} provides default implementations
 * for all methods.
 */
public interface MatchListener extends Receiver {

    /**
     * Register the next receiver in the chain. All
     * events should be forwarded to this.
     *
     * @param next the next receiver in the chain.
     */
    void setNextInChain(Receiver next);

    /**
     * Returns the next receiver in the chain.
     * @return the next receiver
     */
    Receiver getNextInChain();

    /**
     * Walks the chain and returns the final receiver.
     * @return the last receiver in the chain
     */
    Receiver getLastInChain();
}