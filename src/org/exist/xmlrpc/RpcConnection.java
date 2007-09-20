/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.xmlrpc;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Vector;
import java.util.WeakHashMap;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.Namespaces;
import org.exist.backup.Backup;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationException;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.collections.IndexInfo;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentMetadata;
import org.exist.dom.DocumentSet;
import org.exist.dom.DocumentTypeImpl;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.dom.SortedNodeSet;
import org.exist.memtree.NodeImpl;
import org.exist.numbering.NodeId;
import org.exist.protocolhandler.embedded.EmbeddedInputStream;
import org.exist.protocolhandler.xmldb.XmldbURL;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SecurityManager;
import org.exist.security.User;
import org.exist.security.XMLSecurityManager;
import org.exist.security.xacml.AccessContext;
import org.exist.source.Source;
import org.exist.source.StringSource;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.DataBackup;
import org.exist.storage.XQueryPool;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.sync.Sync;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.Compressor;
import org.exist.util.Configuration;
import org.exist.util.LockException;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.util.Occurrences;
import org.exist.util.SyntaxException;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.validation.ValidationReport;
import org.exist.validation.Validator;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.Option;
import org.exist.xquery.PathExpr;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.util.HTTPUtils;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;
import org.exist.xupdate.Modification;
import org.exist.xupdate.XUpdateProcessor;
import org.w3c.dom.DocumentType;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * This class implements the actual methods defined by
 * {@link org.exist.xmlrpc.RpcAPI}.
 *
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 * Modified by {Marco.Tampucci, Massimo.Martinelli} @isti.cnr.it
 */
public class RpcConnection extends Thread {
    
    private final static Logger LOG = Logger.getLogger(RpcConnection.class);
    
    protected BrokerPool brokerPool;
    protected WeakHashMap documentCache = new WeakHashMap();
    protected boolean terminate = false;
    protected List cachedExpressions = new LinkedList();
    
    protected RpcServer.ConnectionPool connectionPool;
    
    private static final int MAX_DOWNLOAD_CHUNK_SIZE = 0x40000;
    
    /** id of the database registered against the BrokerPool */
    protected String databaseid = BrokerPool.DEFAULT_INSTANCE_NAME;

    
    public RpcConnection(Configuration conf, RpcServer.ConnectionPool pool, String id)
    throws EXistException {
        super();
        if (id != null && !"".equals(id)) this.databaseid=id;
        connectionPool = pool;
        brokerPool = BrokerPool.getInstance(this.databaseid);
    }
    
    public void createCollection(User user, String name, Date created) throws Exception,
    	PermissionDeniedException, URISyntaxException {
    	createCollection(user,XmldbURI.xmldbUriFor(name),created);
    }
    
    public void createCollection(User user, XmldbURI collUri, Date created) throws Exception,
            PermissionDeniedException {
        DBBroker broker = null;
        TransactionManager transact = brokerPool.getTransactionManager();
        Txn transaction = transact.beginTransaction();
        try {
            broker = brokerPool.get(user);
            Collection current = broker.getOrCreateCollection(transaction, collUri);
            //TODO : register a lock (wich one ?) within the transaction ?
            if (created != null)
                current.setCreationTime( created.getTime());
            LOG.debug("creating collection " + collUri);
            broker.saveCollection(transaction, current);
            transact.commit(transaction);
            broker.flush();
            //broker.sync();
            LOG.info("collection " + collUri + " has been created");
        } catch (Exception e) {
            transact.abort(transaction);
            LOG.warn(e);
            throw e;
        } finally {
            brokerPool.release(broker);
        }
    }
    
    public void configureCollection(User user, String collName, String configuration)
    throws EXistException, URISyntaxException {
    	configureCollection(user,XmldbURI.xmldbUriFor(collName),configuration);
    }
    public void configureCollection(User user, XmldbURI collUri, String configuration)
    throws EXistException {
        DBBroker broker = null;
        Collection collection = null;
        TransactionManager transact = brokerPool.getTransactionManager();
        Txn transaction = transact.beginTransaction();
        try {
            broker = brokerPool.get(user);
            try {
	            collection = broker.openCollection(collUri, Lock.READ_LOCK);
	            if (collection == null) {
	                transact.abort(transaction);
	                throw new EXistException("collection " + collUri + " not found!");
	            }
            } finally {
            	if (collection != null)
            		collection.release(Lock.READ_LOCK);
            }
            CollectionConfigurationManager mgr = brokerPool.getConfigurationManager();
            mgr.addConfiguration(transaction, broker, collection, configuration);
            transact.commit(transaction);
            LOG.info("Configured '" + collection.getURI() + "'");  
        } catch (CollectionConfigurationException e) {
            transact.abort(transaction);
            throw new EXistException(e.getMessage());
        } finally {
            brokerPool.release(broker);
        }
    }
    
    public String createId(User user, String collName) throws EXistException, URISyntaxException {
    	return createId(user,XmldbURI.xmldbUriFor(collName));
    }
    
    public String createId(User user, XmldbURI collUri) throws EXistException {
        DBBroker broker = brokerPool.get(user);
        Collection collection = null;
        try {
            collection = broker.openCollection(collUri, Lock.READ_LOCK);
            if (collection == null)
                throw new EXistException("collection " + collUri + " not found!");
            XmldbURI id;
            Random rand = new Random();
            boolean ok;
            do {
                ok = true;
                id = XmldbURI.create(Integer.toHexString(rand.nextInt()) + ".xml");
                // check if this id does already exist
                if (collection.hasDocument(id))
                    ok = false;
                
                if (collection.hasSubcollection(id))
                    ok = false;
                
            } while (!ok);
            return id.toString();
        } finally {
            if(collection != null)
                collection.release(Lock.READ_LOCK);
            brokerPool.release(broker);
        }
    }
    
    protected QueryResult doQuery(User user, DBBroker broker, String xpath,
            NodeSet contextSet, Hashtable parameters)
            throws Exception {
        XQuery xquery = broker.getXQueryService();
        XQueryPool pool = xquery.getXQueryPool();
        Source source = new StringSource(xpath);
        CompiledXQuery compiled = compile(user, broker, source, parameters);
        checkPragmas(compiled.getContext(), parameters);
        try {
            long start = System.currentTimeMillis();
            Sequence result = xquery.execute(compiled, contextSet);
            // pass last modified date to the HTTP response
            HTTPUtils.addLastModifiedHeader( result, compiled.getContext() );
            LOG.info("query took " + (System.currentTimeMillis() - start) + "ms.");
            return new QueryResult(compiled.getContext(), result);
        } catch (XPathException e) {
            return new QueryResult(e);
        } finally {
            if(compiled != null)
                pool.returnCompiledXQuery(source, compiled);
        }
    }
    
    private CompiledXQuery compile(User user, DBBroker broker, Source source, Hashtable parameters) throws XPathException, IOException {
        XQuery xquery = broker.getXQueryService();
        XQueryPool pool = xquery.getXQueryPool();
        CompiledXQuery compiled = pool.borrowCompiledXQuery(broker, source);
        XQueryContext context;
        if(compiled == null)
            context = xquery.newContext(AccessContext.XMLRPC);
        else
            context = compiled.getContext();
    	String base = (String) parameters.get(RpcAPI.BASE_URI);
    	if(base!=null)
    		context.setBaseURI(new AnyURIValue(base));
        Hashtable namespaces = (Hashtable)parameters.get(RpcAPI.NAMESPACES);
        if(namespaces != null && namespaces.size() > 0) {
            context.declareNamespaces(namespaces);
        }
        //  declare static variables
        Hashtable variableDecls = (Hashtable)parameters.get(RpcAPI.VARIABLES);
        if(variableDecls != null) {
            for (Iterator i = variableDecls.entrySet().iterator(); i.hasNext();) {
                Map.Entry entry = (Map.Entry) i.next();
                LOG.debug("declaring " + entry.getKey().toString() + " = " + entry.getValue());
                context.declareVariable((String) entry.getKey(), entry.getValue());
            }
        }
        Vector staticDocuments = (Vector)parameters.get(RpcAPI.STATIC_DOCUMENTS);
        if(staticDocuments != null) {
        	try {
            XmldbURI[] d = new XmldbURI[staticDocuments.size()];
            int j = 0;
            for (Iterator i = staticDocuments.iterator(); i.hasNext(); j++) {
                XmldbURI next = XmldbURI.xmldbUriFor((String)i.next());
                d[j] = next;
            }
            context.setStaticallyKnownDocuments(d);
        	} catch(URISyntaxException e) {
        		throw new XPathException(e);
        	}
        } else if(context.isBaseURIDeclared()) {
            context.setStaticallyKnownDocuments(new XmldbURI[] { context.getBaseURI().toXmldbURI() });
        }
        if(compiled == null)
            compiled = xquery.compile(context, source);
        return compiled;
    }
    
    public String printDiagnostics(User user, String query, Hashtable parameters) throws Exception {
        DBBroker broker = null;
        try {
            broker = brokerPool.get(user);
            Source source = new StringSource(query);
            XQuery xquery = broker.getXQueryService();
            XQueryPool pool = xquery.getXQueryPool();
            CompiledXQuery compiled = pool.borrowCompiledXQuery(broker, source);
            if(compiled == null)
                compiled = compile(user, broker, source, parameters);
            StringWriter writer = new StringWriter();
            compiled.dump(writer);
            return writer.toString();
        } finally {
            brokerPool.release(broker);
        }
    }
    
    /**
     * Check if the XQuery contains pragmas that define serialization settings.
     * If yes, copy the corresponding settings to the current set of output properties.
     *
     * @param context
     */
    protected void checkPragmas(XQueryContext context, Hashtable parameters) throws XPathException {
        Option pragma = context.getOption(Option.SERIALIZE_QNAME);
        if(pragma == null)
            return;
        String[] contents = pragma.tokenizeContents();
        for(int i = 0; i < contents.length; i++) {
            String[] pair = Option.parseKeyValuePair(contents[i]);
            if(pair == null)
                throw new XPathException("Unknown parameter found in " + pragma.getQName().getStringValue() +
                        ": '" + contents[i] + "'");
            LOG.debug("Setting serialization property from pragma: " + pair[0] + " = " + pair[1]);
            parameters.put(pair[0], pair[1]);
        }
    }
    
    public int executeQuery(User user, String xpath, Hashtable parameters) throws Exception {
        long startTime = System.currentTimeMillis();
        DBBroker broker = null;
        try {
            broker = brokerPool.get(user);
            QueryResult result = doQuery(user, broker, xpath, null,
                    parameters);
            if(result.hasErrors())
                throw result.getException();
            result.queryTime = System.currentTimeMillis() - startTime;
            return connectionPool.resultSets.add(result);
        } finally {
            brokerPool.release(broker);
        }
    }
    
    protected String formatErrorMsg(String message) {
        return formatErrorMsg("error", message);
    }
    
    protected String formatErrorMsg(String type, String message) {
        StringBuffer buf = new StringBuffer();
        buf.append("<exist:result xmlns:exist=\""+ Namespaces.EXIST_NS + "\" ");
        buf.append("hitCount=\"0\">");
        buf.append('<');
        buf.append(type);
        buf.append('>');
        buf.append(message);
        buf.append("</");
        buf.append(type);
        buf.append("></exist:result>");
        return buf.toString();
    }
    
    public Hashtable getCollectionDesc(User user, String rootCollection)
    throws Exception, URISyntaxException {
    	return getCollectionDesc(user,(rootCollection==null)?XmldbURI.ROOT_COLLECTION_URI:XmldbURI.xmldbUriFor(rootCollection));
    }
    
    public Hashtable getCollectionDesc(User user, XmldbURI rootUri)
    throws Exception {
        DBBroker broker = brokerPool.get(user);
        Collection collection = null;
        try {           
            collection = broker.openCollection(rootUri, Lock.READ_LOCK);
            if (collection == null)
                throw new EXistException("collection " + rootUri + " not found!");
            Hashtable desc = new Hashtable();
            Vector docs = new Vector();
            Vector collections = new Vector();
            if (collection.getPermissions().validate(user, Permission.READ)) {                      
                for (Iterator i = collection.iterator(broker); i.hasNext(); ) {
                	DocumentImpl doc = (DocumentImpl) i.next();
                	Permission perms = doc.getPermissions();
                	Hashtable hash = new Hashtable(4);
                    hash.put("name", doc.getFileURI().toString());
                    hash.put("owner", perms.getOwner());
                    hash.put("group", perms.getOwnerGroup());
                    hash.put("permissions", new Integer(perms.getPermissions()));
                    hash.put("type",
                            doc.getResourceType() == DocumentImpl.BINARY_FILE
                            ? "BinaryResource"
                            : "XMLResource");
                    docs.addElement(hash);
                }
                for (Iterator i = collection.collectionIterator(); i.hasNext(); )
                    collections.addElement(((XmldbURI) i.next()).toString());
            }
            Permission perms = collection.getPermissions();
            desc.put("collections", collections);
            desc.put("documents", docs);
            desc.put("name", collection.getURI().toString());
            desc.put("created", Long.toString(collection.getCreationTime()));
            desc.put("owner", perms.getOwner());
            desc.put("group", perms.getOwnerGroup());
            desc.put("permissions", new Integer(perms.getPermissions()));
            return desc;
        } finally {
            if(collection != null)
                collection.release(Lock.READ_LOCK);
            brokerPool.release(broker);
        }
    }
    
    public Hashtable describeResource(User user, String resourceName)
    throws EXistException, PermissionDeniedException, URISyntaxException {
    	return describeResource(user,XmldbURI.xmldbUriFor(resourceName));
    }
    
    public Hashtable describeResource(User user, XmldbURI resourceUri)
    throws EXistException, PermissionDeniedException {
        DBBroker broker = brokerPool.get(user);
        DocumentImpl doc = null;
        Hashtable hash = new Hashtable(5);
        try {
            doc = (DocumentImpl) broker.getXMLResource(resourceUri, Lock.READ_LOCK);
            if (doc == null) {
                LOG.debug("document " + resourceUri + " not found!");
                return hash;
            }
            if (!doc.getCollection().getPermissions().validate(user, Permission.READ)) {
                throw new PermissionDeniedException("Not allowed to read collection");
            }
            Permission perms = doc.getPermissions();
            hash.put("name", resourceUri.toString());
            hash.put("owner", perms.getOwner());
            hash.put("group", perms.getOwnerGroup());
            hash.put("permissions", new Integer(perms.getPermissions()));
            hash.put("type",
                    doc.getResourceType() == DocumentImpl.BINARY_FILE
                    ? "BinaryResource"
                    : "XMLResource");
            hash.put("content-length", new Integer(doc.getContentLength()));
            hash.put("mime-type", doc.getMetadata().getMimeType());
            hash.put("created", new Date(doc.getMetadata().getCreated()));
            hash.put("modified", new Date(doc.getMetadata().getLastModified()));
            return hash;
        } finally {
            if(doc != null)
                doc.getUpdateLock().release(Lock.READ_LOCK);
            brokerPool.release(broker);
        }
    }
    public Hashtable describeCollection(User user, String rootCollection)
    throws Exception, URISyntaxException {
    	return describeCollection(user,(rootCollection==null)?XmldbURI.ROOT_COLLECTION_URI:XmldbURI.xmldbUriFor(rootCollection));
    }
    public Hashtable describeCollection(User user, XmldbURI collUri)
    throws Exception {
        DBBroker broker = brokerPool.get(user);
        Collection collection = null;
        try {
            collection = broker.openCollection(collUri, Lock.READ_LOCK);
            if (collection == null)
                throw new EXistException("collection " + collUri + " not found!");
            Hashtable desc = new Hashtable();
            Vector collections = new Vector();
            if (collection.getPermissions().validate(user, Permission.READ)) {
                for (Iterator i = collection.collectionIterator(); i.hasNext(); )
                    collections.addElement(((XmldbURI) i.next()).toString());
            }
            Permission perms = collection.getPermissions();
            desc.put("collections", collections);
            desc.put("name", collection.getURI().toString());
            desc.put("created", Long.toString(collection.getCreationTime()));
            desc.put("owner", perms.getOwner());
            desc.put("group", perms.getOwnerGroup());
            desc.put("permissions", new Integer(perms.getPermissions()));
            return desc;
        } finally {
            if(collection != null)
                collection.release(Lock.READ_LOCK);
            brokerPool.release(broker);
        }
    }
    public String getDocument(User user, String docName, Hashtable parametri)
    throws Exception, URISyntaxException {
    	return getDocument(user,XmldbURI.xmldbUriFor(docName), parametri);
    }
    public String getDocument(User user, XmldbURI docUri, Hashtable parametri)
    throws Exception {
        DBBroker broker = null;
        
        Collection collection = null;
        DocumentImpl doc = null;
        try {
            broker = brokerPool.get(user);
            collection = broker.openCollection(docUri.removeLastSegment(), Lock.READ_LOCK);
            if (collection == null) {
                LOG.debug("collection " + docUri.removeLastSegment() + " not found!");
                return null;
            }
            if(!collection.getPermissions().validate(user, Permission.READ)) {
                throw new PermissionDeniedException("Insufficient privileges to read resource");
            }
            doc = collection.getDocumentWithLock(broker, docUri.lastSegment(), Lock.READ_LOCK);
            if (doc == null) {
                LOG.debug("document " + docUri + " not found!");
                throw new EXistException("document not found");
            }
            
            if(!doc.getPermissions().validate(user, Permission.READ))
                throw new PermissionDeniedException("Insufficient privileges to read resource " + docUri);
            Serializer serializer = broker.getSerializer();
            serializer.setProperties(parametri);
            String xml = serializer.serialize(doc);
            
            return xml;
        } catch (NoSuchMethodError nsme) {
            nsme.printStackTrace();
            return null;
        } finally {
            if(collection != null)
                collection.releaseDocument(doc, Lock.READ_LOCK);
            if(collection != null)
                collection.release(Lock.READ_LOCK);
            brokerPool.release(broker);
        }
    }
    
    public Hashtable getDocumentData(User user, String docName, Hashtable parameters)
    throws Exception{
        Collection collection = null;
        DocumentImpl doc = null;
        DBBroker broker = null;
        try {
            broker = brokerPool.get(user);
            XmldbURI docURI = XmldbURI.xmldbUriFor(docName);  
            collection = broker.openCollection(docURI.removeLastSegment(), Lock.READ_LOCK);
            if (collection == null) {
                LOG.debug("collection " + docURI.removeLastSegment() + " not found!");
                throw new EXistException("Collection " + docURI.removeLastSegment() + " not found!");
            }
            if(!collection.getPermissions().validate(user, Permission.READ)) {
                throw new PermissionDeniedException("Insufficient privileges to read resource");
            }
            doc = collection.getDocumentWithLock(broker, docURI.lastSegment(), Lock.READ_LOCK);
            if (doc == null) {
                LOG.debug("document " + docURI + " not found!");
                throw new EXistException("document not found");
            }
            
            if(!doc.getPermissions().validate(user, Permission.READ))
                throw new PermissionDeniedException("Insufficient privileges to read resource " + docName);
            String encoding = (String)parameters.get(OutputKeys.ENCODING);
            if(encoding == null)
                encoding = "UTF-8";
            
            // binary check TODO dwes
            if(doc.getResourceType() == DocumentImpl.XML_FILE) {
                //
                
                Serializer serializer = broker.getSerializer();
                serializer.setProperties(parameters);
                Hashtable result = new Hashtable();
                if(doc.getContentLength() > MAX_DOWNLOAD_CHUNK_SIZE) {
                    File tempFile = File.createTempFile("eXistRPCC", ".xml");
                    tempFile.deleteOnExit();
                    LOG.debug("Writing to temporary file: " + tempFile.getName());
                    
                    OutputStream os = new FileOutputStream(tempFile);
                    Writer writer = new OutputStreamWriter(os, encoding);
                    serializer.serialize(doc, writer);
                    writer.close();
                    
                    byte[] firstChunk = getChunk(tempFile, 0);
                    result.put("data", firstChunk);
                    result.put("handle", tempFile.getAbsolutePath());
                    result.put("offset", new Integer(firstChunk.length));
                } else {
                    String xml = serializer.serialize(doc);
                    result.put("data", xml.getBytes(encoding));
                    result.put("offset", new Integer(0));
                }
                return result;
            } else {
                Hashtable result = new Hashtable();
                if(doc.getContentLength() > MAX_DOWNLOAD_CHUNK_SIZE) {
                    File tempFile = File.createTempFile("eXistRPCC", ".bin");
                    tempFile.deleteOnExit();
                    LOG.debug("Writing to temporary file: " + tempFile.getName());
                    OutputStream os = new FileOutputStream(tempFile);
                    broker.readBinaryResource((BinaryDocument)doc, os);
                    os.close();
                    
                    byte[] firstChunk = getChunk(tempFile, 0);
                    result.put("data", firstChunk);
                    result.put("handle", tempFile.getAbsolutePath());
                    result.put("offset", new Integer(firstChunk.length));
                    
                } else {
                    byte[] data = broker.getBinaryResource((BinaryDocument)doc);
                    result.put("data", data);
                    result.put("offset", new Integer(0));
                }
                return result;
            }
        } finally {
            if(collection != null) {
                collection.releaseDocument(doc, Lock.READ_LOCK);
                collection.getLock().release(Lock.READ_LOCK);
            }
            brokerPool.release(broker);
        }
    }
    
    public Hashtable getNextChunk(User user, String handle, int offset) throws Exception {
        File file = new File(handle);
        if(!(file.isFile() && file.canRead()))
            throw new EXistException("Invalid handle specified");
        if(offset <= 0 || offset > file.length())
            throw new EXistException("No more data available");
        byte[] chunk = getChunk(file, offset);
        int nextChunk = offset + chunk.length;
        
        Hashtable result = new Hashtable();
        result.put("data", chunk);
        result.put("handle", handle);
        if(nextChunk == file.length()) {
            file.delete();
            result.put("offset", new Integer(0));
        } else
            result.put("offset", new Integer(nextChunk));
        return result;
    }
    
    private byte[] getChunk(File file, int offset) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(file, "r");
        raf.seek((long)offset);
        int remaining = (int)(raf.length() - offset);
        if(remaining > MAX_DOWNLOAD_CHUNK_SIZE)
            remaining = MAX_DOWNLOAD_CHUNK_SIZE;
        byte[] data = new byte[remaining];
        raf.readFully(data);
        raf.close();
        return data;
    }
    
    public byte[] getBinaryResource(User user, String name)
    throws EXistException, PermissionDeniedException, URISyntaxException {
    	return getBinaryResource(user,XmldbURI.xmldbUriFor(name));
    }
    
    public byte[] getBinaryResource(User user, XmldbURI name)
    throws EXistException, PermissionDeniedException {
        DBBroker broker = null;
        DocumentImpl doc = null;
        try {
            broker = brokerPool.get(user);
            doc = broker.getXMLResource(name, Lock.READ_LOCK);
            if (doc == null)
                throw new EXistException("Resource " + name + " not found");
            if (doc.getResourceType() != DocumentImpl.BINARY_FILE)
                throw new EXistException("Document " + name
                        + " is not a binary resource");
            if(!doc.getPermissions().validate(user, Permission.READ))
                throw new PermissionDeniedException("Insufficient privileges to read resource");
            return broker.getBinaryResource((BinaryDocument) doc);
		} finally {
            if(doc != null)
                doc.getUpdateLock().release(Lock.READ_LOCK);
            brokerPool.release(broker);
        }
    }
    
    public int xupdate(User user, String collectionName, String xupdate)
    throws SAXException, LockException, PermissionDeniedException, EXistException,
            XPathException, URISyntaxException {
    	return xupdate(user,XmldbURI.xmldbUriFor(collectionName),xupdate);
    }
    
    public int xupdate(User user, XmldbURI collUri, String xupdate)
    throws SAXException, LockException, PermissionDeniedException, EXistException,
            XPathException {
        TransactionManager transact = brokerPool.getTransactionManager();
        Txn transaction = transact.beginTransaction();
        DBBroker broker = null;
        try {
            broker = brokerPool.get(user);
            Collection collection = broker.getCollection(collUri);
            if (collection == null) {
                transact.abort(transaction);
                throw new EXistException("collection " + collUri + " not found");
            }
            //TODO : register a lock (which one ?) in the transaction ?
            DocumentSet docs = collection.allDocs(broker, new DocumentSet(), true, true);
            XUpdateProcessor processor = new XUpdateProcessor(broker, docs, AccessContext.XMLRPC);
            Modification modifications[] = processor.parse(new InputSource(new StringReader(xupdate)));
            long mods = 0;
            for (int i = 0; i < modifications.length; i++) {
                mods += modifications[i].process(transaction);
                broker.flush();
            }
            transact.commit(transaction);
            return (int) mods;
        } catch (ParserConfigurationException e) {
            transact.abort(transaction);
            throw new EXistException(e.getMessage());
        } catch (IOException e) {
            transact.abort(transaction);
            throw new EXistException(e.getMessage());
        } finally {
            brokerPool.release(broker);
        }
    }
    
    public int xupdateResource(User user, String resource, String xupdate)
    throws SAXException, LockException, PermissionDeniedException, EXistException,
            XPathException, URISyntaxException {
    	return xupdateResource(user,XmldbURI.xmldbUriFor(resource),xupdate);
    }
    public int xupdateResource(User user, XmldbURI docUri, String xupdate)
    throws SAXException, LockException, PermissionDeniedException, EXistException,
            XPathException {
        TransactionManager transact = brokerPool.getTransactionManager();
        Txn transaction = transact.beginTransaction();
        DBBroker broker = null;
        try {
            broker = brokerPool.get(user);
            DocumentImpl doc = (DocumentImpl)broker.getXMLResource(docUri);
            if (doc == null) {
                transact.abort(transaction);
                throw new EXistException("document " + docUri + " not found");
            }
            //TODO : register a lock (which one ?) within the transaction ?
            if(!doc.getPermissions().validate(user, Permission.READ)) {
                transact.abort(transaction);
                throw new PermissionDeniedException("Insufficient privileges to read resource");
            }
            DocumentSet docs = new DocumentSet();
            docs.add(doc);
            XUpdateProcessor processor = new XUpdateProcessor(broker, docs, AccessContext.XMLRPC);
            Modification modifications[] = processor.parse(new InputSource(
                    new StringReader(xupdate)));
            long mods = 0;
            for (int i = 0; i < modifications.length; i++) {
                mods += modifications[i].process(transaction);
                broker.flush();
            }
            transact.commit(transaction);
            return (int) mods;
        } catch (ParserConfigurationException e) {
            transact.abort(transaction);
            throw new EXistException(e.getMessage());
        } catch (IOException e) {
            transact.abort(transaction);
            throw new EXistException(e.getMessage());
        } finally {
            brokerPool.release(broker);
        }
    }
    
    public boolean sync() {
        DBBroker broker = null;
        try {
            broker = brokerPool.get(XMLSecurityManager.SYSTEM_USER);
            broker.sync(Sync.MAJOR_SYNC);
        } catch (EXistException e) {
        	LOG.warn(e.getMessage(), e);
        } finally {
            brokerPool.release(broker);
        }
        return true;
    }
    
    public boolean isXACMLEnabled() {
    	return brokerPool.getSecurityManager().isXACMLEnabled();
    }
    
    public boolean dataBackup(User user, String dest ) {
        brokerPool.triggerSystemTask( new DataBackup(dest));
        return true;
    }
    
    public Vector getDocumentListing(User user) throws EXistException {
        DBBroker broker = null;
        try {
            broker = brokerPool.get(user);
            DocumentSet docs = broker.getAllXMLResources(new DocumentSet());
            XmldbURI names[] = docs.getNames();
            Vector vec = new Vector();
            for (int i = 0; i < names.length; i++)
                vec.addElement(names[i].toString());
            
            return vec;
        } finally {
            brokerPool.release(broker);
        }
    }
    
    public Vector getDocumentListing(User user, String collName)
    throws EXistException, PermissionDeniedException, URISyntaxException {
    	return getDocumentListing(user,XmldbURI.xmldbUriFor(collName));
    }
    public Vector getDocumentListing(User user, XmldbURI collUri)
    throws EXistException, PermissionDeniedException {
        DBBroker broker = null;
        Collection collection = null;
        try {
            broker = brokerPool.get(user);            
            collection = broker.openCollection(collUri, Lock.READ_LOCK);
            Vector vec = new Vector();
            if (collection == null) {
                LOG.debug("collection " + collUri + " not found.");
                return vec;
            }
             for (Iterator i = collection.iterator(broker); i.hasNext(); ) {
                vec.addElement(((DocumentImpl) i.next()).getFileURI().toString());
            }
            return vec;
        } finally {
            if(collection != null)
                collection.release(Lock.READ_LOCK);
            brokerPool.release(broker);
        }
    }
    
    public int getResourceCount(User user, String collectionName)
    throws EXistException, PermissionDeniedException, URISyntaxException {
    	return getResourceCount(user,XmldbURI.xmldbUriFor(collectionName));
    }
    public int getResourceCount(User user, XmldbURI collUri)
    throws EXistException, PermissionDeniedException {
        DBBroker broker = null;
        Collection collection = null;
        try {
            broker = brokerPool.get(user);
            collection = broker.openCollection(collUri, Lock.READ_LOCK);
            return collection.getDocumentCount();
        } finally {
            if(collection != null)
                collection.release(Lock.READ_LOCK);
            brokerPool.release(broker);
        }
    }
    
    public String createResourceId(User user, String collectionName)
    throws EXistException, PermissionDeniedException, URISyntaxException {
    	return createResourceId(user,XmldbURI.xmldbUriFor(collectionName));
    }
    
    /**
     * Creates a unique name for a database resource
     * Uniqueness is only guaranteed within the eXist instance
     * 
     * The name is based on a hex encoded string of a random integer
     * and will have the format xxxxxxxx.xml where x is in the range
     * 0 to 9 and a to f 
     * 
     * @return the unique resource name 
     */
    public String createResourceId(User user, XmldbURI collUri) throws EXistException, PermissionDeniedException {
        DBBroker broker = null;
        Collection collection = null;
        try {
            broker = brokerPool.get(user);
            collection = broker.openCollection(collUri, Lock.READ_LOCK);
            XmldbURI id;
            Random rand = new Random();
            boolean ok;
            do {
                ok = true;
                id = XmldbURI.create(Integer.toHexString(rand.nextInt()) + ".xml");
                // check if this id does already exist
                if (collection.hasDocument(id))
                    ok = false;
                
                if (collection.hasSubcollection(id))
                    ok = false;
                
            } while (!ok);
            return id.toString();
        } finally {
            if(collection != null)
                collection.release(Lock.READ_LOCK);
            brokerPool.release(broker);
        }
    }
    
    public Hashtable listDocumentPermissions(User user, String name)
    throws EXistException, PermissionDeniedException, URISyntaxException {
    	return listDocumentPermissions(user,XmldbURI.xmldbUriFor(name));
    }
    public Hashtable listDocumentPermissions(User user, XmldbURI collUri)
    throws EXistException, PermissionDeniedException {
        DBBroker broker = null;
        Collection collection = null;
        try {
            broker = brokerPool.get(user);
            collection = broker.openCollection(collUri, Lock.READ_LOCK);
            if (collection == null)
                throw new EXistException("Collection " + collUri + " not found");
            if (!collection.getPermissions().validate(user, Permission.READ))
                throw new PermissionDeniedException(
                        "not allowed to read collection " + collUri);
            Hashtable result = new Hashtable(collection.getDocumentCount());
            for (Iterator i = collection.iterator(broker); i.hasNext(); ) {
            	DocumentImpl doc = (DocumentImpl) i.next();
            	Permission perm = doc.getPermissions();
            	Vector tmp = new Vector(3);
                tmp.addElement(perm.getOwner());
                tmp.addElement(perm.getOwnerGroup());
                tmp.addElement(new Integer(perm.getPermissions()));
                result.put(doc.getFileURI().toString(), tmp);
            }
            return result;
        } finally {
            if(collection != null)
                collection.release(Lock.READ_LOCK);
            brokerPool.release(broker);
        }
    }
    
    public Hashtable listCollectionPermissions(User user, String name)
    throws EXistException, PermissionDeniedException, URISyntaxException {
    	return listCollectionPermissions(user,XmldbURI.xmldbUriFor(name));
    }
    public Hashtable listCollectionPermissions(User user, XmldbURI collUri)
    throws EXistException, PermissionDeniedException {
        DBBroker broker = null;
        Collection collection = null;
        try {
            broker = brokerPool.get(user);
            collection = broker.openCollection(collUri, Lock.READ_LOCK);
            if (collection == null)
                throw new EXistException("Collection " + collUri + " not found");
            if (!collection.getPermissions().validate(user, Permission.READ))
                throw new PermissionDeniedException("not allowed to read collection " + collUri);
            Hashtable result = new Hashtable(collection.getChildCollectionCount());       
            for (Iterator i = collection.collectionIterator(); i.hasNext(); ) {
            	XmldbURI child = (XmldbURI) i.next();
            	XmldbURI path = collUri.append(child);
                Collection childColl = broker.getCollection(path);
                Permission perm = childColl.getPermissions();
                Vector tmp = new Vector(3);
                tmp.addElement(perm.getOwner());
                tmp.addElement(perm.getOwnerGroup());
                tmp.addElement(new Integer(perm.getPermissions()));
                result.put(child, tmp);
            }
            return result;
        } finally {
            if(collection != null)
                collection.release(Lock.READ_LOCK);
            brokerPool.release(broker);
        }
    }
    
    public int getHits(User user, int resultId) throws EXistException {
        QueryResult qr = connectionPool.resultSets.get(resultId);
        if (qr == null)
            throw new EXistException("result set unknown or timed out");
        qr.timestamp = System.currentTimeMillis();
        if (qr.result == null)
            return 0;
        return qr.result.getItemCount();
    }
    
    public Hashtable getPermissions(User user, String name)
    throws EXistException, PermissionDeniedException, URISyntaxException {
    	return getPermissions(user,XmldbURI.xmldbUriFor(name));
    }
    public Hashtable getPermissions(User user, XmldbURI uri)
    throws EXistException, PermissionDeniedException {
        DBBroker broker = null;
        Collection collection = null;
        try {
            broker = brokerPool.get(user);
            collection = broker.openCollection(uri, Lock.READ_LOCK);
            Permission perm = null;
            if (collection == null) {
                DocumentImpl doc = null;
                try {
                	doc = broker.getXMLResource(uri, Lock.READ_LOCK);
	                if (doc == null)
	                    throw new EXistException("document or collection " + uri + " not found");
	                perm = doc.getPermissions();
                } finally {
                	if (doc != null)
                		doc.getUpdateLock().release(Lock.READ_LOCK);
                }
            } else {
                perm = collection.getPermissions();
            }
            Hashtable result = new Hashtable();
            result.put("owner", perm.getOwner());
            result.put("group", perm.getOwnerGroup());
            result.put("permissions", new Integer(perm.getPermissions()));
            return result;
        } finally {
        	if (collection != null)
                collection.release(Lock.READ_LOCK);
        	brokerPool.release(broker);
        }
    }
    
    public Date getCreationDate(User user, String collectionPath)
    throws PermissionDeniedException, EXistException, URISyntaxException {
    	return getCreationDate(user,XmldbURI.xmldbUriFor(collectionPath));
    }
    public Date getCreationDate(User user, XmldbURI collUri)
    throws PermissionDeniedException, EXistException {
        DBBroker broker = null;
        Collection collection = null;
        try {
            broker = brokerPool.get(user);
            collection = broker.openCollection(collUri, Lock.READ_LOCK);
            if (collection == null)
                throw new EXistException("collection " + collUri + " not found");
            return new Date(collection.getCreationTime());
        } finally {
            if(collection != null)
                collection.release(Lock.READ_LOCK);
            brokerPool.release(broker);
        }
    }
    
    public Vector getTimestamps(User user, String documentPath)
    throws PermissionDeniedException, EXistException, URISyntaxException {
    	return getTimestamps(user,XmldbURI.xmldbUriFor(documentPath));
    }
    public Vector getTimestamps(User user, XmldbURI docUri)
    throws PermissionDeniedException, EXistException {
        DBBroker broker = null;
        DocumentImpl doc = null;
        try {
            broker = brokerPool.get(user);
            doc = broker.getXMLResource(docUri, Lock.READ_LOCK);
            if (doc == null) {
                LOG.debug("document " + docUri + " not found!");
                throw new EXistException("document not found");
            }
            DocumentMetadata metadata = doc.getMetadata();
            Vector vector = new Vector(2);
            vector.addElement(new Date(metadata.getCreated()));
            vector.addElement(new Date(metadata.getLastModified()));
            return vector;
        } finally {
            if(doc != null)
                doc.getUpdateLock().release(Lock.READ_LOCK);
            brokerPool.release(broker);
        }
    }
    
    public Hashtable getUser(User user, String name) throws EXistException,
            PermissionDeniedException {
        User u = brokerPool.getSecurityManager().getUser(name);
        if (u == null)
            throw new EXistException("user " + name + " does not exist");
        Hashtable tab = new Hashtable();
        tab.put("name", u.getName());
        Vector groups = new Vector();
        String[] gl = u.getGroups();
		for (int i = 0; i < gl.length; i++)
			groups.addElement(gl[i]);
        tab.put("groups", groups);
        if (u.getHome() != null)
            tab.put("home", u.getHome().toString());
        return tab;
    }
    
    public Vector getUsers(User user) throws EXistException,
            PermissionDeniedException {
        User users[] = brokerPool.getSecurityManager().getUsers();
        Vector r = new Vector();
        for (int i = 0; i < users.length; i++) {
            final Hashtable tab = new Hashtable();
            tab.put("name", users[i].getName());
            Vector groups = new Vector();
            String[] gl = users[i].getGroups();
    		for (int j = 0; j < gl.length; j++)
                groups.addElement(gl[j]);
            tab.put("groups", groups);
            if (users[i].getHome() != null)
                tab.put("home", users[i].getHome().toString());
            r.addElement(tab);
        }
        return r;
    }
    
    public Vector getGroups(User user) throws EXistException,
            PermissionDeniedException {
        String[] groups = brokerPool.getSecurityManager().getGroups();
        Vector v = new Vector(groups.length);
        for (int i = 0; i < groups.length; i++) {
            v.addElement(groups[i]);
        }
        return v;
    }
    
    public boolean hasDocument(User user, String documentPath) throws Exception, URISyntaxException {
    	return hasDocument(user,XmldbURI.xmldbUriFor(documentPath));
    }
    public boolean hasDocument(User user, XmldbURI docUri) throws Exception {
        DBBroker broker = null;
        try {
            broker = brokerPool.get(user);
            return (broker.getXMLResource(docUri) != null);
        } finally {
            brokerPool.release(broker);
        }
    }
    
    public boolean hasCollection(User user, String collectionName) throws Exception, URISyntaxException {
    	return hasCollection(user,XmldbURI.xmldbUriFor(collectionName));
    }
    public boolean hasCollection(User user, XmldbURI collUri) throws Exception {
        DBBroker broker = null;
        try {
            broker = brokerPool.get(user);
            return (broker.getCollection(collUri) != null);
        } finally {
            brokerPool.release(broker);
        }
    }
    
    
    public boolean parse(User user, byte[] xml, String documentPath,
            boolean replace) throws Exception, URISyntaxException {
        return parse(user, xml,documentPath, replace, null, null);
    }
    
    public boolean parse(User user, byte[] xml, XmldbURI docUri,
            boolean replace) throws Exception {
        return parse(user, xml,docUri, replace, null, null);
    }
    
    public boolean parse(User user, byte[] xml, String documentPath,
            boolean replace, Date created, Date modified) throws Exception, URISyntaxException {
    	return parse(user,xml,XmldbURI.xmldbUriFor(documentPath),replace,created,modified);
    }
    public boolean parse(User user, byte[] xml, XmldbURI docUri,
            boolean replace, Date created, Date modified) throws Exception {
        DBBroker broker = null;       
        TransactionManager transact = brokerPool.getTransactionManager();
        Txn transaction = transact.beginTransaction();
        try {
            long startTime = System.currentTimeMillis();
            broker = brokerPool.get(user);
         
            IndexInfo info = null;
            InputSource source = null;
            Collection collection = null;
            try {
            	collection = broker.openCollection(docUri.removeLastSegment(), Lock.WRITE_LOCK);
	            if (collection == null) {
	                transact.abort(transaction);
	                throw new EXistException("Collection " + docUri.removeLastSegment() + " not found");
	            }
	
	            if (!replace) {
	                DocumentImpl old = collection.getDocument(broker, docUri.lastSegment());
	                //TODO : register the lock within the transaction ?
	                if (old != null) {
	                    transact.abort(transaction);
	                    throw new PermissionDeniedException("Document exists and overwrite is not allowed");
	                }
	            }
	            
	            InputStream is = new ByteArrayInputStream(xml);
	            source = new InputSource(is);
	            info = collection.validateXMLResource(transaction, broker, docUri.lastSegment(), source);
	            
	            if (created != null)
	                info.getDocument().getMetadata().setCreated( created.getTime());            
	            
	            if (modified != null)
	                info.getDocument().getMetadata().setLastModified( modified.getTime());
	            
            } finally {
            	if (collection != null)
            		collection.release(Lock.WRITE_LOCK);
            }
            
            collection.store(transaction, broker, info, source, false);
            transact.commit(transaction);
            
            LOG.debug("parsing " + docUri + " took " + (System.currentTimeMillis() - startTime) + "ms.");
            documentCache.clear();
            return true;
        } catch (Exception e) {
            transact.abort(transaction);
            LOG.debug(e.getMessage(), e);
            throw e;
        } finally {
            brokerPool.release(broker);
        }
    }
    
    
    
    /**
     * Parse a file previously uploaded with upload.
     *
     * The temporary file will be removed.
     *
     * @param user
     * @param localFile
     * @throws EXistException
     * @throws IOException
     */
    public boolean parseLocal(User user, String localFile, String documentPath,
            boolean replace, String mimeType) throws Exception, URISyntaxException {
        return parseLocal(user, localFile, documentPath, replace, mimeType, null, null);
    }
    
    public boolean parseLocal(User user, String localFile, XmldbURI docUri,
            boolean replace, String mimeType) throws Exception {
        return parseLocal(user, localFile, docUri, replace, mimeType, null, null);
    }
    
    public boolean parseLocal(User user, String localFile, String documentPath,
            boolean replace, String mimeType, Date created, Date modified) throws Exception, URISyntaxException {
    	return parseLocal(user,localFile,XmldbURI.xmldbUriFor(documentPath), replace, mimeType, created, modified);
    }
    
    public boolean parseLocal(User user, String localFile, XmldbURI docUri,
            boolean replace, String mimeType, Date created, Date modified) throws Exception {
        
    	File file = new File(localFile);
        if (!file.canRead())
            throw new EXistException("unable to read file " + localFile);
    
        TransactionManager transact = brokerPool.getTransactionManager();
        Txn transaction = transact.beginTransaction();
        DBBroker broker = null;
        DocumentImpl doc = null;
        
        // DWES
        MimeType mime = MimeTable.getInstance().getContentType(mimeType);
        if (mime == null)
            mime = MimeType.BINARY_TYPE;
                    
        try {
            broker = brokerPool.get(user);
            Collection collection = null;
            IndexInfo info = null;
            InputSource source = null;
            
            try {

	            collection = broker.openCollection(docUri.removeLastSegment(), Lock.WRITE_LOCK);
	            if (collection == null) {
	                transact.abort(transaction);
	                throw new EXistException("Collection " + docUri.removeLastSegment() + " not found");
	            }
	
	            if (!replace) {
	                DocumentImpl old = collection.getDocument(broker, docUri.lastSegment());
	                if (old != null) {
	                    transact.abort(transaction);
	                    throw new PermissionDeniedException("Old document exists and overwrite is not allowed");
	                }
	            }
	            
	            //XML
	            if(mime.isXMLType()) {
	                source = new InputSource(file.toURI().toASCIIString());
	                info = collection.validateXMLResource(transaction, broker, docUri.lastSegment(), source);
	                if (created != null)
	                    info.getDocument().getMetadata().setCreated(created.getTime());	                
	                if (modified != null)
	                    info.getDocument().getMetadata().setLastModified(modified.getTime());	                
	            } else {
	                FileInputStream is = new FileInputStream(file);
	                doc = collection.addBinaryResource(transaction, broker, docUri.lastSegment(), is, 
	                        mime.getName(), (int) file.length());
	                is.close();
	                if (created != null)
	                    doc.getMetadata().setCreated(created.getTime());
	                if (modified != null)
	                    doc.getMetadata().setLastModified(modified.getTime());
	            }
	            
            } finally {
            	if (collection != null)
            		collection.release(Lock.WRITE_LOCK);
            }

            // DWES why seperate store?
            if(mime.isXMLType()){
                collection.store(transaction, broker, info, source, false);
            }
            
            // generic
            transact.commit(transaction);
        } catch (Exception e) {
            transact.abort(transaction);
            throw e;
        } finally {
            brokerPool.release(broker);
        }
        
        // DWES there are situations the file is not cleaned up
        file.delete();
        
        documentCache.clear();
        return true; // when arrived here, insert/update was successfull
    }
    
    
    
    
    
    public boolean storeBinary(User user, byte[] data, String documentPath, String mimeType,
            boolean replace) throws Exception, URISyntaxException {
        return storeBinary(user, data, documentPath, mimeType, replace, null, null);
    }

    public boolean storeBinary(User user, byte[] data, XmldbURI docUri, String mimeType,
            boolean replace) throws Exception {
        return storeBinary(user, data, docUri, mimeType, replace, null, null);
    }

    public boolean storeBinary(User user, byte[] data, String documentPath, String mimeType,
            boolean replace, Date created, Date modified) throws Exception, URISyntaxException {
    	return storeBinary(user,data,XmldbURI.xmldbUriFor(documentPath),mimeType,replace,created,modified);
    }    
    public boolean storeBinary(User user, byte[] data, XmldbURI docUri, String mimeType,
            boolean replace, Date created, Date modified) throws Exception {
        DBBroker broker = null;
        DocumentImpl doc = null;   
        TransactionManager transact = brokerPool.getTransactionManager();
        Txn transaction = transact.beginTransaction();
        try {
            broker = brokerPool.get(user);
            Collection collection = broker.openCollection(docUri.removeLastSegment(), Lock.WRITE_LOCK);
            if (collection == null) {
            	transact.abort(transaction);
                throw new EXistException("Collection " + docUri.removeLastSegment() + " not found");
            }
            // keep the write lock in the transaction
            transaction.registerLock(collection.getLock(), Lock.WRITE_LOCK);
            if (!replace) {
                DocumentImpl old = collection.getDocument(broker, docUri.lastSegment());
                if (old != null) {
                	transact.abort(transaction);
                    throw new PermissionDeniedException("Old document exists and overwrite is not allowed");
                }
            }
            LOG.debug("Storing binary resource to collection " + collection.getURI());
            
            doc = collection.addBinaryResource(transaction, broker, docUri.lastSegment(), data, mimeType);
            if (created != null)
                doc.getMetadata().setCreated(created.getTime());
            if (modified != null)
                doc.getMetadata().setLastModified(modified.getTime());
            transact.commit(transaction);
        } catch (Exception e) {
            transact.abort(transaction);
            throw e;
        } finally {
            brokerPool.release(broker);
        }
        documentCache.clear();
        return doc != null;
    }
    
    public String upload(User user, byte[] chunk, int length, String fileName, boolean compressed)
    throws EXistException, IOException {
        File file;
        if (fileName == null || fileName.length() == 0) {
            // create temporary file
            file = File.createTempFile("rpc", ".xml");
            fileName = file.getAbsolutePath();
            LOG.debug("created temporary file " + file.getAbsolutePath());
        } else {
//            LOG.debug("appending to file " + fileName);
            file = new File(fileName);
        }
        if (!file.canWrite())
            throw new EXistException("cannot write to file " + fileName);
        FileOutputStream os = new FileOutputStream(file, true);
        if (compressed)
            Compressor.uncompress(chunk, os);
        else
            os.write(chunk, 0, length);
        os.close();
        return fileName;
    }
    
    protected String printAll(DBBroker broker, Sequence resultSet, int howmany,
            int start, Hashtable properties, long queryTime) throws Exception {
        if (resultSet.isEmpty())
            return "<?xml version=\"1.0\"?>\n"
                    + "<exist:result xmlns:exist=\""+ Namespaces.EXIST_NS + "\" "
                    + "hitCount=\"0\"/>";
        if (howmany > resultSet.getItemCount() || howmany == 0)
            howmany = resultSet.getItemCount();
        
        if (start < 1 || start > resultSet.getItemCount())
            throw new EXistException("start parameter out of range");
        
        StringWriter writer = new StringWriter();
        writer.write("<exist:result xmlns:exist=\"");
        writer.write(Namespaces.EXIST_NS);
        writer.write("\" hits=\"");
        writer.write(Integer.toString(resultSet.getItemCount()));
        writer.write("\" start=\"");
        writer.write(Integer.toString(start));
        writer.write("\" count=\"");
        writer.write(Integer.toString(howmany));
        writer.write("\">\n");
        
        Serializer serializer = broker.getSerializer();
        serializer.reset();
        serializer.setProperties(properties);
        
        Item item;
        for (int i = --start; i < start + howmany; i++) {
            item = resultSet.itemAt(i);
            if (item == null)
                continue;
            if (item.getType() == Type.ELEMENT) {
                NodeValue node = (NodeValue) item;
                writer.write(serializer.serialize(node));
            } else {
                writer.write("<exist:value type=\"");
                writer.write(Type.getTypeName(item.getType()));
                writer.write("\">");
                writer.write(item.getStringValue());
                writer.write("</exist:value>");
            }
        }
        writer.write("\n</exist:result>");
        return writer.toString();
    }
    
    public Hashtable compile(User user, String query, Hashtable parameters) throws Exception {
        Hashtable ret = new Hashtable();
        DBBroker broker = null;
        XQueryPool pool = null;
        CompiledXQuery compiled = null;
        Source source = new StringSource(query);
        try {
            broker = brokerPool.get(user);
            XQuery xquery = broker.getXQueryService();
            pool = xquery.getXQueryPool();
            compiled = compile(user, broker, source, parameters);
        } catch (XPathException e) {
            ret.put(RpcAPI.ERROR, e.getMessage());
            if(e.getLine() != 0) {
                ret.put(RpcAPI.LINE, new Integer(e.getLine()));
                ret.put(RpcAPI.COLUMN, new Integer(e.getColumn()));
            }
        } finally {
            brokerPool.release(broker);
            if(compiled != null && pool != null)
                pool.returnCompiledXQuery(source, compiled);
        }
        return ret;
    }
    
    public String query(User user, String xpath, int howmany, int start,
            Hashtable parameters) throws Exception {
        long startTime = System.currentTimeMillis();
        String result;
        DBBroker broker = null;
        try {
            broker = brokerPool.get(user);
            QueryResult qr = doQuery(user, broker, xpath, null, parameters);
            if (qr.hasErrors())
                throw qr.getException();
            if (qr == null)
                return "<?xml version=\"1.0\"?>\n"
                        + "<exist:result xmlns:exist=\"" + Namespaces.EXIST_NS + "\" "
                        + "hitCount=\"0\"/>";
            
            result = printAll(broker, qr.result, howmany, start, parameters,
                    (System.currentTimeMillis() - startTime));
        } finally {
            brokerPool.release(broker);
        }
        return result;
    }
    
    public Hashtable queryP(User user, String xpath, String documentPath,
            String s_id, Hashtable parameters) throws Exception, URISyntaxException {
    	return queryP(user,xpath,(documentPath==null)?null:XmldbURI.xmldbUriFor(documentPath),s_id,parameters);
    }    
    public Hashtable queryP(User user, String xpath, XmldbURI docUri,
            String s_id, Hashtable parameters) throws Exception {
        long startTime = System.currentTimeMillis();
        String sortBy = (String) parameters.get(RpcAPI.SORT_EXPR);
        
        Hashtable ret = new Hashtable();
        Vector result = new Vector();
        NodeSet nodes = null;
        QueryResult queryResult;
        Sequence resultSeq = null;
        DBBroker broker = null;
        try {
            broker = brokerPool.get(user);
            if (docUri != null && s_id != null) {
                DocumentImpl doc;
                if (!documentCache.containsKey(docUri)) {
                    doc = (DocumentImpl) broker.getXMLResource(docUri);
                    documentCache.put(docUri, doc);
                } else
                    doc = (DocumentImpl) documentCache.get(docUri);
                Vector docs = new Vector(1);
                docs.addElement(docUri.toString());
                parameters.put(RpcAPI.STATIC_DOCUMENTS, docs);
                
                if(s_id.length() > 0) {
                    NodeId nodeId = brokerPool.getNodeFactory().createFromString(s_id);
                    NodeProxy node = new NodeProxy(doc, nodeId);
                    nodes = new ExtArrayNodeSet(1);
                    nodes.add(node);
                }
            }
            queryResult = doQuery(user, broker, xpath, nodes, parameters);
            if (queryResult == null)
                return ret;
            if (queryResult.hasErrors()) {
                // return an error description
                XPathException e = queryResult.getException();
                ret.put(RpcAPI.ERROR, e.getMessage());
                if(e.getLine() != 0) {
                    ret.put(RpcAPI.LINE, new Integer(e.getLine()));
                    ret.put(RpcAPI.COLUMN, new Integer(e.getColumn()));
                }
                return ret;
            }
            resultSeq = queryResult.result;
            if (LOG.isDebugEnabled())
            	LOG.debug("found " + resultSeq.getItemCount());
            
            if (sortBy != null) {
                SortedNodeSet sorted = new SortedNodeSet(brokerPool, user,
                        sortBy, AccessContext.XMLRPC);
                sorted.addAll(resultSeq);
                resultSeq = sorted;
            }
            NodeProxy p;
            Vector entry;
            if (resultSeq != null) {
                SequenceIterator i = resultSeq.iterate();
                if (i != null) {
                    Item next;
                    while (i.hasNext()) {
                        next = i.nextItem();
                        if (Type.subTypeOf(next.getType(), Type.NODE)) {
                            entry = new Vector();
                            if (((NodeValue) next).getImplementationType() == NodeValue.PERSISTENT_NODE) {
                                p = (NodeProxy) next;
                                entry.addElement(p.getDocument().getURI().toString());
                                entry.addElement(p.getNodeId().toString());
                            } else {
                                entry.addElement("temp_xquery/" + next.hashCode());
                                entry.addElement(String.valueOf(((NodeImpl) next).getNodeNumber()));
                            }
                            result.addElement(entry);
                        } else
                            result.addElement(next.getStringValue());
                    }
                } else {
                    LOG.debug("sequence iterator is null. Should not");
                }
            } else
                LOG.debug("result sequence is null. Skipping it...");
        } finally {
            brokerPool.release(broker);
        }
        queryResult.result = resultSeq;
        queryResult.queryTime = (System.currentTimeMillis() - startTime);
        int id = connectionPool.resultSets.add(queryResult);
        ret.put("id", new Integer(id));
        ret.put("results", result);
        return ret;
    }
    
    public Hashtable execute(User user, String xpath, Hashtable parameters) throws Exception {
        long startTime = System.currentTimeMillis();
        String sortBy = (String) parameters.get(RpcAPI.SORT_EXPR);
        
        Hashtable ret = new Hashtable();
        Vector result = new Vector();
        NodeSet nodes = null;
        QueryResult queryResult;
        Sequence resultSeq = null;
        DBBroker broker = null;
        try {
            broker = brokerPool.get(user);
            
            queryResult = doQuery(user, broker, xpath, nodes, parameters);
            if (queryResult == null)
                return ret;
            if (queryResult.hasErrors()) {
                // return an error description
                XPathException e = queryResult.getException();
                ret.put(RpcAPI.ERROR, e.getMessage());
                if(e.getLine() != 0) {
                    ret.put(RpcAPI.LINE, new Integer(e.getLine()));
                    ret.put(RpcAPI.COLUMN, new Integer(e.getColumn()));
                }
                return ret;
            }
            resultSeq = queryResult.result;
            if (LOG.isDebugEnabled())
            	LOG.debug("found " + resultSeq.getItemCount());
            
            if (sortBy != null) {
                SortedNodeSet sorted = new SortedNodeSet(brokerPool, user,
                        sortBy, AccessContext.XMLRPC);
                sorted.addAll(resultSeq);
                resultSeq = sorted;
            }
            NodeProxy p;
            Vector entry;
            if (resultSeq != null) {
                SequenceIterator i = resultSeq.iterate();
                if (i != null) {
                    Item next;
                    while (i.hasNext()) {
                        next = i.nextItem();
                        if (Type.subTypeOf(next.getType(), Type.NODE)) {
                            entry = new Vector();
                            if (((NodeValue) next).getImplementationType() == NodeValue.PERSISTENT_NODE) {
                                p = (NodeProxy) next;
                                entry.addElement(p.getDocument().getURI().toString());
                                entry.addElement(p.getNodeId().toString());
                            } else {
                                entry.addElement("temp_xquery/"
                                        + next.hashCode());
                                entry.addElement(String
                                        .valueOf(((NodeImpl) next)
                                        .getNodeNumber()));
                            }
                            result.addElement(entry);
                        } else
                            result.addElement(next.getStringValue());
                    }
                } else {
                    LOG.debug("sequence iterator is null. Should not");
                }
            } else
                LOG.debug("result sequence is null. Skipping it...");
        } finally {
            brokerPool.release(broker);
        }
        queryResult.result = resultSeq;
        queryResult.queryTime = (System.currentTimeMillis() - startTime);
        int id = connectionPool.resultSets.add(queryResult);
        ret.put("id", new Integer(id));
        ret.put("results", result);
        return ret;
    }
    
    public void releaseQueryResult(int handle) {
        connectionPool.resultSets.remove(handle);
        documentCache.clear();
        LOG.debug("removed query result with handle " + handle);
    }
    
    public void remove(User user, String documentPath) throws Exception, URISyntaxException {
    	remove(user,XmldbURI.xmldbUriFor(documentPath));
    }    
    public void remove(User user, XmldbURI docUri) throws Exception {
        TransactionManager transact = brokerPool.getTransactionManager();
        Txn transaction = transact.beginTransaction();
        DBBroker broker = null;
        Collection collection = null;
        try {
            broker = brokerPool.get(user);
            collection = broker.openCollection(docUri.removeLastSegment(), Lock.WRITE_LOCK);
            if (collection == null) {
                transact.abort(transaction);
                throw new EXistException("Collection " + docUri.removeLastSegment() + " not found");
            }
            // keep the write lock in the transaction
            transaction.registerLock(collection.getLock(), Lock.WRITE_LOCK);

            DocumentImpl doc = collection.getDocument(broker, docUri.lastSegment());
            if (doc == null) {
                transact.abort(transaction);
                throw new EXistException("Document " + docUri + " not found");
            }
            
            if(doc.getResourceType() == DocumentImpl.BINARY_FILE)
                collection.removeBinaryResource(transaction, broker, doc);
            else
                collection.removeXMLResource(transaction, broker, docUri.lastSegment());
            transact.commit(transaction);
            documentCache.clear();
        } finally {
            brokerPool.release(broker);
        }
    }
    
    public boolean removeCollection(User user, String collectionName) throws Exception, URISyntaxException {
    	return removeCollection(user,XmldbURI.xmldbUriFor(collectionName));
    } 
    
    public boolean removeCollection(User user, XmldbURI collURI) throws Exception {
        TransactionManager transact = brokerPool.getTransactionManager();
        Txn transaction = transact.beginTransaction();
        DBBroker broker = null;
        Collection collection = null;
        try {
            broker = brokerPool.get(user);
            collection = broker.openCollection(collURI, Lock.WRITE_LOCK);
            if (collection == null) {
            	transact.abort(transaction);
                return false;
            }
            // keep the write lock in the transaction
            transaction.registerLock(collection.getLock(), Lock.WRITE_LOCK);
            LOG.debug("removing collection " + collURI);
            documentCache.clear();
            boolean removed = broker.removeCollection(transaction, collection);
            transact.commit(transaction);
            return removed;
        } catch (Exception e) {
            transact.abort(transaction);
            throw e;
        } finally {
            brokerPool.release(broker);
        }
    }
    
    public boolean removeUser(User user, String name) throws EXistException,
            PermissionDeniedException {
        SecurityManager manager = brokerPool
                .getSecurityManager();
        if (!manager.hasAdminPrivileges(user))
            throw new PermissionDeniedException(
                    "you are not allowed to remove users");
        
        manager.deleteUser(name);
        return true;
    }
    
    public String retrieve(User user, String documentPath, String s_id,
            Hashtable parameters) throws Exception, URISyntaxException {
    	return retrieve(user,XmldbURI.xmldbUriFor(documentPath),s_id,parameters);
    }    
    public String retrieve(User user, XmldbURI docUri, String s_id,
            Hashtable parameters) throws Exception {
        DBBroker broker = brokerPool.get(user);
        try {
            NodeId nodeId = brokerPool.getNodeFactory().createFromString(s_id);
            DocumentImpl doc;
            if (!documentCache.containsKey(docUri)) {
                LOG.debug("loading doc " + docUri);
                doc = (DocumentImpl) broker.getXMLResource(docUri);
                documentCache.put(docUri, doc);
            } else
                doc = (DocumentImpl) documentCache.get(docUri);
            
            NodeProxy node = new NodeProxy(doc, nodeId);
            Serializer serializer = broker.getSerializer();
            serializer.reset();
            serializer.setProperties(parameters);
            return serializer.serialize(node);
        } finally {
            brokerPool.release(broker);
        }
    }
    
    public String retrieve(User user, int resultId, int num,
            Hashtable parameters) throws Exception {
        DBBroker broker = null;
        try {
            broker = brokerPool.get(user);
            QueryResult qr = connectionPool.resultSets.get(resultId);
            if (qr == null)
                throw new EXistException("result set unknown or timed out");
            qr.timestamp = System.currentTimeMillis();
            Item item = qr.result.itemAt(num);
            if (item == null)
                throw new EXistException("index out of range");
            
            if(Type.subTypeOf(item.getType(), Type.NODE)) {
                NodeValue nodeValue = (NodeValue)item;
                Serializer serializer = broker.getSerializer();
                serializer.reset();
                checkPragmas(qr.context, parameters);
                serializer.setProperties(parameters);
                return serializer.serialize(nodeValue);
            } else {
                return item.getStringValue();
            }
        } finally {
            brokerPool.release(broker);
        }
    }
    
    public String retrieveAll(User user, int resultId, Hashtable parameters) throws Exception {
        DBBroker broker = null;
        try {
            broker = brokerPool.get(user);
            QueryResult qr = connectionPool.resultSets.get(resultId);
            if (qr == null)
                throw new EXistException("result set unknown or timed out");
            qr.timestamp = System.currentTimeMillis();
            checkPragmas(qr.context, parameters);
            Serializer serializer = broker.getSerializer();
            serializer.reset();
            serializer.setProperties(parameters);
            
            SAXSerializer handler = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);
            StringWriter writer = new StringWriter();
            handler.setOutput(writer, getProperties(parameters));
            
//			serialize results
            handler.startDocument();
            handler.startPrefixMapping("exist", Namespaces.EXIST_NS);
            AttributesImpl attribs = new AttributesImpl();
            attribs.addAttribute(
                    "",
                    "hitCount",
                    "hitCount",
                    "CDATA",
                    Integer.toString(qr.result.getItemCount()));
            handler.startElement(
            		Namespaces.EXIST_NS,
                    "result",
                    "exist:result",
                    attribs);
            Item current;
            char[] value;
            for(SequenceIterator i = qr.result.iterate(); i.hasNext(); ) {
                current = i.nextItem();
                if(Type.subTypeOf(current.getType(), Type.NODE))
                    ((NodeValue)current).toSAX(broker, handler, null);
                else {
                    value = current.toString().toCharArray();
                    handler.characters(value, 0, value.length);
                }
            }
            handler.endElement(Namespaces.EXIST_NS, "result", "exist:result");
            handler.endPrefixMapping("exist");
            handler.endDocument();
            SerializerPool.getInstance().returnObject(handler);
            return writer.toString();
        } finally {
            brokerPool.release(broker);
        }
    }
    
    public void run() {
        synchronized (this) {
            while (!terminate)
                try {
                    this.wait(500);
                } catch (InterruptedException inte) {
                }
            
        }
        // broker.shutdown();
    }
    
    public boolean setPermissions(User user, String resource, String owner,
            String ownerGroup, String permissions) throws EXistException,
            PermissionDeniedException, URISyntaxException {
    	return setPermissions(user,XmldbURI.xmldbUriFor(resource),owner,ownerGroup,permissions);
    }    
    public boolean setPermissions(User user, XmldbURI uri, String owner,
            String ownerGroup, String permissions) throws EXistException,
            PermissionDeniedException {
        DBBroker broker = null;
        Collection collection = null;
        DocumentImpl doc = null;
        TransactionManager transact = brokerPool.getTransactionManager();
        Txn transaction = transact.beginTransaction();
        try {
            broker = brokerPool.get(user);
            SecurityManager manager = brokerPool
                    .getSecurityManager();
            collection = broker.openCollection(uri, Lock.WRITE_LOCK);
            if (collection == null) {
                doc = broker.getXMLResource(uri, Lock.WRITE_LOCK);
                if (doc == null) {
                	transact.abort(transaction);
                	throw new EXistException("document or collection " + uri + " not found");
                }
                //TODO : register the lock within the transaction ?
                LOG.debug("changing permissions on document " + uri);
                Permission perm = doc.getPermissions();
                if (perm.getOwner().equals(user.getName())
                || manager.hasAdminPrivileges(user)) {
                    if (owner != null) {
                        perm.setOwner(owner);
                        if (!manager.hasGroup(ownerGroup))
                            manager.addGroup(ownerGroup);
                        perm.setGroup(ownerGroup);
                    }
                    if (permissions != null && permissions.length() > 0)
                        perm.setPermissions(permissions);
                    broker.storeXMLResource(transaction, doc);
                    transact.commit(transaction);
                    broker.flush();
                    return true;
                }
                transact.abort(transaction);
                throw new PermissionDeniedException("not allowed to change permissions");
            } else {
                // keep the write lock in the transaction
                transaction.registerLock(collection.getLock(), Lock.WRITE_LOCK);            
                LOG.debug("changing permissions on collection " + uri);
                Permission perm = collection.getPermissions();
                if (perm.getOwner().equals(user.getName())
                || manager.hasAdminPrivileges(user)) {
                    if (permissions != null)
                        perm.setPermissions(permissions);
                    if (owner != null) {
                        perm.setOwner(owner);
                        if (!manager.hasGroup(ownerGroup))
                            manager.addGroup(ownerGroup);
                        perm.setGroup(ownerGroup);
                    }
                    broker.saveCollection(transaction, collection);
                    transact.commit(transaction);
                    broker.flush();
                    return true;
                }
                transact.abort(transaction);
                throw new PermissionDeniedException("not allowed to change permissions");
            }
        } catch (SyntaxException e) {
            transact.abort(transaction);
            throw new EXistException(e.getMessage());
        } catch (PermissionDeniedException e) {
            transact.abort(transaction);
            throw new EXistException(e.getMessage());
        } catch (IOException e) {
            transact.abort(transaction);
            throw new EXistException(e.getMessage());
        } finally {
            if(doc != null)
                doc.getUpdateLock().release(Lock.WRITE_LOCK);
            brokerPool.release(broker);
        }
    }
    
    public boolean setPermissions(User user, String resource, String owner,
            String ownerGroup, int permissions) throws EXistException,
            PermissionDeniedException, URISyntaxException {
    	return setPermissions(user,XmldbURI.xmldbUriFor(resource),owner,ownerGroup,permissions);
    }    
    public boolean setPermissions(User user, XmldbURI uri, String owner,
            String ownerGroup, int permissions) throws EXistException,
            PermissionDeniedException {
        DBBroker broker = null;
        Collection collection = null;
        DocumentImpl doc = null;
        TransactionManager transact = brokerPool.getTransactionManager();
        Txn transaction = transact.beginTransaction();
        try {
            broker = brokerPool.get(user);
            SecurityManager manager = brokerPool.getSecurityManager();
            collection = broker.openCollection(uri, Lock.WRITE_LOCK);
            if (collection == null) {
                doc = broker.getXMLResource(uri, Lock.WRITE_LOCK);
                if (doc == null) {
                	transact.abort(transaction);
                	throw new EXistException("document or collection " + uri + " not found");
                }
                //TODO : register the lock within the transaction ?
                LOG.debug("changing permissions on document " + uri);
                Permission perm = doc.getPermissions();
                if (perm.getOwner().equals(user.getName())
                || manager.hasAdminPrivileges(user)) {
                    if (owner != null) {
                        perm.setOwner(owner);
                        if (!manager.hasGroup(ownerGroup))
                            manager.addGroup(ownerGroup);
                        perm.setGroup(ownerGroup);
                    }
                    perm.setPermissions(permissions);
                    broker.storeXMLResource(transaction, doc);
                    transact.commit(transaction);
                    broker.flush();
                    return true;
                }
                transact.abort(transaction);
                throw new PermissionDeniedException("not allowed to change permissions");
            }
            //keep the write lock in the transaction
            transaction.registerLock(collection.getLock(), Lock.WRITE_LOCK);
            LOG.debug("changing permissions on collection " + uri);
            Permission perm = collection.getPermissions();
            if (perm.getOwner().equals(user.getName())
            || manager.hasAdminPrivileges(user)) {
                perm.setPermissions(permissions);
                if (owner != null) {
                    perm.setOwner(owner);
                    if (!manager.hasGroup(ownerGroup))
                        manager.addGroup(ownerGroup);
                    perm.setGroup(ownerGroup);
                }                
                broker.saveCollection(transaction, collection);
                transact.commit(transaction);
                broker.flush();
                return true;
            }
            transact.abort(transaction);
            throw new PermissionDeniedException("not allowed to change permissions");
        } catch (PermissionDeniedException e) {
            transact.abort(transaction);
            throw new EXistException(e.getMessage());
        } catch (IOException e) {
            transact.abort(transaction);
            throw new EXistException(e.getMessage());
        } finally {
            if(doc != null)
                doc.getUpdateLock().release(Lock.WRITE_LOCK);
            brokerPool.release(broker);
        }
    }
    
    public boolean setUser(User user, String name, String passwd, String passwdDigest,
            Vector groups, String home) throws EXistException,
            PermissionDeniedException {
        if (passwd.length() == 0)
            passwd = null;
        SecurityManager manager = brokerPool
                .getSecurityManager();
        User u;
        if (!manager.hasUser(name)) {
            if (!manager.hasAdminPrivileges(user))
                throw new PermissionDeniedException(
                        "not allowed to create user");
            u = new User(name);
            u.setEncodedPassword(passwd);
            u.setPasswordDigest(passwdDigest);
        } else {
            u = manager.getUser(name);
            if (!(u.getName().equals(user.getName()) || manager
                    .hasAdminPrivileges(user)))
                throw new PermissionDeniedException(
                        "you are not allowed to change this user");
            u.setEncodedPassword(passwd);
            u.setPasswordDigest(passwdDigest);
        }
        String g;
        for (Iterator i = groups.iterator(); i.hasNext(); ) {
            g = (String) i.next();
            if (!u.hasGroup(g)) {
                if(!manager.hasAdminPrivileges(user))
                    throw new PermissionDeniedException(
                            "User is not allowed to add groups");
                u.addGroup(g);
            }
        }
        if (home != null) {
        	try {
                u.setHome(XmldbURI.xmldbUriFor(home));
        	} catch(URISyntaxException e) {
        		throw new EXistException("Invalid home URI",e);
        	}
        }
         manager.setUser(u);
        return true;
    }
    
    /**
     * Added by {Marco.Tampucci, Massimo.Martinelli} @isti.cnr.it
     */
    public boolean setUser(User user, String name, Vector groups) throws EXistException,
    PermissionDeniedException {

    	SecurityManager manager = brokerPool
        	.getSecurityManager();
    	User u;
    	if (!manager.hasUser(name)) {
    		if (!manager.hasAdminPrivileges(user))
    			throw new PermissionDeniedException(
                	"not allowed to create user");
    		u = new User(name);
    	} else {
    		u = manager.getUser(name);
    		if (!(u.getName().equals(user.getName()) || manager
    			.hasAdminPrivileges(user)))
    			throw new PermissionDeniedException(
                	"you are not allowed to change this user");
    	}
    	String g;
    	for (Iterator i = groups.iterator(); i.hasNext(); ) {
    		g = (String) i.next();
    		if (!u.hasGroup(g)) {
    			if(!manager.hasAdminPrivileges(user))
    				throw new PermissionDeniedException(
                    	"User is not allowed to add groups");
    			u.addGroup(g);
    		}
    	}
    	manager.setUser(u);
    	return true;
    }
    
    /**
     * Added by {Marco.Tampucci, Massimo.Martinelli} @isti.cnr.it
     */
    public boolean setUser(User user, String name, Vector groups, String rgroup) throws EXistException,
    PermissionDeniedException {

    	SecurityManager manager = brokerPool
        	.getSecurityManager();
    	User u;
    	if (!manager.hasUser(name)) {
    		if (!manager.hasAdminPrivileges(user))
    			throw new PermissionDeniedException(
                	"not allowed to create user");
    		u = new User(name);
    	} else {
    		u = manager.getUser(name);
    		if (!(u.getName().equals(user.getName()) || manager
    				.hasAdminPrivileges(user)))
    			throw new PermissionDeniedException(
                	"you are not allowed to change this user");
    	}
    	String g;
    	for (Iterator i = groups.iterator(); i.hasNext(); ) {
    		g = (String) i.next();
    		if (g.equals(rgroup)) {
    			if(!manager.hasAdminPrivileges(user))
    				throw new PermissionDeniedException(
                    	"User is not allowed to remove groups");
    			u.remGroup(g);
    		}
    	}
    	manager.setUser(u);
    	return true;
    }
    
    public boolean lockResource(User user, String documentPath, String userName) throws Exception, URISyntaxException {
    	return lockResource(user,XmldbURI.xmldbUriFor(documentPath),userName);
    }    
    public boolean lockResource(User user, XmldbURI docURI, String userName) throws Exception {
        DBBroker broker = null;
        DocumentImpl doc = null;
        TransactionManager transact = brokerPool.getTransactionManager();
        Txn transaction = transact.beginTransaction();
        try {
            broker = brokerPool.get(user);
            doc = broker.getXMLResource(docURI, Lock.WRITE_LOCK);           
            if (doc == null) {
                throw new EXistException("Resource " + docURI + " not found");
            }
            //TODO : register the lock within the transaction ?
            if (!doc.getPermissions().validate(user, Permission.UPDATE))
                throw new PermissionDeniedException("User is not allowed to lock resource " + docURI);
            SecurityManager manager = brokerPool.getSecurityManager();
            if (!(userName.equals(user.getName()) || manager.hasAdminPrivileges(user)))
                throw new PermissionDeniedException("User " + user.getName() + " is not allowed " +
                        "to lock the resource for user " + userName);
            User lockOwner = doc.getUserLock();
            if(lockOwner != null && (!lockOwner.equals(user)) && (!manager.hasAdminPrivileges(user)))
                throw new PermissionDeniedException("Resource is already locked by user " +
                        lockOwner.getName());
            doc.setUserLock(user);
            broker.storeXMLResource(transaction, doc);
            transact.commit(transaction);
            return true;
        } catch (Exception e) {
            transact.abort(transaction);
            throw e;
        } finally {
            if(doc != null)
                doc.getUpdateLock().release(Lock.WRITE_LOCK);
            brokerPool.release(broker);
        }
    }
    
    public String hasUserLock(User user, String documentPath) throws Exception, URISyntaxException {
    	return hasUserLock(user,XmldbURI.xmldbUriFor(documentPath));
    }    
    public String hasUserLock(User user, XmldbURI docURI) throws Exception {
        DBBroker broker = null;
        DocumentImpl doc = null;
        try {
            broker = brokerPool.get(user);
            doc = broker.getXMLResource(docURI, Lock.READ_LOCK);
            if(!doc.getPermissions().validate(user, Permission.READ))
                throw new PermissionDeniedException("Insufficient privileges to read resource");
            if (doc == null)
                throw new EXistException("Resource " + docURI + " not found");
            User u = doc.getUserLock();
            return u == null ? "" : u.getName();
        } finally {
            if(doc != null)
                doc.getUpdateLock().release(Lock.READ_LOCK);
            brokerPool.release(broker);
        }
    }
    
    public boolean unlockResource(User user, String documentPath) throws Exception, URISyntaxException {
    	return unlockResource(user,XmldbURI.xmldbUriFor(documentPath));
    }    
    public boolean unlockResource(User user, XmldbURI docURI) throws Exception {
        DBBroker broker = null;
        DocumentImpl doc = null;
        try {
            broker = brokerPool.get(user);
            doc = broker.getXMLResource(docURI, Lock.WRITE_LOCK);
            if (doc == null)
                throw new EXistException("Resource "
                        + docURI + " not found");
            if (!doc.getPermissions().validate(user, Permission.UPDATE))
                throw new PermissionDeniedException("User is not allowed to lock resource " + docURI);
            SecurityManager manager = brokerPool.getSecurityManager();
            User lockOwner = doc.getUserLock();
            if(lockOwner != null && (!lockOwner.equals(user)) && (!manager.hasAdminPrivileges(user)))
                throw new PermissionDeniedException("Resource is already locked by user " +
                        lockOwner.getName());
            //TODO : start the transaction earlier and register the lock within it ?
            TransactionManager transact = brokerPool.getTransactionManager();
            Txn transaction = transact.beginTransaction();            
            doc.setUserLock(null);
            broker.storeXMLResource(transaction, doc);
            transact.commit(transaction);
            return true;
        } finally {
            if(doc != null)
                doc.getUpdateLock().release(Lock.WRITE_LOCK);
            brokerPool.release(broker);
        }
    }
    
    public Hashtable summary(User user, String xpath) throws Exception {
        long startTime = System.currentTimeMillis();
        DBBroker broker = null;
        try {
            broker = brokerPool.get(user);
            QueryResult qr = doQuery(user, broker, xpath, null, null);
            if (qr == null)
                return new Hashtable();
            if (qr.hasErrors())
                throw qr.getException();
            NodeList resultSet = (NodeList) qr.result;
            HashMap map = new HashMap();
            HashMap doctypes = new HashMap();
            NodeProxy p;
            String docName;
            DocumentType doctype;
            NodeCount counter;
            DoctypeCount doctypeCounter;
            for (Iterator i = ((NodeSet) resultSet).iterator(); i.hasNext(); ) {
                p = (NodeProxy) i.next();
                 docName = p.getDocument().getURI().toString();
                doctype = p.getDocument().getDoctype();
                if (map.containsKey(docName)) {
                    counter = (NodeCount) map.get(docName);
                    counter.inc();
                } else {
                    counter = new NodeCount(p.getDocument());
                    map.put(docName, counter);
                }
                if (doctype == null)
                    continue;
                if (doctypes.containsKey(doctype.getName())) {
                    doctypeCounter = (DoctypeCount) doctypes.get(doctype
                            .getName());
                    doctypeCounter.inc();
                } else {
                    doctypeCounter = new DoctypeCount(doctype);
                    doctypes.put(doctype.getName(), doctypeCounter);
                }
            }
            Hashtable result = new Hashtable();
            result.put("queryTime", new Integer((int) (System
                    .currentTimeMillis() - startTime)));
            result.put("hits", new Integer(resultSet.getLength()));
            Vector documents = new Vector();
            Vector hitsByDoc;
            for (Iterator i = map.values().iterator(); i.hasNext(); ) {
                counter = (NodeCount) i.next();
                hitsByDoc = new Vector();
                hitsByDoc.addElement(counter.doc.getFileURI().toString());
                hitsByDoc.addElement(new Integer(counter.doc.getDocId()));
                hitsByDoc.addElement(new Integer(counter.count));
                documents.addElement(hitsByDoc);
            }
            result.put("documents", documents);
            Vector dtypes = new Vector();
            Vector hitsByType;
            DoctypeCount docTemp;
            for (Iterator i = doctypes.values().iterator(); i.hasNext(); ) {
                docTemp = (DoctypeCount) i.next();
                hitsByType = new Vector();
                hitsByType.addElement(docTemp.doctype.getName());
                hitsByType.addElement(new Integer(docTemp.count));
                dtypes.addElement(hitsByType);
            }
            result.put("doctypes", dtypes);
            return result;
        } finally {
            brokerPool.release(broker);
        }
    }
    
    public Hashtable summary(User user, int resultId) throws EXistException, XPathException {
        QueryResult qr = connectionPool.resultSets.get(resultId);
        if (qr == null)
            throw new EXistException("result set unknown or timed out");
        qr.timestamp = System.currentTimeMillis();
        Hashtable result = new Hashtable();
        result.put("queryTime", new Integer((int) qr.queryTime));
        if (qr.result == null) {
            result.put("hits", new Integer(0));
            return result;
        }
        DBBroker broker = brokerPool.get(user);
        try {
        	NodeList resultSet = qr.result.toNodeSet();
            HashMap map = new HashMap();
            HashMap doctypes = new HashMap();
            NodeProxy p;
            String docName;
            DocumentType doctype;
            NodeCount counter;
            DoctypeCount doctypeCounter;
            for (Iterator i = ((NodeSet) resultSet).iterator(); i.hasNext(); ) {
                p = (NodeProxy) i.next();
                docName = p.getDocument().getURI().toString();
                doctype = p.getDocument().getDoctype();
                if (map.containsKey(docName)) {
                    counter = (NodeCount) map.get(docName);
                    counter.inc();
                } else {
                    counter = new NodeCount(p.getDocument());
                    map.put(docName, counter);
                }
                if (doctype == null)
                    continue;
                if (doctypes.containsKey(doctype.getName())) {
                    doctypeCounter = (DoctypeCount) doctypes.get(doctype
                            .getName());
                    doctypeCounter.inc();
                } else {
                    doctypeCounter = new DoctypeCount(doctype);
                    doctypes.put(doctype.getName(), doctypeCounter);
                }
            }
            result.put("hits", new Integer(resultSet.getLength()));
            Vector documents = new Vector();
            Vector hitsByDoc;
            for (Iterator i = map.values().iterator(); i.hasNext(); ) {
                counter = (NodeCount) i.next();
                hitsByDoc = new Vector();
                hitsByDoc.addElement(counter.doc.getFileURI().toString());
                hitsByDoc.addElement(new Integer(counter.doc.getDocId()));
                hitsByDoc.addElement(new Integer(counter.count));
                documents.addElement(hitsByDoc);
            }
            result.put("documents", documents);
            Vector dtypes = new Vector();
            Vector hitsByType;
            DoctypeCount docTemp;
            for (Iterator i = doctypes.values().iterator(); i.hasNext(); ) {
                docTemp = (DoctypeCount) i.next();
                hitsByType = new Vector();
                hitsByType.addElement(docTemp.doctype.getName());
                hitsByType.addElement(new Integer(docTemp.count));
                dtypes.addElement(hitsByType);
            }
            result.put("doctypes", dtypes);
            return result;
        } finally {
            brokerPool.release(broker);
        }
    }
    
    public Vector getIndexedElements(User user, String collectionName,
            boolean inclusive) throws EXistException, PermissionDeniedException, URISyntaxException {
    	return getIndexedElements(user,XmldbURI.xmldbUriFor(collectionName),inclusive);
    }    
    public Vector getIndexedElements(User user, XmldbURI collUri,
            boolean inclusive) throws EXistException, PermissionDeniedException {
        DBBroker broker = null;
        Collection collection = null;
        try {
            broker = brokerPool.get(user);
            collection = broker.openCollection(collUri, Lock.READ_LOCK);
            if (collection == null)
                throw new EXistException("collection " + collUri + " not found");
            Occurrences occurrences[] = broker.getElementIndex().scanIndexedElements(collection,
                    inclusive);
            Vector result = new Vector(occurrences.length);
            for (int i = 0; i < occurrences.length; i++) {
                QName qname = (QName)occurrences[i].getTerm();
                Vector temp = new Vector(4);
                temp.addElement(qname.getLocalName());
                temp.addElement(qname.getNamespaceURI());
                temp.addElement(qname.getPrefix() == null ? "" : qname.getPrefix());
                temp.addElement(new Integer(occurrences[i].getOccurrences()));
                result.addElement(temp);
            }
            return result;
        } finally {
            if(collection != null)
                collection.release(Lock.READ_LOCK);
            brokerPool.release(broker);
        }
    }
    
    public Vector scanIndexTerms(User user, String collectionName,
            String start, String end, boolean inclusive)
            throws PermissionDeniedException, EXistException, URISyntaxException {
    	return scanIndexTerms(user,XmldbURI.xmldbUriFor(collectionName),start,end,inclusive);
    }    
    public Vector scanIndexTerms(User user, XmldbURI collUri,
            String start, String end, boolean inclusive)
            throws PermissionDeniedException, EXistException {
        DBBroker broker = null;
        Collection collection = null;
        try {
            broker = brokerPool.get(user);
            collection = broker.openCollection(collUri, Lock.READ_LOCK);
            if (collection == null)
                throw new EXistException("collection " + collUri + " not found");
            DocumentSet docs = new DocumentSet();
            collection.allDocs(broker, docs, inclusive, true);
            NodeSet nodes = docs.toNodeSet();
            Vector result = scanIndexTerms(start, end, broker, docs, nodes);
            return result;
        } finally {
            if(collection != null)
                collection.release(Lock.READ_LOCK);
            brokerPool.release(broker);
        }
    }
    
    public Vector scanIndexTerms(User user, String xpath,
            String start, String end)
            throws PermissionDeniedException, EXistException, XPathException {
        DBBroker broker = null;
        try {
            broker = brokerPool.get(user);
            XQuery xquery = broker.getXQueryService();
            Sequence nodes = xquery.execute(xpath, null, AccessContext.XMLRPC);
            Vector result = scanIndexTerms(start, end, broker, nodes.getDocumentSet(), nodes.toNodeSet());
            return result;
        } finally {
            brokerPool.release(broker);
        }
    }
    
    /**
     * @param start
     * @param end
     * @param broker
     * @param docs
     * @return
     * @throws PermissionDeniedException
     */
    private Vector scanIndexTerms(String start, String end, DBBroker broker, DocumentSet docs, NodeSet nodes)
    throws PermissionDeniedException {
        Occurrences occurrences[] =
                broker.getTextEngine().scanIndexTerms(docs, nodes, start, end);
        Vector result = new Vector(occurrences.length);
        Vector temp;
        for (int i = 0; i < occurrences.length; i++) {
            temp = new Vector(2);
            temp.addElement(occurrences[i].getTerm().toString());
            temp.addElement(new Integer(occurrences[i].getOccurrences()));
            result.addElement(temp);
        }
        return result;
    }
    
    public void synchronize() {
        documentCache.clear();
    }
    
    public void terminate() {
        terminate = true;
    }
    
    private Properties getProperties(Hashtable parameters) {
        Properties properties = new Properties();
        for (Iterator i = parameters.entrySet().iterator(); i.hasNext(); ) {
            Map.Entry entry = (Map.Entry) i.next();
            properties.setProperty((String) entry.getKey(), entry.getValue().toString());
        }
        return properties;
    }
    
    class CachedQuery {
        
        PathExpr expression;
        String queryString;
        long timestamp;
        
        public CachedQuery(PathExpr expr, String query) {
            this.expression = expr;
            this.queryString = query;
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    class DoctypeCount {
        int count = 1;
        DocumentType doctype;
        
        /**
         * Constructor for the DoctypeCount object
         *
         * @param doctype
         *                   Description of the Parameter
         */
        public DoctypeCount(DocumentType doctype) {
            this.doctype = doctype;
        }
        
        public void inc() {
            count++;
        }
    }
    
    class NodeCount {
        int count = 1;
        DocumentImpl doc;
        
        /**
         * Constructor for the NodeCount object
         *
         * @param doc
         *                   Description of the Parameter
         */
        public NodeCount(DocumentImpl doc) {
            this.doc = doc;
        }
        
        public void inc() {
            count++;
        }
    }
    
//	FIXME: Check it for possible security hole. Check name.
    public byte[] getDocumentChunk(User user, String name, int start, int len)
    throws EXistException, PermissionDeniedException, IOException {
        File file = new File(System.getProperty("java.io.tmpdir")
        + File.separator + name);
        if (!file.canRead())
            throw new EXistException("unable to read file " + name);
        if (file.length() < start+len)
            throw new EXistException("address too big " + name);
        byte buffer[] = new byte[len];
        RandomAccessFile os = new RandomAccessFile(file.getAbsolutePath(), "r");
        LOG.debug("Read from: " + start + " to: " + (start + len));
        os.seek(start);
        os.read(buffer);
        os.close();
        return buffer;
    }
    
    public boolean moveOrCopyResource(User user, String documentPath, String destinationPath,
            String newName, boolean move)
            throws EXistException, PermissionDeniedException, URISyntaxException {
    	return moveOrCopyResource(user,XmldbURI.xmldbUriFor(documentPath),
    			XmldbURI.xmldbUriFor(destinationPath),XmldbURI.xmldbUriFor(newName),move);
    }    
    public boolean moveOrCopyResource(User user, XmldbURI docUri, XmldbURI destUri,
            XmldbURI newName, boolean move)
            throws EXistException, PermissionDeniedException {
        TransactionManager transact = brokerPool.getTransactionManager();
        Txn transaction = transact.beginTransaction();
        DBBroker broker = null;
        Collection collection = null;
        Collection destination = null;
        DocumentImpl doc = null;
        try {
        	//TODO : use  transaction.registerLock(collection.getLock(), Lock.WRITE_LOCK);
            broker = brokerPool.get(user);
            collection = broker.openCollection(docUri.removeLastSegment(), move ? Lock.WRITE_LOCK : Lock.READ_LOCK);
            if (collection == null) {
                transact.abort(transaction);
                throw new EXistException("Collection " + docUri.removeLastSegment() + " not found");
            }           
            doc = collection.getDocumentWithLock(broker, docUri.lastSegment(), Lock.WRITE_LOCK);
            if(doc == null) {
                transact.abort(transaction);
                throw new EXistException("Document " + docUri + " not found");
            }
            //TODO : register the lock within the transaction ?
            
            // get destination collection
            destination = broker.openCollection(destUri, Lock.WRITE_LOCK);
            if(destination == null) {
                transact.abort(transaction);
                throw new EXistException("Destination collection " + destUri + " not found");
            }
            if(move)
                broker.moveXMLResource(transaction, doc, destination, newName);
            else
                broker.copyXMLResource(transaction, doc, destination, newName);
            transact.commit(transaction);
            documentCache.clear();
            return true;
        } catch (LockException e) {
            transact.abort(transaction);
            throw new PermissionDeniedException("Could not acquire lock on document " + docUri);
        } catch (IOException e) {
            transact.abort(transaction);
            throw new EXistException("Could not acquire lock on document " + docUri);
        } finally {
            if(doc != null)
                doc.getUpdateLock().release(Lock.WRITE_LOCK);
            if(collection != null)
                collection.release(move ? Lock.WRITE_LOCK : Lock.READ_LOCK);
            if(destination != null)
                destination.release(Lock.WRITE_LOCK);
            brokerPool.release(broker);
        }
    }
    
    public boolean moveOrCopyCollection(User user, String collectionName, String destinationPath,
            String newName, boolean move)
            throws EXistException, PermissionDeniedException, URISyntaxException {
    	return moveOrCopyCollection(user,XmldbURI.xmldbUriFor(collectionName),
    			XmldbURI.xmldbUriFor(destinationPath),XmldbURI.xmldbUriFor(newName),move);
    }    
    
    public boolean moveOrCopyCollection(User user, XmldbURI collUri, XmldbURI destUri,
            XmldbURI newName, boolean move)
            throws EXistException, PermissionDeniedException {
        TransactionManager transact = brokerPool.getTransactionManager();
        Txn transaction = transact.beginTransaction();
        DBBroker broker = null;
        Collection collection = null;
        Collection destination = null;
        try {
            broker = brokerPool.get(user);
            // get source document
            //TODO : use  transaction.registerLock(collection.getLock(), Lock.WRITE_LOCK);
            collection = broker.openCollection(collUri, move ? Lock.WRITE_LOCK : Lock.READ_LOCK);
            if (collection == null) {
                transact.abort(transaction);
                throw new EXistException("Collection " + collUri + " not found");
            }            
            // get destination collection
            destination = broker.openCollection(destUri, Lock.WRITE_LOCK);
            if(destination == null) {
                transact.abort(transaction);
                throw new EXistException("Destination collection " + destUri + " not found");
            }            
            if(move)
                broker.moveCollection(transaction, collection, destination, newName);
            else
                broker.copyCollection(transaction, collection, destination, newName);
            transact.commit(transaction);
            documentCache.clear();
            return true;
        } catch (LockException e) {
            transact.abort(transaction);
            throw new PermissionDeniedException(e.getMessage());
        } catch (IOException e) {
            transact.abort(transaction);
            throw new EXistException(e.getMessage());
        } finally {
            if(collection != null)
                collection.release(move ? Lock.WRITE_LOCK : Lock.READ_LOCK);
            if(destination != null)
                destination.release(Lock.WRITE_LOCK);
            brokerPool.release(broker);
        }
    }
    
    public void reindexCollection(User user, String collectionName) throws Exception,
    PermissionDeniedException, URISyntaxException {
    	reindexCollection(user,XmldbURI.xmldbUriFor(collectionName));
    }    
    
    public void reindexCollection(User user, XmldbURI collUri) throws Exception,
    PermissionDeniedException {
        DBBroker broker = null;
        try {
            broker = brokerPool.get(user);
            broker.reindexCollection(collUri);
            LOG.debug("collection " + collUri + " and sub collection reindexed");
        } catch (Exception e) {
            LOG.debug(e);
            throw e;
        } finally {
            brokerPool.release(broker);
        }
    }
    
    public void backup(User user, String userbackup, String password,
	String destcollection, String collection) throws Exception,
    PermissionDeniedException {
    	try {
    		   Backup backup = new Backup(
    				userbackup,
                    password, 
                    destcollection+"-backup",
                    XmldbURI.xmldbUriFor(XmldbURI.EMBEDDED_SERVER_URI.toString() + collection));
                backup.backup(false, null);
            } catch (Exception e) {
             e.printStackTrace(); 
             
			}
    }
    
    /**
     *   Validate if specified document is Valid.
     *
     * @param user      Name of user
     * @param documentPath   Path to XML document in database
     * @throws java.lang.Exception  Generic exception
     * @throws PermissionDeniedException  User is not allowed to perform action.
     * @return TRUE if document is valid, FALSE if not or errors or.....
     */
    public boolean isValid(User user, String documentPath)
    throws PermissionDeniedException, Exception, URISyntaxException {
    	return isValid(user,XmldbURI.xmldbUriFor(documentPath));
    }   
    
    public boolean isValid(User user, XmldbURI docUri)
                                   throws PermissionDeniedException, Exception{
        boolean retVal=false;
        DBBroker broker = null;
        
        try {
            // TODO not sure about this
            broker = brokerPool.get(user);
            BrokerPool pPool = broker.getBrokerPool();
            
            // Setup validator
            Validator validator = new Validator(pPool);
            
            // Get inputstream
            // TODO DWES reconsider
            InputStream is = new EmbeddedInputStream( new XmldbURL(docUri) );
            
            // Perform validation
            ValidationReport veh = validator.validate(is);
            
            // Return validation result
            retVal = veh.isValid();
            
        } catch (Exception e) {
            LOG.debug(e);
            throw e;
        } finally {
            brokerPool.release(broker);
        }
        return retVal;
    }
    
    public Vector getDocType(User user, String documentPath)
    throws PermissionDeniedException, EXistException, URISyntaxException {
    	return getDocType(user,XmldbURI.xmldbUriFor(documentPath));
    }    
    
    public Vector getDocType(User user, XmldbURI docUri)
    throws PermissionDeniedException, EXistException {
        DBBroker broker = null;
        DocumentImpl doc = null;
        
        try {
            broker = brokerPool.get(user);
            doc = broker.getXMLResource(docUri, Lock.READ_LOCK);
            if (doc == null) {
                LOG.debug("document " + docUri + " not found!");
                throw new EXistException("document not found");
            }
            
            Vector vector = new Vector(3);
                        
            if (doc.getDoctype() != null)
            {            	
            	vector.addElement(doc.getDoctype().getName()); 
            	
            	if (doc.getDoctype().getPublicId() != null) {  
            		vector.addElement(doc.getDoctype().getPublicId());             	  
            	} else {
            		vector.addElement("");
            	}
            		
            	if (doc.getDoctype().getSystemId() != null) {
            		vector.addElement(doc.getDoctype().getSystemId());
            	} else {
            		vector.addElement("");
            	}
            } else
            {
            	vector.addElement("");
            	vector.addElement("");
            	vector.addElement("");
            }
            return vector;
        } finally {
            if(doc != null)
                doc.getUpdateLock().release(Lock.READ_LOCK);
            brokerPool.release(broker);
        }
    }
    

    public boolean setDocType(User user, String documentPath, String doctypename, String publicid, String systemid) throws
    Exception, URISyntaxException {
    	return setDocType(user,XmldbURI.xmldbUriFor(documentPath),doctypename, publicid, systemid);
    }    
    public boolean setDocType(User user, XmldbURI docUri, String doctypename, String publicid, String systemid) throws
    Exception {
        DBBroker broker = null;
        DocumentImpl doc = null;
        DocumentType result = null;
        TransactionManager transact = brokerPool.getTransactionManager();
        Txn transaction = transact.beginTransaction();
        try {
            broker = brokerPool.get(user);
            doc = broker.getXMLResource(docUri, Lock.WRITE_LOCK);          
            if (doc == null) {
            	transact.abort(transaction);
                throw new EXistException("Resource " + docUri + " not found");
            }
            //TODO : register the lock within the transaction ?
            if (!doc.getPermissions().validate(user, Permission.UPDATE)) {
            	transact.abort(transaction);
            	throw new PermissionDeniedException("User is not allowed to lock resource " + docUri);
            }
            
            if (!"".equals(doctypename)) {
            	result = new DocumentTypeImpl(doctypename,
            			"".equals(publicid) ? null : publicid,
            			"".equals(systemid) ? null : systemid );            	
            }
            	            
            doc.setDocumentType(result);
            broker.storeXMLResource(transaction, doc);
            transact.commit(transaction);
            return true;
        } catch (Exception e) {
            transact.abort(transaction);
            throw e;
        } finally {
            if(doc != null)
                doc.getUpdateLock().release(Lock.WRITE_LOCK);
            brokerPool.release(broker);
        }
    }
}





