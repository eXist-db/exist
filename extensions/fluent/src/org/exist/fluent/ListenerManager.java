package org.exist.fluent;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.*;

import org.apache.log4j.Logger;
import org.exist.collections.*;
import org.exist.collections.Collection;
import org.exist.collections.triggers.*;
import org.exist.dom.DocumentImpl;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;
import org.jmock.Mock;
import org.jmock.core.*;
import org.xml.sax.*;
import org.xml.sax.ext.LexicalHandler;

/**
 * Internal class not for public use; needs to be public due to external instantiation requirements.
 * Mediates between native eXist triggers and db listeners.
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class ListenerManager {

	static void configureTriggerDispatcher(Database db) {
		Folder rootConfigFolder = db.createFolder(CollectionConfigurationManager.CONFIG_COLLECTION + Database.ROOT_PREFIX);
		rootConfigFolder.namespaceBindings().put("", CollectionConfiguration.NAMESPACE);
		boolean hasTrigger = rootConfigFolder.documents().query().exists(
				"//trigger[" +
				"	@event='store update remove create-collection rename-collection delete-collection' and" +
				"	@class='org.exist.fluent.ListenerManager$TriggerDispatcher']");
		if (!hasTrigger) {
			org.exist.fluent.Node triggers = rootConfigFolder.documents().query().optional("//triggers").node();
			if (!triggers.extant()) {
				org.exist.fluent.Node configRoot = rootConfigFolder.documents().query().optional("/collection").node();
				if (!configRoot.extant()) {
					rootConfigFolder.documents().build(Name.create(CollectionConfiguration.DEFAULT_COLLECTION_CONFIG_FILE))
						.elem("collection").elem("triggers").end("triggers").end("collection").commit();
				} else {
					configRoot.append().elem("triggers").end("triggers").commit();
				}
				triggers = rootConfigFolder.documents().query().single("//triggers").node();
			}
			triggers.append().elem("trigger")
				.attr("event", "store update remove create-collection rename-collection delete-collection")
				.attr("class", "org.exist.fluent.ListenerManager$TriggerDispatcher")
			.end("trigger").commit();
		}
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
				case org.exist.collections.triggers.Trigger.CREATE_COLLECTION_EVENT:
					return before ? Trigger.BEFORE_CREATE : Trigger.AFTER_CREATE;
				case org.exist.collections.triggers.Trigger.UPDATE_DOCUMENT_EVENT:
				case org.exist.collections.triggers.Trigger.RENAME_COLLECTION_EVENT:
					return before ? Trigger.BEFORE_UPDATE : Trigger.AFTER_UPDATE;
				case org.exist.collections.triggers.Trigger.REMOVE_DOCUMENT_EVENT:
				case org.exist.collections.triggers.Trigger.DELETE_COLLECTION_EVENT:
					return before ? Trigger.BEFORE_DELETE : Trigger.AFTER_DELETE;
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
				(this.path.length() == that.path.length() || that.path.charAt(this.path.length()) == '/');
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
		boolean fireDocumentEvent(EventKey key, DocumentImpl doc) {
			Listener listener = refListener.get();
			if (listener == null) return false;
			if (listener instanceof Document.Listener) ((Document.Listener) listener).handle(new Document.Event(key, wrap(doc)));
			return true;
		}
		boolean fireFolderEvent(EventKey key, Collection col) {
			Listener listener = refListener.get();
			if (listener == null) return false;
			if (listener instanceof Folder.Listener) ((Folder.Listener) listener).handle(new Folder.Event(key, wrap(col)));
			return true;
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
		listenerMaps[Depth.ZERO.ordinal()] = new HashMap<EventKey,List<ListenerWrapper>>();
		listenerMaps[Depth.ONE.ordinal()] = new HashMap<EventKey,List<ListenerWrapper>>();
		listenerMaps[Depth.MANY.ordinal()] = new TreeMap<EventKey,List<ListenerWrapper>>();
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
			List<ListenerWrapper> list = listenerMaps[depth.ordinal()].get(key);
			if (list == null) {
				list = new LinkedList<ListenerWrapper>();
				listenerMaps[depth.ordinal()].put(key, list);
			}
			list.add(new ListenerWrapper(listener, origin));
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
		for (Iterator<Map.Entry<EventKey,List<ListenerWrapper>>> it = map.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<EventKey,List<ListenerWrapper>> entry = it.next();
			if (path != null && !entry.getKey().path.equals(path)) continue;
			for (Iterator<ListenerWrapper> it2 = entry.getValue().iterator(); it2.hasNext(); ) {
				if (it2.next().sameOrNull(listener)) it2.remove();
			}
			if (entry.getValue().isEmpty()) it.remove();
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
		
		for (Map.Entry<EventKey,List<ListenerWrapper>> entry :
				((SortedMap<EventKey,List<ListenerWrapper>>) listenerMaps[Depth.MANY.ordinal()]).tailMap(key).entrySet()) {
			EventKey target = entry.getKey();
			if (!target.matchesAsPrefix(key)) break;
			fire(entry.getValue(), key, doc, col, documentEvent);
		}
	}
	
	private void fire(List<ListenerWrapper> list, EventKey key, DocumentImpl doc, org.exist.collections.Collection col, boolean documentEvent) {
		if (list == null) return;
		for (Iterator<ListenerWrapper> it = list.iterator(); it.hasNext(); ) {
			ListenerWrapper wrap = it.next();
			boolean wrapOK = documentEvent
				? wrap.fireDocumentEvent(key, doc)
				: wrap.fireFolderEvent(key, col);
			if (!wrapOK) it.remove();
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
	public static class TriggerDispatcher implements DocumentTrigger, CollectionTrigger {
		private static final Logger LOG = Logger.getLogger(TriggerDispatcher.class);
		private boolean validating;
		private ContentHandler contentHandler;
		private LexicalHandler lexicalHandler;
		
		public void configure(DBBroker broker, org.exist.collections.Collection parent, Map parameters) throws CollectionConfigurationException {
			// nothing to do
		}
		public void prepare(int event, DBBroker broker, Txn txn, XmldbURI documentName, DocumentImpl existingDocument) throws TriggerException {
			EventKey key = new EventKey(documentName.getCollectionPath(), event, true);
			INSTANCE.fire(key, existingDocument);
		}
		public void finish(int event, DBBroker broker, Txn txn, DocumentImpl document) {
			EventKey key = new EventKey(document.getURI().getCollectionPath(), event, false);
			INSTANCE.fire(key, key.trigger == Trigger.AFTER_DELETE ? null : document);
		}
		public void prepare(int event, DBBroker broker, Txn txn, org.exist.collections.Collection collection, String newName) throws TriggerException {
			EventKey key = new EventKey(newName, event, true);
			INSTANCE.fire(key, collection);
		}
		public void finish(int event, DBBroker broker, Txn txn, org.exist.collections.Collection collection, String newName) {
			EventKey key = new EventKey(newName, event, false);
			INSTANCE.fire(key, key.trigger == Trigger.AFTER_DELETE ? null : collection);
		}
		public boolean isValidating() {
			return validating;
		}
		public void setValidating(boolean validating) {
			this.validating = validating;
		}
		public void setOutputHandler(ContentHandler handler) {
			this.contentHandler = handler;
		}
		public void setLexicalOutputHandler(LexicalHandler handler) {
			this.lexicalHandler = handler;
		}
		public ContentHandler getOutputHandler() {
			return contentHandler;
		}
		public ContentHandler getInputHandler() {
			return this;
		}
		public LexicalHandler getLexicalOutputHandler() {
			return lexicalHandler;
		}
		public LexicalHandler getLexicalInputHandler() {
			return this;
		}
		public Logger getLogger() {
			return LOG;
		}
		public void characters(char[] ch, int start, int length) throws SAXException {
			contentHandler.characters(ch, start, length);
		}
		public void endDocument() throws SAXException {
			contentHandler.endDocument();
		}
		public void endElement(String uri, String localName, String qName) throws SAXException {
			contentHandler.endElement(uri, localName, qName);
		}
		public void endPrefixMapping(String prefix) throws SAXException {
			contentHandler.endPrefixMapping(prefix);
		}
		public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
			contentHandler.ignorableWhitespace(ch, start, length);
		}
		public void processingInstruction(String target, String data) throws SAXException {
			contentHandler.processingInstruction(target, data);
		}
		public void setDocumentLocator(Locator locator) {
			contentHandler.setDocumentLocator(locator);
		}
		public void skippedEntity(String name) throws SAXException {
			contentHandler.skippedEntity(name);
		}
		public void startDocument() throws SAXException {
			contentHandler.startDocument();
		}
		public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
			contentHandler.startElement(uri, localName, qName, atts);
		}
		public void startPrefixMapping(String prefix, String uri) throws SAXException {
			contentHandler.startPrefixMapping(prefix, uri);
		}
		public void comment(char[] ch, int start, int length) throws SAXException {
			lexicalHandler.comment(ch, start, length);
		}
		public void endCDATA() throws SAXException {
			lexicalHandler.endCDATA();
		}
		public void endDTD() throws SAXException {
			lexicalHandler.endDTD();
		}
		public void endEntity(String name) throws SAXException {
			lexicalHandler.endEntity(name);
		}
		public void startCDATA() throws SAXException {
			lexicalHandler.startCDATA();
		}
		public void startDTD(String name, String publicId, String systemId) throws SAXException {
			lexicalHandler.startDTD(name, publicId, systemId);
		}
		public void startEntity(String name) throws SAXException {
			lexicalHandler.startEntity(name);
		}
		
	}
	
	/**
	 * @deprecated Test class that should not be javadoc'ed.
	 */
	@Deprecated
	public static class Test extends Database.DatabaseTest {
		private interface BothListener extends Document.Listener, Folder.Listener {/*nothing to add*/}
		
		private Mock documentListener, folderListener, bothListener;
		private Document.Listener documentListenerProxy;
		private Folder.Listener folderListenerProxy;
		private BothListener bothListenerProxy;
		
		@Override
		protected void setUp() {
			super.setUp();
			
			documentListener = mock(Document.Listener.class);
			folderListener = mock(Folder.Listener.class);
			bothListener = mock(BothListener.class);
			
			documentListenerProxy = (Document.Listener) documentListener.proxy();
			folderListenerProxy = (Folder.Listener) folderListener.proxy();
			bothListenerProxy = (BothListener) bothListener.proxy();
		}
		
		@Override
		protected void tearDown() throws Exception {
			super.tearDown();
			INSTANCE.remove(documentListenerProxy);
			INSTANCE.remove(folderListenerProxy);
			INSTANCE.remove(bothListenerProxy);
			documentListener = null;			folderListener = null;			bothListener = null;
			documentListenerProxy = null;	folderListenerProxy = null;	bothListenerProxy = null;
		}
		
		private Constraint eqDelayedDoc(final Document.Event ev) {
			return new Constraint() {
				public StringBuffer describeTo(StringBuffer buf) {
					return buf.append("eqDelayedDoc(").append(ev).append(")");
				}
				public boolean eval(Object o) {
					return new Document.Event(ev.trigger, ev.path, db.getDocument(ev.path)).equals(o);
				}
			};
		}
		
		private Stub checkDocumentExists(final String path, final boolean shouldExist) {
			return new Stub() {
				public StringBuffer describeTo(StringBuffer buf) {
					return buf.append("check that document '" + path + "' " + (shouldExist ? "exists" : "does not exist"));
				}
				public Object invoke(Invocation inv) throws Throwable {
					try {
						db.getDocument(path);
						if (!shouldExist) fail("document '" + path + "' exists but shouldn't");
					} catch (DatabaseException e) {
						if (shouldExist) fail("document '" + path + "' doesn't exist but should");
					}
					return null;
				}
			};
		}
		
		private Stub checkFolderExists(final String path, final boolean shouldExist) {
			return new Stub() {
				public StringBuffer describeTo(StringBuffer buf) {
					return buf.append("check that folder '" + path + "' " + (shouldExist ? "exists" : "does not exist"));
				}
				public Object invoke(Invocation inv) throws Throwable {
					try {
						db.getFolder(path);
						if (!shouldExist) fail("folder '" + path + "' exists but shouldn't");
					} catch (DatabaseException e) {
						if (shouldExist) fail("folder '" + path + "' doesn't exist but should");
					}
					return null;
				}
			};
		}
		
		private Stub checkDocumentStamp(final String expectedStamp) {
			return new Stub() {
				public StringBuffer describeTo(StringBuffer buf) {
					buf.append("check that event document is stamped with '" + expectedStamp + "'");
					return buf;
				}
				public Object invoke(Invocation inv) throws Throwable {
					XMLDocument doc = ((Document.Event) inv.parameterValues.get(0)).document.xml();
					assertNotNull("event document is null", doc);
					assertEquals(expectedStamp, doc.query().single("/test/@stamp").value());
					return null;
				}
			};
		}
		
		private XMLDocument createDocument(String path) {
			return createDocument(path, null);
		}
		
		private XMLDocument createDocument(String path, String stamp) {
			int k = path.lastIndexOf('/');
			assert k > 0;
			Folder folder = db.createFolder(path.substring(0, k));
			return folder.documents().build(Name.overwrite(path.substring(k+1)))
				.elem("test").attrIf(stamp != null, "stamp", stamp).end("test").commit();
		}
		
		public void testListenDocumentsBeforeCreateDocument1() {
			final String docPath = "/top/test.xml";
			Document.Event ev = new Document.Event(Trigger.BEFORE_CREATE, docPath, null);
			Folder top = db.createFolder("/top");
			documentListener.expects(once()).method("handle").with(eq(ev)).will(checkDocumentExists(docPath, false));
			top.documents().listeners().add(Trigger.BEFORE_CREATE, documentListenerProxy);
			createDocument(docPath);
			createDocument("/elsewhere/test.xml");
			createDocument("/top/deeper/test.xml");
		}

		public void testListenDocumentsBeforeCreateDocument2() {
			final String docPath = "/top/test.xml";
			Folder top = db.createFolder("/top");
			documentListener.expects(never()).method("handle").with(ANYTHING);
			top.documents().listeners().add(Trigger.BEFORE_CREATE, documentListenerProxy);
			top.documents().listeners().remove(documentListenerProxy);
			createDocument(docPath);
		}

		public void testListenDocumentsAfterCreateDocument1() {
			final String docPath = "/top/test.xml";
			XMLDocument doc = createDocument(docPath);
			Document.Event ev = new Document.Event(Trigger.AFTER_CREATE, docPath, doc);
			doc.delete();
			Folder top = db.createFolder("/top");
			documentListener.expects(once()).method("handle").with(eqDelayedDoc(ev)).will(checkDocumentExists(docPath, true));
			top.documents().listeners().add(Trigger.AFTER_CREATE, documentListenerProxy);
			createDocument(docPath);
			createDocument("/elsewhere/test.xml");
			createDocument("/top/deeper/test.xml");
		}

		public void testListenDocumentsAfterCreateDocument2() {
			final String docPath = "/top/test.xml";
			Folder top = db.createFolder("/top");
			documentListener.expects(never()).method("handle").with(ANYTHING);
			top.documents().listeners().add(Trigger.AFTER_CREATE, documentListenerProxy);
			top.documents().listeners().remove(documentListenerProxy);
			createDocument(docPath);
		}

		public void testListenFolderBeforeCreateDocument1() {
			final String docPath = "/top/test2.xml";
			Document.Event ev = new Document.Event(Trigger.BEFORE_CREATE, docPath, null);
			Folder top = db.createFolder("/top");
			documentListener.expects(once()).method("handle").with(eq(ev)).will(checkDocumentExists(docPath, false));
			top.listeners().add(Trigger.BEFORE_CREATE, documentListenerProxy);
			createDocument(docPath);
			createDocument("/elsewhere/test.xml");
		}

		public void testListenFolderBeforeCreateDocument2() {
			final String docPath = "/top/test2.xml";
			Folder top = db.createFolder("/top");
			documentListener.expects(never()).method("handle").with(ANYTHING);
			top.listeners().add(Trigger.BEFORE_CREATE, documentListenerProxy);
			top.listeners().remove(documentListenerProxy);
			createDocument(docPath);
		}

		public void testListenFolderAfterCreateDocument1() {
			final String docPath = "/top/test2.xml";
			XMLDocument doc = createDocument(docPath);
			Document.Event ev = new Document.Event(Trigger.AFTER_CREATE, docPath, doc);
			doc.delete();
			Folder top = db.createFolder("/top");
			documentListener.expects(once()).method("handle").with(eqDelayedDoc(ev)).will(checkDocumentExists(docPath, true));
			top.listeners().add(Trigger.AFTER_CREATE, documentListenerProxy);
			createDocument(docPath);
			createDocument("/elsewhere/test.xml");
		}
		
		public void testListenFolderAfterCreateDocument2() {
			final String docPath = "/top/test2.xml";
			Folder top = db.createFolder("/top");
			documentListener.expects(never()).method("handle").with(ANYTHING);
			top.listeners().add(Trigger.AFTER_CREATE, documentListenerProxy);
			top.listeners().remove(documentListenerProxy);
			createDocument(docPath);
		}

		public void testListenDocumentsBeforeUpdateDocument1() {
			final String docPath = "/top/test.xml";
			XMLDocument doc = createDocument(docPath, "before");
			Document.Event ev = new Document.Event(Trigger.BEFORE_UPDATE, docPath, doc);
			Folder top = db.createFolder("/top");
			documentListener.expects(once()).method("handle").with(eq(ev)).will(checkDocumentStamp("before"));
			top.documents().listeners().add(Trigger.BEFORE_UPDATE, documentListenerProxy);
			createDocument(docPath, "after");
			createDocument("/elsewhere/test.xml");
			createDocument("/top/deeper/test.xml");
		}

		public void testListenDocumentsAfterUpdateDocument1() {
			final String docPath = "/top/test.xml";
			XMLDocument doc = createDocument(docPath, "before");
			Document.Event ev = new Document.Event(Trigger.AFTER_UPDATE, docPath, doc);
			Folder top = db.createFolder("/top");
			documentListener.expects(once()).method("handle").with(eq(ev)).will(checkDocumentStamp("after"));
			top.documents().listeners().add(Trigger.AFTER_UPDATE, documentListenerProxy);
			createDocument(docPath, "after");
			createDocument("/elsewhere/test.xml");
			createDocument("/top/deeper/test.xml");
		}

		public void testListenFolderBeforeUpdateDocument1() {
			final String docPath = "/top/test.xml";
			XMLDocument doc = createDocument(docPath, "before");
			Document.Event ev = new Document.Event(Trigger.BEFORE_UPDATE, docPath, doc);
			Folder top = db.createFolder("/top");
			documentListener.expects(once()).method("handle").with(eq(ev)).will(checkDocumentStamp("before"));
			top.listeners().add(Trigger.BEFORE_UPDATE, documentListenerProxy);
			createDocument(docPath, "after");
			createDocument("/elsewhere/test.xml");
		}

		public void testListenFolderAfterUpdateDocument1() {
			final String docPath = "/top/test.xml";
			XMLDocument doc = createDocument(docPath, "before");
			Document.Event ev = new Document.Event(Trigger.AFTER_UPDATE, docPath, doc);
			Folder top = db.createFolder("/top");
			documentListener.expects(once()).method("handle").with(eq(ev)).will(checkDocumentStamp("after"));
			top.listeners().add(Trigger.AFTER_UPDATE, documentListenerProxy);
			createDocument(docPath, "after");
			createDocument("/elsewhere/test.xml");
		}
		
		public void testListenFolderDeepBeforeCreateDocument1() {
			final String docPath = "/top/middle/test2.xml";
			Document.Event ev = new Document.Event(Trigger.BEFORE_CREATE, docPath, null);
			Folder top = db.createFolder("/top");
			documentListener.expects(once()).method("handle").with(eq(ev)).will(checkDocumentExists(docPath, false));
			top.listeners().add(Trigger.BEFORE_CREATE, documentListenerProxy);
			createDocument(docPath);
			createDocument("/elsewhere/test.xml");
		}

		public void testListenDocumentBeforeUpdateDocument1() {
			final String docPath = "/top/test.xml";
			XMLDocument doc = createDocument(docPath, "before");
			Document.Event ev = new Document.Event(Trigger.BEFORE_UPDATE, docPath, doc);
			documentListener.expects(once()).method("handle").with(eq(ev)).will(checkDocumentStamp("before"));
			doc.listeners().add(Trigger.BEFORE_UPDATE, documentListenerProxy);
			createDocument(docPath, "after");
			createDocument("/elsewhere/test.xml");
			createDocument("/top/test2.xml");
		}

		public void testListenDocumentAfterUpdateDocument1() {
			final String docPath = "/top/test.xml";
			XMLDocument doc = createDocument(docPath, "before");
			Document.Event ev = new Document.Event(Trigger.AFTER_UPDATE, docPath, doc);
			documentListener.expects(once()).method("handle").with(eq(ev)).will(checkDocumentStamp("after"));
			doc.listeners().add(Trigger.AFTER_UPDATE, documentListenerProxy);
			createDocument(docPath, "after");
			createDocument("/elsewhere/test.xml");
			createDocument("/top/test2.xml");
		}

		public void testListenDocumentsBeforeDeleteDocument1() {
			final String docPath = "/top/test.xml";
			XMLDocument doc = createDocument(docPath);
			Document.Event ev = new Document.Event(Trigger.BEFORE_DELETE, docPath, doc);
			documentListener.expects(once()).method("handle").with(eq(ev)).will(checkDocumentExists(docPath, true));
			Folder top = db.createFolder("/top");
			top.documents().listeners().add(Trigger.BEFORE_DELETE, documentListenerProxy);
			doc.delete();
			createDocument("/elsewhere/test.xml").delete();
			createDocument("/top/deeper/test.xml").delete();
		}

		public void testListenDocumentsBeforeDeleteDocument2() {
			final String docPath = "/top/test.xml";
			XMLDocument doc = createDocument(docPath, "before");
			doc.delete();
			Document.Event ev = new Document.Event(Trigger.BEFORE_DELETE, docPath, doc);
			documentListener.expects(once()).method("handle").with(eqDelayedDoc(ev)).will(checkDocumentExists(docPath, true));
			Folder top = db.createFolder("/top");
			top.documents().listeners().add(Trigger.BEFORE_DELETE, documentListenerProxy);
			createDocument(docPath).delete();
			createDocument("/elsewhere/test.xml").delete();
			createDocument("/top/deeper/test.xml").delete();
		}

		public void testListenDocumentsAfterDeleteDocument1() {
			final String docPath = "/top/test.xml";
			Document.Event ev = new Document.Event(Trigger.AFTER_DELETE, docPath, null);
			documentListener.expects(once()).method("handle").with(eq(ev)).will(checkDocumentExists(docPath, false));
			Folder top = db.createFolder("/top");
			top.documents().listeners().add(Trigger.AFTER_DELETE, documentListenerProxy);
			createDocument(docPath).delete();
			createDocument("/elsewhere/test.xml").delete();
			createDocument("/top/deeper/test.xml").delete();
		}

		public void testListenFolderBeforeDeleteDocument1() {
			final String docPath = "/top/test.xml";
			XMLDocument doc = createDocument(docPath);
			Document.Event ev = new Document.Event(Trigger.BEFORE_DELETE, docPath, doc);
			documentListener.expects(once()).method("handle").with(eq(ev)).will(checkDocumentExists(docPath, true));
			Folder top = db.createFolder("/top");
			top.listeners().add(Trigger.BEFORE_DELETE, documentListenerProxy);
			doc.delete();
			createDocument("/elsewhere/test.xml").delete();
		}

		public void testListenFolderBeforeDeleteDocument2() {
			final String docPath = "/top/deeper/test.xml";
			XMLDocument doc = createDocument(docPath);
			Document.Event ev = new Document.Event(Trigger.BEFORE_DELETE, docPath, doc);
			documentListener.expects(once()).method("handle").with(eq(ev)).will(checkDocumentExists(docPath, true));
			Folder top = db.createFolder("/top");
			top.listeners().add(Trigger.BEFORE_DELETE, documentListenerProxy);
			doc.delete();
			createDocument("/elsewhere/test.xml").delete();
		}

		public void testListenFolderAfterDeleteDocument1() {
			final String docPath = "/top/test.xml";
			XMLDocument doc = createDocument(docPath);
			Document.Event ev = new Document.Event(Trigger.AFTER_DELETE, docPath, null);
			documentListener.expects(once()).method("handle").with(eq(ev)).will(checkDocumentExists(docPath, false));
			Folder top = db.createFolder("/top");
			top.listeners().add(Trigger.AFTER_DELETE, documentListenerProxy);
			doc.delete();
			createDocument("/elsewhere/test.xml").delete();
		}

		public void testListenFolderAfterDeleteDocument2() {
			final String docPath = "/top/deeper/test.xml";
			XMLDocument doc = createDocument(docPath);
			Document.Event ev = new Document.Event(Trigger.AFTER_DELETE, docPath, null);
			documentListener.expects(once()).method("handle").with(eq(ev)).will(checkDocumentExists(docPath, false));
			Folder top = db.createFolder("/top");
			top.listeners().add(Trigger.AFTER_DELETE, documentListenerProxy);
			doc.delete();
			createDocument("/elsewhere/test.xml").delete();
		}

		public void testListenDocumentBeforeDeleteDocument1() {
			final String docPath = "/top/test.xml";
			XMLDocument doc = createDocument(docPath);
			Document.Event ev = new Document.Event(Trigger.BEFORE_DELETE, docPath, doc);
			documentListener.expects(once()).method("handle").with(eq(ev)).will(checkDocumentExists(docPath, true));
			doc.listeners().add(Trigger.BEFORE_DELETE, documentListenerProxy);
			doc.delete();
			createDocument("/elsewhere/test.xml").delete();
			createDocument("/top/deeper/test.xml").delete();
			createDocument("/top/test2.xml").delete();
		}

		public void testListenDocumentAfterDeleteDocument1() {
			final String docPath = "/top/test.xml";
			XMLDocument doc = createDocument(docPath);
			Document.Event ev = new Document.Event(Trigger.AFTER_DELETE, docPath, null);
			documentListener.expects(once()).method("handle").with(eq(ev)).will(checkDocumentExists(docPath, false));
			doc.listeners().add(Trigger.AFTER_DELETE, documentListenerProxy);
			doc.delete();
			createDocument("/elsewhere/test.xml").delete();
			createDocument("/top/deeper/test.xml").delete();
			createDocument("/top/test2.xml").delete();
		}

		public void bugtestListenBeforeCreateFolder1() {
			final String folderPath = "/top/child";
			Folder.Event ev = new Folder.Event(Trigger.BEFORE_CREATE, folderPath, null);
			folderListener.expects(once()).method("handle").with(eq(ev)).will(checkFolderExists(folderPath, false));
			Folder top = db.createFolder("/top");
			top.listeners().add(Trigger.BEFORE_CREATE, folderListenerProxy);
			top.children().create("child");
		}

		public void bugtestListenBeforeCreateFolder2() {
			final String folderPath = "/top/middle/child";
			Folder.Event ev = new Folder.Event(Trigger.BEFORE_CREATE, folderPath, null);
			folderListener.expects(once()).method("handle").with(eq(ev)).will(checkFolderExists(folderPath, false));
			Folder top = db.createFolder("/top");
			Folder middle = db.createFolder("/top/middle");
			top.listeners().add(Trigger.BEFORE_CREATE, folderListenerProxy);
			middle.children().create("child");
		}

	}
}
