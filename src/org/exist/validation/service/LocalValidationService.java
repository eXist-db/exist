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

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.log4j.Logger;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.validation.ValidationReport;
import org.exist.validation.Validator;
import org.exist.xmldb.LocalCollection;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;



/**
 *  XML validation service for LocalMode of eXist database.
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 */
public class LocalValidationService implements ValidationService {
    
    private static Logger logger = Logger.getLogger(LocalValidationService.class);
    
    private BrokerPool brokerPool ;
    private User user;
    private LocalCollection localCollection;
    private Validator validator;
    
    public LocalValidationService(User user, BrokerPool pool, LocalCollection collection) {
        logger.info("Starting LocalValidationService");
        this.user = user;
        this.brokerPool = pool;
        this.localCollection = collection;
        validator = new Validator(pool);
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
        
        logger.info("Validating resource '"+documentPath+"'");
        
        InputStream is = null;
        try {
            
            is = new URL(documentPath).openStream();
        } catch (MalformedURLException ex) {
            logger.error(ex);
            throw new XMLDBException(ErrorCodes.INVALID_URI, ex);
        } catch (Exception ex) {
            logger.error(ex);
            throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, ex);
        }
        
        if(is==null){
            logger.error("Resource not found");
            throw new XMLDBException(ErrorCodes.NO_SUCH_RESOURCE, "Resource not found");
        }
        
        // Perform validation
        ValidationReport report = null;
        if(grammarPath==null){
            report = validator.validate(is); 
        } else {
            report = validator.validate(is, grammarPath);
        }
        
        
        // Return validation result
        logger.info("Validation done.");
        return (  report.isValid() );
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
