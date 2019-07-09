/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  meier@ifs.tu-darmstadt.de
 *  http://exist.sourceforge.net
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 * 
 *  $Id$
 */
package org.exist.xquery;

import java.net.URISyntaxException;
import java.util.List;

import org.exist.dom.persistent.AVLTreeNodeSet;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.memtree.DocumentBuilderReceiver;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.dom.memtree.NodeImpl;
import org.exist.numbering.NodeId;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.util.io.FastByteArrayInputStream;
import org.exist.util.serializer.DOMStreamer;
import org.exist.util.serializer.SerializerPool;
import org.exist.xmldb.LocalXMLResource;
import org.exist.xmldb.RemoteXMLResource;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.value.*;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xmldb.api.base.ResourceIterator;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

public class XPathUtil {

    /**
     * Convert Java object to an XQuery sequence. Objects of type Sequence are
     * directly returned, other objects are converted into the corresponding
     * internal types.
     *
     * @param obj The java object
     * @param context XQuery context
     * @return XQuery sequence
     * @throws XPathException in case of an error
     */
    public final static Sequence javaObjectToXPath(Object obj, XQueryContext context)
            throws XPathException {
        return javaObjectToXPath(obj, context, true);
    }

    public final static Sequence javaObjectToXPath(Object obj, XQueryContext context,
            boolean expandChars) throws XPathException {

        if (obj == null) {
            //return Sequence.EMPTY_SEQUENCE;
            return null;
        } else if (obj instanceof Sequence) {
            return (Sequence) obj;
        } else if (obj instanceof String) {
            final StringValue v = new StringValue((String) obj);
            return (expandChars ? v.expand() : v);
        } else if (obj instanceof Boolean) {
            return BooleanValue.valueOf(((Boolean) obj));
        } else if (obj instanceof Float) {
            return new FloatValue(((Float) obj));
        } else if (obj instanceof Double) {
            return new DoubleValue(((Double) obj));
        } else if (obj instanceof Short) {
            return new IntegerValue(((Short) obj), Type.SHORT);
        } else if (obj instanceof Integer) {
            return new IntegerValue(((Integer) obj), Type.INT);
        } else if (obj instanceof Long) {
            return new IntegerValue(((Long) obj), Type.LONG);
        } else if (obj instanceof byte[]) {
            return BinaryValueFromInputStream.getInstance(context, new Base64BinaryValueType(), new FastByteArrayInputStream((byte[]) obj));

        } else if (obj instanceof ResourceSet) {
            final Sequence seq = new AVLTreeNodeSet();
            try {
                final DBBroker broker = context.getBroker();
                for (final ResourceIterator it = ((ResourceSet) obj).getIterator(); it.hasMoreResources();) {
                    seq.add(getNode(broker, (XMLResource) it.nextResource()));
                }
            } catch (final XMLDBException xe) {
                throw new XPathException("Failed to convert ResourceSet to node: " + xe.getMessage());
            }
            return seq;

        } else if (obj instanceof XMLResource) {
            return getNode(context.getBroker(), (XMLResource) obj);

        } else if (obj instanceof Node) {
            final DOMStreamer streamer = (DOMStreamer) SerializerPool.getInstance().borrowObject(DOMStreamer.class);
            try {
                final MemTreeBuilder builder = new MemTreeBuilder(context);
                builder.startDocument();
                final DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(builder);
                streamer.setContentHandler(receiver);
                streamer.serialize((Node) obj, false);
                if(obj instanceof Document) {
                    return builder.getDocument();
                } else {
                    return builder.getDocument().getNode(1);
                }
            } catch (final SAXException e) {
                throw new XPathException(
                        "Failed to transform node into internal model: "
                        + e.getMessage());
            } finally {
                SerializerPool.getInstance().returnObject(streamer);
            }

        } else if (obj instanceof List<?>) {
            boolean createNodeSequence = true;

            for (Object next : ((List<?>) obj)) {
                if (!(next instanceof NodeProxy)) {
                    createNodeSequence = false;
                }
            }
            Sequence seq = createNodeSequence ? new AVLTreeNodeSet() : new ValueSequence();
            for (Object o : ((List<?>) obj)) {
                seq.add((Item) javaObjectToXPath(o, context, expandChars));
            }
            return seq;

        } else if (obj instanceof NodeList) {
            final DOMStreamer streamer = (DOMStreamer) SerializerPool.getInstance().borrowObject(DOMStreamer.class);
            try {
                final MemTreeBuilder builder = new MemTreeBuilder();
                builder.startDocument();
                final DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(builder);
                streamer.setContentHandler(receiver);
                final ValueSequence seq = new ValueSequence();
                final NodeList nl = (NodeList) obj;
                int last = builder.getDocument().getLastNode();
                for (int i = 0; i < nl.getLength(); i++) {
                    final Node n = nl.item(i);
                    streamer.serialize(n, false);
                    final NodeImpl created = builder.getDocument().getNode(last + 1);
                    seq.add(created);
                    last = builder.getDocument().getLastNode();
                }
                return seq;
            } catch (final SAXException e) {
                throw new XPathException(
                        "Failed to transform node into internal model: "
                        + e.getMessage());
            } finally {
                SerializerPool.getInstance().returnObject(streamer);
            }

        } else if (obj instanceof Object[]) {
            boolean createNodeSequence = true;
            final Object[] array = (Object[]) obj;
            for (Object arrayItem : array) {
                if (!(arrayItem instanceof NodeProxy)) {
                    createNodeSequence = false;
                }
            }

            Sequence seq = createNodeSequence ? new AVLTreeNodeSet() : new ValueSequence();
            for (Object arrayItem : array) {
                seq.add((Item) javaObjectToXPath(arrayItem, context, expandChars));
            }
            return seq;

        } else {
            return new JavaObjectValue(obj);
        }
    }

    public final static int javaClassToXPath(Class<?> clazz) {
        if (clazz == String.class) {
            return Type.STRING;
        } else if (clazz == Boolean.class || clazz == boolean.class) {
            return Type.BOOLEAN;
        } else if (clazz == Integer.class || clazz == int.class
                || clazz == Long.class || clazz == long.class
                || clazz == Short.class || clazz == short.class
                || clazz == Byte.class || clazz == byte.class) {
            return Type.INTEGER;
        } else if (clazz == Double.class || clazz == double.class) {
            return Type.DOUBLE;
        } else if (clazz == Float.class || clazz == float.class) {
            return Type.FLOAT;
        } else if (clazz.isAssignableFrom(Node.class)) {
            return Type.NODE;
        } else {
            return Type.JAVA_OBJECT;
        }
    }

    /**
     * Converts an XMLResource into a NodeProxy.
     *
     * @param broker The DBBroker to use to access the database
     * @param xres The XMLResource to convert
     * @return A NodeProxy for accessing the content represented by xres
     * @throws XPathException if an XMLDBException is encountered
     */
    public static final NodeProxy getNode(DBBroker broker, XMLResource xres) throws XPathException {
        if (xres instanceof LocalXMLResource) {
            final LocalXMLResource lres = (LocalXMLResource) xres;
            try {
                return lres.getNode();
            } catch (final XMLDBException xe) {
                throw new XPathException("Failed to convert LocalXMLResource to node: " + xe.getMessage());
            }
        }

        DocumentImpl document;
        try {
            document = broker.getCollection(XmldbURI.xmldbUriFor(xres.getParentCollection().getName())).getDocument(broker, XmldbURI.xmldbUriFor(xres.getDocumentId()));
        } catch (final URISyntaxException xe) {
            throw new XPathException(xe);
        } catch (final XMLDBException xe) {
            throw new XPathException("Failed to get document for RemoteXMLResource: " + xe.getMessage());
        } catch (final PermissionDeniedException pde) {
            throw new XPathException("Failed to get document: " + pde.getMessage());
        }
        final NodeId nodeId = broker.getBrokerPool().getNodeFactory().createFromString(((RemoteXMLResource) xres).getNodeId());
        return new NodeProxy(document, nodeId);

    }
}
