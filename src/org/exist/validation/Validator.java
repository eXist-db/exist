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
import org.apache.xerces.xni.parser.XMLEntityResolver;

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
    private static XMLEntityResolver enityResolver = null;;
    private static GrammarPool grammarPool = null;
    private static DatabaseResources dbResources = null;
    private static SAXParserFactory saxFactory = null;
    private static BrokerPool brokerPool ;
    
    // Xerces feature and property names
    final static String FEATURE_DYNAMIC
            ="http://apache.org/xml/features/validation/dynamic";
    final static String FEATURE_SCHEMA
            ="http://apache.org/xml/features/validation/schema";
    final static String PROPERTIES_GRAMMARPOOL
            ="http://apache.org/xml/properties/internal/grammar-pool";
    final static String PROPERTIES_RESOLVER
            ="http://apache.org/xml/properties/internal/entity-resolver";
    
    /**
     *  Setup Validator object with brokerpool as centre.
     */
    public Validator( BrokerPool pool ){
        logger.info("Initializing Validator");
        
        if(brokerPool==null){
            this.brokerPool = pool;
        }
        
        
        // setup access to grammars ; be sure just one instance!
        if(dbResources==null){
            dbResources = new DatabaseResources(pool);
        }
        
        // setup enityResolver ; be sure just one instance!
        if(enityResolver==null){
            enityResolver = new EntityResolver(dbResources);
        }
        
        // setup grammar brokerPool ; be sure just one instance!
        if(grammarPool==null){
            grammarPool = new GrammarPool();
        }
        
        // setup sax factory ; be sure just one instance!
        if(saxFactory==null){
            saxFactory = SAXParserFactory.newInstance();
        }
        
        // Enable validation stuff
        saxFactory.setValidating(true);
        saxFactory.setNamespaceAware(true);
        
        try{
            // Enable validation features of xerces
            saxFactory.setFeature(FEATURE_DYNAMIC, true);
            saxFactory.setFeature(FEATURE_SCHEMA,true);
            
        } catch (ParserConfigurationException ex){
            logger.error(ex);
            
        } catch (SAXNotRecognizedException ex){
            logger.error(ex);
            
        } catch (SAXNotSupportedException ex){
            logger.error(ex);
            
        }
    }
    
    
    /**
     *  Validate XML data in inputstream.
     *
     * @param is    XML input stream.
     * @return      Validation report containing all validation info.
     */
    public ValidationReport validate(InputStream is) {
        return validate( new InputStreamReader(is)  );
        
    }
    
    
    /**
     *  Validate XML data from reader.
     * @param reader    XML input
     * @return          Validation report containing all validation info.
     */
    public ValidationReport validate(Reader reader) {
        
        logger.debug("Start validation");
        long start = System.currentTimeMillis();
        
        ValidationReport report = new ValidationReport();
        
        try{
            InputSource source = new InputSource(reader);
            
            SAXParser sax = saxFactory.newSAXParser();
            sax.setProperty(PROPERTIES_GRAMMARPOOL, grammarPool);
            
            XMLReader xmlReader = sax.getXMLReader();
            xmlReader.setProperty(PROPERTIES_RESOLVER, enityResolver);
            xmlReader.setErrorHandler( report );
            
            
            logger.debug("Parse begin");
            xmlReader.parse(source);
            logger.debug("Parse end");
            
            if( report.hasErrors() ){
                logger.debug( "Parse errors \n" + report.getErrorReport() )  ;
            }
            
            if( report.hasWarnings() ){
                logger.debug( "Parse warnings \n" + report.getWarningReport() )  ;
            }
            
        } catch (IOException ex){
            logger.error(ex);
            
        } catch (ParserConfigurationException ex){
            logger.error(ex);
            
        } catch (SAXNotSupportedException ex){
            logger.error(ex);
            
        } catch (SAXException ex){
            logger.error(ex);
        }
        
        logger.debug("Validation performed in " + (System.currentTimeMillis()-start) + " msec.");
        return report;
    }
    
    /**
     *  Get access to internal DatabaseResources.
     * @return Internally used DatabaseResources.
     */
    public DatabaseResources getDatabaseResources(){
        return dbResources;
    }
    
    /**
     *  Get access to internal XMLEntityResolver.
     * @return Internally used XMLEntityResolver.
     */
    public XMLEntityResolver getXMLEntityResolver(){
        return enityResolver;
    }
    
    /**
     *  Get access to internal GrammarPool.
     * @return Internally used GrammarPool.
     */
    public GrammarPool getGrammarPool(){
        return grammarPool;
    }
}
