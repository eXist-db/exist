/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2010 The eXist Project
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
 *  $Id$
 */

package org.exist.validation.resolver;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.apache.xerces.xni.XMLResourceIdentifier;
import org.apache.xerces.xni.XNIException;
import org.apache.xerces.xni.parser.XMLEntityResolver;
import org.apache.xerces.xni.parser.XMLInputSource;

import org.exist.protocolhandler.embedded.EmbeddedInputStream;
import org.exist.protocolhandler.xmldb.XmldbURL;
import org.exist.protocolhandler.xmlrpc.XmlrpcInputStream;

/**
 *  Resolve a resource specified by xs:anyURI. First time the
 * resource is resolved by the URL as specified in the constructor, 
 * the second the URL of the ExpandedSystemId is used.
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 */
public class AnyUriResolver implements XMLEntityResolver {
    private final static Logger LOG = LogManager.getLogger(AnyUriResolver.class);
    
    private String docPath;
    private String parentURI;
    
    private boolean firstTime=true;
    
    /** Creates a new instance of AnyUriResolver */
    public AnyUriResolver(String path) {
        docPath=path;
        if(docPath.startsWith("/")){
            docPath="xmldb:exist://"+docPath;
        }
        LOG.debug("Specified path="+path);
        
        if(path.lastIndexOf('/')!=-1){
            parentURI=path.substring(0, path.lastIndexOf('/'));
            LOG.debug("parentURI="+parentURI);
        } else {
            parentURI="";
        }
    }
    

    @Override
    public XMLInputSource resolveEntity(XMLResourceIdentifier xri) throws XNIException, IOException {
        
        if(xri.getExpandedSystemId()==null && xri.getLiteralSystemId()==null && 
           xri.getNamespace()==null && xri.getPublicId()==null){
            
            // quick fail
            return null;
        }
        
        if(LOG.isDebugEnabled()) {
            LOG.debug("Resolving XMLResourceIdentifier: "+getXriDetails(xri));
        }
        
        String resourcePath=null;
        String baseSystemId=null;
        
        if(firstTime){
            // First time use constructor supplied path
            resourcePath = docPath;
            baseSystemId = parentURI;
            xri.setExpandedSystemId(docPath);      
            firstTime=false;
            
        } else {
            resourcePath=xri.getExpandedSystemId();
        }
        xri.setBaseSystemId(docPath);
        
        LOG.debug("resourcePath='"+resourcePath+"'");

        // prevent NPE
        if(resourcePath==null){
            return null;
        }
        
        InputStream is = null;
        if(resourcePath.startsWith("xmldb:")){
            final XmldbURL xmldbURL = new XmldbURL(resourcePath);
            if(xmldbURL.isEmbedded()){
                is = new EmbeddedInputStream( xmldbURL );
                
            } else {
                is = new XmlrpcInputStream( xmldbURL );
            } 

        } else {
            is = new URL(resourcePath).openStream();
        }
        
        final XMLInputSource xis = new XMLInputSource(xri.getPublicId(), resourcePath,
            baseSystemId, is, "UTF-8");
        
        if(LOG.isDebugEnabled()) {
            LOG.debug( "XMLInputSource: "+getXisDetails(xis) );
        }
        
        return xis;
        
    }
    
    private String getXriDetails(XMLResourceIdentifier xrid){
        final StringBuilder sb = new StringBuilder();
        sb.append("PublicId='").append(xrid.getPublicId()).append("' ");
        sb.append("BaseSystemId='").append(xrid.getBaseSystemId()).append("' ");
        sb.append("ExpandedSystemId='").append(xrid.getExpandedSystemId()).append("' ");
        sb.append("LiteralSystemId='").append(xrid.getLiteralSystemId()).append("' ");
        sb.append("Namespace='").append(xrid.getNamespace()).append("' ");
        return sb.toString();
    }

    private String getXisDetails(XMLInputSource xis){
        final StringBuilder sb = new StringBuilder();
        sb.append("PublicId='").append(xis.getPublicId()).append("' ");
        sb.append("SystemId='").append(xis.getSystemId()).append("' ");
        sb.append("BaseSystemId='").append(xis.getBaseSystemId()).append("' ");
        sb.append("Encoding='").append(xis.getEncoding()).append("' ");
        return sb.toString();
    }
    
}
