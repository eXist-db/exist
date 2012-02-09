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
 *  $Id$
 */
package org.exist.validation.service;

import org.apache.log4j.Logger;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.exist.validation.Validator;
import org.exist.xmldb.RemoteCollection;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;

import java.util.ArrayList;
import java.util.List;

/**
 *  XML validation service for eXist database.
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 */
public class RemoteValidationService implements ValidationService {
    
    private static Logger logger = Logger.getLogger(RemoteValidationService.class);
    
    private XmlRpcClient client = null;
    private RemoteCollection remoteCollection = null;
    private Validator validator = null;
    
    public RemoteValidationService( RemoteCollection parent, XmlRpcClient client ) {
        logger.info("Starting RemoteValidationService");
        this.client = client;
        this.remoteCollection = parent;
    }
    
    /**
     * Validate specified resource.
     */
    public boolean validateResource(String id) throws XMLDBException {
         return validateResource(id, null);
    }
    
    public boolean validateResource(String documentPath, String grammarPath) throws XMLDBException {
        
        if(documentPath.startsWith("/")){
            documentPath="xmldb:exist://"+documentPath;
        }
        
        if(grammarPath!=null && grammarPath.startsWith("/")){
            grammarPath="xmldb:exist://"+grammarPath;
        }
        
        logger.info("Validating resource '" + documentPath + "'");
        boolean documentIsValid = false;
//        documentPath = remoteCollection.getPathURI().resolveCollectionPath(documentPath);

        List params = new ArrayList(1);
        params.add( documentPath );
        
        if(grammarPath!=null){
            params.add( grammarPath );
        }
        
        try {
            Boolean result = (Boolean) client.execute("isValid", params);
            documentIsValid= result.booleanValue();
        } catch (XmlRpcException xre) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, xre.getMessage(), xre);
        }

        return documentIsValid;
    }
    
    // ----------------------------------------------------------
    
    public void setCollection(Collection collection) throws XMLDBException {
        // left empty
    }
    
    public String getName() throws XMLDBException {
        return "ValidationService";
    }
    
    public String getVersion() throws XMLDBException {
        return "1.0";
    }
    
    public void setProperty(String str, String str1) throws XMLDBException {
        // left empty
    }
    
    public String getProperty(String str) throws XMLDBException {
        // left empty
        return null;
    }
    
    
}
