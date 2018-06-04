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
package org.exist.protocolhandler;

import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.protocolhandler.protocols.xmldb.Handler;

/**
 * Factory class for creating custom stream handlers for the 'xmldb' protocol.
 *
 * @see java.net.URLStreamHandler
 * @see java.net.URLStreamHandlerFactory
 *
 * @author Dannes Wessels
 */
public class eXistURLStreamHandlerFactory implements URLStreamHandlerFactory {

    private final static Logger LOG = LogManager.getLogger(eXistURLStreamHandlerFactory.class);

    private final URLStreamHandler handler;

    eXistURLStreamHandlerFactory(final Mode mode) {
        handler = new Handler(mode);
    }

    /**
     *  Create Custom URL streamhandler for the <B>xmldb</B> protocol.
     *
     * @param protocol Protocol
     * @return Custom Xmldb stream handler.
     */
    public URLStreamHandler createURLStreamHandler(final String protocol) {
        if("xmldb".equals(protocol)) {

            if(LOG.isDebugEnabled()) {
                LOG.debug(protocol);
            }

            return handler;
        } else {
            //LOG.error("Protocol should be xmldb, not "+protocol);
            return null;
        }
    }
    
}
