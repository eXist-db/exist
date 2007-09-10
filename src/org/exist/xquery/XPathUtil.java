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
import java.util.Iterator;
import java.util.List;

import org.exist.dom.AVLTreeNodeSet;
import org.exist.dom.DocumentImpl;
import org.exist.dom.NodeProxy;
import org.exist.memtree.DocumentBuilderReceiver;
import org.exist.memtree.MemTreeBuilder;
import org.exist.memtree.NodeImpl;
import org.exist.numbering.NodeId;
import org.exist.storage.DBBroker;
import org.exist.util.serializer.DOMStreamer;
import org.exist.util.serializer.SerializerPool;
import org.exist.xmldb.LocalXMLResource;
import org.exist.xmldb.RemoteXMLResource;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.DoubleValue;
import org.exist.xquery.value.FloatValue;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;
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
     * @param obj
     * @return XQuery sequence
     * @throws XPathException
     */
	public final static Sequence javaObjectToXPath(Object obj, XQueryContext context)
    throws XPathException {
		return javaObjectToXPath(obj, context, true);
	}
	
    public final static Sequence javaObjectToXPath(Object obj, XQueryContext context, 
    		boolean expandChars) throws XPathException {
        if (obj == null)
            //return Sequence.EMPTY_SEQUENCE;
        	return null;
        if (obj instanceof Sequence)
            return (Sequence) obj;
        else if (obj instanceof String) {
            StringValue v = new StringValue((String) obj);
            return (expandChars ? v.expand() : v);
        } else if (obj instanceof Boolean)
            return BooleanValue.valueOf(((Boolean) obj).booleanValue());
        else if (obj instanceof Float)
            return new FloatValue(((Float) obj).floatValue());
        else if (obj instanceof Double)
            return new DoubleValue(((Double) obj).doubleValue());
        else if (obj instanceof Short)
            return new IntegerValue(((Short) obj).shortValue(), Type.SHORT);
        else if (obj instanceof Integer)
            return new IntegerValue(((Integer) obj).intValue(), Type.INT);
        else if (obj instanceof Long)
            return new IntegerValue(((Long) obj).longValue(), Type.LONG);
        else if (obj instanceof ResourceSet) {
            Sequence seq = new AVLTreeNodeSet();
            try {
                DBBroker broker = context.getBroker();
                for(ResourceIterator it = ((ResourceSet)obj).getIterator(); it.hasMoreResources();) {
                    seq.add(getNode(broker, (XMLResource)it.nextResource()));
                }
            } catch (XMLDBException xe) {
                throw new XPathException("Failed to convert ResourceSet to node: " + xe.getMessage());
            }
            return seq;
        } else if (obj instanceof XMLResource) {
            return getNode(context.getBroker(), (XMLResource)obj);
        } else if (obj instanceof Node) {
            DOMStreamer streamer = (DOMStreamer) SerializerPool.getInstance().borrowObject(DOMStreamer.class);
            try {
                MemTreeBuilder builder = new MemTreeBuilder(context);
                builder.startDocument();
                DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(
                        builder);
                streamer.setContentHandler(receiver);
                streamer.serialize((Node) obj, false);
                return builder.getDocument().getNode(1);
            } catch (SAXException e) {
                throw new XPathException(
                        "Failed to transform node into internal model: "
                                + e.getMessage());
            } finally {
                SerializerPool.getInstance().returnObject(streamer);
            }
        } else if (obj instanceof List) {
            boolean createNodeSequence = true;
            Object next;
            for (Iterator i = ((List) obj).iterator(); i.hasNext();) {
                next = i.next();
                if (!(next instanceof NodeProxy))
                    createNodeSequence = false;
            }
            Sequence seq = null;
            if (createNodeSequence)
                seq = new AVLTreeNodeSet();
            else
                seq = new ValueSequence();
            for (Iterator i = ((List) obj).iterator(); i.hasNext();) {
                seq.add((Item) javaObjectToXPath(i.next(), context, expandChars));
            }
            return seq;
        } else if (obj instanceof NodeList) {
            DOMStreamer streamer = (DOMStreamer) SerializerPool.getInstance().borrowObject(DOMStreamer.class);
            try {
                MemTreeBuilder builder = new MemTreeBuilder();
                builder.startDocument();
                DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(
                        builder);
                streamer.setContentHandler(receiver);
                ValueSequence seq = new ValueSequence();
                NodeList nl = (NodeList) obj;
                int last = builder.getDocument().getLastNode();
                for (int i = 0; i < nl.getLength(); i++) {
                    Node n = nl.item(i);
                    streamer.serialize(n, false);
                    NodeImpl created = builder.getDocument().getNode(last + 1);
                    seq.add(created);
                    last = builder.getDocument().getLastNode();
                }
                return seq;
            } catch (SAXException e) {
                throw new XPathException(
                        "Failed to transform node into internal model: "
                                + e.getMessage());
            } finally {
                SerializerPool.getInstance().returnObject(streamer);
            }
        } else if (obj instanceof Object[]) {
            boolean createNodeSequence = true;
            Object[] array = (Object[]) obj;
            for (int i = 0; i < array.length; i++) {
                if (!(array[i] instanceof NodeProxy))
                    createNodeSequence = false;
            }
            Sequence seq = null;
            if (createNodeSequence)
                seq = new AVLTreeNodeSet();
            else
                seq = new ValueSequence();
            for (int i = 0; i < array.length; i++) {
                seq.add((Item) javaObjectToXPath(array[i], context, expandChars));
            }
            return seq;
        } else
            return new JavaObjectValue(obj);
    }

    public final static int javaClassToXPath(Class clazz) {
        if (clazz == String.class)
            return Type.STRING;
        else if (clazz == Boolean.class || clazz == boolean.class)
            return Type.BOOLEAN;
        else if (clazz == Integer.class || clazz == int.class
                || clazz == Long.class || clazz == long.class
                || clazz == Short.class || clazz == short.class
                || clazz == Byte.class || clazz == byte.class)
            return Type.INTEGER;
        else if (clazz == Double.class || clazz == double.class)
            return Type.DOUBLE;
        else if (clazz == Float.class || clazz == float.class)
            return Type.FLOAT;
        else if (clazz.isAssignableFrom(Node.class))
            return Type.NODE;
        else
            return Type.JAVA_OBJECT;
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
        if(xres instanceof LocalXMLResource) {
            LocalXMLResource lres = (LocalXMLResource)xres;
            try {
                return lres.getNode();
            } catch (XMLDBException xe) {
                throw new XPathException("Failed to convert LocalXMLResource to node: " + xe.getMessage());
            }
        }
        
        DocumentImpl document;
        try {
            document = broker.getCollection(XmldbURI.xmldbUriFor(xres.getParentCollection().getName())).getDocument(broker, XmldbURI.xmldbUriFor(xres.getDocumentId()));
        } catch (URISyntaxException xe) {
            throw new XPathException(xe);
        } catch (XMLDBException xe) {
            throw new XPathException("Failed to get document for RemoteXMLResource: " + xe.getMessage());
        }
        NodeId nodeId = broker.getBrokerPool().getNodeFactory().createFromString(((RemoteXMLResource)xres).getNodeId());
        return new NodeProxy(document, nodeId);
        
    }
}
