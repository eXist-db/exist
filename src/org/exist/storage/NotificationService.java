package org.exist.storage;

import org.exist.dom.DocumentImpl;
import org.exist.util.hashtable.Object2LongIdentityHashMap;

/**
 * Global notification service for document updates. Other classes
 * can subscribe to this service to be notified of document modifications,
 * removals or additions.
 * 
 * @author wolf
 *
 */
public class NotificationService extends Object2LongIdentityHashMap {

	private final static long DUMMY_VALUE = 0;
	
	public NotificationService() {
		super();
	}
	
	/**
	 * Subscribe an {@link UpdateListener} to receive notifications.
	 * 
	 * @param listener
	 */
	public synchronized void subscribe(UpdateListener listener) {
		put(listener, DUMMY_VALUE);
	}
	
	/**
	 * Unsubscribe an {@link UpdateListener}.
	 * 
	 * @param listener
	 */
	public synchronized void unsubscribe(UpdateListener listener) {
		remove(listener);
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
		for(int idx = 0; idx < tabSize; idx++) {
	        if(keys[idx] == null || keys[idx] == REMOVED)
	            continue;
	        listener = (UpdateListener) keys[idx];
	        listener.documentUpdated(document, event);
		}
	}
}
