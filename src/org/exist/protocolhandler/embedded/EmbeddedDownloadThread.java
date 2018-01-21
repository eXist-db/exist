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
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.protocolhandler.xmldb.XmldbURL;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;

/**
 *  Wrap EmbeddedDownload class into a thread for EmbeddedInputStream.
 *
 * @author Dannes Wessels
 */
public class EmbeddedDownloadThread extends Thread {
    
    private static final Logger LOG = LogManager.getLogger(EmbeddedDownloadThread.class);
    
    private final XmldbURL xmldbURL;
    private final OutputStream bos;
    
    private Subject subject;
    private BrokerPool brokerPool;

    private static final AtomicInteger threadInitNumber = new AtomicInteger();
    
    /**
     *  Constructor of EmbeddedDownloadThread.
     * 
     * @param url Document location in database.
     * @param bos Stream to which the document is written.
     */
    public EmbeddedDownloadThread(final XmldbURL url, final OutputStream bos) {
        super("exist-embeddedDownloadThread-" + threadInitNumber.getAndIncrement());
        this.xmldbURL = url;
        this.bos = bos;
        
        try {
            final BrokerPool pool = BrokerPool.getInstance(url.getInstanceName());
            this.subject = pool.getActiveBroker().getCurrentSubject();
        } catch (final Throwable e) {
            LOG.error(e);
        }
    }

    /**
     *  Constructor of EmbeddedDownloadThread.
     *
     * @param url Document location in database.
     * @param bos Stream to which the document is written.
     */
    public EmbeddedDownloadThread(final BrokerPool brokerPool, final XmldbURL url, final OutputStream bos) {
        super("EmbeddedDownloadThread-" + threadInitNumber.getAndIncrement());
        this.xmldbURL = url;
        this.bos = bos;

        try {
            if (brokerPool == null) {
                this.brokerPool = BrokerPool.getInstance(url.getInstanceName());
            } else {
                this.brokerPool = brokerPool;
            }
            
            this.subject = this.brokerPool.getActiveBroker().getCurrentSubject();
        } catch (final Throwable e) {
            LOG.error(e);
        }
    }
    
    /**
     * Write resource to the output stream.
     */
    @Override
    public void run() {
        if(LOG.isDebugEnabled()) {
            LOG.debug("Thread started.");
        }

        try {
            final EmbeddedDownload ed = new EmbeddedDownload();
            ed.setBrokerPool(brokerPool);
            ed.stream(xmldbURL, bos, subject);
        } catch (final IOException ex) {
            LOG.error(ex);
            
        } finally {
            try { // NEEDED!
                bos.close();
            } catch (final IOException ex) {
                LOG.warn(ex);
            }

            if(LOG.isDebugEnabled()) {
                LOG.debug("Thread stopped.");
            }
        }
    }

}
