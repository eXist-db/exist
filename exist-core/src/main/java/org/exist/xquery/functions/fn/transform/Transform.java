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

package org.exist.xquery.functions.fn.transform;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.lacuna.bifurcan.IEntry;
import net.sf.saxon.Configuration;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.s9api.*;
import net.sf.saxon.serialize.SerializationProperties;
import net.sf.saxon.trans.UncheckedXPathException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.util.Holder;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.fn.FnTransform;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.*;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.annotation.Nonnull;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;
import static org.exist.xquery.functions.fn.transform.Options.Option.*;

/**
 * Implementation of fn:transform.
 *
 * This is the core of the eval() function of fn:transform
 * It is a separate class due to the multiplicity of options that must be dealt with,
 * which lead to too much code in a single file.
 *
 * This class contains the core of the logic
 * - create an XSLT compiler (if we don't have one compiled already, which matches our stylesheet)
 * - set parameters on the compiler
 * - create a transformer from the compiler
 * - set parameters on the transformer (inputs)
 * - invoke the transformation
 * - deliver and postprocess the output in the required form
 *
 * The parsing and checking of the options to fn:transform is isolated in {@link Options}
 * Delivery and output of the result, depending on the required format, is in {@link Delivery}
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 * @author <a href="mailto:alan@evolvedbinary.com">Alan Paxton</a>
 */
public class Transform {

    private static final Logger LOGGER =  LogManager.getLogger(org.exist.xquery.functions.fn.transform.Transform.class);
    private static final org.exist.xquery.functions.fn.transform.Transform.ErrorListenerLog4jAdapter ERROR_LISTENER = new Transform.ErrorListenerLog4jAdapter(Transform.LOGGER);

    //TODO(AR) if you want Saxon-EE features we need to set those in the Configuration
    static final Configuration SAXON_CONFIGURATION = new Configuration();
    private static final Processor SAXON_PROCESSOR = new Processor(org.exist.xquery.functions.fn.transform.Transform.SAXON_CONFIGURATION);

    static final Convert.ToSaxon toSaxon = new Convert.ToSaxon() {
        @Override
        DocumentBuilder newDocumentBuilder() {
            return SAXON_PROCESSOR.newDocumentBuilder();
        }
    };

    private static final Cache<String, XsltExecutable> XSLT_EXECUTABLE_CACHE = Caffeine.newBuilder()
            .maximumSize(25)
            .weakValues()
            .build();

    private final XQueryContext context;
    private final FnTransform fnTransform;

    public Transform(final XQueryContext context, final FnTransform fnTransform) {
        this.context = context;
        this.fnTransform = fnTransform;
    }

    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {

        final Options options = new Options(context, fnTransform, (MapType) args[0].itemAt(0));

        //TODO(AR) Saxon recommends to use a <code>StreamSource</code> or <code>SAXSource</code> instead of DOMSource for performance
        final Optional<Source> sourceNode = Transform.getSourceNode(options.sourceNode, context.getBaseURI());

        if (options.xsltVersion.equals(V1_0) || options.xsltVersion.equals(V2_0) || options.xsltVersion.equals(V3_0)) {
            try {
                final Holder<XPathException> compileException = new Holder<>();
                final XsltExecutable xsltExecutable;
                if (options.shouldCache.orElse(BooleanValue.TRUE).getValue()) {
                    xsltExecutable = Transform.XSLT_EXECUTABLE_CACHE.get(executableHash(options), key -> {
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
                if (xsltExecutable == null) {
                    throw new XPathException(fnTransform, ErrorCodes.FOXT0003, "Unable to compile stylesheet (No error returned from compilation)");
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
                                (code, message) -> new XPathException(fnTransform, code, message));
                final Delivery delivery = new Delivery(context, options.deliveryFormat, serializationProperties);

                // Record the secondary result documents generated
                final Map<URI, Delivery> resultDocuments = new HashMap<>();
                xslt30Transformer.setResultDocumentHandler(resultDocumentURI -> {
                    final Delivery resultDelivery = new Delivery(context, options.deliveryFormat, serializationProperties);
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
                    if (!(node instanceof org.exist.dom.memtree.DocumentImpl) && !(node instanceof org.exist.dom.persistent.DocumentImpl)) {
                        //The source may not be a document
                        //If it isn't, it should be part of a document, so we build a DOMSource to use
                        document = node.getOwnerDocument();
                        source = new DOMSource(document);
                    }
                    final DocumentBuilder sourceBuilder = Transform.SAXON_PROCESSOR.newDocumentBuilder();
                    final XdmNode xdmNode = sourceBuilder.build(source);
                    xslt30Transformer.setGlobalContextItem(xdmNode);
                } else {
                    xslt30Transformer.setGlobalContextItem(null);
                }

                final Transform.TemplateInvocation invocation = new Transform.TemplateInvocation(
                        options, sourceNode, delivery, xslt30Transformer, resultDocuments);
                return invocation.invoke();
            } catch (final SaxonApiException e) {
              throw originalXPathException("Could not transform with "+options.xsltSource._1+" line "+e.getLineNumber()+": ", e, ErrorCodes.FOXT0003);
            } catch (final UncheckedXPathException e) {
              throw originalXPathException("Could not transform with "+options.xsltSource._1+" line "+e.getXPathException().getLocationAsString()+": ", e, ErrorCodes.FOXT0003);
            }

        } else {
            throw new XPathException(fnTransform, ErrorCodes.FOXT0001, "xslt-version: " + options.xsltVersion + " is not supported.");
        }
    }


    private XsltExecutable compileExecutable(final Options options) throws XPathException {

        final XsltCompiler xsltCompiler = org.exist.xquery.functions.fn.transform.Transform.SAXON_PROCESSOR.newXsltCompiler();
        final SingleRequestErrorListener errorListener = new SingleRequestErrorListener(Transform.ERROR_LISTENER);
        xsltCompiler.setErrorListener(errorListener);

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
                if ((!options.resolvedStylesheetBaseURI.isPresent()) && !hrefURI.isAbsolute() && StringUtils.isEmpty(base)) {
                    final XPathException resolutionException = new XPathException(fnTransform,
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
            return xsltCompiler.compile(options.xsltSource._2); //TODO(AR) need to implement support for xslt-packages
        } catch (final SaxonApiException e) {
            final Optional<Exception> compilerException = errorListener.getWorst().map(e1 -> e1);
            throw originalXPathException("Could not compile stylesheet: ", compilerException.orElse(e), ErrorCodes.FOXT0003);
        }
    }

    /**
     * Search for an XPathException in the cause chain, and return it "directly"
     * Either an eXist XPathException, which is immediate
     * Or a Saxon XPathException, when we convert it to something similar in eXist.
     *
     * @param e the top of the exception stack
     * @param defaultErrorCode use this code and its description to fill in blanks in what we finally throw
     * @return XPathException the eventual eXist exception which the caller is expected to throw
     */
    private XPathException originalXPathException(final String prefix, @Nonnull final Throwable e, final ErrorCodes.ErrorCode defaultErrorCode) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof XPathException) {
                return new XPathException(fnTransform, ((XPathException) cause).getErrorCode(), prefix + cause.getMessage());
            }
            cause = cause.getCause();
        }

        cause = e;
        while (cause != null) {
            if (cause instanceof net.sf.saxon.trans.XPathException) {
                final StructuredQName from = ((net.sf.saxon.trans.XPathException) cause).getErrorCodeQName();
                if (from != null) {
                    final QName errorCodeQName = new QName(from.getLocalPart(), from.getURI(), from.getPrefix());
                    final ErrorCodes.ErrorCode errorCode = new ErrorCodes.ErrorCode(errorCodeQName, cause.getMessage());
                    return new XPathException(fnTransform, errorCode, prefix + cause.getMessage());
                } else {
                    return new XPathException(fnTransform, defaultErrorCode, prefix + cause.getMessage());
                }
            }
            cause = cause.getCause();
        }

        return new XPathException(fnTransform, defaultErrorCode, prefix + e.getMessage());
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

        return Tuple(locationHash, paramHash).toString();
    }

    private class TemplateInvocation {

        final Options options;
        Optional<Source> sourceNode;
        final Delivery delivery;
        final Destination destination;
        final Xslt30Transformer xslt30Transformer;
        final Map<URI, Delivery> resultDocuments;

        TemplateInvocation(final Options options, final Optional<Source> sourceNode, final Delivery delivery, final Xslt30Transformer xslt30Transformer, final Map<URI, Delivery> resultDocuments) {
            this.options = options;
            this.sourceNode = sourceNode;
            this.delivery = delivery;
            this.destination = delivery.createDestination(xslt30Transformer, false);
            this.xslt30Transformer = xslt30Transformer;
            this.resultDocuments = resultDocuments;
        }

        private MapType invokeCallFunction() throws XPathException, SaxonApiException {
            assert options.initialFunction.isPresent();
            final net.sf.saxon.s9api.QName qName = Convert.ToSaxon.of(options.initialFunction.get().getQName());
            final XdmValue[] functionParams;
            if (options.functionParams.isPresent()) {
                functionParams = toSaxon.of(options.functionParams.get());
            } else {
                throw new XPathException(fnTransform, ErrorCodes.FOXT0002, "Error - transform using XSLT 3.0 option initial-function, but the corresponding option function-params was not supplied.");
            }

            xslt30Transformer.callFunction(qName, functionParams, destination);
            return makeResultMap(options, delivery, resultDocuments);
        }

        private MapType invokeCallTemplate() throws XPathException, SaxonApiException {
            assert options.initialTemplate.isPresent();
            if (options.initialMode.isPresent()) {
                throw new XPathException(fnTransform, ErrorCodes.FOXT0002,
                        Options.INITIAL_MODE.name + " supplied indicating apply-templates invocation, " +
                                "AND " + Options.INITIAL_TEMPLATE.name + " supplied indicating call-template invocation.");
            }

            // Convert using our own {@link Convert} class
            // The saxDestination conversion loses type information in some cases
            // e.g. fn-transform-63 from XQTS has a <xsl:template name='main' as='xs:integer'>
            // which alongside "delivery-format":"raw" fails to deliver an int

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
                throw new XPathException(fnTransform,
                        ErrorCodes.FOXT0002,
                        "One of " + Options.SOURCE_NODE.name + " or " +
                                Options.INITIAL_MATCH_SELECTION.name + " or " +
                                Options.INITIAL_TEMPLATE.name + " or " +
                                Options.INITIAL_FUNCTION.name + " is required.");
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

        private MapType makeResultMap(final Options options, final Delivery primaryDelivery, final Map<URI, Delivery> resultDocuments) throws XPathException {

            try (final MapType outputMap = new MapType(context)) {
                final AtomicValue outputKey;
                outputKey = options.baseOutputURI.orElseGet(() -> new StringValue("output"));

                final Sequence primaryValue = postProcess(outputKey, primaryDelivery.convert(), options.postProcess);
                outputMap.add(outputKey, primaryValue);

                for (final Map.Entry<URI, Delivery> resultDocument : resultDocuments.entrySet()) {
                    final AnyURIValue key = new AnyURIValue(resultDocument.getKey());
                    final Delivery secondaryDelivery = resultDocument.getValue();
                    final Sequence value = postProcess(key, secondaryDelivery.convert(), options.postProcess);
                    outputMap.add(key, value);
                }

                return outputMap;
            }
        }
    }

     private Sequence postProcess(final AtomicValue key, final Sequence before, final Optional<FunctionReference> postProcessingFunction) throws XPathException {
        if (postProcessingFunction.isPresent()) {
            FunctionReference functionReference = postProcessingFunction.get();
            return functionReference.evalFunction(null, null, new Sequence[]{key, before});
        } else {
            return before;
        }
    }

    private static Optional<Source> getSourceNode(final Optional<NodeValue> sourceNode, final AnyURIValue baseURI) {
        return sourceNode.map(NodeValue::getNode).map(node -> new DOMSource(node, baseURI.getStringValue()));
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

    private static class SingleRequestErrorListener implements ErrorListener {

        private Optional<TransformerException> lastError;
        private Optional<TransformerException> lastFatal;

        public Optional<TransformerException> getWorst() {
            if (lastFatal.isPresent()) return lastFatal;
            return lastError;
        }

        private final ErrorListener global;
        SingleRequestErrorListener(ErrorListener global) {
            this.global = global;
        }

        @Override
        public void warning(TransformerException exception) throws TransformerException {
            global.warning(exception);
        }

        @Override
        public void error(TransformerException exception) throws TransformerException {
            lastError = Optional.of(exception);
            global.error(exception);
        }

        @Override
        public void fatalError(TransformerException exception) throws TransformerException {
            lastFatal = Optional.of(exception);
            global.fatalError(exception);
        }
    }

    /**
     * A convenience for throwing a checked exception within fn:transform support code,
     * without the {@link XQueryContext} necessary for an immediate XPathException.
     *
     * Useful in a static helper class, for instance.
     */
    static class PendingException extends Exception {

        public PendingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
