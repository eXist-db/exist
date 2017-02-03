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
 * $Id: EmbeddedInputStream.java 223 2007-04-21 22:13:05Z dizzzz $
 */

package org.exist.protocolhandler.embedded;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.MalformedURLException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.protocolhandler.xmldb.XmldbURL;
import org.exist.storage.BrokerPool;

/**
 * Read document from embedded database as an InputStream
 *
 * @author Dannes Wessels
 */
public class EmbeddedInputStream extends InputStream {
    
    private static final Logger LOG = LogManager.getLogger(EmbeddedInputStream.class);
    
    private final PipedInputStream  bis;
    private final PipedOutputStream bos;
    private final EmbeddedDownloadThread rt;
    
    /**
     *  Constructor of EmbeddedInputStream. 
     * 
     * @param xmldbURL Location of document in database.
     * @throws MalformedURLException Thrown for illegalillegal URLs.
     */
    public EmbeddedInputStream(final XmldbURL xmldbURL) throws IOException {
        this(null, xmldbURL);
    }

    /**
     *  Constructor of EmbeddedInputStream.
     *
     * @param xmldbURL Location of document in database.
     * @throws MalformedURLException Thrown for illegalillegal URLs.
     */
    public EmbeddedInputStream(final BrokerPool brokerPool, final XmldbURL xmldbURL) throws IOException {

        if(LOG.isDebugEnabled()) {
            LOG.debug("Initializing EmbeddedInputStream");
        }

        this.bis = new PipedInputStream(4096);
        this.bos = new PipedOutputStream(bis);
        this.rt = new EmbeddedDownloadThread(brokerPool, xmldbURL , bos);

        if(LOG.isDebugEnabled()) {
            LOG.debug("Initializing EmbeddedInputStream done");
        }

        rt.start();
    }
    
    
    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        return bis.read(b, off, len);
    }
    
    @Override
    public int read(final byte[] b) throws IOException {
        return bis.read(b, 0, b.length);
    }
    
    @Override
    public long skip(final long n) throws IOException {
        return bis.skip(n);
    }
    
    @Override
    public void reset() throws IOException {
        bis.reset();
    }
    
    @Override
    public int read() throws IOException {
        return bis.read();
    }

    @Override
    public void close() throws IOException {
        bis.close();
    }

    @Override
    public int available() throws IOException {
        return bis.available();
    }
}
