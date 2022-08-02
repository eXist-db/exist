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
import com.github.krukow.clj_lang.PersistentTreeMap;
import io.lacuna.bifurcan.IEntry;
import net.jpountz.xxhash.XXHash64;
import net.jpountz.xxhash.XXHashFactory;
import net.sf.saxon.Configuration;
import net.sf.saxon.expr.parser.RetainedStaticContext;
import net.sf.saxon.functions.SystemProperty;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.s9api.*;
import net.sf.saxon.serialize.SerializationProperties;
import net.sf.saxon.trans.UncheckedXPathException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.dom.memtree.DocumentBuilderReceiver;
import org.exist.dom.memtree.DocumentImpl;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.dom.memtree.NamespaceNode;
import org.exist.security.PermissionDeniedException;
import org.exist.util.Holder;
import org.exist.xquery.*;
import org.exist.xquery.functions.array.ArrayType;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.util.DocUtils;
import org.exist.xquery.value.*;
import org.w3c.dom.*;

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
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.*;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.exist.Namespaces.XSL_NS;
import static org.exist.xquery.FunctionDSL.param;
import static org.exist.xquery.FunctionDSL.returnsOptMany;
import static org.exist.xquery.functions.fn.FnModule.functionSignature;
import static org.exist.xquery.functions.fn.FnTransform.Option.*;

/**
 * Implementation of fn:transform.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 * @author <a href="mailto:alan@evolvedbinary.com">Alan Paxton</a>
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

    static class SystemProperties {
        private static RetainedStaticContext retainedStaticContext = new RetainedStaticContext(SAXON_CONFIGURATION);

        static String get(QName qName) {
            return SystemProperty.getProperty(qName.getNamespaceURI(), qName.getLocalPart(), retainedStaticContext);
        }
    }

    private static final Convert.ToSaxon toSaxon = new Convert.ToSaxon() {
        @Override
        DocumentBuilder newDocumentBuilder() {
            return SAXON_PROCESSOR.newDocumentBuilder();
        }
    };

    private static final long XXHASH64_SEED = 0x2245a28e;
    private static final XXHash64 XX_HASH_64 = XXHashFactory.fastestInstance().hash64();

    private static final Cache<String, XsltExecutable> XSLT_EXECUTABLE_CACHE = Caffeine.newBuilder()
            .maximumSize(25)
            .weakValues()
            .build();

    public FnTransform(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    private XsltExecutable compileExecutable(final Options options) throws XPathException {
        final XsltCompiler xsltCompiler = FnTransform.SAXON_PROCESSOR.newXsltCompiler();
        xsltCompiler.setErrorListener(FnTransform.ERROR_LISTENER);

        for (final Map.Entry<net.sf.saxon.s9api.QName, XdmValue> entry : options.staticParams.entrySet()) {
            xsltCompiler.setParameter(entry.getKey(), entry.getValue());
        }

        for (final IEntry<AtomicValue, Sequence> entry : options.stylesheetParams) {
            final QName qKey = ((QNameValue) entry.key()).getQName();
            final XdmValue value = toSaxon.of(entry.value());
            xsltCompiler.setParameter(new net.sf.saxon.s9api.QName(qKey.getPrefix(), qKey.getLocalPart()), value);
        }

        // Take URI resolution into our own hands when there is no base
        xsltCompiler.setURIResolver((href, base) -> {
            try {
                final URI hrefURI = URI.create(href);
                if (!options.resolvedStylesheetBaseURI.isPresent() && !hrefURI.isAbsolute() && StringUtils.isEmpty(base)) {
                    final XPathException resolutionException = new XPathException(FnTransform.this,
                            ErrorCodes.XTSE0165,
                            "transform using a relative href, \n" +
                                    "using option stylesheet-text, but without stylesheet-base-uri");
                    throw new TransformerException(resolutionException);
                }
            } catch (final IllegalArgumentException e) {
                throw new TransformerException(e);
            }
            // Pass it back
            return null;
        });

        try {
            options.resolvedStylesheetBaseURI.ifPresent(anyURIValue -> options.xsltSource._2.setSystemId(anyURIValue.getStringValue()));
            FnTransform.ERROR_LISTENER.clear();
            return xsltCompiler.compile(options.xsltSource._2); // .compilePackage //TODO(AR) need to implement support for xslt-packages
        } catch (final SaxonApiException e) {
            //Generated by us, possibly in URI resolution, above - throw it directly
            throwOriginalXPathException(e, ErrorCodes.FOXT0003);
            final Optional<TransformerException> compilerException = FnTransform.ERROR_LISTENER.getWorst();
            if (compilerException.isPresent()) {
                throwOriginalXPathException(compilerException.get(), ErrorCodes.FOXT0003);
                throw new XPathException(this, ErrorCodes.FOXT0003, compilerException.get());
            }
            //Wrap any other error
            throw new XPathException(this, ErrorCodes.FOXT0003, e.getMessage());
        }
    }

    /**
     * Search for an XPathException in the cause chain, and return it "directly"
     * Either an eXist XPathException, which is immediate
     * Or a Saxon XPathException, when we convert it to something similar in eXist.
     *
     * @param e the top of the exception stack
     * @param defaultErrorCode use this code and its description to fill in blanks in what we finally throw
     * @throws XPathException the eventual eXist exception we may throw
     */
    private void throwOriginalXPathException(final Throwable e, final ErrorCodes.ErrorCode defaultErrorCode) throws XPathException {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof XPathException) {
                throw (XPathException) cause;
            }
            cause = cause.getCause();
        }

        cause = e;
        while (cause != null) {
            if (cause instanceof net.sf.saxon.trans.XPathException) {
                final net.sf.saxon.trans.XPathException xPathException = (net.sf.saxon.trans.XPathException)cause;
                final StructuredQName from = xPathException.getErrorCodeQName();
                if (from != null) {
                    final QName errorCodeQName = new QName(from.getLocalPart(), from.getURI(), from.getPrefix());
                    final ErrorCodes.ErrorCode errorCode = new ErrorCodes.ErrorCode(errorCodeQName, defaultErrorCode.getDescription());
                    throw new XPathException(errorCode, cause.getMessage());
                } else {
                    throw new XPathException(this, defaultErrorCode, cause.getMessage());
                }
            }
            cause = cause.getCause();
        }
    }

    /**
     * Hash on the options used to create a compiled executable
     * Hash should match only when the executable can be re-used.
     *
     * @param options options to read
     * @return a string, the hash we want
     */
    private String executableHash(final Options options) {

        final String uniquifier;
        if (options.resolvedStylesheetBaseURI.isPresent() || options.sourceTextChecksum.isPresent()) {
            uniquifier = "";
        } else {
            uniquifier = LocalDateTime.now().toString();
        }
        final String paramHash = Tuple(
                options.stylesheetParams,
                options.staticParams).toString();

        final String locationHash = Tuple(
                options.resolvedStylesheetBaseURI.map(AnyURIValue::getStringValue).orElse(""),
                options.sourceTextChecksum.orElse(0L),
                uniquifier,
                options.stylesheetNodeDocumentPath,
                options.stylesheetNodeDocumentPath).toString();

        final String hash = Tuple(locationHash, paramHash).toString();

        return hash;
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final Options options = new Options((MapType) args[0].itemAt(0));

        //TODO(AR) Saxon recommends to use a <code>StreamSource</code> or <code>SAXSource</code> instead of DOMSource for performance
        final Optional<Source> sourceNode = FnTransform.getSourceNode(options.sourceNode, context.getBaseURI());

        if (options.xsltVersion == 1.0f || options.xsltVersion == 2.0f || options.xsltVersion == 3.0f) {
            try {
                final Holder<XPathException> compileException = new Holder<>();
                final XsltExecutable xsltExecutable;
                if (options.shouldCache.orElse(BooleanValue.TRUE).getValue()) {
                    xsltExecutable = FnTransform.XSLT_EXECUTABLE_CACHE.get(executableHash(options), key -> {
                        try {
                            return compileExecutable(options);
                        } catch (final XPathException e) {
                            compileException.value = e;
                            return null;
                        }
                    });
                } else {
                    xsltExecutable = compileExecutable(options);
                }

                if (compileException.value != null) {
                    // if we could not compile the xslt, rethrow the error
                    throw compileException.value;
                }

                final Xslt30Transformer xslt30Transformer = xsltExecutable.load30();

                options.initialMode.ifPresent(qNameValue -> xslt30Transformer.setInitialMode(Convert.ToSaxon.of(qNameValue.getQName())));
                xslt30Transformer.setInitialTemplateParameters(options.templateParams, false);
                xslt30Transformer.setInitialTemplateParameters(options.tunnelParams, true);
                if (options.baseOutputURI.isPresent()) {
                    final AtomicValue baseOutputURI = options.baseOutputURI.get();
                    final AtomicValue asString = baseOutputURI.convertTo(Type.STRING);
                    if (asString instanceof StringValue) {
                        xslt30Transformer.setBaseOutputURI(asString.getStringValue());
                    }
                }

                // The delivery mechanism
                final SerializationProperties serializationProperties =
                        SerializationParameters.getAsSerializationProperties(
                                options.serializationParams.orElse(new MapType(context)),
                                (code, message) -> new XPathException(FnTransform.this, code, message));
                final Delivery delivery = new Delivery(options.deliveryFormat, serializationProperties);

                // Record the secondary result documents generated
                final Map<URI, Delivery> resultDocuments = new HashMap<>();
                xslt30Transformer.setResultDocumentHandler(resultDocumentURI -> {
                    final Delivery resultDelivery = new Delivery(options.deliveryFormat, serializationProperties);
                    resultDocuments.put(resultDocumentURI, resultDelivery);
                    return resultDelivery.createDestination(xslt30Transformer, true);
                });

                if (options.globalContextItem.isPresent()) {
                    final Item item = options.globalContextItem.get();
                    final XdmItem xdmItem = (XdmItem) toSaxon.of(item);
                    xslt30Transformer.setGlobalContextItem(xdmItem);
                } else if (sourceNode.isPresent()) {
                    final Document document;
                    Source source = sourceNode.get();
                    final Node node = ((DOMSource)sourceNode.get()).getNode();
                    if (!(node instanceof DocumentImpl)) {
                        //The source may not be a document
                        //If it isn't, it should be part of a document, so we build a DOMSource to use
                        document = node.getOwnerDocument();
                        source = new DOMSource(document);
                    }
                    final DocumentBuilder sourceBuilder = FnTransform.SAXON_PROCESSOR.newDocumentBuilder();
                    final XdmNode xdmNode = sourceBuilder.build(source);
                    xslt30Transformer.setGlobalContextItem(xdmNode);
                } else {
                    xslt30Transformer.setGlobalContextItem(null);
                }

                final TemplateInvocation invocation = new TemplateInvocation(
                        options, sourceNode, delivery, xslt30Transformer, resultDocuments);
                return invocation.invoke();

            } catch (final SaxonApiException e) {
                if (e.getErrorCode() != null) {
                    final QName errorQn = QName.fromJavaQName(e.getErrorCode().getStructuredQName().toJaxpQName());
                    throw new XPathException(this, new ErrorCodes.ErrorCode(errorQn, e.getMessage()), e.getMessage());
                } else {
                    throwOriginalXPathException(e, ErrorCodes.FOXT0003);
                    throw new XPathException(this, ErrorCodes.FOXT0003, e.getMessage());
                }
            } catch (final UncheckedXPathException e) {
                throw new XPathException(this, ErrorCodes.FOXT0003, e.getMessage());
            }

        } else {
            throw new XPathException(this, ErrorCodes.FOXT0001, "xslt-version: " + options.xsltVersion + " is not supported.");
        }
    }

    private class TemplateInvocation {

        final Options options;
        Optional<Source> sourceNode;
        final Delivery delivery;
        final Destination destination;
        final Xslt30Transformer xslt30Transformer;
        final Map<URI, Delivery> resultDocuments;

        TemplateInvocation(final Options options, final Optional<Source> sourceNode, final Delivery delivery, final Xslt30Transformer xslt30Transformer, final Map<URI, Delivery> resultDocuments) throws XPathException {
            this.options = options;
            this.sourceNode = sourceNode;
            this.delivery = delivery;
            this.destination = delivery.createDestination(xslt30Transformer, false);
            this.xslt30Transformer = xslt30Transformer;
            this.resultDocuments = resultDocuments;
        }

        private MapType invokeCallFunction() throws XPathException, SaxonApiException {
            final net.sf.saxon.s9api.QName qName = Convert.ToSaxon.of(options.initialFunction.get().getQName());
            final XdmValue[] functionParams;
            if (options.functionParams.isPresent()) {
                functionParams = toSaxon.of(options.functionParams.get());
            } else {
                throw new XPathException(FnTransform.this, ErrorCodes.FOXT0002, "Error - transform using XSLT 3.0 option initial-function, but the corresponding option function-params was not supplied.");
            }

            xslt30Transformer.callFunction(qName, functionParams, destination);
            return makeResultMap(options, delivery, resultDocuments);
        }

        private MapType invokeCallTemplate() throws XPathException, SaxonApiException {
            if (options.initialMode.isPresent()) {
                throw new XPathException(FnTransform.this, ErrorCodes.FOXT0002,
                        INITIAL_MODE.name + " supplied indicating apply-templates invocation, " +
                                "AND " + INITIAL_TEMPLATE.name + " supplied indicating call-template invocation.");
            }

            //TODO (AP) - Implement complete conversion in the {@link Convert} class
            //TODO (AP) - The saxDestination conversion loses type information in some cases
            //TODO (AP) - e.g. fn-transform-63 from XQTS has a <xsl:template name='main' as='xs:integer'>
            //TODO (AP) - which alongside "delivery-format":"raw" fails to deliver an int

            final QName qName = options.initialTemplate.get().getQName();
            xslt30Transformer.callTemplate(Convert.ToSaxon.of(qName), destination);
            return makeResultMap(options, delivery, resultDocuments);
        }

        private MapType invokeApplyTemplates() throws XPathException, SaxonApiException {
            if (options.initialMatchSelection.isPresent()) {
                final Sequence initialMatchSelection = options.initialMatchSelection.get();
                final Item item = initialMatchSelection.itemAt(0);
                if (item instanceof Document) {
                    final Source sourceIMS = new DOMSource((Document)item, context.getBaseURI().getStringValue());
                    xslt30Transformer.applyTemplates(sourceIMS, destination);
                } else {
                    final XdmValue selection = toSaxon.of(initialMatchSelection);
                    xslt30Transformer.applyTemplates(selection, destination);
                }
            } else if (sourceNode.isPresent()) {
                xslt30Transformer.applyTemplates(sourceNode.get(), destination);
            } else {
                throw new XPathException(FnTransform.this,
                        ErrorCodes.FOXT0002,
                        "One of " + SOURCE_NODE.name + " or " +
                                INITIAL_MATCH_SELECTION.name + " or " +
                                INITIAL_TEMPLATE.name + " or " +
                                INITIAL_FUNCTION.name + " is required.");
            }
            return makeResultMap(options, delivery, resultDocuments);
        }

        private MapType invoke() throws XPathException, SaxonApiException {
            if (options.initialFunction.isPresent()) {
                return invokeCallFunction();
            } else if (options.initialTemplate.isPresent()) {
                return invokeCallTemplate();
            } else {
                return invokeApplyTemplates();
            }
        }
    }

    private class Delivery {

        final DeliveryFormat format;
        final SerializationProperties serializationProperties;
        MemTreeBuilder builder;
        StringWriter stringWriter;

        RawDestination rawDestination;

        Delivery(final DeliveryFormat format, final SerializationProperties serializationProperties) {
            this.format = format;
            this.serializationProperties = serializationProperties;
        }

        final Destination createDestination(final Xslt30Transformer xslt30Transformer, final boolean forceCreation) {
            switch (format) {
                case DOCUMENT:
                    if (!forceCreation) {
                        this.builder = context.getDocumentBuilder();
                    } else {
                        this.builder = new MemTreeBuilder(context);
                        this.builder.startDocument();
                    }
                    return new SAXDestination(new DocumentBuilderReceiver(builder));
                case SERIALIZED:
                    final Serializer serializer = xslt30Transformer.newSerializer();
                    final SerializationProperties stylesheetProperties = serializer.getSerializationProperties();

                    final SerializationProperties combinedProperties =
                    SerializationParameters.combinePropertiesAndCharacterMaps(
                                    stylesheetProperties,
                                    serializationProperties);

                    serializer.setOutputProperties(combinedProperties);
                    stringWriter = new StringWriter();
                    serializer.setOutputWriter(stringWriter);
                    return serializer;
                case RAW:
                    this.rawDestination = new RawDestination();
                    return rawDestination;
                default:
                    return null;
            }
        }

        final String getSerializedString() {

            if (stringWriter == null) {
                return null;
            }
            return stringWriter.getBuffer().toString();
        }

        final DocumentImpl getDocument() {
            if (builder == null) {
                return null;
            }
            return builder.getDocument();
        }

        final XdmValue getXdmValue() {
            if (rawDestination == null) {
                return null;
            }
            return rawDestination.getXdmValue();
        }
   }

    private MapType makeResultMap(final Options options, final Delivery delivery, final Map<URI, Delivery> resultDocuments) throws XPathException {

        final MapType outputMap = new MapType(context);
        final AtomicValue outputKey;
        if (options.baseOutputURI.isPresent()) {
            outputKey = options.baseOutputURI.get();
        } else {
            outputKey = new StringValue("output");
        }

        final Sequence primaryValue = postProcess(outputKey, convertToDeliveryFormat(options, delivery), options.postProcess);
        outputMap.add(outputKey, primaryValue);

        for (final Map.Entry<URI, Delivery> resultDocument : resultDocuments.entrySet()) {
            final AnyURIValue key = new AnyURIValue(resultDocument.getKey());
            final Sequence value = postProcess(key, convertToDeliveryFormat(options, resultDocument.getValue()), options.postProcess);
            outputMap.add(key, value);
        }

        return outputMap;
    }

    private Sequence postProcess(final AtomicValue key, final Sequence before, final Optional<FunctionReference> postProcessingFunction) throws XPathException {
        if (postProcessingFunction.isPresent()) {
            FunctionReference functionReference = postProcessingFunction.get();
            return functionReference.evalFunction(null, null, new Sequence[]{key, before});
        } else {
            return before;
        }
    }

    private Sequence convertToDeliveryFormat(final Options options, final Delivery delivery) throws XPathException {

        switch (options.deliveryFormat) {
            case SERIALIZED:
                return new StringValue(delivery.getSerializedString());
            case RAW:
                //TODO (AP) rawOutput and mediation via document when the Xdm value is complex, is a hack
                final XdmValue xdmValue = delivery.getXdmValue();
                if (xdmValue != null) {
                    return Convert.ToExist.of(xdmValue);
                } else {
                    return rawOutput(delivery.getDocument());
                }
            case DOCUMENT:
            default:
                return delivery.getDocument();
        }
    }

    private static Sequence rawOutput(final NodeValue outputDocument) throws XPathException {
        final Node node = outputDocument.getNode();
        if (node != null) {
            final NodeList children = node.getChildNodes();
            final int length = children.getLength();
            if (length == 0) {
                return Sequence.EMPTY_SEQUENCE;
            } else if (length == 1) {
                final Node item = children.item(0);
                if (item instanceof NodeValue) {
                    return (NodeValue)item;
                }
            } else {
                final ValueSequence valueSequence = new ValueSequence();
                for (int i = 0; i < children.getLength(); i++) {
                    final Node child = children.item(i);
                    if (child instanceof NodeValue) {
                        valueSequence.add((NodeValue)child);
                    }
                }
                return valueSequence;
            }
            throw new XPathException(ErrorCodes.XPTY0004, "Unable to produce raw output from contents of: " + outputDocument);
        }
        return Sequence.EMPTY_SEQUENCE;
    }

    private static Optional<Source> getSourceNode(final Optional<NodeValue> sourceNode, final AnyURIValue baseURI) {
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

        final URI uri = URI.create(stylesheetLocation);
        if (uri.isAbsolute()) {
            return resolvePossibleStylesheetLocation(stylesheetLocation);
        } else {
            final AnyURIValue resolved = resolveURI(new AnyURIValue(stylesheetLocation), context.getBaseURI());
            return resolvePossibleStylesheetLocation(resolved.getStringValue());
        }
    }

    /**
     * Resolve an absolute stylesheet location
     *
     * @param location
     * @return
     * @throws XPathException
     */
    private Source resolvePossibleStylesheetLocation(final String location) throws XPathException {

        Sequence document = null;
        try {
            document = DocUtils.getDocument(context, location);
        } catch (final PermissionDeniedException e) {
            throw new XPathException(this, ErrorCodes.FODC0002,
                    "Can not access '" + location + "'" + e.getMessage());
        }
        if (document != null && document.hasOne() && Type.subTypeOf(document.getItemType(), Type.NODE)) {
            return new DOMSource((Node) document.itemAt(0));
        }
        throw new XPathException(this, ErrorCodes.FODC0002,
                "Location '"+ location + "' returns an item which is not a document node");
    }

    /**
     * URI resolution, the core should be the same as for fn:resolve-uri
     * @param relative URI to resolve
     * @param base to resolve against
     * @return resolved URI
     * @throws XPathException if resolution is not possible
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

        final List<Tuple2<String, Source>> results = new ArrayList<>(1);
        final Optional<String> stylesheetLocation = FnTransform.STYLESHEET_LOCATION.get(options).map(StringValue::getStringValue);
        if (stylesheetLocation.isPresent()) {
            results.add(Tuple(stylesheetLocation.get(), resolveStylesheetLocation(stylesheetLocation.get())));
        }

        final Optional<Node> stylesheetNode = FnTransform.STYLESHEET_NODE.get(options).map(NodeValue::getNode);
        if (stylesheetNode.isPresent()) {
            final Node node = stylesheetNode.get();
            results.add(Tuple(node.getBaseURI(), new DOMSource(node)));
        }

        final Optional<String> stylesheetText = FnTransform.STYLESHEET_TEXT.get(options).map(StringValue::getStringValue);
        if (stylesheetText.isPresent()) {
            results.add(Tuple("", new StringSource(stylesheetText.get())));
        }

        if (results.size() > 1) {
            throw new XPathException(this, ErrorCodes.FOXT0002, "More than one of stylesheet-location, stylesheet-node, and stylesheet-text was set");
        }

        if (results.isEmpty()) {
            throw new XPathException(this, ErrorCodes.FOXT0002, "None of stylesheet-location, stylesheet-node, or stylesheet-text was set");
        }

        return results.get(0);
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

    private static final Option<StringValue> BASE_OUTPUT_URI = new ItemOption<>(
            Type.STRING, "base-output-uri", v1_0, v2_0, v3_0);
    private static final Option<BooleanValue> CACHE = new ItemOption<>(
            Type.BOOLEAN, "cache", BooleanValue.TRUE, v1_0, v2_0, v3_0);
    private static final Option<StringValue> DELIVERY_FORMAT = new ItemOption<>(
            Type.STRING, "delivery-format", new StringValue("document"), v1_0, v2_0, v3_0);
    private static final Option<BooleanValue> ENABLE_ASSERTIONS = new ItemOption<>(
            Type.BOOLEAN, "enable-assertions", BooleanValue.FALSE, v3_0);
    private static final Option<BooleanValue> ENABLE_MESSAGES = new ItemOption<>(
            Type.BOOLEAN, "enable-messages", BooleanValue.TRUE, v1_0, v2_0, v3_0);
    private static final Option<BooleanValue> ENABLE_TRACE = new ItemOption<>(
            Type.BOOLEAN, "enable-trace", BooleanValue.TRUE, v2_0, v3_0);
    private static final Option<ArrayType> FUNCTION_PARAMS = new ItemOption<>(
            Type.ARRAY,"function-params", v3_0);
    private static final Option<Item> GLOBAL_CONTEXT_ITEM = new ItemOption<>(
            Type.ITEM, "global-context-item", v3_0);
    private static final Option<QNameValue> INITIAL_FUNCTION = new ItemOption<>(
            Type.QNAME,"initial-function", v3_0);
    private static final Option<Sequence> INITIAL_MATCH_SELECTION = new SequenceOption<>(
            Type.ATOMIC,Type.NODE, "initial-match-selection", v3_0);
    private static final Option<QNameValue> INITIAL_MODE = new ItemOption<>(
            Type.QNAME,"initial-mode", v1_0, v2_0, v3_0);
    private static final Option<QNameValue> INITIAL_TEMPLATE = new ItemOption<>(
            Type.QNAME,"initial-template", v2_0, v3_0);
    private static final Option<StringValue> PACKAGE_NAME = new ItemOption<>(
            Type.STRING,"package-name", v3_0);
    private static final Option<StringValue> PACKAGE_LOCATION = new ItemOption<>(
            Type.STRING,"package-location", v3_0);
    private static final Option<NodeValue> PACKAGE_NODE = new ItemOption<>(
            Type.NODE,"package-node", v3_0);
    private static final Option<StringValue> PACKAGE_TEXT = new ItemOption<>(
            Type.STRING,"package-text", v3_0);
    private static final Option<StringValue> PACKAGE_VERSION = new ItemOption<>(
            Type.STRING,"package-version", new StringValue("*"), v3_0);
    private static final Option<FunctionReference> POST_PROCESS = new ItemOption<>(
            Type.FUNCTION_REFERENCE,"post-process", v1_0, v2_0, v3_0);
    private static final Option<MapType> REQUESTED_PROPERTIES = new ItemOption<>(
            Type.MAP,"requested-properties", v1_0, v2_0, v3_0);
    private static final Option<MapType> SERIALIZATION_PARAMS = new ItemOption<>(
            Type.MAP,"serialization-params", v1_0, v2_0, v3_0);
    private static final Option<NodeValue> SOURCE_NODE = new ItemOption<>(
            Type.NODE,"source-node", v1_0, v2_0, v3_0);
    private static final Option<MapType> STATIC_PARAMS = new ItemOption<>(
            Type.MAP,"static-params", v3_0);
    private static final Option<StringValue> STYLESHEET_BASE_URI = new ItemOption<>(
            Type.STRING, "stylesheet-base-uri", v1_0, v2_0, v3_0);
    private static final Option<StringValue> STYLESHEET_LOCATION = new ItemOption<>(
            Type.STRING,"stylesheet-location", v1_0, v2_0, v3_0);
    private static final Option<NodeValue> STYLESHEET_NODE = new ItemOption<>(
            Type.NODE,"stylesheet-node", v1_0, v2_0, v3_0);
    private static final Option<MapType> STYLESHEET_PARAMS = new ItemOption<>(
            Type.MAP,"stylesheet-params", v1_0, v2_0, v3_0);
    private static final Option<StringValue> STYLESHEET_TEXT = new ItemOption<>(
            Type.STRING,"stylesheet-text", v1_0, v2_0, v3_0);
    private static final Option<MapType> TEMPLATE_PARAMS = new ItemOption<>(
            Type.MAP,"template-params", v3_0);
    private static final Option<MapType> TUNNEL_PARAMS = new ItemOption<>(
            Type.MAP,"tunnel-params", v3_0);
    private static final Option<MapType> VENDOR_OPTIONS = new ItemOption<>(
            Type.MAP,"vendor-options", v1_0, v2_0, v3_0);
    private static final Option<DecimalValue> XSLT_VERSION = new ItemOption<>(
            Type.DECIMAL,"xslt-version", v1_0, v2_0, v3_0);

    static abstract class Option<T> {
        public static final float v1_0 = 1.0f;
        public static final float v2_0 = 2.0f;
        public static final float v3_0 = 3.0f;

        protected final StringValue name;
        protected final Optional<T> defaultValue;
        protected final float[] appliesToVersions;
        protected final int itemSubtype;

        private Option(final int itemSubtype, final String name, final Optional<T> defaultValue, final float... appliesToVersions) {
            this.name = new StringValue(name);
            this.defaultValue = defaultValue;
            this.appliesToVersions = appliesToVersions;
            this.itemSubtype = itemSubtype;
        }

        public abstract Optional<T> get(final MapType options) throws XPathException;

        private boolean notApplicableToVersion(final float xsltVersion) {
            for (final float appliesToVersion : appliesToVersions) {
                if (xsltVersion == appliesToVersion) {
                    return false;
                }
            }
            return true;
        }

        public Optional<T> get(final float xsltVersion, final MapType options) throws XPathException {
            if (notApplicableToVersion(xsltVersion)) {
                return Optional.empty();
            }
            
            return get(options);
        }
    }

    static class SequenceOption<T extends Sequence> extends Option<T> {

        private final int sequenceSubtype;

        public SequenceOption(final int sequenceSubtype, final int itemSubtype, final String name, final float... appliesToVersions) {
            super(itemSubtype, name, Optional.empty(), appliesToVersions);
            this.sequenceSubtype = sequenceSubtype;
        }

        public SequenceOption(final int sequenceSubtype, final int itemSubtype, final String name, @Nullable final T defaultValue, final float... appliesToVersions) {
            super(itemSubtype, name, Optional.ofNullable(defaultValue), appliesToVersions);
            this.sequenceSubtype = sequenceSubtype;
        }

        @Override
        public Optional<T> get(final MapType options) throws XPathException {
            if (options.contains(name)) {
                final Sequence sequence = options.get(name);
                if (sequence != null) {
                    if (Type.subTypeOf(sequence.getItemType(), sequenceSubtype)) {
                        return Optional.of((T) sequence);
                    }
                    final Item item0 = options.get(name).itemAt(0);
                    if (item0 != null) {
                        if (Type.subTypeOf(item0.getType(), itemSubtype)) {
                            return Optional.of((T) item0);
                        } else {
                            throw new XPathException(
                                    ErrorCodes.XPTY0004, "Type error: expected " + Type.getTypeName(itemSubtype) + ", got " + Type.getTypeName(sequence.getItemType()));
                        }
                    }
                }
            }
            return defaultValue;
        }
    }

    static class ItemOption<T extends Item> extends Option<T> {

        public ItemOption(final int itemSubtype, final String name, final float... appliesToVersions) {
            super(itemSubtype, name, Optional.empty(), appliesToVersions);
        }

        public ItemOption(final int itemSubtype, final String name, @Nullable final T defaultValue, final float... appliesToVersions) {
            super(itemSubtype, name, Optional.ofNullable(defaultValue), appliesToVersions);
        }

        @Override
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
    }

    private static class ErrorListenerLog4jAdapter implements ErrorListener {
        private final Logger logger;
        private Optional<TransformerException> lastError;
        private Optional<TransformerException> lastFatal;

        public void clear() {
            lastError = Optional.empty();
            lastFatal = Optional.empty();
        }

        public Optional<TransformerException> getWorst() {
            if (lastFatal.isPresent()) return lastFatal;
            if (lastError.isPresent()) return lastError;
            return Optional.empty();
        }

        public ErrorListenerLog4jAdapter(final Logger logger) {
            this.logger = logger;
        }

        @Override
        public void warning(final TransformerException e) {
            logger.warn(e.getMessage(), e);
        }

        @Override
        public void error(final TransformerException e) {
            lastError = Optional.of(e);
            logger.error(e.getMessage(), e);
        }

        @Override
        public void fatalError(final TransformerException e) {
            lastFatal = Optional.of(e);
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

        private Map<net.sf.saxon.s9api.QName, XdmValue> readParamsMap(final Optional<MapType> option, final String name) throws XPathException {

            final Map<net.sf.saxon.s9api.QName, XdmValue> result = new HashMap<>();

            final MapType paramsMap = option.orElse(new MapType(context));
            for (final IEntry<AtomicValue, Sequence> entry : paramsMap) {
                final AtomicValue key = entry.key();
                if (!(key instanceof QNameValue)) {
                    throw new XPathException(FnTransform.this, ErrorCodes.FOXT0002, "Supplied " + name + " is not a valid xs:qname: " + entry);
                }
                if (!(entry.value() instanceof Sequence)) {
                    throw new XPathException(FnTransform.this, ErrorCodes.FOXT0002, "Supplied " + name + " is not a valid xs:sequence: " + entry);
                }
                result.put(Convert.ToSaxon.of((QNameValue) key), toSaxon.of(entry.value()));
            }
            return result;
        }

        final Tuple2<String, Source> xsltSource;
        final MapType stylesheetParams;
        final Map<net.sf.saxon.s9api.QName, XdmValue> staticParams;
        final float xsltVersion;
        final Optional<AnyURIValue> resolvedStylesheetBaseURI;
        final Optional<QNameValue> initialFunction;
        final Optional<ArrayType> functionParams;
        final Map<net.sf.saxon.s9api.QName, XdmValue> templateParams;
        final Map<net.sf.saxon.s9api.QName, XdmValue> tunnelParams;
        final Optional<QNameValue> initialTemplate;
        final Optional<QNameValue> initialMode;
        final Optional<NodeValue> sourceNode;
        final Optional<Item> globalContextItem;
        final Optional<Sequence> initialMatchSelection;
        final Optional<BooleanValue> shouldCache;
        final DeliveryFormat deliveryFormat;
        final Optional<StringValue> baseOutputURI;
        final Optional<MapType> serializationParams;

        final Optional<Long> sourceTextChecksum;
        final String stylesheetNodeDocumentPath;

        final Optional<FunctionReference> postProcess;

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
            if (!StringUtils.isEmpty(stylesheetBaseUri)) {
                resolvedStylesheetBaseURI = Optional.of(resolveURI(new AnyURIValue(stylesheetBaseUri), context.getBaseURI()));
            } else {
                resolvedStylesheetBaseURI = Optional.empty();
            }
            initialFunction = FnTransform.INITIAL_FUNCTION.get(options);
            functionParams = FnTransform.FUNCTION_PARAMS.get(options);

            initialTemplate = FnTransform.INITIAL_TEMPLATE.get(options);
            initialMode = FnTransform.INITIAL_MODE.get(options);

            templateParams = readParamsMap(FnTransform.TEMPLATE_PARAMS.get(options), FnTransform.TEMPLATE_PARAMS.name.getStringValue());
            tunnelParams = readParamsMap(FnTransform.TUNNEL_PARAMS.get(options), FnTransform.TUNNEL_PARAMS.name.getStringValue());
            staticParams = readParamsMap(FnTransform.STATIC_PARAMS.get(options), FnTransform.STATIC_PARAMS.name.getStringValue());

            sourceNode = FnTransform.SOURCE_NODE.get(options);
            globalContextItem = FnTransform.GLOBAL_CONTEXT_ITEM.get(options);
            initialMatchSelection = FnTransform.INITIAL_MATCH_SELECTION.get(options);
            if (sourceNode.isPresent() && initialMatchSelection.isPresent()) {
                throw new XPathException(ErrorCodes.FOXT0002,
                        "Both " + SOURCE_NODE.name + " and " + INITIAL_MATCH_SELECTION.name + " were supplied. " +
                                "These options cannot both be supplied.");
            }
            sourceTextChecksum = getSourceTextChecksum(options);
            stylesheetNodeDocumentPath = getStylesheetNodeDocumentPath(options);

            shouldCache = FnTransform.CACHE.get(xsltVersion, options);

            deliveryFormat = getDeliveryFormat(xsltVersion, options);

            baseOutputURI = FnTransform.BASE_OUTPUT_URI.get(xsltVersion, options);

            serializationParams = FnTransform.SERIALIZATION_PARAMS.get(xsltVersion, options);

            validateRequestedProperties(FnTransform.REQUESTED_PROPERTIES.get(xsltVersion, options).orElse(new MapType(context)));

            postProcess = FnTransform.POST_PROCESS.get(xsltVersion, options);
        }

        private DeliveryFormat getDeliveryFormat(final float xsltVersion, final MapType options) throws XPathException {
            final String string = FnTransform.DELIVERY_FORMAT.get(xsltVersion, options).get().getStringValue().toUpperCase();
            final DeliveryFormat deliveryFormat;
            try {
                deliveryFormat = DeliveryFormat.valueOf(string);
            } catch (final IllegalArgumentException ie) {
                throw new XPathException(FnTransform.this, ErrorCodes.FOXT0002,
                        ": \"" + string + "\" is not a valid " + FnTransform.DELIVERY_FORMAT.name);
            }
            /* TODO (AP) it's unclear (spec vs XQTS) if this is meant to happen, or not ??
            if (deliveryFormat == DeliveryFormat.RAW && xsltVersion < 3.0f) {
                throw new XPathException(FnTransform.this, ErrorCodes.FOXT0002, "Supplied " + FnTransform.DELIVERY_FORMAT.name +
                        " is not valid when using XSLT version " + xsltVersion);
            }
             */
            return deliveryFormat;
        }

        private Optional<Long> getSourceTextChecksum(final MapType options) throws XPathException {
            final Optional<String> stylesheetText = FnTransform.STYLESHEET_TEXT.get(options).map(StringValue::getStringValue);
            if (stylesheetText.isPresent()) {
                final String text = stylesheetText.get();
                final byte[] data = text.getBytes(UTF_8);
                return Optional.of(FnTransform.XX_HASH_64.hash(data, 0, data.length, FnTransform.XXHASH64_SEED));
            }
            return Optional.empty();
        }

        private String getStylesheetNodeDocumentPath(final MapType options) throws XPathException {
            final Optional<Node> stylesheetNode = FnTransform.STYLESHEET_NODE.get(options).map(NodeValue::getNode);
            if (stylesheetNode.isPresent()) {
                return TreeUtils.pathTo(stylesheetNode.get()).toString();
            }
            return "";
        }

        private void validateRequestedProperties(final MapType requestedProperties) throws XPathException {
            for (final IEntry<AtomicValue, Sequence> entry : requestedProperties) {
                final AtomicValue key = entry.key();
                if (!Type.subTypeOf(key.getType(), Type.QNAME)) {
                    throw new XPathException(ErrorCodes.XPTY0004, "Type error: requested-properties key: " + key.toString() + " is not a QName");
                }
                final Sequence value = entry.value();
                if (!value.hasOne()) {
                    throw new XPathException(ErrorCodes.XPTY0004, "Type error: requested-properties " + key.toString() + " does not have a single item value.");
                }
                final Item item = value.itemAt(0);
                final String requiredPropertyValue;
                if (Type.subTypeOf(item.getType(), Type.STRING)) {
                    requiredPropertyValue = item.getStringValue();
                } else if (Type.subTypeOf(item.getType(), Type.BOOLEAN)) {
                    requiredPropertyValue = ((BooleanValue) item).getValue() ? "yes" : "no";
                } else {
                    throw new XPathException(ErrorCodes.XPTY0004,
                            "Type error: requested-properties " + key +
                                    " is not a " + Type.getTypeName(Type.STRING) +
                                    " or a " + Type.getTypeName(Type.BOOLEAN));
                }
                final String actualPropertyValue = SystemProperties.get(((QNameValue) key).getQName());
                if (!actualPropertyValue.equalsIgnoreCase(requiredPropertyValue)) {
                    throw new XPathException(ErrorCodes.FOXT0001,
                            "The XSLT processor cannot provide the requested-property: " + key +
                                    " requested: " + requiredPropertyValue +
                                    ", actual: " + actualPropertyValue);
                }
            }
        }
    }

}
