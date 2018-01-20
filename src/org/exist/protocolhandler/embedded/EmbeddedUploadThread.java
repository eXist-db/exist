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
 * $Id: EmbeddedUploadThread.java 223 2007-04-21 22:13:05Z dizzzz $
 */

package org.exist.protocolhandler.embedded;


import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.protocolhandler.xmldb.XmldbURL;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.storage.io.BlockingInputStream;

/**
 *  Wrap XmlrpcUpload class into a thread for XmlrpcOutputStream.
 *
 * @author Dannes Wessels
 */
public class EmbeddedUploadThread extends Thread {
    
    private final static Logger logger = LogManager.getLogger(EmbeddedUploadThread.class);
    private XmldbURL xmldbURL;
    private BlockingInputStream bis;
    
    private Subject subject = null;

    private static final AtomicInteger threadInitNumber = new AtomicInteger();
    
    public EmbeddedUploadThread(XmldbURL url, BlockingInputStream bis) {
        super("exist-embeddedDownloadThread-" + threadInitNumber.getAndIncrement());

        xmldbURL=url;
        this.bis=bis;
        
        try {
            BrokerPool pool = BrokerPool.getInstance(url.getInstanceName());
            subject = pool.getActiveBroker().getCurrentSubject();
        } catch (Throwable e) {
            //e.printStackTrace();
        }
    }
    
    /**
     * Start Thread.
     */
    @Override
    public void run() {
        logger.debug("Thread started." );
        IOException exception=null;
        try {
            final EmbeddedUpload uploader = new EmbeddedUpload();
            uploader.stream(xmldbURL, bis, subject);
            
        } catch (IOException ex) {
            logger.error(ex);
            exception = ex;
            
        } finally {
            bis.close(exception);
            logger.debug("Thread stopped." );
        }
    }
}
