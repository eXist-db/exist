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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.log4j.Logger;
import org.exist.Namespaces;
import org.exist.storage.BrokerPool;
import org.exist.validation.internal.DatabaseResources;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;

/**
 *  Validate XML documents with their grammars (DTD's and Schemas).
 *
 * @author dizzzz
 */
public class Validator {
    
    private final static Logger logger = Logger.getLogger(Validator.class);
    
    // TODO check whether this private static trick is wise to do.
    // These are made static to prevent expensive double initialization
    // of classes.
    private static GrammarPool grammarPool = null;
    private static DatabaseResources dbResources = null;
    private static SAXParserFactory saxFactory = null;
    private static BrokerPool brokerPool ;
    
    // Xerces feature and property names
    final static String FEATURE_SCHEMA
            ="http://apache.org/xml/features/validation/schema";
    final static String PROPERTIES_GRAMMARPOOL
            ="http://apache.org/xml/properties/internal/grammar-pool";
    final static String PROPERTIES_RESOLVER
            ="http://apache.org/xml/properties/internal/entity-resolver";
    final static String PROPERTIES_LOAD_EXT_DTD
            ="http://apache.org/xml/features/nonvalidating/load-external-dtd";

    /**
     *  Setup Validator object with brokerpool as centre.
     */
    public Validator( BrokerPool pool ){
        logger.info("Initializing Validator.");
        
        if(brokerPool==null){
            this.brokerPool = pool;
        }
        
        // Check xerces version        
    	StringBuffer xmlLibMessage = new StringBuffer();
        if(!XmlLibraryChecker.hasValidParser(xmlLibMessage))
        {
      	  logger.error(xmlLibMessage);
        }
        
        
        // setup access to grammars ; be sure just one instance!
        if(dbResources==null){
            dbResources = new DatabaseResources(pool);
        }
        
//        // setup enityResolver ; be sure just one instance!
//        if(enityResolver==null){
//            enityResolver = new EntityResolver(dbResources);
//        }
        
        // setup grammar brokerPool ; be sure just one instance!
        if(grammarPool==null){
            grammarPool = new GrammarPool();
        }
        
        // setup sax factory ; be sure just one instance!
        if(saxFactory==null){
            saxFactory = SAXParserFactory.newInstance();
            
            
            // Enable validation stuff
            saxFactory.setValidating(true);
            saxFactory.setNamespaceAware(true);
            
            try{
                // Enable validation features of xerces
                saxFactory.setFeature(Namespaces.SAX_VALIDATION, true);
                saxFactory.setFeature(Namespaces.SAX_VALIDATION_DYNAMIC, false);
                saxFactory.setFeature(FEATURE_SCHEMA,true);
                saxFactory.setFeature(PROPERTIES_LOAD_EXT_DTD, true);
                saxFactory.setFeature(Namespaces.SAX_NAMESPACES_PREFIXES, true);
                
            } catch (ParserConfigurationException ex){
                logger.error(ex);
                
            } catch (SAXNotRecognizedException ex){
                logger.error(ex);
                
            } catch (SAXNotSupportedException ex){
                logger.error(ex);
                
            }
        }
    }
    
    
    /**
     *  Validate XML data in inputstream.
     *
     * @param is    XML input stream.
     * @return      Validation report containing all validation info.
     */
    public ValidationReport validate(InputStream is) {
        return validate( new InputStreamReader(is) , null );
        
    }
    
    /**
     *  Validate XML data in inputstream.
     *
     * @param is    XML input stream.
     * @return      Validation report containing all validation info.
     */
    public ValidationReport validate(InputStream is, String grammarPath) {
        return validate( new InputStreamReader(is), grammarPath );
        
    }
    
    /**
     *  Validate XML data from reader.
     * @param reader    XML input
     * @return          Validation report containing all validation info.
     */
    public ValidationReport validate(Reader reader) {
        return validate(reader, null);
    }
    
    /**
     *  Validate XML data from reader using specified grammar.
     *
     *  grammar path
     *      null : search all documents starting in /db
     *      /db/doc/ : start search start in specified collection
     *
     *      /db/doc/schema/schema.xsd :start with this schema, no search needed.
     *
     * @return Validation report containing all validation info.
     * @param grammarPath   User supplied path to grammar.
     * @param reader        XML input.
     */
    public ValidationReport validate(Reader reader, String grammarPath) {
        
        logger.debug("Start validation.");
        
        EntityResolver entityResolver = new EntityResolver(dbResources);
        
        if(grammarPath != null){
            entityResolver.setStartGrammarPath(grammarPath);
        }
        
        ValidationReport report = new ValidationReport();
        
        try{
            InputSource source = new InputSource(reader);
            
            SAXParser sax = saxFactory.newSAXParser();
            sax.setProperty(PROPERTIES_GRAMMARPOOL, grammarPool);
            
            XMLReader xmlReader = sax.getXMLReader();
            xmlReader.setProperty(PROPERTIES_RESOLVER, entityResolver);
            xmlReader.setErrorHandler( report );
            
            
            logger.debug("Parse begin.");
            long start = System.currentTimeMillis();
            xmlReader.parse(source);
            long stop = System.currentTimeMillis();
            
            report.setValidationDuration(stop-start);
            logger.debug("Parse end." +
                    "Validation performed in " + (stop-start) + " msec.");
            
            if( ! report.isValid() ){
                logger.debug( "Parse errors \n" + report.toString() )  ;
            }
            
        } catch (IOException ex){
            logger.error(ex);
            report.setException(ex);
            
        } catch (ParserConfigurationException ex){
            logger.error(ex);
            report.setException(ex);
            
        } catch (SAXNotSupportedException ex){
            logger.error(ex);
            report.setException(ex);
            
        } catch (SAXException ex){
            logger.error(ex);
            report.setException(ex);
        }
        
        return report;
    }
    
    /**
     *  Get access to internal DatabaseResources.
     * @return Internally used DatabaseResources.
     */
    public DatabaseResources getDatabaseResources(){
        return dbResources;
    }
    
//    /**
//     *  Get access to internal XMLEntityResolver.
//     * @return Internally used XMLEntityResolver.
//     */
//    public XMLEntityResolver getXMLEntityResolver(){
//        return entityResolver;
//    }
    
    /**
     *  Get access to internal GrammarPool.
     * @return Internally used GrammarPool.
     */
    public GrammarPool getGrammarPool(){
        return grammarPool;
    }
    
    public void setGrammarPool(GrammarPool gp){
        grammarPool = gp;
    }
}
