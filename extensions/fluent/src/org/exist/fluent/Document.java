package org.exist.fluent;

import java.io.*;
import java.util.EnumSet;
import java.util.Set;

import org.exist.dom.*;
import org.exist.storage.DBBroker;
import org.exist.xquery.value.Sequence;

/**
 * A document from the database, either binary or XML.  Note that querying a non-XML
 * document is harmless, but will never return any results.
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class Document extends Resource {

	/**
	 * Listener for events affecting documents.  The three possible actions are document
	 * creation, update (modification), and deletion.
	 *
	 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
	 */
	public interface Listener extends org.exist.fluent.Listener {
		/**
		 * Respond to a document event. 
		 *
		 * @param ev the details of the event
		 */
		void handle(Document.Event ev);
	}

	/**
	 * An event that concerns a document.
	 *
	 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
	 */
	public static class Event extends org.exist.fluent.Listener.Event {
		/**
		 * The document that's the subject of this event.
		 * Note that for some timing/action combinations, this field might be <code>null</code>.
		 */
		public final Document document;
		
		Event(Trigger trigger, String path, Document document) {
			super(trigger, path);
			this.document = document;
		}
		
		Event(ListenerManager.EventKey key, Document document) {
			super(key);
			this.document = document;
		}
		
		@Override
		public boolean equals(Object o) {
			return
				super.equals(o) &&
				(document == null ? ((Event) o).document == null : document.equals(((Event) o).document));
		}
		
		@Override
		public int hashCode() {
			return super.hashCode() * 37 + (document == null ? 0 : document.hashCode());
		}
		
		@Override
		public String toString() {
			StringBuilder buf = new StringBuilder(super.toString());
			buf.insert(3, "Document.");
			buf.insert(buf.length()-1, ", " + document);
			return buf.toString();
		}
	}

	/**
	 * The facet that gives access to a document's listeners.
	 *
	 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
	 */
	public class ListenersFacet {
		
		/**
		 * Add a listener for this document.  Equivalent to <code>add(EnumSet.of(trigger), listener)</code>.
		 *
		 * @see #add(Set, Document.Listener)
		 * @param trigger the kind of event the listener should be notified of
		 * @param listener the listener to notify of events
		 */
		public void add(Trigger trigger, Document.Listener listener) {
			add(EnumSet.of(trigger), listener);
		}
		
		/**
		 * Add a listener for this document.
		 * 
		 * @param triggers the kinds of events the listener should be notified of; the set must not be empty
		 * @param listener the listener to notify of events 
		 */
		public void add(Set<Trigger> triggers, Document.Listener listener) {
			staleMarker.check();
			ListenerManager.INSTANCE.add(path(), ListenerManager.Depth.ZERO, triggers, listener, Document.this);
		}
		
		/**
		 * Remove a listener previously added through this facet.  This will remove the listener from
		 * all combinations of timing and action for this document, even if added via multiple invocations
		 * of the <code>add</code> methods.  However, it will not remove the listener from combinations
		 * added through other facets.
		 *
		 * @param listener the listener to remove
		 */
		public void remove(Document.Listener listener) {
			// don't check for staleness here, might still want to remove listeners after doc is gone
			ListenerManager.INSTANCE.remove(path(), ListenerManager.Depth.ZERO, listener);
		}
	}

	protected DocumentImpl doc;
	protected StaleMarker staleMarker;
	private ListenersFacet listeners;
	
	Document(DocumentImpl dimpl, NamespaceMap namespaceBindings, Database db) {
		super(namespaceBindings, db);
		changeDoc(dimpl);
	}
	
	private void changeDoc(DocumentImpl dimpl) {
		if (dimpl == null) throw new NullPointerException("no such document");
		assert getClass() == (dimpl instanceof BinaryDocument ? Document.class : XMLDocument.class);
		this.doc = dimpl;
		String path = dimpl.getURI().getCollectionPath();
		staleMarker = new StaleMarker();
		staleMarker.track(path.substring(0, path.lastIndexOf('/')));		 // folder
		staleMarker.track(path);		// document
	}
	
	static Document newInstance(DocumentImpl dimpl, Resource origin) {
		return newInstance(dimpl, origin.namespaceBindings().extend(), origin.database());
	}
	
	static Document newInstance(DocumentImpl dimpl, NamespaceMap namespaceBindings, Database db) {
		return dimpl instanceof BinaryDocument ? new Document(dimpl, namespaceBindings, db) : new XMLDocument(dimpl, namespaceBindings, db);
	}

	@Override Sequence convertToSequence() {
		// TODO: figure out if binary documents can be converted after all
		throw new UnsupportedOperationException("binary resources are not convertible");
	}

	/**
	 * Return the listeners facet for this document, used for adding and removing document listeners.
	 *
	 * @return the listeners facet for this document
	 */
	public ListenersFacet listeners() {
		if (listeners == null) listeners = new ListenersFacet();
		return listeners;
	}
	
	/**
	 * Cast this document to an {@link XMLDocument}, if possible.
	 *
	 * @return this document cast as an XML document
	 * @throws DatabaseException if this document is not an XML document
	 */
	public XMLDocument xml() {
		throw new DatabaseException("document is not XML");
	}
	
	@Override public boolean equals(Object o) {
		if (o instanceof Document) return doc.getDocId() == ((Document) o).doc.getDocId();
		return false;
	}
	
	@Override public int hashCode() {
		return doc.getDocId();
	}

	/**
	 * Return a string representation of the reference to this document.  The representation will
	 * list the document's path, but will not include its contents.
	 * 
	 * @return a string representation of this document
	 */
	 @Override public String toString() {
		return "document '" + path() + "'";
	}
	
	/**
	 * Return the local filename of this document.  This name will never contain slashes ('/').
	 * 
	 * @return the local filename of this document
	 */
	public String name() {
		return doc.getFileURI().getCollectionPath();
	}

	/**
	 * Return the full path of this document.  This is the path of its parent folder plus its
	 * filename.
	 *
	 * @return the full path of this document
	 */
	public String path() {
		// TODO:  is this check necessary?
		// if (doc.getURI() == null) throw new DatabaseException("handle invalid, document may have been deleted");
		return Database.normalizePath(doc.getURI().getCollectionPath());
	}

	/**
	 * Return the folder that contains this document.
	 *
	 * @return the folder that contains this document
	 */
	public Folder folder() {
		staleMarker.check();
		String path = path();
		int i = path.lastIndexOf('/');
		assert i != -1;
		return new Folder(path.substring(0, i), false, this);
	}

	/**
	 * Delete this document from the database.
	 */
	public void delete() {
		staleMarker.check();
		folder().removeDocument(doc);
	}

	/**
	 * Copy this document to another collection, potentially changing its name in the process.
	 * @see Name
	 *
	 * @param destination the destination collection for the copy
	 * @param name the desired name for the copy
	 * @return the new copy of the document
	 */
	public Document copy(Folder destination, Name name) {
		return newInstance(moveOrCopy(destination, name, true), this);
	}

	/**
	 * Move this document to another collection, potentially changing its name in the process.
	 * This document will refer to the document in its new location after this method returns.
	 * You can easily use this method to move a document without changing its name
	 * (<code>doc.move(newFolder, Name.keepCreate())</code>) or to rename a document
	 * without changing its location (<code>doc.move(doc.folder(), Name.create(newName))</code>).
	 * @see Name
	 *
	 * @param destination the destination collection for the move
	 * @param name the desired name for the moved document
	 */
	public void move(Folder destination, Name name) {
		changeDoc(moveOrCopy(destination, name, false));
	}

	private DocumentImpl moveOrCopy(Folder destination, Name name, boolean copy) {
		db.checkSame(destination);
		staleMarker.check();
		name.setOldName(name());
		return destination.moveOrCopyDocument(doc, name, copy);
	}

	/**
	 * Export this document to the given file, overwriting it if it already exists.
	 *
	 * @param destination the file to export to
	 * @throws IOException if the export failed due to an I/O error
	 */
	public void export(File destination) throws IOException {
		OutputStream stream = new BufferedOutputStream(new FileOutputStream(destination));
		try {
			write(stream);
		} finally {
			stream.close();
		}
	}
	
	/**
	 * Copy the contents of the document to the given stream.  XML documents will use
	 * the default character encoding set for the database.
	 * @see Database#setDefaultExportEncoding(String)
	 *
	 * @param stream the output stream to copy the document to
	 * @throws IOException in case of I/O problems
	 */
	public void write(OutputStream stream) throws IOException {
		DBBroker broker = db.acquireBroker();
		try {
			stream.write(broker.getBinaryResource((BinaryDocument) doc));
		} finally {
			db.releaseBroker(broker);
		}
	}

	@Override QueryService createQueryService() {
		return QueryService.NULL;
	}
}
