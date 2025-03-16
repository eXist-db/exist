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
package org.exist.storage.serializers;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import javax.annotation.Nullable;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TemplatesHandler;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import com.evolvedbinary.j8fu.lazy.LazyVal;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.Namespaces;
import org.exist.dom.memtree.NodeImpl;
import org.exist.dom.memtree.ReferenceNode;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.QName;
import org.exist.dom.persistent.XMLUtil;
import org.exist.indexing.IndexController;
import org.exist.indexing.MatchListener;
import org.exist.numbering.NodeId;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.storage.DBBroker;
import org.exist.util.Configuration;
import org.exist.util.MimeType;
import org.exist.util.serializer.AttrList;
import org.exist.util.serializer.Receiver;
import org.exist.util.serializer.ReceiverToSAX;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.Constants;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;
import org.exist.xslt.TransformerFactoryAllocator;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ProcessingInstruction;
import org.xml.sax.*;
import org.xml.sax.ext.LexicalHandler;

/**
 * Serializer base class, used to serialize a document or document fragment
 * back to XML. A serializer may be obtained by calling DBBroker.getSerializer().
 * <p>
 * The class basically offers two overloaded methods: serialize()
 * and toSAX(). serialize() returns the XML as a string, while
 * toSAX() generates a stream of SAX events. The stream of SAX
 * events is passed to the ContentHandler set by setContentHandler().
 * <p>
 * Internally, both types of methods pass events to a {@link org.exist.util.serializer.Receiver}.
 * Subclasses thus have to implement the various serializeToReceiver() methods.
 * <p>
 * Output can be configured through properties. Property keys are defined in classes
 * {@link javax.xml.transform.OutputKeys} and {@link org.exist.storage.serializers.EXistOutputKeys}
 *
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public abstract class Serializer implements XMLReader {

    protected static final Logger LOG = LogManager.getLogger(Serializer.class);

    public static final String CONFIGURATION_ELEMENT_NAME = "serializer";
    public static final String OMIT_XML_DECLARATION_ATTRIBUTE = "omit-xml-declaration";
    public static final String PROPERTY_OMIT_XML_DECLARATION = "serialization.omit-xml-declaration";
    public static final String OMIT_ORIGINAL_XML_DECLARATION_ATTRIBUTE = "omit-original-xml-declaration";
    public static final String PROPERTY_OMIT_ORIGINAL_XML_DECLARATION = "serialization.omit-original-xml-declaration";
    public static final String OUTPUT_DOCTYPE_ATTRIBUTE = "output-doctype";
    public static final String PROPERTY_OUTPUT_DOCTYPE = "serialization.output-doctype";
    public static final String ENABLE_XINCLUDE_ATTRIBUTE = "enable-xinclude";
    public static final String PROPERTY_ENABLE_XINCLUDE = "serialization.enable-xinclude";
    public static final String ENABLE_XSL_ATTRIBUTE = "enable-xsl";
    public static final String PROPERTY_ENABLE_XSL = "serialization.enable-xsl";
    public static final String INDENT_ATTRIBUTE = "indent";
    public static final String PROPERTY_INDENT = "serialization.indent";
    public static final String COMPRESS_OUTPUT_ATTRIBUTE = "compress-output";
    public static final String PROPERTY_COMPRESS_OUTPUT = "serialization.compress-output";
    public static final String ADD_EXIST_ID_ATTRIBUTE = "add-exist-id";
    public static final String PROPERTY_ADD_EXIST_ID = "serialization.add-exist-id";
    public static final String TAG_MATCHING_ELEMENTS_ATTRIBUTE = "match-tagging-elements";
    public static final String PROPERTY_TAG_MATCHING_ELEMENTS = "serialization.match-tagging-elements";
    public static final String TAG_MATCHING_ATTRIBUTES_ATTRIBUTE = "match-tagging-attributes";
    public static final String PROPERTY_TAG_MATCHING_ATTRIBUTES = "serialization.match-tagging-attributes";
    public static final String PROPERTY_SESSION_ID = "serialization.session-id";

    // constants to configure the highlighting of matches in text and attributes
    public static final int TAG_NONE = 0x0;
    public static final int TAG_ELEMENT_MATCHES = 0x1;
    public static final int TAG_ATTRIBUTE_MATCHES = 0x2;
    public static final int TAG_BOTH = 0x3;

    public static final int EXIST_ID_NONE = 0;
    public static final int EXIST_ID_ELEMENT = 1;
    public static final int EXIST_ID_ALL = 2;

    protected int showId = EXIST_ID_NONE;

    public static final String GENERATE_DOC_EVENTS = "sax-document-events";
    public static final String ENCODING = "encoding";

    private static final QName ATTR_HITS_QNAME = new QName("hits", Namespaces.EXIST_NS, "exist");
    private static final QName ATTR_START_QNAME = new QName("start", Namespaces.EXIST_NS, "exist");
    private static final QName ATTR_COUNT_QNAME = new QName("count", Namespaces.EXIST_NS, "exist");
    private static final QName ELEM_RESULT_QNAME = new QName("result", Namespaces.EXIST_NS, "exist");
    private static final QName ATTR_SESSION_ID = new QName("session", Namespaces.EXIST_NS, "exist");
    private static final QName ATTR_COMPILATION_TIME_QNAME = new QName("compilation-time", Namespaces.EXIST_NS, "exist");
    private static final QName ATTR_EXECUTION_TIME_QNAME = new QName("execution-time", Namespaces.EXIST_NS, "exist");
    private static final QName ATTR_TYPE_QNAME = new QName("type", Namespaces.EXIST_NS, "exist");
    private static final QName ELEM_VALUE_QNAME = new QName("value", Namespaces.EXIST_NS, "exist");

    // required for XQJ/typed information implementation
    // -----------------------------------------
    private static final QName ELEM_DOC_QNAME = new QName("document", Namespaces.EXIST_NS, "exist");
    private static final QName ELEM_ATTR_QNAME = new QName("attribute", Namespaces.EXIST_NS, "exist");
    private static final QName ELEM_TEXT_QNAME = new QName("text", Namespaces.EXIST_NS, "exist");

    private static final QName ATTR_URI_QNAME = new QName("uri", Namespaces.EXIST_NS, "exist");
    private static final QName ATTR_TNS_QNAME = new QName("target-namespace", Namespaces.EXIST_NS, "exist");
    private static final QName ATTR_LOCAL_QNAME = new QName("local", Namespaces.EXIST_NS, "exist");
    private static final QName ATTR_PREFIX_QNAME = new QName("prefix", Namespaces.EXIST_NS, "exist");
    private static final QName ATTR_HAS_ELEMENT_QNAME = new QName("has-element", Namespaces.EXIST_NS, "exist");
    // -----------------------------------------


    protected final DBBroker broker;

    private @Nullable EntityResolver entityResolver = null;

    private @Nullable ErrorHandler errorHandler = null;

    private final LazyVal<SAXTransformerFactory> factory;
    protected boolean createContainerElements = false;

    private final Properties defaultOutputProperties = new Properties();
    protected final Properties outputProperties = new Properties();
    private @Nullable Templates templates = null;
    private @Nullable TransformerHandler xslHandler = null;
    private final XIncludeFilter xinclude;
    private final CustomMatchListenerFactory customMatchListeners;
    protected @Nullable Receiver receiver = null;
    private @Nullable LexicalHandler lexicalHandler = null;
    private @Nullable Int2ObjectMap<String> useCharacterMaps = null;
    private @Nullable Subject user = null;

    protected @Nullable XQueryContext.HttpContext httpContext = null;

    protected boolean documentStarted = false;

    public void setHttpContext(final XQueryContext.HttpContext httpContext) {
        this.httpContext = httpContext;
    }


    public Serializer(final DBBroker broker, final Configuration config) {
        this(broker, config, null);
    }

    public Serializer(final DBBroker broker, final Configuration config, final List<String> chainOfReceivers) {
        this.broker = broker;
        this.factory = new LazyVal<>(() -> TransformerFactoryAllocator.getTransformerFactory(broker.getBrokerPool()));

        this.xinclude = new XIncludeFilter(this);
        this.customMatchListeners = new CustomMatchListenerFactory(broker, config, chainOfReceivers);
        this.receiver = xinclude;

        defaultOutputProperties.setProperty(EXistOutputKeys.PROCESS_XSL_PI, config.getProperty(PROPERTY_ENABLE_XSL, "no"));

        @Nullable String option = null;

        if ((option = (String) config.getProperty(PROPERTY_ENABLE_XINCLUDE)) != null) {
            defaultOutputProperties.setProperty(EXistOutputKeys.EXPAND_XINCLUDES, option);
        }

        if ((option = (String) config.getProperty(PROPERTY_INDENT)) != null) {
            defaultOutputProperties.setProperty(OutputKeys.INDENT, option);
        }

        if ((option = (String) config.getProperty(PROPERTY_COMPRESS_OUTPUT)) != null) {
            defaultOutputProperties.setProperty(EXistOutputKeys.COMPRESS_OUTPUT, option);
        }

        if ((option = (String) config.getProperty(PROPERTY_ADD_EXIST_ID)) != null) {
            defaultOutputProperties.setProperty(EXistOutputKeys.ADD_EXIST_ID, option);
        }

        boolean tagElements = true, tagAttributes = false;
        if ((option = (String) config.getProperty(PROPERTY_TAG_MATCHING_ELEMENTS)) != null) {
            tagElements = "yes".equals(option);
        }
        if ((option = (String) config.getProperty(PROPERTY_TAG_MATCHING_ATTRIBUTES)) != null) {
            tagAttributes = "yes".equals(option);
        }
        if (tagElements && tagAttributes) {
            option = "both";
        } else if (tagElements) {
            option = "elements";
        } else if (tagAttributes) {
            option = "attributes";
        } else {
            option = "none";
        }
        defaultOutputProperties.setProperty(EXistOutputKeys.HIGHLIGHT_MATCHES, option);

        defaultOutputProperties.setProperty(GENERATE_DOC_EVENTS, "true");

        // NOTE(AR) copy the default output properties to output properties (which is mutable!)
        outputProperties.putAll(defaultOutputProperties);
    }

    public void setProperties(@Nullable Properties properties)
            throws SAXNotRecognizedException, SAXNotSupportedException {
        if (properties == null) {
            return;
        }

        for (final Enumeration<?> e = properties.propertyNames(); e.hasMoreElements(); ) {
            final String key = (String) e.nextElement();
            if (key.equals(Namespaces.SAX_LEXICAL_HANDLER)) {
                lexicalHandler = (LexicalHandler) properties.get(key);
            } else if (key.equals(EXistOutputKeys.USE_CHARACTER_MAPS)) {
                useCharacterMaps = (Int2ObjectMap<String>) properties.get(key);
            } else {
                setProperty(key, properties.getProperty(key));
            }
        }
    }

    public void setProperties(@Nullable final HashMap<String, Object> table)
            throws SAXNotRecognizedException, SAXNotSupportedException {
        if (table == null) {
            return;
        }

        for (final Map.Entry<String, Object> entry : table.entrySet()) {
            setProperty(entry.getKey(), entry.getValue().toString());
        }
    }

    public void setProperty(final String prop, final Object value)
            throws SAXNotRecognizedException, SAXNotSupportedException {
        switch (prop) {
            case Namespaces.SAX_LEXICAL_HANDLER:
                lexicalHandler = (LexicalHandler) value;
                break;
            case EXistOutputKeys.ADD_EXIST_ID:
                if ("element".equals(value)) {
                    showId = EXIST_ID_ELEMENT;
                } else if ("all".equals(value)) {
                    showId = EXIST_ID_ALL;
                } else {
                    showId = EXIST_ID_NONE;
                }
                break;
            default:
                outputProperties.put(prop, value);
                break;
        }

    }

    public String getProperty(final String key, String defaultValue) {
        return outputProperties.getProperty(key, defaultValue);
    }

    public boolean isStylesheetApplied() {
        return templates != null;
    }

    protected int getHighlightingMode() {
        final String option =
                getProperty(EXistOutputKeys.HIGHLIGHT_MATCHES, "elements");
        return switch (option) {
            case "both", "all" -> TAG_BOTH;
            case "elements" -> TAG_ELEMENT_MATCHES;
            case "attributes" -> TAG_ATTRIBUTE_MATCHES;
            case null, default -> TAG_NONE;
        };
    }

    /**
     * If an XSL stylesheet is present, plug it into
     * the chain.
     *
     * @param writer the writer
     */
    protected void applyXSLHandler(final Writer writer) {
        final StreamResult result = new StreamResult(writer);
        xslHandler.setResult(result);
        if ("yes".equals(getProperty(EXistOutputKeys.EXPAND_XINCLUDES, "yes"))) {
            xinclude.setReceiver(new ReceiverToSAX(xslHandler));
            receiver = xinclude;
        } else {
            receiver = new ReceiverToSAX(xslHandler);
        }
    }

    /**
     * Return my internal EntityResolver
     *
     * @return The entityResolver value
     */
    public @Nullable EntityResolver getEntityResolver() {
        return entityResolver;
    }

    public @Nullable ErrorHandler getErrorHandler() {
        return errorHandler;
    }

    /**
     * Set the current User. A valid user is required to
     * process XInclude elements.
     *
     * @param user the user
     */
    public void setUser(final Subject user) {
        this.user = user;
    }

    /**
     * Get the current User.
     *
     * @return the user
     */
    public @Nullable Subject getUser() {
        return user;
    }

    public boolean getFeature(final String name)
            throws SAXNotRecognizedException, SAXNotSupportedException {
        if (name.equals(Namespaces.SAX_NAMESPACES)
                || name.equals(Namespaces.SAX_NAMESPACES_PREFIXES)) {
            throw new SAXNotSupportedException(name);
        }
        throw new SAXNotRecognizedException(name);
    }

    public Object getProperty(final String name)
            throws SAXNotRecognizedException, SAXNotSupportedException {
        if (name.equals(Namespaces.SAX_LEXICAL_HANDLER)) {
            return lexicalHandler;
        }
        throw new SAXNotRecognizedException(name);
    }

    public @Nullable String getStylesheetProperty(final String name) {
        if (xslHandler != null) {
            return xslHandler.getTransformer().getOutputProperty(name);
        }
        return null;
    }

    public void parse(final InputSource input) throws IOException, SAXException {
        // only system-ids are handled
        final String doc = input.getSystemId();
        if (doc == null) {
            throw new SAXException("source is not an eXist document");
        }
        parse(doc);
    }

    protected void setDocument(final DocumentImpl doc) {
        xinclude.setDocument(doc);
    }

    protected void setXQueryContext(@Nullable final XQueryContext context) {
        if (context != null) {
            xinclude.setModuleLoadPath(context.getModuleLoadPath());
        }
    }

    public void parse(final String systemId) throws IOException, SAXException {
        try {
            // try to load document from eXist
            //TODO: this systemId came from exist, so should be an unchecked create, right?
            final DocumentImpl doc = broker.getResource(XmldbURI.create(systemId), Permission.READ);
            if (doc == null) {
                throw new SAXException("document " + systemId + " not found in database");
            } else {
                LOG.debug("serializing {}", doc.getFileURI());
            }

            toSAX(doc);
        } catch (final PermissionDeniedException e) {
            throw new SAXException("permission denied");
        }
    }

    /**
     * Reset the class to its initial state.
     */
    public void reset() {
        this.showId = EXIST_ID_NONE;
        this.entityResolver = null;
        this.errorHandler = null;
        this.createContainerElements = false;
        this.outputProperties.clear();
        this.outputProperties.putAll(defaultOutputProperties);
        this.templates = null;
        this.xslHandler = null;
        this.xinclude.reset();
        this.receiver = xinclude;
        this.lexicalHandler = null;
        this.useCharacterMaps = null;
        this.user = null;
        this.httpContext = null;
        this.documentStarted = false;
    }

    public String serialize(final DocumentImpl doc) throws SAXException {
        final StringWriter writer = new StringWriter();
        serialize(doc, writer);
        return writer.toString();
    }

    /**
     * Serialize a document to the supplied writer.
     *
     * @param doc    the document
     * @param writer the output writer
     * @throws SAXException if an error occurs during serialization
     */
    public void serialize(final DocumentImpl doc, final Writer writer) throws SAXException {
        serialize(doc, writer, true);
    }

    public void serialize(final DocumentImpl doc, final Writer writer, final boolean prepareStylesheet) throws SAXException {
        if (prepareStylesheet) {
            try {
                prepareStylesheets(doc);
            } catch (final TransformerConfigurationException e) {
                throw new SAXException(e.getMessage(), e);
            }
        }

        final SAXSerializer prettyPrinter;
        if (templates != null) {
            applyXSLHandler(writer);
            prettyPrinter = null;
        } else {
            prettyPrinter = setPrettyPrinter(writer, "no".equals(outputProperties.getProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")), null, true);
        }

        try {
            serializeToReceiver(doc, true);
        } finally {
            if (prettyPrinter != null) {
                releasePrettyPrinter(prettyPrinter);
            }
        }
    }

    public String serialize(final NodeValue n) throws SAXException {
        final StringWriter out = new StringWriter();
        serialize(n, out);
        return out.toString();
    }

    public void serialize(final NodeValue n, final Writer out) throws SAXException {
        serialize(n, out, true);
    }

    public void serialize(final NodeValue n, final Writer out, final boolean prepareStylesheet) throws SAXException {
        final Document doc;
        if (n.getItemType() == Type.DOCUMENT && !(n instanceof NodeProxy)) {
            doc = (Document) n;
        } else {
            doc = n.getOwnerDocument();
        }

        if (prepareStylesheet) {
            try {
                prepareStylesheets(doc);
            } catch (final TransformerConfigurationException e) {
                throw new SAXException(e.getMessage(), e);
            }
        }

        final SAXSerializer prettyPrinter;
        if (templates != null) {
            applyXSLHandler(out);
            prettyPrinter = null;
        } else {
            prettyPrinter = setPrettyPrinter(out, "no".equals(outputProperties.getProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")),
                    n.getImplementationType() == NodeValue.PERSISTENT_NODE ? (NodeProxy) n : null, false);
        }

        try {
            serializeToReceiver(n, true);
        } finally {
            if (prettyPrinter != null) {
                releasePrettyPrinter(prettyPrinter);
            }
        }
    }

    /**
     * Serialize a single NodeProxy.
     *
     * @param p the node proxy
     * @return the serialized result
     * @throws SAXException if a SAX error occurs
     */
    public String serialize(final NodeProxy p) throws SAXException {
        final StringWriter out = new StringWriter();
        serialize(p, out);
        return out.toString();
    }

    public void serialize(final NodeProxy p, final Writer out) throws SAXException {
        serialize(p, out, true);
    }

    public void serialize(final NodeProxy p, final Writer out, final boolean prepareStylesheet) throws SAXException {
        if (prepareStylesheet) {
            try {
                prepareStylesheets(p.getOwnerDocument());
            } catch (final TransformerConfigurationException e) {
                throw new SAXException(e.getMessage(), e);
            }
        }

        final SAXSerializer prettyPrinter;
        if (templates != null) {
            applyXSLHandler(out);
            prettyPrinter = null;
        } else {
            prettyPrinter = setPrettyPrinter(out, "no".equals(outputProperties.getProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")), p, false);
        }

        try {
            serializeToReceiver(p, false);
        } finally {
            if (prettyPrinter != null) {
                releasePrettyPrinter(prettyPrinter);
            }
        }
    }

    private void prepareStylesheets(final Document doc) throws TransformerConfigurationException {
        if ("yes".equals(outputProperties.getProperty(EXistOutputKeys.PROCESS_XSL_PI, "no"))) {
            @Nullable final String stylesheet = hasXSLPi(doc);
            if (stylesheet != null) {
                setStylesheet(doc, stylesheet);
            }
        }
        setStylesheetFromProperties(doc);
    }

    /**
     * Set the ContentHandler to be used during serialization.
     *
     * @param contentHandler the content handler
     * @param lexicalHandler the lexical handle
     */
    public void setSAXHandlers(final ContentHandler contentHandler, final LexicalHandler lexicalHandler) {
        final ReceiverToSAX toSAX = new ReceiverToSAX(contentHandler);
        toSAX.setLexicalHandler(lexicalHandler);
        if ("yes".equals(getProperty(EXistOutputKeys.EXPAND_XINCLUDES, "yes"))) {
            xinclude.setReceiver(toSAX);
            receiver = xinclude;
        } else {
            receiver = toSAX;
        }
    }

    public void setReceiver(final Receiver receiver) {
        this.receiver = receiver;
    }

    public void setReceiver(final Receiver receiver, final boolean handleIncludes) {
        if (handleIncludes && "yes".equals(getProperty(EXistOutputKeys.EXPAND_XINCLUDES, "yes"))) {
            xinclude.setReceiver(receiver);
            this.receiver = xinclude;
        } else {
            this.receiver = receiver;
        }
    }

    public XIncludeFilter getXIncludeFilter() {
        return xinclude;
    }

    @Override
    public void setContentHandler(final ContentHandler handler) {
        setSAXHandlers(handler, null);
    }

    @Override
    public @Nullable ContentHandler getContentHandler() {
        return null;
    }

    /**
     * Sets the entityResolver attribute of the Serializer object
     *
     * @param entityResolver The new entityResolver value
     */
    public void setEntityResolver(@Nullable final EntityResolver entityResolver) {
        this.entityResolver = entityResolver;
    }

    /**
     * Sets the errorHandler attribute of the Serializer object
     *
     * @param errorHandler The new errorHandler value
     */
    public void setErrorHandler(@Nullable final ErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }

    /**
     * Sets the feature attribute of the Serializer object
     *
     * @param name  The new feature name
     * @param value The new feature value
     * @throws SAXNotRecognizedException Description of the Exception
     * @throws SAXNotSupportedException  Description of the Exception
     */
    public void setFeature(final String name, final boolean value)
            throws SAXNotRecognizedException, SAXNotSupportedException {
        if (name.equals(Namespaces.SAX_NAMESPACES) || name.equals(Namespaces.SAX_NAMESPACES_PREFIXES)) {
            throw new SAXNotSupportedException(name);
        }
        throw new SAXNotRecognizedException(name);
    }

    protected SAXSerializer setPrettyPrinter(final Writer writer, final boolean xmlDecl, final NodeProxy root, final boolean applyFilters) {
        outputProperties.setProperty(OutputKeys.OMIT_XML_DECLARATION, xmlDecl ? "no" : "yes");

        final SAXSerializer xmlout = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);
        xmlout.setOutput(writer, outputProperties);

        if ("yes".equals(getProperty(EXistOutputKeys.EXPAND_XINCLUDES, "yes"))) {
            xinclude.setReceiver(xmlout);
            receiver = xinclude;
        } else {
            receiver = xmlout;
        }

        if (root != null && getHighlightingMode() != TAG_NONE) {
            final IndexController controller = broker.getIndexController();
            MatchListener listener = controller.getMatchListener(root);
            if (listener != null) {
                final MatchListener last = (MatchListener) listener.getLastInChain();
                last.setNextInChain(receiver);
                receiver = listener;
            }
        }

        if (root == null && applyFilters && customMatchListeners.getFirst() != null) {
            customMatchListeners.getLast().setNextInChain(receiver);
            receiver = customMatchListeners.getFirst();
        }

        return xmlout;
    }

    protected Receiver setupMatchListeners(final NodeProxy p) {
        final Receiver oldReceiver = receiver;
        if (getHighlightingMode() != TAG_NONE) {
            final IndexController controller = broker.getIndexController();
            MatchListener listener = controller.getMatchListener(p);
            if (listener != null) {
                final MatchListener last = (MatchListener) listener.getLastInChain();
                last.setNextInChain(receiver);
                receiver = listener;
            }
        }
        return oldReceiver;
    }

    protected void releasePrettyPrinter(final SAXSerializer prettyPrinter) {
        SerializerPool.getInstance().returnObject(prettyPrinter);
    }

    protected void setStylesheetFromProperties(final Document doc) throws TransformerConfigurationException {
        if (templates != null) {
            return;
        }

        final String stylesheet = outputProperties.getProperty(EXistOutputKeys.STYLESHEET);
        if (stylesheet != null) {
            if (doc instanceof DocumentImpl) {
                setStylesheet(doc, stylesheet);
            } else {
                setStylesheet(null, stylesheet);
            }
        }
    }

    protected void checkStylesheetParams() {
        if (xslHandler == null) {
            return;
        }

        for (final Enumeration<?> e = outputProperties.propertyNames(); e.hasMoreElements(); ) {
            String property = (String) e.nextElement();
            if (property.startsWith(EXistOutputKeys.STYLESHEET_PARAM)) {
                final String value = outputProperties.getProperty(property);
                property = property.substring(EXistOutputKeys.STYLESHEET_PARAM.length() + 1);
                xslHandler.getTransformer().setParameter(property, value);
            }
        }
    }

    /**
     * Plug an XSL stylesheet into the processing pipeline.
     * All output will be passed to this stylesheet.
     *
     * @param doc        the document
     * @param stylesheet the stylesheet
     * @throws TransformerConfigurationException if the stylesheet cannot be set
     */
    public void setStylesheet(final Document doc, final @Nullable String stylesheet) throws TransformerConfigurationException {
        if (stylesheet == null) {
            templates = null;
            return;
        }

        final long start = System.currentTimeMillis();
        xslHandler = null;
        @Nullable XmldbURI stylesheetUri = null;
        @Nullable URI externalUri = null;
        try {
            stylesheetUri = XmldbURI.xmldbUriFor(stylesheet);
            if (!stylesheetUri.toCollectionPathURI().equals(stylesheetUri)) {
                externalUri = stylesheetUri.getXmldbURI();
            }
        } catch (final URISyntaxException e) {
            //could be an external URI!
            try {
                externalUri = new URI(stylesheet);
            } catch (final URISyntaxException ee) {
                throw new IllegalArgumentException("Stylesheet URI could not be parsed: " + ee.getMessage());
            }
        }
        // does stylesheet point to an external resource?
        if (externalUri != null) {
            final StreamSource source = new StreamSource(externalUri.toString());
            this.templates = factory.get().newTemplates(source);
            // read stylesheet from the database
        } else {
            // if stylesheet is relative, add path to the
            // current collection and normalize
            if (doc != null && doc instanceof DocumentImpl) {
                stylesheetUri = ((DocumentImpl) doc).getCollection().getURI().resolveCollectionPath(stylesheetUri).normalizeCollectionPath();
            }

            // load stylesheet from eXist
            DocumentImpl xsl = null;
            try {
                xsl = broker.getResource(stylesheetUri, Permission.READ);
            } catch (final PermissionDeniedException e) {
                throw new TransformerConfigurationException("permission denied to read " + stylesheetUri);
            }
            if (xsl == null) {
                throw new TransformerConfigurationException("stylesheet not found: " + stylesheetUri);
            }

            //TODO: use xmldbURI
            if (xsl.getCollection() != null) {
                factory.get().setURIResolver(new InternalURIResolver(xsl.getCollection().getURI().toString()));
            }

            // save handlers
            Receiver oldReceiver = receiver;

            // compile stylesheet
            factory.get().setErrorListener(new ErrorListener());
            final TemplatesHandler handler = factory.get().newTemplatesHandler();
            receiver = new ReceiverToSAX(handler);
            try {
                this.serializeToReceiver(xsl, true);
                templates = handler.getTemplates();
            } catch (final SAXException e) {
                throw new TransformerConfigurationException(e.getMessage(), e);
            }

            // restore handlers
            receiver = oldReceiver;
            factory.get().setURIResolver(null);
        }
        LOG.debug("compiling stylesheet took {}", System.currentTimeMillis() - start);
        if (templates != null) {
            xslHandler = factory.get().newTransformerHandler(templates);
            try {
                xslHandler.startDocument();
                documentStarted = true;
            } catch (final SAXException e) {
                throw new TransformerConfigurationException(e.getMessage(), e);
            }
        }
//			xslHandler.getTransformer().setOutputProperties(outputProperties);
        checkStylesheetParams();
    }

    /**
     * Set stylesheet parameter
     *
     * @param name  the parameter name
     * @param value the parameter value
     */
    public void setStylesheetParam(final String name, final String value) {
        if (xslHandler != null) {
            xslHandler.getTransformer().setParameter(name, value);
        }
    }

    protected void setXSLHandler(@Nullable final NodeProxy root, final boolean applyFilters) {
        if (templates != null && xslHandler != null) {
            final SAXResult result = new SAXResult();
            final boolean processXInclude =
                    "yes".equals(getProperty(EXistOutputKeys.EXPAND_XINCLUDES, "yes"));

            final ReceiverToSAX filter;
            if (processXInclude) {
                final Receiver xincludeReceiver = xinclude.getReceiver();
                if (xincludeReceiver != null && xincludeReceiver instanceof SAXSerializer) {
                    filter = new ReceiverToSAX((SAXSerializer) xincludeReceiver);
                } else {
                    filter = (ReceiverToSAX) xincludeReceiver;
                }
            } else {
                filter = (ReceiverToSAX) receiver;
            }

            result.setHandler(filter.getContentHandler());
            result.setLexicalHandler(filter.getLexicalHandler());

            filter.setLexicalHandler(xslHandler);
            filter.setContentHandler(xslHandler);

            xslHandler.setResult(result);
            if (processXInclude) {
                xinclude.setReceiver(new ReceiverToSAX(xslHandler));
                receiver = xinclude;
            } else {
                receiver = new ReceiverToSAX(xslHandler);
            }
        }
        if (root != null && getHighlightingMode() != TAG_NONE) {
            final IndexController controller = broker.getIndexController();
            MatchListener listener = controller.getMatchListener(root);
            if (listener != null) {
                final MatchListener last = (MatchListener) listener.getLastInChain();
                last.setNextInChain(receiver);
                receiver = listener;
            }
        }
        if (applyFilters && root == null && customMatchListeners.getFirst() != null) {
            customMatchListeners.getLast().setNextInChain(receiver);
            receiver = customMatchListeners.getFirst();
        }
    }

    public void toSAX(final DocumentImpl doc) throws SAXException {
        try {
            prepareStylesheets(doc);
        } catch (final TransformerConfigurationException e) {
            throw new SAXException(e.getMessage(), e);
        }
        setXSLHandler(null, true);
        serializeToReceiver(doc, "true".equals(getProperty(GENERATE_DOC_EVENTS, "false")));
    }

    public void toSAX(final NodeValue n) throws SAXException {
        try {
            if (n.getType() == Type.DOCUMENT && !(n instanceof NodeProxy)) {
                setStylesheetFromProperties((Document) n);
            } else {
                setStylesheetFromProperties(n.getOwnerDocument());
            }
        } catch (final TransformerConfigurationException e) {
            throw new SAXException(e.getMessage(), e);
        }
        setXSLHandler(n.getImplementationType() == NodeValue.PERSISTENT_NODE ? (NodeProxy) n : null, false);
        serializeToReceiver(n, "true".equals(getProperty(GENERATE_DOC_EVENTS, "false")));
    }

    public void toSAX(final NodeProxy p) throws SAXException {
        try {
            setStylesheetFromProperties(p.getOwnerDocument());
        } catch (final TransformerConfigurationException e) {
            throw new SAXException(e.getMessage(), e);
        }
        setXSLHandler(p, false);
        if (p.getNodeId() == NodeId.DOCUMENT_NODE) {
            serializeToReceiver(p.getOwnerDocument(), "true".equals(getProperty(GENERATE_DOC_EVENTS, "false")));
        } else {
            serializeToReceiver(p, "true".equals(getProperty(GENERATE_DOC_EVENTS, "false")));
        }
    }

    /**
     * Serialize the items in the given sequence to SAX, starting with item start. If parameter
     * wrap is set to true, output a wrapper element to enclose the serialized items. The
     * wrapper element will be in namespace {@link org.exist.Namespaces#EXIST_NS} and has the following form:
     * <p>
     * &lt;exist:result hits="sequence length" start="value of start" count="value of count"&gt;
     *
     * @param seq             The sequence to serialize
     * @param start           The position in the sequence to start serialization from
     * @param count           The number of items from the start position to serialize
     * @param wrap            Indicates whether the output should be wrapped
     * @param typed           Indicates whether the output types should be wrapped
     * @param compilationTime The time taken to compile the query which produced the sequence
     * @param executionTime   The time taken to execute the query which produced the sequence
     * @throws SAXException If an error occurs during serialization
     */
    public void toSAX(final Sequence seq, int start, final int count, final boolean wrap, final boolean typed, final long compilationTime, final long executionTime) throws SAXException {
        try {
            setStylesheetFromProperties(null);
        } catch (final TransformerConfigurationException e) {
            throw new SAXException(e.getMessage(), e);
        }
        setXSLHandler(null, false);
        final AttrList attrs = new AttrList();
        attrs.addAttribute(ATTR_HITS_QNAME, Integer.toString(seq.getItemCount()));
        attrs.addAttribute(ATTR_START_QNAME, Integer.toString(start));
        attrs.addAttribute(ATTR_COUNT_QNAME, Integer.toString(count));
        if (outputProperties.getProperty(PROPERTY_SESSION_ID) != null) {
            attrs.addAttribute(ATTR_SESSION_ID, outputProperties.getProperty(PROPERTY_SESSION_ID));
        }
        attrs.addAttribute(ATTR_COMPILATION_TIME_QNAME, Long.toString(compilationTime));
        attrs.addAttribute(ATTR_EXECUTION_TIME_QNAME, Long.toString(compilationTime));

        if (!documentStarted) {
            receiver.startDocument();
            documentStarted = true;
        }
        if (wrap) {
            receiver.startPrefixMapping("exist", Namespaces.EXIST_NS);
            receiver.startElement(ELEM_RESULT_QNAME, attrs);
        }

        for (int i = --start; i < start + count; i++) {
            final Item item = seq.itemAt(i);
            if (item == null) {
                LOG.debug("item {} not found", i);
                continue;
            }
            itemToSAX(item, typed, wrap);
        }

        if (wrap) {
            receiver.endElement(ELEM_RESULT_QNAME);
            receiver.endPrefixMapping("exist");
        }
        receiver.endDocument();
    }

    /**
     * Serialize the items in the given sequence to SAX, starting with item start. If parameter
     * wrap is set to true, output a wrapper element to enclose the serialized items. The
     * wrapper element will be in namespace {@link org.exist.Namespaces#EXIST_NS} and has the following form:
     * <p>
     * &lt;exist:result hits="sequence length" start="value of start" count="value of count"&gt;
     *
     * @param seq The sequence to serialize
     * @throws SAXException If an error occurs during serialization
     */
    public void toSAX(final Sequence seq) throws SAXException {
        try {
            setStylesheetFromProperties(null);
        } catch (final TransformerConfigurationException e) {
            throw new SAXException(e.getMessage(), e);
        }

        setXSLHandler(null, false);

        if (!documentStarted) {
            receiver.startDocument();
            documentStarted = true;
        }

        try {
            final SequenceIterator itSeq = seq.iterate();
            while (itSeq.hasNext()) {
                final Item item = itSeq.nextItem();
                itemToSAX(item, false, false);
            }
        } catch (final XPathException xpe) {
            throw new SAXException(xpe.getMessage(), xpe);
        }

        receiver.endDocument();
    }

    /**
     * Serializes an Item
     *
     * @param item  The item to serialize
     * @param wrap  Indicates whether the output should be wrapped
     * @param typed Indicates whether the output types should be wrapped
     * @throws SAXException If an error occurs during serialization
     */
    public void toSAX(final Item item, final boolean wrap, final boolean typed) throws SAXException {
        try {
            setStylesheetFromProperties(null);
        } catch (final TransformerConfigurationException e) {
            throw new SAXException(e.getMessage(), e);
        }

        setXSLHandler(null, false);
        final AttrList attrs = new AttrList();
        attrs.addAttribute(ATTR_HITS_QNAME, "1");
        attrs.addAttribute(ATTR_START_QNAME, "1");
        attrs.addAttribute(ATTR_COUNT_QNAME, "1");
        if (outputProperties.getProperty(PROPERTY_SESSION_ID) != null) {
            attrs.addAttribute(ATTR_SESSION_ID, outputProperties.getProperty(PROPERTY_SESSION_ID));
        }

        if (!documentStarted) {
            receiver.startDocument();
            documentStarted = true;
        }

        if (wrap) {
            receiver.startPrefixMapping("exist", Namespaces.EXIST_NS);
            receiver.startElement(ELEM_RESULT_QNAME, attrs);
        }

        itemToSAX(item, typed, wrap);

        if (wrap) {
            receiver.endElement(ELEM_RESULT_QNAME);
            receiver.endPrefixMapping("exist");
        }

        receiver.endDocument();
    }

    private void itemToSAX(final Item item, final boolean typed, final boolean wrap) throws SAXException {
        if (Type.subTypeOf(item.getType(), Type.NODE)) {
            final NodeValue node = (NodeValue) item;

            if (typed) {
                //TODO the typed and wrapped stuff should ideally be replaced
                //with Marshaller.marshallItem
                //unfortrunately calling Marshaller.marshallItem(broker, item, new SAXToReceiver(receiver))
                //results in a stack overflow
                //TODO consider a full XDM serializer in place of this for these special needs

                serializeTypePreNode(node);
                if (node.getType() == Type.ATTRIBUTE) {
                    serializeTypeAttributeValue(node);
                } else {
                    serializeToReceiver(node, false);
                }
                serializeTypePostNode(node);
            } else {
                serializeToReceiver(node, false);
            }
        } else {
            if (wrap) {
                final AttrList attrs = new AttrList();
                attrs.addAttribute(ATTR_TYPE_QNAME, Type.getTypeName(item.getType()));
                receiver.startElement(ELEM_VALUE_QNAME, attrs);
            }
            try {
                receiver.characters(item.getStringValue());
            } catch (final XPathException e) {
                throw new SAXException(e.getMessage(), e);
            }
            if (wrap) {
                receiver.endElement(ELEM_VALUE_QNAME);
            }
        }
    }

    public void toReceiver(final NodeProxy p, final boolean highlightMatches) throws SAXException {
        toReceiver(p, highlightMatches, true);
    }

    public void toReceiver(final NodeProxy p, final boolean highlightMatches, final boolean checkAttributes) throws SAXException {
        final Receiver oldReceiver = highlightMatches ? setupMatchListeners(p) : receiver;
        serializeToReceiver(p, false, checkAttributes);
        receiver = oldReceiver;
    }

    protected abstract void serializeToReceiver(NodeProxy p, boolean generateDocEvent, boolean checkAttributes) throws SAXException;

    protected abstract void serializeToReceiver(DocumentImpl doc, boolean generateDocEvent)
            throws SAXException;

    protected void serializeToReceiver(final NodeValue v, final boolean generateDocEvents)
            throws SAXException {
        if (v.getImplementationType() == NodeValue.PERSISTENT_NODE) {
            serializeToReceiver((NodeProxy) v, generateDocEvents, true);
        } else {
            serializeToReceiver((org.exist.dom.memtree.NodeImpl) v, generateDocEvents);
        }
    }

    protected void serializeToReceiver(final org.exist.dom.memtree.NodeImpl n, final boolean generateDocEvents)
            throws SAXException {
        if (generateDocEvents && !documentStarted) {
            receiver.startDocument();
        }
        setDocument(null);
        if (n.getNodeType() == Node.DOCUMENT_NODE) {
            final org.exist.dom.memtree.DocumentImpl doc = (org.exist.dom.memtree.DocumentImpl) n;
            setXQueryContext(doc.getContext());
            //TODO set XSL //code from set Stlyesheet XSL_PI
            if ("yes".equals(outputProperties.getProperty(EXistOutputKeys.PROCESS_XSL_PI, "no"))) {
                final String stylesheet = hasXSLPi(doc);
                if (stylesheet != null) {
                    try {
                        setStylesheet(doc, stylesheet);
                    } catch (final TransformerConfigurationException e) {
                        throw new SAXException(e.getMessage(), e);
                    }
                }
            }
            try {
                setStylesheetFromProperties(doc);
            } catch (final TransformerConfigurationException e) {
                throw new SAXException(e.getMessage(), e);
            }
            setXSLHandler(null, true);
        } else {
            setXQueryContext(n.getOwnerDocument().getContext());
            //TODO unSet XSL code from
            try {
                setStylesheetFromProperties(null);
            } catch (final TransformerConfigurationException e) {
                throw new SAXException(e.getMessage(), e);
            }
            setXSLHandler(null, false);
        }
        n.streamTo(this, receiver);
        if (generateDocEvents) {
            receiver.endDocument();
        }
    }

    @Override
    public void setDTDHandler(final DTDHandler handler) {
    }

    @Override
    public @Nullable DTDHandler getDTDHandler() {
        return null;
    }

    /**
     * Check if the document has an xml-stylesheet processing instruction
     * that references an XSLT stylesheet. Return the link to the stylesheet.
     *
     * @param doc the document
     * @return link to the stylesheet, or null if there is no xml-stylesheet processing instruction.
     */
    public @Nullable String hasXSLPi(final Document doc) {
        final boolean applyXSLPI = outputProperties.getProperty(EXistOutputKeys.PROCESS_XSL_PI, "no").equalsIgnoreCase("yes");
        if (!applyXSLPI) {
            return null;
        }

        NodeList docChildren = doc.getChildNodes();
        if (docChildren.getLength() == 1) {
            final Node onlyChild = docChildren.item(0);
            if (onlyChild.getNodeType() == NodeImpl.REFERENCE_NODE) {
                // if this is a reference to a persistent document node then we must expand it
                final NodeProxy nodeProxy = ((ReferenceNode) onlyChild).getReference();
                if (nodeProxy.getNodeType() == Node.DOCUMENT_NODE) {
                    // switch docChildren to the children of the dereferencedNode
                    final Node dereferencedNode = nodeProxy.getNode();
                    docChildren = dereferencedNode.getChildNodes();
                }
            }
        }

        for (int i = 0; i < docChildren.getLength(); i++) {
            final Node node = docChildren.item(i);
            if (node.getNodeType() == Node.PROCESSING_INSTRUCTION_NODE
                    && "xml-stylesheet".equals(((ProcessingInstruction) node).getTarget())) {
                // found <?xml-stylesheet?>
                final String xsl = ((ProcessingInstruction) node).getData();
                final String type = XMLUtil.parseValue(xsl, "type");
                if (type != null && (type.equals(MimeType.XML_TYPE.getName()) || type.equals(MimeType.XSL_TYPE.getName()) || type.equals(MimeType.XSLT_TYPE.getName()))) {
                    final String href = XMLUtil.parseValue(xsl, "href");
                    if (href == null) {
                        continue;
                    }
                    return href;
                }
            }
        }
        return null;
    }


    /**
     * Quick code fix for the remote XQJ API implementation.
     * <p>
     * attribute name { "value" } ---&gt; goes through fine.
     * <p>
     * fn:doc($expr)/element()/attribute() ---&gt; fails, as this is
     * contained within the Database (not an in memory attribute).
     *
     * @param item a NodeValue
     * @throws SAXException if the attribute can't be serialized
     * @author Charles Foster
     */
    protected void serializeTypeAttributeValue(final NodeValue item) throws SAXException {
        try {
            receiver.characters(item.getStringValue());
        } catch (final XPathException e) {
            LOG.error("XPath error trying to retrieve attribute value. {}", e.getMessage(), e);
        }
    }

    /**
     * Writes a start element for DOCUMENT, ATTRIBUTE and TEXT nodes.
     * This is required for the XQJ API implementation.
     *
     * @param item a NodeValue which will be wrapped in a element.
     * @throws SAXException if the element can't be serialized
     * @author Charles Foster
     */
    protected void serializeTypePreNode(final NodeValue item) throws SAXException {
        @Nullable AttrList attrs = null;

        switch (item.getType()) {
            case Type.DOCUMENT:

                final String baseUri = ((Document) item).getBaseURI();

                attrs = new AttrList();
                if (baseUri != null && !baseUri.isEmpty()) {
                    attrs.addAttribute(ATTR_URI_QNAME, baseUri);
                }
                if (((Document) item).getDocumentElement() == null) {
                    attrs.addAttribute(ATTR_HAS_ELEMENT_QNAME, "false");
                }

                receiver.startElement(ELEM_DOC_QNAME, attrs);
                break;

            case Type.ATTRIBUTE:
                attrs = new AttrList();

                String attributeValue;
                if ((attributeValue = item.getNode().getLocalName()) != null && !attributeValue.isEmpty()) {
                    attrs.addAttribute(ATTR_LOCAL_QNAME, attributeValue);
                }
                if ((attributeValue = item.getNode().getNamespaceURI()) != null && !attributeValue.isEmpty()) {
                    attrs.addAttribute(ATTR_TNS_QNAME, attributeValue);
                }
                if ((attributeValue = item.getNode().getPrefix()) != null && !attributeValue.isEmpty()) {
                    attrs.addAttribute(ATTR_PREFIX_QNAME, attributeValue);
                }

                receiver.startElement(ELEM_ATTR_QNAME, attrs);
                break;

            case Type.TEXT:
                receiver.startElement(ELEM_TEXT_QNAME, null);
                break;

            default:
        }
    }

    /**
     * Writes an end element for DOCUMENT, ATTRIBUTE and TEXT nodes.
     * This is required for the XQJ API implementation.
     *
     * @param item the item which will be wrapped in an element.
     * @throws SAXException if the element can't be serialized
     * @author Charles Foster
     */
    protected void serializeTypePostNode(final NodeValue item) throws SAXException {
        switch (item.getType()) {
            case Type.DOCUMENT:
                receiver.endElement(ELEM_DOC_QNAME);
                break;
            case Type.ATTRIBUTE:
                receiver.endElement(ELEM_ATTR_QNAME);
                break;
            case Type.TEXT:
                receiver.endElement(ELEM_TEXT_QNAME);
                break;
            default:
        }
    }


    /**
     * URIResolver is called by the XSL transformer to handle <xsl:include>,
     * <xsl:import> ...
     *
     * @author <a href="mailto:meier@ifs.tu-darmstadt.de">Wolfgang Meier</a>
     */
    private class InternalURIResolver implements URIResolver {

        private final String collectionId;

        public InternalURIResolver(final String collection) {
            collectionId = collection;
        }

        @Override
        public Source resolve(String href, final String base) throws TransformerException {
            LOG.debug("resolving stylesheet ref {}", href);
            if (href.indexOf(':') != Constants.STRING_NOT_FOUND) {
                // href is an URL pointing to an external resource
                return null;
            }
            ///TODO : use dedicated function in XmldbURI
            final URI baseURI = URI.create(collectionId + "/");
            final URI uri = URI.create(href);
            href = baseURI.resolve(uri).toString();

            // TODO(AR) ideally we should reuse from DBBroker.borrowSerializer
            final Serializer serializer = new NativeSerializer(broker, broker.getConfiguration());

            return new SAXSource(serializer, new InputSource(href));
        }
    }

    /**
     * An error listener that just rethrows the exception
     */
    private static class ErrorListener implements javax.xml.transform.ErrorListener {

        @Override
        public void warning(final TransformerException exception) throws TransformerException {
            LOG.warn("Warning while applying stylesheet: {}", exception.getMessage(), exception);
        }

        @Override
        public void error(final TransformerException exception) throws TransformerException {
            throw exception;
        }

        @Override
        public void fatalError(final TransformerException exception) throws TransformerException {
            throw exception;
        }
    }
}
