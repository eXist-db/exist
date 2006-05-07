package org.exist.storage;

import org.exist.dom.DocumentImpl;

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
	 * @param document
	 * @param event
	 */
	public void documentUpdated(DocumentImpl document, int event);
	
	public void debug();
}