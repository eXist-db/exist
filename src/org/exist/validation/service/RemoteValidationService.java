/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 *  $Id$
 */

package org.exist.validation.service;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.apache.xmlrpc.XmlRpcClient;
import org.apache.xmlrpc.XmlRpcException;
import org.exist.validation.Validator;
import org.exist.xmldb.RemoteCollection;
import org.exist.xmldb.XmldbURI;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;

/**
 *  XML validation service for eXist database.
 *
 * @author dizzzz
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
    	try{
    		return validateResource(XmldbURI.xmldbUriFor(id));
    	} catch(URISyntaxException e) {
    		throw new XMLDBException(ErrorCodes.INVALID_URI,e);
    	}
    }
    /**
     * Validate specified resource.
     */
    public boolean validateResource(XmldbURI id) throws XMLDBException {
        logger.info("Validating resource '" + id + "'");
        boolean documentIsValid = false;       
        id = remoteCollection.getPathURI().resolveCollectionPath(id);
        
        Vector params = new Vector();
        params.addElement( id.toString() );
        
        try {
            Boolean result = (Boolean) client.execute("isValid", params);
            documentIsValid= result.booleanValue();            
        } catch (XmlRpcException xre) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, xre.getMessage(), xre);            
        } catch (IOException ioe) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, ioe.getMessage(), ioe);
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
