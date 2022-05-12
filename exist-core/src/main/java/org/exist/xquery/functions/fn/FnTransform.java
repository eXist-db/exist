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
import net.jpountz.xxhash.XXHash64;
import net.jpountz.xxhash.XXHashFactory;
import net.sf.saxon.Configuration;
import net.sf.saxon.s9api.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.dom.memtree.DocumentBuilderReceiver;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.util.Base64Encoder;
import org.exist.xquery.*;
import org.exist.xquery.functions.array.ArrayType;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

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
import javax.xml.ws.Holder;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Optional;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.exist.Namespaces.XSL_NS;
import static org.exist.xquery.FunctionDSL.*;
import static org.exist.xquery.functions.fn.FnModule.*;
import static org.exist.xquery.functions.fn.FnTransform.Option.*;

/**
 * Implementation of fn:transform.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class FnTransform extends BasicFunction {

    private static final Logger LOGGER =  LogManager.getLogger(FnTransform.class);
    private static final ErrorListenerLog4jAdapter ERROR_LISTENER = new ErrorListenerLog4jAdapter(LOGGER);

    private static final javax.xml.namespace.QName QN_XSL_STYLESHEET = new javax.xml.namespace.QName(XSL_NS, "stylesheet");
    private static final javax.xml.namespace.QName QN_VERSION = new javax.xml.namespace.QName("version");

    private static final String FS_TRANSFORM_NAME = "transform";
    static final FunctionSignature FS_TRANSFORM = functionSignature(
            FS_TRANSFORM_NAME,
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
    private static final Processor SAXON_PROCESSOR = new Processor(SAXON_CONFIGURATION);

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
        final MapType options = (MapType) args[0].itemAt(0);

        final Tuple2<String, Source> xsltSource = getStylesheet(options);

        final float xsltVersion;
        final Optional<DecimalValue> explicitXsltVersion = XSLT_VERSION.get(options);
        if (explicitXsltVersion.isPresent()) {
            try {
                xsltVersion = explicitXsltVersion.get().getFloat();
            } catch (final XPathException e) {
                throw new XPathException(this, ErrorCodes.FOXT0002, "Supplied xslt-version is not a valid xs:decimal: " + e.getMessage(), explicitXsltVersion.get(), e);
            }
        } else {
            xsltVersion = getXsltVersion(xsltSource._2);
        }

        final String stylesheetBaseUri;
        final Optional<StringValue> explicitStylesheetBaseUri = STYLESHEET_BASE_URI.get(xsltVersion, options);
        if (explicitStylesheetBaseUri.isPresent()) {
            stylesheetBaseUri = explicitStylesheetBaseUri.get().getStringValue();
        } else {
            stylesheetBaseUri = xsltSource._1;
        }

        //TODO(AR) Saxon recommends to use a <code>StreamSource</code> or <code>SAXSource</code> instead of DOMSource for performance
        final Source sourceNode = getSourceNode(options);

        final boolean shouldCache = CACHE.get(xsltVersion, options).map(BooleanValue::getValue).orElse(true);

        if (xsltVersion == 1.0f || xsltVersion == 2.0f || xsltVersion == 3.0f) {
            try {
                final Holder<SaxonApiException> compileException = new Holder<>();
                final XsltExecutable xsltExecutable = XSLT_EXECUTABLE_CACHE.get(stylesheetBaseUri, key -> {
                    final XsltCompiler xsltCompiler = SAXON_PROCESSOR.newXsltCompiler();
                    xsltCompiler.setErrorListener(ERROR_LISTENER);

                    try {
                        return xsltCompiler.compile(xsltSource._2); // .compilePackage //TODO(AR) need to implement support for xslt-packages
                    } catch (final SaxonApiException e) {
                        compileException.value = e;
                        return null;
                    }
                });

                if (compileException.value != null) {
                    // if we could not compile the xslt, rethrow the error
                    throw compileException.value;
                }

                final Xslt30Transformer xslt30Transformer = xsltExecutable.load30();

//                xslt30Transformer.

                // XdmValue xdmValue; //= xslt30Transformer.applyTemplates();
//            Serializer serializer = processor.newSerializer();
                //processor.newDocumentBuilder().newBuildingContentHandler()

//                //TODO temp
//                final StringWriter writer = new StringWriter();
//                final Serializer serializer = SAXON_PROCESSOR.newSerializer(writer);
//                xslt30Transformer.applyTemplates(sourceNode, serializer);
//                return null;
//                //TODO end temp

                // TODO(AR) this is just for DOM results... need to handle other response types!
                final MemTreeBuilder builder = context.getDocumentBuilder();
                final DocumentBuilderReceiver builderReceiver = new DocumentBuilderReceiver(builder);

                final SAXDestination saxDestination = new SAXDestination(builderReceiver);
                xslt30Transformer.applyTemplates(sourceNode, saxDestination);
                return builder.getDocument();

            } catch (final SaxonApiException e) {
                if (e.getErrorCode() != null) {
                    final QName errorQn = QName.fromJavaQName(e.getErrorCode().getStructuredQName().toJaxpQName());
                    throw new XPathException(this, new ErrorCodes.ErrorCode(errorQn, e.getMessage()), e.getMessage());
                } else {
                    throw new XPathException(this, ErrorCodes.FOXT0003, e.getMessage());
                }

            }

        } else {
            throw new XPathException(this, ErrorCodes.FOXT0001, "xslt-version: " + xsltVersion + " is not supported.");
        }
    }

    private Source getSourceNode(final MapType options) {
        final Optional<Node> sourceNode = SOURCE_NODE.get(options).map(NodeValue::getNode);
        if (sourceNode.isPresent()) {
            return new DOMSource(sourceNode.get());
        }
        return null;
    }

    /**
     * Get the stylesheet.
     *
     * @return a Tuple whose first value is the stylesheet base uri, and whose second
     *     value is the source for accessing the stylesheet
     */
    private Tuple2<String, Source> getStylesheet(final MapType options) throws XPathException {
        final Optional<String> stylesheetLocation = STYLESHEET_LOCATION.get(options).map(StringValue::getStringValue);
        if (stylesheetLocation.isPresent()) {
            //TODO(AR) handle database resources, see org.exist.xquery.functions.transform.EXistURIResolver.databaseSource
            return Tuple(stylesheetLocation.get(), new StreamSource(stylesheetLocation.get()));
        }

        final Optional<Node> stylesheetNode = STYLESHEET_NODE.get(options).map(NodeValue::getNode);
        if (stylesheetNode.isPresent()) {
            return Tuple(stylesheetNode.get().getBaseURI(), new DOMSource(stylesheetNode.get()));
        }

        final Optional<String> stylesheetText = STYLESHEET_TEXT.get(options).map(StringValue::getStringValue);
        if (stylesheetNode.isPresent()) {
            final String text = stylesheetText.get();
            final byte[] data = text.getBytes(UTF_8);
            final long checksum = XX_HASH_64.hash(data, 0, data.length, XXHASH64_SEED);
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

        if (node instanceof Element) {
            final Element elem = (Element) node;
            if (XSL_NS.equals(node.getNamespaceURI())
                    && "stylesheet".equals(node.getLocalName())) {
                final String version = elem.getAttribute("version");
                return Float.parseFloat(version);
            }
        }

        throw new XPathException(this, ErrorCodes.FOXT0002, "Unable to extract version from XSLT via DOM");
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
                    if (QN_XSL_STYLESHEET.equals(startElement.getName())) {
                        final Attribute version = startElement.getAttributeByName(QN_VERSION);
                        return Float.parseFloat(version.getValue());
                    }
                }
            }
        } catch (final XMLStreamException e) {
            throw new XPathException(this, ErrorCodes.FOXT0002, "Unable to extract version from XSLT via STaX: " + e.getMessage(), Sequence.EMPTY_SEQUENCE, e);
        }

        throw new XPathException(this, ErrorCodes.FOXT0002, "Unable to extract version from XSLT via STaX");
    }

    private static final Option<StringValue> BASE_OUTPUT_URI = new Option<>(
            "base-output-uri", v1_0, v2_0, v3_0);
    private static final Option<BooleanValue> CACHE = new Option<>(
            "cache", BooleanValue.TRUE, v1_0, v2_0, v3_0);
    private static final Option<StringValue> DELIVERY_FORMAT = new Option<>(
            "delivery-format", new StringValue("document"), v1_0, v2_0, v3_0);
    private static final Option<BooleanValue> ENABLE_ASSERTIONS = new Option<>(
            "enable-assertions", BooleanValue.FALSE, v3_0);
    private static final Option<BooleanValue> ENABLE_MESSAGES = new Option<>(
            "enable-messages", BooleanValue.TRUE, v1_0, v2_0, v3_0);
    private static final Option<BooleanValue> ENABLE_TRACE = new Option<>(
            "enable-trace", BooleanValue.TRUE, v2_0, v3_0);
    private static final Option<ArrayType> FUNCTION_PARAMS = new Option<>(
            "function-params", v3_0);
    private static final Option<Item> GLOBAL_CONTEXT_ITEM = new Option<>(
            "global-context-item", v3_0);
    private static final Option<QNameValue> INITIAL_FUNCTION = new Option<>(
            "initial-function", v3_0);
    private static final Option<Item> INITIAL_MATCH_SELECTION = new Option<>(
            "initial-match-selection", v3_0);
    private static final Option<QNameValue> INITIAL_MODE = new Option<>(
            "initial-match-selection", v1_0, v2_0, v3_0);
    private static final Option<QNameValue> INITIAL_TEMPLATE = new Option<>(
            "initial-template", v2_0, v3_0);
    private static final Option<StringValue> PACKAGE_NAME = new Option<>(
            "package-name", v3_0);
    private static final Option<StringValue> PACKAGE_LOCATION = new Option<>(
            "package-location", v3_0);
    private static final Option<NodeValue> PACKAGE_NODE = new Option<>(
            "package-node", v3_0);
    private static final Option<StringValue> PACKAGE_TEXT = new Option<>(
            "package-text", v3_0);
    private static final Option<StringValue> PACKAGE_VERSION = new Option<>(
            "package-version", new StringValue("*"), v3_0);
    private static final Option<FunctionReference> POST_PROCESS = new Option<>(
            "post-process", v1_0, v2_0, v3_0);
    private static final Option<MapType> REQUESTED_PROPERTIES = new Option<>(
            "requested-properties", v1_0, v2_0, v3_0);
    private static final Option<MapType> SERIALIZATION_PARAMS = new Option<>(
            "serialization-params", v1_0, v2_0, v3_0);
    private static final Option<NodeValue> SOURCE_NODE = new Option<>(
            "source-node", v1_0, v2_0, v3_0);
    private static final Option<NodeValue> STATIC_PARAMS = new Option<>(
            "static-params", v3_0);
    private static final Option<StringValue> STYLESHEET_BASE_URI = new Option<>(
            "stylesheet-base-uri", v1_0, v2_0, v3_0);
    private static final Option<StringValue> STYLESHEET_LOCATION = new Option<>(
            "stylesheet-location", v1_0, v2_0, v3_0);
    private static final Option<NodeValue> STYLESHEET_NODE = new Option<>(
            "stylesheet-node", v1_0, v2_0, v3_0);
    private static final Option<MapType> STYLESHEET_PARAMS = new Option<>(
            "stylesheet-params", v1_0, v2_0, v3_0);
    private static final Option<StringValue> STYLESHEET_TEXT = new Option<>(
            "stylesheet-text", v1_0, v2_0, v3_0);
    private static final Option<MapType> TEMPLATE_PARAMS = new Option<>(
            "template-params", v3_0);
    private static final Option<MapType> TUNNEL_PARAMS = new Option<>(
            "tunnel-params", v3_0);
    private static final Option<MapType> VENDOR_OPTIONS = new Option<>(
            "vendor-options", v1_0, v2_0, v3_0);
    private static final Option<DecimalValue> XSLT_VERSION = new Option<>(
            "xslt-version", v1_0, v2_0, v3_0);

    static class Option<T extends Item> {
        public static final float v1_0 = 1.0f;
        public static final float v2_0 = 2.0f;
        public static final float v3_0 = 3.0f;

        private final StringValue name;
        private final Optional<T> defaultValue;
        private final float[] appliesToVersions;

        public Option(final String name, final float... appliesToVersions) {
            this(name, Optional.empty(), appliesToVersions);
        }

        public Option(final String name, @Nullable final T defaultValue, final float... appliesToVersions) {
            this(name, Optional.ofNullable(defaultValue), appliesToVersions);
        }

        private Option(final String name, final Optional<T> defaultValue, final float... appliesToVersions) {
            this.name = new StringValue(name);
            this.defaultValue = defaultValue;
            this.appliesToVersions = appliesToVersions;
        }

        public Optional<T> get(final MapType options) {
            if (options.contains(name)) {
                return Optional.of((T)options.get(name).itemAt(0));
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
}
