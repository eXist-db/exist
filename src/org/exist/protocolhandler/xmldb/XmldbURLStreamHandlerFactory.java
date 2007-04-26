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
 *  $Id: XmldbURLStreamHandlerFactory.java 189 2007-03-30 15:02:18Z dizzzz $
 */

package org.exist.protocolhandler.xmldb;

import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;

import org.apache.log4j.Logger;
import org.exist.protocolhandler.protocols.xmldb.Handler;
import org.exist.xmldb.*;

/**
 * Factory class for creating custom stream handlers for the 'xmldb' protocol.
 *
 * @see java.net.URLStreamHandler
 * @see java.net.URLStreamHandlerFactory
 *
 * @author Dannes Wessels
 */
public class XmldbURLStreamHandlerFactory implements URLStreamHandlerFactory {
    
    private final static Logger LOG = Logger.getLogger(XmldbURLStreamHandlerFactory.class);
    
    /**
     *  Create Custom URL streamhandler for the <B>xmldb</B> protocol.
     *
     * @param protocol Protocol
     * @return Custom Xmldb stream handler.
     */
    public URLStreamHandler createURLStreamHandler(String protocol) {
        
        URLStreamHandler handler=null;
        
        if("xmldb".equals(protocol)){
            LOG.debug(protocol);
            handler=new Handler();
        } else {
            //LOG.error("Protocol should be xmldb, not "+protocol);
        }
        
        return handler;
    }
    
}
