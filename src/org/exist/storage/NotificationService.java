package org.exist.storage;

import org.apache.log4j.Logger;
import org.exist.dom.DocumentImpl;
import org.exist.dom.StoredNode;
import org.exist.numbering.NodeId;

import java.util.IdentityHashMap;
import java.util.Iterator;

/**
 * Global notification service for document updates. Other classes
 * can subscribe to this service to be notified of document modifications,
 * removals or additions.
 * 
 * @author wolf
 *
 */
public class NotificationService extends IdentityHashMap {

	private final static Logger LOG = Logger.getLogger(NotificationService.class);
	
	public NotificationService() {
		super();
	}
	
	/**
	 * Subscribe an {@link UpdateListener} to receive notifications.
	 * 
	 * @param listener
	 */
	public synchronized void subscribe(UpdateListener listener) {
		put(listener, new Object());
	}
	
	/**
	 * Unsubscribe an {@link UpdateListener}.
	 * 
	 * @param listener
	 */
	public synchronized void unsubscribe(UpdateListener listener) {
		Object i = remove(listener);
		if (i == null)
			throw new RuntimeException(hashCode() + " listener not found: " + listener.hashCode());
        listener.unsubscribe();
    }

	/**
	 * Notify all subscribers that a document has been updated/removed or
	 * a new document has been added.
	 * 
	 * @param document
	 * @param event
	 */
	public synchronized void notifyUpdate(DocumentImpl document, int event) {
		UpdateListener listener;
		for (Iterator i = keySet().iterator(); i.hasNext(); ) {
	        listener = (UpdateListener) i.next();
	        listener.documentUpdated(document, event);
		}
	}

    /**
	 * Notify all subscribers that a node has been moved. Nodes may be moved during a
     * defragmentation run.
	 */
	public synchronized void notifyMove(NodeId oldNodeId, StoredNode newNode) {
		UpdateListener listener;
		for (Iterator i = keySet().iterator(); i.hasNext(); ) {
	        listener = (UpdateListener) i.next();
	        listener.nodeMoved(oldNodeId, newNode);
		}
	}

    public void debug() {
		LOG.debug("Registered UpdateListeners:");
		UpdateListener listener;
		for (Iterator i = keySet().iterator(); i.hasNext(); ) {
	        listener = (UpdateListener) i.next();
	        listener.debug();
		}
	}
}
