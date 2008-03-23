package org.exist.dom;

import org.exist.storage.DBBroker;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;

import java.util.Iterator;

/**
 */
public interface DocumentSet {

    public final static DocumentSet EMPTY_DOCUMENT_SET = new DefaultDocumentSet(9);

    public Iterator getDocumentIterator();

	public Iterator getCollectionIterator();

	public int getDocumentCount();

    DocumentImpl getDocumentAt(int pos);

	public DocumentImpl getDoc(int docId);

	public XmldbURI[] getNames();

	public DocumentSet intersection(DocumentSet other);

	public boolean contains(DocumentSet other);

	public boolean contains(int id);

	public NodeSet docsToNodeSet();

	public void lock(DBBroker broker, boolean exclusive, boolean checkExisting) throws LockException;

	public void unlock(boolean exclusive);

    public boolean equalDocs(DocumentSet other);
}
