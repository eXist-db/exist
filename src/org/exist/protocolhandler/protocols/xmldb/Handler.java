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
 *  $Id: Handler.java 189 2007-03-30 15:02:18Z dizzzz $
 */

package org.exist.protocolhandler.protocols.xmldb;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

import org.apache.log4j.Logger;

/**
 *  A stream protocol handler knows how to make a connection for a particular
 * protocol type. This handler deals with "xmldb:"
 *
 * @author Dannes Wessels
 *
 * @see <A HREF="http://java.sun.com/developer/onlineTraining/protocolhandlers/"
 *                                     >A New Era for Java Protocol Handlers</A>
 * @see java.net.URLStreamHandler
 */
public class Handler extends URLStreamHandler {
    
    private final static Logger LOG = Logger.getLogger(Handler.class);
    
    public static final String XMLDB_EXIST  = "xmldb:exist:";
    public static final String XMLDB        = "xmldb:";
    public static final String PATTERN      = "xmldb:[\\w]+:\\/\\/.*";
    
    /**
     * Creates a new instance of Handler
     */
    public Handler() {
        LOG.debug("Setup \"xmldb:exist:\" handler");
    }
    
    /**
     * @see java.net.URLStreamHandler#parseURL(java.net.URL,java.lang.String,int,int)
     *
     * TODO: exist instance names must be supported. The idea is to pass
     * this information as a parameter to the url, format __instance=XXXXX
     * Should we clean all other params? remove #?
     */
    protected void parseURL(URL url, String spec, int start, int limit) {
        LOG.debug(spec);
        
        if(spec.startsWith(XMLDB_EXIST+"//")){
            LOG.debug("Parsing xmldb:exist:// URL.");
            super.parseURL(url, spec, XMLDB_EXIST.length(), limit);
            
        } else if(spec.startsWith(XMLDB+"//")) {
            LOG.debug("Parsing xmldb:// URL.");
            super.parseURL(url, spec, XMLDB.length(), limit);
            
        } else if(spec.matches(PATTERN)) {
            LOG.debug("Parsing URL with custom exist instance");
            String splits[] = spec.split(":",3);
            String instance = splits[1]; // TODO pass to URL as param

            int seperator = spec.indexOf("//");
            super.parseURL(url, spec, seperator, limit);
            
        } else if(spec.startsWith("xmldb:://")){  // very dirty
            int seperator = spec.indexOf("//");
            super.parseURL(url, spec, seperator, limit);
            
        } else if(spec.startsWith("xmldb:/")){  // little dirty
            super.parseURL(url, spec, start, limit);
            
        } else {
            LOG.error("Expected 'xmldb:'-like URL, found "+spec);
            super.parseURL(url, spec, start, limit);
        }
        
    }
    
    /**
     * @see java.net.URLStreamHandler#openConnection(java.net.URL)
     */
    protected URLConnection openConnection(URL u) throws IOException {
        return new Connection(u);
    }
    
}
