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
package org.exist.xquery.functions.transform;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.dom.memtree.DocumentBuilderReceiver;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.dom.persistent.NodeProxy;
import org.exist.http.servlets.ResponseWrapper;
import org.exist.numbering.NodeId;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.serializers.XIncludeFilter;
import org.exist.util.serializer.Receiver;
import org.exist.util.serializer.ReceiverToSAX;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.value.*;
import org.exist.xslt.Stylesheet;
import org.exist.xslt.TemplatesFactory;
import org.exist.xslt.TransformerFactoryAllocator;
import org.exist.xslt.XSLTErrorsListener;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.transform.*;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Properties;

/**
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public class Transform extends BasicFunction {

    public final static FunctionSignature[] signatures = {
            new FunctionSignature(
                    new QName("transform", TransformModule.NAMESPACE_URI, TransformModule.PREFIX),
                    "Applies an XSL stylesheet to the node tree passed as first argument. The stylesheet " +
                            "is specified in the second argument. This should either be an URI or a node. If it is an " +
                            "URI, it can either point to an external location or to an XSL stored in the db by using the " +
                            "'xmldb:' scheme. Stylesheets are cached unless they were just created from an XML " +
                            "fragment and not from a complete document. " +
                            "Stylesheet parameters " +
                            "may be passed in the third argument using an XML fragment with the following structure: " +
                            "<parameters><param name=\"param-name1\" value=\"param-value1\"/>" +
                            "</parameters>. There are two special parameters named \"exist:stop-on-warn\" and " +
                            "\"exist:stop-on-error\". If set to value \"yes\", eXist will generate an XQuery error " +
                            "if the XSL processor reports a warning or error.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("node-tree", Type.NODE, Cardinality.ZERO_OR_MORE, "The source-document (node tree)"),
                            new FunctionParameterSequenceType("stylesheet", Type.ITEM, Cardinality.EXACTLY_ONE, "The XSL stylesheet"),
                            new FunctionParameterSequenceType("parameters", Type.NODE, Cardinality.ZERO_OR_ONE, "The transformer parameters")
                    },
                    new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_ONE, "the transformed result (node tree)")),
            new FunctionSignature(
                    new QName("transform", TransformModule.NAMESPACE_URI, TransformModule.PREFIX),
                    "Applies an XSL stylesheet to the node tree passed as first argument. The stylesheet " +
                            "is specified in the second argument. This should either be an URI or a node. If it is an " +
                            "URI, it can either point to an external location or to an XSL stored in the db by using the " +
                            "'xmldb:' scheme. Stylesheets are cached unless they were just created from an XML " +
                            "fragment and not from a complete document. " +
                            "Stylesheet parameters " +
                            "may be passed in the third argument using an XML fragment with the following structure: " +
                            "<parameters><param name=\"param-name\" value=\"param-value\"/>" +
                            "</parameters>. There are two special parameters named \"exist:stop-on-warn\" and " +
                            "\"exist:stop-on-error\". If set to value \"yes\", eXist will generate an XQuery error " +
                            "if the XSL processor reports a warning or error. " +
                            "The fourth argument specifies attributes to be set on the used Java TransformerFactory with the following structure: " +
                            "<attributes><attr name=\"attr-name\" value=\"attr-value\"/></attributes>.  " +
                            "The fifth argument specifies serialization " +
                            "options in the same way as if they " +
                            "were passed to \"declare option exist:serialize\" expression. An additional serialization option, " +
                            "\"xinclude-path\", is supported, which specifies a base path against which xincludes will be expanded " +
                            "(if there are xincludes in the document). A relative path will be relative to the current " +
                            "module load path.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("node-tree", Type.NODE, Cardinality.ZERO_OR_MORE, "The source-document (node tree)"),
                            new FunctionParameterSequenceType("stylesheet", Type.ITEM, Cardinality.EXACTLY_ONE, "The XSL stylesheet"),
                            new FunctionParameterSequenceType("parameters", Type.NODE, Cardinality.ZERO_OR_ONE, "The transformer parameters"),
                            new FunctionParameterSequenceType("attributes", Type.NODE, Cardinality.ZERO_OR_ONE, "Attributes to pass to the transformation factory"),
                            new FunctionParameterSequenceType("serialization-options", Type.STRING, Cardinality.ZERO_OR_ONE, "The serialization options")},
                    new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_ONE, "the transformed result (node tree)")),
            new FunctionSignature(
                    new QName("stream-transform", TransformModule.NAMESPACE_URI, TransformModule.PREFIX),
                    "Applies an XSL stylesheet to the node tree passed as first argument. The parameters are the same " +
                            "as for the transform function. stream-transform can only be used within a servlet context. Instead " +
                            "of returning the transformed document fragment, it directly streams its output to the servlet's output stream. " +
                            "It should thus be the last statement in the XQuery.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("node-tree", Type.NODE, Cardinality.ZERO_OR_MORE, "The source-document (node tree)"),
                            new FunctionParameterSequenceType("stylesheet", Type.ITEM, Cardinality.EXACTLY_ONE, "The XSL stylesheet"),
                            new FunctionParameterSequenceType("parameters", Type.NODE, Cardinality.ZERO_OR_ONE, "The transformer parameters")
                    },
                    new SequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE)),
            new FunctionSignature(
                    new QName("stream-transform", TransformModule.NAMESPACE_URI, TransformModule.PREFIX),
                    "Applies an XSL stylesheet to the node tree passed as first argument. The parameters are the same " +
                            "as for the transform function. stream-transform can only be used within a servlet context. Instead " +
                            "of returning the transformed document fragment, it directly streams its output to the servlet's output stream. " +
                            "It should thus be the last statement in the XQuery.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("node-tree", Type.NODE, Cardinality.ZERO_OR_MORE, "The source-document (node tree)"),
                            new FunctionParameterSequenceType("stylesheet", Type.ITEM, Cardinality.EXACTLY_ONE, "The XSL stylesheet"),
                            new FunctionParameterSequenceType("parameters", Type.NODE, Cardinality.ZERO_OR_ONE, "The transformer parameters"),
                            new FunctionParameterSequenceType("attributes", Type.NODE, Cardinality.ZERO_OR_ONE, "Attributes to pass to the transformation factory"),
                            new FunctionParameterSequenceType("serialization-options", Type.STRING, Cardinality.ZERO_OR_ONE, "The serialization options")},
                    new SequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE))
    };

    private static final Logger logger = LogManager.getLogger(Transform.class);

    private boolean stopOnError = true;
    private boolean stopOnWarn = false;

    public Transform(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
     */
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {

        final Properties attributes = new Properties();
        final Properties serializationProps = new Properties();
        final Properties stylesheetParams = new Properties();

        // Parameter 1 & 2
        final Sequence inputNode = args[0];
        final Item stylesheetItem = args[1].itemAt(0);

        // Parse 3rd parameter
        final Node options = args[2].isEmpty() ? null : ((NodeValue) args[2].itemAt(0)).getNode();
        if (options != null) {
            stylesheetParams.putAll(parseParameters(options));
        }

        // Parameter 4 when present
        if (getArgumentCount() >= 4) {
            final Sequence attrs = args[3];
            attributes.putAll(extractAttributes(attrs));
        }

        // Parameter 5 when present
        if (getArgumentCount() >= 5) {
            //extract serialization options
            final Sequence serOpts = args[4];
            serializationProps.putAll(extractSerializationProperties(serOpts));

        } else {
            context.checkOptions(serializationProps);
        }

        boolean expandXIncludes =
                "yes".equals(serializationProps.getProperty(EXistOutputKeys.EXPAND_XINCLUDES, "yes"));


        final XSLTErrorsListener<XPathException> errorListener =
            new XSLTErrorsListener<XPathException>(stopOnError, stopOnWarn) {
                @Override
                protected void raiseError(final String error, final TransformerException ex) throws XPathException {
                    throw new XPathException(Transform.this, error, ex);
                }
            };

        // Setup handler and error listener
        final TransformerHandler handler = createHandler(stylesheetItem, stylesheetParams, attributes, errorListener);


        if (isCalledAs("transform")) {
            //transform:transform()

            final ValueSequence seq = new ValueSequence();
            context.pushDocumentContext();

            try {
                final MemTreeBuilder builder = context.getDocumentBuilder();
                final DocumentBuilderReceiver builderReceiver = new DocumentBuilderReceiver(this, builder, true);
                final SAXResult result = new SAXResult(builderReceiver);
                result.setLexicalHandler(builderReceiver);        //preserve comments etc... from xslt output
                handler.setResult(result);
                final Receiver receiver = new ReceiverToSAX(handler);
                final Serializer serializer = context.getBroker().borrowSerializer();
                try {
                    serializer.setProperties(serializationProps);
                    serializer.setReceiver(receiver, true);
                    if (expandXIncludes) {
                        String xiPath = serializationProps.getProperty(EXistOutputKeys.XINCLUDE_PATH);
                        if (xiPath != null && !xiPath.startsWith(XmldbURI.XMLDB_URI_PREFIX)) {
                            final Path f = Paths.get(xiPath).normalize();
                            if (!f.isAbsolute()) {
                                xiPath = Paths.get(context.getModuleLoadPath(), xiPath).normalize().toAbsolutePath().toString();
                            }
                        } else {
                            xiPath = context.getModuleLoadPath();
                        }
                        serializer.getXIncludeFilter().setModuleLoadPath(xiPath);
                    }
                    serializer.toSAX(inputNode, 1, inputNode.getItemCount(), false, false, 0, 0);

                } catch (final Exception e) {
                    throw new XPathException(this, "Exception while transforming node: " + e.getMessage(), e);
                } finally {
                    context.getBroker().returnSerializer(serializer);
                }

                errorListener.checkForErrors();
                Node next = builder.getDocument().getFirstChild();
                while (next != null) {
                    seq.add((NodeValue) next);
                    next = next.getNextSibling();
                }

                return seq;
            } finally {
                context.popDocumentContext();
            }

        } else {
            //transform:stream-transform()

            final Optional<ResponseWrapper> maybeResponse = Optional.ofNullable(context.getHttpContext())
                    .map(XQueryContext.HttpContext::getResponse);

            if (!maybeResponse.isPresent()) {
                throw new XPathException(this, ErrorCodes.XPDY0002, "No response object found in the current XQuery context.");
            }

            final ResponseWrapper response =  maybeResponse.get();
            if (!"org.exist.http.servlets.HttpResponseWrapper".equals(response.getClass().getName())) {
                throw new XPathException(this, ErrorCodes.XPDY0002, signatures[1] +
                        " can only be used within the EXistServlet or XQueryServlet");
            }

            //setup the response correctly
            final String mediaType = handler.getTransformer().getOutputProperty("media-type");
            final String encoding = handler.getTransformer().getOutputProperty("encoding");
            if (mediaType != null) {
                if (encoding == null) {
                    response.setContentType(mediaType);
                } else {
                    response.setContentType(mediaType + "; charset=" + encoding);
                }
            }

            //do the transformation
            try {
                final OutputStream os = new BufferedOutputStream(response.getOutputStream());
                final StreamResult result = new StreamResult(os);
                handler.setResult(result);
                final Serializer serializer = context.getBroker().borrowSerializer();
                Receiver receiver = new ReceiverToSAX(handler);

                try {
                    serializer.setProperties(serializationProps);
                    if (expandXIncludes) {
                        XIncludeFilter xinclude = new XIncludeFilter(serializer, receiver);
                        String xiPath = serializationProps.getProperty(EXistOutputKeys.XINCLUDE_PATH);
                        if (xiPath != null) {
                            final Path f = Paths.get(xiPath).normalize();
                            if (!f.isAbsolute()) {
                                xiPath = Paths.get(context.getModuleLoadPath(), xiPath).normalize().toAbsolutePath().toString();
                            }

                        } else {
                            xiPath = context.getModuleLoadPath();
                        }

                        xinclude.setModuleLoadPath(xiPath);
                        receiver = xinclude;
                    }
                    serializer.setReceiver(receiver);
                    serializer.toSAX(inputNode);

                } catch (final Exception e) {
                    throw new XPathException(this, "Exception while transforming node: " + e.getMessage(), e);
                } finally {
                    context.getBroker().returnSerializer(serializer);
                }

                errorListener.checkForErrors();
                os.close();

                //commit the response
                response.flushBuffer();

            } catch (final IOException e) {
                throw new XPathException(this, "IO exception while transforming node: " + e.getMessage(), e);
            }
            return Sequence.EMPTY_SEQUENCE;
        }
    }

    /**
     * @param stylesheetItem
     * @param options
     * @param attributes     Attributes to set on the Transformer Factory
     * @throws TransformerFactoryConfigurationError
     * @throws XPathException
     */
    private TransformerHandler createHandler(
        Item stylesheetItem,
        Properties options,
        Properties attributes,
        XSLTErrorsListener<XPathException> errorListener
    )
        throws TransformerFactoryConfigurationError, XPathException
    {

        boolean useCache = true;
        final Object property = context.getBroker().getConfiguration().getProperty(TransformerFactoryAllocator.PROPERTY_CACHING_ATTRIBUTE);
        if (property != null) {
            useCache = (Boolean) property;
        }

        TransformerHandler handler;
        try {
            Stylesheet stylesheet = null;
            if (Type.subTypeOf(stylesheetItem.getType(), Type.NODE)) {
                final NodeValue stylesheetNode = (NodeValue) stylesheetItem;
                // if the passed node is a document node or document root element,
                // we construct an XMLDB URI and use the caching implementation.
                if (stylesheetNode.getImplementationType() == NodeValue.PERSISTENT_NODE) {
                    final NodeProxy root = (NodeProxy) stylesheetNode;
                    if (root.getNodeId() == NodeId.DOCUMENT_NODE || root.getNodeId().getTreeLevel() == 1) {

                        final String uri = XmldbURI.XMLDB_URI_PREFIX + context.getBroker().getBrokerPool().getId() + "://" + root.getOwnerDocument().getURI();

                        stylesheet = TemplatesFactory.stylesheet(uri, context.getModuleLoadPath(), attributes, useCache);
                    }
                }
                if (stylesheet == null) {
                    stylesheet = TemplatesFactory.stylesheet(
                        getContext().getBroker(),
                        stylesheetNode,
                        context.getModuleLoadPath()
                    );
                }
            } else {
                String baseUri = context.getModuleLoadPath();
                if (stylesheetItem instanceof Document) {
                    baseUri = ((Document) stylesheetItem).getDocumentURI();

                    /*
                     * This must be checked because in the event the stylesheet is
                     * an in-memory document, it will cause an NPE
                     */
                    if (baseUri == null) {
                        baseUri = context.getModuleLoadPath();
                    } else {
                        baseUri = baseUri.substring(0, baseUri.lastIndexOf('/'));
                    }
                }

                final String uri = stylesheetItem.getStringValue();

                stylesheet = TemplatesFactory.stylesheet(uri, baseUri, attributes, useCache);
            }

            handler = stylesheet.newTransformerHandler(getContext().getBroker(), errorListener);

            if (options != null) {
                setParameters(options, handler.getTransformer());
            }

        } catch (final Exception e) {
            if (e instanceof XPathException) {
                throw (XPathException) e;
            }
            throw new XPathException(this, "Unable to set up transformer: " + e.getMessage(), e);
        }
        return handler;
    }

    private Properties extractSerializationProperties(final Sequence serOpts) throws XPathException {
        final Properties serializationProps = new Properties();
        if (!serOpts.isEmpty()) {
            final String[] contents = Option.tokenize(serOpts.getStringValue());
            for (String content : contents) {
                final String[] pair = Option.parseKeyValuePair(content);
                if (pair == null) {
                    throw new XPathException(this, "Found invalid serialization option: " + content);
                }
                logger.info("Setting serialization property: {} = {}", pair[0], pair[1]);
                serializationProps.setProperty(pair[0], pair[1]);
            }
        }
        return serializationProps;
    }

    private Properties extractAttributes(final Sequence attrs) throws XPathException {
        if (attrs.isEmpty()) {
            return new Properties();
        } else {
            return parseElementParam(((NodeValue) attrs.itemAt(0)).getNode(), "attributes", "attr");
        }
    }

    private Properties parseParameters(final Node options) throws XPathException {
        return parseElementParam(options, "parameters", "param");
    }

    private Properties parseElementParam(final Node elementParam, final String container, final String param) throws XPathException {
        final Properties props = new Properties();
        if (elementParam.getNodeType() == Node.ELEMENT_NODE && elementParam.getLocalName().equals(container)) {
            Node child = elementParam.getFirstChild();
            while (child != null) {
                if (child.getNodeType() == Node.ELEMENT_NODE && child.getLocalName().equals(param)) {
                    final Element elem = (Element) child;
                    final String name = elem.getAttribute("name");
                    final String value = elem.getAttribute("value");
                    if (name == null || value == null) {
                        throw new XPathException(this, "Name or value attribute missing");
                    }

                    if ("exist:stop-on-warn".equals(name)) {
                        stopOnWarn = "yes".equals(value);
                    } else if ("exist:stop-on-error".equals(name)) {
                        stopOnError = "yes".equals(value);
                    } else {
                        props.setProperty(name, value);
                    }
                }
                child = child.getNextSibling();
            }
        }
        return props;
    }

    private void setParameters(Properties parameters, Transformer handler) {
        for (Object o : parameters.keySet()) {
            final String key = (String) o;
            handler.setParameter(key, parameters.getProperty(key));
        }
    }
}
