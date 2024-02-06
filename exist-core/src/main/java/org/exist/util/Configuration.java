/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.util;

import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.backup.SystemExport;
import org.exist.collections.CollectionCache;
import org.exist.repo.Deployment;

import org.exist.resolver.ResolverFactory;
import org.exist.start.Main;
import org.exist.storage.lock.LockManager;
import org.exist.storage.lock.LockTable;
import org.exist.util.io.ContentFilePool;
import org.exist.xquery.*;
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
import org.exist.dom.memtree.SAXAdapter;
import org.exist.scheduler.JobConfig;
import org.exist.scheduler.JobException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.DefaultCacheManager;
import org.exist.storage.IndexSpec;
import org.exist.storage.NativeBroker;
import org.exist.storage.XQueryPool;
import org.exist.storage.serializers.CustomMatchListenerFactory;
import org.exist.storage.serializers.Serializer;
import org.exist.validation.GrammarPool;
import org.exist.xmldb.DatabaseImpl;
import org.exist.xslt.TransformerFactoryAllocator;

import java.io.IOException;
import java.io.InputStream;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.exist.Namespaces;
import org.exist.scheduler.JobType;
import org.xmlresolver.Resolver;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;
import static javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING;
import static org.exist.Namespaces.XPATH_FUNCTIONS_NS;
import static org.exist.scheduler.JobConfig.*;
import static org.exist.storage.BrokerFactory.PROPERTY_DATABASE;
import static org.exist.storage.BrokerPoolConstants.*;
import static org.exist.Indexer.CONFIGURATION_INDEX_ELEMENT_NAME;
import static org.exist.Indexer.PRESERVE_WS_MIXED_CONTENT_ATTRIBUTE;
import static org.exist.Indexer.PROPERTY_INDEXER_CONFIG;
import static org.exist.Indexer.PROPERTY_PRESERVE_WS_MIXED_CONTENT;
import static org.exist.Indexer.PROPERTY_SUPPRESS_WHITESPACE;
import static org.exist.Indexer.SUPPRESS_WHITESPACE_ATTRIBUTE;
import static org.exist.collections.CollectionCache.PROPERTY_CACHE_SIZE_BYTES;
import static org.exist.storage.DBBroker.*;
import static org.exist.storage.DefaultCacheManager.*;
import static org.exist.storage.NativeBroker.INDEX_DEPTH_ATTRIBUTE;
import static org.exist.storage.NativeBroker.PROPERTY_INDEX_DEPTH;
import static org.exist.storage.NativeValueIndex.INDEX_CASE_SENSITIVE_ATTRIBUTE;
import static org.exist.storage.NativeValueIndex.PROPERTY_INDEX_CASE_SENSITIVE;
import static org.exist.storage.XQueryPool.MAX_STACK_SIZE_ATTRIBUTE;
import static org.exist.storage.XQueryPool.POOL_SIZE_ATTTRIBUTE;
import static org.exist.storage.XQueryPool.PROPERTY_MAX_STACK_SIZE;
import static org.exist.storage.journal.Journal.PROPERTY_RECOVERY_JOURNAL_DIR;
import static org.exist.storage.journal.Journal.PROPERTY_RECOVERY_SIZE_LIMIT;
import static org.exist.storage.journal.Journal.PROPERTY_RECOVERY_SYNC_ON_COMMIT;
import static org.exist.storage.journal.Journal.RECOVERY_JOURNAL_DIR_ATTRIBUTE;
import static org.exist.storage.journal.Journal.RECOVERY_SIZE_LIMIT_ATTRIBUTE;
import static org.exist.storage.journal.Journal.RECOVERY_SYNC_ON_COMMIT_ATTRIBUTE;
import static org.exist.storage.serializers.Serializer.*;
import static org.exist.util.HtmlToXmlParser.*;
import static org.exist.util.ParametersExtractor.PARAMETER_ELEMENT_NAME;
import static org.exist.util.XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE;
import static org.exist.util.XMLReaderPool.XmlParser.*;
import static org.exist.util.io.ContentFilePool.PROPERTY_IN_MEMORY_SIZE;
import static org.exist.util.io.VirtualTempPath.DEFAULT_IN_MEMORY_SIZE;
import static org.exist.xquery.FunctionFactory.*;
import static org.exist.xquery.XQueryContext.*;
import static org.exist.xquery.XQueryWatchDog.PROPERTY_OUTPUT_SIZE_LIMIT;
import static org.exist.xquery.XQueryWatchDog.PROPERTY_QUERY_TIMEOUT;
import static org.exist.xslt.TransformerFactoryAllocator.*;


public class Configuration implements ErrorHandler {
    public static final String BINARY_CACHE_CLASS_PROPERTY = "binary.cache.class";
    private static final String PRP_DETAILS = "{}: {}";
    private static final Logger LOG = LogManager.getLogger(Configuration.class); //Logger
    private static final String XQUERY_CONFIGURATION_ELEMENT_NAME = "xquery";
    private static final String XQUERY_BUILTIN_MODULES_CONFIGURATION_MODULES_ELEMENT_NAME = "builtin-modules";
    private static final String XQUERY_BUILTIN_MODULES_CONFIGURATION_MODULE_ELEMENT_NAME = "module";
    private final Map<String, Object> config = new HashMap<>(); //Configuration
    protected Optional<Path> configFilePath = Optional.empty();
    protected Optional<Path> existHome = Optional.empty();

    public Configuration() throws DatabaseConfigurationException {
        this(DatabaseImpl.CONF_XML, Optional.empty());
    }

    public Configuration(@Nullable String configFilename) throws DatabaseConfigurationException {
        this(configFilename, Optional.empty());
    }

    public Configuration(@Nullable String configFilename, Optional<Path> existHomeDirname)
            throws DatabaseConfigurationException {
        InputStream is = null;
        try {
            if (configFilename == null) {
                // Default file name
                configFilename = DatabaseImpl.CONF_XML;
            }

            // firstly, try to read the configuration from a file within the
            // classpath
            try {
                is = Configuration.class.getClassLoader().getResourceAsStream(configFilename);

                if (is != null) {
                    LOG.info("Reading configuration from classloader");
                    configFilePath = Optional.of(Paths.get(Configuration.class.getClassLoader().getResource(configFilename).toURI()));
                }
            } catch (final Exception e) {
                // EB: ignore and go forward, e.g. in case there is an absolute
                // file name for configFileName
                LOG.debug(e);
            }

            existHomeDirname = existHomeDirname.map(Path::normalize);

            // otherwise, secondly try to read configuration from file. Guess the
            // location if necessary
            if (is == null) {
                existHome = existHomeDirname.map(Optional::of)
                        .orElse(ConfigurationHelper.getExistHome(configFilename));

                if (existHome.isEmpty()) {

                    // EB: try to create existHome based on location of config file
                    // when config file points to absolute file location
                    final Path absoluteConfigFile = Paths.get(configFilename);

                    if (absoluteConfigFile.isAbsolute() && Files.exists(absoluteConfigFile) && Files.isReadable(absoluteConfigFile)) {
                        existHome = Optional.of(absoluteConfigFile.getParent());
                        configFilename = FileUtils.fileName(absoluteConfigFile);
                    }
                }

                Path configFile = Paths.get(configFilename);

                if (!configFile.isAbsolute() && existHome.isPresent()) {

                    // try the passed or constructed existHome first
                    configFile = existHome.get().resolve(configFilename);

                    if (!Files.exists(configFile)) {
                        configFile = existHome.get().resolve(Main.CONFIG_DIR_NAME).resolve(configFilename);
                    }
                }

                if (!Files.exists(configFile) || !Files.isReadable(configFile)) {
                    throw new DatabaseConfigurationException("Unable to read configuration file at " + configFile);
                }

                configFilePath = Optional.of(configFile.toAbsolutePath());
                is = Files.newInputStream(configFile);
            }

            LOG.info("Reading configuration from file {}", configFilePath.map(Path::toString).orElse("Unknown"));

            // set dbHome to parent of the conf file found, to resolve relative
            // path from conf file
            final Optional<Path> existHome = configFilePath.map(Path::getParent);

            // initialize xml parser
            // we use eXist's in-memory DOM implementation to work
            // around a bug in Xerces
            final SAXParserFactory factory = ExistSAXParserFactory.getSAXParserFactory();
            factory.setNamespaceAware(true);

            final InputSource src = new InputSource(is);
            final SAXParser parser = factory.newSAXParser();
            final XMLReader reader = parser.getXMLReader();

            reader.setFeature("http://xml.org/sax/features/external-general-entities", false);
            reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            reader.setFeature(FEATURE_SECURE_PROCESSING, true);

            final SAXAdapter adapter = new SAXAdapter((Expression) null);
            reader.setContentHandler(adapter);
            reader.setProperty(Namespaces.SAX_LEXICAL_HANDLER, adapter);
            reader.parse(src);

            final Document doc = adapter.getDocument();

            //indexer settings
            configureElement(doc, Indexer.CONFIGURATION_ELEMENT_NAME, element -> configureIndexer(doc, element));
            //scheduler settings
            configureElement(doc, JobConfig.CONFIGURATION_ELEMENT_NAME, this::configureScheduler);
            //db connection settings
            configureElement(doc, CONFIGURATION_CONNECTION_ELEMENT_NAME, element -> configureBackend(existHome, element));
            // lock-table settings
            configureElement(doc, "lock-manager", this::configureLockManager);
            // repository settings
            configureElement(doc, "repository", this::configureRepository);
            // binary manager settings
            configureElement(doc, "binary-manager", this::configureBinaryManager);
            // transformer settings
            configureElement(doc, TransformerFactoryAllocator.CONFIGURATION_ELEMENT_NAME, this::configureTransformer);
            // saxon settings (most importantly license file for PE or EE features)
            configureElement(doc, SaxonConfiguration.SAXON_CONFIGURATION_ELEMENT_NAME, this::configureSaxon);
            // parser settings
            configureElement(doc, HtmlToXmlParser.PARSER_ELEMENT_NAME, this::configureParser);
            // serializer settings
            configureElement(doc, Serializer.CONFIGURATION_ELEMENT_NAME, this::configureSerializer);
            // XUpdate settings
            configureElement(doc, DBBroker.CONFIGURATION_ELEMENT_NAME, this::configureXUpdate);
            // XQuery settings
            configureElement(doc, XQUERY_CONFIGURATION_ELEMENT_NAME, this::configureXQuery);
            // Validation
            configureElement(doc, XMLReaderObjectFactory.CONFIGURATION_ELEMENT_NAME, element -> configureValidation(existHome, element));
            // RPC server
            configureElement(doc, "rpc-server", this::configureRpcServer);
        } catch (final SAXException | IOException | ParserConfigurationException e) {
            LOG.error("error while reading config file: {}", configFilename, e);
            throw new DatabaseConfigurationException(e.getMessage(), e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (final IOException ioe) {
                    LOG.error(ioe);
                }
            }
        }
    }

    @FunctionalInterface
    interface ElementConfigurationAction {
        void apply(Element element) throws DatabaseConfigurationException;
    }
    void configureElement(Document doc, String elementName, ElementConfigurationAction action)
            throws DatabaseConfigurationException {
        configureFirstElement(doc.getElementsByTagName(elementName), action);
    }

    void configureElement(Element parent, String elementName, ElementConfigurationAction action)
            throws DatabaseConfigurationException {
        configureFirstElement(parent.getElementsByTagName(elementName), action);
    }

    void configureFirstElement(NodeList nodeList, ElementConfigurationAction action)
            throws DatabaseConfigurationException {
        if (nodeList.getLength() > 0) {
            action.apply((Element)nodeList.item(0));
        }
    }

    /**
     * Takes the passed string and converts it to a non-null <code>Boolean</code> object. If value is null, the specified default value is used.
     * Otherwise, Boolean.TRUE is returned if and only if the passed string equals &quot;yes&quot; or &quot;true&quot;, ignoring case.
     *
     * @param value        The string to parse
     * @param defaultValue The default if the string is null
     * @return The parsed <code>Boolean</code>
     */
    public static boolean parseBoolean(@Nullable final String value, final boolean defaultValue) {
        return Optional.ofNullable(value)
                .map(v -> v.equalsIgnoreCase("yes") || v.equalsIgnoreCase("true"))
                .orElse(defaultValue);
    }

    /**
     * Takes the passed string and converts it to a non-null <code>int</code> value. If value is null, the specified default value is used.
     * Otherwise, Boolean.TRUE is returned if and only if the passed string equals &quot;yes&quot; or &quot;true&quot;, ignoring case.
     *
     * @param value        The string to parse
     * @param defaultValue The default if the string is null or empty
     * @return The parsed <code>int</code>
     */
    public static int parseInt(@Nullable final String value, final int defaultValue) {
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(value);
        } catch (final NumberFormatException e) {
            LOG.warn("Could not parse: {}, as an int: {}", value, e.getMessage());
            return defaultValue;
        }
    }

    private void configureLockManager(final Element lockManager) throws DatabaseConfigurationException {
        final boolean upgradeCheck = parseBoolean(getConfigAttributeValue(lockManager, "upgrade-check"), false);
        final boolean warnWaitOnReadForWrite = parseBoolean(getConfigAttributeValue(lockManager, "warn-wait-on-read-for-write"), false);
        final boolean pathsMultiWriter = parseBoolean(getConfigAttributeValue(lockManager, "paths-multi-writer"), false);

        setProperty(LockManager.CONFIGURATION_UPGRADE_CHECK, upgradeCheck);
        setProperty(LockManager.CONFIGURATION_WARN_WAIT_ON_READ_FOR_WRITE, warnWaitOnReadForWrite);
        setProperty(LockManager.CONFIGURATION_PATHS_MULTI_WRITER, pathsMultiWriter);

        configureElement(lockManager, "lock-table", lockTable -> {
            final boolean lockTableDisabled = parseBoolean(getConfigAttributeValue(lockTable, "disabled"), false);
            final int lockTableTraceStackDepth = parseInt(getConfigAttributeValue(lockTable, "trace-stack-depth"), 0);

            setProperty(LockTable.CONFIGURATION_DISABLED, lockTableDisabled);
            setProperty(LockTable.CONFIGURATION_TRACE_STACK_DEPTH, lockTableTraceStackDepth);
        });

        configureElement(lockManager, "document", document -> {
            final boolean documentUsePathLocks = parseBoolean(getConfigAttributeValue(document, "use-path-locks"), false);

            setProperty(LockManager.CONFIGURATION_PATH_LOCKS_FOR_DOCUMENTS, documentUsePathLocks);
        });
    }

    private void configureRepository(final Element element) {
        String root = getConfigAttributeValue(element, "root");
        if (root == null || root.isEmpty()) {
            return;
        }

        if (!root.endsWith("/")) {
            root += "/";
        }

        setProperty(Deployment.PROPERTY_APP_ROOT, root);
    }

    private void configureBinaryManager(final Element binaryManager) throws DatabaseConfigurationException {
        configureElement(binaryManager, "cache", cache -> {
            final String binaryCacheClass = getConfigAttributeValue(cache, "class");
            setProperty(BINARY_CACHE_CLASS_PROPERTY, binaryCacheClass);
        });
    }

    private void configureXQuery(final Element xquery) throws DatabaseConfigurationException {
        //java binding
        final String javabinding = getConfigAttributeValue(xquery, ENABLE_JAVA_BINDING_ATTRIBUTE);

        if (javabinding != null) {
            setProperty(PROPERTY_ENABLE_JAVA_BINDING, javabinding);
        }

        final String disableDeprecated = getConfigAttributeValue(xquery, DISABLE_DEPRECATED_FUNCTIONS_ATTRIBUTE);
        setProperty(PROPERTY_DISABLE_DEPRECATED_FUNCTIONS,
                Configuration.parseBoolean(disableDeprecated, DISABLE_DEPRECATED_FUNCTIONS_BY_DEFAULT));

        final String optimize = getConfigAttributeValue(xquery, ENABLE_QUERY_REWRITING_ATTRIBUTE);

        if (optimize != null && !optimize.isEmpty()) {
            setProperty(PROPERTY_ENABLE_QUERY_REWRITING, optimize);
        }

        final String enforceIndexUse = getConfigAttributeValue(xquery, ENFORCE_INDEX_USE_ATTRIBUTE);
        if (enforceIndexUse != null) {
            setProperty(PROPERTY_ENFORCE_INDEX_USE, enforceIndexUse);
        }

        final String backwardCompatible = getConfigAttributeValue(xquery, XQUERY_BACKWARD_COMPATIBLE_ATTRIBUTE);

        if ((backwardCompatible != null) && (!backwardCompatible.isEmpty())) {
            setProperty(PROPERTY_XQUERY_BACKWARD_COMPATIBLE, backwardCompatible);
        }

        final String raiseErrorOnFailedRetrieval = getConfigAttributeValue(xquery,
                XQUERY_RAISE_ERROR_ON_FAILED_RETRIEVAL_ATTRIBUTE);
        setProperty(PROPERTY_XQUERY_RAISE_ERROR_ON_FAILED_RETRIEVAL,
                Configuration.parseBoolean(raiseErrorOnFailedRetrieval, XQUERY_RAISE_ERROR_ON_FAILED_RETRIEVAL_DEFAULT));

        final String trace = getConfigAttributeValue(xquery, PerformanceStats.CONFIG_ATTR_TRACE);
        setProperty(PerformanceStats.CONFIG_PROPERTY_TRACE, trace);

        // built-in-modules
        final Map<String, Class<?>> classMap = new HashMap<>();
        final Map<String, String> knownMappings = new HashMap<>();
        final Map<String, Map<String, List<? extends Object>>> moduleParameters = new HashMap<>();
        loadModuleClasses(xquery, classMap, knownMappings, moduleParameters);
        setProperty(PROPERTY_BUILT_IN_MODULES, classMap);
        setProperty(PROPERTY_STATIC_MODULE_MAP, knownMappings);
        setProperty(PROPERTY_MODULE_PARAMETERS, moduleParameters);
    }

    /**
     * Read list of built-in modules from the configuration. This method will only make sure
     * that the specified module class exists and is a subclass of {@link org.exist.xquery.Module}.
     *
     * @param xquery           configuration root
     * @param modulesClassMap  map containing all classes of modules
     * @param modulesSourceMap map containing all source uris to external resources
     * @throws DatabaseConfigurationException if one of the modules is configured incorrectly
     */
    private void loadModuleClasses(final Element xquery,
                                   final Map<String, Class<?>> modulesClassMap,
                                   final Map<String, String> modulesSourceMap,
                                   final Map<String, Map<String, List<? extends Object>>> moduleParameters
    ) throws DatabaseConfigurationException {
        // add the standard function module
        modulesClassMap.put(XPATH_FUNCTIONS_NS, org.exist.xquery.functions.fn.FnModule.class);

        // add other modules specified in configuration
        configureElement(xquery, XQUERY_BUILTIN_MODULES_CONFIGURATION_MODULES_ELEMENT_NAME, builtIn -> {

            final NodeList modules = builtIn.getElementsByTagName(XQUERY_BUILTIN_MODULES_CONFIGURATION_MODULE_ELEMENT_NAME);

            // iterate over all <module src= uri= class=> entries
            for (int i = 0; i < modules.getLength(); i++) {
                // Get element.
                final Element elem = (Element) modules.item(i);

                // Get attributes uri class and src
                final String uri = elem.getAttribute(BUILT_IN_MODULE_URI_ATTRIBUTE);
                final String clazz = elem.getAttribute(BUILT_IN_MODULE_CLASS_ATTRIBUTE);
                final String source = elem.getAttribute(BUILT_IN_MODULE_SOURCE_ATTRIBUTE);

                // uri attribute is the identifier and is always required
                if (uri == null) {
                    throw (new DatabaseConfigurationException("element 'module' requires an attribute 'uri'"));
                }

                // either class or source attribute must be present
                if (clazz == null && source == null) {
                    throw (new DatabaseConfigurationException("element 'module' requires either an attribute " + "'class' or 'src'"));
                }

                if (source != null) {
                    // Store src attribute info

                    modulesSourceMap.put(uri, source);

                    LOG.debug("Registered mapping for module '{}' to '{}'", uri, source);
                } else {
                    // source class attribute info

                    // Get class of module
                    final Class<?> moduleClass = lookupModuleClass(uri, clazz);

                    // Store class if thw module class actually exists
                    if (moduleClass != null) {
                        modulesClassMap.put(uri, moduleClass);
                    }

                    LOG.debug("Configured module '{}' implemented in '{}'", uri, clazz);
                }

                //parse any module parameters
                moduleParameters.put(uri, ParametersExtractor.extract(
                        elem.getElementsByTagName(PARAMETER_ELEMENT_NAME)));
            }
        });
    }

    /**
     * Returns the Class object associated with the given module class name. All
     * important exceptions are caught. @see org.exist.xquery.Module
     *
     * @param uri   namespace of class. For logging purposes only.
     * @param clazz the fully qualified name of the desired module class.
     * @return the module Class object for the module with the specified name.
     * @throws DatabaseConfigurationException if the given module class is not an instance
     *                                        of org.exist.xquery.Module
     */
    private Class<?> lookupModuleClass(final String uri, final String clazz) throws DatabaseConfigurationException {
        try {
            final Class<?> mClass = Class.forName(clazz);

            if (!(org.exist.xquery.Module.class.isAssignableFrom(mClass))) {
                throw (new DatabaseConfigurationException("Failed to load module: " + uri + ". " +
                        "Class " + clazz + " is not an instance of org.exist.xquery.Module."));
            }
            return mClass;
        } catch (final ClassNotFoundException e) {
            // Note: can't throw an exception here since this would create
            // problems with test cases and jar dependencies
            LOG.error("Configuration problem: class not found for module '{}' (ClassNotFoundException); " +
                    "class:'{}'; message:'{}'", uri, clazz, e.getMessage());

        } catch (final NoClassDefFoundError e) {
            LOG.error("Module {} could not be initialized due to a missing dependency (NoClassDefFoundError): " +
                    "{}", uri, e.getMessage(), e);
        }
        return null;
    }

    /**
     * DOCUMENT ME!
     *
     * @param xupdate configuration element
     * @throws NumberFormatException if one of the settings is not parseable
     */
    private void configureXUpdate(final Element xupdate) throws NumberFormatException {
        final String fragmentation = getConfigAttributeValue(xupdate, XUPDATE_FRAGMENTATION_FACTOR_ATTRIBUTE);

        if (fragmentation != null) {
            setProperty(PROPERTY_XUPDATE_FRAGMENTATION_FACTOR, Integer.valueOf(fragmentation));
        }

        final String consistencyCheck = getConfigAttributeValue(xupdate, XUPDATE_CONSISTENCY_CHECKS_ATTRIBUTE);

        if (consistencyCheck != null) {
            setProperty(PROPERTY_XUPDATE_CONSISTENCY_CHECKS, parseBoolean(consistencyCheck, false));
        }
    }

    private void configureSaxon(final Element saxon) {
        final String configurationFile = getConfigAttributeValue( saxon,
            SaxonConfiguration.SAXON_CONFIGURATION_FILE_ATTRIBUTE);
        if (configurationFile != null) {
            setProperty(SaxonConfiguration.SAXON_CONFIGURATION_FILE_PROPERTY, configurationFile);
        }
    }

    private void configureTransformer(final Element transformer) {
        final String className = getConfigAttributeValue(transformer, TRANSFORMER_CLASS_ATTRIBUTE);

        if (className != null) {
            setProperty(PROPERTY_TRANSFORMER_CLASS, className);

            // Process any specified attributes that should be passed to the transformer factory

            final NodeList attrs = transformer.getElementsByTagName(CONFIGURATION_TRANSFORMER_ATTRIBUTE_ELEMENT_NAME);
            final Hashtable<Object, Object> attributes = new Properties();

            for (int a = 0; a < attrs.getLength(); a++) {
                final Element attr = (Element) attrs.item(a);
                final String name = attr.getAttribute("name");
                final String value = attr.getAttribute("value");
                final String type = attr.getAttribute("type");

                if (name == null || name.isEmpty()) {
                    LOG.warn("Discarded invalid attribute for TransformerFactory: '{}', name not specified", className);
                } else if (type == null || type.isEmpty() || type.equalsIgnoreCase("string")) {
                    attributes.put(name, value);
                } else if (type.equalsIgnoreCase("boolean")) {
                    attributes.put(name, Boolean.valueOf(value));
                } else if (type.equalsIgnoreCase("integer")) {
                    try {
                        attributes.put(name, Integer.valueOf(value));
                    } catch (final NumberFormatException nfe) {
                        LOG.warn("Discarded invalid attribute for TransformerFactory: '{}', name: {}, value not integer: {}", className, name, value, nfe);
                    }
                } else {
                    // Assume string type
                    attributes.put(name, value);
                }
            }

            setProperty(PROPERTY_TRANSFORMER_ATTRIBUTES, attributes);
        }

        final String cachingValue = getConfigAttributeValue(transformer, TRANSFORMER_CACHING_ATTRIBUTE);

        if (cachingValue != null) {
            setProperty(PROPERTY_CACHING_ATTRIBUTE, parseBoolean(cachingValue, false));
        }
    }

    private void configureParser(final Element parser) throws DatabaseConfigurationException {
        configureXmlParser(parser);
        configureHtmlToXmlParser(parser);
    }

    private void configureXmlParser(final Element parser) throws DatabaseConfigurationException {
        configureElement(parser, XML_PARSER_ELEMENT, xml -> {
            configureElement(xml, XML_PARSER_FEATURES_ELEMENT, nlFeature -> {
                final Properties pFeatures = ParametersExtractor.parseFeatures(nlFeature);

                if (pFeatures.isEmpty()) {
                    return;
                }

                final Map<String, Boolean> features = new HashMap<>();
                pFeatures.forEach((k, v) -> features.put(k.toString(), Boolean.valueOf(v.toString())));
                setProperty(XML_PARSER_FEATURES_PROPERTY, features);
            });
        });
    }

    private void configureHtmlToXmlParser(final Element parser) throws DatabaseConfigurationException {
        configureElement(parser, HTML_TO_XML_PARSER_ELEMENT, htmlToXml -> {
            final String htmlToXmlParserClass = getConfigAttributeValue(htmlToXml, HTML_TO_XML_PARSER_CLASS_ATTRIBUTE);
            setProperty(HTML_TO_XML_PARSER_PROPERTY, htmlToXmlParserClass);

            configureElement(htmlToXml, HTML_TO_XML_PARSER_PROPERTIES_ELEMENT, nlProperties -> {
                final Properties pProperties = ParametersExtractor.parseProperties(nlProperties);
                final Map<String, Object> properties = new HashMap<>();
                pProperties.forEach((k, v) -> properties.put(k.toString(), v));
                setProperty(HTML_TO_XML_PARSER_PROPERTIES_PROPERTY, properties);
            });

            configureElement(htmlToXml, HTML_TO_XML_PARSER_FEATURES_ELEMENT, nlFeatures -> {
                final Properties pFeatures = ParametersExtractor.parseFeatures(nlFeatures);
                final Map<String, Boolean> features = new HashMap<>();
                pFeatures.forEach((k, v) -> features.put(k.toString(), Boolean.valueOf(v.toString())));
                setProperty(HTML_TO_XML_PARSER_FEATURES_PROPERTY, features);
            });
        });
    }

    /**
     * DOCUMENT ME!
     *
     * @param serializer element with serializer settings
     */
    private void configureSerializer(final Element serializer) {
        final String omitXmlDeclaration = getConfigAttributeValue(serializer, OMIT_XML_DECLARATION_ATTRIBUTE);
        if (omitXmlDeclaration != null) {
            setProperty(PROPERTY_OMIT_XML_DECLARATION, omitXmlDeclaration);
        }

        final String omitOriginalXmlDeclaration = getConfigAttributeValue(serializer, OMIT_ORIGINAL_XML_DECLARATION_ATTRIBUTE);
        if (omitOriginalXmlDeclaration != null) {
            setProperty(PROPERTY_OMIT_ORIGINAL_XML_DECLARATION, omitOriginalXmlDeclaration);
        }

        final String outputDocType = getConfigAttributeValue(serializer, OUTPUT_DOCTYPE_ATTRIBUTE);
        if (outputDocType != null) {
            setProperty(PROPERTY_OUTPUT_DOCTYPE, outputDocType);
        }

        final String xinclude = getConfigAttributeValue(serializer, ENABLE_XINCLUDE_ATTRIBUTE);
        if (xinclude != null) {
            setProperty(PROPERTY_ENABLE_XINCLUDE, xinclude);
        }

        final String xsl = getConfigAttributeValue(serializer, ENABLE_XSL_ATTRIBUTE);
        if (xsl != null) {
            setProperty(PROPERTY_ENABLE_XSL, xsl);
        }

        final String indent = getConfigAttributeValue(serializer, INDENT_ATTRIBUTE);
        if (indent != null) {
            setProperty(PROPERTY_INDENT, indent);
        }

        final String compress = getConfigAttributeValue(serializer, COMPRESS_OUTPUT_ATTRIBUTE);
        if (compress != null) {
            setProperty(PROPERTY_COMPRESS_OUTPUT, compress);
        }

        final String internalId = getConfigAttributeValue(serializer, ADD_EXIST_ID_ATTRIBUTE);
        if (internalId != null) {
            setProperty(PROPERTY_ADD_EXIST_ID, internalId);
        }

        final String tagElementMatches = getConfigAttributeValue(serializer, TAG_MATCHING_ELEMENTS_ATTRIBUTE);
        if (tagElementMatches != null) {
            setProperty(PROPERTY_TAG_MATCHING_ELEMENTS, tagElementMatches);
        }

        final String tagAttributeMatches = getConfigAttributeValue(serializer, TAG_MATCHING_ATTRIBUTES_ATTRIBUTE);
        if (tagAttributeMatches != null) {
            setProperty(PROPERTY_TAG_MATCHING_ATTRIBUTES, tagAttributeMatches);
        }

        final NodeList nlFilters = serializer.getElementsByTagName(CustomMatchListenerFactory.CONFIGURATION_ELEMENT);
        if (nlFilters.getLength() > 0) {
            final List<String> filters = new ArrayList<>(nlFilters.getLength());

            for (int i = 0; i < nlFilters.getLength(); i++) {
                final Element filterElem = (Element) nlFilters.item(i);
                final String filterClass = filterElem.getAttribute(CustomMatchListenerFactory.CONFIGURATION_ATTR_CLASS);

                if (filterClass != null) {
                    filters.add(filterClass);
                    LOG.debug(PRP_DETAILS, CustomMatchListenerFactory.CONFIG_MATCH_LISTENERS, filterClass);
                } else {
                    LOG.warn("Configuration element " + CustomMatchListenerFactory.CONFIGURATION_ELEMENT + " needs an attribute 'class'");
                }
            }
            setProperty(CustomMatchListenerFactory.CONFIG_MATCH_LISTENERS, filters);
        }

        final NodeList backupFilters = serializer.getElementsByTagName(SystemExport.CONFIGURATION_ELEMENT);
        if (backupFilters.getLength() > 0) {
            final List<String> filters = new ArrayList<>(backupFilters.getLength());

            for (int i = 0; i < backupFilters.getLength(); i++) {
                final Element filterElem = (Element) backupFilters.item(i);
                final String filterClass = filterElem.getAttribute(CustomMatchListenerFactory.CONFIGURATION_ATTR_CLASS);

                if (filterClass != null) {
                    filters.add(filterClass);
                    LOG.debug(PRP_DETAILS, CustomMatchListenerFactory.CONFIG_MATCH_LISTENERS, filterClass);
                } else {
                    LOG.warn("Configuration element " + SystemExport.CONFIGURATION_ELEMENT + " needs an attribute 'class'");
                }
                if (!filters.isEmpty()) {
                    setProperty(SystemExport.CONFIG_FILTERS, filters);
                }
            }
        }
    }

    /**
     * Reads the scheduler configuration.
     *
     * @param scheduler DOCUMENT ME!
     */
    private void configureScheduler(final Element scheduler) {
        final NodeList nlJobs = scheduler.getElementsByTagName(JobConfig.CONFIGURATION_JOB_ELEMENT_NAME);
        final List<JobConfig> jobList = new ArrayList<>();

        for (int i = 0; i < nlJobs.getLength(); i++) {
            final Element job = (Element) nlJobs.item(i);
            if (job != null) {
                addJobToList(jobList, job);
            }
        }

        if (jobList.isEmpty()) {
            return;
        }

        final JobConfig[] jobConfigs = new JobConfig[jobList.size()];
        for (int i = 0; i < jobList.size(); i++) {
            jobConfigs[i] = jobList.get(i);
        }
        setProperty(PROPERTY_SCHEDULER_JOBS, jobConfigs);
    }

    private void addJobToList(final List<JobConfig> jobList, final Element job) {
        //get the job type
        final String strJobType = getConfigAttributeValue(job, JOB_TYPE_ATTRIBUTE);

        final JobType jobType;
        if (strJobType == null) {
            jobType = JobType.USER; //default to user if unspecified
        } else {
            jobType = JobType.valueOf(strJobType.toUpperCase(Locale.ENGLISH));
        }

        final String jobName = getConfigAttributeValue(job, JOB_NAME_ATTRIBUTE);

        //get the job resource
        String jobResource = getConfigAttributeValue(job, JOB_CLASS_ATTRIBUTE);
        if (jobResource == null) {
            jobResource = getConfigAttributeValue(job, JOB_XQUERY_ATTRIBUTE);
        }

        //get the job schedule
        String jobSchedule = getConfigAttributeValue(job, JOB_CRON_TRIGGER_ATTRIBUTE);
        if (jobSchedule == null) {
            jobSchedule = getConfigAttributeValue(job, JOB_PERIOD_ATTRIBUTE);
        }

        final String jobUnschedule = getConfigAttributeValue(job, JOB_UNSCHEDULE_ON_EXCEPTION);

        //create the job config
        try {
            final JobConfig jobConfig = new JobConfig(jobType, jobName, jobResource, jobSchedule, jobUnschedule);

            //get and set the job delay
            final String jobDelay = getConfigAttributeValue(job, JOB_DELAY_ATTRIBUTE);
            if (jobDelay != null && !jobDelay.isEmpty()) {
                jobConfig.setDelay(Long.parseLong(jobDelay));
            }

            //get and set the job repeat
            final String jobRepeat = getConfigAttributeValue(job, JOB_REPEAT_ATTRIBUTE);
            if (jobRepeat != null && !jobRepeat.isEmpty()) {
                jobConfig.setRepeat(Integer.parseInt(jobRepeat));
            }

            final NodeList nlParam = job.getElementsByTagName(PARAMETER_ELEMENT_NAME);
            final Map<String, List<? extends Object>> params = ParametersExtractor.extract(nlParam);

            for (final Entry<String, List<?>> param : params.entrySet()) {
                final List<?> values = param.getValue();
                if (values != null && !values.isEmpty()) {
                    jobConfig.addParameter(param.getKey(), values.get(0).toString());

                    if (values.size() > 1) {
                        LOG.warn("Parameter '{}' for job '{}' has more than one value, ignoring further values.", param.getKey(), jobName);
                    }
                }
            }

            jobList.add(jobConfig);

            LOG.debug("Configured scheduled '{}' job '{}{}{}{}'", jobType, jobResource,
                    (jobSchedule == null) ? "" : ("' with trigger '" + jobSchedule),
                    (jobDelay == null) ? "" : ("' with delay '" + jobDelay),
                    (jobRepeat == null) ? "" : ("' repetitions '" + jobRepeat));
        } catch (final JobException je) {
            LOG.error(je);
        }
    }

    /**
     * DOCUMENT ME!
     *
     * @param dbHome
     * @param con
     * @throws DatabaseConfigurationException
     */
    private void configureBackend(final Optional<Path> dbHome, Element con) throws DatabaseConfigurationException {
        final String database = getConfigAttributeValue(con, PROPERTY_DATABASE);
        if (database != null) {
            setProperty(PROPERTY_DATABASE, database);
        }

        // directory for database files
        final String dataFiles = getConfigAttributeValue(con, DATA_DIR_ATTRIBUTE);

        if (dataFiles != null) {
            final Path df = ConfigurationHelper.lookup(dataFiles, dbHome);
            if (!Files.isReadable(df)) {
                try {
                    Files.createDirectories(df);
                } catch (final IOException ioe) {
                    throw new DatabaseConfigurationException("cannot read data directory: " + df, ioe);
                }
            }
            setProperty(PROPERTY_DATA_DIR, df);
        }

        String cacheMem = getConfigAttributeValue(con, CACHE_SIZE_ATTRIBUTE);

        if (cacheMem != null) {

            if (cacheMem.endsWith("M") || cacheMem.endsWith("m")) {
                cacheMem = cacheMem.substring(0, cacheMem.length() - 1);
            }

            try {
                setProperty(PROPERTY_CACHE_SIZE, Integer.valueOf(cacheMem));
            } catch (final NumberFormatException nfe) {
                LOG.warn("Cannot convert " + PROPERTY_CACHE_SIZE + " value to integer: {}", cacheMem, nfe);
            }
        }

        // Process the Check Max Cache value

        String checkMaxCache = getConfigAttributeValue(con, CACHE_CHECK_MAX_SIZE_ATTRIBUTE);

        if (checkMaxCache == null) {
            checkMaxCache = DEFAULT_CACHE_CHECK_MAX_SIZE_STRING;
        }

        setProperty(PROPERTY_CACHE_CHECK_MAX_SIZE, parseBoolean(checkMaxCache, true));

        String cacheShrinkThreshold = getConfigAttributeValue(con, SHRINK_THRESHOLD_ATTRIBUTE);

        if (cacheShrinkThreshold == null) {
            cacheShrinkThreshold = DefaultCacheManager.DEFAULT_SHRINK_THRESHOLD_STRING;
        }

        try {
            setProperty(SHRINK_THRESHOLD_PROPERTY, Integer.valueOf(cacheShrinkThreshold));
        } catch (final NumberFormatException nfe) {
            LOG.warn("Cannot convert " + SHRINK_THRESHOLD_PROPERTY + " value to integer: {}", cacheShrinkThreshold, nfe);
        }

        String collectionCache = getConfigAttributeValue(con, CollectionCache.CACHE_SIZE_ATTRIBUTE);
        if (collectionCache != null) {
            collectionCache = collectionCache.toLowerCase();

            try {
                final int collectionCacheBytes;
                if (collectionCache.endsWith("k")) {
                    collectionCacheBytes = 1024 * Integer.parseInt(collectionCache.substring(0, collectionCache.length() - 1));
                } else if (collectionCache.endsWith("kb")) {
                    collectionCacheBytes = 1024 * Integer.parseInt(collectionCache.substring(0, collectionCache.length() - 2));
                } else if (collectionCache.endsWith("m")) {
                    collectionCacheBytes = 1024 * 1024 * Integer.parseInt(collectionCache.substring(0, collectionCache.length() - 1));
                } else if (collectionCache.endsWith("mb")) {
                    collectionCacheBytes = 1024 * 1024 * Integer.parseInt(collectionCache.substring(0, collectionCache.length() - 2));
                } else if (collectionCache.endsWith("g")) {
                    collectionCacheBytes = 1024 * 1024 * 1024 * Integer.parseInt(collectionCache.substring(0, collectionCache.length() - 1));
                } else if (collectionCache.endsWith("gb")) {
                    collectionCacheBytes = 1024 * 1024 * 1024 * Integer.parseInt(collectionCache.substring(0, collectionCache.length() - 2));
                } else {
                    collectionCacheBytes = Integer.parseInt(collectionCache);
                }

                setProperty(PROPERTY_CACHE_SIZE_BYTES, collectionCacheBytes);
            } catch (final NumberFormatException nfe) {
                LOG.warn("Cannot convert " + PROPERTY_CACHE_SIZE_BYTES + " value to integer: {}", collectionCache, nfe);
            }
        }

        final String pageSize = getConfigAttributeValue(con, NativeBroker.PAGE_SIZE_ATTRIBUTE);
        if (pageSize != null) {
            try {
                setProperty(PROPERTY_PAGE_SIZE, Integer.valueOf(pageSize));
            } catch (final NumberFormatException nfe) {
                LOG.warn("Cannot convert " + PROPERTY_PAGE_SIZE + " value to integer: {}", pageSize, nfe);
            }
        }

        //Not clear : rather looks like a buffers count
        final String collCacheSize = getConfigAttributeValue(con, BrokerPool.COLLECTION_CACHE_SIZE_ATTRIBUTE);
        if (collCacheSize != null) {
            try {
                setProperty(PROPERTY_COLLECTION_CACHE_SIZE, Integer.valueOf(collCacheSize));
            } catch (final NumberFormatException nfe) {
                LOG.warn("Cannot convert " + PROPERTY_COLLECTION_CACHE_SIZE + " value to integer: {}", collCacheSize, nfe);
            }
        }

        final String nodesBuffer = getConfigAttributeValue(con, BrokerPool.NODES_BUFFER_ATTRIBUTE);
        if (nodesBuffer != null) {
            try {
                setProperty(PROPERTY_NODES_BUFFER, Integer.valueOf(nodesBuffer));
            } catch (final NumberFormatException nfe) {
                LOG.warn("Cannot convert " + PROPERTY_NODES_BUFFER + " value to integer: {}", nodesBuffer, nfe);
            }
        }

        String diskSpace = getConfigAttributeValue(con, BrokerPool.DISK_SPACE_MIN_ATTRIBUTE);
        if (diskSpace != null) {
            if (diskSpace.endsWith("M") || diskSpace.endsWith("m")) {
                diskSpace = diskSpace.substring(0, diskSpace.length() - 1);
            }

            try {
                setProperty(DISK_SPACE_MIN_PROPERTY, Short.valueOf(diskSpace));
            } catch (final NumberFormatException nfe) {
                LOG.warn("Cannot convert " + DISK_SPACE_MIN_PROPERTY + " value to integer: {}", diskSpace, nfe);
            }
        }

        final String posixChownRestrictedStr = getConfigAttributeValue(con, POSIX_CHOWN_RESTRICTED_ATTRIBUTE);
        final boolean posixChownRestricted = posixChownRestrictedStr == null || Boolean.parseBoolean(posixChownRestrictedStr);
        setProperty(POSIX_CHOWN_RESTRICTED_PROPERTY, posixChownRestricted);

        final String preserveOnCopyStr = getConfigAttributeValue(con, PRESERVE_ON_COPY_ATTRIBUTE);
        // default or configuration explicitly specifies that attributes should be preserved on copy
        final PreserveType preserveOnCopy = Boolean.parseBoolean(preserveOnCopyStr)
                ? PreserveType.PRESERVE
                : PreserveType.NO_PRESERVE;
        setProperty(PRESERVE_ON_COPY_PROPERTY, preserveOnCopy);

        final NodeList startupConf = con.getElementsByTagName(BrokerPool.CONFIGURATION_STARTUP_ELEMENT_NAME);
        if (startupConf.getLength() > 0) {
            configureStartup((Element) startupConf.item(0));
        } else {
            // Prevent NPE
            final List<StartupTriggerConfig> startupTriggers = new ArrayList<>();
            setProperty(PROPERTY_STARTUP_TRIGGERS, startupTriggers);
        }

        configureElement(con, BrokerPool.CONFIGURATION_POOL_ELEMENT_NAME, this::configurePool);
        configureElement(con, XQueryPool.CONFIGURATION_ELEMENT_NAME, this::configureXQueryPool);
        configureElement(con, XQueryWatchDog.CONFIGURATION_ELEMENT_NAME, this::configureWatchdog);
        configureElement(con, BrokerPool.CONFIGURATION_RECOVERY_ELEMENT_NAME, element -> configureRecovery(dbHome, element));
    }

    private void configureRecovery(final Optional<Path> dbHome, final Element recovery) throws DatabaseConfigurationException {
        final String enabled = getConfigAttributeValue(recovery, RECOVERY_ENABLED_ATTRIBUTE);
        setProperty(PROPERTY_RECOVERY_ENABLED, parseBoolean(enabled, true));

        final String syncOnCommit = getConfigAttributeValue(recovery, RECOVERY_SYNC_ON_COMMIT_ATTRIBUTE);
        setProperty(PROPERTY_RECOVERY_SYNC_ON_COMMIT, parseBoolean(syncOnCommit, true));

        final String groupCommit = getConfigAttributeValue(recovery, RECOVERY_GROUP_COMMIT_ATTRIBUTE);
        setProperty(PROPERTY_RECOVERY_GROUP_COMMIT, parseBoolean(groupCommit, false));

        final String journalDir = getConfigAttributeValue(recovery, RECOVERY_JOURNAL_DIR_ATTRIBUTE);
        if (journalDir != null) {
            final Path rf = ConfigurationHelper.lookup(journalDir, dbHome);

            if (!Files.isReadable(rf)) {
                throw new DatabaseConfigurationException("cannot read data directory: " + rf);
            }
            setProperty(PROPERTY_RECOVERY_JOURNAL_DIR, rf);
        }

        final String sizeLimit = getConfigAttributeValue(recovery, RECOVERY_SIZE_LIMIT_ATTRIBUTE);
        if (sizeLimit != null) {
            try {
                final int size;
                if (sizeLimit.endsWith("M") || sizeLimit.endsWith("m")) {
                    size = Integer.parseInt(sizeLimit.substring(0, sizeLimit.length() - 1));
                } else {
                    size = Integer.parseInt(sizeLimit);
                }
                setProperty(PROPERTY_RECOVERY_SIZE_LIMIT, size);
            } catch (final NumberFormatException e) {
                throw (new DatabaseConfigurationException("size attribute in recovery section needs to be a number"));
            }
        }

        final String forceRestart = getConfigAttributeValue(recovery, RECOVERY_FORCE_RESTART_ATTRIBUTE);
        final boolean forceRestartValue = "yes".equals(forceRestart);
        setProperty(PROPERTY_RECOVERY_FORCE_RESTART, forceRestartValue);

        final String postRecoveryCheck = getConfigAttributeValue(recovery, RECOVERY_POST_RECOVERY_CHECK);
        final boolean postRecoveryCheckValue = "yes".equals(postRecoveryCheck);
        setProperty(PROPERTY_RECOVERY_CHECK, postRecoveryCheckValue);
    }

    /**
     * DOCUMENT ME!
     *
     * @param watchDog element with watchDog settings
     */
    private void configureWatchdog(final Element watchDog) {
        final String timeout = getConfigAttributeValue(watchDog, "query-timeout");

        if (timeout != null) {
            try {
                setProperty(PROPERTY_QUERY_TIMEOUT, Long.valueOf(timeout));
            } catch (final NumberFormatException e) {
                LOG.warn(e);
            }
        }

        final String maxOutput = getConfigAttributeValue(watchDog, "output-size-limit");

        if (maxOutput != null) {
            try {
                setProperty(PROPERTY_OUTPUT_SIZE_LIMIT, Integer.valueOf(maxOutput));
            } catch (final NumberFormatException e) {
                LOG.warn(e);
            }
        }
    }

    /**
     * DOCUMENT ME!
     *
     * @param queryPool element with queryPool settings
     */
    private void configureXQueryPool(final Element queryPool) {
        final String maxStackSize = getConfigAttributeValue(queryPool, MAX_STACK_SIZE_ATTRIBUTE);

        if (maxStackSize != null) {
            try {
                setProperty(PROPERTY_MAX_STACK_SIZE, Integer.valueOf(maxStackSize));
            } catch (final NumberFormatException e) {
                LOG.warn(e);
            }
        }

        final String maxPoolSize = getConfigAttributeValue(queryPool, POOL_SIZE_ATTTRIBUTE);

        if (maxPoolSize != null) {
            try {
                setProperty(XQueryPool.PROPERTY_POOL_SIZE, Integer.valueOf(maxPoolSize));
            } catch (final NumberFormatException e) {
                LOG.warn(e);
            }
        }
    }

    private void configureStartup(final Element startup) throws DatabaseConfigurationException {
        // Retrieve <triggers>
        configureElement(startup, "triggers", triggers -> {
            // Get <trigger>
            final NodeList nlTrigger = triggers.getElementsByTagName("trigger");

            // If <trigger> exists and there are more than 0
            if (nlTrigger.getLength() == 0) {
                return;
            }

            // Initialize trigger configuration
            List<StartupTriggerConfig> startupTriggers = (List<StartupTriggerConfig>) config.get(PROPERTY_STARTUP_TRIGGERS);
            if (startupTriggers == null) {
                startupTriggers = new ArrayList<>();
                setProperty(PROPERTY_STARTUP_TRIGGERS, startupTriggers);
            }

            // Iterate over <trigger> elements
            for (int i = 0; i < nlTrigger.getLength(); i++) {

                // Get <trigger> element
                final Element trigger = (Element) nlTrigger.item(i);

                // Get @class
                final String startupTriggerClass = trigger.getAttribute("class");

                boolean isStartupTrigger = false;
                try {
                    // Verify if class is StartupTrigger
                    for (final Class iface : Class.forName(startupTriggerClass).getInterfaces()) {
                        if ("org.exist.storage.StartupTrigger".equals(iface.getName())) {
                            isStartupTrigger = true;
                            break;
                        }
                    }

                    // if it actually is a StartupTrigger
                    if (isStartupTrigger) {
                        // Parse additional parameters
                        final Map<String, List<?>> params = ParametersExtractor.extract(trigger.getElementsByTagName(PARAMETER_ELEMENT_NAME));

                        // Register trigger
                        startupTriggers.add(new StartupTriggerConfig(startupTriggerClass, params));

                        // Done
                        LOG.info("Registered StartupTrigger: {}", startupTriggerClass);

                    } else {
                        LOG.warn("StartupTrigger: {} does not implement org.exist.storage.StartupTrigger. IGNORING!", startupTriggerClass);
                    }

                } catch (final ClassNotFoundException cnfe) {
                    LOG.error("Could not find StartupTrigger class: {}. {}", startupTriggerClass, cnfe.getMessage(), cnfe);
                }
            }
        });
    }

    private void configurePool(final Element pool) {
        final String min = getConfigAttributeValue(pool, MIN_CONNECTIONS_ATTRIBUTE);
        if (min != null) {
            try {
                setProperty(PROPERTY_MIN_CONNECTIONS, Integer.valueOf(min));
            } catch (final NumberFormatException e) {
                LOG.warn(e);
            }
        }

        final String max = getConfigAttributeValue(pool, MAX_CONNECTIONS_ATTRIBUTE);
        if (max != null) {
            try {
                setProperty(PROPERTY_MAX_CONNECTIONS, Integer.valueOf(max));
            } catch (final NumberFormatException e) {
                LOG.warn(e);
            }
        }

        final String sync = getConfigAttributeValue(pool, SYNC_PERIOD_ATTRIBUTE);
        if (sync != null) {
            try {
                setProperty(PROPERTY_SYNC_PERIOD, Long.valueOf(sync));
            } catch (final NumberFormatException e) {
                LOG.warn(e);
            }
        }

        final String maxShutdownWait = getConfigAttributeValue(pool, SHUTDOWN_DELAY_ATTRIBUTE);
        if (maxShutdownWait != null) {
            try {
                setProperty(PROPERTY_SHUTDOWN_DELAY, Long.valueOf(maxShutdownWait));
            } catch (final NumberFormatException e) {
                LOG.warn(e);
            }
        }
    }

    private void configureIndexer(final Document doc, final Element indexer) throws DatabaseConfigurationException {
        final String caseSensitive = getConfigAttributeValue(indexer, INDEX_CASE_SENSITIVE_ATTRIBUTE);

        if (caseSensitive != null) {
            setProperty(PROPERTY_INDEX_CASE_SENSITIVE, parseBoolean(caseSensitive, false));
        }

        int depth = 3;
        final String indexDepth = getConfigAttributeValue(indexer, INDEX_DEPTH_ATTRIBUTE);

        if (indexDepth != null) {
            try {
                depth = Integer.parseInt(indexDepth);

                if (depth < 3) {
                    LOG.warn("parameter index-depth should be >= 3 or you will experience a severe "
                            + "performance loss for node updates (XUpdate or XQuery update extensions)");
                    depth = 3;
                }
                setProperty(PROPERTY_INDEX_DEPTH, depth);
            } catch (final NumberFormatException e) {
                LOG.warn(e);
            }
        }

        final String suppressWS = getConfigAttributeValue(indexer, SUPPRESS_WHITESPACE_ATTRIBUTE);

        if (suppressWS != null) {
            setProperty(PROPERTY_SUPPRESS_WHITESPACE, suppressWS);
        }

        final String suppressWSmixed = getConfigAttributeValue(indexer, PRESERVE_WS_MIXED_CONTENT_ATTRIBUTE);

        if (suppressWSmixed != null) {
            setProperty(PROPERTY_PRESERVE_WS_MIXED_CONTENT, parseBoolean(suppressWSmixed, false));
        }

        // index settings
        final NodeList cl = doc.getElementsByTagName(CONFIGURATION_INDEX_ELEMENT_NAME);

        if (cl.getLength() > 0) {
            final Element elem = (Element) cl.item(0);
            final IndexSpec spec = new IndexSpec(null, elem);
            setProperty(PROPERTY_INDEXER_CONFIG, spec);
        }

        // index modules
        final NodeList modules = indexer.getElementsByTagName(IndexManager.CONFIGURATION_ELEMENT_NAME);

        if (modules.getLength() == 0) {
            return;
        }
        final NodeList module = ((Element) modules.item(0)).getElementsByTagName(IndexManager.CONFIGURATION_MODULE_ELEMENT_NAME);
        final IndexModuleConfig[] modConfig = new IndexModuleConfig[module.getLength()];

        for (int i = 0; i < module.getLength(); i++) {
            final Element elem = (Element) module.item(i);
            final String className = elem.getAttribute(IndexManager.INDEXER_MODULES_CLASS_ATTRIBUTE);
            final String id = elem.getAttribute(IndexManager.INDEXER_MODULES_ID_ATTRIBUTE);

            if (className == null || className.isEmpty()) {
                throw (new DatabaseConfigurationException("Required attribute class is missing for module"));
            }

            if (id == null || id.isEmpty()) {
                throw (new DatabaseConfigurationException("Required attribute id is missing for module"));
            }

            modConfig[i] = new IndexModuleConfig(id, className, elem);
        }
        setProperty(IndexManager.PROPERTY_INDEXER_MODULES, modConfig);
    }

    private void configureValidation(final Optional<Path> dbHome, final Element validation) {
        // Determine validation mode
        final String mode = getConfigAttributeValue(validation, XMLReaderObjectFactory.VALIDATION_MODE_ATTRIBUTE);
        if (mode != null) {
            setProperty(PROPERTY_VALIDATION_MODE, mode);
        }

        // cache
        final GrammarPool gp = new GrammarPool();
        setProperty(XMLReaderObjectFactory.GRAMMAR_POOL, gp);

        // Configure the Entity Resolver
        final NodeList entityResolver = validation.getElementsByTagName(XMLReaderObjectFactory.CONFIGURATION_ENTITY_RESOLVER_ELEMENT_NAME);
        if (entityResolver.getLength() == 0) {
            return;
        }
        LOG.info("Creating xmlresolver.org OASIS Catalog resolver");

        final Element elemEntityResolver = (Element) entityResolver.item(0);
        final NodeList nlCatalogs = elemEntityResolver.getElementsByTagName(XMLReaderObjectFactory.CONFIGURATION_CATALOG_ELEMENT_NAME);

        // Determine webapps directory. SingleInstanceConfiguration cannot
        // be used at this phase. Trick is to check whether dbHOME is
        // pointing to a WEB-INF directory, meaning inside the war file.
        final Path webappHome = dbHome.map(h -> {
            if (FileUtils.fileName(h).endsWith("WEB-INF")) {
                return h.getParent().toAbsolutePath();
            }
            return h.resolve("webapp").toAbsolutePath();
        }).orElse(Paths.get("webapp").toAbsolutePath());

        if (LOG.isDebugEnabled()) {
            LOG.debug("Found {} catalog uri entries.", nlCatalogs.getLength());
            LOG.debug("Using dbHome={}", dbHome);
            LOG.debug("using webappHome={}", webappHome);
        }

        // Get the Catalog URIs
        final List<String> catalogUris = new ArrayList<>();
        for (int i = 0; i < nlCatalogs.getLength(); i++) {
            final String uriAttributeValue = ((Element) nlCatalogs.item(i)).getAttribute("uri");

            if (!uriAttributeValue.isEmpty()) {
                final String uri;
                // Substitute string, creating an uri from a local file
                if (uriAttributeValue.contains("${WEBAPP_HOME}")) {
                    uri = uriAttributeValue.replaceAll("\\$\\{WEBAPP_HOME}", webappHome.toUri().toString());
                } else if (uriAttributeValue.contains("${EXIST_HOME}")) {
                    uri = uriAttributeValue.replaceAll("\\$\\{EXIST_HOME}", dbHome.toString());
                } else {
                    uri = uriAttributeValue;
                }

                // Add uri to configuration
                LOG.info("Adding Catalog URI: {}", uri);
                catalogUris.add(uri);
            }
        }

        // Store all configured URIs
        setProperty(XMLReaderObjectFactory.CATALOG_URIS, catalogUris);

        // Create and Store the resolver
        try {
            final List<Tuple2<String, Optional<InputSource>>> catalogs = catalogUris.stream()
                    .map(catalogUri -> Tuple(catalogUri, Optional.<InputSource>empty()))
                    .collect(Collectors.toList());
            final Resolver resolver = ResolverFactory.newResolver(catalogs);
            setProperty(XMLReaderObjectFactory.CATALOG_RESOLVER, resolver);
        } catch (final URISyntaxException e) {
            LOG.error("Unable to parse catalog uri: {}", e.getMessage(), e);
        }
    }

    private void configureRpcServer(final Element validation) {
        final NodeList contentFile = validation.getElementsByTagName("content-file");
        if (contentFile.getLength() > 0) {
            final String inMemorySize = ((Element) contentFile.item(0)).getAttribute("in-memory-size");
            if (inMemorySize != null) {
                setProperty(PROPERTY_IN_MEMORY_SIZE, parseInt(inMemorySize, DEFAULT_IN_MEMORY_SIZE));
            }
        }
        final NodeList contentFilePool = validation.getElementsByTagName("content-file-pool");
        if (contentFilePool.getLength() > 0) {
            final String size = ((Element) contentFilePool.item(0)).getAttribute("size");
            setProperty(ContentFilePool.PROPERTY_POOL_SIZE, parseInt(size, -1));
            final String maxIdle = ((Element) contentFilePool.item(0)).getAttribute("max-idle");
            setProperty(ContentFilePool.PROPERTY_POOL_MAX_IDLE, parseInt(maxIdle, 5));
        }
    }

    /**
     * Gets the value of a configuration attribute
     * <p>
     * The value typically is specified in the conf.xml file, but can be overridden with using a System Property
     *
     * @param element       The attribute's parent element
     * @param attributeName The name of the attribute
     * @return The value of the attribute
     */
    private String getConfigAttributeValue(final Element element, final String attributeName) {
        if (element == null || attributeName == null) {
            return null;
        }

        final String property = getAttributeSystemPropertyName(element, attributeName);
        final String value = System.getProperty(property);

        if (value != null) {
            LOG.warn("Configuration value overridden by system property: {}, with value: {}", property, value);
            return value;
        }
        // If the value has not been overridden in a system property, then get it from the configuration
        return element.getAttribute(attributeName);
    }

    /**
     * Generates a suitable system property name from the given config attribute and parent element.
     * <p>
     * values are of the form org.element.element.....attribute and follow the hierarchical structure of the conf.xml file.
     * For example, the db-connection cacheSize property name would be org.exist.db-connection.cacheSize
     *
     * @param element       The attribute's parent element
     * @param attributeName The name of the attribute
     * @return The generated system property name
     */
    private String getAttributeSystemPropertyName(Element element, String attributeName) {
        final StringBuilder property = new StringBuilder(attributeName);
        Node parent = element.getParentNode();

        property.insert(0, ".");
        property.insert(0, element.getLocalName());

        while (parent instanceof Element) {
            final String parentName = parent.getLocalName();

            property.insert(0, ".");
            property.insert(0, parentName);

            parent = parent.getParentNode();
        }

        property.insert(0, "org.");

        return property.toString();
    }

    public Optional<Path> getConfigFilePath() {
        return configFilePath;
    }

    public Optional<Path> getExistHome() {
        return existHome;
    }

    public Object getProperty(final String name) {
        return config.get(name);
    }

    public <T> T getProperty(final String name, final T defaultValue) {
        return Optional.ofNullable((T) config.get(name)).orElse(defaultValue);
    }

    public boolean hasProperty(final String name) {
        return config.containsKey(name);
    }

    public void setProperty(final String name, final Object obj) {
        config.put(name, obj);
        LOG.debug(PRP_DETAILS, name, obj);
    }

    public void removeProperty(final String name) {
        config.remove(name);
    }

    public int getInteger(final String name) {
        return getInteger(name, -1);
    }

    public int getInteger(final String name, final int defaultValue) {
        return Optional.ofNullable(getProperty(name))
                .filter(v -> v instanceof Integer)
                .map(v -> (int) v)
                .orElse(defaultValue);
    }

    /**
     * (non-Javadoc).
     *
     * @param exception DOCUMENT ME!
     * @throws SAXException DOCUMENT ME!
     * @see org.xml.sax.ErrorHandler#error(org.xml.sax.SAXParseException)
     */
    @Override
    public void error(SAXParseException exception) throws SAXException {
        LOG.error("error occurred while reading configuration file [line: {}]:{}",
                exception.getLineNumber(), exception.getMessage(), exception);
    }

    /**
     * (non-Javadoc).
     *
     * @param exception DOCUMENT ME!
     * @throws SAXException DOCUMENT ME!
     * @see org.xml.sax.ErrorHandler#fatalError(org.xml.sax.SAXParseException)
     */
    @Override
    public void fatalError(SAXParseException exception) throws SAXException {
        LOG.error("error occurred while reading configuration file [line: {}]:{}",
                exception.getLineNumber(), exception.getMessage(), exception);
    }

    /**
     * (non-Javadoc).
     *
     * @param exception DOCUMENT ME!
     * @throws SAXException DOCUMENT ME!
     * @see org.xml.sax.ErrorHandler#warning(org.xml.sax.SAXParseException)
     */
    @Override
    public void warning(SAXParseException exception) throws SAXException {
        LOG.error("error occurred while reading configuration file [line: {}]:{}",
                exception.getLineNumber(), exception.getMessage(), exception);
    }

    public record StartupTriggerConfig(String clazz, Map<String, List<?>> params) {
    }

    public record IndexModuleConfig(String id, String className, Element config) {
    }

}
