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

package org.exist.validation;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

import org.exist.storage.BrokerPool;
import org.exist.validation.internal.DatabaseResources;

import org.apache.log4j.Logger;
import org.apache.xerces.xni.parser.XMLEntityResolver;
import org.apache.xerces.xni.parser.XMLInputSource;
import org.apache.xerces.xni.XMLResourceIdentifier;
import org.apache.xerces.xni.XNIException;


/**
 *  Specific grammar resolver for eXist. Currently supports XSD and DTD.
 *
 * XML Schemas and DTD grammars are stored in collections of the database:
 *  /db/system/grammar/xsd
 *  /db/system/grammar/dtd
 *
 * The XSD's are resolved automatically using xQuery. For DTD's (hey this is
 * ancient stuff, these are no xml documents) separate data management is
 * required. The details are stored in
 *
 *  /db/system/grammar/dtd/catalog/xml
 *
 * Extra bonus: an xQuery generating a catalogus with DTD's and XSD's
 *
 *  /db/system/grammar/xq/catalog.xq
 *
 * RelaxNG support can be added later.
 *
 * @author dizzzz
 * @see org.apache.xerces.xni.parser.XMLEntityResolver
 */
public class EntityResolver  implements XMLEntityResolver {
    
    /* Local logger  */
    private final static Logger logger = Logger.getLogger(EntityResolver.class);
    
    private BrokerPool pool = null;
    private DatabaseResources databaseResources = null;
        
    /**
     *  Initialize EntityResolver.
     * @param pool  BrokerPool
     */
    public EntityResolver(DatabaseResources resources) {
        
        logger.info("Initializing EntityResolver");
        this.databaseResources = resources;
    }
    
    /**
     *  Resolve GRAMMAR specified with this GRAMMAR id
     *
     * @param  xrid             Grammar Identifier.
     * @throws XNIException     Xerces exception, can be anything
     * @throws IOException      Can be anything
     * @return Inputsource containing grammar.
     */
    public XMLInputSource resolveEntity(XMLResourceIdentifier xrid)  throws XNIException, IOException {
        
        XMLInputSource xis=null;
        String resourcePath = null;
        Reader rd=null;

        int type=0;
        
        if( xrid.getNamespace() !=null ){
            logger.debug("Resolving namespace '"+xrid.getNamespace()+"'.");
            type=DatabaseResources.GRAMMAR_XSD;
            resourcePath = databaseResources.getGrammarPath(type, xrid.getNamespace() );
            
            this.logXMLResourceIdentifier(xrid);
            
        } else if ( xrid.getPublicId() !=null ){
            logger.debug("Resolving publicId '"+xrid.getPublicId()+"'.");
            type=DatabaseResources.GRAMMAR_DTD;
            resourcePath =  databaseResources.DTDBASE+"/"+ databaseResources.getGrammarPath(type,  xrid.getPublicId() );
            
            // Fix, remove leading path
            if(resourcePath.endsWith(DatabaseResources.NOGRAMMAR)){
                resourcePath=DatabaseResources.NOGRAMMAR;
            }
            
            // TODO: remove this log statement
            this.logXMLResourceIdentifier(xrid);
            
        } else {
            // Fast escape; no logging, otherwise validation is slow!
            return null;
           
        }
        
        // TODO: if resourcepath = null then default resolver must be checked.
        
                
        if(resourcePath == null || resourcePath.equals("NONE") ){
            logger.debug("Resource not found in database.");
            return null;
        }
        logger.debug("resourcePath="+resourcePath);
        
        // TODO make this streaming, fortunately the grammarfiles are small.
        // Get grammar from database
        rd = new InputStreamReader(
                new ByteArrayInputStream( databaseResources.getGrammar(type, resourcePath) ) ) ;
        
        if(rd==null){
            logger.debug("Grammar not found.");
            return null;
        }
        
        xis = new XMLInputSource( xrid.getPublicId(), xrid.getExpandedSystemId(),
                                  xrid.getBaseSystemId(), rd , "UTF-8");   
        return xis;
    }    
    
    private void logXMLResourceIdentifier(XMLResourceIdentifier xrid){
        logger.info( "getPublicId="+xrid.getPublicId() );
        logger.info( "getBaseSystemId="+xrid.getBaseSystemId() );
        logger.info( "getExpandedSystemId="+xrid.getExpandedSystemId() );
        logger.info( "getLiteralSystemId="+xrid.getLiteralSystemId() );
        logger.info( "getNamespace="+xrid.getNamespace() );
        logger.info( xrid.toString() );
        
    }
}
