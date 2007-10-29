package org.exist.fluent;

import java.io.*;

import org.exist.dom.*;
import org.exist.storage.DBBroker;
import org.exist.xquery.value.Sequence;
import org.xml.sax.SAXException;

/**
 * An XML document from the database.
 * 
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class XMLDocument extends Document {
	
	private final NodeProxy proxy;

	/**
	 * Create a new XML document wrapper for the given document, and start tracking
	 * the node.
	 *
	 * @param dimpl the document implementation to wrap
	 * @param namespaceBindings the namespace bindings to use
	 * @param db the database the document is part of
	 */
	XMLDocument(DocumentImpl dimpl, NamespaceMap namespaceBindings, Database db) {
		super(dimpl, namespaceBindings, db);
		if (dimpl instanceof BinaryDocument) throw new IllegalArgumentException("binary document impl passed to XML document constructor");
		proxy = new NodeProxy(dimpl);
		// no need to track a DocumentImpl proxy, since its gid cannot change
	}
	
	@Override Sequence convertToSequence() {
		return proxy;
	}
	
	/**
	 * Return this XML document.
	 * 
	 * @return this document
	 */
	@Override public XMLDocument xml() {
		return this;
	}
	
	/**
	 * Return the root element node of this document.
	 *
	 * @return the root element node of this document
	 */
	public org.exist.fluent.Node root() {
		staleMarker.check();
		return query().single("*").node();
	}
	
	/**
	 * Return a query service that executes queries in the context of this document.
	 * 
	 * @return a query service over this document
	 */
	@Override	public QueryService query() {
		staleMarker.check();
		return super.query();
	}
	
	@Override QueryService createQueryService() {
		// must explicitly return null here to avoid getting stuck with a NULL from superclass
		return null;
	}
	
	/**
	 * Return a string representation of the reference to this document.  The representation will
	 * list the document's path, but will not include its contents.
	 * 
	 * @return a string representation of this XML document
	 */
	@Override
	public String toString() {
		return "XML " + super.toString();
	}
	
	@Override public XMLDocument copy(Folder destination, Name name) {
		return (XMLDocument) super.copy(destination, name);
	}

	/**
	 * Return the serialized contents of this XML document.
	 *
	 * @return the serialized contents of this XML document
	 */
	public String contentsAsString() {
		StringWriter writer = new StringWriter();
		write(writer);
		return writer.toString();
	}

	/**
	 * Serialize this document to the given output stream using the default encoding specified
	 * for the database.  If you wish to control the encoding at a finer granularity, use
	 * {@link #write(Writer)}.
	 * 
	 * @see Database#setDefaultExportEncoding(String)
	 * @param stream the output stream to write to
	 * @throws IOException in case of problems with the encoding
	 * @throws DatabaseException in case of I/O problems
	 */
	@Override public void write(OutputStream stream) throws IOException {
		write(new OutputStreamWriter(stream, db.defaultExportEncoding));
	}

	/**
	 * Serialize this document to the given writer.
	 *
	 * @param writer destination writer
	 */
	public void write(Writer writer) {
		staleMarker.check();
		DBBroker broker = null;
		try {
			broker = db.acquireBroker();
			broker.getSerializer().serialize(doc, writer);
		} catch (SAXException e) {
			throw new DatabaseException(e);
		} finally {
			db.releaseBroker(broker);
		}
	}
	
	
}
