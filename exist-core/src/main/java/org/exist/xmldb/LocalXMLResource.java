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
package org.exist.xmldb;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.xml.transform.TransformerException;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import net.bytebuddy.matcher.ElementMatchers;
import org.apache.commons.io.IOUtils;
import org.exist.dom.memtree.DocumentImpl;
import org.exist.dom.memtree.NodeImpl;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.StoredNode;
import org.exist.dom.persistent.XMLUtil;
import org.exist.numbering.NodeId;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.Txn;
import org.exist.util.MimeType;
import org.exist.util.serializer.DOMSerializer;
import org.exist.util.serializer.DOMStreamer;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.*;
import org.xml.sax.ext.LexicalHandler;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

import com.evolvedbinary.j8fu.function.ConsumerE;
import com.evolvedbinary.j8fu.tuple.Tuple3;

/**
 * Local implementation of XMLResource.
 */
public class LocalXMLResource extends AbstractEXistResource implements XMLResource {

    private NodeProxy proxy = null;

    private Properties outputProperties;
    private LexicalHandler lexicalHandler = null;

    // those are the different types of content this resource
    // may have to deal with
    protected String content = null;
    protected Path file = null;
    protected InputSource inputSource = null;
    protected Node root = null;
    protected AtomicValue value = null;

    public LocalXMLResource(final Subject user, final BrokerPool brokerPool, final LocalCollection parent, final XmldbURI did) throws XMLDBException {
        super(user, brokerPool, parent, did, MimeType.XML_TYPE.getName());
        this.outputProperties = parent != null ? parent.getProperties() : null;
    }

    public LocalXMLResource(final Subject user, final BrokerPool brokerPool, final LocalCollection parent, final NodeProxy p) throws XMLDBException {
        this(user, brokerPool, parent, p.getOwnerDocument().getFileURI());
        this.proxy = p;
        this.outputProperties = parent != null ? parent.getProperties() : null;
    }

    @Override
    public String getDocumentId() throws XMLDBException {
        return docId.toString();
    }

    @Override
    public Object getContent() throws XMLDBException {
        if (content != null) {            
            return content;
        }

        // Case 1: content is an external DOM node
        else if (root != null && !(root instanceof NodeValue)) {
            try(final StringWriter writer = new StringWriter()) {
                final DOMSerializer serializer = new DOMSerializer(writer, getProperties());
                try {
                    serializer.serialize(root);
                    content = writer.toString();
                } catch (final TransformerException e) {
                    throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, e.getMessage(), e);
                }
                return content;
            } catch(final IOException e) {
                throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(), e);
            }

        // Case 2: content is an atomic value
        } else if (value != null) {
            try {
                if (Type.subTypeOf(value.getType(),Type.STRING)) {
                    return ((StringValue)value).getStringValue(true);
                } else {
                    return value.getStringValue();
                }
            } catch (final XPathException e) {
                throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, e.getMessage(), e);
            }

        // Case 3: content is a file
        } else if (file != null) {
            try {
                content = XMLUtil.readFile(file);
                return content;
            } catch (final IOException e) {
                throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "error while reading resource contents", e);
            }

        // Case 4: content is an input source
        } else if (inputSource != null) {
            try {
                content = XMLUtil.readFile(inputSource);
                return content;
            } catch (final IOException e) {
                throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "error while reading resource contents", e);
            }

        // Case 5: content is a document or internal node, we MUST serialize it
        } else {
            content = withDb((broker, transaction) -> {
                final Serializer serializer = broker.borrowSerializer();

                try {
                    serializer.setUser(user);
                    serializer.setProperties(getProperties());

                    if (root != null) {
                        return serialize(broker, saxSerializer -> saxSerializer.toSAX((NodeValue) root));
                    } else if (proxy != null) {
                        return serialize(broker, saxSerializer -> saxSerializer.toSAX(proxy));
                    } else {
                        return this.<String>read(broker, transaction).apply((document, broker1, transaction1) -> {
                            try {
                                return serialize(broker, saxSerializer -> saxSerializer.toSAX(document));
                            } catch (final SAXException e) {
                                throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
                            }
                        });
                    }
                } catch (final SAXException e) {
                    throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
                } finally {
                    broker.returnSerializer(serializer);
                }
            });
            return content;
        }
    }
    
    @Override
    public void getContentAsStream(OutputStream os) throws XMLDBException {
        try {
            if (content != null) {
                os.write(content.getBytes(UTF_8));

            // Case 1: content is an external DOM node
            } else if (root != null && !(root instanceof NodeValue)) {
                try(OutputStreamWriter writer = new OutputStreamWriter(os, UTF_8)) {
                    final DOMSerializer serializer = new DOMSerializer(writer, getProperties());
                    try {
                        serializer.serialize(root);
                        content = writer.toString();
                    } catch (final TransformerException e) {
                        throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, e.getMessage(), e);
                    }
                }

            // Case 2: content is an atomic value
            } else if (value != null) {
                try {
                    if (Type.subTypeOf(value.getType(),Type.STRING)) {
                        os.write(((StringValue)value).getStringValue(true).getBytes(UTF_8));
                    } else {
                        os.write(value.getStringValue().getBytes(UTF_8));
                    }
                } catch (final XPathException e) {
                    throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, e.getMessage(), e);
                }

            // Case 3: content is a file
            } else if (file != null) {
                Files.copy(file, os);
            
              // Case 4: content is an input source
            } else if (inputSource != null) {
                IOUtils.copy(inputSource.getByteStream(), os);

              // Case 5: content is a document or internal node, we MUST serialize it
            } else {
                content = withDb((broker, transaction) -> {
                    final Serializer serializer = broker.getSerializer();
                    serializer.setUser(user);
                    try {
                        serializer.setProperties(getProperties());
                        if (root != null) {
                            return serialize(broker, saxSerializer -> saxSerializer.toSAX((NodeValue) root));
                        } else if (proxy != null) {
                            return serialize(broker, saxSerializer -> saxSerializer.toSAX(proxy));
                        } else {
                            return this.<String>read(broker, transaction).apply((document, broker1, transaction1) -> {
                                try {
                                    return serialize(broker, saxSerializer -> saxSerializer.toSAX(document));
                                } catch (final SAXException e) {
                                    throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
                                }
                            });
                        }
                    } catch (final SAXException e) {
                        throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
                    }
               });
               os.write(content.getBytes(UTF_8));
            }
        } catch (IOException e) {
            throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(), e);
        }
    }

    private String serialize(final DBBroker broker, final ConsumerE<Serializer, SAXException> toSaxFunction) throws SAXException, IOException {
        final Serializer serializer = broker.borrowSerializer();
        SAXSerializer saxSerializer = null;
        try {
            serializer.setUser(user);
            serializer.setProperties(getProperties());
            saxSerializer = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);

            try (final StringWriter writer = new StringWriter()) {
                saxSerializer.setOutput(writer, getProperties());
                serializer.setSAXHandlers(saxSerializer, saxSerializer);

                toSaxFunction.accept(serializer);

                writer.flush();
                return writer.toString();
            }
        } finally {
            if (saxSerializer != null) {
                SerializerPool.getInstance().returnObject(saxSerializer);
            }
            broker.returnSerializer(serializer);
        }
    }

    @Override
    public Node getContentAsDOM() throws XMLDBException {
        final Node result;
        if (root != null) {
            if(root instanceof NodeImpl) {
                withDb((broker, transaction) -> {
                    ((NodeImpl)root).expand();
                    return null;
                });
            }
            result = root;
        } else if (value != null) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "cannot return an atomic value as DOM node");
        } else {
            result = read((document, broker, transaction) -> {
                if (proxy != null) {
                    return document.getNode(proxy);
                } else {
                    // <frederic.glorieux@ajlsm.com> return a full to get root PI and comments
                    return document;
                }
            });
        }

        return exportInternalNode(result);
    }

    /**
     * Provides a safe export of an internal persistent DOM
     * node from eXist via the Local XML:DB API.
     *
     * This is done by providing a cglib Proxy object that only implements
     * the appropriate W3C DOM interface. This helps prevent the
     * XML:DB Local API from leaking implementation through
     * its abstractions.
     */
    private Node exportInternalNode(final Node node) {
        final Optional<Class<? extends Node>> domClazz = getW3cNodeInterface(node.getClass());
        if(!domClazz.isPresent()) {
            throw new IllegalArgumentException("Provided node does not implement org.w3c.dom");
        }

        DynamicType.Builder<? extends Node> byteBuddyBuilder = new ByteBuddy()
                .subclass(domClazz.get());

        // these interfaces are just used to flag the node type (persistent or memtree) to make
        // the implementation of {@link DOMMethodInterceptor} simpler.
        if (node instanceof StoredNode) {
            byteBuddyBuilder = byteBuddyBuilder.implement(StoredNodeIdentity.class);
        } else if (node instanceof org.exist.dom.memtree.NodeImpl) {
            byteBuddyBuilder = byteBuddyBuilder.implement(MemtreeNodeIdentity.class);
        }

        byteBuddyBuilder = byteBuddyBuilder
                .method(ElementMatchers.any())
                .intercept(InvocationHandlerAdapter.of(new DOMMethodInterceptor(node)));

        try {
            final Node nodeProxy = byteBuddyBuilder
                    .make()
                    .load(getClass().getClassLoader())
                    .getLoaded()
                    .getDeclaredConstructor().newInstance();

            return nodeProxy;
        } catch (final NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private Optional<Class<? extends Node>> getW3cNodeInterface(final Class<? extends Node> nodeClazz) {
        return Stream.of(nodeClazz.getInterfaces())
                .filter(iface -> iface.getPackage().getName().equals("org.w3c.dom"))
                .findFirst()
                .map(c -> (Class<? extends Node>)c);
    }

    public class DOMMethodInterceptor implements InvocationHandler {
        private final Node node;

        public DOMMethodInterceptor(final Node node) {
            this.node = node;
        }

        @Override
        public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {


            /*
                NOTE(AR): we have to take special care of eXist-db's
                persistent and memtree DOM's node equality.

                For the persistent DOM, we reproduce in the proxy the behaviour taken
                by org.exist.dom.persistent.StoredNode#equals(Object),
                by overriding equals for StoredNode's and then implementing
                a method to retrieve the nodeIds from each side of the equality
                comparison. We have to do this as StoredNode attempts instanceof
                equality which will fail against the proxied objects.

                For the memtree DOM, we reproduce in the proxy the behaviour taken
                by org.exist.dom.memtree.NodeImpl#equals(Object),
                by overriding equals for memtree.NodeImpl's and then implementing
                a method to retrieve the nodeIds from each side of the equality
                comparison. We have to do this as NodeImpl attempts instanceof and
                reference equality which will fail against the proxied objects.
             */
            Object domResult = null;
            if(method.getName().equals("equals")
                    && proxy instanceof StoredNodeIdentity ni1
                    && args.length == 1 && args[0] instanceof StoredNodeIdentity ni2) {

                final Optional<Boolean> niEquals = ni1.getNodeId().flatMap(n1id -> ni2.getNodeId().map(n1id::equals));
                if (niEquals.isPresent()) {
                    domResult = niEquals.get();
                }
            } else if(method.getName().equals("equals")
                        && proxy instanceof MemtreeNodeIdentity ni1
                        && args.length == 1 && args[0] instanceof MemtreeNodeIdentity ni2) {

                final Optional<Boolean> niEquals = ni1.getNodeId().flatMap(n1id -> ni2.getNodeId().map(n2id -> n1id._1 == n2id._1 && n1id._2 == n2id._2 && n1id._3 == n2id._3));
                    if (niEquals.isPresent()) {
                        domResult = niEquals.get();
                    }
            } else if(method.getName().equals("getNodeId")) {
                if (proxy instanceof StoredNodeIdentity
                        && (args == null || args.length == 0)
                        && node instanceof StoredNode) {
                    domResult = Optional.of(((StoredNode) node).getNodeId());
                } else if (proxy instanceof MemtreeNodeIdentity
                        && (args == null || args.length == 0)
                        && node instanceof NodeImpl memtreeNode) {
                    domResult = Optional.of(Tuple(memtreeNode.getOwnerDocument(), memtreeNode.getNodeNumber(), memtreeNode.getNodeType()));
                } else {
                    domResult = Optional.empty();
                }
            }

            if (domResult == null) {
                domResult = method.invoke(node, args);
            }

            if(domResult != null && Node.class.isAssignableFrom(method.getReturnType())) {
                return exportInternalNode((Node) domResult); //recursively wrap node result

            } else if(domResult != null && method.getReturnType().equals(NodeList.class)) {
                final NodeList underlying = (NodeList)domResult; //recursively wrap nodes in nodelist result
                return new NodeList() {
                    @Override
                    public Node item(final int index) {
                        return Optional.ofNullable(underlying.item(index))
                                .map(LocalXMLResource.this::exportInternalNode)
                                .orElse(null);
                    }

                    @Override
                    public int getLength() {
                        return underlying.getLength();
                    }
                };
            } else {
                return domResult;
            }
        }
    }

    /**
     * Used by {@link DOMMethodInterceptor} to
     * help with equality of persistent DOM nodes.
     */
    public interface StoredNodeIdentity {
        Optional<NodeId> getNodeId();
    }

    /**
     * Used by {@link DOMMethodInterceptor} to
     * help with equality of memtree DOM nodes.
     */
    public interface MemtreeNodeIdentity {
        Optional<Tuple3<DocumentImpl, Integer, Short>> getNodeId();
    }

    @Override
    public void getContentAsSAX(final ContentHandler handler) throws XMLDBException {

        // case 1: content is an external DOM node
        if (root != null && !(root instanceof NodeValue)) {
            try {
                final String option = collection.getProperty(Serializer.GENERATE_DOC_EVENTS, "false");
                final DOMStreamer streamer = (DOMStreamer) SerializerPool.getInstance().borrowObject(DOMStreamer.class);
                try {
                    streamer.setContentHandler(handler);
                    streamer.setLexicalHandler(lexicalHandler);
                    streamer.serialize(root, option.equalsIgnoreCase("true"));
                } finally {
                    SerializerPool.getInstance().returnObject(streamer);
                }
            } catch (final Exception e) {
                throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, e.getMessage(), e);
            }
        } else {
            withDb((broker, transaction) -> {
                try {
                    // case 2: content is an atomic value
                    if (value != null) {
                        value.toSAX(broker, handler, getProperties());

                    // case 3: content is an internal node or a document
                    } else {
                        final Serializer serializer = broker.borrowSerializer();
                        try {
                            serializer.setUser(user);
                            serializer.setProperties(getProperties());
                            serializer.setSAXHandlers(handler, lexicalHandler);
                            if (root != null) {
                                serializer.toSAX((NodeValue) root);

                            } else if (proxy != null) {
                                serializer.toSAX(proxy);

                            } else {
                                read(broker, transaction).apply((document, broker1, transaction1) -> {
                                    try {
                                        serializer.toSAX(document);
                                        return null;
                                    } catch (final SAXException e) {
                                        throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
                                    }
                                });
                            }
                        } finally {
                            broker.returnSerializer(serializer);
                        }
                    }
                    return null;
                } catch(final SAXException e) {
                    throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
                }
            });
        }
    }

    /**
     * Sets the content for this resource. If value is of type File, it is
     * directly passed to the parser when Collection.storeResource is called.
     * Otherwise the method tries to convert the value to String.
     * 
     * Passing a File object should be preferred if the document is large. The
     * file's content will not be loaded into memory but directly passed to a
     * SAX parser.
     * 
     * @param obj the content value to set for the resource.
     * @throws XMLDBException with expected error codes. See {@link ErrorCodes#VENDOR_ERROR}
     *     for any vendor specific errors that occur.
     */
    @Override
    public void setContent(final Object obj) throws XMLDBException {
        content = null;
        file = null;
        value = null;
        inputSource = null;
        root = null;

        switch (obj) {
            case Path path -> file = path;
            case java.io.File file1 -> file = file1.toPath();
            case AtomicValue atomicValue -> value = atomicValue;
            case InputSource source -> inputSource = source;
            case byte[] bytes -> content = new String(bytes, UTF_8);
            case null, default -> content = obj.toString();
        }
    }

    @Override
    public void setContentAsDOM(final Node root) throws XMLDBException {
//        if (root instanceof AttrImpl) {
//            throw new XMLDBException(ErrorCodes.WRONG_CONTENT_TYPE, "SENR0001: can not serialize a standalone attribute");
//        }

        content = null;
        file = null;
        value = null;
        inputSource = null;
        this.root = root;
    }

    @Override
    public ContentHandler setContentAsSAX() throws XMLDBException {
        file = null;
        value = null;
        inputSource = null;
        root = null;
        return new InternalXMLSerializer();
    }

    @Override
    public boolean getSAXFeature(final String name) throws SAXNotRecognizedException, SAXNotSupportedException {
        return false;
    }

    @Override
    public void setSAXFeature(final String name, final boolean value) throws SAXNotRecognizedException, SAXNotSupportedException {
    }
	
    @Override
    public void setLexicalHandler(final LexicalHandler lexicalHandler) {
        this.lexicalHandler = lexicalHandler;
    }

    @Override
    public void setProperties(final Properties properties) {
        this.outputProperties = properties;
    }

    @Override
    @Nullable public Properties getProperties() {
        return outputProperties;
    }

    public NodeProxy getNode() throws XMLDBException {
        if(proxy != null) {
            return proxy;
        } else {
            return read((document, broker, transaction) -> new NodeProxy(null, document, NodeId.DOCUMENT_NODE));
        }
    }

    /**
     * Similar to {@link org.exist.xmldb.LocalXMLResource#getNode()}
     * but useful for operations within the XML:DB Local API
     * that are already working within a transaction.
     *
     * @param broker the database broker.
     * @param transaction the database transaction.
     *
     * @return a proxy to the node.
     *
     * @throws XMLDBException if an error occurs whilst getting the node
     */
    public NodeProxy getNode(final DBBroker broker, final Txn transaction) throws XMLDBException {
        if(proxy != null) {
            return proxy;
        } else {
            return this.<NodeProxy>read(broker, transaction).apply((document, broker1, transaction1) -> new NodeProxy(null, document, NodeId.DOCUMENT_NODE));
        }
    }

    @Override
	public  DocumentType getDocType() throws XMLDBException {
        return read((document, broker, transaction) -> document.getDoctype());
    }

    @Override
    public void setDocType(final DocumentType doctype) throws XMLDBException {
        modify((document, broker, transaction) -> {
            if (document == null) {
                throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, "Resource " + docId + " not found");
            }

            document.setDocumentType(doctype);
            return null;
        });
    }
        
    private class InternalXMLSerializer extends SAXSerializer {
        public InternalXMLSerializer() {
            super(new StringWriter(), null);
        }

        @Override
        public void endDocument() throws SAXException {
            super.endDocument();
            content = getWriter().toString();
        }
    }

    @Override
    public void setXMLReader(XMLReader xmlReader) {
        // no action
    }
}
