/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
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

import java.util.Iterator;
import java.util.List;

import org.exist.dom.AVLTreeNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.memtree.MemTreeBuilder;
import org.exist.memtree.NodeImpl;
import org.exist.memtree.DocumentBuilderReceiver;
import org.exist.util.serializer.DOMStreamer;
import org.exist.util.serializer.DOMStreamerPool;
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

public class XPathUtil {

    /**
     * Convert Java object to an XQuery sequence. Objects of type Sequence are
     * directly returned, other objects are converted into the corresponding
     * internal types.
     * 
     * @param obj
     * @return
     * @throws XPathException
     */
    public final static Sequence javaObjectToXPath(Object obj)
            throws XPathException {
        if (obj == null)
            return Sequence.EMPTY_SEQUENCE;
        if (obj instanceof Sequence)
            return (Sequence) obj;
        else if (obj instanceof String)
            return new StringValue((String) obj);
        else if (obj instanceof Boolean)
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
        else if (obj instanceof Node) {
            DOMStreamer streamer = DOMStreamerPool.getInstance()
                    .borrowDOMStreamer();
            try {
                MemTreeBuilder builder = new MemTreeBuilder();
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
                DOMStreamerPool.getInstance().returnDOMStreamer(streamer);
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
                seq.add((Item) javaObjectToXPath(i.next()));
            }
            return seq;
        } else if (obj instanceof NodeList) {
            DOMStreamer streamer = DOMStreamerPool.getInstance()
                    .borrowDOMStreamer();
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
                DOMStreamerPool.getInstance().returnDOMStreamer(streamer);
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
                seq.add((Item) javaObjectToXPath(array[i]));
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
}
