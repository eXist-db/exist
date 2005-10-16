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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.security.SecurityManager;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionException;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;

import org.apache.log4j.Logger;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;


/**
 *
 * @author wessels
 */
public class DatabaseResources {
    
    /** Local reference to database  */
    private BrokerPool brokerPool = null;
    
    /** Local logger */
    private final static Logger logger = Logger.getLogger(DatabaseResources.class);
    
    /** Path to grammar in database  */
    private String GRAMMERBASE = "/db/system/grammar";
    private String XSDBASE = GRAMMERBASE + "/xsd";
    private String DTDBASE = GRAMMERBASE + "/dtd";
    private String DTDCATALOG = DTDBASE + "/catalog.xml";
    
    public static int GRAMMAR_UNKNOWN = 0;
    public static int GRAMMAR_XSD = 1;
    public static int GRAMMAR_DTD = 2;
    
    private String CATALOGCONTENT="<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            +"<catalog>\n"
            +"<!-- Warning this file is regenerated at every start -->\n"
            +"<!-- Will be fixed in the near future -->\n"
            +"<!-- <public publicId=\"-//PLAY//EN\" uri=\"entities/play.dtd\"/> -->\n"
            +"</catalog>";
    
    /**
     * Creates a new instance of DatabaseResources
     */
    public DatabaseResources(BrokerPool pool) {
        
        logger.info("Initializing DatabaseResources");
        this.brokerPool = pool;
        
        // TODO this must be performed once.... and earlier...
        insertCollection(GRAMMERBASE);
        insertCollection(XSDBASE);
        insertCollection(DTDBASE);
        insertGrammar( new StringReader(CATALOGCONTENT), GRAMMAR_DTD, "catalog.xml");
        
    }
    
    public boolean insertCollection(String path){
        
        boolean insertIsSuccessfull=false;
        
        DBBroker broker = null;
        try {
            broker = brokerPool.get(SecurityManager.SYSTEM_USER);
            
            TransactionManager transact = brokerPool.getTransactionManager();
            Txn transaction = transact.beginTransaction();
            
            Collection collection = broker.getOrCreateCollection(transaction, path);
            broker.saveCollection(transaction, collection);
            
            transact.commit(transaction);
            
            insertIsSuccessfull=true;
            
        } catch (PermissionDeniedException ex){
            logger.error(ex);
            
        } catch (TransactionException ex){
            logger.error(ex);
            
        } catch (EXistException ex){
            logger.error(ex);
        } finally {
            if(brokerPool!=null){
                brokerPool.release(broker);
            }
            
        }
        
        
        
        return insertIsSuccessfull;
    }
    
    
    public boolean insertGrammar(Reader reader, int type, String path){
        
        boolean insertIsSuccessfull=false;
        
        try{
            File tmpFile = File.createTempFile("InsertGrammar","tmp");
            FileWriter fw = new FileWriter(tmpFile);
            
            // Transfer bytes from in to out
            char[] buf = new char[1024];
            int len;
            while ((len = reader.read(buf, 0, 1024)) > 0) {
                fw.write(buf, 0, len);
            }
            reader.close();
            fw.close();
            
            insertIsSuccessfull = insertGrammar(tmpFile, type, path);
            tmpFile.delete();
        } catch (IOException ex){
            logger.error(ex);
        }
        return insertIsSuccessfull;
    }
    
    public boolean insertGrammar(File file, int type, String path){
        
        boolean insertIsSuccesfull = false;
        
        // Path = subpath/doc.xml
        String baseFolder=null;
        if(type==GRAMMAR_XSD){
            baseFolder = XSDBASE;
        } else {
            baseFolder = DTDBASE;
        }
        
        String collection=null;
        String document=null;
        int separatorPos = path.lastIndexOf("/");
        if(separatorPos==-1){
            document=path;
            collection=baseFolder ;
        } else {
            document=path.substring(separatorPos+1);
            collection=baseFolder + "/" +path.substring(0, separatorPos);
        }
        
        logger.info("document="+document);
        logger.info("collection="+collection);
        
        return insertDocumentInDatabase(file,  collection, document);
    }
    
    public boolean insertDocumentInDatabase(File file, String collection, String document){
        
        boolean insertIsSuccesfull=false;
        
        DBBroker broker = null;
        try {
            
            broker = brokerPool.get(SecurityManager.SYSTEM_USER);
            
            TransactionManager transact = brokerPool.getTransactionManager();
            Txn transaction = transact.beginTransaction();
            
            Collection test = broker.getOrCreateCollection(transaction, collection);
            broker.saveCollection(transaction, test);
            
            
            IndexInfo info = test.validate( transaction, broker, document , new InputSource( new FileReader(file) ) );
            test.store(transaction, broker, info, new InputSource( new FileReader(file) ), false);
            
            transact.commit(transaction);
            
            insertIsSuccesfull=true;
            
        } catch (EXistException ex){
            logger.error(ex);
        } catch (PermissionDeniedException ex){
            logger.error(ex);
        } catch (SAXException ex){
            logger.error(ex);
        } catch (TriggerException ex){
            logger.error(ex);
        } catch(LockException ex){
            logger.error(ex);
        } catch(FileNotFoundException ex){
            logger.error(ex);
        } finally {
            if(brokerPool!=null){
                brokerPool.release(broker);
            }
        }
        return insertIsSuccesfull;
    }
    
    
    public boolean hasGrammar(int type, String id){
        return !getGrammarPath(type, id).equalsIgnoreCase("NONE");
    }
    
    public String getGrammarPath(int type, String id){
        
        logger.info("Get path of '"+id+"'");
        
        String result="EMPTY";
        String query = getGrammarQuery(type, id);
        
        DBBroker broker = null;
        try {
            broker = brokerPool.get(SecurityManager.SYSTEM_USER);
        } catch (EXistException ex){
            logger.error("Error getting DBBroker", ex);
        }
        
        XQuery xquery = broker.getXQueryService();
        try{
            Sequence seq = xquery.execute(query, null);
            
            SequenceIterator i = seq.iterate();
            if(i.hasNext()){
                result= i.nextItem().getStringValue();
                
            } else {
                logger.debug("No xQuery result");
            }
            
        } catch (XPathException ex){
            logger.error("XSD xQuery error: "+ ex.getMessage());
        }
        
        brokerPool.release(broker);
        
        return result;
    }
    
    public String getGrammarQuery(int type, String id){ // TODO double
        String query="NOQUERY";
        if(type==GRAMMAR_XSD){
            query = "let $top := collection('"+XSDBASE+"') " +
                    "let $schemas := $top/xs:schema[ @targetNamespace = \"" + id+ "\" ] "+
                    "return if($schemas) then document-uri($schemas[1]) else \"NONE\" " ;
        } else if(type==GRAMMAR_DTD){
            query = "let $top := doc('"+DTDCATALOG+"') "+
                    "let $dtds := $top//public[@publicId = \""+id+"\"]/@uri " +
                    "return if($dtds) then document-uri($dtds[1]) else \"NONE\"" ;
        }
        logger.info(query);
        return query;
    }
    
    /**
     *  Get GRAMMAR resource specified by DB path
     * @param path          Path in DB to resource.
     * @param isBinary      Flag is resource binary?
     * @return              Reader to the resource.
     */
    public byte[] getGrammar(int type, String path ){
        
        byte[] data = null;
        boolean isBinary=false;
        
        if(type==GRAMMAR_DTD){
            isBinary=true;
        }
        
        logger.debug("Get resource '"+path + "' binary="+ isBinary);
        Reader reader=null;
        
        DBBroker broker = null;
        try {
            
            broker = brokerPool.get(SecurityManager.SYSTEM_USER);
        

            if(isBinary){
                BinaryDocument binDoc = (BinaryDocument) broker.openDocument(path, Lock.READ_LOCK);
                data = broker.getBinaryResourceData(binDoc);
                binDoc.getUpdateLock().release(Lock.READ_LOCK);
                
            } else {
                
                DocumentImpl doc = broker.openDocument(path, Lock.READ_LOCK);
                Serializer serializer = broker.getSerializer();
                serializer.reset();
                data = serializer.serialize(doc).getBytes();
                doc.getUpdateLock().release(Lock.READ_LOCK);
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
    
}
