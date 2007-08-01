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
 * $Id$
 */

package org.exist.validation.resolver;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.apache.log4j.Logger;
import org.apache.xerces.xni.XMLResourceIdentifier;
import org.apache.xerces.xni.XNIException;
import org.apache.xerces.xni.parser.XMLEntityResolver;
import org.apache.xerces.xni.parser.XMLInputSource;

/**
 *  Resolve a resource straight out of database.
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 */
public class StoredResourceResolver implements XMLEntityResolver {
    private final static Logger LOG = Logger.getLogger(StoredResourceResolver.class);
    
    private String docPath=null;
    
    /** Creates a new instance of StoredResourceResolver */
    public StoredResourceResolver(String path) {
        docPath=path;
        if(docPath.startsWith("/")){
            docPath="xmldb:exist://"+docPath;
        }
    }
    
    /**
     *
     */
    public XMLInputSource resolveEntity(XMLResourceIdentifier xri) throws XNIException, IOException {
        
        LOG.debug("BaseSystemId='" + xri.getBaseSystemId()
        +"' ExpandedSystemId=" + xri.getExpandedSystemId()
        +"' LiteralSystemId=" + xri.getLiteralSystemId()
        +"' Namespace=" + xri.getNamespace()
        +"' PublicId=" + xri.getPublicId() +"'");
        
        String resourcePath=null;
        
        if(xri.getBaseSystemId()==null){
            // First time use constructor supplied path
            resourcePath = docPath;
            // set expandedSystem=Id?
            
        } else {
            // subsequent steps
            resourcePath = xri.getExpandedSystemId();
        }
        
        LOG.debug("resourcePath="+resourcePath);
        // DWES set systemid?
        
        InputStream is = new URL(resourcePath).openStream();
        XMLInputSource retVal = new XMLInputSource(xri.getPublicId(), xri.getExpandedSystemId(),
            xri.getBaseSystemId(), is, null); //UTF-8?
        return retVal;
        
    }
    
}
