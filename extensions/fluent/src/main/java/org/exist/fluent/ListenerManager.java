package org.exist.fluent;

import java.lang.ref.*;
import java.util.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.collections.Collection;
import org.exist.collections.triggers.CollectionTrigger;
import org.exist.collections.triggers.DocumentTrigger;
import org.exist.collections.triggers.SAXTrigger;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;

/**
 * Internal class not for public use; needs to be public due to external instantiation requirements.
 * Mediates between native eXist triggers and db listeners.
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class ListenerManager {
	
	static String getTriggerConfigXml() {
		return "<triggers><trigger class='org.exist.fluent.ListenerManager$TriggerDispatcher'/></triggers>";
	}
	
	static class EventKey implements Comparable<EventKey> {
		final String path;
		final Trigger trigger;
		EventKey(String path, Trigger trigger) {
			this.path = Database.normalizePath(path);
			this.trigger = trigger;
		}
		EventKey(String path, int existEventCode, boolean before) {
			this(path, toTrigger(existEventCode, before));
		}
		private static Trigger toTrigger(int code, boolean before) {
			switch(code) {
				case org.exist.collections.triggers.Trigger.STORE_DOCUMENT_EVENT:
					return before ? Trigger.BEFORE_STORE : Trigger.AFTER_STORE;
				case org.exist.collections.triggers.Trigger.CREATE_COLLECTION_EVENT:
					return before ? Trigger.BEFORE_CREATE : Trigger.AFTER_CREATE;
				case org.exist.collections.triggers.Trigger.UPDATE_DOCUMENT_EVENT:
					return before ? Trigger.BEFORE_UPDATE : Trigger.AFTER_UPDATE;
				case org.exist.collections.triggers.Trigger.RENAME_DOCUMENT_EVENT:
				case org.exist.collections.triggers.Trigger.RENAME_COLLECTION_EVENT:
					return before ? Trigger.BEFORE_RENAME : Trigger.AFTER_RENAME;
				case org.exist.collections.triggers.Trigger.MOVE_DOCUMENT_EVENT:
				case org.exist.collections.triggers.Trigger.MOVE_COLLECTION_EVENT:
					return before ? Trigger.BEFORE_MOVE : Trigger.AFTER_MOVE;
				case org.exist.collections.triggers.Trigger.REMOVE_DOCUMENT_EVENT:
				case org.exist.collections.triggers.Trigger.REMOVE_COLLECTION_EVENT:
					return before ? Trigger.BEFORE_REMOVE : Trigger.AFTER_REMOVE;
				default:
					throw new IllegalArgumentException("unknown exist trigger code " + code);
			}
		}
		@Override
		public boolean equals(Object o) {
			if (o instanceof EventKey) {
				EventKey that = (EventKey) o;
				return
					path.equals(that.path) &&
					trigger == that.trigger; 
			}
			return false;
		}
		@Override
		public int hashCode() {
			return path.hashCode() * 37 + trigger.hashCode();
		}
		public int compareTo(EventKey that) {
			int r = this.trigger.compareTo(that.trigger);
			if (r == 0) r = that.path.compareTo(this.path);	// reverse order on purpose
			return r;
		}
		boolean matchesAsPrefix(EventKey that) {
			return
				this.trigger == that.trigger &&
				that.path.startsWith(this.path) &&
				(this.path.equals("/") || this.path.length() == that.path.length() || that.path.charAt(this.path.length()) == '/');
		}
	}
	
	private static class ListenerWrapper {
		final Reference<Listener> refListener;
		final Resource origin;
		ListenerWrapper(Listener listener, Resource origin) {
			this.refListener = new WeakReference<Listener>(listener);
			this.origin = origin;
		}
		private Document wrap(DocumentImpl doc) {
			return doc == null ? null : Document.newInstance(doc, origin);
		}
		private Folder wrap(org.exist.collections.Collection col) {
			if (col == null) return null;
			return new Folder(col.getURI().getCollectionPath(), false, origin);
		}
		boolean sameOrNull(Listener listener) {
			Listener x = refListener.get();
			return x == null || x == listener;
		}
		boolean isAlive() {
			return refListener.get() != null;
		}
		void fireDocumentEvent(EventKey key, DocumentImpl doc) {
			Listener listener = refListener.get();
			if (listener instanceof Document.Listener) ((Document.Listener) listener).handle(new Document.Event(key, wrap(doc)));
		}
		void fireFolderEvent(EventKey key, Collection col) {
			Listener listener = refListener.get();
			if (listener instanceof Folder.Listener) ((Folder.Listener) listener).handle(new Folder.Event(key, wrap(col)));
		}
	}
	
	enum Depth {
		/**
		 * Targets matching the given path exactly.
		 */
		ZERO,
		/**
		 * Targets one level below the given path (i.e. inside the given folder).
		 */
		ONE,
		/**
		 * Targets matching or at any level below the given path.
		 */
		MANY}

	private final Map<EventKey,List<ListenerWrapper>>[] listenerMaps;
	
	@SuppressWarnings("unchecked")
	private ListenerManager() {
		listenerMaps = new Map[Depth.values().length];
		listenerMaps[Depth.ZERO.ordinal()] = Collections.synchronizedMap(new HashMap<EventKey,List<ListenerWrapper>>());
		listenerMaps[Depth.ONE.ordinal()] = Collections.synchronizedMap(new HashMap<EventKey,List<ListenerWrapper>>());
		listenerMaps[Depth.MANY.ordinal()] = Collections.synchronizedSortedMap(new TreeMap<EventKey,List<ListenerWrapper>>());
	}
	
	private void checkListenerType(Listener listener) {
		if (!(listener instanceof Document.Listener || listener instanceof Folder.Listener))
			throw new IllegalArgumentException("invalid listener type " + listener.getClass().getName());
	}
	
	void add(String pathPrefix, Depth depth, Set<Trigger> triggers, Listener listener, Resource origin) {
		checkListenerType(listener);
		if (triggers.isEmpty()) throw new IllegalArgumentException("cannot add listener with empty set of triggers");
		for (Trigger trigger : triggers) {
			EventKey key = new EventKey(pathPrefix, trigger);
			List<ListenerWrapper> list;
			synchronized(listenerMaps[depth.ordinal()]) {
				list = listenerMaps[depth.ordinal()].get(key);
				if (list == null) {
					list = new LinkedList<ListenerWrapper>();
					listenerMaps[depth.ordinal()].put(key, list);
				}
			}
			synchronized(list) {
				list.add(new ListenerWrapper(listener, origin));
			}
		}
	}

	void remove(String pathPrefix, Depth depth, Listener listener) {
		remove(pathPrefix, listenerMaps[depth.ordinal()], listener);
	}
	
	void remove(Listener listener) {
		for (Map<EventKey,List<ListenerWrapper>> map : listenerMaps) remove(null, map, listener);
	}
	
	private void remove(String path, Map<EventKey,List<ListenerWrapper>> map, Listener listener) {
		checkListenerType(listener);
		synchronized(map) {
			for (Iterator<Map.Entry<EventKey,List<ListenerWrapper>>> it = map.entrySet().iterator(); it.hasNext(); ) {
				Map.Entry<EventKey,List<ListenerWrapper>> entry = it.next();
				if (path != null && !entry.getKey().path.equals(path)) continue;
				synchronized(entry.getValue()) {
					for (Iterator<ListenerWrapper> it2 = entry.getValue().iterator(); it2.hasNext(); ) {
						if (it2.next().sameOrNull(listener)) it2.remove();
					}
				}
				if (entry.getValue().isEmpty()) it.remove();
			}
		}
	}
	
	void fire(EventKey key, DocumentImpl doc) {
		fire(key, doc, null, true);
	}
		
	void fire(EventKey key, org.exist.collections.Collection col) {
		fire(key, null, col, false);
	}
		
	private void fire(EventKey key, DocumentImpl doc, org.exist.collections.Collection col, boolean documentEvent) {
		fire(listenerMaps[Depth.ZERO.ordinal()].get(key), key, doc, col, documentEvent);
		
		int k = key.path.lastIndexOf('/');
		assert k != -1;
		if (k > 0) {
			EventKey trimmedKey = new EventKey(key.path.substring(0, k), key.trigger);
			fire(listenerMaps[Depth.ONE.ordinal()].get(trimmedKey), key, doc, col, documentEvent);
		}
		
		SortedMap<EventKey,List<ListenerWrapper>> map =
				(SortedMap<EventKey,List<ListenerWrapper>>) listenerMaps[Depth.MANY.ordinal()];
		SortedMap<EventKey,List<ListenerWrapper>> tailMap;
		synchronized(map) {
			tailMap = new TreeMap<EventKey,List<ListenerWrapper>>(map.tailMap(key));
		}
		for (Map.Entry<EventKey,List<ListenerWrapper>> entry : tailMap.entrySet()) {
			EventKey target = entry.getKey();
			if (!target.matchesAsPrefix(key)) break;
			fire(entry.getValue(), key, doc, col, documentEvent);
		}
	}
	
	private void fire(List<ListenerWrapper> list, EventKey key, DocumentImpl doc, org.exist.collections.Collection col, boolean documentEvent) {
		if (list == null) return;
		List<ListenerWrapper> listCopy;
		synchronized(list) {
			for (Iterator<ListenerWrapper> it = list.iterator(); it.hasNext(); ) {
				if (!it.next().isAlive()) it.remove();
			}
			listCopy = new ArrayList<ListenerWrapper>(list);
		}
		if (documentEvent) {
			for (ListenerWrapper wrap : listCopy) wrap.fireDocumentEvent(key, doc);
		} else {
			for (ListenerWrapper wrap : listCopy) wrap.fireFolderEvent(key, col);
		}
	}

	static final ListenerManager INSTANCE = new ListenerManager();

	/**
	 * A centralized trigger listener for eXist that dispatches back to the singleton
	 * <code>ListenerManager</code>.  Public only because it needs to be instantiated
	 * via reflection; for internal use only.
	 *
	 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
	 */
	public static class TriggerDispatcher extends SAXTrigger implements DocumentTrigger, CollectionTrigger {
		private static final Logger LOG = LogManager.getLogger(TriggerDispatcher.class);
		
		public void configure(DBBroker broker, org.exist.collections.Collection parent, Map<String, List<? extends Object>> parameters)  {
			// nothing to do
		}
		public Logger getLogger() {
			return LOG;
		}

		@Override
		public void beforeCreateCollection(DBBroker broker, Txn transaction, XmldbURI uri) throws TriggerException {
			EventKey key = new EventKey(uri.toString(), Trigger.BEFORE_CREATE);
			INSTANCE.fire(key, null, null, false);
		}
		@Override
		public void afterCreateCollection(DBBroker broker, Txn transaction, Collection collection) throws TriggerException {
			EventKey key = new EventKey(collection.getURI().toString(), Trigger.AFTER_CREATE);
			INSTANCE.fire(key, null, collection, false);
		}
		@Override
		public void beforeCopyCollection(DBBroker broker, Txn transaction, Collection collection, XmldbURI newUri) throws TriggerException {
			EventKey key = new EventKey(newUri.toString(), Trigger.BEFORE_CREATE);
			INSTANCE.fire(key, null, null, false);
		}
		@Override
		public void afterCopyCollection(DBBroker broker, Txn transaction, Collection collection, XmldbURI newUri) throws TriggerException {
			EventKey key = new EventKey(newUri.toString(), Trigger.AFTER_CREATE);
			INSTANCE.fire(key, null, collection, false);
		}
		@Override
		public void beforeMoveCollection(DBBroker broker, Txn transaction, Collection collection, XmldbURI newUri) throws TriggerException {
			EventKey key = new EventKey(collection.getURI().toString(), Trigger.BEFORE_MOVE);
			INSTANCE.fire(key, null, collection, false);
		}
		@Override
		public void afterMoveCollection(DBBroker broker, Txn transaction, Collection collection, XmldbURI newUri) throws TriggerException {
			EventKey key = new EventKey(collection.getURI().toString(), Trigger.AFTER_MOVE);
			INSTANCE.fire(key, null, collection, false);
		}
		@Override
		public void beforeDeleteCollection(DBBroker broker, Txn transaction, Collection collection) throws TriggerException {
			EventKey key = new EventKey(collection.getURI().toString(), Trigger.BEFORE_REMOVE);
			INSTANCE.fire(key, null, collection, false);
		}
		@Override
		public void afterDeleteCollection(DBBroker broker, Txn transaction, XmldbURI uri) throws TriggerException {
			EventKey key = new EventKey(uri.toString(), Trigger.AFTER_REMOVE);
			INSTANCE.fire(key, (DocumentImpl)null, null, false);
		}
		@Override
		public void beforeCreateDocument(DBBroker broker, Txn transaction, XmldbURI uri) throws TriggerException {
			EventKey key = new EventKey(uri.toString(), Trigger.BEFORE_CREATE);
			INSTANCE.fire(key, (DocumentImpl)null, (org.exist.collections.Collection)null, true);
		}
		@Override
		public void afterCreateDocument(DBBroker broker, Txn transaction, DocumentImpl document) { //throws TriggerException {
			EventKey key = new EventKey(document.getURI().toString(), Trigger.AFTER_CREATE);
			INSTANCE.fire(key, document, null, true);
		}
		@Override
		public void beforeUpdateDocument(DBBroker broker, Txn transaction, DocumentImpl document) throws TriggerException {
			EventKey key = new EventKey(document.getURI().toString(), Trigger.BEFORE_UPDATE);
			INSTANCE.fire(key, document, null, true);
		}
		@Override
		public void afterUpdateDocument(DBBroker broker, Txn transaction, DocumentImpl document) { //throws TriggerException {
			EventKey key = new EventKey(document.getURI().toString(), Trigger.AFTER_UPDATE);
			INSTANCE.fire(key, document, null, true);
			
		}
		@Override
		public void beforeCopyDocument(DBBroker broker, Txn transaction, DocumentImpl document, XmldbURI newUri) throws TriggerException {
			EventKey key = new EventKey(newUri.toString(), Trigger.BEFORE_CREATE);
			INSTANCE.fire(key, document, null, true);
		}
		@Override
		public void afterCopyDocument(DBBroker broker, Txn transaction, DocumentImpl document, XmldbURI newUri) { //throws TriggerException {
			EventKey key = new EventKey(document.getURI().toString(), Trigger.AFTER_CREATE);
			INSTANCE.fire(key, document, null, true);
		}
		@Override
		public void beforeMoveDocument(DBBroker broker, Txn transaction, DocumentImpl document, XmldbURI newUri) throws TriggerException {
			EventKey key = new EventKey(document.getURI().toString(), Trigger.BEFORE_RENAME);
			INSTANCE.fire(key, document, null, true);
		}
		@Override
		public void afterMoveDocument(DBBroker broker, Txn transaction, DocumentImpl document, XmldbURI newUri) { //throws TriggerException {
			EventKey key = new EventKey(document.getURI().toString(), Trigger.AFTER_RENAME);
			INSTANCE.fire(key, document, null, true);
		}
		@Override
		public void beforeDeleteDocument(DBBroker broker, Txn transaction, DocumentImpl document) throws TriggerException {
			EventKey key = new EventKey(document.getURI().toString(), Trigger.BEFORE_REMOVE);
			INSTANCE.fire(key, document, null, true);
		}
		@Override
		public void afterDeleteDocument(DBBroker broker, Txn transaction, XmldbURI uri) { //throws TriggerException {
			EventKey key = new EventKey(uri.toString(), Trigger.AFTER_REMOVE);
			INSTANCE.fire(key, null, null, true);
		}

		@Override
		public void beforeUpdateDocumentMetadata(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException {
                    EventKey key = new EventKey(document.getURI().toString(), Trigger.BEFORE_UPDATE_META);
                    INSTANCE.fire(key, document, null, true);
		}
		
		@Override
		public void afterUpdateDocumentMetadata(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException {
                    EventKey key = new EventKey(document.getURI().toString(), Trigger.AFTER_UPDATE_META);
                    INSTANCE.fire(key, document, null, true);
		}
	}
}
