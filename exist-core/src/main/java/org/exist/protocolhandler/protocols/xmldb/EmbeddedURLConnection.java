/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2017 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.protocolhandler.protocols.xmldb;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.protocolhandler.embedded.EmbeddedInputStream;
import org.exist.protocolhandler.embedded.EmbeddedOutputStream;
import org.exist.protocolhandler.xmldb.XmldbURL;
import org.exist.protocolhandler.xmlrpc.XmlrpcInputStream;
import org.exist.protocolhandler.xmlrpc.XmlrpcOutputStream;

/**
 *  A URLConnection object manages the translation of a URL object into a
 * resource stream.
 *
 * @see <A HREF="http://java.sun.com/developer/onlineTraining/protocolhandlers/"
 *                                     >A New Era for Java Protocol Handlers</A>
 *
 * @see java.net.URLConnection
 *
 * @author Dannes Wessels
 */
public class EmbeddedURLConnection extends URLConnection {
    private static final Logger LOG = LogManager.getLogger(EmbeddedURLConnection.class);
    private final ThreadGroup threadGroup;

    /**
     * Constructs a URL connection to the specified URL.
     *
     * @param threadGroup Thread group
     * @param url URL
      */
    protected EmbeddedURLConnection(final ThreadGroup threadGroup, final URL url) {
        super(url);
        if (LOG.isDebugEnabled()) {
            LOG.debug(url);
        }
        this.threadGroup = threadGroup;
        setDoInput(true);
        setDoOutput(true);
    }
    
    @Override
    public void connect() {
        if (LOG.isDebugEnabled()) {
            LOG.debug(url);
        }
    }
    
    @Override
    public InputStream getInputStream() throws IOException {
        if (LOG.isDebugEnabled()) {
            LOG.debug(url);
        }

        final XmldbURL xmldbURL = new XmldbURL(url);

        final InputStream inputstream;
        if(xmldbURL.isEmbedded()){
            inputstream = new EmbeddedInputStream( xmldbURL );
        } else {
            inputstream = new XmlrpcInputStream(threadGroup, xmldbURL);
        }
        
        return inputstream;
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        if (LOG.isDebugEnabled()) {
            LOG.debug(url);
        }

        final XmldbURL xmldbURL = new XmldbURL(url);

        final OutputStream outputstream;
        if(xmldbURL.isEmbedded()){
            outputstream = new EmbeddedOutputStream( xmldbURL );
        } else {
            outputstream = new XmlrpcOutputStream(threadGroup, xmldbURL);
        }
        
        return outputstream;
    }
}
