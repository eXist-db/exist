/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2015 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exist.xmldb;

import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.XMLUtil;
import org.exist.dom.memtree.AttrImpl;
import org.exist.dom.memtree.NodeImpl;
import org.exist.numbering.NodeId;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.storage.serializers.Serializer;
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

import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Local implementation of XMLResource.
 */
public class LocalXMLResource extends AbstractEXistResource implements XMLResource {

    private NodeProxy proxy = null;

    private Properties outputProperties = null;
    private LexicalHandler lexicalHandler = null;

    // those are the different types of content this resource
    // may have to deal with
    protected String content = null;
    protected File file = null;
    protected InputSource inputSource = null;
    protected Node root = null;
    protected AtomicValue value = null;

    public LocalXMLResource(final Subject user, final BrokerPool brokerPool, final LocalCollection parent, final XmldbURI did) throws XMLDBException {
        super(user, brokerPool, parent, did, MimeType.XML_TYPE.getName());
    }

    public LocalXMLResource(final Subject user, final BrokerPool brokerPool, final LocalCollection parent, final NodeProxy p) throws XMLDBException {
        this(user, brokerPool, parent, p.getOwnerDocument().getFileURI());
        this.proxy = p;
    }

    @Override
    public String getDocumentId() throws XMLDBException {
        return docId.toString();
    }

    @Override
    public String getResourceType() throws XMLDBException {
        return XMLResource.RESOURCE_TYPE;
    }

    @Override
    public Object getContent() throws XMLDBException {
        if (content != null) {            
            return content;
        }

        // Case 1: content is an external DOM node
        else if (root != null && !(root instanceof NodeValue)) {
            final StringWriter writer = new StringWriter();
            final DOMSerializer serializer = new DOMSerializer(writer, getProperties());
            try {
                    serializer.serialize(root);
                    content = writer.toString();
            } catch (final TransformerException e) {
                    throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, e.getMessage(), e);
            }
            return content;

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
                final Serializer serializer = broker.getSerializer();
                serializer.setUser(user);

                try {
                    serializer.setProperties(getProperties());

                    if (root != null) {
                        return serializer.serialize((NodeValue) root);
                    } else if (proxy != null) {
                        return serializer.serialize(proxy);
                    } else {
                        return this.<String>read(broker, transaction).apply((document, broker1, transaction1) -> {
                            try {
                                return serializer.serialize(document);
                            } catch (final SAXException e) {
                                throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
                            }
                        });
                    }
                } catch (final SAXException e) {
                    throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
                }
            });
            return content;
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
     * This is done by providing a proxy object that only implements
     * the appropriate W3C DOM interface. This helps prevent the
     * XML:DB Local API from leaking implementation through
     * its abstractions.
     */
    private Node exportInternalNode(final Node node) {
        final Optional<Class<? extends Node>> domClazz = getW3cNodeInterface(node.getClass());
        if(!domClazz.isPresent()) {
            throw new IllegalArgumentException("Provided node does not implement org.w3c.dom");
        }

        final Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(domClazz.get());
        enhancer.setCallback(new MethodInterceptor() {
            @Override
            public Object intercept(final Object obj, final Method method, final Object[] args, final MethodProxy proxy) throws Throwable {

                final Object domResult = method.invoke(node, args);

                if(domResult != null && Node.class.isAssignableFrom(method.getReturnType())) {
                    return exportInternalNode((Node) domResult); //recursively wrap node result

                } else if(domResult != null && method.getReturnType().equals(NodeList.class)) {
                    final NodeList underlying = (NodeList)domResult; //recursively wrap nodes in nodelist result
                    return new NodeList() {
                        @Override
                        public Node item(final int index) {
                            return Optional.ofNullable(underlying.item(index))
                                    .map(n -> exportInternalNode(n))
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
        });

        return (Node)enhancer.create();
    }

    private Optional<Class<? extends Node>> getW3cNodeInterface(final Class<? extends Node> nodeClazz) {
        return Stream.of(nodeClazz.getInterfaces())
                .filter(iface -> iface.getPackage().getName().equals("org.w3c.dom"))
                .findFirst()
                .map(c -> (Class<? extends Node>)c);
    }

    @Override
    public void getContentAsSAX(final ContentHandler handler) throws XMLDBException {

        // case 1: content is an external DOM node
        if (root != null && !(root instanceof NodeValue)) {
            try {
                final String option = collection.getProperty(Serializer.GENERATE_DOC_EVENTS, "false");
                final DOMStreamer streamer = (DOMStreamer) SerializerPool.getInstance().borrowObject(DOMStreamer.class);
                streamer.setContentHandler(handler);
                streamer.setLexicalHandler(lexicalHandler);
                streamer.serialize(root, option.equalsIgnoreCase("true"));
                SerializerPool.getInstance().returnObject(streamer);
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
                        final Serializer serializer = broker.getSerializer();
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
                                } catch(final SAXException e) {
                                    throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
                                }
                            });
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
     * @exception XMLDBException with expected error codes. <br />
     *     <code>ErrorCodes.VENDOR_ERROR</code> for any vendor specific errors
     *     that occur. <br />
     */
    @Override
    public void setContent(final Object obj) throws XMLDBException {
        content = null;
        file = null;
        value = null;
        inputSource = null;
        root = null;

        if (obj instanceof File) {
            file = (File) obj;
        } else if (obj instanceof AtomicValue) {
            value = (AtomicValue) obj;
        } else if (obj instanceof InputSource) {
            inputSource=(InputSource) obj;
        } else if (obj instanceof byte[]) {
            content = new String((byte[])obj, UTF_8);
        } else {
            content = obj.toString();
        }
    }

    @Override
    public void setContentAsDOM(final Node root) throws XMLDBException {
        if (root instanceof AttrImpl) {
            throw new XMLDBException(ErrorCodes.WRONG_CONTENT_TYPE, "SENR0001: can not serialize a standalone attribute");
        }

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
    public void freeResources() throws XMLDBException {
        //dO nothing
        //TODO consider unifying close() code into freeResources()
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

    protected void setProperties(final Properties properties) {
        this.outputProperties = properties;
    }

    private Properties getProperties() {
        return outputProperties == null ? collection.getProperties(): outputProperties;
    }

    public NodeProxy getNode() throws XMLDBException {
        if(proxy != null) {
            return proxy;
        } else {
            return read((document, broker, transaction) -> new NodeProxy(document, NodeId.DOCUMENT_NODE));
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
}
