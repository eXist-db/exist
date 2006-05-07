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

import java.io.InputStream;
import java.net.URISyntaxException;

import org.apache.log4j.Logger;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.validation.ValidationReport;
import org.exist.validation.Validator;
import org.exist.validation.internal.DatabaseResources;
import org.exist.validation.internal.ResourceInputStream;
import org.exist.xmldb.LocalCollection;
import org.exist.xmldb.XmldbURI;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;



/**
 *  XML validation service for LocalMode of eXist database.
 *
 * @author dizzzz
 */
public class LocalValidationService implements ValidationService {
    
    private static Logger logger = Logger.getLogger(LocalValidationService.class);
    
    private BrokerPool brokerPool ;
    private User user;
    private LocalCollection localCollection;
    private Validator validator;
    private DatabaseResources grammaraccess;
    
    
    public LocalValidationService(User user, BrokerPool pool, LocalCollection collection) {
        logger.info("Starting LocalValidationService");
        this.user = user;
        this.brokerPool = pool;
        this.localCollection = collection;
        validator = new Validator(pool);
        grammaraccess = validator.getDatabaseResources();
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
        
        logger.info("Validating resource '"+id+"'");
        
        // Write resource contents into stream, using Thread
        InputStream is = new ResourceInputStream(brokerPool, id);
        
        if(is==null){
            logger.error("resource not found");
        }
        
        // Perform validation
        ValidationReport report = validator.validate(is);
        
        // Return validation result
        logger.info("Validation done.");
        return (  report.isValid() );
    }
    
//    /**
//     * Validates a resource given its contents
//     */
//    public boolean validateContents(String contents) throws XMLDBException {
//        Reader rd = new StringReader(contents);
//        ValidationReport report = validator.validate(rd);
//        return !( report.hasErrors() || report.hasWarnings() );
//    }
    
    
    
//    /**
//     * find the whole schema as an XMLResource
//     */
//    public XMLResource getSchema(String targetNamespace) throws XMLDBException {
//        String path = grammaraccess.getGrammarPath(DatabaseResources.GRAMMAR_XSD , targetNamespace);
//        grammaraccess.getGrammar(DatabaseResources.GRAMMAR_XSD, path);
//        return null;
//    }
    
//    /**
//     *  Is a schema defining this namespace/id known
//     * @param namespaceURI
//     * @return
//     * @throws XMLDBException
//     */
//    public boolean isKnownNamespace(String namespaceURI) throws XMLDBException {
//        return grammaraccess.hasGrammar(DatabaseResources.GRAMMAR_XSD, namespaceURI);
//    }
    
//    /**
//     * Stores a new schema given its contents
//     */
//    public void putSchema(String schemaContents) throws XMLDBException {
//        //
//    }
//
    
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
