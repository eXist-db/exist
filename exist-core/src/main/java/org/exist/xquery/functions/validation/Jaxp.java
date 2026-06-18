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
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
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
import org.w3c.dom.ls.LSResourceResolver;
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
    private static final String XSD_VERSIONING_NS = "http://www.w3.org/2007/XMLSchema-versioning";

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
        LSResourceResolver catalogResolver = null;
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
                    // SearchResourceResolver implements both XMLEntityResolver (for this SAX
                    // pipeline) and LSResourceResolver (for the XSD 1.1 validator below), so
                    // directory-search catalogs work the same way regardless of which pipeline
                    // ends up validating.
                    final SearchResourceResolver resolver = new SearchResourceResolver(brokerPool, context.getSubject(), catalogUrls[0]);
                    catalogResolver = resolver;
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

            /* The bundled Xerces XSD 1.1 support is only wired into the JAXP
               SchemaFactory/Validator API, not into this dynamic-discovery
               SAXParser pipeline -- it's a hard limitation of the dependency,
               not a configuration gap (see plans/catalog-dtd.plan.md). Rather
               than always discovering this by parsing with the wrong pipeline
               and failing, peek (best-effort, see detectXsd11ViaSchemaLocation)
               at the schema the instance's own hint points to and pick the
               right pipeline up front when that succeeds. If the peek can't
               tell (catalog-mediated location, unresolvable hint, etc.), fall
               through to the unchanged default pipeline below, which still
               retries with the XSD 1.1 validator on the cvc-elt.1.a failure
               signature -- the peek is purely an optimization, never a
               correctness requirement. */
            final boolean useXsd11UpFront;
            final InputSource peekInstance = Shared.getInputSource(args[0].itemAt(0), context);
            try {
                useXsd11UpFront = detectXsd11ViaSchemaLocation(peekInstance);
            } finally {
                Shared.closeInputSource(peekInstance);
            }

            if (useXsd11UpFront) {
                LOG.debug("Detected XSD 1.1 schema (vc:minVersion) via the instance's schemaLocation hint " +
                        "before parsing; using the XSD 1.1 validator directly.");

                final Validator validator = newXsd11Validator(catalogResolver);
                validator.setErrorHandler(report);
                validator.validate(new SAXSource(instance), new SAXResult(contenthandler));

            } else {
                // Jaxp document
                LOG.debug("Start parsing document");
                xmlReader.parse(instance);
                LOG.debug("Stopped parsing document");

                /* Safety net: the up-front peek above didn't (or couldn't) detect XSD 1.1.
                   When that shows up as "no declaration found" for the root element, retry once
                   with the XSD-1.1-capable Validator before giving up -- but only when retrying
                   could plausibly help: either the instance carries an explicit schemaLocation
                   hint (the no-catalog case, where Xerces' own default resolution would have used
                   it), or a catalog/resolver IS configured (system catalog, .xml catalog, or
                   directory-search), since any of those can resolve a schema purely by the root
                   element's namespace -- the directory-search fixtures in this codebase, for
                   example, carry no schemaLocation hint at all and rely entirely on namespace-based
                   lookup. DTD-only documents with neither a hint nor a catalog never produce this
                   cvc-* signature in the first place, so they never retry. */
                if (!report.isValid() && isMissingElementDeclaration(report)
                        && (catalogResolver != null || hasSchemaLocationHint(contenthandler, instanceBuilder))) {
                    LOG.debug("Retrying validation with XSD 1.1 validator after cvc-elt.1.a");
                    report.clear();

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
     * of the instance is needed. One of two ways the XSD 1.1 fallback retry's
     * guard decides retrying could plausibly help when there's no catalog/resolver
     * configured at all (the other being a configured catalog/resolver, which can
     * resolve a schema purely by namespace with no hint present -- see the retry
     * guard in {@code eval()}). A DTD-only document with neither produces the
     * cvc-elt.1.a signature in the first place, so it never reaches this check.
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
     * Best-effort, pre-parse check for whether the instance's referenced schema is XSD 1.1
     * (declares {@code vc:minVersion} containing "1.1"), so the right validation pipeline can
     * be chosen up front instead of discovering the mismatch only after a failed first attempt
     * (see {@link #isMissingElementDeclaration(ValidationReport)}). Resolves the schemaLocation
     * hint(s) only via simple relative-URI resolution against the instance's own base URI --
     * it does NOT replicate the full catalog/entity-resolver chain used for the real parse.
     * Returns {@code false} (never throws) whenever any step can't be completed; the
     * retry-after-failure check in {@code eval()} remains the safety net for those cases
     * (catalog-mediated locations, an unresolvable hint, etc.).
     *
     * @param peekInstance a fresh, not-yet-consumed InputSource for the same instance document.
     */
    private static boolean detectXsd11ViaSchemaLocation(final InputSource peekInstance) {
        final Map<String, String> rootAttrs = peekRootAttributes(peekInstance);
        final String baseUri = peekInstance.getSystemId();
        if (rootAttrs == null || baseUri == null) {
            return false;
        }

        final List<String> candidateLocations = new ArrayList<>();
        final String noNsLocation = rootAttrs.get(clark(XSI_NS, "noNamespaceSchemaLocation"));
        if (noNsLocation != null) {
            candidateLocations.add(noNsLocation);
        }
        final String schemaLocation = rootAttrs.get(clark(XSI_NS, "schemaLocation"));
        if (schemaLocation != null) {
            // xsi:schemaLocation is a list of "namespace location" pairs; we only need the locations.
            final String[] tokens = schemaLocation.trim().split("\\s+");
            for (int i = 1; i < tokens.length; i += 2) {
                candidateLocations.add(tokens[i]);
            }
        }

        for (final String location : candidateLocations) {
            if (isXsd11Schema(baseUri, location)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves {@code location} relative to {@code baseUri} (the same xmldb:// normalization
     * the catalog mechanism uses, so this works for documents stored in the database), opens it,
     * and checks whether its root element declares {@code vc:minVersion} containing "1.1".
     * Returns {@code false} for any resolution/read failure -- this is a best-effort peek, not
     * a substitute for the real catalog-aware resolution the actual validation pass performs.
     *
     * <p>{@code location} is the literal, attacker/document-author-controlled value of the
     * instance's own {@code xsi:schemaLocation}/{@code noNamespaceSchemaLocation} hint -- if it's
     * an absolute URI (e.g. {@code file:///etc/passwd}, {@code http://internal-host/...}, or even
     * an absolute {@code xmldb://some-other-host:1234/db/...} naming a remote eXist instance),
     * {@link URI#resolve(String)} returns it verbatim, ignoring {@code baseUri} entirely. Opening
     * that unconditionally would let any caller make this (unprivileged, security-context-free)
     * peek fetch arbitrary local files or issue arbitrary outbound requests (file read, or a
     * network connection -- {@code org.exist.protocolhandler.protocols.xmldb.Handler} dispatches
     * to XML-RPC against whatever host an absolute {@code xmldb://} URI names) using the server
     * process's own OS-level/network access, regardless of the calling Subject's DB permissions --
     * this is NOT the same trust boundary as the real validation pass, which only fetches whatever
     * a configured catalog/resolver permits. So only resolutions that land in the exact same
     * scheme+authority (host/port) as {@code baseUri} are attempted -- i.e. genuinely relative
     * locations within the instance's own origin, never a different scheme or host. Anything else
     * falls through to the unchanged, already-accepted-risk default pipeline below, exactly as if
     * this peek didn't exist.</p>
     */
    private static boolean isXsd11Schema(final String baseUri, final String location) {
        try {
            final URI baseUriNormalized = new URI(ResolverFactory.fixupExistCatalogUri(baseUri));
            final URI resolvedUri = baseUriNormalized.resolve(location);
            if (!Objects.equals(baseUriNormalized.getScheme(), resolvedUri.getScheme())
                    || !Objects.equals(baseUriNormalized.getAuthority(), resolvedUri.getAuthority())) {
                LOG.debug("Refusing to peek candidate schema '{}': resolved to a different origin ('{}') than " +
                        "the instance's own base URI ('{}') -- leaving this to the default pipeline/catalog instead.",
                        location, resolvedUri, baseUriNormalized);
                return false;
            }
            try (final InputStream is = resolvedUri.toURL().openStream()) {
                final InputSource schemaSource = new InputSource(is);
                schemaSource.setSystemId(resolvedUri.toString());
                final Map<String, String> schemaRootAttrs = peekRootAttributes(schemaSource);
                if (schemaRootAttrs == null) {
                    return false;
                }
                final String minVersion = schemaRootAttrs.get(clark(XSD_VERSIONING_NS, "minVersion"));
                return minVersion != null && minVersion.contains("1.1");
            }
        } catch (final URISyntaxException | IOException ex) {
            LOG.debug("Could not peek candidate schema '{}' relative to '{}': {}", location, baseUri, ex.getMessage());
            return false;
        }
    }

    /**
     * Reads only as far as the root element's start tag and returns its attributes, keyed by
     * Clark-notation {@code {namespace}localName} (see {@link #clark(String, String)}) -- cheaper
     * than a full (validating) parse since StAX stops pulling events the moment the caller stops
     * asking for them, and avoids the exception-as-control-flow pattern a SAX-based equivalent
     * would need to abort early. DTD processing and external entities are disabled; this reads
     * untrusted instance documents as well as schema documents.
     *
     * @return the root element's attributes, or {@code null} if the source couldn't be read/parsed.
     */
    @Nullable
    private static Map<String, String> peekRootAttributes(final InputSource source) {
        try {
            final XMLInputFactory factory = XMLInputFactory.newInstance();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);

            final XMLStreamReader reader = factory.createXMLStreamReader(source.getByteStream());
            try {
                while (reader.hasNext()) {
                    if (reader.next() == XMLStreamConstants.START_ELEMENT) {
                        final Map<String, String> attrs = new HashMap<>();
                        for (int i = 0; i < reader.getAttributeCount(); i++) {
                            attrs.put(clark(reader.getAttributeNamespace(i), reader.getAttributeLocalName(i)), reader.getAttributeValue(i));
                        }
                        return attrs;
                    }
                }
                return null;
            } finally {
                reader.close();
            }
        } catch (final XMLStreamException ex) {
            LOG.debug("Could not peek root element attributes: {}", ex.getMessage());
            return null;
        }
    }

    private static String clark(@Nullable final String namespaceUri, final String localName) {
        return (namespaceUri == null ? "" : "{" + namespaceUri + "}") + localName;
    }

    /**
     * @param resolver catalog resolver to use for schema/entity resolution, or null.
     * @return a {@link Validator} for the only XSD 1.1-capable pipeline this
     * Xerces fork supports: {@link SchemaFactory}/{@link Schema} with no
     * pre-supplied schema documents, so it dynamically discovers the schema
     * from the instance's own schemaLocation hint, mirroring how the default
     * SAXParser pipeline behaves for XSD 1.0.
     */
    private Validator newXsd11Validator(@Nullable final LSResourceResolver resolver) throws SAXException {
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
