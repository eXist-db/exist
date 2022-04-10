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
package org.exist.xquery.functions.validation;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.apache.xerces.impl.xs.XSDDescription;
import org.apache.xerces.util.SAXInputSource;
import org.apache.xerces.xni.XMLResourceIdentifier;
import org.apache.xerces.xni.XNIException;
import org.apache.xerces.xni.parser.XMLEntityResolver;

import org.apache.xerces.xni.parser.XMLInputSource;
import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.dom.memtree.DocumentBuilderReceiver;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.Serializer;
import org.exist.util.Configuration;
import org.exist.util.ExistSAXParserFactory;
import org.exist.util.XMLReaderObjectFactory;
import org.exist.util.io.TemporaryFileManager;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.validation.GrammarPool;
import org.exist.validation.ValidationContentHandler;
import org.exist.validation.ValidationReport;
import org.exist.validation.resolver.SearchResourceResolver;
import org.exist.validation.resolver.eXistXMLCatalogResolver;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.EntityResolver2;
import org.xmlresolver.Resolver;
import org.xmlresolver.ResolverFeature;
import org.xmlresolver.XMLResolverConfiguration;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;
import static javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING;

/**
 *   xQuery function for validation of XML instance documents
 * using grammars like XSDs and DTDs.
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 */
public class Jaxp extends BasicFunction {

    private static final String simpleFunctionTxt =
            "Validate document by parsing $instance. Optionally "
            + "grammar caching can be enabled. Supported grammars types "
            + "are '.xsd' and '.dtd'.";
    
    private static final String extendedFunctionTxt =
            "Validate document by parsing $instance. Optionally "
            + "grammar caching can be enabled and "
            + "an XML catalog can be specified. Supported grammars types "
            + "are '.xsd' and '.dtd'.";
    
    private static final String documentTxt = "The document referenced as xs:anyURI, a node (element or result of fn:doc()) "
            + "or as a Java file object.";
    
    private static final String catalogTxt = "The catalogs referenced as xs:anyURI's.";
    
    private static final String cacheTxt = "Set the flag to true() to enable grammar caching.";

    private final BrokerPool brokerPool;
    // Setup function signature
    public final static FunctionSignature[] signatures = {
        new FunctionSignature(
        new QName("jaxp", ValidationModule.NAMESPACE_URI, ValidationModule.PREFIX),
        simpleFunctionTxt,
        new SequenceType[]{
            new FunctionParameterSequenceType("instance", Type.ITEM, Cardinality.EXACTLY_ONE,
            documentTxt),
            new FunctionParameterSequenceType("cache-grammars", Type.BOOLEAN, Cardinality.EXACTLY_ONE,
            cacheTxt)
        },
        new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE,
        Shared.simplereportText)),
        
        new FunctionSignature(
        new QName("jaxp", ValidationModule.NAMESPACE_URI, ValidationModule.PREFIX),
        extendedFunctionTxt,
        new SequenceType[]{
            new FunctionParameterSequenceType("instance", Type.ITEM, Cardinality.EXACTLY_ONE,
            documentTxt),
            new FunctionParameterSequenceType("cache-grammars", Type.BOOLEAN, Cardinality.EXACTLY_ONE,
            cacheTxt),
            new FunctionParameterSequenceType("catalogs", Type.ITEM, Cardinality.ZERO_OR_MORE,
            catalogTxt),},
        new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE,
        Shared.simplereportText)),
        
        new FunctionSignature(
        new QName("jaxp-report", ValidationModule.NAMESPACE_URI, ValidationModule.PREFIX),
        simpleFunctionTxt + " An XML report is returned.",
        new SequenceType[]{
            new FunctionParameterSequenceType("instance", Type.ITEM, Cardinality.EXACTLY_ONE,
            documentTxt),
            new FunctionParameterSequenceType("enable-grammar-cache", Type.BOOLEAN, Cardinality.EXACTLY_ONE,
            cacheTxt),},
        new FunctionReturnSequenceType(Type.NODE, Cardinality.EXACTLY_ONE,
        Shared.xmlreportText)),
        
        new FunctionSignature(
        new QName("jaxp-report", ValidationModule.NAMESPACE_URI, ValidationModule.PREFIX),
        extendedFunctionTxt + " An XML report is returned.",
        new SequenceType[]{
            new FunctionParameterSequenceType("instance", Type.ITEM, Cardinality.EXACTLY_ONE,
            documentTxt),
            new FunctionParameterSequenceType("enable-grammar-cache", Type.BOOLEAN, Cardinality.EXACTLY_ONE,
            cacheTxt),
            new FunctionParameterSequenceType("catalogs", Type.ITEM, Cardinality.ZERO_OR_MORE,
            catalogTxt),},
        new FunctionReturnSequenceType(Type.NODE, Cardinality.EXACTLY_ONE,
        Shared.xmlreportText)),
        
        new FunctionSignature(
        new QName("jaxp-parse", ValidationModule.NAMESPACE_URI, ValidationModule.PREFIX),
        "Parse document in validating mode, all defaults are filled in according to the " +
        "grammar (xsd).",
        new SequenceType[]{
            new FunctionParameterSequenceType("instance", Type.ITEM, Cardinality.EXACTLY_ONE,
            documentTxt),
            new FunctionParameterSequenceType("enable-grammar-cache", Type.BOOLEAN, Cardinality.EXACTLY_ONE,
            cacheTxt),
            new FunctionParameterSequenceType("catalogs", Type.ITEM, Cardinality.ZERO_OR_MORE,
            catalogTxt),},
        new FunctionReturnSequenceType(Type.NODE, Cardinality.EXACTLY_ONE,
        "the parsed document."))
    };

    public Jaxp(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
        brokerPool = context.getBroker().getBrokerPool();
    }


    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {

        XMLEntityResolver entityResolver = null;
        GrammarPool grammarPool = null;

        final ValidationReport report = new ValidationReport();
        ContentHandler contenthandler = null;
        MemTreeBuilder instanceBuilder = null;
        InputSource instance = null;

        if (isCalledAs("jaxp-parse")) {
            instanceBuilder = context.getDocumentBuilder();
            contenthandler = new DocumentBuilderReceiver(instanceBuilder, true); // (namespace?)

        } else {
            contenthandler = new ValidationContentHandler();
        }

        try {
            report.start();

            // Get initialized parser
            final XMLReader xmlReader = getXMLReader();

            // Setup validation reporting
            xmlReader.setContentHandler(contenthandler);
            xmlReader.setErrorHandler(report);

            // Get inputstream for instance document
            instance = Shared.getInputSource(args[0].itemAt(0), context);

            // Handle catalog
            if (args.length == 2) {
                LOG.debug("No Catalog specified");

            } else if (args[2].isEmpty()) {
                // Use system catalog
                LOG.debug("Using system catalog.");
                final Configuration config = brokerPool.getConfiguration();
                entityResolver = (eXistXMLCatalogResolver) config.getProperty(XMLReaderObjectFactory.CATALOG_RESOLVER);
                setXmlReaderEnitityResolver(xmlReader, entityResolver);

            } else {
                // Get URL for catalog
                final String[] catalogUrls = Shared.getUrls(args[2]);
                final String singleUrl = catalogUrls[0];

                if (singleUrl.endsWith("/")) {
                    // Search grammar in collection specified by URL. Just one collection is used.
                    LOG.debug("Search for grammar in {}", singleUrl);
                    entityResolver = new SearchResourceResolver(catalogUrls[0], brokerPool);
                    setXmlReaderEnitityResolver(xmlReader, entityResolver);

                } else if (singleUrl.endsWith(".xml")) {
                    LOG.debug("Using catalogs {}", getStrings(catalogUrls));

                    final List<Tuple2<String, Optional<InputSource>>> catalogs = new ArrayList<>();
                    for (String catalogUrl : catalogUrls) {

                        /* NOTE(AR): Catalog URL if stored in database must start with
                           URI Scheme xmldb:// so that the XML Resolver can use
                           org.exist.protocolhandler.protocols.xmldb.Handler
                           to resolve any relative URI resources from the database.
                         */
                        final Optional<InputSource> maybeInputSource;
                        if (catalogUrl.startsWith("xmldb:exist://")) {
                            catalogUrl = catalogUrl.replace("xmldb:exist://", "xmldb://");
                            maybeInputSource = Optional.of(new InputSource(new StringReader(serializeDocument(XmldbURI.create(catalogUrl)))));
                        } else if (catalogUrl.startsWith("/db")) {
                            catalogUrl = "xmldb://" + catalogUrl;
                            maybeInputSource = Optional.of(new InputSource(new StringReader(serializeDocument(XmldbURI.create(catalogUrl)))));
                        } else {
                            maybeInputSource = Optional.empty();
                        }

                        if (maybeInputSource.isPresent()) {
                            maybeInputSource.get().setSystemId(catalogUrl);
                        }
                        catalogs.add(Tuple(catalogUrl, maybeInputSource));
                    }
                    final Resolver resolver = getXmlResolver(catalogs);
                    setXmlReaderEnitityResolver(xmlReader, new ResolverWrapper(resolver));

                } else {
                    LOG.error("Catalog URLs should end on / or .xml");
                }

            }

            // Use grammarpool
            final boolean useCache = ((BooleanValue) args[1].itemAt(0)).getValue();
            if (useCache) {
                LOG.debug("Grammar caching enabled.");
                final Configuration config = brokerPool.getConfiguration();
                grammarPool = (GrammarPool) config.getProperty(XMLReaderObjectFactory.GRAMMAR_POOL);
                xmlReader.setProperty(XMLReaderObjectFactory.APACHE_PROPERTIES_INTERNAL_GRAMMARPOOL, grammarPool);
            }

            // Jaxp document
            LOG.debug("Start parsing document");
            xmlReader.parse(instance);
            LOG.debug("Stopped parsing document");

            // Distill namespace from document
            if (contenthandler instanceof ValidationContentHandler) {
                report.setNamespaceUri(
                        ((ValidationContentHandler) contenthandler).getNamespaceUri());
            }


        } catch (final MalformedURLException ex) {
            LOG.error(ex.getMessage());
            report.setException(ex);

        } catch (final IOException ex) {
            LOG.error(ex.getCause());
            report.setException(ex);

        } catch (final Throwable ex) {
            LOG.error(ex);
            report.setException(ex);

        } finally {
            report.stop();

            Shared.closeInputSource(instance);
        }

        // Create response
        if (isCalledAs("jaxp")) {
            final Sequence result = new ValueSequence();
            result.add(new BooleanValue(report.isValid()));
            return result;

        } else /* isCalledAs("jaxp-report or jaxp-parse ") */ {

            if(report.getThrowable()!=null){
                throw new XPathException(report.getThrowable().getMessage(), report.getThrowable());
            }

            if (contenthandler instanceof DocumentBuilderReceiver) {
                //DocumentBuilderReceiver dbr = (DocumentBuilderReceiver) contenthandler;
                return instanceBuilder.getDocument().getNode(0);

            } else {

                context.pushDocumentContext();
                try {
                    final MemTreeBuilder builder = context.getDocumentBuilder();
                    return Shared.writeReport(report, builder);
                } finally {
                    context.popDocumentContext();
                }
            }

        }
    }

    // ####################################

    
    private XMLReader getXMLReader() throws ParserConfigurationException, SAXException {

        // setup sax factory ; be sure just one instance!
        final SAXParserFactory saxFactory = ExistSAXParserFactory.getSAXParserFactory();

        // Enable validation stuff
        saxFactory.setValidating(true);
        saxFactory.setNamespaceAware(true);

        saxFactory.setFeature(XMLReaderObjectFactory.APACHE_FEATURES_VALIDATION_SCHEMA, true);

        // Create xml reader
        final SAXParser saxParser = saxFactory.newSAXParser();
        final XMLReader xmlReader = saxParser.getXMLReader();

        xmlReader.setFeature(FEATURE_SECURE_PROCESSING, true);

        setXmlReaderFeature(xmlReader, Namespaces.SAX_VALIDATION, true);
        setXmlReaderFeature(xmlReader, Namespaces.SAX_VALIDATION_DYNAMIC, false);
        setXmlReaderFeature(xmlReader, XMLReaderObjectFactory.APACHE_FEATURES_VALIDATION_SCHEMA, true);
        setXmlReaderFeature(xmlReader, XMLReaderObjectFactory.APACHE_PROPERTIES_LOAD_EXT_DTD, true);
        setXmlReaderFeature(xmlReader, Namespaces.SAX_NAMESPACES_PREFIXES, true);

        return xmlReader;
    }

    private void setXmlReaderFeature(XMLReader xmlReader, String featureName, boolean value){

        try {
            xmlReader.setFeature(featureName, value);
            
        } catch (final SAXNotRecognizedException | SAXNotSupportedException ex) {
            LOG.error(ex.getMessage());

        }
    }

    private static Resolver getXmlResolver(final List<Tuple2<String, Optional<InputSource>>> catalogs) throws URISyntaxException {
        final XMLResolverConfiguration resolverConfiguration = new XMLResolverConfiguration();
        resolverConfiguration.setFeature(ResolverFeature.RESOLVER_LOGGER_CLASS, "org.xmlresolver.logging.SystemLogger");
        resolverConfiguration.setFeature(ResolverFeature.CATALOG_LOADER_CLASS, "org.xmlresolver.loaders.ValidatingXmlLoader");
        resolverConfiguration.setFeature(ResolverFeature.CLASSPATH_CATALOGS, true);
        resolverConfiguration.setFeature(ResolverFeature.URI_FOR_SYSTEM, true);

        for (final Tuple2<String, Optional<InputSource>> catalog : catalogs) {
            if (catalog._2.isPresent()) {
                resolverConfiguration.addCatalog(new URI(catalog._1), catalog._2.get());
            } else {
                resolverConfiguration.addCatalog(catalog._1);
            }
        }

        return new Resolver(resolverConfiguration);
    }

    // TODO(AR) remove this when PR https://github.com/xmlresolver/xmlresolver/pull/98 is merged
    private String serializeDocument(final XmldbURI documentUri) throws SAXException, IOException {
        try (final LockedDocument lockedDocument = context.getBroker().getXMLResource(documentUri, Lock.LockMode.READ_LOCK)) {
            if (lockedDocument == null) {
                throw new IOException("No such document: " + documentUri);
            }

            final DocumentImpl doc = lockedDocument.getDocument();

            try (final StringWriter stringWriter = new StringWriter()) {
                final Properties outputProperties = new Properties();
                outputProperties.setProperty(OutputKeys.METHOD, "XML");
                outputProperties.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
                outputProperties.setProperty(OutputKeys.INDENT, "no");
                outputProperties.setProperty(OutputKeys.ENCODING, "UTF-8");

                final Serializer serializer = context.getBroker().getSerializer();
                serializer.reset();
                SAXSerializer sax = null;
                try {
                    sax = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);
                    sax.setOutput(stringWriter, outputProperties);
                    serializer.setProperties(outputProperties);
                    serializer.setSAXHandlers(sax, sax);
                    serializer.toSAX(doc);
                } catch (final SAXNotSupportedException | SAXNotRecognizedException e) {
                    throw new SAXException(e.getMessage(), e);
                } finally {
                    if (sax != null) {
                        SerializerPool.getInstance().returnObject(sax);
                    }
                }

                return stringWriter.toString();
            }
        } catch (final PermissionDeniedException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    private void setXmlReaderEnitityResolver(final XMLReader xmlReader, final XMLEntityResolver entityResolver) {
        try {
            xmlReader.setProperty(XMLReaderObjectFactory.APACHE_PROPERTIES_INTERNAL_ENTITYRESOLVER, entityResolver);

        } catch (final SAXNotRecognizedException | SAXNotSupportedException ex) {
            LOG.error(ex.getMessage());

        }


//        try {
//            xmlReader.setProperty(XMLReaderObjectFactory.APACHE_PROPERTIES_INTERNAL_ENTITYRESOLVER, entityResolver);
//
//        } catch (final SAXNotRecognizedException | SAXNotSupportedException ex) {
//            LOG.error(ex.getMessage());
//
//        }
    }

    // No-go ...processor is in validating mode
    private Path preparseDTD(StreamSource instance, String systemId)
            throws IOException, TransformerConfigurationException, TransformerException {

        // prepare output tmp storage
        final TemporaryFileManager temporaryFileManager = TemporaryFileManager.getInstance();
        final Path tmp = temporaryFileManager.getTemporaryFile();

        final StreamResult result = new StreamResult(tmp.toFile());

        final TransformerFactory tf = TransformerFactory.newInstance();

        final Transformer transformer = tf.newTransformer();

        transformer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, systemId);
        transformer.transform(instance, result);

        return tmp;
    }

    private static String getStrings(String[] data) {
        final StringBuilder sb = new StringBuilder();
        for (final String field : data) {
            sb.append(field);
            sb.append(" ");
        }
        return sb.toString();
    }

    /*
     *           // Prepare grammar ; does not work
    /*
    if (args[1].hasOne()) {
    // Get URL for grammar
    grammarUrl = Shared.getUrl(args[1].itemAt(0));

    // Special case for DTD, the document needs to be rewritten.
    if (grammarUrl.endsWith(".dtd")) {
    StreamSource newInstance = Shared.getStreamSource(instance);
    tmpFile = preparseDTD(newInstance, grammarUrl);
    instance = new InputSource(new FileInputStream(tmpFile));

    } else if (grammarUrl.endsWith(".xsd")) {
    xmlReader.setProperty(XMLReaderObjectFactory.APACHE_PROPERTIES_NONAMESPACESCHEMALOCATION, grammarUrl);

    } else {
    throw new XPathException("Grammar type not supported.");
    }
    }
     */

    public static class ResolverWrapper implements XMLEntityResolver, EntityResolver2 {
        private final Resolver resolver;

        public ResolverWrapper(final Resolver resolver) {
            this.resolver = resolver;
        }

        @Override
        public XMLInputSource resolveEntity(final XMLResourceIdentifier xmlResourceIdentifier) throws XNIException, IOException {
            try {
                final org.apache.xerces.xni.QName triggeringComponent = ((XSDDescription)xmlResourceIdentifier).getTriggeringComponent();

                // TODO (AR) I have no idea if this is correct?!?
                final String name;
                if (triggeringComponent != null) {
                    name = triggeringComponent.localpart;
                } else {
                    name = null;
                }

                // TODO (AR) I have no idea if this is correct?!?
                final String systemId;
                if (xmlResourceIdentifier.getExpandedSystemId() !=  null) {
                    systemId = xmlResourceIdentifier.getExpandedSystemId();
                } else {
                    systemId = xmlResourceIdentifier.getNamespace();
                }

                final InputSource src = resolver.resolveEntity(name, xmlResourceIdentifier.getPublicId(), xmlResourceIdentifier.getBaseSystemId(), systemId);

                if (src == null) {
                    return null;
                }

                return new SAXInputSource(src);

            } catch (final SAXException e) {
                throw new XNIException(e);
            }
        }

        @Override
        public InputSource getExternalSubset(final String name, final String baseURI) throws SAXException, IOException {
            return resolver.getExternalSubset(name, baseURI);
        }

        @Override
        public InputSource resolveEntity(final String name, final String publicId, final String baseURI, final String systemId) throws SAXException, IOException {
            return resolver.resolveEntity(name, publicId, baseURI, systemId);
        }

        @Override
        public InputSource resolveEntity(final String publicId, final String systemId) throws SAXException, IOException {
            return resolver.resolveEntity(publicId, systemId);
        }
    }
}
