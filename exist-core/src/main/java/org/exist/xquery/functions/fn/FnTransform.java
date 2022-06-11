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

package org.exist.xquery.functions.fn;

import com.evolvedbinary.j8fu.tuple.Tuple2;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.lacuna.bifurcan.IEntry;
import net.jpountz.xxhash.XXHash64;
import net.jpountz.xxhash.XXHashFactory;
import net.sf.saxon.Configuration;
import net.sf.saxon.s9api.*;
import net.sf.saxon.trans.UncheckedXPathException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.dom.memtree.DocumentBuilderReceiver;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.dom.memtree.NamespaceNode;
import org.exist.security.PermissionDeniedException;
import org.exist.util.Holder;
import org.exist.util.serializer.XQuerySerializer;
import org.exist.xquery.*;
import org.exist.xquery.functions.array.ArrayType;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.util.DocUtils;
import org.exist.xquery.util.SerializerUtils;
import org.exist.xquery.value.*;
import org.exist.xslt.EXistURIResolver;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.annotation.Nullable;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.exist.Namespaces.XSL_NS;
import static org.exist.xquery.FunctionDSL.param;
import static org.exist.xquery.FunctionDSL.returnsOptMany;
import static org.exist.xquery.functions.fn.FnModule.functionSignature;
import static org.exist.xquery.functions.fn.FnTransform.Option.*;
import static org.exist.xquery.functions.fn.FunSerialize.normalize;

/**
 * Implementation of fn:transform.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class FnTransform extends BasicFunction {

    private static final Logger LOGGER =  LogManager.getLogger(FnTransform.class);
    private static final ErrorListenerLog4jAdapter ERROR_LISTENER = new ErrorListenerLog4jAdapter(FnTransform.LOGGER);

    private static final javax.xml.namespace.QName QN_XSL_STYLESHEET = new javax.xml.namespace.QName(XSL_NS, "stylesheet");
    private static final javax.xml.namespace.QName QN_VERSION = new javax.xml.namespace.QName("version");

    private static final String FS_TRANSFORM_NAME = "transform";
    static final FunctionSignature FS_TRANSFORM = functionSignature(
            FnTransform.FS_TRANSFORM_NAME,
            "Invokes a transformation using a dynamically-loaded XSLT stylesheet.",
            returnsOptMany(Type.MAP, "The result of the transformation is returned as a map. " +
                    "There is one entry in the map for the principal result document, and one for each " +
                    "secondary result document. The key is a URI in the form of an xs:string value. " +
                    "The key for the principal result document is the base output URI if specified, or " +
                    "the string \"output\" otherwise. The key for secondary result documents is the URI of the " +
                    "document, as an absolute URI. The associated value in each entry depends on the requested " +
                    "delivery format. If the delivery format is document, the value is a document node. If the " +
                    "delivery format is serialized, the value is a string containing the serialized result."),
            param("options", Type.MAP, "The inputs to the transformation are supplied in the form of a map")
    );

    //TODO(AR) if you want Saxon-EE features we need to set those in the Configuration
    private static final Configuration SAXON_CONFIGURATION = new Configuration();
    private static final Processor SAXON_PROCESSOR = new Processor(FnTransform.SAXON_CONFIGURATION);

    private static final long XXHASH64_SEED = 0x2245a28e;
    private static final XXHash64 XX_HASH_64 = XXHashFactory.fastestInstance().hash64();

    private static final Cache<String, XsltExecutable> XSLT_EXECUTABLE_CACHE = Caffeine.newBuilder()
            .maximumSize(25)
            .weakValues()
            .build();

    public FnTransform(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final Options options = new Options((MapType) args[0].itemAt(0));

        final String executableHash = Tuple(options.resolvedStylesheetBaseURI, options
                .stylesheetParams).toString();

        //TODO(AR) Saxon recommends to use a <code>StreamSource</code> or <code>SAXSource</code> instead of DOMSource for performance
        final Optional<Source> sourceNode = FnTransform.getSourceNode(options.sourceNode, context.getBaseURI());

        final boolean shouldCache = options.shouldCache.map(BooleanValue::getValue).orElse(true);

        if (options.xsltVersion == 1.0f || options.xsltVersion == 2.0f || options.xsltVersion == 3.0f) {
            try {
                final Holder<SaxonApiException> compileException = new Holder<>();
                final XsltExecutable xsltExecutable = FnTransform.XSLT_EXECUTABLE_CACHE.get(executableHash, key -> {
                    final XsltCompiler xsltCompiler = FnTransform.SAXON_PROCESSOR.newXsltCompiler();
                    xsltCompiler.setErrorListener(FnTransform.ERROR_LISTENER);
                    for (final IEntry entry : options.stylesheetParams) {
                        final QName qKey = ((QNameValue) entry.key()).getQName();
                        final XdmValue value = XdmValue.makeValue("2");
                        xsltCompiler.setParameter(new net.sf.saxon.s9api.QName(qKey.getPrefix(), qKey.getLocalPart()), value);
                    }

                    try {
                        if (!options.resolvedStylesheetBaseURI.isEmpty()) {
                            options.xsltSource._2.setSystemId(options.resolvedStylesheetBaseURI.getStringValue());
                        } else {
                            options.xsltSource._2.setSystemId(context.getBaseURI().getStringValue());
                        }
                        return xsltCompiler.compile(options.xsltSource._2); // .compilePackage //TODO(AR) need to implement support for xslt-packages
                    } catch (final SaxonApiException e) {
                        compileException.value = e;
                        return null;
                    } catch (final XPathException e) {
                        throw new RuntimeException("Invalid base URI in context", e);
                    }
                });

                if (compileException.value != null) {
                    // if we could not compile the xslt, rethrow the error
                    throw compileException.value;
                }

                final Xslt30Transformer xslt30Transformer = xsltExecutable.load30();

                // TODO(AR) this is just for DOM results... need to handle other response types!
                final MemTreeBuilder builder = context.getDocumentBuilder();
                final DocumentBuilderReceiver builderReceiver = new DocumentBuilderReceiver(builder);

                // Record the secondary result documents generated
                final Map<URI, MemTreeBuilder> resultDocuments = new HashMap<>();
                xslt30Transformer.setResultDocumentHandler(resultDocumentURI -> {
                    final MemTreeBuilder resultBuilder = context.getDocumentBuilder();
                    final DocumentBuilderReceiver resultBuilderReceiver = new DocumentBuilderReceiver(builder);
                    resultDocuments.put(resultDocumentURI, resultBuilder);
                    return new SAXDestination(resultBuilderReceiver);
                });

                final SAXDestination saxDestination = new SAXDestination(builderReceiver);
                if (options.initialTemplate.isPresent()) {
                    if (sourceNode.isPresent()) {
                        final DocumentBuilder sourceBuilder = FnTransform.SAXON_PROCESSOR.newDocumentBuilder();
                        final XdmNode xdmNode = sourceBuilder.build(sourceNode.get());
                        xslt30Transformer.setGlobalContextItem(xdmNode);
                    } else {
                        xslt30Transformer.setGlobalContextItem(null);
                    }
                    final QName qName = options.initialTemplate.get().getQName();
                    xslt30Transformer.callTemplate(
                            new net.sf.saxon.s9api.QName(qName.getPrefix() == null ? "" : qName.getPrefix(), qName.getNamespaceURI(), qName.getLocalPart()), saxDestination);
                } else {
                    if (!sourceNode.isPresent()) {
                        // TODO (AP) OK if initial match selection is supplied instead, not yet implemented
                        throw new XPathException(this, ErrorCodes.FOXT0002, SOURCE_NODE.name + " not supplied");
                    }
                    xslt30Transformer.applyTemplates(sourceNode.get(), saxDestination);
                }
                return makeResultMap(options, builder.getDocument(), resultDocuments);

            } catch (final SaxonApiException e) {
                if (e.getErrorCode() != null) {
                    final QName errorQn = QName.fromJavaQName(e.getErrorCode().getStructuredQName().toJaxpQName());
                    throw new XPathException(this, new ErrorCodes.ErrorCode(errorQn, e.getMessage()), e.getMessage());
                } else {
                    throw new XPathException(this, ErrorCodes.FOXT0003, e.getMessage());
                }
            } catch (final UncheckedXPathException e) {
                throw new XPathException(this, ErrorCodes.FOXT0003, e.getMessage());
            }

        } else {
            throw new XPathException(this, ErrorCodes.FOXT0001, "xslt-version: " + options.xsltVersion + " is not supported.");
        }
    }

    private MapType makeResultMap(final Options options, final NodeValue outputDocument, final Map<URI, MemTreeBuilder> resultDocuments) throws XPathException {

        final MapType outputMap = new MapType(context);
        final AtomicValue outputKey;
        if (options.baseOutputURI.isPresent()) {
            outputKey = options.baseOutputURI.get();
        } else {
            outputKey = new StringValue("output");
        }

        outputMap.add(outputKey, convertToDeliveryFormat(options, outputDocument));

        for (final Map.Entry<URI, MemTreeBuilder> resultDocument : resultDocuments.entrySet()) {
            final Sequence value = convertToDeliveryFormat(options, resultDocument.getValue().getDocument());
            outputMap.add(new AnyURIValue(resultDocument.getKey()), value);
        }

        return outputMap;
    }

    private Sequence convertToDeliveryFormat(final Options options, final NodeValue document) throws XPathException {

        switch (options.deliveryFormat) {
            case SERIALIZED:
                return serializeOutput(options.serializationParams, document);
            case RAW:
                return rawOutput(document);
            case DOCUMENT:
            default:
                return document;
        }
    }

    /**
     * If the output document is to be serialized, serialize it.
     *
     * @param serializationOptionsMap fromserialization parameters
     * @param outputDocument the generated document to be serialized
     * @return a {@link StringValue} which is the serialized value of the document
     * @throws XPathException if serialization failed
     */
    private Sequence serializeOutput(final Optional<MapType> serializationOptionsMap, final NodeValue outputDocument) throws XPathException {
        final Properties outputProperties = SerializerUtils.getSerializationOptions(this, serializationOptionsMap.orElse(new MapType(context)));
        try(final StringWriter writer = new StringWriter()) {
            final XQuerySerializer xqSerializer = new XQuerySerializer(context.getBroker(), outputProperties, writer);

            Sequence seq = outputDocument;
            if (xqSerializer.normalize()) {
                seq = normalize(this, context, seq);
            }

            xqSerializer.serialize(seq);
            return new StringValue(writer.toString());
        } catch (final IOException | SAXException e) {
            throw new XPathException(this, FnModule.SENR0001, e.getMessage());
        }
    }

    private Sequence rawOutput(final NodeValue outputDocument) throws XPathException {
        if (outputDocument instanceof Document) {
            System.err.println("[outputDocument]: " + outputDocument);
            final Document document = (Document) outputDocument;
            final Element element = document.getDocumentElement();
            if (element instanceof NodeValue) {
                System.err.println("[outputDocument document element]: " + element);
                return (Sequence) element;
            }
        }
        throw new XPathException(this, ErrorCodes.FOXT0002, "Unexpected output not a node value, requested " +
                FnTransform.DELIVERY_FORMAT + " " + DeliveryFormat.RAW.name().toLowerCase());
    }

    private static Optional<Source> getSourceNode(final Optional<NodeValue> sourceNode, final AnyURIValue baseURI) throws XPathException {
        return sourceNode.map(NodeValue::getNode).map(node -> new DOMSource(node, baseURI.getStringValue()));
    }

    /**
     * Resolve the stylesheet location.
     * <p>
     *     It may be a dynamically configured document.
     *     Or a document within the database.
     * </p>
     * @param stylesheetLocation path or URI of stylesheet
     * @return a source wrapping the contents of the stylesheet
     * @throws XPathException if there is a problem resolving the location.
     */
    private Source resolveStylesheetLocation(final String stylesheetLocation) throws XPathException {
        final Sequence document;
        try {
            document = DocUtils.getDocument(context, stylesheetLocation);
        } catch (PermissionDeniedException e) {
            throw new XPathException(this, ErrorCodes.FODC0002,
                    "Can not access '" + stylesheetLocation + "'" + e.getMessage());
        }
        if (document != null && document.hasOne() && Type.subTypeOf(document.getItemType(), Type.NODE)) {
            return new DOMSource((Node) document.itemAt(0));
        }
        final EXistURIResolver eXistURIResolver = new EXistURIResolver(
                context.getBroker().getBrokerPool(), null);
        try {
            return eXistURIResolver.resolve(stylesheetLocation, context.getBaseURI().getStringValue());
        } catch (final TransformerException e) {
            throw new XPathException(this, ErrorCodes.FOXT0002, "Unable to resolve stylesheet location: " + stylesheetLocation + ": " + e.getMessage(), Sequence.EMPTY_SEQUENCE, e);
        }
    }

    /**
     * URI resolution, the core should be the same as for fn:resolve-uri
     * @param relative
     * @param base
     * @return
     * @throws XPathException
     */
    private AnyURIValue resolveURI(final AnyURIValue relative, final AnyURIValue base) throws XPathException {
        final URI relativeURI;
        final URI baseURI;
        try {
            relativeURI = new URI(relative.getStringValue());
            baseURI = new URI(base.getStringValue() );
        } catch (final URISyntaxException e) {
            throw new XPathException(this, ErrorCodes.FORG0009, "unable to resolve a relative URI against a base URI in fn:transform(): " + e.getMessage(), null, e);
        }
        if (relativeURI.isAbsolute()) {
            return relative;
        } else {
            return new AnyURIValue(baseURI.resolve(relativeURI));
        }

    }

    /**
     * Get the stylesheet.
     *
     * @return a Tuple whose first value is the stylesheet base uri, and whose second
     *     value is the source for accessing the stylesheet
     */
    private Tuple2<String, Source> getStylesheet(final MapType options) throws XPathException {
        final Optional<String> stylesheetLocation = FnTransform.STYLESHEET_LOCATION.get(options).map(StringValue::getStringValue);
        if (stylesheetLocation.isPresent()) {
            return Tuple(stylesheetLocation.get(), resolveStylesheetLocation(stylesheetLocation.get()));
        }

        final Optional<Node> stylesheetNode = FnTransform.STYLESHEET_NODE.get(options).map(NodeValue::getNode);
        if (stylesheetNode.isPresent()) {
            final Node node = stylesheetNode.get();
            return Tuple(node.getBaseURI(), new DOMSource(node));
        }

        final Optional<String> stylesheetText = FnTransform.STYLESHEET_TEXT.get(options).map(StringValue::getStringValue);
        if (stylesheetText.isPresent()) {
            final String text = stylesheetText.get();
            final byte[] data = text.getBytes(UTF_8);
            final long checksum = FnTransform.XX_HASH_64.hash(data, 0, data.length, FnTransform.XXHASH64_SEED);
            return Tuple("checksum://" + checksum, new StringSource(stylesheetText.get()));
        }

        throw new XPathException(this, ErrorCodes.FOXT0002, "Neither stylesheet-location, stylesheet-node, or stylesheet-text was set");
    }

    private float getXsltVersion(final Source xsltStylesheet) throws XPathException {

        if (xsltStylesheet instanceof DOMSource) {
            return domExtractXsltVersion(xsltStylesheet);
        } else if (xsltStylesheet instanceof StreamSource) {
            return staxExtractXsltVersion(xsltStylesheet);
        }

        throw new XPathException(this, ErrorCodes.FOXT0002, "Unable to extract version from XSLT, unrecognised source");
    }

    private float domExtractXsltVersion(final Source xsltStylesheet) throws XPathException {

        Node node = ((DOMSource) xsltStylesheet).getNode();
        if (node instanceof Document) {
            node = ((Document) node).getDocumentElement();
        }

        String version = "";

        if (node instanceof Element) {

            final Element elem = (Element) node;
            if (XSL_NS.equals(node.getNamespaceURI())
                    && "stylesheet".equals(node.getLocalName())) {
                version = elem.getAttribute("version");
            }

            // No luck ? Search the attributes of a "simplified stylesheet"
            final NamedNodeMap attributes = elem.getAttributes();
            for (int i = 0; version.isEmpty() && i < attributes.getLength(); i++) {
                if (attributes.item(i) instanceof NamespaceNode) {
                    final NamespaceNode nsNode = (NamespaceNode) attributes.item(i);
                    final String uri = nsNode.getNodeValue();
                    final String localName = nsNode.getLocalName(); // "xsl"
                    if (XSL_NS.equals(uri)) {
                        version = elem.getAttribute(localName + ":version");
                    }
                }
            }
        }

        if (version.isEmpty()) {
            throw new XPathException(this, ErrorCodes.FOXT0002, "Unable to extract version from XSLT via DOM");
        }

        try {
            return Float.parseFloat(version);
        } catch (final NumberFormatException nfe) {
            throw new XPathException(this, ErrorCodes.FOXT0002, "Unable to extract version from XSLT via DOM. Value: " + version + " : " + nfe.getMessage());
        }
    }

    private float staxExtractXsltVersion(final Source xsltStylesheet) throws XPathException {
        try {
            final XMLInputFactory factory = XMLInputFactory.newInstance();
            final XMLEventReader eventReader =
                    factory.createXMLEventReader(xsltStylesheet);

            while (eventReader.hasNext()) {
                final XMLEvent event = eventReader.nextEvent();
                if (event.getEventType() == XMLStreamConstants.START_ELEMENT) {
                    final StartElement startElement = event.asStartElement();
                    if (FnTransform.QN_XSL_STYLESHEET.equals(startElement.getName())) {
                        final Attribute version = startElement.getAttributeByName(FnTransform.QN_VERSION);
                        return Float.parseFloat(version.getValue());
                    }
                }
            }
        } catch (final XMLStreamException e) {
            throw new XPathException(this, ErrorCodes.FOXT0002, "Unable to extract version from XSLT via STaX: " + e.getMessage(), Sequence.EMPTY_SEQUENCE, e);
        }

        throw new XPathException(this, ErrorCodes.FOXT0002, "Unable to extract version from XSLT via STaX");
    }

    private static final Option<AnyURIValue> BASE_OUTPUT_URI = new Option<>(
            Type.STRING, "base-output-uri", v1_0, v2_0, v3_0);
    private static final Option<BooleanValue> CACHE = new Option<>(
            Type.BOOLEAN, "cache", BooleanValue.TRUE, v1_0, v2_0, v3_0);
    private static final Option<StringValue> DELIVERY_FORMAT = new Option<>(
            Type.STRING, "delivery-format", new StringValue("document"), v1_0, v2_0, v3_0);
    private static final Option<BooleanValue> ENABLE_ASSERTIONS = new Option<>(
            Type.BOOLEAN, "enable-assertions", BooleanValue.FALSE, v3_0);
    private static final Option<BooleanValue> ENABLE_MESSAGES = new Option<>(
            Type.BOOLEAN, "enable-messages", BooleanValue.TRUE, v1_0, v2_0, v3_0);
    private static final Option<BooleanValue> ENABLE_TRACE = new Option<>(
            Type.BOOLEAN, "enable-trace", BooleanValue.TRUE, v2_0, v3_0);
    private static final Option<ArrayType> FUNCTION_PARAMS = new Option<>(
            Type.ARRAY,"function-params", v3_0);
    private static final Option<Item> GLOBAL_CONTEXT_ITEM = new Option<>(
            Type.ITEM, "global-context-item", v3_0);
    private static final Option<QNameValue> INITIAL_FUNCTION = new Option<>(
            Type.QNAME,"initial-function", v3_0);
    private static final Option<Item> INITIAL_MATCH_SELECTION = new Option<>(
            Type.ITEM,"initial-match-selection", v3_0);
    private static final Option<QNameValue> INITIAL_MODE = new Option<>(
            Type.QNAME,"initial-mode", v1_0, v2_0, v3_0);
    private static final Option<QNameValue> INITIAL_TEMPLATE = new Option<>(
            Type.QNAME,"initial-template", v2_0, v3_0);
    private static final Option<StringValue> PACKAGE_NAME = new Option<>(
            Type.STRING,"package-name", v3_0);
    private static final Option<StringValue> PACKAGE_LOCATION = new Option<>(
            Type.STRING,"package-location", v3_0);
    private static final Option<NodeValue> PACKAGE_NODE = new Option<>(
            Type.NODE,"package-node", v3_0);
    private static final Option<StringValue> PACKAGE_TEXT = new Option<>(
            Type.STRING,"package-text", v3_0);
    private static final Option<StringValue> PACKAGE_VERSION = new Option<>(
            Type.STRING,"package-version", new StringValue("*"), v3_0);
    private static final Option<FunctionReference> POST_PROCESS = new Option<>(
            Type.FUNCTION_REFERENCE,"post-process", v1_0, v2_0, v3_0);
    private static final Option<MapType> REQUESTED_PROPERTIES = new Option<>(
            Type.MAP,"requested-properties", v1_0, v2_0, v3_0);
    private static final Option<MapType> SERIALIZATION_PARAMS = new Option<>(
            Type.MAP,"serialization-params", v1_0, v2_0, v3_0);
    private static final Option<NodeValue> SOURCE_NODE = new Option<>(
            Type.NODE,"source-node", v1_0, v2_0, v3_0);
    private static final Option<NodeValue> STATIC_PARAMS = new Option<>(
            Type.NODE,"static-params", v3_0);
    private static final Option<StringValue> STYLESHEET_BASE_URI = new Option<>(
            Type.STRING, "stylesheet-base-uri", v1_0, v2_0, v3_0);
    private static final Option<StringValue> STYLESHEET_LOCATION = new Option<>(
            Type.STRING,"stylesheet-location", v1_0, v2_0, v3_0);
    private static final Option<NodeValue> STYLESHEET_NODE = new Option<>(
            Type.NODE,"stylesheet-node", v1_0, v2_0, v3_0);
    private static final Option<MapType> STYLESHEET_PARAMS = new Option<>(
            Type.MAP,"stylesheet-params", v1_0, v2_0, v3_0);
    private static final Option<StringValue> STYLESHEET_TEXT = new Option<>(
            Type.STRING,"stylesheet-text", v1_0, v2_0, v3_0);
    private static final Option<MapType> TEMPLATE_PARAMS = new Option<>(
            Type.MAP,"template-params", v3_0);
    private static final Option<MapType> TUNNEL_PARAMS = new Option<>(
            Type.MAP,"tunnel-params", v3_0);
    private static final Option<MapType> VENDOR_OPTIONS = new Option<>(
            Type.MAP,"vendor-options", v1_0, v2_0, v3_0);
    private static final Option<DecimalValue> XSLT_VERSION = new Option<>(
            Type.DECIMAL,"xslt-version", v1_0, v2_0, v3_0);

    static class Option<T extends Item> {
        public static final float v1_0 = 1.0f;
        public static final float v2_0 = 2.0f;
        public static final float v3_0 = 3.0f;

        private final StringValue name;
        private final Optional<T> defaultValue;
        private final float[] appliesToVersions;
        private final int itemSubtype;

        public Option(final int itemSubtype, final String name, final float... appliesToVersions) {
            this(itemSubtype, name, Optional.empty(), appliesToVersions);
        }

        public Option(final int itemSubtype, final String name, @Nullable final T defaultValue, final float... appliesToVersions) {
            this(itemSubtype, name, Optional.ofNullable(defaultValue), appliesToVersions);
        }

        private Option(final int itemSubtype, final String name, final Optional<T> defaultValue, final float... appliesToVersions) {
            this.name = new StringValue(name);
            this.defaultValue = defaultValue;
            this.appliesToVersions = appliesToVersions;
            this.itemSubtype = itemSubtype;
        }

        public Optional<T> get(final MapType options) throws XPathException {
            if (options.contains(name)) {
                final Item item0 = options.get(name).itemAt(0);
                if (item0 != null) {
                    if (Type.subTypeOf(item0.getType(), itemSubtype)) {
                        return Optional.of((T) item0);
                    } else {
                        throw new XPathException(
                                ErrorCodes.XPTY0004, "Type error: expected " + Type.getTypeName(itemSubtype) + ", got " + Type.getTypeName(item0.getType()));
                    }
                }
            }

            return defaultValue;
        }

        public Optional<Sequence> getSeq(final MapType options) {
            if (options.contains(name)) {
                return Optional.of(options.get(name));
            }

            return defaultValue.map(ValueSequence::new);
        }

        private boolean appliesToVersion(final float xsltVersion) {
            for (final float appliesToVersion : appliesToVersions) {
                if (xsltVersion == appliesToVersion) {
                    return true;
                }
            }
            return false;
        }

        public Optional<T> get(final float xsltVersion, final MapType options) {
            if (!appliesToVersion(xsltVersion)) {
                return Optional.empty();
            }

            if (options.contains(name)) {
                return Optional.of((T)options.get(name).itemAt(0));
            }

            return defaultValue;
        }

        public Optional<Sequence> getSeq(final float xsltVersion, final MapType options) {
            if (!appliesToVersion(xsltVersion)) {
                return Optional.empty();
            }

            if (options.contains(name)) {
                return Optional.of(options.get(name));
            }

            return defaultValue.map(ValueSequence::new);
        }
    }

    private static class ErrorListenerLog4jAdapter implements ErrorListener {
        private final Logger logger;

        public ErrorListenerLog4jAdapter(final Logger logger) {
            this.logger = logger;
        }

        @Override
        public void warning(final TransformerException e) {
            logger.warn(e.getMessage(), e);
        }

        @Override
        public void error(final TransformerException e) {
            logger.error(e.getMessage(), e);
        }

        @Override
        public void fatalError(final TransformerException e) {
            logger.fatal(e.getMessage(), e);
        }
    }

    private static class StringSource extends StreamSource {
        private final String string;

        public StringSource(final String string) {
            this.string = string;
        }

        @Override
        public Reader getReader() {
            return new StringReader(string);
        }
    }

    private enum DeliveryFormat {
        DOCUMENT,
        SERIALIZED,
        RAW;

    }

    /**
     * Read options into class values in a single place.
     *
     * This is a bit clearer where we need an option several times,
     * we know we have read it up front.
     */
    private class Options {

        final Tuple2<String, Source> xsltSource;
        final MapType stylesheetParams;
        final float xsltVersion;
        final AnyURIValue resolvedStylesheetBaseURI;
        final Optional<QNameValue> initialTemplate;
        final Optional<NodeValue> sourceNode;
        final Optional<BooleanValue> shouldCache;
        final DeliveryFormat deliveryFormat;
        final Optional<AnyURIValue> baseOutputURI;
        final Optional<MapType> serializationParams;

        Options(final MapType options) throws XPathException {
            xsltSource = getStylesheet(options);

            stylesheetParams = FnTransform.STYLESHEET_PARAMS.get(options).orElse(new MapType(context));
            for (final IEntry<AtomicValue, Sequence> entry : stylesheetParams) {
                if (!(entry.key() instanceof QNameValue)) {
                    throw new XPathException(FnTransform.this, ErrorCodes.FOXT0002, "Supplied stylesheet-param is not a valid xs:qname: " + entry);
                }
                if (!(entry.value() instanceof Sequence)) {
                    throw new XPathException(FnTransform.this, ErrorCodes.FOXT0002, "Supplied stylesheet-param is not a valid xs:sequence: " + entry);
                }
            }

            final Optional<DecimalValue> explicitXsltVersion = FnTransform.XSLT_VERSION.get(options);
            if (explicitXsltVersion.isPresent()) {
                try {
                    xsltVersion = explicitXsltVersion.get().getFloat();
                } catch (final XPathException e) {
                    throw new XPathException(FnTransform.this, ErrorCodes.FOXT0002, "Supplied xslt-version is not a valid xs:decimal: " + e.getMessage(), explicitXsltVersion.get(), e);
                }
            } else {
                xsltVersion = getXsltVersion(xsltSource._2);
            }

            final String stylesheetBaseUri;
            final Optional<StringValue> explicitStylesheetBaseUri = FnTransform.STYLESHEET_BASE_URI.get(xsltVersion, options);
            if (explicitStylesheetBaseUri.isPresent()) {
                stylesheetBaseUri = explicitStylesheetBaseUri.get().getStringValue();
            } else {
                stylesheetBaseUri = xsltSource._1;
            }
            resolvedStylesheetBaseURI = resolveURI(new AnyURIValue(stylesheetBaseUri), context.getBaseURI());

            initialTemplate = FnTransform.INITIAL_TEMPLATE.get(options);

            sourceNode = FnTransform.SOURCE_NODE.get(options);

            shouldCache = FnTransform.CACHE.get(xsltVersion, options);

            deliveryFormat = getDeliveryFormat(xsltVersion, options);

            baseOutputURI = FnTransform.BASE_OUTPUT_URI.get(xsltVersion, options);

            serializationParams = FnTransform.SERIALIZATION_PARAMS.get(xsltVersion, options);
        }

        private DeliveryFormat getDeliveryFormat(final float xsltVersion, final MapType options) throws XPathException {
            final String string = FnTransform.DELIVERY_FORMAT.get(xsltVersion, options).get().getStringValue().toUpperCase();
            final DeliveryFormat deliveryFormat;
            try {
                deliveryFormat = DeliveryFormat.valueOf(string);
            } catch (final IllegalArgumentException ie) {
                throw new XPathException(FnTransform.this, ErrorCodes.FOXT0002, "Supplied " + FnTransform.DELIVERY_FORMAT.name +
                        " is not a valid " + FnTransform.DELIVERY_FORMAT.name);
            }
            if (deliveryFormat.equals(DeliveryFormat.RAW) && xsltVersion < v3_0) {
                throw new XPathException(FnTransform.this, ErrorCodes.FOXT0002, FnTransform.DELIVERY_FORMAT.name + " " +
                        DeliveryFormat.RAW + " is only valid with XSLT version 3.0 or better. " +
                        "Version requested is " + xsltVersion);
            }
            return deliveryFormat;
        }
    }
}
