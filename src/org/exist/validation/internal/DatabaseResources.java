/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 *  $Id$
 */

package org.exist.validation.internal;

import java.io.ByteArrayInputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SecurityManager;
import org.exist.security.xacml.AccessContext;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.Constants;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;



/**
 *  Helper class for accessing grammars.
 * @author Dannes Wessels
 */
public class DatabaseResources {
    
    /** Local reference to database  */
    private BrokerPool brokerPool = null;
    
    /** Local logger */
    private final static Logger logger = Logger.getLogger(DatabaseResources.class);
    
    /** Path to grammar in database  */
    public String GRAMMARBASE = DBBroker.SYSTEM_COLLECTION +"/grammar";
    public String XSDBASE = DBBroker.ROOT_COLLECTION; // TODO check is this ok
    public String DTDBASE = GRAMMARBASE + "/dtd";
    public String DTDCATALOG = DTDBASE + "/catalog.xml";
    
    public static String NOGRAMMAR = "NONE";
    
//    // TODO remove
//    public static int GRAMMAR_UNKNOWN = 0;
    public static int GRAMMAR_XSD = 1;
    public static int GRAMMAR_DTD = 2;
    
    public static final String OASISCATALOGURN
            ="urn:oasis:names:tc:entity:xmlns:xml:catalog";
    
    public static final String FINDSCHEMA
            ="for $schema in collection('COLLECTION')/xs:schema"
            +"[@targetNamespace = 'TARGET'] return document-uri($schema)";
    
    public static final String FINDCATALOG
            ="declare namespace catalogns='"+OASISCATALOGURN+"';"
            +"for $catalog in collection('COLLECTION')/catalogns:catalog "
            +"return document-uri($catalog)";
    
    public static final String FINDDTD
            ="let $docs := for $doc in collection($collection) "
            +"return document-uri($doc) for $doc in $docs "
            +"where ends-with($doc, '.dtd') return $doc";
    
    public static final String FINDXSDINCATALOG
            ="declare namespace ctlg='"+OASISCATALOGURN+"';"
            +"for $schema in fn:document('CATALOGFILE')/ctlg:catalog"
            +"/ctlg:uri[@name = 'NAMESPACE']/@uri return $schema";
    
    public static final String FINDDTDINCATALOG
            ="declare namespace ctlg='"+OASISCATALOGURN+"';"
            +"for $dtd in fn:document('CATALOGFILE')/ctlg:catalog"
            +"/ctlg:public[@publicId = 'PUBLICID']/@uri return $dtd";
    
    public static final String FINDPUBLICIDINCATALOGS
            ="declare namespace ctlg='"+OASISCATALOGURN+"';"
            +"for $dtd in collection('COLLECTION')/ctlg:catalog"
            +"/ctlg:public[@publicId = 'PUBLICID']/@uri "
            +"return document-uri($dtd)";
    
    /**
     *  Execute xquery.
     *
     * @param query  The xQuery
     * @return  Sequence when results are available, null when errors occur.
     */
    private Sequence executeQuery(String query) {
        
        DBBroker broker = null;
        Sequence result= null;
        
        try {
            broker = brokerPool.get(SecurityManager.SYSTEM_USER);
        } catch (EXistException ex){
            logger.error("Error getting DBBroker", ex);
        }
        
        try {
            result = broker.getXQueryService().execute(query, null, AccessContext.VALIDATION_INTERNAL);
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
     *  Execute xquery, return single result.
     *
     * @param   query  The xQuery
     * @return  String When a result is available, null when an error occured.
     */
    private String executeQuerySingleResult(String xquery){
        
        String result = null;
        
        try {
            // execute query
            Sequence sequence = executeQuery(xquery);
            
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
     *  Execute xquery, return multiple result.
     *
     * @param   query  The xQuery
     * @return  List of Strings when a result is available, null when an
     *          error occured.
     */
    private List executeQueryListResult(String xquery){
        
        List result = new ArrayList();
        
        try {
            // execute query
            Sequence sequence = executeQuery(xquery);
            
            SequenceIterator i = sequence.iterate();
            
            logger.debug("Query yielded "+sequence.getLength()+" hits.");
            
            while(i.hasNext()){
                String path =  i.nextItem().getStringValue();
                result.add(path);
            }
            
        } catch (XPathException ex) {
            logger.error("xQuery issue.", ex);
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
        String path=null;
        
        // Fill parameters for query
        String xquery = FINDSCHEMA.replaceAll("COLLECTION",collection.getCollectionPath()).replaceAll("TARGET",namespace);
        
        return executeQuerySingleResult(xquery);
    }
    
    /**
     *  Find catalogs in database recursively.
     *
     * @param collection  Start point for search, e.g. /db
     * @return  List of document paths (strings), e.g. /db/foo/bar/catalog.xml.
     */
    public List getCatalogs(String collection){
        
        logger.debug("Find catalogs with namespace '"+OASISCATALOGURN+"' in '"+collection+"'.");
        
        // Fill parameters for query
        String xquery = FINDCATALOG.replaceAll("COLLECTION",collection);
        
        return executeQueryListResult(xquery);
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
        
        logger.debug("Find DTD with publicID '"+publicId+"' in '"+collection.getCollectionPath()+"'.");
        String dtdPath=null;
        
        // Find all catalogs containing publicId
        String xquery = FINDPUBLICIDINCATALOGS
                .replaceAll("COLLECTION",collection.getCollectionPath())
                .replaceAll("PUBLICID", publicId);
        
        String catalogPath = executeQuerySingleResult(xquery);
        
        // Get from selected catalog file the publicId
        if(catalogPath !=null){
            
            XmldbURI col=null;
            try {
                col = new XmldbURI("xmldb:exist://" + getCollectionPath(catalogPath));
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
     * @param isBinary      Indicate wether resource is binary.
     * @param documentPath  Path to the resource.
     * @return              Byte array of resource, null if not found.
     */
    public byte[] getGrammar(boolean isBinary, String documentPath){
        
        byte[] data = null;
        
        logger.debug("Get resource '"+documentPath + "' binary="+ isBinary);
        
        DBBroker broker = null;
        try {
            broker = brokerPool.get(SecurityManager.SYSTEM_USER);
            
            if(isBinary){
                BinaryDocument binDoc = (BinaryDocument) broker
                        .getXMLResource(documentPath, Lock.READ_LOCK);
                
                // if document is not present, null is returned
                if(binDoc == null){
                    logger.error("Binary document '"
                            + documentPath + " does not exist.");
                } else {
                    data = broker.getBinaryResource(binDoc);
                    binDoc.getUpdateLock().release(Lock.READ_LOCK);
                }
                
            } else {
                
                DocumentImpl doc = broker
                                    .getXMLResource(documentPath, Lock.READ_LOCK);
                
                // if document is not present, null is returned
                if(doc == null){
                    logger.error("Xml document '"
                                + documentPath + " does not exist.");
                } else {
                    Serializer serializer = broker.getSerializer();
                    serializer.reset();
                    data = serializer.serialize(doc).getBytes();
                    doc.getUpdateLock().release(Lock.READ_LOCK);   
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
    
    
    /**
     *  Inser document to database. Not well tested yet.
     *
     * @param grammar      ByteArray containing file.
     * @param isBinary     Indicate wether resource is binary.
     * @param documentPath Path to the resource.
     * @return             TRUE if successfull, FALSE if not.
     */
    public boolean insertGrammar(boolean isBinary, String documentPath, byte[] grammar){
        
        boolean insertIsSuccesfull = false;
        
        String collectionName = DatabaseResources.getCollectionPath(documentPath);
        String documentName = DatabaseResources.getDocumentName(documentPath);
        
        DBBroker broker = null;
        try {
            
            broker = brokerPool.get(SecurityManager.SYSTEM_USER);
            
            TransactionManager transact = brokerPool.getTransactionManager();
            Txn transaction = transact.beginTransaction();
            
            Collection collection = broker
                    .getOrCreateCollection(transaction, collectionName);
            
            broker.saveCollection(transaction, collection);
            
            if(isBinary){
                
                // TODO : call mime-type stuff for goof mimetypes
                BinaryDocument doc =
                        collection.addBinaryResource(transaction, broker,
                        documentName, grammar, "text/text");
                
            } else {
                IndexInfo info = collection.validateXMLResource( transaction, broker, documentName , new InputSource( new ByteArrayInputStream(grammar) ) );
                collection.store(transaction, broker, info, new InputSource( new ByteArrayInputStream(grammar) ), false);
            }
            transact.commit(transaction);
            
            insertIsSuccesfull=true;
            
        } catch (Exception ex){
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
    
//    /**
//     * @deprecated Get rid of this code.
//     */
//    public boolean hasGrammar(int type, String id){
//        return !getGrammarPath(type, id).equalsIgnoreCase("NONE");
//    }
    
//
//    /**
//     * @deprecated Get rid of this code.
//     */
//    public String getGrammarPath(int type, String id){
//
//        logger.info("Get path of '"+id+"'");
//
//        String result="EMPTY";
//        String query = getGrammarQuery(type, id);
//
//        DBBroker broker = null;
//        try {
//            broker = brokerPool.get(SecurityManager.SYSTEM_USER);
//        } catch (EXistException ex){
//            logger.error("Error getting DBBroker", ex);
//        }
//
//        XQuery xquery = broker.getXQueryService();
//        try{
//            Sequence seq = xquery.execute(query, null);
//
//            SequenceIterator i = seq.iterate();
//            if(i.hasNext()){
//                result= i.nextItem().getStringValue();
//
//            } else {
//                logger.debug("No xQuery result");
//            }
//
//        } catch (XPathException ex){
//            logger.error("XSD xQuery error: "+ ex.getMessage());
//        }
//
//        brokerPool.release(broker);
//
//        return result;
//    }
    
//    /**
//     * @deprecated Get rid of this code.
//     */
//    public String getGrammarQuery(int type, String id){ // TODO double
//        String query="NOQUERY";
//        if(type==GRAMMAR_XSD){
//            query = "let $top := collection('"+XSDBASE+"') " +
//                    "let $schemas := $top/xs:schema[ @targetNamespace = \"" + id+ "\" ] "+
//                    "return if($schemas) then document-uri($schemas[1]) else \""+NOGRAMMAR+"\" " ;
//        } else if(type==GRAMMAR_DTD){
//            query = "let $top := doc('"+DTDCATALOG+"') "+
//                    "let $dtds := $top//public[@publicId = \""+id+"\"]/@uri " +
//                    "return if($dtds) then $dtds[1] else \""+NOGRAMMAR+"\"" ;
//        } else {
//            logger.error("Unknown grammar type, not able to find query.");
//        }
//
//        return query;
//    }
    
//    /**
//     *  Get GRAMMAR resource specified by DB path
//     *
//     * @deprecated Get rid of this code.
//     * @param path          Path in DB to resource.
//     * @param isBinary      Flag is resource binary?
//     * @return              Reader to the resource.
//     */
//    public byte[] getGrammar(int type, String path ){
//
//        byte[] data = null;
//        boolean isBinary=false;
//
//        if(type==GRAMMAR_DTD){
//            isBinary=true;
//        }
//
//        logger.debug("Get resource '"+path + "' binary="+ isBinary);
//
//        DBBroker broker = null;
//        try {
//
//            broker = brokerPool.get(SecurityManager.SYSTEM_USER);
//
//
//            if(isBinary){
//                BinaryDocument binDoc = (BinaryDocument) broker.openDocument(path, Lock.READ_LOCK);
//                data = broker.getBinaryResourceData(binDoc);
//                binDoc.getUpdateLock().release(Lock.READ_LOCK);
//
//            } else {
//
//                DocumentImpl doc = broker.openDocument(path, Lock.READ_LOCK);
//                Serializer serializer = broker.getSerializer();
//                serializer.reset();
//                data = serializer.serialize(doc).getBytes();
//                doc.getUpdateLock().release(Lock.READ_LOCK);
//            }
//        } catch (PermissionDeniedException ex){
//            logger.error("Error opening document", ex);
//        } catch (SAXException ex){
//            logger.error("Error serializing document", ex);
//        }  catch (EXistException ex){
//            logger.error(ex);
//        } finally {
//            if(brokerPool!=null){
//                brokerPool.release(broker);
//            }
//        }
//
//        return data;
//    }
    
    
    
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
        
        String xquery = FINDXSDINCATALOG
                .replaceAll("CATALOGFILE",collection.getCollectionPath()+"/"+docName)
                .replaceAll("NAMESPACE", namespace);
        
        String path = executeQuerySingleResult(xquery);
        
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
        String xquery = FINDDTDINCATALOG
                .replaceAll("CATALOGFILE",collection.getCollectionPath()+"/"+docName)
                .replaceAll("PUBLICID", publicId);
        
        String path = executeQuerySingleResult(xquery);
        
        if(path!= null && !path.startsWith("/")){
            path = collection.getCollectionPath()+"/"+ path;
        }
        
        return path;
    }
    
}
