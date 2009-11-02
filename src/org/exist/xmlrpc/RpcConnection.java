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

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.Namespaces;
import org.exist.backup.Backup;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationException;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.collections.IndexInfo;
import org.exist.dom.*;
import org.exist.memtree.NodeImpl;
import org.exist.numbering.NodeId;
import org.exist.protocolhandler.embedded.EmbeddedInputStream;
import org.exist.protocolhandler.xmldb.XmldbURL;
import org.exist.security.*;
import org.exist.security.SecurityManager;
import org.exist.security.xacml.AccessContext;
import org.exist.source.Source;
import org.exist.source.StringSource;
import org.exist.storage.DBBroker;
import org.exist.storage.DataBackup;
import org.exist.storage.XQueryPool;
import org.exist.storage.BrokerPool;
import org.exist.storage.lock.Lock;
import org.exist.storage.lock.LockedDocumentMap;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.storage.sync.Sync;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.*;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.validation.ValidationReport;
import org.exist.validation.Validator;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.util.HTTPUtils;
import org.exist.xquery.value.*;
import org.exist.xupdate.Modification;
import org.exist.xupdate.XUpdateProcessor;
import org.w3c.dom.DocumentType;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import java.io.*;
import java.net.URISyntaxException;
import java.util.*;
import java.util.zip.DeflaterOutputStream;

/**
 * This class implements the actual methods defined by
 * {@link org.exist.xmlrpc.RpcAPI}.
 *
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 * Modified by {Marco.Tampucci, Massimo.Martinelli} @isti.cnr.it
 */
public class RpcConnection implements RpcAPI {
    
    private final static Logger LOG = Logger.getLogger(RpcConnection.class);

    private final static int MAX_DOWNLOAD_CHUNK_SIZE = 0x40000;

    protected XmldbRequestProcessorFactory factory;

    protected User user;

    /**
     * Creates a new <code>RpcConnection</code> instance.
     *
     * @exception EXistException if an error occurs
     */
    public RpcConnection(XmldbRequestProcessorFactory factory, User user)
    {
        super();
        this.factory = factory;
        this.user = user;
    }

    private void handleException(Throwable e) throws EXistException, PermissionDeniedException {
        LOG.debug(e.getMessage(), e);
        if (e instanceof EXistException)
            throw (EXistException) e;

        else if (e instanceof PermissionDeniedException)
            throw (PermissionDeniedException) e;
        
        else {
            //System.out.println(e.getClass().getName());
            throw new EXistException(e);
        }
    }

    public boolean createCollection(String name) throws EXistException, PermissionDeniedException {
        return createCollection(name, null);
    }

    /**
     * The method <code>createCollection</code>
     *
     * @param name a <code>String</code> value
     * @param created a <code>Date</code> value
     * @exception Exception if an error occurs
     * @exception PermissionDeniedException if an error occurs
     * @exception URISyntaxException if an error occurs
     */
    public boolean createCollection(String name, Date created) throws EXistException, PermissionDeniedException {
        try {
            return createCollection(XmldbURI.xmldbUriFor(name),created);
        } catch (URISyntaxException e) {
            handleException(e);
        }
        return false;
    }
    
    /**
     * The method <code>createCollection</code>
     *
     * @param collUri a <code>XmldbURI</code> value
     * @param created a <code>Date</code> value
     * @exception Exception if an error occurs
     * @exception PermissionDeniedException if an error occurs
     */
    private boolean createCollection(XmldbURI collUri, Date created) throws PermissionDeniedException, EXistException {
        DBBroker broker = null;
        TransactionManager transact = factory.getBrokerPool().getTransactionManager();
        Txn transaction = transact.beginTransaction();
        try {
            broker = factory.getBrokerPool().get(user);
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
            return true;

        } catch (Throwable e) {
            transact.abort(transaction);
            handleException(e);
            
        } finally {
            factory.getBrokerPool().release(broker);
        }
        return false;
    }
    
    /**
     * The method <code>configureCollection</code>
     *
     * @param collName a <code>String</code> value
     * @param configuration a <code>String</code> value
     * @exception EXistException if an error occurs
     * @exception URISyntaxException if an error occurs
     */
    public boolean configureCollection(String collName, String configuration)
            throws EXistException, PermissionDeniedException {
        try {
            return configureCollection(XmldbURI.xmldbUriFor(collName),configuration);
        } catch (URISyntaxException e) {
            handleException(e);
        }
        return false;
    }

    /**
     * The method <code>configureCollection</code>
     *
     * @param collUri a <code>XmldbURI</code> value
     * @param configuration a <code>String</code> value
     * @exception EXistException if an error occurs
     */
    private boolean configureCollection(XmldbURI collUri, String configuration)
    throws EXistException {
        DBBroker broker = null;
        Collection collection = null;
        TransactionManager transact = factory.getBrokerPool().getTransactionManager();
        Txn transaction = transact.beginTransaction();
        try {
            broker = factory.getBrokerPool().get(user);
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
            CollectionConfigurationManager mgr = factory.getBrokerPool().getConfigurationManager();
            mgr.addConfiguration(transaction, broker, collection, configuration);
            transact.commit(transaction);
            LOG.info("Configured '" + collection.getURI() + "'");  
        } catch (CollectionConfigurationException e) {
            transact.abort(transaction);
            throw new EXistException(e.getMessage());
        } finally {
            factory.getBrokerPool().release(broker);
        }
        return false;
    }
    
    /**
     * The method <code>createId</code>
     *
     * @param collName a <code>String</code> value
     * @return a <code>String</code> value
     * @exception EXistException if an error occurs
     * @exception URISyntaxException if an error occurs
     */
    public String createId(String collName) throws EXistException, URISyntaxException {
    	return createId(XmldbURI.xmldbUriFor(collName));
    }
    
    private String createId(XmldbURI collUri) throws EXistException {
        DBBroker broker = factory.getBrokerPool().get(user);
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
            factory.getBrokerPool().release(broker);
        }
    }
    
    /**
     * The method <code>doQuery</code>
     *
     * @param broker a <code>DBBroker</code> value
     * @param xpath a <code>String</code> value
     * @param contextSet a <code>NodeSet</code> value
     * @param parameters a <code>HashMap</code> value
     * @return a <code>QueryResult</code> value
     * @exception Exception if an error occurs
     */
    protected QueryResult doQuery(DBBroker broker, String xpath,
            NodeSet contextSet, HashMap parameters)
            throws Exception {
        XQuery xquery = broker.getXQueryService();
        XQueryPool pool = xquery.getXQueryPool();
        Source source = new StringSource(xpath);
        CompiledXQuery compiled = compile(broker, source, parameters);
        checkPragmas(compiled.getContext(), parameters);
        LockedDocumentMap lockedDocuments = null;
        try {
            long start = System.currentTimeMillis();
            lockedDocuments = beginProtected(broker, parameters);
            if (lockedDocuments != null)
                compiled.getContext().setProtectedDocs(lockedDocuments);
            Properties outputProperties = new Properties();
            Sequence result = xquery.execute(compiled, contextSet, outputProperties);
            // pass last modified date to the HTTP response
            HTTPUtils.addLastModifiedHeader( result, compiled.getContext() );
            LOG.info("query took " + (System.currentTimeMillis() - start) + "ms.");
            return new QueryResult(result, outputProperties);
        } catch (XPathException e) {
            return new QueryResult(e);
        } finally {
            if(lockedDocuments != null) {
                lockedDocuments.unlock();
            }
            if(compiled != null)
                pool.returnCompiledXQuery(source, compiled);
        }
    }

    protected LockedDocumentMap beginProtected(DBBroker broker, HashMap parameters) throws EXistException {
        String protectColl = (String) parameters.get(RpcAPI.PROTECTED_MODE);
        if (protectColl == null)
            return null;
        do {
            MutableDocumentSet docs = null;
            LockedDocumentMap lockedDocuments = new LockedDocumentMap();
            try {
                Collection coll = broker.getCollection(XmldbURI.createInternal(protectColl));
                docs = new DefaultDocumentSet();
                coll.allDocs(broker, docs, true, lockedDocuments, Lock.WRITE_LOCK);
                return lockedDocuments;
            } catch (LockException e) {
                LOG.debug("Deadlock detected. Starting over again. Docs: " + docs.getDocumentCount() + "; locked: " +
                lockedDocuments.size());
                lockedDocuments.unlock();
            }
        } while (true);
    }

    /**
     * The method <code>compile</code>
     *
     * @param broker a <code>DBBroker</code> value
     * @param source a <code>Source</code> value
     * @param parameters a <code>HashMap</code> value
     * @return a <code>CompiledXQuery</code> value
     * @exception XPathException if an error occurs
     * @exception IOException if an error occurs
     */
    private CompiledXQuery compile(DBBroker broker, Source source, HashMap parameters) throws XPathException, IOException {
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
        HashMap namespaces = (HashMap)parameters.get(RpcAPI.NAMESPACES);
        if(namespaces != null && namespaces.size() > 0) {
            context.declareNamespaces(namespaces);
        }
        //  declare static variables
        HashMap variableDecls = (HashMap)parameters.get(RpcAPI.VARIABLES);
        if(variableDecls != null) {
            for (Iterator i = variableDecls.entrySet().iterator(); i.hasNext();) {
                Map.Entry entry = (Map.Entry) i.next();
                LOG.debug("declaring " + entry.getKey().toString() + " = " + entry.getValue());
                context.declareVariable((String) entry.getKey(), entry.getValue());
            }
        }
        Object[] staticDocuments = (Object[])parameters.get(RpcAPI.STATIC_DOCUMENTS);
        if(staticDocuments != null) {
        	try {
            XmldbURI[] d = new XmldbURI[staticDocuments.length];
            for (int i = 0; i < staticDocuments.length; i++) {
                XmldbURI next = XmldbURI.xmldbUriFor((String) staticDocuments[i]);
                d[i] = next;
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
    
    /**
     * The method <code>printDiagnostics</code>
     *
     * @param query a <code>String</code> value
     * @param parameters a <code>HashMap</code> value
     * @return a <code>String</code> value
     * @exception Exception if an error occurs
     */
    public String printDiagnostics(String query, HashMap parameters) throws EXistException, PermissionDeniedException {
        DBBroker broker = null;
        try {
            broker = factory.getBrokerPool().get(user);
            Source source = new StringSource(query);
            XQuery xquery = broker.getXQueryService();
            XQueryPool pool = xquery.getXQueryPool();
            CompiledXQuery compiled = pool.borrowCompiledXQuery(broker, source);
            if(compiled == null)
                compiled = compile(broker, source, parameters);
            StringWriter writer = new StringWriter();
            compiled.dump(writer);
            return writer.toString();

        } catch (Throwable e) {
            handleException(e);

        } finally {
            factory.getBrokerPool().release(broker);
        }
        return null;
    }
    
    /**
     * Check if the XQuery contains pragmas that define serialization settings.
     * If yes, copy the corresponding settings to the current set of output properties.
     *
     * @param context
     */
    protected void checkPragmas(XQueryContext context, HashMap parameters) throws XPathException {
        Option pragma = context.getOption(Option.SERIALIZE_QNAME);
        checkPragmas(pragma, parameters);
    }

    protected void checkPragmas(Option pragma, HashMap parameters) throws XPathException {
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

    public int executeQuery(byte[] xpath, String encoding, HashMap parameters) throws EXistException, PermissionDeniedException {
        String xpathString = null;
        if (encoding != null)
            try {
                xpathString = new String(xpath, encoding);
            } catch (UnsupportedEncodingException e) {
                LOG.warn(e);
            }

        if (xpathString == null)
            xpathString = new String(xpath);

        LOG.debug("query: " + xpathString);
        return executeQuery(xpathString, parameters);
    }

    /**
     * The method <code>executeQuery</code>
     *
     * @param xpath a <code>String</code> value
     * @param parameters a <code>HashMap</code> value
     * @return an <code>int</code> value
     * @exception Exception if an error occurs
     */
    public int executeQuery(String xpath, HashMap parameters) throws EXistException, PermissionDeniedException {
        long startTime = System.currentTimeMillis();
        DBBroker broker = null;
        try {
            broker = factory.getBrokerPool().get(user);
            QueryResult result = doQuery(broker, xpath, null,
                    parameters);
            if(result.hasErrors())
                throw result.getException();
            result.queryTime = System.currentTimeMillis() - startTime;
            return factory.resultSets.add(result);

        } catch (Throwable e) {
            handleException(e);

        } finally {
            factory.getBrokerPool().release(broker);
        }
        return -1;
    }
    
    /**
     * The method <code>formatErrorMsg</code>
     *
     * @param message a <code>String</code> value
     * @return a <code>String</code> value
     */
    protected String formatErrorMsg(String message) {
        return formatErrorMsg("error", message);
    }
    
    /**
     * The method <code>formatErrorMsg</code>
     *
     * @param type a <code>String</code> value
     * @param message a <code>String</code> value
     * @return a <code>String</code> value
     */
    protected String formatErrorMsg(String type, String message) {
        StringBuilder buf = new StringBuilder();
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
    
    /**
     * The method <code>getCollectionDesc</code>
     *
     * @param rootCollection a <code>String</code> value
     * @return a <code>HashMap</code> value
     * @exception Exception if an error occurs
     * @exception URISyntaxException if an error occurs
     */
    public HashMap getCollectionDesc(String rootCollection) throws EXistException, PermissionDeniedException {
        try {
            return getCollectionDesc((rootCollection==null)?XmldbURI.ROOT_COLLECTION_URI:XmldbURI.xmldbUriFor(rootCollection));
        } catch (Throwable e) {
            handleException(e);
        }
        return null;
    }
    
    /**
     * The method <code>getCollectionDesc</code>
     *
     * @param rootUri a <code>XmldbURI</code> value
     * @return a <code>HashMap</code> value
     * @exception Exception if an error occurs
     */
    private HashMap getCollectionDesc(XmldbURI rootUri)
    throws Exception {
        DBBroker broker = factory.getBrokerPool().get(user);
        Collection collection = null;
        try {           
            collection = broker.openCollection(rootUri, Lock.READ_LOCK);
            if (collection == null)
                throw new EXistException("collection " + rootUri + " not found!");
            HashMap desc = new HashMap();
            Vector docs = new Vector();
            Vector collections = new Vector();
            if (collection.getPermissions().validate(user, Permission.READ)) {
                for (Iterator i = collection.iterator(broker); i.hasNext(); ) {
                	DocumentImpl doc = (DocumentImpl) i.next();
                	Permission perms = doc.getPermissions();
                	HashMap hash = new HashMap(4);
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
            factory.getBrokerPool().release(broker);
        }
    }
    
    /**
     * The method <code>describeResource</code>
     *
     * @param resourceName a <code>String</code> value
     * @return a <code>HashMap</code> value
     * @exception EXistException if an error occurs
     * @exception PermissionDeniedException if an error occurs
     * @exception URISyntaxException if an error occurs
     */
    public HashMap describeResource(String resourceName)
    throws EXistException, PermissionDeniedException {
        try {
            return describeResource(XmldbURI.xmldbUriFor(resourceName));
        } catch (Throwable e) {
            handleException(e);
        }
        return null;
    }
    
    /**
     * The method <code>describeResource</code>
     *
     * @param resourceUri a <code>XmldbURI</code> value
     * @return a <code>HashMap</code> value
     * @exception EXistException if an error occurs
     * @exception PermissionDeniedException if an error occurs
     */
    private HashMap describeResource(XmldbURI resourceUri)
    throws EXistException, PermissionDeniedException {
        DBBroker broker = factory.getBrokerPool().get(user);
        DocumentImpl doc = null;
        HashMap hash = new HashMap(5);
        try {
            doc = broker.getXMLResource(resourceUri, Lock.READ_LOCK);
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
            factory.getBrokerPool().release(broker);
        }
    }

    /**
     * The method <code>describeCollection</code>
     *
     * @param rootCollection a <code>String</code> value
     * @return a <code>HashMap</code> value
     * @exception Exception if an error occurs
     * @exception URISyntaxException if an error occurs
     */
    public HashMap describeCollection(String rootCollection) throws EXistException, PermissionDeniedException {
        try {
            return describeCollection((rootCollection==null)?XmldbURI.ROOT_COLLECTION_URI:XmldbURI.xmldbUriFor(rootCollection));
        } catch (Throwable e) {
            handleException(e);
            return null;
        }
    }

    /**
     * The method <code>describeCollection</code>
     *
     * @param collUri a <code>XmldbURI</code> value
     * @return a <code>HashMap</code> value
     * @exception Exception if an error occurs
     */
    private HashMap describeCollection(XmldbURI collUri)
    throws Exception {
        DBBroker broker = factory.getBrokerPool().get(user);
        Collection collection = null;
        try {
            collection = broker.openCollection(collUri, Lock.READ_LOCK);
            if (collection == null)
                throw new EXistException("collection " + collUri + " not found!");
            HashMap desc = new HashMap();
            List collections = new ArrayList();
            if (collection.getPermissions().validate(user, Permission.READ)) {
                for (Iterator i = collection.collectionIterator(); i.hasNext(); )
                    collections.add(i.next().toString());
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
            factory.getBrokerPool().release(broker);
        }
    }


    public byte[] getDocument(String name, HashMap parametri) throws EXistException,
            PermissionDeniedException {

        String encoding = "UTF-8";
        String compression = "no";

        if (((String) parametri.get("encoding")) == null) {
            encoding = "UTF-8";
        } else {
            encoding = (String) parametri.get("encoding");
        }

        if (((String) parametri.get(EXistOutputKeys.COMPRESS_OUTPUT)) != null) {
            compression = (String) parametri.get(EXistOutputKeys.COMPRESS_OUTPUT);
        }

        try {
            String xml = getDocumentAsString(name, parametri);
            if (xml == null)
                throw new EXistException("document " + name + " not found!");
            try {
                if (compression.equals("no")) {
                    return xml.getBytes(encoding);
                } else {
                    LOG.debug("getdocument with compression");
                    return Compressor.compress(xml.getBytes(encoding));
                }

            } catch (UnsupportedEncodingException uee) {
                LOG.warn(uee);
                if (compression.equals("no")) {
                    return xml.getBytes();
                } else {
                    LOG.debug("getdocument with compression");
                    return Compressor.compress(xml.getBytes());
                }

            }
        } catch (Throwable e) {
            handleException(e);
            return null;
        }
    }

    /**
     * The method <code>getDocument</code>
     *
     * @param docName a <code>String</code> value
     * @param parametri a <code>HashMap</code> value
     * @return a <code>String</code> value
     * @exception Exception if an error occurs
     * @exception URISyntaxException if an error occurs
     */
    public String getDocumentAsString(String docName, HashMap parametri) throws EXistException, PermissionDeniedException {
        try {
            return getDocumentAsString(XmldbURI.xmldbUriFor(docName), parametri);

        } catch (Throwable e) {
            handleException(e);
            return null;
        }
    }

    /**
     * The method <code>getDocument</code>
     *
     * @param docUri a <code>XmldbURI</code> value
     * @param parametri a <code>HashMap</code> value
     * @return a <code>String</code> value
     * @exception Exception if an error occurs
     */
    private String getDocumentAsString(XmldbURI docUri, HashMap parametri)
    throws Exception {
        DBBroker broker = null;
        
        Collection collection = null;
        DocumentImpl doc = null;
        try {
            broker = factory.getBrokerPool().get(user);
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
            factory.getBrokerPool().release(broker);
        }
    }
    
    /**
     * The method <code>getDocumentData</code>
     *
     * @param docName a <code>String</code> value
     * @param parameters a <code>HashMap</code> value
     * @return a <code>HashMap</code> value
     * @exception Exception if an error occurs
     */
    public HashMap getDocumentData(String docName, HashMap parameters) throws EXistException, PermissionDeniedException {
        Collection collection = null;
        DocumentImpl doc = null;
        DBBroker broker = null;
        try {
            broker = factory.getBrokerPool().get(user);
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
                HashMap result = new HashMap();
                // A tweak for very large XML resources
                if(doc.getContentLength()<0 || doc.getContentLength() > MAX_DOWNLOAD_CHUNK_SIZE) {
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
                    result.put("supports-long-offset", new Boolean(true));
                } else {
                    String xml = serializer.serialize(doc);
                    result.put("data", xml.getBytes(encoding));
                    result.put("offset", new Integer(0));
                }
                return result;
            } else {
                HashMap result = new HashMap();
                // A tweak for very large binary resources
                if(doc.getContentLength()<0 || doc.getContentLength() > MAX_DOWNLOAD_CHUNK_SIZE) {
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
                    result.put("supports-long-offset", new Boolean(true));
                } else {
                    try {
                        InputStream is = broker.getBinaryResource((BinaryDocument)doc);
                        int datasize=(int)broker.getBinaryResourceSize((BinaryDocument)doc);
                        byte[] data = new byte[datasize];
                        int datapos=0;
                        int dataread=0;
                        while(datasize>0 && (dataread=is.read(data, datapos, datasize))!=-1) {
                        	datapos+=dataread;
                        	datasize-=dataread;
                        }
                        is.close();
                        result.put("data", data);
                        result.put("offset", new Integer(0));
                    } catch (IOException ex) {
                       throw new EXistException("I/O error while reading resource.",ex);
                    }
                }
                return result;
            }
            
        } catch (Throwable e) {
            handleException(e);
            return null;

        } finally {
            if(collection != null) {
                collection.releaseDocument(doc, Lock.READ_LOCK);
                collection.getLock().release(Lock.READ_LOCK);
            }
            factory.getBrokerPool().release(broker);
        }
    }
    
    /**
     * The method <code>getNextChunk</code>
     *
     * @param handle a <code>String</code> value
     * @param offset an <code>int</code> value
     * @return a <code>HashMap</code> value
     * @exception Exception if an error occurs
     */
    public HashMap getNextChunk(String handle, int offset) throws EXistException, PermissionDeniedException {
        try {
            File file = new File(handle);
            if(!(file.isFile() && file.canRead()))
                throw new EXistException("Invalid handle specified");
            if(offset <= 0 || offset > file.length())
                throw new EXistException("No more data available");
            byte[] chunk = getChunk(file, offset);
            long nextChunk = offset + chunk.length;

            HashMap result = new HashMap();
            result.put("data", chunk);
            result.put("handle", handle);
            if(nextChunk > (long)Integer.MAX_VALUE || nextChunk == file.length()) {
                file.delete();
                result.put("offset", new Integer(0));
            } else
                result.put("offset", new Integer((int)nextChunk));
            return result;
            
        } catch (Throwable e) {
            handleException(e);
            return null;
        }
    }
    
    /**
     * The method <code>getNextExtendedChunk</code>
     *
     * @param handle a <code>String</code> value
     * @param offset a <code>String</code> value
     * @return a <code>HashMap</code> value
     * @exception Exception if an error occurs
     */
	public HashMap getNextExtendedChunk(String handle, String offset) throws EXistException, PermissionDeniedException {
        try {
            File file = new File(handle);
            if(!(file.isFile() && file.canRead()))
                throw new EXistException("Invalid handle specified");
            long longOffset=new Long(offset).longValue();
            if(longOffset > file.length())
                throw new EXistException("No more data available");
            byte[] chunk = getChunk(file, longOffset);
            long nextChunk = longOffset + chunk.length;

            HashMap result = new HashMap();
            result.put("data", chunk);
            result.put("handle", handle);
            if(nextChunk == file.length()) {
                file.delete();
                result.put("offset", Long.toString(0));
            } else
                result.put("offset", Long.toString(nextChunk));
            return result;
            
        } catch (Throwable e) {
            handleException(e);
            return null;
        }
	}
	
	/**
     * The method <code>getChunk</code>
     *
     * @param file a <code>File</code> value
     * @param offset a <code>long</code> value
     * @return a <code>byte[]</code> value
     * @exception IOException if an error occurs
     */
    private byte[] getChunk(File file, long offset) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(file, "r");
        raf.seek(offset);
        long remaining = raf.length() - offset;
        if(remaining > MAX_DOWNLOAD_CHUNK_SIZE)
            remaining = MAX_DOWNLOAD_CHUNK_SIZE;
        byte[] data = new byte[(int)remaining];
        raf.readFully(data);
        raf.close();
        return data;
    }
    
    /**
     * The method <code>getBinaryResource</code>
     *
     * @param name a <code>String</code> value
     * @return a <code>byte[]</code> value
     * @exception EXistException if an error occurs
     * @exception PermissionDeniedException if an error occurs
     * @exception URISyntaxException if an error occurs
     */
    public byte[] getBinaryResource(String name)
    throws EXistException, PermissionDeniedException, URISyntaxException {
    	return getBinaryResource(XmldbURI.xmldbUriFor(name));
    }
    
    /**
     * The method <code>getBinaryResource</code>
     *
     * @param name a <code>XmldbURI</code> value
     * @return a <code>byte[]</code> value
     * @exception EXistException if an error occurs
     * @exception PermissionDeniedException if an error occurs
     */
    private byte[] getBinaryResource(XmldbURI name)
    throws EXistException, PermissionDeniedException {
        DBBroker broker = null;
        DocumentImpl doc = null;
        try {
            broker = factory.getBrokerPool().get(user);
            doc = broker.getXMLResource(name, Lock.READ_LOCK);
            if (doc == null)
                throw new EXistException("Resource " + name + " not found");
            if (doc.getResourceType() != DocumentImpl.BINARY_FILE)
                throw new EXistException("Document " + name
                        + " is not a binary resource");
            if(!doc.getPermissions().validate(user, Permission.READ))
                throw new PermissionDeniedException("Insufficient privileges to read resource");
            try {
                InputStream is = broker.getBinaryResource((BinaryDocument)doc);
                byte[] data = new byte[(int)broker.getBinaryResourceSize((BinaryDocument)doc)];
                is.read(data);
                is.close();
                return data;
            } catch (IOException ex) {
               throw new EXistException("I/O error while reading resource.",ex);
            }
		} finally {
            if(doc != null)
                doc.getUpdateLock().release(Lock.READ_LOCK);
            factory.getBrokerPool().release(broker);
        }
    }
    
    /**
     * The method <code>xupdate</code>
     *
     * @param collectionName a <code>String</code> value
     * @param xupdate a <code>String</code> value
     * @return an <code>int</code> value
     * @exception SAXException if an error occurs
     * @exception LockException if an error occurs
     * @exception PermissionDeniedException if an error occurs
     * @exception EXistException if an error occurs
     * @exception XPathException if an error occurs
     * @exception URISyntaxException if an error occurs
     */
    public int xupdate(String collectionName, byte[] xupdate) throws PermissionDeniedException, EXistException {
        try {
            return xupdate(XmldbURI.xmldbUriFor(collectionName), new String(xupdate, "UTF-8"));
            
        } catch (Throwable e) {
            handleException(e);
            return -1;
        }
    }
    
    /**
     * The method <code>xupdate</code>
     *
     * @param collUri a <code>XmldbURI</code> value
     * @param xupdate a <code>String</code> value
     * @return an <code>int</code> value
     * @exception SAXException if an error occurs
     * @exception LockException if an error occurs
     * @exception PermissionDeniedException if an error occurs
     * @exception EXistException if an error occurs
     * @exception XPathException if an error occurs
     */
    private int xupdate(XmldbURI collUri, String xupdate)
    throws SAXException, LockException, PermissionDeniedException, EXistException,
            XPathException {
        TransactionManager transact = factory.getBrokerPool().getTransactionManager();
        Txn transaction = transact.beginTransaction();
        DBBroker broker = null;
        try {
            broker = factory.getBrokerPool().get(user);
            Collection collection = broker.getCollection(collUri);
            if (collection == null) {
                transact.abort(transaction);
                throw new EXistException("collection " + collUri + " not found");
            }
            //TODO : register a lock (which one ?) in the transaction ?
            DocumentSet docs = collection.allDocs(broker, new DefaultDocumentSet(), true, true);
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
            factory.getBrokerPool().release(broker);
        }
    }
    
    /**
     * The method <code>xupdateResource</code>
     *
     * @param resource a <code>String</code> value
     * @param xupdate a <code>String</code> value
     * @return an <code>int</code> value
     * @exception SAXException if an error occurs
     * @exception LockException if an error occurs
     * @exception PermissionDeniedException if an error occurs
     * @exception EXistException if an error occurs
     * @exception XPathException if an error occurs
     * @exception URISyntaxException if an error occurs
     */
    public int xupdateResource(String resource, byte[] xupdate, String encoding) throws PermissionDeniedException, EXistException {
        try {
            return xupdateResource(XmldbURI.xmldbUriFor(resource), new String(xupdate, encoding));
        } catch (Throwable e) {
            handleException(e);
            return -1;
        }
    }

    /**
     * The method <code>xupdateResource</code>
     *
     * @param docUri a <code>XmldbURI</code> value
     * @param xupdate a <code>String</code> value
     * @return an <code>int</code> value
     * @exception SAXException if an error occurs
     * @exception LockException if an error occurs
     * @exception PermissionDeniedException if an error occurs
     * @exception EXistException if an error occurs
     * @exception XPathException if an error occurs
     */
    private int xupdateResource(XmldbURI docUri, String xupdate)
    throws SAXException, LockException, PermissionDeniedException, EXistException,
            XPathException {
        TransactionManager transact = factory.getBrokerPool().getTransactionManager();
        Txn transaction = transact.beginTransaction();
        DBBroker broker = null;
        try {
            broker = factory.getBrokerPool().get(user);
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
            MutableDocumentSet docs = new DefaultDocumentSet();
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
            factory.getBrokerPool().release(broker);
        }
    }
    
    /**
     * The method <code>sync</code>
     *
     * @return a <code>boolean</code> value
     */
    public boolean sync() {
        DBBroker broker = null;
        try {
            broker = factory.getBrokerPool().get(XMLSecurityManager.SYSTEM_USER);
            broker.sync(Sync.MAJOR_SYNC);
        } catch (EXistException e) {
        	LOG.warn(e.getMessage(), e);
        } finally {
            factory.getBrokerPool().release(broker);
        }
        return true;
    }
    
    /**
     * The method <code>isXACMLEnabled</code>
     *
     * @return a <code>boolean</code> value
     */
    public boolean isXACMLEnabled() {
    	return factory.getBrokerPool().getSecurityManager().isXACMLEnabled();
    }
    
    /**
     * The method <code>dataBackup</code>
     *
     * @param dest a <code>String</code> value
     * @return a <code>boolean</code> value
     */
    public boolean dataBackup(String dest ) {
        factory.getBrokerPool().triggerSystemTask( new DataBackup(dest));
        return true;
    }
    
    /**
     * The method <code>getDocumentListing</code>
     *
     * @return a <code>Vector</code> value
     * @exception EXistException if an error occurs
     */
    public Vector getDocumentListing() throws EXistException {
        DBBroker broker = null;
        try {
            broker = factory.getBrokerPool().get(user);
            DocumentSet docs = broker.getAllXMLResources(new DefaultDocumentSet());
            XmldbURI names[] = docs.getNames();
            Vector vec = new Vector();
            for (int i = 0; i < names.length; i++)
                vec.addElement(names[i].toString());
            
            return vec;
        } finally {
            factory.getBrokerPool().release(broker);
        }
    }
    
    /**
     * The method <code>getDocumentListing</code>
     *
     * @param collName a <code>String</code> value
     * @return a <code>Vector</code> value
     * @exception EXistException if an error occurs
     * @exception PermissionDeniedException if an error occurs
     * @exception URISyntaxException if an error occurs
     */
    public Vector getDocumentListing(String collName)
    throws EXistException, PermissionDeniedException, URISyntaxException {
    	return getDocumentListing(XmldbURI.xmldbUriFor(collName));
    }

    /**
     * The method <code>getDocumentListing</code>
     *
     * @param collUri a <code>XmldbURI</code> value
     * @return a <code>Vector</code> value
     * @exception EXistException if an error occurs
     * @exception PermissionDeniedException if an error occurs
     */
    private Vector getDocumentListing(XmldbURI collUri)
    throws EXistException, PermissionDeniedException {
        DBBroker broker = null;
        Collection collection = null;
        try {
            broker = factory.getBrokerPool().get(user);
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
            factory.getBrokerPool().release(broker);
        }
    }
    
    /**
     * The method <code>getResourceCount</code>
     *
     * @param collectionName a <code>String</code> value
     * @return an <code>int</code> value
     * @exception EXistException if an error occurs
     * @exception PermissionDeniedException if an error occurs
     * @exception URISyntaxException if an error occurs
     */
    public int getResourceCount(String collectionName)
    throws EXistException, PermissionDeniedException, URISyntaxException {
    	return getResourceCount(XmldbURI.xmldbUriFor(collectionName));
    }

    /**
     * The method <code>getResourceCount</code>
     *
     * @param collUri a <code>XmldbURI</code> value
     * @return an <code>int</code> value
     * @exception EXistException if an error occurs
     * @exception PermissionDeniedException if an error occurs
     */
    private int getResourceCount(XmldbURI collUri)
    throws EXistException, PermissionDeniedException {
        DBBroker broker = null;
        Collection collection = null;
        try {
            broker = factory.getBrokerPool().get(user);
            collection = broker.openCollection(collUri, Lock.READ_LOCK);
            return collection.getDocumentCount();
        } finally {
            if(collection != null)
                collection.release(Lock.READ_LOCK);
            factory.getBrokerPool().release(broker);
        }
    }
    
    /**
     * The method <code>createResourceId</code>
     *
     * @param collectionName a <code>String</code> value
     * @return a <code>String</code> value
     * @exception EXistException if an error occurs
     * @exception PermissionDeniedException if an error occurs
     * @exception URISyntaxException if an error occurs
     */
    public String createResourceId(String collectionName)
    throws EXistException, PermissionDeniedException, URISyntaxException {
    	return createResourceId(XmldbURI.xmldbUriFor(collectionName));
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
    private String createResourceId(XmldbURI collUri) throws EXistException, PermissionDeniedException {
        DBBroker broker = null;
        Collection collection = null;
        try {
            broker = factory.getBrokerPool().get(user);
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
            factory.getBrokerPool().release(broker);
        }
    }
    
    /**
     * The method <code>listDocumentPermissions</code>
     *
     * @return a <code>HashMap</code> value
     * @exception EXistException if an error occurs
     * @exception PermissionDeniedException if an error occurs
     * @exception URISyntaxException if an error occurs
     */
    public HashMap listDocumentPermissions(String name)
    throws EXistException, PermissionDeniedException, URISyntaxException {
    	return listDocumentPermissions(XmldbURI.xmldbUriFor(name));
    }

    /**
     * The method <code>listDocumentPermissions</code>
     *
     * @param collUri a <code>XmldbURI</code> value
     * @return a <code>HashMap</code> value
     * @exception EXistException if an error occurs
     * @exception PermissionDeniedException if an error occurs
     */
    private HashMap listDocumentPermissions(XmldbURI collUri)
    throws EXistException, PermissionDeniedException {
        DBBroker broker = null;
        Collection collection = null;
        try {
            broker = factory.getBrokerPool().get(user);
            collection = broker.openCollection(collUri, Lock.READ_LOCK);
            if (collection == null)
                throw new EXistException("Collection " + collUri + " not found");
            if (!collection.getPermissions().validate(user, Permission.READ))
                throw new PermissionDeniedException(
                        "not allowed to read collection " + collUri);
            HashMap result = new HashMap(collection.getDocumentCount());
            for (Iterator i = collection.iterator(broker); i.hasNext(); ) {
            	DocumentImpl doc = (DocumentImpl) i.next();
            	Permission perm = doc.getPermissions();
            	List tmp = new ArrayList(3);
                tmp.add(perm.getOwner());
                tmp.add(perm.getOwnerGroup());
                tmp.add(new Integer(perm.getPermissions()));
                result.put(doc.getFileURI().toString(), tmp);
            }
            return result;
        } finally {
            if(collection != null)
                collection.release(Lock.READ_LOCK);
            factory.getBrokerPool().release(broker);
        }
    }
    
    /**
     * The method <code>listCollectionPermissions</code>
     *
     * @param name a <code>String</code> value
     * @return a <code>HashMap</code> value
     * @exception EXistException if an error occurs
     * @exception PermissionDeniedException if an error occurs
     * @exception URISyntaxException if an error occurs
     */
    public HashMap listCollectionPermissions(String name)
    throws EXistException, PermissionDeniedException, URISyntaxException {
    	return listCollectionPermissions(XmldbURI.xmldbUriFor(name));
    }

    /**
     * The method <code>listCollectionPermissions</code>
     *
     * @param collUri a <code>XmldbURI</code> value
     * @return a <code>HashMap</code> value
     * @exception EXistException if an error occurs
     * @exception PermissionDeniedException if an error occurs
     */
    private HashMap listCollectionPermissions(XmldbURI collUri)
    throws EXistException, PermissionDeniedException {
        DBBroker broker = null;
        Collection collection = null;
        try {
            broker = factory.getBrokerPool().get(user);
            collection = broker.openCollection(collUri, Lock.READ_LOCK);
            if (collection == null)
                throw new EXistException("Collection " + collUri + " not found");
            if (!collection.getPermissions().validate(user, Permission.READ))
                throw new PermissionDeniedException("not allowed to read collection " + collUri);
            HashMap result = new HashMap(collection.getChildCollectionCount());
            for (Iterator i = collection.collectionIterator(); i.hasNext(); ) {
            	XmldbURI child = (XmldbURI) i.next();
            	XmldbURI path = collUri.append(child);
                Collection childColl = broker.getCollection(path);
                Permission perm = childColl.getPermissions();
                List tmp = new ArrayList(3);
                tmp.add(perm.getOwner());
                tmp.add(perm.getOwnerGroup());
                tmp.add(new Integer(perm.getPermissions()));
                result.put(child, tmp);
            }
            return result;
        } finally {
            if(collection != null)
                collection.release(Lock.READ_LOCK);
            factory.getBrokerPool().release(broker);
        }
    }
    
    /**
     * The method <code>getHits</code>
     *
     * @param resultId an <code>int</code> value
     * @return an <code>int</code> value
     * @exception EXistException if an error occurs
     */
    public int getHits(int resultId) throws EXistException {
        QueryResult qr = factory.resultSets.get(resultId);
        if (qr == null)
            throw new EXistException("result set unknown or timed out");
        qr.timestamp = System.currentTimeMillis();
        if (qr.result == null)
            return 0;
        return qr.result.getItemCount();
    }
    
    /**
     * The method <code>getPermissions</code>
     *
     * @param name a <code>String</code> value
     * @return a <code>HashMap</code> value
     * @exception EXistException if an error occurs
     * @exception PermissionDeniedException if an error occurs
     * @exception URISyntaxException if an error occurs
     */
    public HashMap getPermissions(String name)
    throws EXistException, PermissionDeniedException, URISyntaxException {
    	return getPermissions(XmldbURI.xmldbUriFor(name));
    }

    /**
     * The method <code>getPermissions</code>
     *
     * @param uri a <code>XmldbURI</code> value
     * @return a <code>HashMap</code> value
     * @exception EXistException if an error occurs
     * @exception PermissionDeniedException if an error occurs
     */
    private HashMap getPermissions(XmldbURI uri)
    throws EXistException, PermissionDeniedException {
        DBBroker broker = null;
        Collection collection = null;
        try {
            broker = factory.getBrokerPool().get(user);
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
            HashMap result = new HashMap();
            result.put("owner", perm.getOwner());
            result.put("group", perm.getOwnerGroup());
            result.put("permissions", new Integer(perm.getPermissions()));
            return result;
        } finally {
        	if (collection != null)
                collection.release(Lock.READ_LOCK);
        	factory.getBrokerPool().release(broker);
        }
    }
    
    /**
     * The method <code>getCreationDate</code>
     *
     * @param collectionPath a <code>String</code> value
     * @return a <code>Date</code> value
     * @exception PermissionDeniedException if an error occurs
     * @exception EXistException if an error occurs
     * @exception URISyntaxException if an error occurs
     */
    public Date getCreationDate(String collectionPath)
    throws PermissionDeniedException, EXistException, URISyntaxException {
    	return getCreationDate(XmldbURI.xmldbUriFor(collectionPath));
    }

    /**
     * The method <code>getCreationDate</code>
     *
     * @param collUri a <code>XmldbURI</code> value
     * @return a <code>Date</code> value
     * @exception PermissionDeniedException if an error occurs
     * @exception EXistException if an error occurs
     */
    private Date getCreationDate(XmldbURI collUri)
    throws PermissionDeniedException, EXistException {
        DBBroker broker = null;
        Collection collection = null;
        try {
            broker = factory.getBrokerPool().get(user);
            collection = broker.openCollection(collUri, Lock.READ_LOCK);
            if (collection == null)
                throw new EXistException("collection " + collUri + " not found");
            return new Date(collection.getCreationTime());
        } finally {
            if(collection != null)
                collection.release(Lock.READ_LOCK);
            factory.getBrokerPool().release(broker);
        }
    }
    
    /**
     * The method <code>getTimestamps</code>
     *
     * @param documentPath a <code>String</code> value
     * @return a <code>Vector</code> value
     * @exception PermissionDeniedException if an error occurs
     * @exception EXistException if an error occurs
     * @exception URISyntaxException if an error occurs
     */
    public Vector getTimestamps(String documentPath)
    throws PermissionDeniedException, EXistException, URISyntaxException {
    	return getTimestamps(XmldbURI.xmldbUriFor(documentPath));
    }

    /**
     * The method <code>getTimestamps</code>
     *
     * @param docUri a <code>XmldbURI</code> value
     * @return a <code>Vector</code> value
     * @exception PermissionDeniedException if an error occurs
     * @exception EXistException if an error occurs
     */
    private Vector getTimestamps(XmldbURI docUri)
    throws PermissionDeniedException, EXistException {
        DBBroker broker = null;
        DocumentImpl doc = null;
        try {
            broker = factory.getBrokerPool().get(user);
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
            factory.getBrokerPool().release(broker);
        }
    }
    
    /**
     * The method <code>getUser</code>
     *
     * @param name a <code>String</code> value
     * @return a <code>HashMap</code> value
     * @exception EXistException if an error occurs
     * @exception PermissionDeniedException if an error occurs
     */
    public HashMap getUser(String name) throws EXistException,
            PermissionDeniedException {
        User u = factory.getBrokerPool().getSecurityManager().getUser(name);
        if (u == null)
            throw new EXistException("user " + name + " does not exist");
        HashMap tab = new HashMap();
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
    
    /**
     * The method <code>getUsers</code>
     *
     * @return a <code>Vector</code> value
     * @exception EXistException if an error occurs
     * @exception PermissionDeniedException if an error occurs
     */
    public Vector getUsers() throws EXistException,
            PermissionDeniedException {
        User users[] = factory.getBrokerPool().getSecurityManager().getUsers();
        Vector r = new Vector();
        for (int i = 0; i < users.length; i++) {
            final HashMap tab = new HashMap();
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
    
    /**
     * The method <code>getGroups</code>
     *
     * @return a <code>Vector</code> value
     * @exception EXistException if an error occurs
     * @exception PermissionDeniedException if an error occurs
     */
    public Vector getGroups() throws EXistException,
            PermissionDeniedException {
        String[] groups = factory.getBrokerPool().getSecurityManager().getGroups();
        Vector v = new Vector(groups.length);
        for (int i = 0; i < groups.length; i++) {
            v.addElement(groups[i]);
        }
        return v;
    }
    
    /**
     * The method <code>hasDocument</code>
     *
     * @param documentPath a <code>String</code> value
     * @return a <code>boolean</code> value
     * @exception Exception if an error occurs
     * @exception URISyntaxException if an error occurs
     */
    public boolean hasDocument(String documentPath) throws URISyntaxException, EXistException, PermissionDeniedException {
    	return hasDocument(XmldbURI.xmldbUriFor(documentPath));
    }

    /**
     * The method <code>hasDocument</code>
     *
     * @param docUri a <code>XmldbURI</code> value
     * @return a <code>boolean</code> value
     * @exception Exception if an error occurs
     */
    private boolean hasDocument(XmldbURI docUri) throws EXistException, PermissionDeniedException {
        DBBroker broker = null;
        try {
            broker = factory.getBrokerPool().get(user);
            return (broker.getXMLResource(docUri) != null);
        } finally {
            factory.getBrokerPool().release(broker);
        }
    }
    
    /**
     * The method <code>hasCollection</code>
     *
     * @param collectionName a <code>String</code> value
     * @return a <code>boolean</code> value
     * @exception Exception if an error occurs
     * @exception URISyntaxException if an error occurs
     */
    public boolean hasCollection(String collectionName) throws EXistException, URISyntaxException {
    	return hasCollection(XmldbURI.xmldbUriFor(collectionName));
    }

    /**
     * The method <code>hasCollection</code>
     *
     * @param collUri a <code>XmldbURI</code> value
     * @return a <code>boolean</code> value
     * @exception Exception if an error occurs
     */
    private boolean hasCollection(XmldbURI collUri) throws EXistException {
        DBBroker broker = null;
        try {
            broker = factory.getBrokerPool().get(user);
            return (broker.getCollection(collUri) != null);
        } finally {
            factory.getBrokerPool().release(broker);
        }
    }
    
    
    /**
     * The method <code>parse</code>
     *
     * @param xml a <code>byte</code> value
     * @param documentPath a <code>String</code> value
     * @return a <code>boolean</code> value
     * @exception Exception if an error occurs
     * @exception URISyntaxException if an error occurs
     */
    public boolean parse(byte[] xml, String documentPath, int overwrite) throws URISyntaxException, EXistException, PermissionDeniedException {
        return parse(xml,documentPath, overwrite, null, null);
    }
    
    /**
     * The method <code>parse</code>
     *
     * @param xml a <code>byte</code> value
     * @param documentPath a <code>String</code> value
     * @param created a <code>Date</code> value
     * @param modified a <code>Date</code> value
     * @return a <code>boolean</code> value
     * @exception Exception if an error occurs
     * @exception URISyntaxException if an error occurs
     */
    public boolean parse(byte[] xml, String documentPath,
            int overwrite, Date created, Date modified) throws URISyntaxException, EXistException, PermissionDeniedException {
    	return parse(xml,XmldbURI.xmldbUriFor(documentPath),overwrite,created,modified);
    }

    /**
     * The method <code>parse</code>
     *
     * @param xml a <code>byte</code> value
     * @param docUri a <code>XmldbURI</code> value
     * @param created a <code>Date</code> value
     * @param modified a <code>Date</code> value
     * @return a <code>boolean</code> value
     * @exception Exception if an error occurs
     */
    private boolean parse(byte[] xml, XmldbURI docUri,
            int overwrite, Date created, Date modified) throws EXistException, PermissionDeniedException {
        DBBroker broker = null;       
        TransactionManager transact = factory.getBrokerPool().getTransactionManager();
        Txn transaction = transact.beginTransaction();
        try {
            long startTime = System.currentTimeMillis();
            broker = factory.getBrokerPool().get(user);
         
            IndexInfo info = null;
            InputSource source = null;
            Collection collection = null;
            try {
            	collection = broker.openCollection(docUri.removeLastSegment(), Lock.WRITE_LOCK);
	            if (collection == null) {
	                transact.abort(transaction);
	                throw new EXistException("Collection " + docUri.removeLastSegment() + " not found");
	            }
	
	            if (overwrite == 0) {
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
	            MimeType mime = MimeTable.getInstance().getContentTypeFor(docUri.lastSegment());
	            if (mime != null && mime.isXMLType()){
	            	info.getDocument().getMetadata().setMimeType(mime.getName());
	            }
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
            return true;

        } catch (Throwable e) {
            transact.abort(transaction);
            handleException(e);
            return false;
            
        } finally {
            factory.getBrokerPool().release(broker);
        }
    }
    
    
    
    /**
     * Parse a file previously uploaded with upload.
     *
     * The temporary file will be removed.
     *
     * @param localFile
     * @throws EXistException
     * @throws IOException
     */
    public boolean parseLocal(String localFile, String documentPath,
            int overwrite, String mimeType) throws Exception, URISyntaxException {
        return parseLocal(localFile, documentPath, overwrite, mimeType, null, null);
    }
    
    /**
     * Parse a file previously uploaded with upload, forcing it to XML or Binary.
     *
     * The temporary file will be removed.
     *
     * @param localFile
     * @throws EXistException
     * @throws IOException
     */
    public boolean parseLocalExt(String localFile, String documentPath,
            int overwrite, String mimeType, int isXML) throws Exception, URISyntaxException {
        return parseLocalExt(localFile, documentPath, overwrite, mimeType, isXML, null, null);
    }
    
    /**
     * The method <code>parseLocal</code>
     *
     * @param localFile a <code>String</code> value
     * @param docUri a <code>XmldbURI</code> value
     * @param mimeType a <code>String</code> value
     * @return a <code>boolean</code> value
     * @exception Exception if an error occurs
     */
    private boolean parseLocal(String localFile, XmldbURI docUri,
            int overwrite, String mimeType) throws Exception {
        return parseLocal(localFile, docUri, overwrite, mimeType, null, null, null);
    }
    
    /**
     * The method <code>parseLocal</code>
     *
     * @param localFile a <code>String</code> value
     * @param docUri a <code>XmldbURI</code> value
     * @param mimeType a <code>String</code> value
     * @return a <code>boolean</code> value
     * @exception Exception if an error occurs
     */
    private boolean parseLocalExt(String localFile, XmldbURI docUri,
            int overwrite, String mimeType, int isXML) throws Exception {
        return parseLocal(localFile, docUri, overwrite, mimeType, new Boolean(isXML!=0), null, null);
    }
    
    /**
     * The method <code>parseLocal</code>
     *
     * @param localFile a <code>String</code> value
     * @param documentPath a <code>String</code> value
     * @param mimeType a <code>String</code> value
     * @param created a <code>Date</code> value
     * @param modified a <code>Date</code> value
     * @return a <code>boolean</code> value
     * @exception Exception if an error occurs
     * @exception URISyntaxException if an error occurs
     */
    public boolean parseLocal(String localFile, String documentPath, int overwrite,
                              String mimeType, Date created, Date modified) throws URISyntaxException, EXistException, PermissionDeniedException {
    	return parseLocal(localFile,XmldbURI.xmldbUriFor(documentPath), overwrite, mimeType, null, created, modified);
    }
    
    /**
     * The method <code>parseLocal</code>
     *
     * @param localFile a <code>String</code> value
     * @param documentPath a <code>String</code> value
     * @param mimeType a <code>String</code> value
     * @param created a <code>Date</code> value
     * @param modified a <code>Date</code> value
     * @return a <code>boolean</code> value
     * @exception Exception if an error occurs
     * @exception URISyntaxException if an error occurs
     */
    public boolean parseLocalExt(String localFile, String documentPath, int overwrite,
                              String mimeType, int isXML, Date created, Date modified) throws URISyntaxException, EXistException, PermissionDeniedException {
    	return parseLocal(localFile,XmldbURI.xmldbUriFor(documentPath), overwrite, mimeType, new Boolean(isXML!=0), created, modified);
    }
    
    /**
     * The method <code>parseLocal</code>
     *
     * @param localFile a <code>String</code> value
     * @param docUri a <code>XmldbURI</code> value
     * @param mimeType a <code>String</code> value
     * @param created a <code>Date</code> value
     * @param modified a <code>Date</code> value
     * @return a <code>boolean</code> value
     * @exception Exception if an error occurs
     */
    private boolean parseLocal(String localFile, XmldbURI docUri,
            int overwrite, String mimeType, Boolean isXML, Date created, Date modified) throws EXistException, PermissionDeniedException {
        
    	File file = new File(localFile);
        if (!file.canRead())
            throw new EXistException("unable to read file " + localFile);
    
        TransactionManager transact = factory.getBrokerPool().getTransactionManager();
        Txn transaction = transact.beginTransaction();
        DBBroker broker = null;
        DocumentImpl doc = null;
        
        // DWES
        MimeType mime = MimeTable.getInstance().getContentType(mimeType);
        if (mime == null)
            mime = MimeType.BINARY_TYPE;
        
	boolean treatAsXML=(isXML!=null && isXML.booleanValue()) || (isXML==null && mime.isXMLType());
        try {
            broker = factory.getBrokerPool().get(user);
            Collection collection = null;
            IndexInfo info = null;
            InputSource source = null;
            
            try {

	            collection = broker.openCollection(docUri.removeLastSegment(), Lock.WRITE_LOCK);
	            if (collection == null) {
	                transact.abort(transaction);
	                throw new EXistException("Collection " + docUri.removeLastSegment() + " not found");
	            }
	
	            if (overwrite == 0) {
	                DocumentImpl old = collection.getDocument(broker, docUri.lastSegment());
	                if (old != null) {
	                    transact.abort(transaction);
	                    throw new PermissionDeniedException("Old document exists and overwrite is not allowed");
	                }
	            }
	            
	            //XML
	            if(treatAsXML) {
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
            if(treatAsXML){
                collection.store(transaction, broker, info, source, false);
            }
            
            // generic
            transact.commit(transaction);

        } catch (Throwable e) {
            transact.abort(transaction);
            handleException(e);

        } finally {
            factory.getBrokerPool().release(broker);
        }
        
        // DWES there are situations the file is not cleaned up
        file.delete();
        
        return true; // when arrived here, insert/update was successfull
    }
    
    /**
     * The method <code>storeBinary</code>
     *
     * @param data a <code>byte</code> value
     * @param documentPath a <code>String</code> value
     * @param mimeType a <code>String</code> value
     * @return a <code>boolean</code> value
     * @exception Exception if an error occurs
     * @exception URISyntaxException if an error occurs
     */
    public boolean storeBinary(byte[] data, String documentPath, String mimeType,
            int overwrite) throws Exception, URISyntaxException {
        return storeBinary(data, documentPath, mimeType, overwrite, null, null);
    }

    /**
     * The method <code>storeBinary</code>
     *
     * @param data a <code>byte</code> value
     * @param docUri a <code>XmldbURI</code> value
     * @param mimeType a <code>String</code> value
     * @return a <code>boolean</code> value
     * @exception Exception if an error occurs
     */
    private boolean storeBinary(byte[] data, XmldbURI docUri, String mimeType,
            int overwrite) throws Exception {
        return storeBinary(data, docUri, mimeType, overwrite, null, null);
    }

    /**
     * The method <code>storeBinary</code>
     *
     * @param data a <code>byte</code> value
     * @param documentPath a <code>String</code> value
     * @param mimeType a <code>String</code> value
     * @param created a <code>Date</code> value
     * @param modified a <code>Date</code> value
     * @return a <code>boolean</code> value
     * @exception Exception if an error occurs
     * @exception URISyntaxException if an error occurs
     */
    public boolean storeBinary(byte[] data, String documentPath, String mimeType,
            int overwrite, Date created, Date modified) throws URISyntaxException, EXistException, PermissionDeniedException {
    	return storeBinary(data,XmldbURI.xmldbUriFor(documentPath),mimeType,overwrite,created,modified);
    }    
    /**
     * The method <code>storeBinary</code>
     *
     * @param data a <code>byte</code> value
     * @param docUri a <code>XmldbURI</code> value
     * @param mimeType a <code>String</code> value
     * @param created a <code>Date</code> value
     * @param modified a <code>Date</code> value
     * @return a <code>boolean</code> value
     * @exception Exception if an error occurs
     */
    private boolean storeBinary(byte[] data, XmldbURI docUri, String mimeType,
            int overwrite, Date created, Date modified) throws EXistException, PermissionDeniedException {
        DBBroker broker = null;
        DocumentImpl doc = null;   
        TransactionManager transact = factory.getBrokerPool().getTransactionManager();
        Txn transaction = transact.beginTransaction();
        try {
            broker = factory.getBrokerPool().get(user);
            Collection collection = broker.openCollection(docUri.removeLastSegment(), Lock.WRITE_LOCK);
            if (collection == null) {
            	transact.abort(transaction);
                throw new EXistException("Collection " + docUri.removeLastSegment() + " not found");
            }
            // keep the write lock in the transaction
            transaction.registerLock(collection.getLock(), Lock.WRITE_LOCK);
            if (overwrite == 0) {
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

        } catch (Throwable e) {
            transact.abort(transaction);
            handleException(e);
            return false;

        } finally {
            factory.getBrokerPool().release(broker);
        }
        return doc != null;
    }
    
    /**
     * The method <code>upload</code>
     *
     * @param chunk a <code>byte</code> value
     * @param length an <code>int</code> value
     * @param fileName a <code>String</code> value
     * @param compressed a <code>boolean</code> value
     * @return a <code>String</code> value
     * @exception EXistException if an error occurs
     * @exception IOException if an error occurs
     */
    public String upload(byte[] chunk, int length, String fileName, boolean compressed)
    throws EXistException, IOException {
        File file;
        if (fileName == null || fileName.length() == 0) {
            // create temporary file
            file = File.createTempFile("rpc", ".xml");
            file.deleteOnExit();
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
    
    /**
     * The method <code>printAll</code>
     *
     * @param broker a <code>DBBroker</code> value
     * @param resultSet a <code>Sequence</code> value
     * @param howmany an <code>int</code> value
     * @param start an <code>int</code> value
     * @param properties a <code>HashMap</code> value
     * @param queryTime a <code>long</code> value
     * @return a <code>String</code> value
     * @exception Exception if an error occurs
     */
    protected String printAll(DBBroker broker, Sequence resultSet, int howmany,
            int start, HashMap properties, long queryTime) throws Exception {
        if (resultSet.isEmpty()) {
            StringBuilder buf = new StringBuilder();
            String opt = (String) properties.get(OutputKeys.OMIT_XML_DECLARATION);
            if (opt == null || opt.equalsIgnoreCase("no"))
                buf.append("<?xml version=\"1.0\"?>\n");
            buf.append("<exist:result xmlns:exist=\"").append(Namespaces.EXIST_NS).append("\" ");
            buf.append("hitCount=\"0\"/>");
            return buf.toString();
        }
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
    
    /**
     * The method <code>compile</code>
     *
     * @param query a <code>String</code> value
     * @param parameters a <code>HashMap</code> value
     * @return a <code>HashMap</code> value
     * @exception Exception if an error occurs
     */
    public HashMap compile(String query, HashMap parameters) throws EXistException, PermissionDeniedException {
        HashMap ret = new HashMap();
        DBBroker broker = null;
        XQueryPool pool = null;
        CompiledXQuery compiled = null;
        Source source = new StringSource(query);
        try {
            broker = factory.getBrokerPool().get(user);
            XQuery xquery = broker.getXQueryService();
            pool = xquery.getXQueryPool();
            compiled = compile(broker, source, parameters);
            
        } catch (XPathException e) {
            ret.put(RpcAPI.ERROR, e.getMessage());
            if(e.getLine() != 0) {
                ret.put(RpcAPI.LINE, new Integer(e.getLine()));
                ret.put(RpcAPI.COLUMN, new Integer(e.getColumn()));
            }

        } catch (Throwable e) {
            handleException(e);

        } finally {
            factory.getBrokerPool().release(broker);
            if(compiled != null && pool != null)
                pool.returnCompiledXQuery(source, compiled);
        }
        return ret;
    }
    
    /**
     * The method <code>query</code>
     *
     * @param xpath a <code>String</code> value
     * @param howmany an <code>int</code> value
     * @param start an <code>int</code> value
     * @param parameters a <code>HashMap</code> value
     * @return a <code>String</code> value
     * @exception Exception if an error occurs
     */
    public String query(String xpath, int howmany, int start,
            HashMap parameters) throws EXistException, PermissionDeniedException {
        long startTime = System.currentTimeMillis();
        String result = null;
        DBBroker broker = null;
        try {
            broker = factory.getBrokerPool().get(user);
            QueryResult qr = doQuery(broker, xpath, null, parameters);
            if (qr.hasErrors())
                throw qr.getException();
            if (qr == null)
                return "<?xml version=\"1.0\"?>\n"
                        + "<exist:result xmlns:exist=\"" + Namespaces.EXIST_NS + "\" "
                        + "hitCount=\"0\"/>";
            
            result = printAll(broker, qr.result, howmany, start, parameters,
                    (System.currentTimeMillis() - startTime));

        } catch (Throwable e) {
            handleException(e);

        } finally {
            factory.getBrokerPool().release(broker);
        }
        return result;
    }
    
    /**
     * The method <code>queryP</code>
     *
     * @param xpath a <code>String</code> value
     * @param documentPath a <code>String</code> value
     * @param s_id a <code>String</code> value
     * @param parameters a <code>HashMap</code> value
     * @return a <code>HashMap</code> value
     * @exception Exception if an error occurs
     * @exception URISyntaxException if an error occurs
     */
    public HashMap queryP(String xpath, String documentPath,
            String s_id, HashMap parameters) throws URISyntaxException, EXistException, PermissionDeniedException {
    	return queryP(xpath,
                      (documentPath==null) ? null : XmldbURI.xmldbUriFor(documentPath),
                      s_id, parameters);
    }    
    
    /**
     * The method <code>queryP</code>
     *
     * @param xpath a <code>String</code> value
     * @param docUri a <code>XmldbURI</code> value
     * @param s_id a <code>String</code> value
     * @param parameters a <code>HashMap</code> value
     * @return a <code>HashMap</code> value
     * @exception Exception if an error occurs
     */
    private HashMap queryP(String xpath, XmldbURI docUri,
            String s_id, HashMap parameters) throws EXistException, PermissionDeniedException {
        long startTime = System.currentTimeMillis();
        String sortBy = (String) parameters.get(RpcAPI.SORT_EXPR);
        
        HashMap ret = new HashMap();
        List result = new ArrayList();
        NodeSet nodes = null;
        QueryResult queryResult;
        Sequence resultSeq = null;
        DBBroker broker = null;
        try {
            broker = factory.getBrokerPool().get(user);
            if (docUri != null && s_id != null) {
                DocumentImpl doc = (DocumentImpl) broker.getXMLResource(docUri);
                Object[] docs = new Object[1];
                docs[0] = docUri.toString();
                parameters.put(RpcAPI.STATIC_DOCUMENTS, docs);
                
                if(s_id.length() > 0) {
                    NodeId nodeId = factory.getBrokerPool().getNodeFactory().createFromString(s_id);
                    NodeProxy node = new NodeProxy(doc, nodeId);
                    nodes = new ExtArrayNodeSet(1);
                    nodes.add(node);
                }
            }
            queryResult = doQuery(broker, xpath, nodes, parameters);
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
                SortedNodeSet sorted = new SortedNodeSet(factory.getBrokerPool(), user,
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
                            result.add(entry);
                        } else
                            result.add(next.getStringValue());
                    }
                } else {
                    LOG.debug("sequence iterator is null. Should not");
                }
            } else
                LOG.debug("result sequence is null. Skipping it...");

        } catch (Throwable e) {
            handleException(e);
            return null;

        } finally {
            factory.getBrokerPool().release(broker);
        }
        
        queryResult.result = resultSeq;
        queryResult.queryTime = (System.currentTimeMillis() - startTime);
        int id = factory.resultSets.add(queryResult);
        ret.put("id", new Integer(id));
        ret.put("hash", new Integer(queryResult.hashCode()));
        ret.put("results", result);
        return ret;
    }
    
    /**
     * The method <code>execute</code>
     *
     * @param pathToQuery database path pointing to a stored XQuery
     * @param parameters a <code>HashMap</code> value
     * @return a <code>HashMap</code> value
     * @exception Exception if an error occurs
     */
    public HashMap execute(String pathToQuery, HashMap parameters) throws EXistException, PermissionDeniedException {
        long startTime = System.currentTimeMillis();
        byte[] doc = getBinaryResource(XmldbURI.createInternal(pathToQuery));
        String xpath = null;
        try {
            xpath = new String(doc, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new EXistException("Character encoding issue while reading stored XQuery: " + e.getMessage());
        }
        
        String sortBy = (String) parameters.get(RpcAPI.SORT_EXPR);
        
        HashMap ret = new HashMap();
        Vector result = new Vector();
        NodeSet nodes = null;
        QueryResult queryResult;
        Sequence resultSeq = null;
        DBBroker broker = null;
        try {
            broker = factory.getBrokerPool().get(user);
            
            queryResult = doQuery(broker, xpath, nodes, parameters);
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
                SortedNodeSet sorted = new SortedNodeSet(factory.getBrokerPool(), user,
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

        } catch (Throwable e) {
            handleException(e);
            return null;

        } finally {
            factory.getBrokerPool().release(broker);
        }
        queryResult.result = resultSeq;
        queryResult.queryTime = (System.currentTimeMillis() - startTime);
        int id = factory.resultSets.add(queryResult);
        ret.put("id", new Integer(id));
        ret.put("results", result);
        return ret;
    }
    
    /**
     * The method <code>releaseQueryResult</code>
     *
     * @param handle an <code>int</code> value
     */
    public boolean releaseQueryResult(int handle) {
        factory.resultSets.remove(handle);
        LOG.debug("removed query result with handle " + handle);
        return true;
    }

    public boolean releaseQueryResult(int handle, int hash) {
        factory.resultSets.remove(handle, hash);
        LOG.debug("removed query result with handle " + handle);
        return true;
    }

    /**
     * The method <code>remove</code>
     *
     * @param documentPath a <code>String</code> value
     * @exception Exception if an error occurs
     * @exception URISyntaxException if an error occurs
     */
    public boolean remove(String documentPath) throws URISyntaxException, EXistException, PermissionDeniedException {
    	return remove(XmldbURI.xmldbUriFor(documentPath));
    }    

    /**
     * The method <code>remove</code>
     *
     * @param docUri a <code>XmldbURI</code> value
     * @exception Exception if an error occurs
     */
    private boolean remove(XmldbURI docUri) throws EXistException, PermissionDeniedException {
        TransactionManager transact = factory.getBrokerPool().getTransactionManager();
        Txn transaction = transact.beginTransaction();
        DBBroker broker = null;
        Collection collection = null;
        try {
            broker = factory.getBrokerPool().get(user);
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
            return true;

        } catch (Throwable e) {
            handleException(e);
            return false;
            
        } finally {
            factory.getBrokerPool().release(broker);
        }
    }
    
    /**
     * The method <code>removeCollection</code>
     *
     * @param collectionName a <code>String</code> value
     * @return a <code>boolean</code> value
     * @exception Exception if an error occurs
     * @exception URISyntaxException if an error occurs
     */
    public boolean removeCollection(String collectionName) throws URISyntaxException, EXistException, PermissionDeniedException {
    	return removeCollection(XmldbURI.xmldbUriFor(collectionName));
    } 
    
    /**
     * The method <code>removeCollection</code>
     *
     * @param collURI a <code>XmldbURI</code> value
     * @return a <code>boolean</code> value
     * @exception Exception if an error occurs
     */
    private boolean removeCollection(XmldbURI collURI) throws EXistException, PermissionDeniedException {
        TransactionManager transact = factory.getBrokerPool().getTransactionManager();
        Txn transaction = transact.beginTransaction();
        DBBroker broker = null;
        Collection collection = null;
        try {
            broker = factory.getBrokerPool().get(user);
            collection = broker.openCollection(collURI, Lock.WRITE_LOCK);
            if (collection == null) {
            	transact.abort(transaction);
                return false;
            }
            // keep the write lock in the transaction
            transaction.registerLock(collection.getLock(), Lock.WRITE_LOCK);
            LOG.debug("removing collection " + collURI);
            boolean removed = broker.removeCollection(transaction, collection);
            transact.commit(transaction);
            return removed;

        } catch (Throwable e) {
            transact.abort(transaction);
            handleException(e);
            return false;

        } finally {
            factory.getBrokerPool().release(broker);
        }
    }
    
    /**
     * The method <code>removeUser</code>
     *
     * @param name a <code>String</code> value
     * @return a <code>boolean</code> value
     * @exception EXistException if an error occurs
     * @exception PermissionDeniedException if an error occurs
     */
    public boolean removeUser(String name) throws EXistException,
            PermissionDeniedException {
        SecurityManager manager = factory.getBrokerPool()
                .getSecurityManager();
        if (!manager.hasAdminPrivileges(user))
            throw new PermissionDeniedException(
                    "you are not allowed to remove users");
        
        manager.deleteUser(name);
        return true;
    }

    public byte[] retrieve(String doc, String id, HashMap parameters)
            throws EXistException, PermissionDeniedException {
        String xml = null;
        try {
            xml = retrieveAsString(doc, id, parameters);
            try {
                String encoding = (String) parameters.get(OutputKeys.ENCODING);
                if (encoding == null)
                    encoding = "UTF-8";
                return xml.getBytes(encoding);
            } catch (UnsupportedEncodingException uee) {
                LOG.warn(uee);
                return xml.getBytes();
            }

        } catch (Throwable e) {
            handleException(e);
            return null;
        }
    }

    /**
     * The method <code>retrieve</code>
     *
     * @param documentPath a <code>String</code> value
     * @param s_id a <code>String</code> value
     * @param parameters a <code>HashMap</code> value
     * @return a <code>String</code> value
     * @exception Exception if an error occurs
     * @exception URISyntaxException if an error occurs
     */
    public String retrieveAsString(String documentPath, String s_id,
            HashMap parameters) throws URISyntaxException, EXistException, PermissionDeniedException {
    	return retrieve(XmldbURI.xmldbUriFor(documentPath),s_id,parameters);
    }    

    /**
     * The method <code>retrieve</code>
     *
     * @param docUri a <code>XmldbURI</code> value
     * @param s_id a <code>String</code> value
     * @param parameters a <code>HashMap</code> value
     * @return a <code>String</code> value
     * @exception Exception if an error occurs
     */
    private String retrieve(XmldbURI docUri, String s_id,
            HashMap parameters) throws EXistException, PermissionDeniedException {
        DBBroker broker = null;
        try {
            broker = factory.getBrokerPool().get(user);
            NodeId nodeId = factory.getBrokerPool().getNodeFactory().createFromString(s_id);
            DocumentImpl doc;
                LOG.debug("loading doc " + docUri);
                doc = (DocumentImpl) broker.getXMLResource(docUri);
            NodeProxy node = new NodeProxy(doc, nodeId);
            Serializer serializer = broker.getSerializer();
            serializer.reset();
            serializer.setProperties(parameters);
            return serializer.serialize(node);

        } catch (Throwable e) {
            handleException(e);
            return null;

        } finally {
            factory.getBrokerPool().release(broker);
        }
    }

	/**
	 * The method <code>retrieveFirstChunk</code>
	 *
	 * @param docName a <code>String</code> value
	 * @param id a <code>String</code> value
	 * @param parameters a <code>HashMap</code> value
	 * @return a <code>HashMap</code> value
	 * @exception Exception if an error occurs
	 */
	public HashMap retrieveFirstChunk(String docName, String id, HashMap parameters)
		throws EXistException, PermissionDeniedException
	{
        	DBBroker broker = null;
		String encoding = (String) parameters.get(OutputKeys.ENCODING);
		if (encoding == null)
			encoding = "UTF-8";
		String compression = "no";
		if (((String) parameters.get(EXistOutputKeys.COMPRESS_OUTPUT)) != null) {
			compression = (String) parameters.get(EXistOutputKeys.COMPRESS_OUTPUT);
		}
        	try {
			XmldbURI docUri=XmldbURI.xmldbUriFor(docName);
			broker = factory.getBrokerPool().get(user);
			NodeId nodeId = factory.getBrokerPool().getNodeFactory().createFromString(id);
			DocumentImpl doc;
			LOG.debug("loading doc " + docUri);
			doc = (DocumentImpl) broker.getXMLResource(docUri);
			NodeProxy node = new NodeProxy(doc, nodeId);
			
			Serializer serializer = broker.getSerializer();
			serializer.reset();
			serializer.setProperties(parameters);
			
                	HashMap result = new HashMap();
			File tempFile = File.createTempFile("eXistRPCC", ".xml");
			tempFile.deleteOnExit();
			LOG.debug("Writing to temporary file: " + tempFile.getName());

			FileOutputStream fos = new FileOutputStream(tempFile);
			OutputStream os=null;
			if(compression.equals("yes")) {
				LOG.debug("get result with compression");
				os = new DeflaterOutputStream(fos);
			} else {
				os = fos;
			}
			try {
				Writer writer = new OutputStreamWriter(os, encoding);
				try {
					serializer.serialize(node, writer);
				} finally {
					writer.close();
				}
			} finally {
				try {
					os.close();
				} catch(IOException ioe) {
					//IgnoreIT(R)
				}
				if(os!=fos)
					try {
						fos.close();
					} catch(IOException ioe) {
						//IgnoreIT(R)
					}
			}
			
			byte[] firstChunk = getChunk(tempFile, 0);
			result.put("data", firstChunk);
			if(tempFile.length() > MAX_DOWNLOAD_CHUNK_SIZE) {
				result.put("handle", tempFile.getAbsolutePath());
				result.put("offset", new Integer(firstChunk.length));
				result.put("supports-long-offset", new Boolean(true));
                	} else {
				result.put("offset", new Integer(0));
				tempFile.delete();
                	}
                	return result;

        	} catch (Throwable e) {
                handleException(e);
                return null;

        	} finally {
                factory.getBrokerPool().release(broker);
        	}
	}

    public byte[] retrieve(int resultId, int num, HashMap parameters)
            throws EXistException, PermissionDeniedException {
        String compression = "no";
        if (((String) parameters.get(EXistOutputKeys.COMPRESS_OUTPUT)) != null) {
            compression = (String) parameters.get(EXistOutputKeys.COMPRESS_OUTPUT);
        }

        try {
            String xml = retrieveAsString(resultId, num, parameters);
            String encoding = (String) parameters.get(OutputKeys.ENCODING);
            if (encoding == null)
                encoding = "UTF-8";
            try {

                if (compression.equals("no")) {
                    return xml.getBytes(encoding);
                } else {
                    LOG.debug("get result with compression");
                    return Compressor.compress(xml.getBytes(encoding));
                }

            } catch (UnsupportedEncodingException uee) {
                LOG.warn(uee);
                if (compression.equals("no")) {
                    return xml.getBytes();
                } else {
                    LOG.debug("get result with compression");
                    return Compressor.compress(xml.getBytes());
                }
            }

        } catch (Throwable e) {
            handleException(e);
            return null;
        }
    }

    /**
     * The method <code>retrieve</code>
     *
     * @param resultId an <code>int</code> value
     * @param num an <code>int</code> value
     * @param parameters a <code>HashMap</code> value
     * @return a <code>String</code> value
     * @exception Exception if an error occurs
     */
    private String retrieveAsString(int resultId, int num,
            HashMap parameters) throws Exception {
        DBBroker broker = null;
        try {
            broker = factory.getBrokerPool().get(user);
            QueryResult qr = factory.resultSets.get(resultId);
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
                for (Iterator i = qr.serialization.entrySet().iterator(); i.hasNext();) {
                    Map.Entry entry = (Map.Entry) i.next();
                    parameters.put(entry.getKey().toString(), entry.getValue().toString());
                }
                serializer.setProperties(parameters);
                return serializer.serialize(nodeValue);
            } else {
                return item.getStringValue();
            }
        } finally {
            factory.getBrokerPool().release(broker);
        }
    }

	/**
	 * The method <code>retrieveFirstChunk</code>
	 *
	 * @param resultId an <code>int</code> value
	 * @param num an <code>int</code> value
	 * @param parameters a <code>HashMap</code> value
	 * @return a <code>HashMap</code> value
	 * @exception Exception if an error occurs
	 */
	public HashMap retrieveFirstChunk(int resultId, int num, HashMap parameters)
		throws EXistException, PermissionDeniedException
	{
		String encoding = (String) parameters.get(OutputKeys.ENCODING);
		if (encoding == null)
			encoding = "UTF-8";
		String compression = "no";
		if (((String) parameters.get(EXistOutputKeys.COMPRESS_OUTPUT)) != null) {
			compression = (String) parameters.get(EXistOutputKeys.COMPRESS_OUTPUT);
		}
        	DBBroker broker = null;
        	try {
			broker = factory.getBrokerPool().get(user);
			QueryResult qr = factory.resultSets.get(resultId);
			if (qr == null)
				throw new EXistException("result set unknown or timed out: " + resultId);
			qr.timestamp = System.currentTimeMillis();
			Item item = qr.result.itemAt(num);
			if (item == null)
				throw new EXistException("index out of range");
			
                	HashMap result = new HashMap();
			File tempFile = File.createTempFile("eXistRPCC", ".xml");
			tempFile.deleteOnExit();
			LOG.debug("Writing to temporary file: " + tempFile.getName());

			FileOutputStream fos = new FileOutputStream(tempFile);
			OutputStream os=null;
			if(compression.equals("yes")) {
				LOG.debug("get result with compression");
				os = new DeflaterOutputStream(fos);
			} else {
				os = fos;
			}
			try {
				Writer writer = new OutputStreamWriter(os, encoding);
				try {
					if(Type.subTypeOf(item.getType(), Type.NODE)) {
						NodeValue nodeValue = (NodeValue)item;
						Serializer serializer = broker.getSerializer();
						serializer.reset();
						for (Iterator i = qr.serialization.entrySet().iterator(); i.hasNext();) {
							Map.Entry entry = (Map.Entry) i.next();
							parameters.put(entry.getKey().toString(), entry.getValue().toString());
						}
						serializer.setProperties(parameters);
						
						serializer.serialize(nodeValue, writer);
					} else {
						writer.write(item.getStringValue());
					}
				} finally {
					try {
						writer.close();
					} catch(IOException ioe) {
						//IgnoreIT(R)
					}
				}
			} finally {
				try {
					os.close();
				} catch(IOException ioe) {
					//IgnoreIT(R)
				}
				if(os!=fos)
					try {
						fos.close();
					} catch(IOException ioe) {
						//IgnoreIT(R)
					}
			}
			
			byte[] firstChunk = getChunk(tempFile, 0);
			result.put("data", firstChunk);
			if(tempFile.length() > MAX_DOWNLOAD_CHUNK_SIZE) {
				result.put("handle", tempFile.getAbsolutePath());
				result.put("offset", new Integer(firstChunk.length));
				result.put("supports-long-offset", new Boolean(true));
                	} else {
				result.put("offset", new Integer(0));
				tempFile.delete();
                	}
                	return result;

        	} catch (Throwable e) {
                handleException(e);
                return null;

        	} finally {
                factory.getBrokerPool().release(broker);
        	}
	}


    public byte[] retrieveAll(int resultId, HashMap parameters) throws EXistException,
            PermissionDeniedException {
        try {
            String xml = retrieveAllAsString(resultId, parameters);
            String encoding = (String) parameters.get(OutputKeys.ENCODING);
            if (encoding == null)
                encoding = "UTF-8";
            try {
                return xml.getBytes(encoding);
            } catch (UnsupportedEncodingException uee) {
                LOG.warn(uee);
                return xml.getBytes();
            }

        } catch (Throwable e) {
            handleException(e);
            return null;
        }
    }

    /**
     * The method <code>retrieveAll</code>
     *
     * @param resultId an <code>int</code> value
     * @param parameters a <code>HashMap</code> value
     * @return a <code>String</code> value
     * @exception Exception if an error occurs
     */
    private String retrieveAllAsString(int resultId, HashMap parameters) throws Exception {
        DBBroker broker = null;
        try {
            broker = factory.getBrokerPool().get(user);
            QueryResult qr = factory.resultSets.get(resultId);
            if (qr == null)
                throw new EXistException("result set unknown or timed out");
            qr.timestamp = System.currentTimeMillis();
            Serializer serializer = broker.getSerializer();
            serializer.reset();
            serializer.setProperties(qr.serialization);
            
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
                    current.toSAX(broker, handler, null);
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
            factory.getBrokerPool().release(broker);
        }
    }
    
	/**
	 * The method <code>retrieveAllFirstChunk</code>
	 *
	 * @param resultId an <code>int</code> value
	 * @param parameters a <code>HashMap</code> value
	 * @return a <code>String</code> value
	 * @exception Exception if an error occurs
	 */
	public HashMap retrieveAllFirstChunk(int resultId, HashMap parameters)
		throws EXistException, PermissionDeniedException
	{
		String encoding = (String) parameters.get(OutputKeys.ENCODING);
		if (encoding == null)
			encoding = "UTF-8";
		String compression = "no";
		if (((String) parameters.get(EXistOutputKeys.COMPRESS_OUTPUT)) != null) {
			compression = (String) parameters.get(EXistOutputKeys.COMPRESS_OUTPUT);
		}
        	DBBroker broker = null;
        	try {
			broker = factory.getBrokerPool().get(user);
			QueryResult qr = factory.resultSets.get(resultId);
			if (qr == null)
				throw new EXistException("result set unknown or timed out");
			qr.timestamp = System.currentTimeMillis();
			Serializer serializer = broker.getSerializer();
			serializer.reset();
			for (Iterator i = qr.serialization.entrySet().iterator(); i.hasNext();) {
				Map.Entry entry = (Map.Entry) i.next();
				parameters.put(entry.getKey().toString(), entry.getValue().toString());
			}
			serializer.setProperties(parameters);
			SAXSerializer handler = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);
			
                	HashMap result = new HashMap();
			File tempFile = File.createTempFile("eXistRPCC", ".xml");
			tempFile.deleteOnExit();
			LOG.debug("Writing to temporary file: " + tempFile.getName());

			FileOutputStream fos = new FileOutputStream(tempFile);
			OutputStream os=null;
			if(compression.equals("yes")) {
				LOG.debug("get result with compression");
				os = new DeflaterOutputStream(fos);
			} else {
				os = fos;
			}
			try {
				Writer writer = new OutputStreamWriter(os, encoding);
				try {
					handler.setOutput(writer, getProperties(parameters));
            
					// serialize results
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
				} finally {
					writer.close();
				}
			} finally {
				try {
					os.close();
				} catch(IOException ioe) {
					//IgnoreIT(R)
				}
				if(os!=fos)
					try {
						fos.close();
					} catch(IOException ioe) {
						//IgnoreIT(R)
					}
			}
			
			byte[] firstChunk = getChunk(tempFile, 0);
			result.put("data", firstChunk);
			if(tempFile.length() > MAX_DOWNLOAD_CHUNK_SIZE) {
				result.put("handle", tempFile.getAbsolutePath());
				result.put("offset", new Integer(firstChunk.length));
				result.put("supports-long-offset", new Boolean(true));
                	} else {
				result.put("offset", new Integer(0));
				tempFile.delete();
                	}
                	return result;

        	} catch (Throwable e) {
                handleException(e);
                return null;

        	} finally {
                factory.getBrokerPool().release(broker);
        	}
	}

    /**
     * The method <code>setPermissions</code>
     *
     * @param resource a <code>String</code> value
     * @param owner a <code>String</code> value
     * @param ownerGroup a <code>String</code> value
     * @param permissions a <code>String</code> value
     * @return a <code>boolean</code> value
     * @exception EXistException if an error occurs
     * @exception PermissionDeniedException if an error occurs
     * @exception URISyntaxException if an error occurs
     */
    public boolean setPermissions(String resource, String owner,
            String ownerGroup, String permissions) throws EXistException,
            PermissionDeniedException, URISyntaxException {
    	return setPermissions(XmldbURI.xmldbUriFor(resource),owner,ownerGroup,permissions);
    }    

    /**
     * The method <code>setPermissions</code>
     *
     * @param uri a <code>XmldbURI</code> value
     * @param owner a <code>String</code> value
     * @param ownerGroup a <code>String</code> value
     * @param permissions a <code>String</code> value
     * @return a <code>boolean</code> value
     * @exception EXistException if an error occurs
     * @exception PermissionDeniedException if an error occurs
     */
    private boolean setPermissions(XmldbURI uri, String owner,
            String ownerGroup, String permissions) throws EXistException,
            PermissionDeniedException {
        DBBroker broker = null;
        Collection collection = null;
        DocumentImpl doc = null;
        TransactionManager transact = factory.getBrokerPool().getTransactionManager();
        Txn transaction = transact.beginTransaction();
        try {
            broker = factory.getBrokerPool().get(user);
            SecurityManager manager = factory.getBrokerPool()
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
                if (perm.validate(user, Permission.WRITE)
                || manager.hasAdminPrivileges(user)) {
                    if (owner != null) {
                        if (!(perm.getOwner().equals(user.getName()) || manager.hasAdminPrivileges(user)))
                            throw new PermissionDeniedException("not allowed to change permissions");
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
                if (perm.validate(user, Permission.WRITE)
                || manager.hasAdminPrivileges(user)) {
                    if (permissions != null)
                        perm.setPermissions(permissions);
                    if (owner != null) {
                        if (!(perm.getOwner().equals(user.getName()) || manager.hasAdminPrivileges(user)))
                            throw new PermissionDeniedException("not allowed to change permissions");
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
            factory.getBrokerPool().release(broker);
        }
    }
    
    /**
     * The method <code>setPermissions</code>
     *
     * @param resource a <code>String</code> value
     * @param owner a <code>String</code> value
     * @param ownerGroup a <code>String</code> value
     * @param permissions an <code>int</code> value
     * @return a <code>boolean</code> value
     * @exception EXistException if an error occurs
     * @exception PermissionDeniedException if an error occurs
     * @exception URISyntaxException if an error occurs
     */
    public boolean setPermissions(String resource, String owner,
            String ownerGroup, int permissions) throws EXistException,
            PermissionDeniedException, URISyntaxException {
    	return setPermissions(XmldbURI.xmldbUriFor(resource),owner,ownerGroup,permissions);
    }    

    /**
     * The method <code>setPermissions</code>
     *
     * @param uri a <code>XmldbURI</code> value
     * @param owner a <code>String</code> value
     * @param ownerGroup a <code>String</code> value
     * @param permissions an <code>int</code> value
     * @return a <code>boolean</code> value
     * @exception EXistException if an error occurs
     * @exception PermissionDeniedException if an error occurs
     */
    private boolean setPermissions(XmldbURI uri, String owner,
            String ownerGroup, int permissions) throws EXistException,
            PermissionDeniedException {
        DBBroker broker = null;
        Collection collection = null;
        DocumentImpl doc = null;
        TransactionManager transact = factory.getBrokerPool().getTransactionManager();
        Txn transaction = transact.beginTransaction();
        try {
            broker = factory.getBrokerPool().get(user);
            SecurityManager manager = factory.getBrokerPool().getSecurityManager();
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
                if (perm.validate(user, Permission.WRITE)
                || manager.hasAdminPrivileges(user)) {
                    if (owner != null) {
                        if (!(perm.getOwner().equals(user.getName()) || manager.hasAdminPrivileges(user)))
                            throw new PermissionDeniedException("not allowed to change permissions");
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
            if (perm.validate(user, Permission.WRITE)
            || manager.hasAdminPrivileges(user)) {
                perm.setPermissions(permissions);
                if (owner != null) {
                    if (!(perm.getOwner().equals(user.getName()) || manager.hasAdminPrivileges(user)))
                        throw new PermissionDeniedException("not allowed to change permissions");
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
            factory.getBrokerPool().release(broker);
        }
    }
    
    /**
     * The method <code>setUser</code>
     *
     * @param name a <code>String</code> value
     * @param passwd a <code>String</code> value
     * @param passwdDigest a <code>String</code> value
     * @param groups a <code>Vector</code> value
     * @param home a <code>String</code> value
     * @return a <code>boolean</code> value
     * @exception EXistException if an error occurs
     * @exception PermissionDeniedException if an error occurs
     */
    public boolean setUser(String name, String passwd, String passwdDigest,
            Vector groups, String home) throws EXistException,
            PermissionDeniedException {
        if (passwd.length() == 0)
            passwd = null;
        SecurityManager manager = factory.getBrokerPool()
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
    public boolean setUser(String name, Vector groups) throws EXistException,
    PermissionDeniedException {

    	SecurityManager manager = factory.getBrokerPool()
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
    public boolean setUser(String name, Vector groups, String rgroup) throws EXistException,
    PermissionDeniedException {

    	SecurityManager manager = factory.getBrokerPool()
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
    
    /**
     * The method <code>lockResource</code>
     *
     * @param documentPath a <code>String</code> value
     * @param userName a <code>String</code> value
     * @return a <code>boolean</code> value
     * @exception Exception if an error occurs
     * @exception URISyntaxException if an error occurs
     */
    public boolean lockResource(String documentPath, String userName) throws EXistException, PermissionDeniedException, URISyntaxException {
    	return lockResource(XmldbURI.xmldbUriFor(documentPath),userName);
    }    

    /**
     * The method <code>lockResource</code>
     *
     * @param docURI a <code>XmldbURI</code> value
     * @param userName a <code>String</code> value
     * @return a <code>boolean</code> value
     * @exception Exception if an error occurs
     */
    private boolean lockResource(XmldbURI docURI, String userName) throws EXistException, PermissionDeniedException {
        DBBroker broker = null;
        DocumentImpl doc = null;
        TransactionManager transact = factory.getBrokerPool().getTransactionManager();
        Txn transaction = transact.beginTransaction();
        try {
            broker = factory.getBrokerPool().get(user);
            doc = broker.getXMLResource(docURI, Lock.WRITE_LOCK);           
            if (doc == null) {
                throw new EXistException("Resource " + docURI + " not found");
            }
            //TODO : register the lock within the transaction ?
            if (!doc.getPermissions().validate(user, Permission.UPDATE))
                throw new PermissionDeniedException("User is not allowed to lock resource " + docURI);
            SecurityManager manager = factory.getBrokerPool().getSecurityManager();
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

        } catch (Throwable e) {
            transact.abort(transaction);
            handleException(e);
            return false;

        } finally {
            if(doc != null)
                doc.getUpdateLock().release(Lock.WRITE_LOCK);
            factory.getBrokerPool().release(broker);
        }
    }
    
    /**
     * The method <code>hasUserLock</code>
     *
     * @param documentPath a <code>String</code> value
     * @return a <code>String</code> value
     * @exception Exception if an error occurs
     * @exception URISyntaxException if an error occurs
     */
    public String hasUserLock(String documentPath) throws URISyntaxException, EXistException, PermissionDeniedException {
    	return hasUserLock(XmldbURI.xmldbUriFor(documentPath));
    }    

    /**
     * The method <code>hasUserLock</code>
     *
     * @param docURI a <code>XmldbURI</code> value
     * @return a <code>String</code> value
     * @exception Exception if an error occurs
     */
    private String hasUserLock(XmldbURI docURI) throws EXistException, PermissionDeniedException {
        DBBroker broker = null;
        DocumentImpl doc = null;
        try {
            broker = factory.getBrokerPool().get(user);
            doc = broker.getXMLResource(docURI, Lock.READ_LOCK);
            if(!doc.getPermissions().validate(user, Permission.READ))
                throw new PermissionDeniedException("Insufficient privileges to read resource");
            if (doc == null)
                throw new EXistException("Resource " + docURI + " not found");
            User u = doc.getUserLock();
            return u == null ? "" : u.getName();

        } catch (Throwable e) {
            handleException(e);
            return null;

        } finally {
            if(doc != null)
                doc.getUpdateLock().release(Lock.READ_LOCK);
            factory.getBrokerPool().release(broker);
        }
    }
    
    /**
     * The method <code>unlockResource</code>
     *
     * @param documentPath a <code>String</code> value
     * @return a <code>boolean</code> value
     * @exception Exception if an error occurs
     * @exception URISyntaxException if an error occurs
     */
    public boolean unlockResource(String documentPath) throws URISyntaxException, EXistException, PermissionDeniedException {
    	return unlockResource(XmldbURI.xmldbUriFor(documentPath));
    }    

    /**
     * The method <code>unlockResource</code>
     *
     * @param docURI a <code>XmldbURI</code> value
     * @return a <code>boolean</code> value
     * @exception Exception if an error occurs
     */
    private boolean unlockResource(XmldbURI docURI) throws EXistException, PermissionDeniedException {
        DBBroker broker = null;
        DocumentImpl doc = null;
        try {
            broker = factory.getBrokerPool().get(user);
            doc = broker.getXMLResource(docURI, Lock.WRITE_LOCK);
            if (doc == null)
                throw new EXistException("Resource "
                        + docURI + " not found");
            if (!doc.getPermissions().validate(user, Permission.UPDATE))
                throw new PermissionDeniedException("User is not allowed to lock resource " + docURI);
            SecurityManager manager = factory.getBrokerPool().getSecurityManager();
            User lockOwner = doc.getUserLock();
            if(lockOwner != null && (!lockOwner.equals(user)) && (!manager.hasAdminPrivileges(user)))
                throw new PermissionDeniedException("Resource is already locked by user " +
                        lockOwner.getName());
            //TODO : start the transaction earlier and register the lock within it ?
            TransactionManager transact = factory.getBrokerPool().getTransactionManager();
            Txn transaction = transact.beginTransaction();            
            doc.setUserLock(null);
            broker.storeXMLResource(transaction, doc);
            transact.commit(transaction);
            return true;

        } catch (Throwable e) {
            handleException(e);
            return false;

        } finally {
            if(doc != null)
                doc.getUpdateLock().release(Lock.WRITE_LOCK);
            factory.getBrokerPool().release(broker);
        }
    }
    
    /**
     * The method <code>summary</code>
     *
     * @param xpath a <code>String</code> value
     * @return a <code>HashMap</code> value
     * @exception Exception if an error occurs
     */
    public HashMap summary(String xpath) throws EXistException, PermissionDeniedException {
        long startTime = System.currentTimeMillis();
        DBBroker broker = null;
        try {
            broker = factory.getBrokerPool().get(user);
            QueryResult qr = doQuery(broker, xpath, null, new HashMap());
            if (qr == null)
                return new HashMap();
            if (qr.hasErrors())
                throw qr.getException();
            HashMap map = new HashMap();
            HashMap doctypes = new HashMap();
            NodeProxy p;
            String docName;
            DocumentType doctype;
            NodeCount counter;
            DoctypeCount doctypeCounter;
            for (SequenceIterator i = qr.result.iterate(); i.hasNext(); ) {
                Item item = i.nextItem();
                if (Type.subTypeOf(item.getType(), Type.NODE)) {
                    NodeValue nv = (NodeValue) item;
                    if (nv.getImplementationType() == NodeValue.PERSISTENT_NODE) {
                        p = (NodeProxy) nv;
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
                }
            }
            HashMap result = new HashMap();
            result.put("queryTime", new Integer((int) (System
                    .currentTimeMillis() - startTime)));
            result.put("hits", new Integer(qr.result.getItemCount()));
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

        } catch(Throwable e) {
            handleException(e);
            return null;

        } finally {
            factory.getBrokerPool().release(broker);
        }
    }
    
    /**
     * The method <code>summary</code>
     *
     * @param resultId an <code>int</code> value
     * @return a <code>HashMap</code> value
     * @exception EXistException if an error occurs
     * @exception XPathException if an error occurs
     */
    public HashMap summary(int resultId) throws EXistException, XPathException {
        QueryResult qr = factory.resultSets.get(resultId);
        if (qr == null)
            throw new EXistException("result set unknown or timed out");
        qr.timestamp = System.currentTimeMillis();
        HashMap result = new HashMap();
        result.put("queryTime", new Integer((int) qr.queryTime));
        if (qr.result == null) {
            result.put("hits", new Integer(0));
            return result;
        }
        DBBroker broker = factory.getBrokerPool().get(user);
        try {
            HashMap map = new HashMap();
            HashMap doctypes = new HashMap();
            NodeProxy p;
            String docName;
            DocumentType doctype;
            NodeCount counter;
            DoctypeCount doctypeCounter;
            for (SequenceIterator i = qr.result.iterate(); i.hasNext(); ) {
                Item item = i.nextItem();
                if (Type.subTypeOf(item.getType(), Type.NODE)) {
                    NodeValue nv = (NodeValue) item;
                    if (nv.getImplementationType() == NodeValue.PERSISTENT_NODE) {
                        p = (NodeProxy) nv;
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
                }
            }
            result.put("hits", new Integer(qr.result.getItemCount()));
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
            factory.getBrokerPool().release(broker);
        }
    }
    
    /**
     * The method <code>getIndexedElements</code>
     *
     * @param collectionName a <code>String</code> value
     * @param inclusive a <code>boolean</code> value
     * @return a <code>Vector</code> value
     * @exception EXistException if an error occurs
     * @exception PermissionDeniedException if an error occurs
     * @exception URISyntaxException if an error occurs
     */
    public Vector getIndexedElements(String collectionName,
            boolean inclusive) throws EXistException, PermissionDeniedException, URISyntaxException {
    	return getIndexedElements(XmldbURI.xmldbUriFor(collectionName),inclusive);
    }    

    /**
     * The method <code>getIndexedElements</code>
     *
     * @param collUri a <code>XmldbURI</code> value
     * @param inclusive a <code>boolean</code> value
     * @return a <code>Vector</code> value
     * @exception EXistException if an error occurs
     * @exception PermissionDeniedException if an error occurs
     */
    private Vector getIndexedElements(XmldbURI collUri,
            boolean inclusive) throws EXistException, PermissionDeniedException {
        DBBroker broker = null;
        Collection collection = null;
        try {
            broker = factory.getBrokerPool().get(user);
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
            factory.getBrokerPool().release(broker);
        }
    }
    
    /**
     * The method <code>scanIndexTerms</code>
     *
     * @param collectionName a <code>String</code> value
     * @param start a <code>String</code> value
     * @param end a <code>String</code> value
     * @param inclusive a <code>boolean</code> value
     * @return a <code>Vector</code> value
     * @exception PermissionDeniedException if an error occurs
     * @exception EXistException if an error occurs
     * @exception URISyntaxException if an error occurs
     */
    public Vector scanIndexTerms(String collectionName,
            String start, String end, boolean inclusive)
            throws PermissionDeniedException, EXistException, URISyntaxException {
    	return scanIndexTerms(XmldbURI.xmldbUriFor(collectionName),start,end,inclusive);
    }    

    /**
     * The method <code>scanIndexTerms</code>
     *
     * @param collUri a <code>XmldbURI</code> value
     * @param start a <code>String</code> value
     * @param end a <code>String</code> value
     * @param inclusive a <code>boolean</code> value
     * @return a <code>Vector</code> value
     * @exception PermissionDeniedException if an error occurs
     * @exception EXistException if an error occurs
     */
    private Vector scanIndexTerms(XmldbURI collUri,
            String start, String end, boolean inclusive)
            throws PermissionDeniedException, EXistException {
        DBBroker broker = null;
        Collection collection = null;
        try {
            broker = factory.getBrokerPool().get(user);
            collection = broker.openCollection(collUri, Lock.READ_LOCK);
            if (collection == null)
                throw new EXistException("collection " + collUri + " not found");
            MutableDocumentSet docs = new DefaultDocumentSet();
            collection.allDocs(broker, docs, inclusive, true);
            NodeSet nodes = docs.docsToNodeSet();
            Vector result = scanIndexTerms(start, end, broker, docs, nodes);
            return result;
        } finally {
            if(collection != null)
                collection.release(Lock.READ_LOCK);
            factory.getBrokerPool().release(broker);
        }
    }
    
    /**
     * The method <code>scanIndexTerms</code>
     *
     * @param xpath a <code>String</code> value
     * @param start a <code>String</code> value
     * @param end a <code>String</code> value
     * @return a <code>Vector</code> value
     * @exception PermissionDeniedException if an error occurs
     * @exception EXistException if an error occurs
     * @exception XPathException if an error occurs
     */
    public Vector scanIndexTerms(String xpath,
            String start, String end)
            throws PermissionDeniedException, EXistException, XPathException {
        DBBroker broker = null;
        try {
            broker = factory.getBrokerPool().get(user);
            XQuery xquery = broker.getXQueryService();
            Sequence nodes = xquery.execute(xpath, null, AccessContext.XMLRPC);
            Vector result = scanIndexTerms(start, end, broker, nodes.getDocumentSet(), nodes.toNodeSet());
            return result;
        } finally {
            factory.getBrokerPool().release(broker);
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
    
    /**
     * The method <code>synchronize</code>
     *
     */
    public void synchronize() {
    }
    
    /**
     * The method <code>getProperties</code>
     *
     * @param parameters a <code>HashMap</code> value
     * @return a <code>Properties</code> value
     */
    private Properties getProperties(HashMap parameters) {
        Properties properties = new Properties();
        for (Iterator i = parameters.entrySet().iterator(); i.hasNext(); ) {
            Map.Entry entry = (Map.Entry) i.next();
            properties.setProperty((String) entry.getKey(), entry.getValue().toString());
        }
        return properties;
    }
    
    /**
     * The class <code>CachedQuery</code>
     *
     */
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
    
    /**
     * The class <code>DoctypeCount</code>
     *
     */
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
    
    /**
     * The class <code>NodeCount</code>
     *
     */
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
    public byte[] getDocumentChunk(String name, int start, int len)
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
    
    /**
     * The method <code>moveOrCopyResource</code>
     *
     * @param documentPath a <code>String</code> value
     * @param destinationPath a <code>String</code> value
     * @param newName a <code>String</code> value
     * @param move a <code>boolean</code> value
     * @return a <code>boolean</code> value
     * @exception EXistException if an error occurs
     * @exception PermissionDeniedException if an error occurs
     * @exception URISyntaxException if an error occurs
     */
    public boolean moveOrCopyResource(String documentPath, String destinationPath,
            String newName, boolean move)
            throws EXistException, PermissionDeniedException, URISyntaxException {
    	return moveOrCopyResource(XmldbURI.xmldbUriFor(documentPath),
    			XmldbURI.xmldbUriFor(destinationPath),XmldbURI.xmldbUriFor(newName),move);
    }    

    /**
     * The method <code>moveOrCopyResource</code>
     *
     * @param docUri a <code>XmldbURI</code> value
     * @param destUri a <code>XmldbURI</code> value
     * @param newName a <code>XmldbURI</code> value
     * @param move a <code>boolean</code> value
     * @return a <code>boolean</code> value
     * @exception EXistException if an error occurs
     * @exception PermissionDeniedException if an error occurs
     */
    private boolean moveOrCopyResource(XmldbURI docUri, XmldbURI destUri,
            XmldbURI newName, boolean move)
            throws EXistException, PermissionDeniedException {
        TransactionManager transact = factory.getBrokerPool().getTransactionManager();
        Txn transaction = transact.beginTransaction();
        DBBroker broker = null;
        Collection collection = null;
        Collection destination = null;
        DocumentImpl doc = null;
        try {
        	//TODO : use  transaction.registerLock(collection.getLock(), Lock.WRITE_LOCK);
            broker = factory.getBrokerPool().get(user);
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
                broker.moveResource(transaction, doc, destination, newName);
            else
                broker.copyResource(transaction, doc, destination, newName);
            transact.commit(transaction);
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
            factory.getBrokerPool().release(broker);
        }
    }
    
    /**
     * The method <code>moveOrCopyCollection</code>
     *
     * @param collectionName a <code>String</code> value
     * @param destinationPath a <code>String</code> value
     * @param newName a <code>String</code> value
     * @param move a <code>boolean</code> value
     * @return a <code>boolean</code> value
     * @exception EXistException if an error occurs
     * @exception PermissionDeniedException if an error occurs
     * @exception URISyntaxException if an error occurs
     */
    public boolean moveOrCopyCollection(String collectionName, String destinationPath,
            String newName, boolean move)
            throws EXistException, PermissionDeniedException, URISyntaxException {
    	return moveOrCopyCollection(XmldbURI.xmldbUriFor(collectionName),
    			XmldbURI.xmldbUriFor(destinationPath),XmldbURI.xmldbUriFor(newName),move);
    }    
    
    /**
     * The method <code>moveOrCopyCollection</code>
     *
     * @param collUri a <code>XmldbURI</code> value
     * @param destUri a <code>XmldbURI</code> value
     * @param newName a <code>XmldbURI</code> value
     * @param move a <code>boolean</code> value
     * @return a <code>boolean</code> value
     * @exception EXistException if an error occurs
     * @exception PermissionDeniedException if an error occurs
     */
    private boolean moveOrCopyCollection(XmldbURI collUri, XmldbURI destUri,
            XmldbURI newName, boolean move)
            throws EXistException, PermissionDeniedException {
        TransactionManager transact = factory.getBrokerPool().getTransactionManager();
        Txn transaction = transact.beginTransaction();
        DBBroker broker = null;
        Collection collection = null;
        Collection destination = null;
        try {
            broker = factory.getBrokerPool().get(user);
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
            factory.getBrokerPool().release(broker);
        }
    }
    
    /**
     * The method <code>reindexCollection</code>
     *
     * @param collectionName a <code>String</code> value
     * @exception Exception if an error occurs
     * @exception PermissionDeniedException if an error occurs
     * @exception URISyntaxException if an error occurs
     */
    public boolean reindexCollection(String collectionName) throws URISyntaxException, EXistException, PermissionDeniedException {
    	reindexCollection(XmldbURI.xmldbUriFor(collectionName));
        return true;
    }    
    
    /**
     * The method <code>reindexCollection</code>
     *
     * @param collUri a <code>XmldbURI</code> value
     * @exception Exception if an error occurs
     * @exception PermissionDeniedException if an error occurs
     */
    private void reindexCollection(XmldbURI collUri) throws EXistException, PermissionDeniedException {
        DBBroker broker = null;
        try {
            broker = factory.getBrokerPool().get(user);
            broker.reindexCollection(collUri);
            LOG.debug("collection " + collUri + " and sub collection reindexed");

        } catch (Throwable e) {
            handleException(e);

        } finally {
            factory.getBrokerPool().release(broker);
        }
    }
    
    /**
     * The method <code>backup</code>
     *
     * @param userbackup a <code>String</code> value
     * @param password a <code>String</code> value
     * @param destcollection a <code>String</code> value
     * @param collection a <code>String</code> value
     * @exception Exception if an error occurs
     * @exception PermissionDeniedException if an error occurs
     */
    public boolean backup(String userbackup, String password,
	String destcollection, String collection) throws EXistException, PermissionDeniedException {
    	try {
    		   Backup backup = new Backup(
    				userbackup,
                    password, 
                    destcollection+"-backup",
                    XmldbURI.xmldbUriFor(XmldbURI.EMBEDDED_SERVER_URI.toString() + collection));
                backup.backup(false, null);

            } catch (Throwable e) {
                handleException(e);
			}
        return true;
    }
    
    /**
     *   Validate if specified document is Valid.
     *
     * @param documentPath   Path to XML document in database
     * @throws java.lang.Exception  Generic exception
     * @throws PermissionDeniedException  User is not allowed to perform action.
     * @return TRUE if document is valid, FALSE if not or errors or.....
     */
    public boolean isValid(String documentPath)
            throws PermissionDeniedException, URISyntaxException, EXistException {
    	return isValid(XmldbURI.xmldbUriFor(documentPath));
    }   
    
    /**
     * The method <code>isValid</code>
     *
     * @param docUri a <code>XmldbURI</code> value
     * @return a <code>boolean</code> value
     * @exception PermissionDeniedException if an error occurs
     * @exception Exception if an error occurs
     */
    private boolean isValid(XmldbURI docUri) throws EXistException, PermissionDeniedException {
        boolean isValid=false;

        try {
            // Setup validator
            Validator validator = new Validator(factory.getBrokerPool());
            
            // Get inputstream
            // TODO DWES reconsider
            InputStream is = new EmbeddedInputStream( new XmldbURL(docUri) );
            
            // Perform validation
            ValidationReport report = validator.validate(is); 
            
            // Return validation result
            isValid = report.isValid();
            
        } catch (Throwable e) {
            handleException(e);
            return false;
        }
        
        return isValid;
    }
    
    /**
     * The method <code>getDocType</code>
     *
     * @param documentPath a <code>String</code> value
     * @return a <code>Vector</code> value
     * @exception PermissionDeniedException if an error occurs
     * @exception EXistException if an error occurs
     * @exception URISyntaxException if an error occurs
     */
    public Vector getDocType(String documentPath)
    throws PermissionDeniedException, EXistException, URISyntaxException {
    	return getDocType(XmldbURI.xmldbUriFor(documentPath));
    }    
    
    /**
     * The method <code>getDocType</code>
     *
     * @param docUri a <code>XmldbURI</code> value
     * @return a <code>Vector</code> value
     * @exception PermissionDeniedException if an error occurs
     * @exception EXistException if an error occurs
     */
    private Vector getDocType(XmldbURI docUri)
    throws PermissionDeniedException, EXistException {
        DBBroker broker = null;
        DocumentImpl doc = null;
        
        try {
            broker = factory.getBrokerPool().get(user);
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
            factory.getBrokerPool().release(broker);
        }
    }
    

    /**
     * The method <code>setDocType</code>
     *
     * @param documentPath a <code>String</code> value
     * @param doctypename a <code>String</code> value
     * @param publicid a <code>String</code> value
     * @param systemid a <code>String</code> value
     * @return a <code>boolean</code> value
     * @exception Exception if an error occurs
     * @exception URISyntaxException if an error occurs
     */
    public boolean setDocType(String documentPath, String doctypename, String publicid, String systemid) throws
            URISyntaxException, EXistException, PermissionDeniedException {
    	return setDocType(XmldbURI.xmldbUriFor(documentPath),doctypename, publicid, systemid);
    }    

    /**
     * The method <code>setDocType</code>
     *
     * @param docUri a <code>XmldbURI</code> value
     * @param doctypename a <code>String</code> value
     * @param publicid a <code>String</code> value
     * @param systemid a <code>String</code> value
     * @return a <code>boolean</code> value
     * @exception Exception if an error occurs
     */
    private boolean setDocType(XmldbURI docUri, String doctypename, String publicid, String systemid) throws EXistException, PermissionDeniedException {
        DBBroker broker = null;
        DocumentImpl doc = null;
        DocumentType result = null;
        TransactionManager transact = factory.getBrokerPool().getTransactionManager();
        Txn transaction = transact.beginTransaction();
        try {
            broker = factory.getBrokerPool().get(user);
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

        } catch (Throwable e) {
            transact.abort(transaction);
            handleException(e);
            return false;

        } finally {
            if(doc != null)
                doc.getUpdateLock().release(Lock.WRITE_LOCK);
            factory.getBrokerPool().release(broker);
        }
    }

    public boolean copyResource(String docPath, String destinationPath, String newName) throws EXistException, PermissionDeniedException, URISyntaxException {
        return moveOrCopyResource(docPath, destinationPath, newName, false);
    }

    public boolean copyCollection(String collectionPath, String destinationPath, String newName) throws EXistException, PermissionDeniedException, URISyntaxException {
        return moveOrCopyCollection(collectionPath, destinationPath, newName, false);
    }

    public boolean moveResource(String docPath, String destinationPath, String newName) throws EXistException, PermissionDeniedException, URISyntaxException {
        return moveOrCopyResource(docPath, destinationPath, newName, true);
    }

    public boolean moveCollection(String collectionPath, String destinationPath, String newName) throws EXistException, PermissionDeniedException, URISyntaxException {
        return moveOrCopyCollection(collectionPath, destinationPath, newName, true);
    }

    public List getDocumentChunk(String name, HashMap parameters) throws EXistException, PermissionDeniedException, IOException {
        List result = new ArrayList(2);
        File file;
        file = File.createTempFile("rpc", ".xml");
        file.deleteOnExit();
        FileOutputStream os = new FileOutputStream(file.getAbsolutePath(), true);
        os.write(getDocument(name, parameters));
        os.close();
        result.add(file.getName());
        result.add(Long.toString(file.length()));
        return result;
    }

    public boolean copyCollection(String name, String namedest) throws PermissionDeniedException, EXistException {
        try {
            createCollection(namedest);

            HashMap parametri = new HashMap();
            parametri.put(OutputKeys.INDENT, "no");
            parametri.put(EXistOutputKeys.EXPAND_XINCLUDES, "no");
            parametri.put(OutputKeys.ENCODING, "UTF-8");

            HashMap lista = getCollectionDesc(name);
            Object[] collezioni = (Object[]) lista.get("collections");
            Object[] documents = (Object[]) lista.get("documents");

            //ricrea le directory
            String nome;
            for (int i = 0; i < collezioni.length; i++) {
                nome = collezioni[i].toString();
                createCollection(namedest + "/" + nome);
                copyCollection(name + "/" + nome, namedest + "/" + nome);
            }

            //Copy i file
            HashMap hash;
            int p, dsize = documents.length;
            for (int i = 0; i < dsize; i++) {
                hash = (HashMap) documents[i];
                nome = (String) hash.get("name");
                //TODO : use dedicated function in XmldbURI
                if ((p = nome.lastIndexOf("/")) != Constants.STRING_NOT_FOUND)
                    nome = nome.substring(p + 1);

                byte[] xml = getDocument(name + "/" + nome, parametri);
                parse(xml, namedest + "/" + nome);
            }

            return true;

        } catch (Throwable e) {
            handleException(e);
            return false;
        }
    }

    public int xupdateResource(String resource, byte[] xupdate) throws PermissionDeniedException, EXistException, SAXException {
        return xupdateResource(resource, xupdate, "UTF-8");
    }

    public boolean setPermissions(String resource, int permissions) throws EXistException, PermissionDeniedException, URISyntaxException {
        return setPermissions(resource, null, null, permissions);
    }

    public boolean setPermissions(String resource, String permissions) throws EXistException, PermissionDeniedException, URISyntaxException {
        return setPermissions(resource, null, null, permissions);
    }

    public boolean setUser(String name, String passwd, String digestPassword, Vector groups) throws EXistException, PermissionDeniedException {
        return setUser(name, passwd, digestPassword,groups, null);
    }

    public HashMap querySummary(int resultId) throws EXistException, PermissionDeniedException, XPathException {
        return summary(resultId);
    }

    public int executeQuery(byte[] xpath, HashMap parameters) throws EXistException, PermissionDeniedException {
        return executeQuery(xpath, null, parameters);
    }

    public boolean storeBinary(byte[] data, String docName, String mimeType, boolean replace, Date created, Date modified) throws EXistException, PermissionDeniedException, URISyntaxException {
        return storeBinary(data, docName, mimeType, replace ? 1 : 0, created, modified);
    }

    public boolean storeBinary(byte[] data, String docName, String mimeType, boolean replace) throws EXistException, PermissionDeniedException, URISyntaxException {
        return storeBinary(data, docName, mimeType, replace ? 1 : 0, null, null);
    }

    public boolean parseLocalExt(String localFile, String docName, boolean replace, String mimeType, boolean treatAsXML, Date created, Date modified) throws EXistException, PermissionDeniedException, SAXException, URISyntaxException {
        return parseLocalExt(localFile, docName, replace ? 1 : 0, mimeType, treatAsXML ? 1 : 0, created, modified);
    }

    public boolean parseLocal(String localFile, String docName, boolean replace, String mimeType, Date created, Date modified) throws EXistException, PermissionDeniedException, SAXException, URISyntaxException {
        return parseLocal(localFile, docName, replace ? 1 : 0, mimeType, created, modified);
    }

    public boolean parseLocalExt(String localFile, String docName, boolean replace, String mimeType, boolean treatAsXML) throws EXistException, PermissionDeniedException, SAXException, URISyntaxException {
        return parseLocalExt(localFile, docName, replace ? 1 : 0, mimeType, treatAsXML ? 1 : 0, null, null);
    }

    public boolean parseLocal(String localFile, String docName, boolean replace, String mimeType) throws EXistException, PermissionDeniedException, SAXException, URISyntaxException {
        return parseLocal(localFile, docName, replace ? 1 : 0, mimeType, null, null);
    }

    public String uploadCompressed(String file, byte[] data, int length) throws EXistException, PermissionDeniedException, IOException {
        return upload(data, length, file, true);
    }

    public String uploadCompressed(byte[] data, int length) throws EXistException, PermissionDeniedException, IOException {
        return upload(data, length, null, true);
    }

    public String upload(String file, byte[] chunk, int length) throws EXistException, PermissionDeniedException, IOException {
        return upload(chunk, length, file, false);
    }

    public String upload(byte[] chunk, int length) throws EXistException, PermissionDeniedException, IOException {
        return upload(chunk, length, null, false);
    }

    public boolean parse(String xml, String docName) throws EXistException, PermissionDeniedException, URISyntaxException {
        try {
            return parse(xml.getBytes("UTF-8"), docName, 0);
            
        } catch (UnsupportedEncodingException e) {
            handleException(e);
            return false;
        }
    }

    public boolean parse(String xml, String docName, int overwrite) throws EXistException, PermissionDeniedException, URISyntaxException {
        try {
            return parse(xml.getBytes("UTF-8"), docName, overwrite);

        } catch (UnsupportedEncodingException e) {
            handleException(e);
            return false;
        }
    }

    public boolean parse(byte[] xmlData, String docName) throws EXistException, PermissionDeniedException, URISyntaxException {
        return parse(xmlData, docName, 0);
    }

    /** @deprecated Use XmldbURI version instead */
    public HashMap querySummary(String xquery) throws EXistException, PermissionDeniedException {
        return summary(xquery);
    }

    /** @deprecated Use XmldbURI version instead */
    public byte[] query(byte[] xquery, int howmany, int start, HashMap parameters) throws EXistException, PermissionDeniedException {
        try {
            String result = query(new String(xquery, "UTF-8"), howmany, start, parameters);
            return result.getBytes("UTF-8");

        } catch (UnsupportedEncodingException e) {
            handleException(e);
            return null;
        }
    }

    public HashMap queryP(byte[] xpath, String docName, String s_id, HashMap parameters) throws EXistException, PermissionDeniedException, URISyntaxException {
        try {
            return queryP(new String(xpath, "UTF-8"), docName, s_id, parameters);
        } catch (UnsupportedEncodingException e) {
            handleException(e);
            return null;
        }
    }

    public HashMap queryP(byte[] xpath, HashMap parameters) throws EXistException, PermissionDeniedException {
        try {
            return queryP(new String(xpath, "UTF-8"), (XmldbURI) null, null, parameters);
            
        } catch (UnsupportedEncodingException e) {
            handleException(e);
            return null;
        }
    }

    public HashMap compile(byte[] xquery, HashMap parameters) throws EXistException, PermissionDeniedException {
        try {
            return compile(new String(xquery, "UTF-8"), parameters);
            
        } catch (UnsupportedEncodingException e) {
            handleException(e);
            return null;
        }
    }

    public byte[] retrieve(String doc, String id) throws EXistException, PermissionDeniedException {
        return retrieve(doc, id, null);
    }

    public String getDocumentAsString(String name, int prettyPrint, String stylesheet) throws EXistException, PermissionDeniedException {
        HashMap parametri = new HashMap();

        if (prettyPrint > 0) {
            parametri.put(OutputKeys.INDENT, "yes");
        } else {
            parametri.put(OutputKeys.INDENT, "no");
        }

        if (stylesheet != null) {
            parametri.put(EXistOutputKeys.STYLESHEET, stylesheet);
        }
        return getDocumentAsString(name, parametri);
    }

    public String getDocumentAsString(String name, int prettyPrint) throws EXistException, PermissionDeniedException {
        return getDocumentAsString(name, prettyPrint, null);
    }

    public byte[] getDocument(String name, String encoding, int prettyPrint, String stylesheet) throws EXistException, PermissionDeniedException {
        HashMap parametri = new HashMap();

        if (prettyPrint > 0) {
            parametri.put(OutputKeys.INDENT, "yes");
        } else {
            parametri.put(OutputKeys.INDENT, "no");
        }

        if (stylesheet != null) {
            parametri.put(EXistOutputKeys.STYLESHEET, stylesheet);
        }

        parametri.put(OutputKeys.ENCODING, encoding);

        //String xml = con.getDocument(user, name, (prettyPrint > 0),
        // encoding, stylesheet);
        byte[] xml = getDocument(name, parametri);
        if (xml == null)
            throw new EXistException("document " + name + " not found!");
        return xml;
    }

    public byte[] getDocument(String name, String encoding, int prettyPrint) throws EXistException, PermissionDeniedException {
        return getDocument(name, encoding, prettyPrint, null);
    }

    public boolean shutdown() throws PermissionDeniedException {
        return shutdown(0);
    }

    public boolean shutdown(long delay) throws PermissionDeniedException {
        if (!user.hasDbaRole())
            throw new PermissionDeniedException("not allowed to shut down" + "the database");
        if (delay > 0) {
            TimerTask task = new TimerTask() {
                public void run() {
                    try {
                        BrokerPool.stop();
                    } catch (EXistException e) {
                        LOG.warn("shutdown failed", e);
                    }
                }
            };
            Timer timer = new Timer();
            timer.schedule(task, delay);
        } else {
            try {
                BrokerPool.stop();
            } catch (EXistException e) {
                LOG.warn("shutdown failed", e);
                return false;
            }
        }
        return true;
    }

    public boolean enterServiceMode() throws PermissionDeniedException, EXistException {
        BrokerPool brokerPool = factory.getBrokerPool();
        brokerPool.enterServiceMode(user);
        return true;
    }

    public void exitServiceMode() throws PermissionDeniedException, EXistException {
        BrokerPool brokerPool = factory.getBrokerPool();
        brokerPool.exitServiceMode(user);
    }
}
