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
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.Locale;

import javax.annotation.Nullable;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.apache.xerces.xni.parser.XMLEntityResolver;
import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.dom.memtree.DocumentBuilderReceiver;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.resolver.ResolverFactory;
import org.exist.resolver.XercesXmlResolverAdapter;
import org.exist.storage.BrokerPool;
import org.exist.util.Configuration;
import org.exist.util.ExistSAXParserFactory;
import org.exist.util.XMLReaderObjectFactory;
import org.exist.util.io.TemporaryFileManager;
import org.exist.validation.GrammarPool;
import org.exist.validation.ValidationContentHandler;
import org.exist.validation.ValidationReport;
import org.exist.validation.resolver.SearchResourceResolver;
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

import org.w3c.dom.Element;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Parser;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;
import org.xmlresolver.Resolver;

import static javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING;

/**
 *   xQuery function for validation of XML instance documents
 * using grammars like XSDs and DTDs.
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 */
public class Jaxp extends BasicFunction {

    private static final String XSD_1_1_NS = "http://www.w3.org/XML/XMLSchema/v1.1";
    private static final String XSI_NS = "http://www.w3.org/2001/XMLSchema-instance";

    private static final String simpleFunctionTxt = """
            Validate document by parsing $instance. Optionally \
            grammar caching can be enabled. Supported grammars types \
            are '.xsd' and '.dtd'.""";

    private static final String extendedFunctionTxt = """
            Validate document by parsing $instance. Optionally \
            grammar caching can be enabled and \
            an XML catalog can be specified. Supported grammars types \
            are '.xsd' and '.dtd'.""";

    private static final String documentTxt = """
            The document referenced as xs:anyURI, a node (element or result of fn:doc()) \
            or as a Java file object.""";

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

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final ValidationReport report = new ValidationReport();

        MemTreeBuilder instanceBuilder;
        ContentHandler contenthandler;
        if (isCalledAs("jaxp-parse")) {
            instanceBuilder = context.getDocumentBuilder();
            contenthandler = new DocumentBuilderReceiver(this, instanceBuilder, true); // (namespace?)
        } else {
            instanceBuilder = null;
            contenthandler = new ValidationContentHandler();
        }

        InputSource instance = null;
        Resolver catalogResolver = null;
        boolean usedDirectorySearchCatalog = false;
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
                final Resolver resolver = (Resolver) config.getProperty(XMLReaderObjectFactory.CATALOG_RESOLVER);
                catalogResolver = resolver;
                XercesXmlResolverAdapter.setXmlReaderEntityResolver(xmlReader, resolver);

            } else {
                // Get URL for catalog
                final String[] catalogUrls = Shared.getUrls(args[2]);
                final String singleUrl = catalogUrls[0];

                if (singleUrl.endsWith("/")) {
                    // Search grammar in collection specified by URL. Just one collection is used.
                    LOG.debug("Search for grammar in {}", singleUrl);
                    usedDirectorySearchCatalog = true;
                    final XMLEntityResolver resolver = new SearchResourceResolver(brokerPool, context.getSubject(), catalogUrls[0]);
                    XercesXmlResolverAdapter.setXmlReaderEntityResolver(xmlReader, resolver);

                } else if (singleUrl.endsWith(".xml")) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Using catalogs {}", String.join(" ", catalogUrls));
                    }

                    final Resolver resolver = ResolverFactory.resolveCatalogs(context.getBroker(), catalogUrls);
                    catalogResolver = resolver;
                    XercesXmlResolverAdapter.setXmlReaderEntityResolver(xmlReader, resolver);

                } else {
                    LOG.error("Catalog URLs should end on / or .xml");
                }

            }

            // Use grammarpool
            final boolean useCache = ((BooleanValue) args[1].itemAt(0)).getValue();
            if (useCache) {
                LOG.debug("Grammar caching enabled.");
                final Configuration config = brokerPool.getConfiguration();
                final GrammarPool grammarPool = (GrammarPool) config.getProperty(GrammarPool.GRAMMAR_POOL_ELEMENT);
                xmlReader.setProperty(XMLReaderObjectFactory.APACHE_PROPERTIES_INTERNAL_GRAMMARPOOL, grammarPool);
            }

            // Jaxp document
            LOG.debug("Start parsing document");
            xmlReader.parse(instance);
            LOG.debug("Stopped parsing document");

            /* The bundled Xerces XSD 1.1 support is only wired into the JAXP
               SchemaFactory/Validator API, not into this dynamic-discovery
               SAXParser pipeline. When that shows up as "no declaration found"
               for the root element and the instance actually references a
               schema, retry once with the XSD-1.1-capable Validator before
               giving up. DTD-only documents never produce this cvc-* signature,
               so they never retry. */
            if (!report.isValid() && isMissingElementDeclaration(report) && hasSchemaLocationHint(contenthandler, instanceBuilder)) {
                LOG.debug("Retrying validation with XSD 1.1 validator after cvc-elt.1.a");
                report.clear();

                if (usedDirectorySearchCatalog) {
                    LOG.warn("Directory-search catalogs have no equivalent resource resolver for the XSD 1.1 " +
                            "retry validator -- schema/entity resolution will proceed without a catalog.");
                }

                // The first pass already fed `contenthandler` (and, for jaxp-parse,
                // `instanceBuilder`) a complete document; reusing either for the retry
                // would silently double-build the result. Start over with a fresh
                // content handler/builder so the retry produces a single, clean document.
                if (isCalledAs("jaxp-parse")) {
                    context.pushDocumentContext();
                    try {
                        instanceBuilder = context.getDocumentBuilder();
                    } finally {
                        context.popDocumentContext();
                    }
                    contenthandler = new DocumentBuilderReceiver(this, instanceBuilder, true);
                } else {
                    contenthandler = new ValidationContentHandler();
                }

                final Validator validator = newXsd11Validator(catalogResolver);
                validator.setErrorHandler(report);

                final InputSource retryInstance = Shared.getInputSource(args[0].itemAt(0), context);
                try {
                    validator.validate(new SAXSource(retryInstance), new SAXResult(contenthandler));
                } finally {
                    Shared.closeInputSource(retryInstance);
                }
            }

            // Distill namespace from document
            if (contenthandler instanceof ValidationContentHandler handler) {
                report.setNamespaceUri(
                        handler.getNamespaceUri());
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
            result.add(new BooleanValue(this, report.isValid()));
            return result;

        } else /* isCalledAs("jaxp-report or jaxp-parse ") */ {

            if(report.getThrowable()!=null){
                throw new XPathException(this, report.getThrowable().getMessage(), report.getThrowable());
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

    @SuppressWarnings("deprecation") // org.xml.sax.Parser is the only way to force this Xerces fork's message locale
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

        // Force English error messages, regardless of server locale: isMissingElementDeclaration()
        // below matches on this Xerces fork's formatted "cvc-elt.1.a:" message text, which is
        // otherwise sensitive to the JVM's default locale.
        if (xmlReader instanceof Parser legacyParser) {
            try {
                legacyParser.setLocale(Locale.ENGLISH);
            } catch (final SAXException ex) {
                LOG.debug("Could not force the XMLReader's locale to English: {}", ex.getMessage());
            }
        }

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

    /**
     * @return true if any reported error is the "no global declaration for
     * the root element" signature ({@code cvc-elt.1.a}) produced when this
     * Xerces fork's dynamic-discovery pipeline meets an XSD 1.1-only schema.
     */
    private static boolean isMissingElementDeclaration(final ValidationReport report) {
        return report.getValidationReportItemList().stream()
                .anyMatch(item -> item.getMessage() != null && item.getMessage().startsWith("cvc-elt.1.a:"));
    }

    /**
     * Cheaply checks whether the instance document references a schema via
     * {@code xsi:schemaLocation}/{@code xsi:noNamespaceSchemaLocation}, using
     * only data already captured by the first (failed) parse -- no re-parsing
     * of the instance is needed. Used to decide whether the XSD 1.1 fallback
     * retry could possibly help (a DTD-only document never produces the
     * cvc-elt.1.a signature in the first place, but this guards against
     * retrying on documents that reference no schema at all).
     *
     * @param contenthandler the content handler used for the first parse pass.
     * @param instanceBuilder for {@code jaxp-parse}, the document builder used for the first
     *                        parse pass; {@code null} for {@code jaxp}/{@code jaxp-report}.
     */
    private static boolean hasSchemaLocationHint(final ContentHandler contenthandler, @Nullable final MemTreeBuilder instanceBuilder) {
        if (contenthandler instanceof ValidationContentHandler handler) {
            final Attributes attrs = handler.getRootAttributes();
            return attrs != null
                    && (attrs.getValue(XSI_NS, "schemaLocation") != null || attrs.getValue(XSI_NS, "noNamespaceSchemaLocation") != null);
        }
        if (instanceBuilder != null) {
            final Element root = instanceBuilder.getDocument().getDocumentElement();
            return root != null
                    && (root.hasAttributeNS(XSI_NS, "schemaLocation") || root.hasAttributeNS(XSI_NS, "noNamespaceSchemaLocation"));
        }
        return false;
    }

    /**
     * @param resolver catalog resolver to use for schema/entity resolution, or null.
     * @return a {@link Validator} for the only XSD 1.1-capable pipeline this
     * Xerces fork supports: {@link SchemaFactory}/{@link Schema} with no
     * pre-supplied schema documents, so it dynamically discovers the schema
     * from the instance's own schemaLocation hint, mirroring how the default
     * SAXParser pipeline behaves for XSD 1.0.
     */
    private Validator newXsd11Validator(@Nullable final Resolver resolver) throws SAXException {
        final SchemaFactory schemaFactory = SchemaFactory.newInstance(XSD_1_1_NS);
        final Schema schema = schemaFactory.newSchema();
        final Validator validator = schema.newValidator();
        if (resolver != null) {
            validator.setResourceResolver(resolver);
        }
        return validator;
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
}
