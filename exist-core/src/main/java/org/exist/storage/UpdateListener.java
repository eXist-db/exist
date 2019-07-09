package org.exist.storage;

import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.NodeHandle;
import org.exist.numbering.NodeId;

public interface UpdateListener {

	// Notification types:
	
	/** Notification type: a document was added to a collection */
	public final static int ADD = 0;
	
	/** Notification type: a document has been updated */
	public final static int UPDATE = 1;
	
	/** Notification type: a document was removed */
	public final static int REMOVE = 2;
	
	/**
	 * Called whenever a document is updated within the database.
	 * Parameter event specifies the event type, i.e. one of {@link #ADD}, {@link #UPDATE} 
	 * or {@link #REMOVE}.
	 * 
	 * @param document updated document
	 * @param event update event
	 */
	public void documentUpdated(DocumentImpl document, int event);

    /**
     * nodeMoved is called after a defragmentation run occurred for a document during which
     * the address and the nodeId of a node may have changed. Defragmentation
     * may only occur after a node update.
     *
     * @param oldNodeId the oold node id
     * @param newNode the new node the old node was move to
     */
    public void nodeMoved(NodeId oldNodeId, NodeHandle newNode);

    /**
     * Called when the listener is removed from the notification service
     */
    public void unsubscribe();

    public void debug();
}