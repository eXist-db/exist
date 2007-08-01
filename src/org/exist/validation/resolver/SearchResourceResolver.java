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
import org.exist.security.SecurityManager;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.validation.internal.DatabaseResources;
import org.xml.sax.InputSource;

/**
 *  Resolve a resource by searching in database. Schema's are queried
 * directly, DTD are searched in catalog files.
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 */
public class SearchResourceResolver implements XMLEntityResolver {
    private final static Logger LOG = Logger.getLogger(SearchResourceResolver.class);
    
    private String collection=null;
    private BrokerPool brokerPool = null;
    
    /** Creates a new instance of StoredResourceResolver */
    public SearchResourceResolver(String collectionPath, BrokerPool pool) {
        collection=collectionPath;
        brokerPool=pool;
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
        
        String documentName = null;
        String resourcePath = null;
        
        DatabaseResources databaseResources = new DatabaseResources(brokerPool);
        
        User user = brokerPool.getSecurityManager().getUser(SecurityManager.GUEST_USER);
        
        if( xri.getNamespace() !=null ){
            
            // XML Schema search
            LOG.debug("Searching namespace '"+xri.getNamespace()+"'.");
            resourcePath = databaseResources.findXSD(collection, xri.getNamespace(), user);
            // set systemid?
            
        } else if ( xri.getPublicId() !=null ){
            
            // Catalog search
            LOG.debug("Searching publicId '"+xri.getPublicId()+"'.");
            String catalogPath = databaseResources.findCatalogWithDTD(collection, xri.getPublicId(), user);
            LOG.info("Found catalog='"+catalogPath+"'");
            if(catalogPath!=null && catalogPath.startsWith("/")){
                catalogPath="xmldb:exist://"+catalogPath;
            }
            eXistXMLCatalogResolver resolver = new eXistXMLCatalogResolver();
            resolver.setCatalogList(new String[]{catalogPath});
            try {
                InputSource source = resolver.resolveEntity(xri.getPublicId(), "");
                if(source!=null){
                    resourcePath=source.getSystemId();
                }
            } catch (Exception ex) {
                LOG.debug(ex);
                ex.printStackTrace();
            }
            
            // set systemid?
            
        } else {
            // Fast escape; no logging, otherwise validation is slow!
            return null;
        }
        

        if(resourcePath==null){
            LOG.debug("resourcePath="+resourcePath);
            return null;
        }
        
        if(resourcePath!=null && resourcePath.startsWith("/")){
            resourcePath="xmldb:exist://"+resourcePath;
        }
        LOG.debug("resourcePath="+resourcePath);
        
        
        InputStream is = new URL(resourcePath).openStream();
        XMLInputSource retVal = new XMLInputSource(xri.getPublicId(), 
            xri.getExpandedSystemId(), xri.getBaseSystemId(), is, "UTF-8");
        return retVal;
        
    }
    
}
