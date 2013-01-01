/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009-2010 The eXist Project
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
package org.exist.util;

import org.apache.log4j.Logger;

import org.exist.repo.Deployment;
import org.quartz.SimpleTrigger;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;

import org.exist.Indexer;
import org.exist.indexing.IndexManager;
import org.exist.memtree.SAXAdapter;
import org.exist.protocolhandler.eXistURLStreamHandlerFactory;
import org.exist.scheduler.JobException;
import org.exist.scheduler.Scheduler;
import org.exist.security.internal.RealmImpl;
import org.exist.security.xacml.XACMLConstants;
import org.exist.storage.BrokerFactory;
import org.exist.storage.BrokerPool;
import org.exist.storage.CollectionCacheManager;
import org.exist.storage.DBBroker;
import org.exist.storage.DefaultCacheManager;
import org.exist.storage.IndexSpec;
import org.exist.storage.NativeBroker;
import org.exist.storage.NativeValueIndex;
import org.exist.storage.TextSearchEngine;
import org.exist.storage.XQueryPool;
import org.exist.storage.journal.Journal;
import org.exist.storage.serializers.CustomMatchListenerFactory;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionManager;
import org.exist.validation.GrammarPool;
import org.exist.validation.resolver.eXistXMLCatalogResolver;
import org.exist.xmldb.DatabaseImpl;
import org.exist.xquery.FunctionFactory;
import org.exist.xquery.PerformanceStats;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.XQueryWatchDog;
import org.exist.xslt.TransformerFactoryAllocator;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import java.net.MalformedURLException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.exist.Namespaces;
import org.exist.xquery.Module;


public class Configuration implements ErrorHandler
{
    private final static Logger       LOG            = Logger.getLogger( Configuration.class ); //Logger
    protected String                  configFilePath = null;
    protected File                    existHome      = null;

    protected DocumentBuilder         builder        = null;
    protected HashMap<String, Object> config         = new HashMap<String, Object>(); //Configuration

    private static final String XQUERY_CONFIGURATION_ELEMENT_NAME = "xquery";
    private static final String XQUERY_BUILTIN_MODULES_CONFIGURATION_MODULES_ELEMENT_NAME = "builtin-modules";
    private static final String XQUERY_BUILTIN_MODULES_CONFIGURATION_MODULE_ELEMENT_NAME = "module";

    public final static String BINARY_CACHE_CLASS_PROPERTY = "binary.cache.class";
    
    public Configuration() throws DatabaseConfigurationException
    {
        this( DatabaseImpl.CONF_XML, null );
    }


    public Configuration( String configFilename ) throws DatabaseConfigurationException
    {
        this( configFilename, null );
    }


    public Configuration( String configFilename, String existHomeDirname ) throws DatabaseConfigurationException
    {
        try {
            InputStream is = null;

            if( configFilename == null ) {

                // Default file name
                configFilename = DatabaseImpl.CONF_XML;
            }

            // firstly, try to read the configuration from a file within the
            // classpath
            try {
                is = Configuration.class.getClassLoader().getResourceAsStream( configFilename );

                if( is != null ) {
                    LOG.info( "Reading configuration from classloader" );
                }
            }
            catch( Exception e ) {

                // EB: ignore and go forward, e.g. in case there is an absolute
                // file name for configFileName
                LOG.debug( e );
            }

            // otherwise, secondly try to read configuration from file. Guess the
            // location if necessary
            if( is == null ) {
                existHome = ( existHomeDirname != null ) ? new File( existHomeDirname ) : ConfigurationHelper.getExistHome( configFilename );

                if( existHome == null ) {

                    // EB: try to create existHome based on location of config file
                    // when config file points to absolute file location
                    File absoluteConfigFile = new File( configFilename );

                    if( absoluteConfigFile.isAbsolute() && absoluteConfigFile.exists() && absoluteConfigFile.canRead() ) {
                        existHome      = absoluteConfigFile.getParentFile();
                        configFilename = absoluteConfigFile.getName();
                    }
                }
                File configFile = new File( configFilename );

                if( !configFile.isAbsolute() && ( existHome != null ) ) {

                    // try the passed or constructed existHome first
                    configFile = new File( existHome, configFilename );
                }

                //if( configFile == null ) {
                //    configFile = ConfigurationHelper.lookup( configFilename );
                //}

                if( !configFile.exists() || !configFile.canRead() ) {
                    throw( new DatabaseConfigurationException( "Unable to read configuration file at " + configFile ) );
                }
                configFilePath   = configFile.getAbsolutePath();
                is               = new FileInputStream( configFile );

                // set dbHome to parent of the conf file found, to resolve relative
                // path from conf file
                existHomeDirname = configFile.getParentFile().getCanonicalPath();
                LOG.info( "Reading configuration from file " + configFile );
            }


            // initialize xml parser
            // we use eXist's in-memory DOM implementation to work
            // around a bug in Xerces
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware( true );

//            factory.setFeature("http://apache.org/xml/features/validation/schema", true);
//            factory.setFeature("http://apache.org/xml/features/validation/dynamic", true);
            InputSource src     = new InputSource( is );
            SAXParser   parser  = factory.newSAXParser();
            XMLReader   reader  = parser.getXMLReader();
            SAXAdapter  adapter = new SAXAdapter();
            reader.setContentHandler( adapter );
            reader.parse( src );

            Document doc      = adapter.getDocument();

            //indexer settings
            NodeList indexers = doc.getElementsByTagName( Indexer.CONFIGURATION_ELEMENT_NAME );

            if( indexers.getLength() > 0 ) {
                configureIndexer( existHomeDirname, doc, (Element)indexers.item( 0 ) );
            }

            //scheduler settings
            NodeList schedulers = doc.getElementsByTagName( Scheduler.CONFIGURATION_ELEMENT_NAME );

            if( schedulers.getLength() > 0 ) {
                configureScheduler( (Element)schedulers.item( 0 ) );
            }

            //db connection settings
            NodeList dbcon = doc.getElementsByTagName( BrokerPool.CONFIGURATION_CONNECTION_ELEMENT_NAME );

            if( dbcon.getLength() > 0 ) {
                configureBackend( existHomeDirname, (Element)dbcon.item( 0 ) );
            }

            NodeList repository = doc.getElementsByTagName("repository");
            if (repository.getLength() > 0) {
                configureRepository((Element) repository.item(0));
            }

            NodeList binaryManager = doc.getElementsByTagName("binary-manager");
            if(binaryManager.getLength() > 0) {
                configureBinaryManager((Element)binaryManager.item(0));
            }
            
            //transformer settings
            NodeList transformers = doc.getElementsByTagName( TransformerFactoryAllocator.CONFIGURATION_ELEMENT_NAME );

            if( transformers.getLength() > 0 ) {
                configureTransformer( (Element)transformers.item( 0 ) );
            }

            //serializer settings
            NodeList serializers = doc.getElementsByTagName( Serializer.CONFIGURATION_ELEMENT_NAME );

            if( serializers.getLength() > 0 ) {
                configureSerializer( (Element)serializers.item( 0 ) );
            }

            //XUpdate settings
            NodeList xupdates = doc.getElementsByTagName( DBBroker.CONFIGURATION_ELEMENT_NAME );

            if( xupdates.getLength() > 0 ) {
                configureXUpdate( (Element)xupdates.item( 0 ) );
            }

            //XQuery settings
            NodeList xquery = doc.getElementsByTagName(XQUERY_CONFIGURATION_ELEMENT_NAME );

            if( xquery.getLength() > 0 ) {
                configureXQuery( (Element)xquery.item( 0 ) );
            }

            //XACML settings
            NodeList xacml = doc.getElementsByTagName( XACMLConstants.CONFIGURATION_ELEMENT_NAME );

            //TODO : check that we have only one element
            if( xacml.getLength() > 0 ) {
                configureXACML( (Element)xacml.item( 0 ) );
            }

            //Validation
            NodeList validations = doc.getElementsByTagName( XMLReaderObjectFactory.CONFIGURATION_ELEMENT_NAME );

            if( validations.getLength() > 0 ) {
                configureValidation( existHomeDirname, doc, (Element)validations.item( 0 ) );
            }

        }
        catch( SAXException e ) {
            LOG.warn( "error while reading config file: " + configFilename, e );
            throw( new DatabaseConfigurationException( e.getMessage() ) );
        }
        catch( ParserConfigurationException cfg ) {
            LOG.warn( "error while reading config file: " + configFilename, cfg );
            throw( new DatabaseConfigurationException( cfg.getMessage() ) );
        }
        catch( IOException io ) {
            LOG.warn( "error while reading config file: " + configFilename, io );
            throw( new DatabaseConfigurationException( io.getMessage() ) );
        }
    }

    private void configureRepository(Element element) {
        String root = element.getAttribute("root");
        if (root != null && root.length() > 0) {
            if (!root.endsWith("/"))
                root += "/";
            config.put(Deployment.PROPERTY_APP_ROOT, root);
        }
    }


    private void configureBinaryManager(Element binaryManager) throws DatabaseConfigurationException {
        final NodeList nlCache = binaryManager.getElementsByTagName("cache");
        if(nlCache.getLength() > 0) {
            final Element cache = (Element)nlCache.item(0);
            final String binaryCacheClass = cache.getAttribute("class");
            config.put(BINARY_CACHE_CLASS_PROPERTY, binaryCacheClass);
            LOG.debug(BINARY_CACHE_CLASS_PROPERTY + ": " + config.get(BINARY_CACHE_CLASS_PROPERTY));
        }
    }
    
    private void configureXQuery( Element xquery ) throws DatabaseConfigurationException
    {
        //java binding
        String javabinding = getConfigAttributeValue( xquery, FunctionFactory.ENABLE_JAVA_BINDING_ATTRIBUTE );

        if( javabinding != null ) {
            config.put( FunctionFactory.PROPERTY_ENABLE_JAVA_BINDING, javabinding );
            LOG.debug( FunctionFactory.PROPERTY_ENABLE_JAVA_BINDING + ": " + config.get( FunctionFactory.PROPERTY_ENABLE_JAVA_BINDING ) );
        }

        String disableDeprecated = getConfigAttributeValue( xquery, FunctionFactory.DISABLE_DEPRECATED_FUNCTIONS_ATTRIBUTE );
        config.put( FunctionFactory.PROPERTY_DISABLE_DEPRECATED_FUNCTIONS, Configuration.parseBoolean( disableDeprecated, FunctionFactory.DISABLE_DEPRECATED_FUNCTIONS_BY_DEFAULT ) );
        LOG.debug( FunctionFactory.PROPERTY_DISABLE_DEPRECATED_FUNCTIONS + ": " + config.get( FunctionFactory.PROPERTY_DISABLE_DEPRECATED_FUNCTIONS ) );

        String optimize = getConfigAttributeValue( xquery, XQueryContext.ENABLE_QUERY_REWRITING_ATTRIBUTE );

        if( ( optimize != null ) && ( optimize.length() > 0 ) ) {
            config.put( XQueryContext.PROPERTY_ENABLE_QUERY_REWRITING, optimize );
            LOG.debug( XQueryContext.PROPERTY_ENABLE_QUERY_REWRITING + ": " + config.get( XQueryContext.PROPERTY_ENABLE_QUERY_REWRITING ) );
        }

        String enforceIndexUse = getConfigAttributeValue( xquery, XQueryContext.ENFORCE_INDEX_USE_ATTRIBUTE );
        if (enforceIndexUse != null) {
        	config.put( XQueryContext.PROPERTY_ENFORCE_INDEX_USE, enforceIndexUse );
        }
        
        String backwardCompatible = getConfigAttributeValue( xquery, XQueryContext.XQUERY_BACKWARD_COMPATIBLE_ATTRIBUTE );

        if( ( backwardCompatible != null ) && ( backwardCompatible.length() > 0 ) ) {
            config.put( XQueryContext.PROPERTY_XQUERY_BACKWARD_COMPATIBLE, backwardCompatible );
            LOG.debug( XQueryContext.PROPERTY_XQUERY_BACKWARD_COMPATIBLE + ": " + config.get( XQueryContext.PROPERTY_XQUERY_BACKWARD_COMPATIBLE ) );
        }

        String raiseErrorOnFailedRetrieval = getConfigAttributeValue( xquery, XQueryContext.XQUERY_RAISE_ERROR_ON_FAILED_RETRIEVAL_ATTRIBUTE );
        config.put( XQueryContext.PROPERTY_XQUERY_RAISE_ERROR_ON_FAILED_RETRIEVAL, Configuration.parseBoolean( raiseErrorOnFailedRetrieval, XQueryContext.XQUERY_RAISE_ERROR_ON_FAILED_RETRIEVAL_DEFAULT ) );
        LOG.debug( XQueryContext.PROPERTY_XQUERY_RAISE_ERROR_ON_FAILED_RETRIEVAL + ": " + config.get( XQueryContext.PROPERTY_XQUERY_RAISE_ERROR_ON_FAILED_RETRIEVAL ) );

        String trace = getConfigAttributeValue( xquery, PerformanceStats.CONFIG_ATTR_TRACE );
        config.put( PerformanceStats.CONFIG_PROPERTY_TRACE, trace );

        // built-in-modules
        Map<String, Class<?>> classMap      = new HashMap<String, Class<?>>();
        Map<String, String>   knownMappings = new HashMap<String, String>();
        Map<String, Map<String, List<? extends Object>>> moduleParameters = new HashMap<String, Map<String, List<? extends Object>>>();
        loadModuleClasses(xquery, classMap, knownMappings, moduleParameters);
        config.put( XQueryContext.PROPERTY_BUILT_IN_MODULES, classMap);
        config.put( XQueryContext.PROPERTY_STATIC_MODULE_MAP, knownMappings);
        config.put( XQueryContext.PROPERTY_MODULE_PARAMETERS, moduleParameters);
    }

    /**
     * Read list of built-in modules from the configuration. This method will only make sure
     * that the specified module class exists and is a subclass of {@link org.exist.xquery.Module}.
     *
     * @param   xquery            configuration root
     * @param   modulesClassMap   map containing all classes of modules
     * @param   modulesSourceMap  map containing all source uris to external resources
     *
     * @throws  DatabaseConfigurationException
     */
    private void loadModuleClasses( Element xquery, Map<String, Class<?>> modulesClassMap, Map<String, String> modulesSourceMap, Map<String, Map<String, List<? extends Object>>> moduleParameters) throws DatabaseConfigurationException {
        // add the standard function module
        modulesClassMap.put(Namespaces.XPATH_FUNCTIONS_NS, org.exist.xquery.functions.fn.FnModule.class);

        // add other modules specified in configuration
        NodeList builtins = xquery.getElementsByTagName(XQUERY_BUILTIN_MODULES_CONFIGURATION_MODULES_ELEMENT_NAME);

        // search under <builtin-modules>
        if(builtins.getLength() > 0) {
            Element  elem    = (Element)builtins.item(0);
            NodeList modules = elem.getElementsByTagName(XQUERY_BUILTIN_MODULES_CONFIGURATION_MODULE_ELEMENT_NAME);

            if(modules.getLength() > 0) {

                // iterate over all <module src= uri= class=> entries
                for(int i = 0; i < modules.getLength(); i++) {

                    // Get element.
                    elem = (Element)modules.item(i);

                    // Get attributes uri class and src
                    String uri    = elem.getAttribute(XQueryContext.BUILT_IN_MODULE_URI_ATTRIBUTE);
                    String clazz  = elem.getAttribute(XQueryContext.BUILT_IN_MODULE_CLASS_ATTRIBUTE);
                    String source = elem.getAttribute(XQueryContext.BUILT_IN_MODULE_SOURCE_ATTRIBUTE);

                    // uri attribute is the identifier and is always required
                    if(uri == null) {
                        throw(new DatabaseConfigurationException("element 'module' requires an attribute 'uri'" ));
                    }

                    // either class or source attribute must be present
                    if((clazz == null) && (source == null)) {
                        throw(new DatabaseConfigurationException("element 'module' requires either an attribute " + "'class' or 'src'" ));
                    }

                    if(source != null) {
                        // Store src attribute info

                        modulesSourceMap.put(uri, source);

                        if(LOG.isDebugEnabled()) {
                            LOG.debug( "Registered mapping for module '" + uri + "' to '" + source + "'");
                        }

                    } else {
                        // source class attribute info

                        // Get class of module
                        Class<?> moduleClass = lookupModuleClass(uri, clazz);

                        // Store class if thw module class actually exists
                        if( moduleClass != null) {
                            modulesClassMap.put(uri, moduleClass);
                        }

                        if(LOG.isDebugEnabled()) {
                            LOG.debug("Configured module '" + uri + "' implemented in '" + clazz + "'");
                        }
                    }

                    //parse any module parameters
                    moduleParameters.put(uri, ParametersExtractor.extract(elem.getElementsByTagName(ParametersExtractor.PARAMETER_ELEMENT_NAME)));
                }
            }
        }
    }

    /**
     *  Returns the Class object associated with the with the given module class name. All
     * important exceptions are caught. @see org.exist.xquery.Module
     *
     * @param uri   namespace of class. For logging purposes only.
     * @param clazz the fully qualified name of the desired module class.
     * @return      the module Class object for the module with the specified name.
     * @throws      DatabaseConfigurationException if the given module class is not an instance
     *              of org.exist.xquery.Module
     */
    private Class<?> lookupModuleClass(String uri, String clazz) throws DatabaseConfigurationException
    {
        Class<?> mClass = null;

        try {
            mClass = Class.forName( clazz );

            if( !( Module.class.isAssignableFrom( mClass ) ) ) {
                throw( new DatabaseConfigurationException( "Failed to load module: " + uri
                        + ". Class " + clazz + " is not an instance of org.exist.xquery.Module." ) );
            }

        } catch( ClassNotFoundException e ) {

            // Note: can't throw an exception here since this would create
            // problems with test cases and jar dependencies
            LOG.error( "Configuration problem: class not found for module '" + uri
                    + "' (ClassNotFoundException); class:'" + clazz 
                    + "'; message:'" + e.getMessage() + "'");

        } catch( NoClassDefFoundError e ) {
            LOG.error( "Module " + uri + " could not be initialized due to a missing "
                    + "dependancy (NoClassDefFoundError): " + e.getMessage(), e );
        }

        return mClass;
    }


    public void configureXACML( Element xacml )
    {
        String enable = getConfigAttributeValue( xacml, XACMLConstants.ENABLE_XACML_ATTRIBUTE );
        config.put( XACMLConstants.ENABLE_XACML_PROPERTY, Configuration.parseBoolean( enable, XACMLConstants.ENABLE_XACML_BY_DEFAULT ) );
        LOG.debug( XACMLConstants.ENABLE_XACML_PROPERTY + ": " + config.get( XACMLConstants.ENABLE_XACML_PROPERTY ) );

        String loadDefaults = getConfigAttributeValue( xacml, XACMLConstants.LOAD_DEFAULT_POLICIES_ATTRIBUTE );
        config.put( XACMLConstants.LOAD_DEFAULT_POLICIES_PROPERTY, Configuration.parseBoolean( loadDefaults, true ) );
        LOG.debug( XACMLConstants.LOAD_DEFAULT_POLICIES_PROPERTY + ": " + config.get( XACMLConstants.LOAD_DEFAULT_POLICIES_PROPERTY ) );
    }


    /**
     * DOCUMENT ME!
     *
     * @param   xupdate
     *
     * @throws  NumberFormatException
     */
    private void configureXUpdate( Element xupdate ) throws NumberFormatException
    {
        String fragmentation = getConfigAttributeValue( xupdate, DBBroker.XUPDATE_FRAGMENTATION_FACTOR_ATTRIBUTE );

        if( fragmentation != null ) {
            config.put( DBBroker.PROPERTY_XUPDATE_FRAGMENTATION_FACTOR, new Integer( fragmentation ) );
            LOG.debug( DBBroker.PROPERTY_XUPDATE_FRAGMENTATION_FACTOR + ": " + config.get( DBBroker.PROPERTY_XUPDATE_FRAGMENTATION_FACTOR ) );
        }

        String consistencyCheck = getConfigAttributeValue( xupdate, DBBroker.XUPDATE_CONSISTENCY_CHECKS_ATTRIBUTE );

        if( consistencyCheck != null ) {
            config.put( DBBroker.PROPERTY_XUPDATE_CONSISTENCY_CHECKS, parseBoolean( consistencyCheck, false ) );
            LOG.debug( DBBroker.PROPERTY_XUPDATE_CONSISTENCY_CHECKS + ": " + config.get( DBBroker.PROPERTY_XUPDATE_CONSISTENCY_CHECKS ) );
        }
    }


    private void configureTransformer( Element transformer )
    {
        String className = getConfigAttributeValue( transformer, TransformerFactoryAllocator.TRANSFORMER_CLASS_ATTRIBUTE );

        if( className != null ) {
            config.put( TransformerFactoryAllocator.PROPERTY_TRANSFORMER_CLASS, className );
            LOG.debug( TransformerFactoryAllocator.PROPERTY_TRANSFORMER_CLASS + ": " + config.get( TransformerFactoryAllocator.PROPERTY_TRANSFORMER_CLASS ) );

            // Process any specified attributes that should be passed to the transformer factory

            NodeList                  attrs      = transformer.getElementsByTagName( TransformerFactoryAllocator.CONFIGURATION_TRANSFORMER_ATTRIBUTE_ELEMENT_NAME );
            Hashtable<Object, Object> attributes = new Properties();

            for( int a = 0; a < attrs.getLength(); a++ ) {
                Element attr  = (Element)attrs.item( a );
                String  name  = attr.getAttribute( "name" );
                String  value = attr.getAttribute( "value" );
                String  type  = attr.getAttribute( "type" );

                if( ( name == null ) || ( name.length() == 0 ) ) {
                    LOG.warn( "Discarded invalid attribute for TransformerFactory: '" + className + "', name not specified" );

                } else if( ( type == null ) || ( type.length() == 0 ) || type.equalsIgnoreCase( "string" ) ) {
                    attributes.put( name, value );

                } else if( type.equalsIgnoreCase( "boolean" ) ) {
                    attributes.put( name, Boolean.valueOf( value ) );

                } else if( type.equalsIgnoreCase( "integer" ) ) {

                    try {
                        attributes.put( name, Integer.valueOf( value ) );

                    }
                    catch( NumberFormatException nfe ) {
                        LOG.warn( "Discarded invalid attribute for TransformerFactory: '" + className + "', name: " + name + ", value not integer: " + value );
                    }

                } else {

                    // Assume string type
                    attributes.put( name, value );
                }
            }

            config.put( TransformerFactoryAllocator.PROPERTY_TRANSFORMER_ATTRIBUTES, attributes );
        }

        String cachingValue = getConfigAttributeValue( transformer, TransformerFactoryAllocator.TRANSFORMER_CACHING_ATTRIBUTE );

        if( cachingValue != null ) {
            config.put( TransformerFactoryAllocator.PROPERTY_CACHING_ATTRIBUTE, parseBoolean( cachingValue, false ) );
            LOG.debug( TransformerFactoryAllocator.PROPERTY_CACHING_ATTRIBUTE + ": " + config.get( TransformerFactoryAllocator.PROPERTY_CACHING_ATTRIBUTE ) );
        }
    }


    /**
     * DOCUMENT ME!
     *
     * @param  serializer
     */
    private void configureSerializer( Element serializer )
    {
        String xinclude = getConfigAttributeValue( serializer, Serializer.ENABLE_XINCLUDE_ATTRIBUTE );

        if( xinclude != null ) {
            config.put( Serializer.PROPERTY_ENABLE_XINCLUDE, xinclude );
            LOG.debug( Serializer.PROPERTY_ENABLE_XINCLUDE + ": " + config.get( Serializer.PROPERTY_ENABLE_XINCLUDE ) );
        }

        String xsl = getConfigAttributeValue( serializer, Serializer.ENABLE_XSL_ATTRIBUTE );

        if( xsl != null ) {
            config.put( Serializer.PROPERTY_ENABLE_XSL, xsl );
            LOG.debug( Serializer.PROPERTY_ENABLE_XSL + ": " + config.get( Serializer.PROPERTY_ENABLE_XSL ) );
        }

        String indent = getConfigAttributeValue( serializer, Serializer.INDENT_ATTRIBUTE );

        if( indent != null ) {
            config.put( Serializer.PROPERTY_INDENT, indent );
            LOG.debug( Serializer.PROPERTY_INDENT + ": " + config.get( Serializer.PROPERTY_INDENT ) );
        }

        String compress = getConfigAttributeValue( serializer, Serializer.COMPRESS_OUTPUT_ATTRIBUTE );

        if( compress != null ) {
            config.put( Serializer.PROPERTY_COMPRESS_OUTPUT, compress );
            LOG.debug( Serializer.PROPERTY_COMPRESS_OUTPUT + ": " + config.get( Serializer.PROPERTY_COMPRESS_OUTPUT ) );
        }

        String internalId = getConfigAttributeValue( serializer, Serializer.ADD_EXIST_ID_ATTRIBUTE );

        if( internalId != null ) {
            config.put( Serializer.PROPERTY_ADD_EXIST_ID, internalId );
            LOG.debug( Serializer.PROPERTY_ADD_EXIST_ID + ": " + config.get( Serializer.PROPERTY_ADD_EXIST_ID ) );
        }

        String tagElementMatches = getConfigAttributeValue( serializer, Serializer.TAG_MATCHING_ELEMENTS_ATTRIBUTE );

        if( tagElementMatches != null ) {
            config.put( Serializer.PROPERTY_TAG_MATCHING_ELEMENTS, tagElementMatches );
            LOG.debug( Serializer.PROPERTY_TAG_MATCHING_ELEMENTS + ": " + config.get( Serializer.PROPERTY_TAG_MATCHING_ELEMENTS ) );
        }

        String tagAttributeMatches = getConfigAttributeValue( serializer, Serializer.TAG_MATCHING_ATTRIBUTES_ATTRIBUTE );

        if( tagAttributeMatches != null ) {
            config.put( Serializer.PROPERTY_TAG_MATCHING_ATTRIBUTES, tagAttributeMatches );
            LOG.debug( Serializer.PROPERTY_TAG_MATCHING_ATTRIBUTES + ": " + config.get( Serializer.PROPERTY_TAG_MATCHING_ATTRIBUTES ) );
        }

        NodeList nlFilters = serializer.getElementsByTagName( CustomMatchListenerFactory.CONFIGURATION_ELEMENT );

        if( nlFilters == null ) {
            return;
        }

        List<String> filters = new ArrayList<String>( nlFilters.getLength() );

        for( int i = 0; i < nlFilters.getLength(); i++ ) {
            Element filterElem  = (Element)nlFilters.item( i );
            String  filterClass = filterElem.getAttribute( CustomMatchListenerFactory.CONFIGURATION_ATTR_CLASS );

            if( filterClass != null ) {
                filters.add( filterClass );
                LOG.debug( CustomMatchListenerFactory.CONFIG_MATCH_LISTENERS + ": " + filterClass );
            } else {
                LOG.warn( "Configuration element " + CustomMatchListenerFactory.CONFIGURATION_ELEMENT + " needs an attribute 'class'" );
            }
        }
        config.put( CustomMatchListenerFactory.CONFIG_MATCH_LISTENERS, filters );
    }


    /**
     * Reads the scheduler configuration.
     *
     * @param  scheduler  DOCUMENT ME!
     */
    private void configureScheduler( Element scheduler )
    {
        NodeList nlJobs = scheduler.getElementsByTagName( Scheduler.CONFIGURATION_JOB_ELEMENT_NAME );

        if( nlJobs == null ) {
            return;
        }

        ArrayList<JobConfig> jobList     = new ArrayList<JobConfig>();

        String jobType       = null;
        String jobName       = null;
        String jobResource   = null;
        String jobSchedule   = null;
        String jobDelay      = null;
        String jobRepeat     = null;
        String jobUnschedule = null;

        for( int i = 0; i < nlJobs.getLength(); i++ ) {
            Element job = (Element)nlJobs.item( i );

            //get the job type
            jobType = getConfigAttributeValue( job, Scheduler.JOB_TYPE_ATTRIBUTE );

            if( jobType == null ) {
                jobType = Scheduler.JOB_TYPE_USER; //default to user if unspecified
            }

            jobName = getConfigAttributeValue( job, Scheduler.JOB_NAME_ATTRIBUTE );

            //get the job resource
            jobResource = getConfigAttributeValue( job, Scheduler.JOB_CLASS_ATTRIBUTE );

            if( jobResource == null ) {
                jobResource = getConfigAttributeValue( job, Scheduler.JOB_XQUERY_ATTRIBUTE );
            }

            //get the job schedule
            jobSchedule = getConfigAttributeValue( job, Scheduler.JOB_CRON_TRIGGER_ATTRIBUTE );

            if( jobSchedule == null ) {
                jobSchedule = getConfigAttributeValue( job, Scheduler.JOB_PERIOD_ATTRIBUTE );
            }

            if( jobUnschedule == null ) {
            	jobUnschedule = getConfigAttributeValue( job, Scheduler.JOB_UNSCHEDULE_ON_EXCEPTION );
            }
            
            //create the job config
            try {
                JobConfig jobConfig = new JobConfig( jobType, jobName, jobResource, jobSchedule, jobUnschedule );

                //get and set the job delay
                jobDelay = getConfigAttributeValue( job, Scheduler.JOB_DELAY_ATTRIBUTE );

                if( ( jobDelay != null ) && ( jobDelay.length() > 0 ) ) {
                    jobConfig.setDelay( Long.parseLong( jobDelay ) );
                }

                //get and set the job repeat
                jobRepeat = getConfigAttributeValue( job, Scheduler.JOB_REPEAT_ATTRIBUTE );

                if( ( jobRepeat != null ) && ( jobRepeat.length() > 0 ) ) {
                    jobConfig.setRepeat( Integer.parseInt( jobRepeat ) );
                }

                NodeList params = job.getElementsByTagName( Scheduler.CONFIGURATION_JOB_PARAMETER_ELEMENT_NAME );

                for( int p = 0; p < params.getLength(); p++ ) {
                    Element param = (Element)params.item( p );
                    String  name  = param.getAttribute( "name" );
                    String  value = param.getAttribute( "value" );

                    if( ( name == null ) || ( name.length() == 0 ) ) {
                        LOG.warn( "Discarded invalid parameter for '" + jobType + "' job '" + jobResource + "'" );
                    } else {
                        jobConfig.addParameter( name, value );
                    }
                }

                jobList.add( jobConfig );

                LOG.debug( "Configured scheduled '" + jobType + "' job '" + jobResource + ( ( jobSchedule == null ) ? "" : ( "' with trigger '" + jobSchedule ) ) + ( ( jobDelay == null ) ? "" : ( "' with delay '" + jobDelay ) ) + ( ( jobRepeat == null ) ? "" : ( "' repetitions '" + jobRepeat ) ) + "'" );
            }
            catch( JobException je ) {
                LOG.warn( je );
            }
        }

        if( jobList.size() > 0 ) {
            JobConfig[] configs = new JobConfig[jobList.size()];

            for( int i = 0; i < jobList.size(); i++ ) {
                configs[i] = (JobConfig)jobList.get( i );
            }

            config.put( Scheduler.PROPERTY_SCHEDULER_JOBS, configs );
        }
    }


    /**
     * DOCUMENT ME!
     *
     * @param   dbHome
     * @param   con
     *
     * @throws  DatabaseConfigurationException
     */
    private void configureBackend( String dbHome, Element con ) throws DatabaseConfigurationException
    {
        String mysql = getConfigAttributeValue( con, BrokerFactory.PROPERTY_DATABASE );

        if( mysql != null ) {
            config.put( BrokerFactory.PROPERTY_DATABASE, mysql );
            LOG.debug( BrokerFactory.PROPERTY_DATABASE + ": " + config.get( BrokerFactory.PROPERTY_DATABASE ) );
        }

        // directory for database files
        String dataFiles = getConfigAttributeValue( con, BrokerPool.DATA_DIR_ATTRIBUTE );

        if( dataFiles != null ) {
            File df = ConfigurationHelper.lookup( dataFiles, dbHome );

            if( !df.canRead() ) {
                boolean mkdirs = df.mkdirs();
                if (!(mkdirs && df.canRead())) {
                    throw( new DatabaseConfigurationException( "cannot read data directory: " + df.getAbsolutePath() ) );
                }
            }
            config.put( BrokerPool.PROPERTY_DATA_DIR, df.getAbsolutePath() );
            LOG.debug( BrokerPool.PROPERTY_DATA_DIR + ": " + config.get( BrokerPool.PROPERTY_DATA_DIR ) );
        }

        String cacheMem = getConfigAttributeValue( con, DefaultCacheManager.CACHE_SIZE_ATTRIBUTE );

        if( cacheMem != null ) {

            if( cacheMem.endsWith( "M" ) || cacheMem.endsWith( "m" ) ) {
                cacheMem = cacheMem.substring( 0, cacheMem.length() - 1 );
            }

            try {
                config.put( DefaultCacheManager.PROPERTY_CACHE_SIZE, new Integer( cacheMem ) );
                LOG.debug( DefaultCacheManager.PROPERTY_CACHE_SIZE + ": " + config.get( DefaultCacheManager.PROPERTY_CACHE_SIZE ) + "m" );
            }
            catch( NumberFormatException nfe ) {
                LOG.warn( nfe );
            }
        }
        
        // Process the Check Max Cache value
        
        String checkMaxCache = getConfigAttributeValue( con, DefaultCacheManager.CACHE_CHECK_MAX_SIZE_ATTRIBUTE );
        
        if( checkMaxCache == null ) {
            checkMaxCache = DefaultCacheManager.DEFAULT_CACHE_CHECK_MAX_SIZE_STRING;
        }
        
        config.put( DefaultCacheManager.PROPERTY_CACHE_CHECK_MAX_SIZE, parseBoolean( checkMaxCache, true ) );
        LOG.debug( DefaultCacheManager.PROPERTY_CACHE_CHECK_MAX_SIZE + ": " + config.get( DefaultCacheManager.PROPERTY_CACHE_CHECK_MAX_SIZE ) );

        String cacheShrinkThreshold = getConfigAttributeValue( con, DefaultCacheManager.SHRINK_THRESHOLD_ATTRIBUTE );

        if( cacheShrinkThreshold == null ) {
            cacheShrinkThreshold = DefaultCacheManager.DEFAULT_SHRINK_THRESHOLD_STRING;
        }

        if( cacheShrinkThreshold != null ) {

            try {
                config.put( DefaultCacheManager.SHRINK_THRESHOLD_PROPERTY, new Integer( cacheShrinkThreshold ) );
                LOG.debug( DefaultCacheManager.SHRINK_THRESHOLD_PROPERTY + ": " + config.get( DefaultCacheManager.SHRINK_THRESHOLD_PROPERTY ) );
            }
            catch( NumberFormatException nfe ) {
                LOG.warn( nfe );
            }
        }

        String collectionCache = getConfigAttributeValue( con, CollectionCacheManager.CACHE_SIZE_ATTRIBUTE );

        if( collectionCache != null ) {

            if( collectionCache.endsWith( "M" ) || collectionCache.endsWith( "m" ) ) {
                collectionCache = collectionCache.substring( 0, collectionCache.length() - 1 );
            }

            try {
                config.put( CollectionCacheManager.PROPERTY_CACHE_SIZE, new Integer( collectionCache ) );
                LOG.debug( CollectionCacheManager.PROPERTY_CACHE_SIZE + ": " + config.get( CollectionCacheManager.PROPERTY_CACHE_SIZE ) + "m" );
            }
            catch( NumberFormatException nfe ) {
                LOG.warn( nfe );
            }
        }

        String pageSize = getConfigAttributeValue( con, NativeBroker.PAGE_SIZE_ATTRIBUTE );

        if( pageSize != null ) {

            try {
                config.put( BrokerPool.PROPERTY_PAGE_SIZE, new Integer( pageSize ) );
                LOG.debug( BrokerPool.PROPERTY_PAGE_SIZE + ": " + config.get( BrokerPool.PROPERTY_PAGE_SIZE ) );
            }
            catch( NumberFormatException nfe ) {
                LOG.warn( nfe );
            }
        }

        //Not clear : rather looks like a buffers count
        String collCacheSize = getConfigAttributeValue( con, BrokerPool.COLLECTION_CACHE_SIZE_ATTRIBUTE );

        if( collCacheSize != null ) {

            try {
                config.put( BrokerPool.PROPERTY_COLLECTION_CACHE_SIZE, new Integer( collCacheSize ) );
                LOG.debug( BrokerPool.PROPERTY_COLLECTION_CACHE_SIZE + ": " + config.get( BrokerPool.PROPERTY_COLLECTION_CACHE_SIZE ) );
            }
            catch( NumberFormatException nfe ) {
                LOG.warn( nfe );
            }
        }

        String nodesBuffer = getConfigAttributeValue( con, BrokerPool.NODES_BUFFER_ATTRIBUTE );

        if( nodesBuffer != null ) {

            try {
                config.put( BrokerPool.PROPERTY_NODES_BUFFER, new Integer( nodesBuffer ) );
                LOG.debug( BrokerPool.PROPERTY_NODES_BUFFER + ": " + config.get( BrokerPool.PROPERTY_NODES_BUFFER ) );

            }
            catch( NumberFormatException nfe ) {
                LOG.warn( nfe );
            }
        }

        String docIds = con.getAttribute(BrokerPool.DOC_ID_MODE_ATTRIBUTE);
        if (docIds != null) {
        	config.put(BrokerPool.DOC_ID_MODE_PROPERTY, docIds);
        }
        
        //Unused !
        String buffers = getConfigAttributeValue( con, "buffers" );

        if( buffers != null ) {

            try {
                config.put( "db-connection.buffers", new Integer( buffers ) );
                LOG.debug( "db-connection.buffers: " + config.get( "db-connection.buffers" ) );

            }
            catch( NumberFormatException nfe ) {
                LOG.warn( nfe );
            }
        }

        //Unused !
        String collBuffers = getConfigAttributeValue( con, "collection_buffers" );

        if( collBuffers != null ) {

            try {
                config.put( "db-connection.collections.buffers", new Integer( collBuffers ) );
                LOG.debug( "db-connection.collections.buffers: " + config.get( "db-connection.collections.buffers" ) );

            }
            catch( NumberFormatException nfe ) {
                LOG.warn( nfe );
            }
        }

        //Unused !
        String wordBuffers = getConfigAttributeValue( con, "words_buffers" );

        if( wordBuffers != null ) {

            try {
                config.put( "db-connection.words.buffers", new Integer( wordBuffers ) );
                LOG.debug( "db-connection.words.buffers: " + config.get( "db-connection.words.buffers" ) );

            }
            catch( NumberFormatException nfe ) {
                LOG.warn( nfe );
            }
        }

        //Unused !
        String elementBuffers = getConfigAttributeValue( con, "elements_buffers" );

        if( elementBuffers != null ) {

            try {
                config.put( "db-connection.elements.buffers", new Integer( elementBuffers ) );
                LOG.debug( "db-connection.elements.buffers: " + config.get( "db-connection.elements.buffers" ) );

            }
            catch( NumberFormatException nfe ) {
                LOG.warn( nfe );
            }
        }

        String diskSpace = getConfigAttributeValue(con, BrokerPool.DISK_SPACE_MIN_ATTRIBUTE);

        if( diskSpace != null ) {

            if( diskSpace.endsWith( "M" ) || diskSpace.endsWith( "m" ) ) {
                diskSpace = diskSpace.substring( 0, diskSpace.length() - 1 );
            }

            try {
                config.put(BrokerPool.DISK_SPACE_MIN_PROPERTY, new Integer(diskSpace));
            }
            catch( NumberFormatException nfe ) {
                LOG.warn( nfe );
            }
        }

        NodeList securityConf             = con.getElementsByTagName( BrokerPool.CONFIGURATION_SECURITY_ELEMENT_NAME );
        String   securityManagerClassName = BrokerPool.DEFAULT_SECURITY_CLASS;

        if( securityConf.getLength() > 0 ) {
            Element security = (Element)securityConf.item( 0 );
            securityManagerClassName = getConfigAttributeValue( security, "class" );

            //Unused
            String encoding = getConfigAttributeValue( security, "password-encoding" );
            config.put( "db-connection.security.password-encoding", encoding );

            //Unused
            String realm = getConfigAttributeValue( security, "password-realm" );
            config.put( "db-connection.security.password-realm", realm );

            if( realm != null ) {
                LOG.info( "db-connection.security.password-realm: " + config.get( "db-connection.security.password-realm" ) );
                RealmImpl.setPasswordRealm( realm );

            } else {
                LOG.info( "No password realm set, defaulting." );
            }
        }

        try {
            config.put( BrokerPool.PROPERTY_SECURITY_CLASS, Class.forName( securityManagerClassName ) );
            LOG.debug( BrokerPool.PROPERTY_SECURITY_CLASS + ": " + config.get( BrokerPool.PROPERTY_SECURITY_CLASS ) );

        }
        catch( Throwable ex ) {

            if( ex instanceof ClassNotFoundException ) {
                throw( new DatabaseConfigurationException( "Cannot find security manager class " + securityManagerClassName, ex ) );
            } else {
                throw( new DatabaseConfigurationException( "Cannot load security manager class " + securityManagerClassName + " due to " + ex.getMessage(), ex ) );
            }
        }

        final NodeList startupConf = con.getElementsByTagName(BrokerPool.CONFIGURATION_STARTUP_ELEMENT_NAME);
        if(startupConf.getLength() > 0) {
            configureStartup((Element)startupConf.item(0));
        } else {
            // Prevent NPE
            List<String> startupTriggers = new ArrayList<String>();
            config.put(BrokerPool.PROPERTY_STARTUP_TRIGGERS, startupTriggers);
        }
        
        NodeList poolConf = con.getElementsByTagName( BrokerPool.CONFIGURATION_POOL_ELEMENT_NAME );

        if( poolConf.getLength() > 0 ) {
            configurePool( (Element)poolConf.item( 0 ) );
        }

        NodeList queryPoolConf = con.getElementsByTagName( XQueryPool.CONFIGURATION_ELEMENT_NAME );

        if( queryPoolConf.getLength() > 0 ) {
            configureXQueryPool( (Element)queryPoolConf.item( 0 ) );
        }

        NodeList watchConf = con.getElementsByTagName( XQueryWatchDog.CONFIGURATION_ELEMENT_NAME );

        if( watchConf.getLength() > 0 ) {
            configureWatchdog( (Element)watchConf.item( 0 ) );
        }

        NodeList recoveries = con.getElementsByTagName( BrokerPool.CONFIGURATION_RECOVERY_ELEMENT_NAME );

        if( recoveries.getLength() > 0 ) {
            configureRecovery( dbHome, (Element)recoveries.item( 0 ) );
        }
    }


    private void configureRecovery( String dbHome, Element recovery ) throws DatabaseConfigurationException
    {
        String option = getConfigAttributeValue( recovery, BrokerPool.RECOVERY_ENABLED_ATTRIBUTE );
        setProperty( BrokerPool.PROPERTY_RECOVERY_ENABLED, parseBoolean( option, true ) );
        LOG.debug( BrokerPool.PROPERTY_RECOVERY_ENABLED + ": " + config.get( BrokerPool.PROPERTY_RECOVERY_ENABLED ) );

        option = getConfigAttributeValue( recovery, Journal.RECOVERY_SYNC_ON_COMMIT_ATTRIBUTE );
        setProperty( Journal.PROPERTY_RECOVERY_SYNC_ON_COMMIT, parseBoolean( option, true ) );
        LOG.debug( Journal.PROPERTY_RECOVERY_SYNC_ON_COMMIT + ": " + config.get( Journal.PROPERTY_RECOVERY_SYNC_ON_COMMIT ) );

        option = getConfigAttributeValue( recovery, TransactionManager.RECOVERY_GROUP_COMMIT_ATTRIBUTE );
        setProperty( TransactionManager.PROPERTY_RECOVERY_GROUP_COMMIT, parseBoolean( option, false ) );
        LOG.debug( TransactionManager.PROPERTY_RECOVERY_GROUP_COMMIT + ": " + config.get( TransactionManager.PROPERTY_RECOVERY_GROUP_COMMIT ) );

        option = getConfigAttributeValue( recovery, Journal.RECOVERY_JOURNAL_DIR_ATTRIBUTE );

        if( option != null ) {

            //DWES
            File rf = ConfigurationHelper.lookup( option, dbHome );

            if( !rf.canRead() ) {
                throw( new DatabaseConfigurationException( "cannot read data directory: " + rf.getAbsolutePath() ) );
            }
            setProperty( Journal.PROPERTY_RECOVERY_JOURNAL_DIR, rf.getAbsolutePath() );
            LOG.debug( Journal.PROPERTY_RECOVERY_JOURNAL_DIR + ": " + config.get( Journal.PROPERTY_RECOVERY_JOURNAL_DIR ) );
        }

        option = getConfigAttributeValue( recovery, Journal.RECOVERY_SIZE_LIMIT_ATTRIBUTE );

        if( option != null ) {

            if( option.endsWith( "M" ) || option.endsWith( "m" ) ) {
                option = option.substring( 0, option.length() - 1 );
            }

            try {
                Integer size = new Integer( option );
                setProperty( Journal.PROPERTY_RECOVERY_SIZE_LIMIT, size );
                LOG.debug( Journal.PROPERTY_RECOVERY_SIZE_LIMIT + ": " + config.get( Journal.PROPERTY_RECOVERY_SIZE_LIMIT ) + "m" );
            }
            catch( NumberFormatException e ) {
                throw( new DatabaseConfigurationException( "size attribute in recovery section needs to be a number" ) );
            }
        }

        option = getConfigAttributeValue( recovery, TransactionManager.RECOVERY_FORCE_RESTART_ATTRIBUTE );
        boolean value = false;

        if( option != null ) {
            value = option.equals( "yes" );
        }
        setProperty( TransactionManager.PROPERTY_RECOVERY_FORCE_RESTART, new Boolean( value ) );
        LOG.debug( TransactionManager.PROPERTY_RECOVERY_FORCE_RESTART + ": " + config.get( TransactionManager.PROPERTY_RECOVERY_FORCE_RESTART ) );

        option = getConfigAttributeValue( recovery, BrokerPool.RECOVERY_POST_RECOVERY_CHECK );
        value  = false;

        if( option != null ) {
            value = option.equals( "yes" );
        }
        setProperty( BrokerPool.PROPERTY_RECOVERY_CHECK, new Boolean( value ) );
        LOG.debug( BrokerPool.PROPERTY_RECOVERY_CHECK + ": " + config.get( BrokerPool.PROPERTY_RECOVERY_CHECK ) );
    }

    /**
     * DOCUMENT ME!
     *
     * @param  watchDog
     */
    private void configureWatchdog( Element watchDog )
    {
        String timeout = getConfigAttributeValue( watchDog, "query-timeout" );

        if( timeout != null ) {

            try {
                config.put( XQueryWatchDog.PROPERTY_QUERY_TIMEOUT, new Long( timeout ) );
                LOG.debug( XQueryWatchDog.PROPERTY_QUERY_TIMEOUT + ": " + config.get( XQueryWatchDog.PROPERTY_QUERY_TIMEOUT ) );
            }
            catch( NumberFormatException e ) {
                LOG.warn( e );
            }
        }

        String maxOutput = getConfigAttributeValue( watchDog, "output-size-limit" );

        if( maxOutput != null ) {

            try {
                config.put( XQueryWatchDog.PROPERTY_OUTPUT_SIZE_LIMIT, new Integer( maxOutput ) );
                LOG.debug( XQueryWatchDog.PROPERTY_OUTPUT_SIZE_LIMIT + ": " + config.get( XQueryWatchDog.PROPERTY_OUTPUT_SIZE_LIMIT ) );
            }
            catch( NumberFormatException e ) {
                LOG.warn( e );
            }
        }
    }


    /**
     * DOCUMENT ME!
     *
     * @param  queryPool
     */
    private void configureXQueryPool( Element queryPool )
    {
        String maxStackSize = getConfigAttributeValue( queryPool, XQueryPool.MAX_STACK_SIZE_ATTRIBUTE );

        if( maxStackSize != null ) {

            try {
                config.put( XQueryPool.PROPERTY_MAX_STACK_SIZE, new Integer( maxStackSize ) );
                LOG.debug( XQueryPool.PROPERTY_MAX_STACK_SIZE + ": " + config.get( XQueryPool.PROPERTY_MAX_STACK_SIZE ) );
            }
            catch( NumberFormatException e ) {
                LOG.warn( e );
            }
        }

        String maxPoolSize = getConfigAttributeValue( queryPool, XQueryPool.POOL_SIZE_ATTTRIBUTE );

        if( maxPoolSize != null ) {

            try {
                config.put( XQueryPool.PROPERTY_POOL_SIZE, new Integer( maxPoolSize ) );
                LOG.debug( XQueryPool.PROPERTY_POOL_SIZE + ": " + config.get( XQueryPool.PROPERTY_POOL_SIZE ) );
            }
            catch( NumberFormatException e ) {
                LOG.warn( e );
            }
        }

        String timeout = getConfigAttributeValue( queryPool, XQueryPool.TIMEOUT_ATTRIBUTE );

        if( timeout != null ) {

            try {
                config.put( XQueryPool.PROPERTY_TIMEOUT, new Long( timeout ) );
                LOG.debug( XQueryPool.PROPERTY_TIMEOUT + ": " + config.get( XQueryPool.PROPERTY_TIMEOUT ) );
            }
            catch( NumberFormatException e ) {
                LOG.warn( e );
            }
        }

        String timeoutCheckInterval = getConfigAttributeValue( queryPool, XQueryPool.TIMEOUT_CHECK_INTERVAL_ATTRIBUTE );

        if( timeoutCheckInterval != null ) {

            try {
                config.put( XQueryPool.PROPERTY_TIMEOUT_CHECK_INTERVAL, new Long( timeoutCheckInterval ) );
                LOG.debug( XQueryPool.PROPERTY_TIMEOUT_CHECK_INTERVAL + ": " + config.get( XQueryPool.PROPERTY_TIMEOUT_CHECK_INTERVAL ) );
            }
            catch( NumberFormatException e ) {
                LOG.warn( e );
            }
        }
    }

    private void configureStartup(final Element startup) {
        final NodeList nlTriggers = startup.getElementsByTagName("triggers");
        if(nlTriggers != null && nlTriggers.getLength() > 0) {
            final Element triggers = (Element)nlTriggers.item(0);
            final NodeList nlTrigger = triggers.getElementsByTagName("trigger");
            if(nlTrigger != null && nlTrigger.getLength() > 0) {
                for(int i = 0; i < nlTrigger.getLength(); i++) {
                    final Element trigger = (Element)nlTrigger.item(i);
                    List<String> startupTriggers = (List<String>)config.get(BrokerPool.PROPERTY_STARTUP_TRIGGERS);
                    if(startupTriggers == null) {
                        startupTriggers = new ArrayList<String>();
                        config.put(BrokerPool.PROPERTY_STARTUP_TRIGGERS, startupTriggers);
                    }
                    
                    final String startupTriggerClass = trigger.getAttribute("class");
                    
                    boolean isStartupTrigger = false;
                    try {
                        for(Class iface : Class.forName(startupTriggerClass).getInterfaces()) {
                            if(iface.getName().equals("org.exist.storage.StartupTrigger")) {
                                isStartupTrigger = true;
                                break;
                            }
                        }

                        if(isStartupTrigger) {
                            startupTriggers.add(startupTriggerClass);
                            LOG.debug("Registered StartupTrigger: " + startupTriggerClass);
                        } else {
                            LOG.warn("StartupTrigger: " + startupTriggerClass + " does not implement org.exist.storage.StartupTrigger. IGNORING!");
                        }
                    } catch(ClassNotFoundException cnfe) {
                        LOG.error("Could not find StartupTrigger class: " + startupTriggerClass + ". " + cnfe.getMessage(), cnfe);
                    }
                }
            }
        }
    }
    

    /**
     * DOCUMENT ME!
     *
     * @param  pool
     */
    private void configurePool( Element pool )
    {
        String min = getConfigAttributeValue( pool, BrokerPool.MIN_CONNECTIONS_ATTRIBUTE );

        if( min != null ) {

            try {
                config.put( BrokerPool.PROPERTY_MIN_CONNECTIONS, new Integer( min ) );
                LOG.debug( BrokerPool.PROPERTY_MIN_CONNECTIONS + ": " + config.get( BrokerPool.PROPERTY_MIN_CONNECTIONS ) );
            }
            catch( NumberFormatException e ) {
                LOG.warn( e );
            }
        }

        String max = getConfigAttributeValue( pool, BrokerPool.MAX_CONNECTIONS_ATTRIBUTE );

        if( max != null ) {

            try {
                config.put( BrokerPool.PROPERTY_MAX_CONNECTIONS, new Integer( max ) );
                LOG.debug( BrokerPool.PROPERTY_MAX_CONNECTIONS + ": " + config.get( BrokerPool.PROPERTY_MAX_CONNECTIONS ) );
            }
            catch( NumberFormatException e ) {
                LOG.warn( e );
            }
        }

        String sync = getConfigAttributeValue( pool, BrokerPool.SYNC_PERIOD_ATTRIBUTE );

        if( sync != null ) {

            try {
                config.put( BrokerPool.PROPERTY_SYNC_PERIOD, new Long( sync ) );
                LOG.debug( BrokerPool.PROPERTY_SYNC_PERIOD + ": " + config.get( BrokerPool.PROPERTY_SYNC_PERIOD ) );
            }
            catch( NumberFormatException e ) {
                LOG.warn( e );
            }
        }

        String maxShutdownWait = getConfigAttributeValue( pool, BrokerPool.SHUTDOWN_DELAY_ATTRIBUTE );

        if( maxShutdownWait != null ) {

            try {
                config.put( BrokerPool.PROPERTY_SHUTDOWN_DELAY, new Long( maxShutdownWait ) );
                LOG.debug( BrokerPool.PROPERTY_SHUTDOWN_DELAY + ": " + config.get( BrokerPool.PROPERTY_SHUTDOWN_DELAY ) );
            }
            catch( NumberFormatException e ) {
                LOG.warn( e );
            }
        }
    }


    private void configureIndexer( String dbHome, Document doc, Element indexer ) throws DatabaseConfigurationException, MalformedURLException
    {
        String parseNum = getConfigAttributeValue( indexer, TextSearchEngine.INDEX_NUMBERS_ATTRIBUTE );

        if( parseNum != null ) {
            config.put( TextSearchEngine.PROPERTY_INDEX_NUMBERS, parseBoolean( parseNum, false ) );
            LOG.debug( TextSearchEngine.PROPERTY_INDEX_NUMBERS + ": " + config.get( TextSearchEngine.PROPERTY_INDEX_NUMBERS ) );
        }

        String stemming = getConfigAttributeValue( indexer, TextSearchEngine.STEM_ATTRIBUTE );

        if( stemming != null ) {
            config.put( TextSearchEngine.PROPERTY_STEM, parseBoolean( stemming, false ) );
            LOG.debug( TextSearchEngine.PROPERTY_STEM + ": " + config.get( TextSearchEngine.PROPERTY_STEM ) );
        }

        String termFreq = getConfigAttributeValue( indexer, TextSearchEngine.STORE_TERM_FREQUENCY_ATTRIBUTE );

        if( termFreq != null ) {
            config.put( TextSearchEngine.PROPERTY_STORE_TERM_FREQUENCY, parseBoolean( termFreq, false ) );
            LOG.debug( TextSearchEngine.PROPERTY_STORE_TERM_FREQUENCY + ": " + config.get( TextSearchEngine.PROPERTY_STORE_TERM_FREQUENCY ) );
        }

        String tokenizer = getConfigAttributeValue( indexer, TextSearchEngine.TOKENIZER_ATTRIBUTE );

        if( tokenizer != null ) {
            config.put( TextSearchEngine.PROPERTY_TOKENIZER, tokenizer );
            LOG.debug( TextSearchEngine.PROPERTY_TOKENIZER + ": " + config.get( TextSearchEngine.PROPERTY_TOKENIZER ) );
        }

        String caseSensitive = getConfigAttributeValue( indexer, NativeValueIndex.INDEX_CASE_SENSITIVE_ATTRIBUTE );

        if( caseSensitive != null ) {
            config.put( NativeValueIndex.PROPERTY_INDEX_CASE_SENSITIVE, parseBoolean( caseSensitive, false ) );
            LOG.debug( NativeValueIndex.PROPERTY_INDEX_CASE_SENSITIVE + ": " + config.get( NativeValueIndex.PROPERTY_INDEX_CASE_SENSITIVE ) );
        }

        // stopwords
        NodeList stopwords = indexer.getElementsByTagName( TextSearchEngine.CONFIGURATION_STOPWORDS_ELEMENT_NAME );

        if( stopwords.getLength() > 0 ) {
            String stopwordFile = ( (Element)stopwords.item( 0 ) ).getAttribute( TextSearchEngine.STOPWORD_FILE_ATTRIBUTE );
            File   sf           = ConfigurationHelper.lookup( stopwordFile, dbHome );

            if( sf.canRead() ) {
                config.put( TextSearchEngine.PROPERTY_STOPWORD_FILE, stopwordFile );
                LOG.debug( TextSearchEngine.PROPERTY_STOPWORD_FILE + ": " + config.get( TextSearchEngine.PROPERTY_STOPWORD_FILE ) );
            }
        }

        int    depth      = 3;
        String indexDepth = getConfigAttributeValue( indexer, NativeBroker.INDEX_DEPTH_ATTRIBUTE );

        if( indexDepth != null ) {

            try {
                depth = Integer.parseInt( indexDepth );

                if( depth < 3 ) {
                    LOG.warn( "parameter index-depth should be >= 3 or you will experience a severe " + "performance loss for node updates (XUpdate or XQuery update extensions)" );
                    depth = 3;
                }
                config.put( NativeBroker.PROPERTY_INDEX_DEPTH, new Integer( depth ) );
                LOG.debug( NativeBroker.PROPERTY_INDEX_DEPTH + ": " + config.get( NativeBroker.PROPERTY_INDEX_DEPTH ) );
            }
            catch( NumberFormatException e ) {
                LOG.warn( e );
            }
        }

        String suppressWS = getConfigAttributeValue( indexer, Indexer.SUPPRESS_WHITESPACE_ATTRIBUTE );

        if( suppressWS != null ) {
            config.put( Indexer.PROPERTY_SUPPRESS_WHITESPACE, suppressWS );
            LOG.debug( Indexer.PROPERTY_SUPPRESS_WHITESPACE + ": " + config.get( Indexer.PROPERTY_SUPPRESS_WHITESPACE ) );
        }

        String suppressWSmixed = getConfigAttributeValue( indexer, Indexer.PRESERVE_WS_MIXED_CONTENT_ATTRIBUTE );

        if( suppressWSmixed != null ) {
            config.put( Indexer.PROPERTY_PRESERVE_WS_MIXED_CONTENT, parseBoolean( suppressWSmixed, false ) );
            LOG.debug( Indexer.PROPERTY_PRESERVE_WS_MIXED_CONTENT + ": " + config.get( Indexer.PROPERTY_PRESERVE_WS_MIXED_CONTENT ) );
        }

        // index settings
        NodeList cl = doc.getElementsByTagName( Indexer.CONFIGURATION_INDEX_ELEMENT_NAME );

        if( cl.getLength() > 0 ) {
            Element   elem = (Element)cl.item( 0 );
            IndexSpec spec = new IndexSpec( null, elem );
            config.put( Indexer.PROPERTY_INDEXER_CONFIG, spec );
            //LOG.debug(Indexer.PROPERTY_INDEXER_CONFIG + ": " + config.get(Indexer.PROPERTY_INDEXER_CONFIG));
        }

        // index modules
        NodeList modules = indexer.getElementsByTagName( IndexManager.CONFIGURATION_ELEMENT_NAME );

        if( modules.getLength() > 0 ) {
            modules = ( (Element)modules.item( 0 ) ).getElementsByTagName( IndexManager.CONFIGURATION_MODULE_ELEMENT_NAME );
            IndexModuleConfig[] modConfig = new IndexModuleConfig[modules.getLength()];

            for( int i = 0; i < modules.getLength(); i++ ) {
                Element elem      = (Element)modules.item( i );
                String  className = elem.getAttribute( IndexManager.INDEXER_MODULES_CLASS_ATTRIBUTE );
                String  id        = elem.getAttribute( IndexManager.INDEXER_MODULES_ID_ATTRIBUTE );

                if( ( className == null ) || ( className.length() == 0 ) ) {
                    throw( new DatabaseConfigurationException( "Required attribute class is missing for module" ) );
                }

                if( ( id == null ) || ( id.length() == 0 ) ) {
                    throw( new DatabaseConfigurationException( "Required attribute id is missing for module" ) );
                }
                modConfig[i] = new IndexModuleConfig( id, className, elem );
            }
            config.put( IndexManager.PROPERTY_INDEXER_MODULES, modConfig );
        }
    }


    private void configureValidation( String dbHome, Document doc, Element validation ) throws DatabaseConfigurationException
    {
        // Register custom protocol URL
        // TODO DWES move to different location?
        eXistURLStreamHandlerFactory.init();

        // Determine validation mode
        String mode = getConfigAttributeValue( validation, XMLReaderObjectFactory.VALIDATION_MODE_ATTRIBUTE );

        if( mode != null ) {
            config.put( XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE, mode );
            LOG.debug( XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE + ": " + config.get( XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE ) );
        }


        // Extract catalogs
        LOG.debug( "Creating eXist catalog resolver" );
        eXistXMLCatalogResolver resolver       = new eXistXMLCatalogResolver();

        NodeList                entityResolver = validation.getElementsByTagName( XMLReaderObjectFactory.CONFIGURATION_ENTITY_RESOLVER_ELEMENT_NAME );

        if( entityResolver.getLength() > 0 ) {
            Element  r        = (Element)entityResolver.item( 0 );
            NodeList catalogs = r.getElementsByTagName( XMLReaderObjectFactory.CONFIGURATION_CATALOG_ELEMENT_NAME );

            LOG.debug( "Found " + catalogs.getLength() + " catalog uri entries." );
            LOG.debug( "Using dbHome=" + dbHome );

            // Determine webapps directory. SingleInstanceConfiguration cannot
            // be used at this phase. Trick is to check wether dbHOME is
            // pointing to a WEB-INF directory, meaning inside war file)
            File webappHome = null;

            if( dbHome == null ) { /// DWES Why? let's make jUnit happy
                webappHome = new File( "webapp" ).getAbsoluteFile();

            } else if( dbHome.endsWith( "WEB-INF" ) ) {
                webappHome = new File( dbHome ).getParentFile().getAbsoluteFile();

            } else {
                webappHome = new File( dbHome, "webapp" ).getAbsoluteFile();
            }
            LOG.debug( "using webappHome=" + webappHome.toURI().toString() );

            // Get and store all URIs
            List<String> allURIs = new ArrayList<String>();

            for( int i = 0; i < catalogs.getLength(); i++ ) {
                String uri = ( (Element)catalogs.item( i ) ).getAttribute( "uri" );

                if( uri != null ) { // when uri attribute is filled in

                    // Substitute string, creating an uri from a local file
                    if( uri.indexOf( "${WEBAPP_HOME}" ) != -1 ) {
                        uri = uri.replaceAll( "\\$\\{WEBAPP_HOME\\}", webappHome.toURI().toString() );
                    }
                    if( uri.indexOf( "${EXIST_HOME}" ) != -1 ) {
                        uri = uri.replaceAll( "\\$\\{EXIST_HOME\\}", dbHome );
                    }

                    // Add uri to confiuration
                    LOG.info( "Add catalog uri " + uri + "" );
                    allURIs.add( uri );
                }

            }
            resolver.setCatalogs( allURIs );

            // Store all configured URIs
            config.put( XMLReaderObjectFactory.CATALOG_URIS, allURIs );

        }

        // Store resolver
        config.put( XMLReaderObjectFactory.CATALOG_RESOLVER, resolver );

        // cache
        GrammarPool gp = new GrammarPool();
        config.put( XMLReaderObjectFactory.GRAMMER_POOL, gp );

    }
    
     /**
     * Gets the value of a configuration attribute
     *
     * The value typically is specified in the conf.xml file, but can be overriden with using a System Property
     *
     * @param   element        The attribute's parent element
     * @param   attributeName  The name of the attribute
     *
     * @return  The value of the attribute
     */
    private String getConfigAttributeValue( Element element, String attributeName )
    {
    	String value = null;
    	
    	if( element != null && attributeName != null ) {
    		String property = getAttributeSystemPropertyName( element, attributeName );
    		
    		value = System.getProperty( property );
    		
    		// If the value has not been overriden in a system property, then get it from the configuration
    		
    		if( value != null ) {
    			LOG.warn( "Configuration value overriden by system property: " + property + ", with value: " + value );
    		} else {
    			value = element.getAttribute( attributeName );
    		}
    	}
    	
    	return( value );
    }
    
    /**
     * Generates a suitable system property name from the given config attribute and parent element.
     *
     * values are of the form org.element.element.....attribute and follow the heirarchical structure of the conf.xml file. 
     * For example, the db-connection cacheSize property name would be org.exist.db-connection.cacheSize
     *
     * @param   element        The attribute's parent element
     * @param   attributeName  The name of the attribute
     *
     * @return  The generated system property name
     */
    private String getAttributeSystemPropertyName( Element element, String attributeName )
    {
    	StringBuilder  	property 	= new StringBuilder( attributeName );
    	Node			parent		= element.getParentNode();
    	
    	property.insert( 0, "." );
    	property.insert( 0,  element.getLocalName() );
    	
    	while( parent != null && parent instanceof Element ) {
    		String parentName = ((Element)parent).getLocalName();
    		
    		property.insert( 0, "." );
    		property.insert( 0, parentName );
    		
    		parent   = parent.getParentNode();
    	}
    	
    	property.insert( 0, "org." );
    	
    	return( property.toString() );
    }


    public String getConfigFilePath()
    {
        return( configFilePath );
    }


    public File getExistHome()
    {
        return( existHome );
    }


    public Object getProperty( String name )
    {
        return( config.get( name ) );
    }

    public Object getProperty( String name, Object defaultValue )
    {
    	Object value = config.get( name );
    	
    	if (value == null)
    		return defaultValue;
        
    	return value;
    }

    public boolean hasProperty( String name )
    {
        return( config.containsKey( name ) );
    }


    public void setProperty( String name, Object obj )
    {
        config.put( name, obj );
    }

    public void removeProperty(String name) {
        config.remove(name);
    }

    /**
     * Takes the passed string and converts it to a non-null <code>Boolean</code> object. If value is null, the specified default value is used.
     * Otherwise, Boolean.TRUE is returned if and only if the passed string equals &quot;yes&quot; or &quot;true&quot;, ignoring case.
     *
     * @param   value         The string to parse
     * @param   defaultValue  The default if the string is null
     *
     * @return  The parsed <code>Boolean</code>
     */
    public static Boolean parseBoolean( String value, boolean defaultValue )
    {
        if( value == null ) {
            return( Boolean.valueOf( defaultValue ) );
        }
        return( Boolean.valueOf( "yes".equalsIgnoreCase( value ) || "true".equalsIgnoreCase( value ) ) );
    }


    public int getInteger( String name )
    {
        Object obj = getProperty( name );

        if( ( obj == null ) || !( obj instanceof Integer ) ) {
            return( -1 );
        }

        return( ( (Integer)obj ).intValue() );
    }


    /**
     * (non-Javadoc).
     *
     * @param   exception  DOCUMENT ME!
     *
     * @throws  SAXException  DOCUMENT ME!
     *
     * @see     org.xml.sax.ErrorHandler#error(org.xml.sax.SAXParseException)
     */
    public void error( SAXParseException exception ) throws SAXException
    {
        System.err.println( "error occured while reading configuration file " + "[line: " + exception.getLineNumber() + "]:" + exception.getMessage() );
    }


    /**
     * (non-Javadoc).
     *
     * @param   exception  DOCUMENT ME!
     *
     * @throws  SAXException  DOCUMENT ME!
     *
     * @see     org.xml.sax.ErrorHandler#fatalError(org.xml.sax.SAXParseException)
     */
    public void fatalError( SAXParseException exception ) throws SAXException
    {
        System.err.println( "error occured while reading configuration file " + "[line: " + exception.getLineNumber() + "]:" + exception.getMessage() );
    }


    /**
     * (non-Javadoc).
     *
     * @param   exception  DOCUMENT ME!
     *
     * @throws  SAXException  DOCUMENT ME!
     *
     * @see     org.xml.sax.ErrorHandler#warning(org.xml.sax.SAXParseException)
     */
    public void warning( SAXParseException exception ) throws SAXException
    {
        System.err.println( "error occured while reading configuration file " + "[line: " + exception.getLineNumber() + "]:" + exception.getMessage() );
    }

    public static final class JobConfig
    {
        private String     type         = null;
        private String     jobName      = null;
        private String     resourceName = null;
        private String     schedule     = null;
        private long       delay        = -1;
        private int        repeat       = SimpleTrigger.REPEAT_INDEFINITELY;
        private boolean unscheduleOnException = true;
        private Properties parameters   = new Properties();

        public JobConfig( String type, String jobName, String resourceName, String schedule, String unscheduleOnException ) throws JobException
        {
            if( type != null ) {
                this.type = type;
            } else {
                this.type = Scheduler.JOB_TYPE_USER;
            }

            this.jobName = jobName;

            if( resourceName != null ) {
                this.resourceName = resourceName;
            } else {
                throw( new JobException( JobException.JOB_ABORT, "Job must have a resource for execution" ) );
            }

            if( ( schedule == null ) && !type.equals( Scheduler.JOB_TYPE_STARTUP ) ) {
                throw( new JobException( JobException.JOB_ABORT, "Job must have a schedule" ) );
            } else {
                this.schedule = schedule;
            }
            
            if (unscheduleOnException != null) {
            	this.unscheduleOnException = parseBoolean( unscheduleOnException, true );
            }
        }

        public String getType()
        {
            return( type );
        }


        public String getJobName()
        {
            return( jobName );
        }


        public String getResourceName()
        {
            return( resourceName );
        }


        public String getSchedule()
        {
            return( schedule );
        }


        public void setDelay( long delay )
        {
            this.delay = delay;
        }


        public void setRepeat( int repeat )
        {
            this.repeat = repeat;
        }


        public long getDelay()
        {
            return( delay );
        }


        public int getRepeat()
        {
            return( repeat );
        }


        public void addParameter( String name, String value )
        {
            parameters.put( name, value );
        }


        public Properties getParameters()
        {
            return( parameters );
        }

		public boolean unscheduleOnException() 
		{
			return unscheduleOnException;
		}

    }


    public static final class IndexModuleConfig
    {
        protected String  id;
        protected String  className;
        protected Element config;

        public IndexModuleConfig( String id, String className, Element config )
        {
            this.id        = id;
            this.className = className;
            this.config    = config;
        }

        public String getClassName()
        {
            return( className );
        }


        public Element getConfig()
        {
            return( config );
        }


        public String getId()
        {
            return( id );
        }
    }

}
