/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
 *  http://exist-db.org
 *
 *  This program stream free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program stream distributed in the hope that it will be useful,
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

import com.thaiopensource.util.PropertyMapBuilder;
import com.thaiopensource.validate.SchemaReader;
import com.thaiopensource.validate.ValidateProperty;
import com.thaiopensource.validate.ValidationDriver;
import com.thaiopensource.validate.rng.CompactSchemaReader;
import org.apache.log4j.Logger;
import org.exist.Namespaces;
import org.exist.storage.BrokerPool;
import org.exist.storage.io.ExistIOException;
import org.exist.util.Configuration;
import org.exist.util.XMLReaderObjectFactory;
import org.exist.validation.resolver.AnyUriResolver;
import org.exist.validation.resolver.SearchResourceResolver;
import org.exist.validation.resolver.eXistXMLCatalogResolver;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.InputStream;

/**
 *  Validate XML documents with their grammars (DTD's and Schemas).
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 */
public class Validator {

    private final static Logger logger = Logger.getLogger(Validator.class);
    private BrokerPool brokerPool = null;
    private GrammarPool grammarPool = null;
    private Configuration config = null;
    private eXistXMLCatalogResolver systemCatalogResolver = null;

    /**
     *  Setup Validator object with brokerpool as db connection.
     * 
     * @param pool Brokerpool
     */
    public Validator(BrokerPool pool) {
        logger.info("Initializing Validator.");

        if(brokerPool == null){
            this.brokerPool = pool;
        }

        // Get configuration
        config = brokerPool.getConfiguration();

        // Check xerces version        
        StringBuilder xmlLibMessage = new StringBuilder();
        if(!XmlLibraryChecker.hasValidParser(xmlLibMessage)){
            logger.error(xmlLibMessage);
        }

        // setup grammar brokerPool
        grammarPool = (GrammarPool) 
                config.getProperty(XMLReaderObjectFactory.GRAMMER_POOL);

        // setup system wide catalog resolver
        systemCatalogResolver = (eXistXMLCatalogResolver) 
                config.getProperty(XMLReaderObjectFactory.CATALOG_RESOLVER);

    }
    
    /**
     *  Validate XML data using system catalog. XSD and DTD only. 
     *
     * @param stream XML input.
     * @return Validation report containing all validation info.
     */
    public ValidationReport validate(InputStream stream) {
        return validate(stream, null);
    }
    
    /**
     *  Validate XML data from reader using specified grammar.
     *
     * @param grammarUrl   User supplied path to grammar.
     * @param stream       XML input.
     * @return Validation report containing all validation info.
     */
    public ValidationReport validate(InputStream stream, String grammarUrl) {

        // repair path to local resource
        if(grammarUrl != null && grammarUrl.startsWith("/")){
            grammarUrl = "xmldb:exist://" + grammarUrl;
        }

        if(grammarUrl != null &&
                (grammarUrl.endsWith(".rng") || grammarUrl.endsWith(".rnc") ||
                grammarUrl.endsWith(".nvdl") || grammarUrl.endsWith(".sch"))){
            // Validate with Jing
            return validateJing(stream, grammarUrl);
            
        } else {
            // Validate with Xerces
            return validateParse(stream, grammarUrl);
        }

    }

    /**
     *  Validate XML data from reader using specified grammar with Jing.
     *
     * @param stream       XML input document.
     * @param grammarUrl   User supplied path to grammar.
     * @return Validation report containing all validation info.
     */
    public ValidationReport validateJing(InputStream stream, String grammarUrl) {

        ValidationReport report = new ValidationReport();
        try {
            report.start();

            // Setup validation properties. see Jing interface
            PropertyMapBuilder properties = new PropertyMapBuilder();
            ValidateProperty.ERROR_HANDLER.put(properties, report);

            // Copied from Jing code ; the Compact syntax seem to have a different
            // Schema reader. To be investigated. http://www.thaiopensource.com/relaxng/api/jing/index.html
            SchemaReader schemaReader = grammarUrl.endsWith(".rnc") ? CompactSchemaReader.getInstance() : null;

            // Setup driver
            ValidationDriver driver = new ValidationDriver(properties.toPropertyMap(), schemaReader);

            // Load schema
            driver.loadSchema(new InputSource(grammarUrl));

            // Validate XML instance
            driver.validate(new InputSource(stream));

        } catch(ExistIOException ex) {
            logger.error(ex.getCause());
            report.setThrowable(ex.getCause());

        } catch(Exception ex) {
            logger.debug(ex);
            report.setThrowable(ex);

        } finally {
            report.stop();
        }
        return report;
    }

    /**
     *  Validate XML data using system catalog. XSD and DTD only.
     *
     * @param stream XML input.
     * @return Validation report containing all validation info.
     */
    public ValidationReport validateParse(InputStream stream) {
        return validateParse(stream, null);
    }

    /**
     *  Validate XML data from reader using specified grammar.
     *
     * @param grammarUrl   User supplied path to grammar.
     * @param stream XML input.
     * @return Validation report containing all validation info.
     */
    public ValidationReport validateParse(InputStream stream, String grammarUrl) {

        logger.debug("Start validation.");

        ValidationReport report = new ValidationReport();
        ValidationContentHandler contenthandler = new ValidationContentHandler();


        try {

            XMLReader xmlReader = getXMLReader(contenthandler, report);

            if(grammarUrl == null){

                // Scenario 1 : no params - use system catalog
                logger.debug("Validation using system catalog.");
                xmlReader.setProperty(XMLReaderObjectFactory.APACHE_PROPERTIES_ENTITYRESOLVER, systemCatalogResolver);

            } else if(grammarUrl.endsWith(".xml")){

                // Scenario 2 : path to catalog (xml)
                logger.debug("Validation using user specified catalog '" + grammarUrl + "'.");
                eXistXMLCatalogResolver resolver = new eXistXMLCatalogResolver();
                resolver.setCatalogList(new String[]{grammarUrl});
                xmlReader.setProperty(XMLReaderObjectFactory.APACHE_PROPERTIES_ENTITYRESOLVER, resolver);

            } else if(grammarUrl.endsWith("/")){

                // Scenario 3 : path to collection ("/"): search.
                logger.debug("Validation using searched grammar, start from '" + grammarUrl + "'.");
                SearchResourceResolver resolver = new SearchResourceResolver(grammarUrl, brokerPool);
                xmlReader.setProperty(XMLReaderObjectFactory.APACHE_PROPERTIES_ENTITYRESOLVER, resolver);

            } else {

                // Scenario 4 : path to grammar (xsd, dtd) specified.
                logger.debug("Validation using specified grammar '" + grammarUrl + "'.");
                AnyUriResolver resolver = new AnyUriResolver(grammarUrl);
                xmlReader.setProperty(XMLReaderObjectFactory.APACHE_PROPERTIES_ENTITYRESOLVER, resolver);
            }

            logger.debug("Validation started.");
            report.start();
            InputSource source = new InputSource(stream);
            xmlReader.parse(source);
            logger.debug("Validation stopped.");

            report.stop();

            report.setNamespaceUri(contenthandler.getNamespaceUri());

            if(!report.isValid()){
                logger.debug("Document is not valid.");
            }

        } catch(ExistIOException ex) {
            logger.error(ex.getCause());
            report.setThrowable(ex.getCause());

        } catch(Exception ex) {
            logger.error(ex);
            report.setThrowable(ex);

        } finally {
            report.stop();

            logger.debug("Validation performed in " + report.getValidationDuration() + " msec.");

        }

        return report;
    }

    private XMLReader getXMLReader(ContentHandler contentHandler,
            ErrorHandler errorHandler) throws ParserConfigurationException, SAXException {

        // setup sax factory ; be sure just one instance!
        SAXParserFactory saxFactory = SAXParserFactory.newInstance();

        // Enable validation stuff
        saxFactory.setValidating(true);
        saxFactory.setNamespaceAware(true);

        // Create xml reader
        SAXParser saxParser = saxFactory.newSAXParser();
        XMLReader xmlReader = saxParser.getXMLReader();

        // Setup xmlreader
        xmlReader.setProperty(XMLReaderObjectFactory.APACHE_PROPERTIES_INTERNAL_GRAMMARPOOL, grammarPool);

        xmlReader.setFeature(Namespaces.SAX_VALIDATION, true);
        xmlReader.setFeature(Namespaces.SAX_VALIDATION_DYNAMIC, false);
        xmlReader.setFeature(XMLReaderObjectFactory.APACHE_FEATURES_VALIDATION_SCHEMA, true);
        xmlReader.setFeature(XMLReaderObjectFactory.APACHE_PROPERTIES_LOAD_EXT_DTD, true);
        xmlReader.setFeature(Namespaces.SAX_NAMESPACES_PREFIXES, true);

        xmlReader.setContentHandler(contentHandler);
        xmlReader.setErrorHandler(errorHandler);

        return xmlReader;
    }
}
