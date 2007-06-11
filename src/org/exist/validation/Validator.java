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
package org.exist.validation;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.log4j.Logger;

import org.exist.Namespaces;
import org.exist.storage.BrokerPool;
import org.exist.storage.io.ExistIOException;
import org.exist.util.Configuration;
import org.exist.util.XMLReaderObjectFactory;
import org.exist.validation.resolver.SearchResourceResolver;
import org.exist.validation.resolver.StoredResourceResolver;
import org.exist.validation.resolver.eXistXMLCatalogResolver;

import org.xml.sax.InputSource;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;

/**
 *  Validate XML documents with their grammars (DTD's and Schemas).
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 */
public class Validator {
    
    private final static Logger logger = Logger.getLogger(Validator.class);
    
    // TODO check whether this private static trick is wise to do.
    // These are made static to prevent expensive double initialization
    // of classes.
    private static SAXParserFactory saxFactory = null;

    private BrokerPool brokerPool = null;
    private GrammarPool grammarPool = null;
    private Configuration config = null;
    

    /**
     *  Setup Validator object with brokerpool as centre.
     */
    public Validator( BrokerPool pool ){
        logger.info("Initializing Validator.");
        
        if(brokerPool==null){
            this.brokerPool = pool;
        }
        
        // Get configuration
        config = brokerPool.getConfiguration();
        
        // Check xerces version        
        StringBuffer xmlLibMessage = new StringBuffer();
        if(!XmlLibraryChecker.hasValidParser(xmlLibMessage)) {
            logger.error(xmlLibMessage);
        }
        
        // setup grammar brokerPool
       grammarPool 
           = (GrammarPool) config.getProperty(XMLReaderObjectFactory.GRAMMER_POOL);
        
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
                saxFactory.setFeature(XMLReaderObjectFactory.FEATURE_SCHEMA,true);
                saxFactory.setFeature(XMLReaderObjectFactory.PROPERTIES_LOAD_EXT_DTD, true);
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
        
        ValidationReport report = new ValidationReport();
        
        try{
            InputSource source = new InputSource(reader);
            
            SAXParser sax = saxFactory.newSAXParser();
            sax.setProperty(XMLReaderObjectFactory.PROPERTIES_GRAMMARPOOL, grammarPool);
            
            XMLReader xmlReader = sax.getXMLReader();
            
            // repair path to local resource
            if(grammarPath!=null && grammarPath.startsWith("/")){
                grammarPath="xmldb:exist://"+grammarPath;
            }

            if(grammarPath==null){
                // Scenario 1 : no params - use system catalog
                logger.debug("Validation using system catalog.");
                eXistXMLCatalogResolver resolver = 
                    (eXistXMLCatalogResolver) config.getProperty(XMLReaderObjectFactory.CATALOG_RESOLVER);
                xmlReader.setProperty(XMLReaderObjectFactory.PROPERTIES_RESOLVER, resolver);
                
            } else if(grammarPath.endsWith(".xml")){
                // Scenario 2 : path to catalog (xml)
                logger.debug("Validation using user specified catalog '"+grammarPath+"'.");
                eXistXMLCatalogResolver resolver = new eXistXMLCatalogResolver();
                resolver.setCatalogList(new String[]{grammarPath});
                xmlReader.setProperty(XMLReaderObjectFactory.PROPERTIES_RESOLVER, resolver);
                
            } else if(grammarPath.endsWith("/")){
                // Scenario 3 : path to collection ("/"): search.
                logger.debug("Validation using searched grammar, start from '"+grammarPath+"'.");
                SearchResourceResolver resolver = new SearchResourceResolver(grammarPath, brokerPool);
                xmlReader.setProperty(XMLReaderObjectFactory.PROPERTIES_RESOLVER, resolver);
                
            } else {
                // Scenario 4 : path to grammar (xsd, dtd) specified.
                logger.debug("Validation using specified grammar '"+grammarPath+"'.");
                
                // Just find the document using empty resolver
                //eXistXMLCatalogResolver resolver = new eXistXMLCatalogResolver();
                StoredResourceResolver resolver = new StoredResourceResolver(grammarPath);
                xmlReader.setProperty(XMLReaderObjectFactory.PROPERTIES_RESOLVER, resolver);

            }

            xmlReader.setErrorHandler( report );
            
            logger.debug("Validation started.");
            report.start();
            xmlReader.parse(source);
            
            logger.debug("Validation stopped.");
            report.stop();
            
            if( ! report.isValid() ){
                logger.debug( "Parse errors \n" + report.toString() )  ;
            }
            
        } catch(ExistIOException ex){
            logger.error(ex.getCause());
            report.setThrowable(ex.getCause());
            
        } catch (Exception ex){
            logger.error(ex);
            report.setThrowable(ex);

        } finally {
            report.stop();
            
            logger.debug("Validation performed in " 
                + report.getValidationDuration() + " msec.");

        }
        
        return report;
    }

}
