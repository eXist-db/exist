/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 The eXist Project
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
 * $Id$
 */

package org.exist.validation.internal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SecurityManager;
import org.exist.security.xacml.AccessContext;
import org.exist.source.ClassLoaderSource;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.Constants;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 *  Helper class for accessing grammars.
 * @author Dannes Wessels
 */
public class DatabaseResources {
    
    public static String FIND_XSD 
           = "org/exist/validation/internal/query/find_schema.xq";
    
    public static String FIND_PUBLICID_IN_CATALOGS
           = "org/exist/validation/internal/query/find_publicid_in_catalogs.xq";
    
    public static String FIND_XSD_IN_CATALOG 
           = "org/exist/validation/internal/query/find_xsd_in_catalog.xq";
    
    public static String FIND_DTD_IN_CATALOG 
           = "org/exist/validation/internal/query/find_dtd_in_catalog.xq";
    
    public static String PUBLICID 
            = "publicId";
    
    public static String TARGETNAMESPACE 
            = "targetNamespace";
    
    public static String CATALOG 
            = "catalog";
    
    /** Local reference to database  */
    private BrokerPool brokerPool = null;
    
    /** Local logger */
    private final static Logger logger = Logger.getLogger(DatabaseResources.class);
    
    
    /**
     *  Convert sequence into list of strings. Java5 would make
     * this method more safe to use.
     *
     * @param   sequence  Result of query.
     * @return  List containing String objects.
     */
    public List getAllResults(Sequence sequence){
        List result = new ArrayList();
        
        try {
            SequenceIterator i = sequence.iterate();
            
            while(i.hasNext()){
                String path =  i.nextItem().getStringValue();
                result.add(path);
            }
            
        } catch (XPathException ex) {
            logger.error("xQuery issue.", ex);
            result=null;
        }
        
        return result;
    }
    
    /**
     *  Get first entry of sequence as String. Java5 would make
     * this method more safe to use.
     *
     * @param   sequence  Result of query.
     * @return  String containing representation of 1st entry of sequence.
     */
    public String getFirstResult(Sequence sequence){
        String result = null;
        
        try {
            SequenceIterator i = sequence.iterate();
            if(i.hasNext()){
                result= i.nextItem().getStringValue();
                
                logger.debug("Single query result: '"+result+"'.");
                
            } else {
                logger.debug("No query result.");
            }
            
        } catch (XPathException ex) {
            logger.error("xQuery issue ", ex);
        }
        
        return result;
    }
    
    
    /**
     *  Execute query with supplied parameters.
     *
     * namespace,  publicId,  catalogPath, 
     *
     * @param collection    Collection in which query is executed.
     * @param params        Map of parameters used in cquery
     * @param queryPath     Path to xquery in classpath
     * @return              Result of xQuery
     */
    public Sequence executeQuery(XmldbURI collection, Map params, String queryPath){
        
        String namespace = (String) params.get(TARGETNAMESPACE);
        String publicId = (String) params.get(PUBLICID);
        String catalogPath = (String) params.get(CATALOG);
        
        logger.debug("collection=" + collection + " namespace=" + namespace
                + " publicId="+publicId);
        
        DBBroker broker = null;
        Sequence result= null;
        try {
            broker = brokerPool.get(SecurityManager.SYSTEM_USER);
        } catch (EXistException ex){
            logger.error("Error getting DBBroker", ex);
        }
        
        CompiledXQuery compiled =null;
        XQuery xquery = broker.getXQueryService();
        XQueryContext context = xquery.newContext(AccessContext.INTERNAL_PREFIX_LOOKUP);
        
        
        try {
            if(collection!=null){
                context.declareVariable("collection", collection.getCollectionPath());
            }
            
            if(namespace!=null){
                context.declareVariable(TARGETNAMESPACE, namespace);
            }
            
            if(publicId!=null){
                context.declareVariable(PUBLICID, publicId);
            }
            
            if(catalogPath!=null){
                context.declareVariable(CATALOG, catalogPath);
            }
            
            compiled = xquery.compile(context, new ClassLoaderSource(queryPath) );
            
        } catch (IOException ex) {
            logger.error(ex);
        } catch (XPathException ex) {
            logger.error(ex);
        }
        
        try {
            result = xquery.execute(compiled, null);
        } catch (XPathException ex) {
            logger.error("Problem executing xquery", ex);
        } finally{
            if(brokerPool!=null){
                brokerPool.release(broker);
            }
        }
        return result;
    }
    
    /**
     *  Find document path of XSD describing a namespace.
     *
     * @param collection    Start point for search, e.g. '/db'.
     * @param namespace     Namespace that needs to be found.
     * @return              Document path (e.g. '/db/foo/bar.xsd') if found,
     *                      null if namespace could not be found.
     */
    public String getSchemaPath(XmldbURI collection, String namespace){
        
        logger.debug("Find schema with namespace '"+namespace+"' in '"+collection+"'.");
        
        Map params = new HashMap();
        params.put(TARGETNAMESPACE, namespace);
        
        Sequence result = executeQuery(collection, params, FIND_XSD );
        
        return getFirstResult(result);
    }
    
    /**
     *  Find document catalogPath of DTD describing a publicId.
     *
     * @param collection    Start point for search, e.g. '/db'.
     * @param publicId      PublicID that needs to be found.
     * @return Document catalogPath (e.g. '/db/foo/bar.dtd') if found,
     *                      null if publicID could not be found.
     */
    public String getDtdPath(XmldbURI collection, String publicId){
        
        logger.debug("Find DTD with publicID '"+publicId+"' in '"
                +collection.getCollectionPath()+"'.");
        
        Map params = new HashMap();
        params.put(PUBLICID, publicId);
        
        Sequence result = executeQuery(collection, params, FIND_PUBLICID_IN_CATALOGS );
        
        String dtdPath=null;
        
        String catalogPath = getFirstResult(result);
        
        // Get from selected catalog file the publicId
        if(catalogPath !=null){
            
            XmldbURI col=null;
            try {
                col = XmldbURI.xmldbUriFor("xmldb:exist://" + getCollectionPath(catalogPath));
            } catch (URISyntaxException ex) {
                ex.printStackTrace();
            }
            
            String docName = getDocumentName(catalogPath);
            
            dtdPath = getDtdPathFromCatalog(col, docName, publicId);
        }
        
        return dtdPath;
    }
    
    /**
     *  Get document from database.
     *
     * @param documentPath  Path to the resource.
     * @return              Byte array of resource, null if not found.
     */
    //TODO: use XmldbURI
    public byte[] getResource(String documentPath){
        
        XmldbURI documentURI;
        try{
        	documentURI = XmldbURI.xmldbUriFor(documentPath);
        } catch(URISyntaxException e) {
        	throw new IllegalArgumentException("Invalid URI: "+e.getMessage());
        }

        MimeType mime = MimeTable.getInstance().getContentTypeFor(documentURI.lastSegment());
        if (mime == null){
            mime = MimeType.BINARY_TYPE;
        }
        
        byte[] data = null;
        
        logger.debug("Get resource '"+documentURI);
        
        DBBroker broker = null;
        try {
            broker = brokerPool.get(SecurityManager.SYSTEM_USER);
            
            if(mime.isXMLType()){
                DocumentImpl doc = broker
                        .getXMLResource(documentURI, Lock.READ_LOCK);
                
                // if document is not present, null is returned
                if(doc == null){
                    logger.error("Xml document '"
                            + documentURI + " does not exist.");
                } else {
                    Serializer serializer = broker.getSerializer();
                    serializer.reset();
                    data = serializer.serialize(doc).getBytes();
                    doc.getUpdateLock().release(Lock.READ_LOCK);
                }
                
            } else {
                BinaryDocument binDoc = (BinaryDocument) broker
                        .getXMLResource(documentURI, Lock.READ_LOCK);
                
                // if document is not present, null is returned
                if(binDoc == null){
                    logger.error("Binary document '"
                            + documentURI + " does not exist.");
                } else {
                    data = broker.getBinaryResource(binDoc);
                    binDoc.getUpdateLock().release(Lock.READ_LOCK);
                }
                
            }
        } catch (PermissionDeniedException ex){
            logger.error("Error opening document", ex);
        } catch (SAXException ex){
            logger.error("Error serializing document", ex);
        }  catch (EXistException ex){
            logger.error(ex);
        } finally {
            if(brokerPool!=null){
                brokerPool.release(broker);
            }
        }
        
        return data;
    }
    
    //TODO: use XmldbURI
    public boolean insertResource(String documentPath, byte[] grammar){
        boolean insertIsSuccesfull = false;
        
        XmldbURI documentURI;
        try{
        	documentURI = XmldbURI.xmldbUriFor(documentPath);
        } catch(URISyntaxException e) {
        	throw new IllegalArgumentException("Invalid URI: "+e.getMessage());
        }

        DBBroker broker = null;
        TransactionManager transact = null;
        Txn transaction = null;
        try {
            MimeType mime = MimeTable.getInstance().getContentTypeFor(documentURI.lastSegment());
            if (mime == null){
                mime = MimeType.BINARY_TYPE;
            }
            
            broker = brokerPool.get(SecurityManager.SYSTEM_USER);
            
            transact = brokerPool.getTransactionManager();
            transaction = transact.beginTransaction();
            
            Collection collection = broker
            	//TODO : resolve URI against ".."
                    .getOrCreateCollection(transaction, documentURI.removeLastSegment());
            
            broker.saveCollection(transaction, collection);
            
            if(mime.isXMLType()){
                
                IndexInfo info = collection.validateXMLResource( transaction, broker, documentURI.lastSegment() , new InputSource( new ByteArrayInputStream(grammar) ) );
                collection.store(transaction, broker, info, new InputSource( new ByteArrayInputStream(grammar) ), false);
                
            } else {
                // TODO : call mime-type stuff for good mimetypes
                collection.addBinaryResource(transaction, broker,
                        		documentURI.lastSegment(), grammar, mime.getName() );
            }
            transact.commit(transaction);
            
            insertIsSuccesfull=true;
            
        } catch (Exception ex){
        	transact.abort(transaction);
            ex.printStackTrace();
            logger.error(ex);
            
        } finally {
            if(brokerPool!=null){
                brokerPool.release(broker);
            }
        }
        return insertIsSuccesfull;
        
    }
    
    
    
    /**
     * Creates a new instance of DatabaseResources.
     *
     * @param pool  Instance shared broker pool.
     */
    public DatabaseResources(BrokerPool pool) {
        
        logger.info("Initializing DatabaseResources");
        this.brokerPool = pool;
        
    }
    
    /**
     *  Get document name from path.
     *
     *  /db/foo/bar/doc.xml gives doc.xml
     *  xmldb:exist:///db/fo/bar/doc.xml gives doc.xml
     *
     * @param path  The Path
     * @return  Document name.
     */
    static public String getDocumentName(String path){
        
        String docName = null;
        
        int separatorPos = path.lastIndexOf("/");
        if(separatorPos == Constants.STRING_NOT_FOUND){
            docName=path;
            
        } else {
            docName=path.substring(separatorPos + 1);
        }
        
        return docName;
    }
    
    /**
     *  Get collection pathname from path.
     *
     *  /db/foo/bar/doc.xml gives /db/foo/bar
     *  xmldb:exist:///db/fo/bar/doc.xml gives xmldb:exist:///db/fo/bar
     *
     * @param path  The Path
     * @return  Collection path name, "" if none available (doc.xml)
     */
    static public String getCollectionPath(String path){
        
        String pathName = null;
        
        int separatorPos = path.lastIndexOf("/");
        if(separatorPos == Constants.STRING_NOT_FOUND){
            // no path
            pathName="";
            
        } else {
            pathName=path.substring(0, separatorPos);
        }
        
        return pathName;
        
    }
    
    /**
     *  Get schema path information from catalog.
     *
     * @param collection Collection containing the catalog file
     * @param docName    Catalog filename
     * @param namespace  This namespace needs to be resolved
     * @return           Path to schema, or null if not found.
     */
    public String getSchemaPathFromCatalog(XmldbURI collection, String docName,
            String namespace) {
        
        Map params = new HashMap();
        params.put(TARGETNAMESPACE, namespace);
        params.put(CATALOG, collection.getCollectionPath()+"/"+docName);
        
        Sequence result = executeQuery(collection, params, FIND_XSD_IN_CATALOG);
        
        
        String path = getFirstResult(result);
        
        if(path!= null && !path.startsWith("/")){
            path = collection.getCollectionPath()+"/"+ path;
        }
        
        return path;
    }
    
    
    /**
     *  Get DTD path information from catalog.
     *
     * @param collection Collection containing the catalog file
     * @param docName    Catalog filename
     * @param publicId   This publicId needs to be resolved
     * @return           Path to DTD, or null if not found.
     */
    public String getDtdPathFromCatalog(XmldbURI collection, String docName,
            String publicId) {
        
        Map params = new HashMap();
        params.put(PUBLICID, publicId);
        params.put(CATALOG, collection.getCollectionPath()+"/"+docName);
        
        Sequence result = executeQuery(collection, params, FIND_DTD_IN_CATALOG);
        
        String path = getFirstResult(result);
        
        if(path!= null && !path.startsWith("/")){
            path = collection.getCollectionPath()+"/"+ path;
        }
        
        return path;
    }
    
}
