//$Id$
package org.exist.cluster;

import org.exist.EXistException;
import org.exist.Indexer;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.MutableDocumentSet;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.DBBroker;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.util.SyntaxException;
import org.exist.xmldb.XmldbURI;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import java.io.*;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.Observer;


/**
 * Created by Francesco Mondora.
 *
 * TODO ... verify TRANSACTION IN CLUSTER
 * @author Francesco Mondora aka Makkina
 * @author Michele Danieli aka cinde
 * @author Nicola Breda aka maiale
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




    public void store(Txn txn, DBBroker broker, IndexInfo info, String data, boolean privileged)
            throws EXistException, PermissionDeniedException, TriggerException,
            SAXException, LockException {
        InputSource is = new InputSource(new ByteArrayInputStream(data.getBytes()));
        this.store(txn, broker, info, is, privileged);
    }


    public void removeXMLResource(Txn transaction, DBBroker broker, XmldbURI docURI) throws PermissionDeniedException, TriggerException, LockException {
        collection.removeXMLResource(transaction, broker, docURI);
        try {
            ClusterComunication cluster = ClusterComunication.getInstance();
            if(cluster!=null)
                cluster.removeDocument(this.getURI().toString(), docURI.toString());
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
    public void store(Txn transaction, DBBroker broker, IndexInfo info, InputSource source, boolean privileged)
            throws EXistException, PermissionDeniedException, TriggerException,
            SAXException, LockException {
        
        Indexer indexer = info.getIndexer();
        DocumentImpl document = indexer.getDocument();

        collection.store(transaction, broker, info, source, privileged);


        InputStream is = source.getByteStream();
        Reader cs = source.getCharacterStream();
        String uri = null;

        String content = "";
        try {
            byte b[] = new byte[1];
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            if (is != null) {

                is.reset();
                while (is.read(b) != -1) {
                    bos.write(b);
                }
            } else if(cs!=null) {

                if (cs != null) {
                    cs.reset();
                    int c;
                    while ((c = cs.read()) != -1) {
                        bos.write(c);
                    }
                }
            }else {
                uri = source.getSystemId();
                URL url = new URL(uri);
                BufferedReader br = new BufferedReader(new InputStreamReader(url.openConnection().getInputStream()));
                StringBuffer buffer = new StringBuffer();
                String line = null;
                while((line=br.readLine())!=null){
                    buffer.append(line).append(System.getProperty("line.separator"));
                }
                content = buffer.toString();
            }


            bos.flush();
            bos.close();
            if(uri==null){
                content = bos.toString();
            }
        } catch (IOException e) {
             e.printStackTrace();
        }

        try {
            ClusterComunication cluster = ClusterComunication.getInstance();
            if(cluster!=null)
                cluster.storeDocument(this.getURI().toString(), document.getFileURI().toString(), content);
        } catch (ClusterException e) {
            e.printStackTrace();
        }


    }

    public BinaryDocument addBinaryResource(Txn transaction, DBBroker broker,
			XmldbURI name, byte[] data, String mimeType) throws EXistException,
            PermissionDeniedException, LockException, TriggerException {
       try {
        return collection.addBinaryResource(transaction, broker, name, data, mimeType);
       } catch (IOException ex) {
          throw new EXistException("Cannot add binary due to I/O error.",ex);
       }
    }

    public Lock getLock() {
        return collection.getLock();
    }

    public void addCollection(DBBroker broker, Collection child, boolean isNew) {
        try {
            collection.addCollection(broker, child, isNew);
    		final String childName = child.getURI().lastSegment().toString();
            System.out.println("________ ADDDING COLLECTION " + child.getURI() +" TO " + this.getURI() );
            ClusterComunication cluster = ClusterComunication.getInstance();
            if(cluster!=null)
                cluster.addCollection(this.getURI().toString(),childName);
        } catch (ClusterException e) {
            e.printStackTrace();
        }

    }


    public void removeCollection(XmldbURI name) throws LockException {
        try {
            collection.removeCollection(name);
            System.out.println("REMOVED COLLECTION " +name);
            ClusterComunication cluster = ClusterComunication.getInstance();
    		//TODO: use xmldbUri
            if(cluster!=null)
                cluster.removeCollection(this.getURI().toString(),name.toString());
        } catch (ClusterException e) {
            e.printStackTrace();
        }
    }

    public boolean hasChildCollection(XmldbURI name) {
        return collection.hasChildCollection(name);
    }

    public void release(int mode) {
        collection.release(mode);
    }

    public void update(Collection child) {
        collection.update(child);
    }

    public void addDocument(Txn transaction, DBBroker broker, DocumentImpl doc) {
        collection.addDocument(transaction, broker, doc);
    }

    public Iterator collectionIterator() {
        return collection.collectionIterator();
    }

    public List getDescendants(DBBroker broker, User user) {
        return collection.getDescendants(broker, user);
    }

    public MutableDocumentSet allDocs(DBBroker broker, MutableDocumentSet docs,
                               boolean recursive, boolean checkPermissions) {
        return collection.allDocs(broker, docs, recursive, checkPermissions);
    }

    public DocumentSet getDocuments(DBBroker broker, MutableDocumentSet docs, boolean checkPermissions) {
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

    public DocumentImpl getDocument(DBBroker broker, XmldbURI name) {
        return collection.getDocument(broker, name);
    }

    /** @deprecated */
    public DocumentImpl getDocumentWithLock(DBBroker broker, XmldbURI name)
            throws LockException {
        return collection.getDocumentWithLock(broker, name);
    }

    public DocumentImpl getDocumentWithLock(DBBroker broker, XmldbURI name, int lockMode)
            throws LockException {
        return collection.getDocumentWithLock(broker, name, lockMode);
    }

    /**
     * @deprecated Use other method
     * @see org.exist.collections.Collection#releaseDocument(org.exist.dom.DocumentImpl)
     */
    public void releaseDocument(DocumentImpl doc) {
        collection.releaseDocument(doc);
    }

    public void releaseDocument(DocumentImpl doc, int mode) {
        collection.releaseDocument(doc, mode);
    }
    
    public int getDocumentCount() {
        return collection.getDocumentCount();
    }

    public int getId() {
        return collection.getId();
    }

    public XmldbURI getURI() {
        return collection.getURI();
    }

    public XmldbURI getParentURI() {
        return collection.getParentURI();
    }

    public Permission getPermissions() {
        return collection.getPermissions();
    }

    public Permission getPermissionsNoLock() {
        return collection.getPermissionsNoLock();
    }

    public boolean hasDocument(XmldbURI name) {
        return collection.hasDocument(name);
    }

    public boolean hasSubcollection(XmldbURI name) {
        return collection.hasSubcollection(name);
    }

    public boolean hasSubcollectionNoLock(XmldbURI name) {
        return collection.hasSubcollectionNoLock(name);
    }

    public Iterator iterator(DBBroker broker) {
        return collection.iterator(broker);
    }

    public void read(DBBroker broker, VariableByteInput istream)
            throws IOException {
        collection.read(broker, istream);
    }

    public void removeBinaryResource(Txn transaction, DBBroker broker, XmldbURI docname) 
    	throws PermissionDeniedException, LockException, TriggerException {
        collection.removeBinaryResource(transaction, broker, docname);
    }

    public void removeBinaryResource(Txn transaction, DBBroker broker, DocumentImpl doc) 
    	throws PermissionDeniedException, LockException, TriggerException {
        collection.removeBinaryResource(transaction, broker, doc);
    }

    public IndexInfo validateXMLResource(Txn txn, DBBroker broker, XmldbURI name, InputSource source)
            throws EXistException, PermissionDeniedException, TriggerException,
            SAXException, LockException, IOException {
        return collection.validateXMLResource(txn, broker, name, source);
    }


    public IndexInfo validateXMLResource(Txn txn, DBBroker broker, XmldbURI name, String data)
            throws EXistException, PermissionDeniedException, TriggerException,
            SAXException, LockException,IOException {
        return collection.validateXMLResource(txn, broker, name, data);
    }


    public IndexInfo validateXMLResource(Txn txn, DBBroker broker, XmldbURI name, Node node)
            throws EXistException, PermissionDeniedException, TriggerException,
            SAXException, LockException, IOException {
        return collection.validateXMLResource(txn, broker, name, node);
    }

    public void store(Txn txn, DBBroker broker, IndexInfo info, Node node, boolean privileged)
            throws EXistException, PermissionDeniedException, TriggerException,
            SAXException, LockException {
        collection.store(txn, broker, info, node, privileged);
    }

    public void setId(int id) {
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

    /*public InputSource resolveEntity(String publicId, String systemId)
            throws SAXException, IOException {
        return collection.resolveEntity(publicId, systemId);
    }*/

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
    public boolean sync(boolean syncJournal) {
        return collection.sync(syncJournal);
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
