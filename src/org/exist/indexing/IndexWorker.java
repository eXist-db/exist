package org.exist.indexing;

import org.w3c.dom.NodeList;
import org.exist.dom.DocumentImpl;
import org.exist.util.DatabaseConfigurationException;

import java.util.Map;

/**
 * Provide concurrent access to an index structure. Implements the core operations on the index.
 * The methods in this class are used in a multi-threaded environment.
 * {@link org.exist.indexing.Index#getWorker()} should
 * thus return a new IndexWorker whenever it is  called. Implementations of IndexWorker have
 * to take care of synchronizing access to shared resources.
 */
public interface IndexWorker {

    String getIndexId();
    
    Object configure(NodeList configNodes, Map namespaces) throws DatabaseConfigurationException;

    void flush();

    StreamListener getListener(DocumentImpl document);
}