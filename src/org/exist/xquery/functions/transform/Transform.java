/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2011 The eXist Project
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
package org.exist.xquery.functions.transform;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.dom.memtree.DocumentBuilderReceiver;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.NodeProxy;
import org.exist.http.servlets.ResponseWrapper;
import org.exist.numbering.NodeId;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.serializers.XIncludeFilter;
import org.exist.util.serializer.Receiver;
import org.exist.util.serializer.ReceiverToSAX;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.functions.response.ResponseModule;
import org.exist.xquery.value.*;
import org.exist.xslt.TransformerFactoryAllocator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.transform.*;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TemplatesHandler;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class Transform extends BasicFunction {

    public final static FunctionSignature signatures[] = {
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
                            "<parameters><param name=\"param-name1\" value=\"param-value1\"/>" +
                            "</parameters>. There are two special parameters named \"exist:stop-on-warn\" and " +
                            "\"exist:stop-on-error\". If set to value \"yes\", eXist will generate an XQuery error " +
                            "if the XSL processor reports a warning or error. The fourth argument specifies serialization " +
                            "options in the same way as if they " +
                            "were passed to \"declare option exist:serialize\" expression. An additional serialization option, " +
                            "xinclude-path, is supported, which specifies a base path against which xincludes will be expanded " +
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
                    new SequenceType(Type.ITEM, Cardinality.EMPTY)),
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
                    new SequenceType(Type.ITEM, Cardinality.EMPTY))
    };

    private static final Logger logger = LogManager.getLogger(Transform.class);
    private final Map<String, CachedStylesheet> cache = new HashMap<>();
    private boolean caching = true;

    private boolean stopOnError = true;
    private boolean stopOnWarn = false;

    /**
     * @param context
     * @param signature
     */
    public Transform(XQueryContext context, FunctionSignature signature) {
        super(context, signature);

        final Object property = context.getBroker().getConfiguration().getProperty(TransformerFactoryAllocator.PROPERTY_CACHING_ATTRIBUTE);
        if (property != null) {
            caching = (Boolean) property;
        }
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


        // Setup handler and error listener
        final TransformerHandler handler = createHandler(stylesheetItem, stylesheetParams, attributes);
        final TransformErrorListener errorListener = new TransformErrorListener();
        handler.getTransformer().setErrorListener(errorListener);


        if (isCalledAs("transform")) {
            //transform:transform()

            final Transformer transformer = handler.getTransformer();
            if ("org.exist.xslt.TransformerImpl".equals(transformer.getClass().getName())) {
                context.pushDocumentContext();

                final Sequence seq = ((org.exist.xslt.Transformer) transformer).transform(args[0]);

                context.popDocumentContext();
                return seq;

            } else {

                final ValueSequence seq = new ValueSequence();
                context.pushDocumentContext();

                final MemTreeBuilder builder = context.getDocumentBuilder();
                final DocumentBuilderReceiver builderReceiver = new DocumentBuilderReceiver(builder, true);
                final SAXResult result = new SAXResult(builderReceiver);
                result.setLexicalHandler(builderReceiver);        //preserve comments etc... from xslt output
                handler.setResult(result);
                final Receiver receiver = new ReceiverToSAX(handler);
                final Serializer serializer = context.getBroker().getSerializer();
                serializer.reset();

                try {
                    serializer.setProperties(serializationProps);
                    serializer.setReceiver(receiver, true);
                    if (expandXIncludes) {
                        String xiPath = serializationProps.getProperty(EXistOutputKeys.XINCLUDE_PATH);
                        if (xiPath != null) {
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
                }

                errorListener.checkForErrors();
                Node next = builder.getDocument().getFirstChild();
                while (next != null) {
                    seq.add((NodeValue) next);
                    next = next.getNextSibling();
                }

                context.popDocumentContext();
                return seq;
            }

        } else {
            //transform:stream-transform()

            final ResponseModule myModule = (ResponseModule) context.getModule(ResponseModule.NAMESPACE_URI);
            // response object is read from global variable $response
            final Variable respVar = myModule.resolveVariable(ResponseModule.RESPONSE_VAR);

            if (respVar == null) {
                throw new XPathException(this, ErrorCodes.XPDY0002, "No response object found in the current XQuery context.");
            }

            if (respVar.getValue().getItemType() != Type.JAVA_OBJECT) {
                throw new XPathException(this, ErrorCodes.XPDY0002, "Variable $response is not bound to an Java object.");
            }

            final JavaObjectValue respValue = (JavaObjectValue) respVar.getValue().itemAt(0);
            if (!"org.exist.http.servlets.HttpResponseWrapper".equals(respValue.getObject().getClass().getName())) {
                throw new XPathException(this, ErrorCodes.XPDY0002, signatures[1] +
                        " can only be used within the EXistServlet or XQueryServlet");
            }

            final ResponseWrapper response = (ResponseWrapper) respValue.getObject();

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
                final Serializer serializer = context.getBroker().getSerializer();
                serializer.reset();
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
    private TransformerHandler createHandler(Item stylesheetItem, Properties options, Properties attributes) throws TransformerFactoryConfigurationError, XPathException {
        final SAXTransformerFactory factory = TransformerFactoryAllocator.getTransformerFactory(context.getBroker().getBrokerPool());

        //set any attributes
        for (final Map.Entry<Object, Object> attribute : attributes.entrySet()) {
            factory.setAttribute((String) attribute.getKey(), attribute.getValue());
        }

        TransformerHandler handler;
        try {
            Templates templates = null;
            if (Type.subTypeOf(stylesheetItem.getType(), Type.NODE)) {
                final NodeValue stylesheetNode = (NodeValue) stylesheetItem;
                // if the passed node is a document node or document root element,
                // we construct an XMLDB URI and use the caching implementation.
                if (stylesheetNode.getImplementationType() == NodeValue.PERSISTENT_NODE) {
                    final NodeProxy root = (NodeProxy) stylesheetNode;
                    if (root.getNodeId() == NodeId.DOCUMENT_NODE || root.getNodeId().getTreeLevel() == 1) {
                        //as this is a persistent node (e.g. a stylesheet stored in the db)
                        //set the URI Resolver as a DatabaseResolver
                        factory.setURIResolver(new EXistURIResolver(context.getBroker(), root.getOwnerDocument().getCollection().getURI().toString()));

                        final String uri = XmldbURI.XMLDB_URI_PREFIX + context.getBroker().getBrokerPool().getId() + "://" + root.getOwnerDocument().getURI();
                        templates = getSource(factory, uri);
                    }
                }
                if (templates == null) {
                    if (stylesheetItem instanceof Document) {
                        String uri = ((Document) stylesheetItem).getDocumentURI();

						/*
                         * This must be checked because in the event the stylesheet is
						 * an in-memory document, it will cause an NPE
						 */
                        if (uri != null) {
                            uri = uri.substring(0, uri.lastIndexOf('/'));
                            factory.setURIResolver(new EXistURIResolver(context.getBroker(), uri));
                        }
                    }
                    templates = getSource(factory, stylesheetNode);
                }
            } else {
                if (stylesheetItem instanceof Document) {
                    String uri = ((Document) stylesheetItem).getDocumentURI();

					/*
					 * This must be checked because in the event the stylesheet is 
					 * an in-memory document, it will cause an NPE
					 */
                    if (uri != null) {
                        uri = uri.substring(0, uri.lastIndexOf('/'));
                        factory.setURIResolver(new EXistURIResolver(context.getBroker(), uri));
                    }
                }

                final String stylesheet = stylesheetItem.getStringValue();
                templates = getSource(factory, stylesheet);
            }
            handler = factory.newTransformerHandler(templates);

            if (options != null) {
                setParameters(options, handler.getTransformer());
            }

        } catch (final TransformerConfigurationException e) {
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
                logger.info("Setting serialization property: " + pair[0] + " = " + pair[1]);
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

    private Templates getSource(SAXTransformerFactory factory, String stylesheet)
            throws XPathException, TransformerConfigurationException {
        String base;
        if (stylesheet.indexOf(':') == Constants.STRING_NOT_FOUND) {
            Path f = Paths.get(stylesheet).normalize();
            if (Files.isReadable(f)) {
                stylesheet = f.toUri().toASCIIString();
            } else {
                stylesheet = context.getModuleLoadPath() + File.separatorChar + stylesheet;
                f = Paths.get(stylesheet).normalize();
                if (Files.isReadable(f)) {
                    stylesheet = f.toUri().toASCIIString();
                }
            }
        }

        //TODO : use dedicated function in XmldbURI
        final int p = stylesheet.lastIndexOf("/");
        if (p != Constants.STRING_NOT_FOUND) {
            base = stylesheet.substring(0, p);
        } else {
            base = stylesheet;
        }

        CachedStylesheet cached = cache.get(stylesheet);
        try {
            if (cached == null) {
                cached = new CachedStylesheet(factory, stylesheet, base);
                cache.put(stylesheet, cached);
            }
            return cached.getTemplates();

        } catch (final MalformedURLException e) {
            LOG.debug(e.getMessage(), e);
            throw new XPathException(this, "Malformed URL for stylesheet: " + stylesheet, e);

        } catch (final IOException e) {
            throw new XPathException(this, "IO error while loading stylesheet: " + stylesheet, e);
        }
    }

    private Templates getSource(SAXTransformerFactory factory, NodeValue stylesheetRoot) throws XPathException, TransformerConfigurationException {
        final TemplatesHandler handler = factory.newTemplatesHandler();
        try {
            handler.startDocument();
            stylesheetRoot.toSAX(context.getBroker(), handler, null);
            handler.endDocument();
            return handler.getTemplates();

        } catch (final SAXException e) {
            throw new XPathException(this,
                    "A SAX exception occurred while compiling the stylesheet: " + e.getMessage(), e);
        }
    }

    public static class ExternalResolver implements URIResolver {
        private final String baseURI;

        public ExternalResolver(final String base) {
            this.baseURI = base;
        }

        @Override
        public Source resolve(final String href, final String base) throws TransformerException {
            try {
                //TODO : use dedicated function in XmldbURI
                final URL url = new URL(baseURI + "/" + href);
                final URLConnection connection = url.openConnection();
                return new StreamSource(connection.getInputStream());
            } catch (final IOException e) {
                LOG.warn(e);
                return null;
            }
        }
    }

    //TODO: revisit this class with XmldbURI in mind
    private class CachedStylesheet {

        SAXTransformerFactory factory;
        long lastModified = -1;
        Templates templates = null;
        String uri;

        public CachedStylesheet(SAXTransformerFactory factory, String uri, String baseURI)
                throws TransformerConfigurationException, IOException, XPathException {
            this.factory = factory;
            this.uri = uri;
            if (!baseURI.startsWith(XmldbURI.EMBEDDED_SERVER_URI_PREFIX)) {
                factory.setURIResolver(new ExternalResolver(baseURI));
            }
            getTemplates();
        }

        public Templates getTemplates() throws TransformerConfigurationException, IOException, XPathException {
            if (uri.startsWith(XmldbURI.EMBEDDED_SERVER_URI_PREFIX)) {
                final String docPath = uri.substring(XmldbURI.EMBEDDED_SERVER_URI_PREFIX.length());
                DocumentImpl doc = null;
                try {
                    doc = context.getBroker().getXMLResource(XmldbURI.create(docPath), LockMode.READ_LOCK);
                    if (!caching || (doc != null && (templates == null || doc.getMetadata().getLastModified() > lastModified))) {
                        templates = getSource(doc);
                    }
                    lastModified = doc.getMetadata().getLastModified();
                } catch (final PermissionDeniedException e) {
                    throw new XPathException(Transform.this, "Permission denied to read stylesheet: " + uri);
                } finally {
                    if (doc != null) {
                        doc.getUpdateLock().release(LockMode.READ_LOCK);
                    }
                }

            } else {
                final URL url = new URL(uri);
                final URLConnection connection = url.openConnection();
                long modified = connection.getLastModified();
                if (!caching || (templates == null || modified > lastModified || modified == 0)) {
                    LOG.debug("compiling stylesheet " + url);
                    try (final InputStream is = connection.getInputStream()) {
                        templates = factory.newTemplates(new StreamSource(is));
                    }
                }
                lastModified = modified;
            }
            return templates;
        }

        private Templates getSource(DocumentImpl stylesheet)
                throws XPathException, TransformerConfigurationException {
            factory.setURIResolver(new EXistURIResolver(context.getBroker(), stylesheet.getCollection().getURI().toString()));
            final TransformErrorListener errorListener = new TransformErrorListener();
            factory.setErrorListener(errorListener);
            final TemplatesHandler handler = factory.newTemplatesHandler();

            try {
                handler.startDocument();
                final Serializer serializer = context.getBroker().getSerializer();
                serializer.reset();
                serializer.setSAXHandlers(handler, null);
                serializer.toSAX(stylesheet);
                handler.endDocument();
                final Templates t = handler.getTemplates();
                errorListener.checkForErrors();
                return t;

            } catch (final Exception e) {
                if (e instanceof XPathException) {
                    throw (XPathException) e;
                }
                throw new XPathException(Transform.this,
                        "An exception occurred while compiling the stylesheet: " + stylesheet.getURI() +
                                ": " + e.getMessage(), e);
            }
        }
    }

    private class TransformErrorListener implements ErrorListener {

        private final static int NO_ERROR = 0;
        private final static int WARNING = 1;
        private final static int ERROR = 2;
        private final static int FATAL = 3;

        private int errorCode = NO_ERROR;
        private Exception exception;

        protected void checkForErrors() throws XPathException {
            switch (errorCode) {
                case WARNING:
                    if (stopOnWarn) {
                        throw new XPathException("XSL transform reported warning: " + exception.getMessage(),
                                exception);
                    }
                    break;
                case ERROR:
                    if (stopOnError) {
                        throw new XPathException("XSL transform reported error: " + exception.getMessage(), exception);
                    }
                    break;
                case FATAL:
                    throw new XPathException("XSL transform reported error: " + exception.getMessage(), exception);
            }
        }

        public void warning(TransformerException except) throws TransformerException {
            LOG.warn("XSL transform reports warning: " + except.getMessage(), except);
            errorCode = WARNING;
            exception = except;
            if (stopOnWarn) {
                throw except;
            }
        }

        public void error(TransformerException except) throws TransformerException {
            LOG.warn("XSL transform reports recoverable error: " + except.getMessage(), except);
            errorCode = ERROR;
            exception = except;
            if (stopOnError) {
                throw except;
            }
        }

        public void fatalError(TransformerException except) throws TransformerException {
            LOG.warn("XSL transform reports fatal error: " + except.getMessage(), except);
            errorCode = FATAL;
            exception = except;
            throw except;
        }
    }
}