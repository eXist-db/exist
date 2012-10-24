/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009 The eXist Project
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
 * $Id: EmbeddedDownloadThread.java 223 2007-04-21 22:13:05Z dizzzz $
 */

package org.exist.protocolhandler.embedded;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.log4j.Logger;

import org.exist.protocolhandler.xmldb.XmldbURL;
import org.exist.storage.BrokerPool;
import org.exist.storage.io.BlockingOutputStream;

/**
 *  Wrap EmbeddedDownload class into a thread for EmbeddedInputStream.
 *
 * @author Dannes Wessels
 */
public class EmbeddedDownloadThread extends Thread {
    
    private final static Logger logger = Logger.getLogger(EmbeddedDownloadThread.class);
    
    private XmldbURL xmldbURL;
    private OutputStream bos;
    private BrokerPool brokerPool;
    
    /**
     *  Constructor of EmbeddedDownloadThread.
     * 
     * @param url Document location in database.
     * @param bos Stream to which the document is written.
     */
    public EmbeddedDownloadThread(XmldbURL url, OutputStream bos) {
        xmldbURL = url;
        this.bos = bos;
    }

    /**
     *  Constructor of EmbeddedDownloadThread.
     *
     * @param url Document location in database.
     * @param bos Stream to which the document is written.
     */
    public EmbeddedDownloadThread(BrokerPool brokerPool, XmldbURL url, OutputStream bos) {
        xmldbURL = url;
        this.bos = bos;
        this.brokerPool=brokerPool;
    }
    
    /**
     * Write resource to the output stream.
     */
    public void run() {
        logger.debug("Thread started." );
        IOException exception=null;
        try {
            EmbeddedDownload ed = new EmbeddedDownload();
            ed.setBrokerPool(brokerPool);
            ed.stream(xmldbURL, bos);
            
        } catch (IOException ex) {
            logger.error(ex);
            exception = ex;
            
        } finally {
            try { // NEEDED!
                bos.close();
            } catch (IOException ex) {
                logger.debug(ex);
            }
            logger.debug("Thread stopped." );
        }
    }

}
