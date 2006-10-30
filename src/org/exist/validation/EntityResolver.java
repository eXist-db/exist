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
import java.net.URISyntaxException;
import org.exist.validation.internal.DatabaseResources;

import org.apache.log4j.Logger;
import org.apache.xerces.xni.parser.XMLEntityResolver;
import org.apache.xerces.xni.parser.XMLInputSource;
import org.apache.xerces.xni.XMLResourceIdentifier;
import org.apache.xerces.xni.XNIException;
import org.exist.xmldb.XmldbURI;


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
 * @author dizzzz
 * @see org.apache.xerces.xni.parser.XMLEntityResolver
 *
 * NOTES
 * =====
 *
 * - Keep list called grammar id's. For first grammar the base URI must be set.
 *   other grammars must be found relative, unless full path is used.
 * - If not schema but folder is supplied, use this folder as startpoint search
 *   grammar set
 * -
 *
 */
public class EntityResolver  implements XMLEntityResolver {
    
    /* Local logger  */
    private final static Logger logger = Logger.getLogger(EntityResolver.class);
    
    private DatabaseResources databaseResources = null;
    
    private String startGrammarPath="/db";
    
    private boolean isCatalogSpecified = false;
    private boolean isGrammarSpecified = false;
    private boolean isGrammarSearched = false;
    private XmldbURI collection = null;
    private String documentName = null;
    
    /**
     *  Initialize EntityResolver.
     * 
     * @param resources 
     */
    public EntityResolver(DatabaseResources resources) {
        
        logger.info("Initializing EntityResolver");
        this.databaseResources = resources;
        
        try {
            collection = XmldbURI.xmldbUriFor("xmldb:exist:///db");
        } catch (URISyntaxException ex) {
            logger.error(ex);
        }
        
        isGrammarSearched=true;
    }
    
    /**
     *  WHat can be supplied:
     *
     *  - path to collection   (/db/foo/bar/)
     *    In this case all grammars must be searched.
     *    o Grammars can be found using xquery
     *    o DTD's can only be found by finding catalog files.
     *
     *  - path to start schema (/db/foo/bar/special.xsd)
     *    The pointed grammar -if it exist- must be used
     *
     *  - path to catalog file (/db/foo/bar/catalog.xml)
     *
     * @param path  Path tp
     */
    public void setStartGrammarPath(String path){
        
        //TODO : use XmldbURI methods !
        if(path.startsWith("/db")){
            path="xmldb:exist://"+path;
        } else if (path.startsWith("/")) {
            path="xmldb:exist:///db"+path;
        }
        
        // TODO help...
        startGrammarPath=path;
        
        //TODO : read from mime types
        if( path.endsWith(".xml") ){
            // Catalog is specified
            logger.debug("Using catalog '"+path+"'.");
            isCatalogSpecified=true;
            
            try {
                collection= XmldbURI.xmldbUriFor( DatabaseResources.getCollectionPath(path) );
            } catch (URISyntaxException ex) {
                logger.error("Error constructing collection uri of '"+path+"'.", ex);
            }
            
            documentName=DatabaseResources.getDocumentName(path);
            
        } else if ( path.endsWith(".xsd") ||  path.endsWith(".dtd") ){
            // Grammar is specified
            logger.debug("Using grammar '"+path+"'.");
            isGrammarSpecified=true;
            logger.info("cp="+DatabaseResources.getCollectionPath(path));
            try {
                collection= XmldbURI.xmldbUriFor( DatabaseResources.getCollectionPath(path) );
            } catch (URISyntaxException ex) {
                logger.error("Error constructing collection uri of '"+path+"'.", ex);
            }
            
            documentName=DatabaseResources.getDocumentName(path);
            
        } else if ( path.endsWith("/") ){
            // Entity resolver must search for grammars.
            logger.debug("Searching grammars in collection '"+path+"'.");
            isGrammarSearched=true;
            try {
                collection=XmldbURI.xmldbUriFor( DatabaseResources.getCollectionPath(path) );
            } catch (URISyntaxException ex) {
                logger.error("Error constructing collection uri of '"+path+"'.", ex);
            }
            
        } else {
            // Oh oh
            logger.error("No grammar, collection of catalog specified.");
        }
        
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
        
        byte grammar[] = null;
        boolean grammarIsBinary = true;
        
        if(isGrammarSpecified){
            
            /*  Get User specified grammar right away from database.
             *
             * At first entrance "BaseSystemId=null" and "ExpandedSystemId=path"
             * At following entries BaseSystemId contains schema path and
             * ExpandedSystemId contains path new schema
             */
            
            if(xrid.getBaseSystemId()==null){
                // First step
                resourcePath = collection.getCollectionPath()+"/"+documentName;
                
            } else {
                // subsequent steps
                try {
                    resourcePath = XmldbURI.xmldbUriFor(xrid.getExpandedSystemId()).getCollectionPath();
                    
                } catch (URISyntaxException ex) {
                    logger.error(ex);
                }
            }
            
            if(documentName.endsWith(".xsd")){
                grammarIsBinary=false;
            }
            
        } else if(isCatalogSpecified){
            /* Only use data in specified catalog  */
            logger.debug("Resolve using catalog.");
            if( xrid.getNamespace() !=null ){
                
                logger.debug("Resolve schema namespace.");
                resourcePath = databaseResources.getSchemaPathFromCatalog(collection, documentName, xrid.getNamespace());
                
            } else if ( xrid.getPublicId() !=null ){
                
                logger.debug("Resolve dtd publicId.");
                resourcePath = databaseResources.getDtdPathFromCatalog(collection, documentName, xrid.getPublicId());
                
            } else {
                // TODO remove logging for performance?
                logger.error("Can only resolve namespace or publicId.");
                return null;
            }
            
        } else {
            // Search for grammar, Might be 'somewhere' in database.
            logger.debug("Search for grammar.");
            
            if( xrid.getNamespace() !=null ){
                
                /*****************************
                 * XML Schema search
                 *****************************/
                
                logger.debug("Searching namespace '"+xrid.getNamespace()+"'.");
                this.logXMLResourceIdentifier(xrid);
                
                resourcePath = databaseResources.getSchemaPath(collection, xrid.getNamespace());
                grammarIsBinary=false;
                
            } else if ( xrid.getPublicId() !=null ){
                
                /*****************************
                 * DTD search
                 *****************************/
                
                logger.debug("Searching publicId '"+xrid.getPublicId()+"'.");
                this.logXMLResourceIdentifier(xrid);
                
                resourcePath = databaseResources.getDtdPath(collection, xrid.getPublicId());
                grammarIsBinary=true;
                
            } else {
                // Fast escape; no logging, otherwise validation is slow!
                return null;
            }
        }
        
        if(resourcePath == null ){
            logger.debug("Resource not found in database.");
            return null;
        }
        
        // Get grammar from database
        logger.debug("resourcePath="+resourcePath);
        grammar = databaseResources.getResource(resourcePath);
        
        if(grammar == null ){
            logger.debug("Grammar not retrieved from database.");
            return null;
        }
        
        Reader rd = new InputStreamReader( new ByteArrayInputStream(grammar) ) ;
        
        // TODO check ; is all information filled incorrect?
        logger.info("publicId="+xrid.getPublicId()
        +" systemId="+"xmldb:exist://"+resourcePath
                + " baseSystemId="+xrid.getBaseSystemId());
        
        xis = new XMLInputSource(
                xrid.getPublicId(),            // publicId
                "xmldb:exist://"+resourcePath, // systemId
                xrid.getBaseSystemId(),        // baseSystemId
                rd ,
                "UTF-8" );
        
        return xis;
    }
    
    private void logXMLResourceIdentifier(XMLResourceIdentifier xrid){
        logger.info( "PublicId="+xrid.getPublicId() );
        logger.info( "BaseSystemId="+xrid.getBaseSystemId() );
        logger.info( "ExpandedSystemId="+xrid.getExpandedSystemId() );
        logger.info( "LiteralSystemId="+xrid.getLiteralSystemId() );
        logger.info( "Namespace="+xrid.getNamespace() );
    }
    
}
