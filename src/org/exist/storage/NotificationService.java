package org.exist.storage;

import java.util.IdentityHashMap;
import java.util.Iterator;

import org.apache.log4j.Logger;
import org.exist.dom.DocumentImpl;

/**
 * Global notification service for document updates. Other classes
 * can subscribe to this service to be notified of document modifications,
 * removals or additions.
 * 
 * @author wolf
 *
 */
public class NotificationService extends IdentityHashMap {

	private final static Object DUMMY_VALUE = new Object();
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
		LOG.debug(hashCode() + " adding listener: " + listener.hashCode());
		put(listener, new Object());
		debug();
	}
	
	/**
	 * Unsubscribe an {@link UpdateListener}.
	 * 
	 * @param listener
	 */
	public synchronized void unsubscribe(UpdateListener listener) {
		debug();
		Object i = remove(listener);
		if (i == null)
			throw new RuntimeException(hashCode() + " listener not found: " + listener.hashCode());
		else
			LOG.debug("Removed listener: " + listener.hashCode());
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
	
	public void debug() {
		LOG.debug("Registered UpdateListeners:");
		UpdateListener listener;
		for (Iterator i = keySet().iterator(); i.hasNext(); ) {
	        listener = (UpdateListener) i.next();
	        listener.debug();
		}
	}
}
