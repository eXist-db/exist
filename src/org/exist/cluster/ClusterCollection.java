//$Id$
package org.exist.cluster;

import org.exist.EXistException;
import org.exist.Indexer;
import org.exist.collections.triggers.TriggerException;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.start.*;
import org.exist.storage.DBBroker;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.util.Lock;
import org.exist.util.LockException;
import org.exist.util.SyntaxException;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import java.io.*;
import java.util.Iterator;
import java.util.List;
import java.util.Observer;


/**
 * Created by Francesco Mondora.
 *
 * @author Francesco Mondora aka Makkina
 * @author Michele Danieli aka cinde
 *
 *         Date: Aug 31, 2004
 *         Time: 8:45:47 AM
 *         Revision $Revision$
 */
public final class ClusterCollection extends Collection {

    Collection collection;

    private Collection getWrappedCollection(Collection collection) {
        if (collection instanceof ClusterCollection) {
            return getWrappedCollection(((ClusterCollection) collection).collection);
        }
        return collection;
    }

    public ClusterCollection(Collection collection) {
        this.collection = getWrappedCollection(collection);
    }




    public void store(DBBroker broker, IndexInfo info, String data, boolean privileged)
            throws EXistException, PermissionDeniedException, TriggerException,
            SAXException, LockException {
        InputSource is = new InputSource(new ByteArrayInputStream(data.getBytes()));
        this.store(broker, info, is, privileged);
    }


    public void removeDocument(DBBroker broker, String docname) throws PermissionDeniedException, TriggerException, LockException {
        collection.removeDocument(broker, docname);
        try {
            ClusterClient cc = new ClusterClient();
            cc.sendClusterEvent(new RemoveClusterEvent(docname, this.getName()));
        } catch (ClusterException e) {
            e.printStackTrace();
        }
    }


    /**
     * This method is used by the XML RPC client.
     *
     * @param broker
     * @param info
     * @param source
     * @param privileged
     * @throws EXistException
     * @throws PermissionDeniedException
     * @throws TriggerException
     * @throws SAXException
     * @throws LockException
     */
    public void store(DBBroker broker, IndexInfo info, InputSource source, boolean privileged)
            throws EXistException, PermissionDeniedException, TriggerException,
            SAXException, LockException {
        
        Indexer indexer = info.getIndexer();
        DocumentImpl document = indexer.getDocument();

        collection.store(broker, info, source, privileged);

        InputStream is = source.getByteStream();
        Reader cs = null;

        String content = "";
        try {
            byte b[] = new byte[1];
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            if (is != null) {
                //System.out.println("IS is: " + is);

                is.reset();
                while (is.read(b) != -1) {
                    bos.write(b);
                }
            } else {
                cs = source.getCharacterStream();
                // System.out.println("CS is: " + cs);
                if (cs != null) {
                    cs.reset();
                    int c;
                    while ((c = cs.read()) != -1) {
// System.out.print((char)c);
                        bos.write(c);
                    }
                }
            }


            bos.flush();
            bos.close();
            content = bos.toString();
        } catch (IOException e) {
            // e.printStackTrace();
        }

        try {
            // if (ClusterAwareMain.hasToBePublished(content)) {
            ClusterClient cc = new ClusterClient();
            cc.sendClusterEvent(new StoreClusterEvent(content, this.getName(), document.getName().substring(this.getName().length() + 1)));
            //}
        } catch (ClusterException e) {
            e.printStackTrace();
        }


    }

    public BinaryDocument addBinaryResource(DBBroker broker,
			String name, byte[] data, String mimeType) throws EXistException,
            PermissionDeniedException, LockException {
        return collection.addBinaryResource(broker, name, data, mimeType);
    }

    public void setName(String name) {
        collection.setName(name);
    }

    public Lock getLock() {
        return collection.getLock();
    }

    public void addCollection(Collection child) {
        try {
            collection.addCollection(child);
    		final int p = child.getName().lastIndexOf('/') + 1;
    		final String childName = child.getName().substring(p);
            ClusterEvent c = new CreateCollectionClusterEvent( this.getName(), childName );
            ClusterClient cc = new ClusterClient();
            cc.sendClusterEvent(c);
        } catch (ClusterException e) {
            e.printStackTrace();
        }

    }

    public boolean hasChildCollection(String name) {
        return collection.hasChildCollection(name);
    }

    public void release() {
        collection.release();
    }

    public void update(Collection child) {
        collection.update(child);
    }

    public void addDocument(DBBroker broker, DocumentImpl doc) {
        collection.addDocument(broker, doc);
    }

    public void addDocumentLink(DBBroker broker, DocumentImpl doc) {
        collection.addDocumentLink(broker, doc);
    }

    public void unlinkDocument(DocumentImpl doc) {
        collection.unlinkDocument(doc);
    }

    public Iterator collectionIterator() {
        return collection.collectionIterator();
    }

    public List getDescendants(DBBroker broker, User user) {
        return collection.getDescendants(broker, user);
    }

    public DocumentSet allDocs(DBBroker broker, DocumentSet docs,
                               boolean recursive, boolean checkPermissions) {
        return collection.allDocs(broker, docs, recursive, checkPermissions);
    }

    public DocumentSet getDocuments(DBBroker broker, DocumentSet docs, boolean checkPermissions) {
        return collection.getDocuments(broker, docs, checkPermissions);
    }

    public boolean allowUnload() {
        return collection.allowUnload();
    }

    public int compareTo(Object obj) {
        return collection.compareTo(obj);
    }

    public boolean equals(Object obj) {
        return collection.equals(obj);
    }

    public int getChildCollectionCount() {
        return collection.getChildCollectionCount();
    }

    public DocumentImpl getDocument(DBBroker broker, String name) {
        return collection.getDocument(broker, name);
    }

    public DocumentImpl getDocumentWithLock(DBBroker broker, String name)
            throws LockException {
        return collection.getDocumentWithLock(broker, name);
    }

    public DocumentImpl getDocumentWithLock(DBBroker broker, String name, int lockMode)
            throws LockException {
        return collection.getDocumentWithLock(broker, name, lockMode);
    }

    public void releaseDocument(DocumentImpl doc) {
        collection.releaseDocument(doc);
    }

    public int getDocumentCount() {
        return collection.getDocumentCount();
    }

    public short getId() {
        return collection.getId();
    }

    public String getName() {
        return collection.getName();
    }

    public String getParentPath() {
        return collection.getParentPath();
    }

    public Permission getPermissions() {
        return collection.getPermissions();
    }

    public boolean hasDocument(String name) {
        return collection.hasDocument(name);
    }

    public boolean hasSubcollection(String name) {
        return collection.hasSubcollection(name);
    }

    public Iterator iterator(DBBroker broker) {
        return collection.iterator(broker);
    }

    public void read(DBBroker broker, VariableByteInput istream)
            throws IOException {
        collection.read(broker, istream);
    }

    public void removeCollection(String name) throws LockException {
        collection.removeCollection(name);
    }


    public void removeBinaryResource(DBBroker broker,
                                     String docname) throws PermissionDeniedException, LockException {
        collection.removeBinaryResource(broker, docname);
    }

    public void removeBinaryResource(DBBroker broker,
                                     DocumentImpl doc) throws PermissionDeniedException, LockException {
        collection.removeBinaryResource(broker, doc);
    }

    public IndexInfo validate(DBBroker broker, String name, InputSource source)
            throws EXistException, PermissionDeniedException, TriggerException,
            SAXException, LockException {
        return collection.validate(broker, name, source);
    }


    public IndexInfo validate(DBBroker broker, String name, String data)
            throws EXistException, PermissionDeniedException, TriggerException,
            SAXException, LockException {
        return collection.validate(broker, name, data);
    }


    public IndexInfo validate(DBBroker broker, String name, Node node)
            throws EXistException, PermissionDeniedException, TriggerException,
            SAXException, LockException {
        return collection.validate(broker, name, node);
    }

    public void store(DBBroker broker, IndexInfo info, Node node, boolean privileged)
            throws EXistException, PermissionDeniedException, TriggerException,
            SAXException, LockException {
        collection.store(broker, info, node, privileged);
    }

    public DocumentImpl addDocument(DBBroker broker, String name, String data)
            throws EXistException, PermissionDeniedException, TriggerException,
            SAXException, LockException {
        return collection.addDocument(broker, name, data);
    }

    public DocumentImpl addDocument(DBBroker broker, String name, String data,
                                    boolean privileged) throws EXistException,
            PermissionDeniedException, TriggerException, SAXException,
            LockException {
        return collection.addDocument(broker, name, data, privileged);
    }

    public DocumentImpl addDocument(DBBroker broker, String name,
                                    InputSource source) throws EXistException, LockException,
            PermissionDeniedException, TriggerException, SAXException {
        return collection.addDocument(broker, name, source);
    }

    public DocumentImpl addDocument(DBBroker broker, String name,
                                    InputSource source, boolean privileged) throws EXistException,
            PermissionDeniedException, SAXException, TriggerException,
            LockException {
        return collection.addDocument(broker, name, source, privileged);
    }

    public DocumentImpl addDocument(DBBroker broker, String name, Node node)
            throws EXistException, PermissionDeniedException, TriggerException,
            SAXException, LockException {
        return collection.addDocument(broker, name, node);
    }

    public DocumentImpl addDocument(DBBroker broker, String name, Node node,
                                    boolean privileged) throws EXistException, LockException,
            PermissionDeniedException, TriggerException, SAXException {
        return collection.addDocument(broker, name, node, privileged);
    }



    public void setId(short id) {
        collection.setId(id);
    }

    public void setPermissions(int mode) throws LockException {
        collection.setPermissions(mode);
    }

    public void setPermissions(String mode) throws SyntaxException, LockException {
        collection.setPermissions(mode);
    }

    public void setPermissions(Permission permissions) throws LockException {
        collection.setPermissions(permissions);
    }

    public void write(DBBroker broker, VariableByteOutputStream ostream)
            throws IOException {
        collection.write(broker, ostream);
    }

    public void setAddress(long addr) {
        collection.setAddress(addr);
    }

    public long getAddress() {
        return collection.getAddress();
    }

    public void setCreationTime(long ms) {
        collection.setCreationTime(ms);
    }

    public long getCreationTime() {
        return collection.getCreationTime();
    }

    public void setTriggersEnabled(boolean enabled) {
        collection.setTriggersEnabled(enabled);
    }

    public void setReader(XMLReader reader) {
        collection.setReader(reader);
    }

    public InputSource resolveEntity(String publicId, String systemId)
            throws SAXException, IOException {
        return collection.resolveEntity(publicId, systemId);
    }

    /* (non-Javadoc)
	 * @see java.util.Observable#addObserver(java.util.Observer)
	 */
    public void addObserver(Observer o) {
        collection.addObserver(o);
    }

    /* (non-Javadoc)
	 * @see java.util.Observable#deleteObservers()
	 */
    public void deleteObservers() {
        collection.deleteObservers();
    }

    /* (non-Javadoc)
	 * @see org.exist.storage.cache.Cacheable#getKey()
	 */
    public long getKey() {
        return collection.getKey();
    }

    /* (non-Javadoc)
	 * @see org.exist.storage.cache.Cacheable#getReferenceCount()
	 */
    public int getReferenceCount() {
        return collection.getReferenceCount();
    }

    /* (non-Javadoc)
	 * @see org.exist.storage.cache.Cacheable#incReferenceCount()
	 */
    public int incReferenceCount() {
        return collection.incReferenceCount();
    }

    /* (non-Javadoc)
	 * @see org.exist.storage.cache.Cacheable#decReferenceCount()
	 */
    public int decReferenceCount() {
        return collection.decReferenceCount();
    }

    /* (non-Javadoc)
	 * @see org.exist.storage.cache.Cacheable#setReferenceCount(int)
	 */
    public void setReferenceCount(int count) {
        collection.setReferenceCount(count);
    }

    /* (non-Javadoc)
	 * @see org.exist.storage.cache.Cacheable#setTimestamp(int)
	 */
    public void setTimestamp(int timestamp) {
        collection.setTimestamp(timestamp);
    }

    /* (non-Javadoc)
	 * @see org.exist.storage.cache.Cacheable#getTimestamp()
	 */
    public int getTimestamp() {
        return collection.getTimestamp();
    }

    /* (non-Javadoc)
	 * @see org.exist.storage.cache.Cacheable#release()
	 */
    public boolean sync() {
        return collection.sync();
    }

    /* (non-Javadoc)
     * @see org.exist.storage.cache.Cacheable#isDirty()
     */
    public boolean isDirty() {
        return collection.isDirty();
    }

    public String toString() {
        return collection.toString();
    }



}
