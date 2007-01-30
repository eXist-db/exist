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

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.dom.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SecurityManager;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.Serializer;
import org.exist.xmldb.XmldbURI;
import org.xml.sax.SAXException;


/**
 *  Write XML resource to outputstream in thread.
 *
 * @author wessels
 */
public class ResourceThread extends Thread {
    
    private final static Logger logger = Logger.getLogger(ResourceThread.class);
    private BrokerPool brokerPool;
    private XmldbURI docUri;
    private OutputStream outputStream;
    private Exception exception=null;

    
    /** Creates a new instance of ResourceThread */
    public ResourceThread(BrokerPool pool, XmldbURI docUri, OutputStream os) {
        logger.debug("Initializing ResourceThread." );
        this.brokerPool=pool;
        this.docUri=docUri;
        this.outputStream=os;
    }
    
    public boolean isExceptionThrown(){
        return (exception!=null);
    }
    
    public Exception getThrownException(){
        return this.exception;
    }
    
    /**
     * Start Thread.
     */
    public void run() {
        logger.debug("Start thread." );
        writeXmlResource( new OutputStreamWriter(outputStream) );
    }
    
    /**
     *   Serialize XML document to Writer object.
     *
     * @param writer Object that receives the serialized data.
     */
    private void writeXmlResource( Writer writer ){
        
        logger.debug("Writing XML resource '"+docUri+"' as stream." );
        DBBroker broker = null;
        
        try {
            broker = brokerPool.get(SecurityManager.SYSTEM_USER);
            
            
            DocumentImpl doc = broker.getXMLResource(docUri, Lock.READ_LOCK);
            
            if(doc==null){
                logger.error("Document '"+docUri+"' does not exist");
            } else {
                Serializer serializer = broker.getSerializer();
                serializer.reset();
                
                // Doctype info must be serialized too.
                serializer.setProperty("output-doctype","yes");
                serializer.serialize(doc, writer);
                doc.getUpdateLock().release(Lock.READ_LOCK);
            }
            
            writer.flush();
            writer.close();
            
        } catch (EXistException ex){
            logger.error(ex);
            exception=ex;
        } catch (PermissionDeniedException ex){
            logger.error(ex);
            exception=ex;
        } catch (SAXException ex){
            logger.error(ex);
            exception=ex;
        } catch (IOException ex){
            logger.error(ex);
            exception=ex;
        } finally {
            if(brokerPool!=null){
                brokerPool.release(broker);
            }
        }
        
        
        logger.debug("Writing XML resource ready." );
    }
    
}
