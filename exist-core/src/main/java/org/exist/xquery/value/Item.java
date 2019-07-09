/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU Library General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 *  $Id$
 */
package org.exist.xquery.value;

import org.exist.dom.memtree.DocumentBuilderReceiver;
import org.exist.dom.persistent.NodeHandle;
import org.exist.numbering.NodeId;
import org.exist.storage.DBBroker;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.util.Properties;

/**
 * This class represents an item in a sequence as defined by the XPath 2.0 specification.
 * Every item is either an {@link org.exist.xquery.value.AtomicValue atomic value} or
 * a {@link org.exist.dom.persistent.NodeProxy node}.
 *
 * @author wolf
 */
public interface Item {

    /**
     * Return the type of this item according to the type constants defined in class
     * {@link Type}.
     *
     * @return constant indicating the type: {@link Type}
     */
    int getType();

    /**
     * Return the string value of this item (see the definition of string value in XPath).
     *
     * @return string value
     * @throws XPathException on dynamic errors
     */
    String getStringValue() throws XPathException;

    /**
     * Convert this item into a sequence, containing only the item.
     *
     * @return item converted to sequence
     */
    Sequence toSequence();

    /**
     * Clean up any resources used by the items in this sequence.
     *
     * @param context current context
     * @param contextSequence the sequence to clean up
     */
    void destroy(XQueryContext context, Sequence contextSequence);

    /**
     * Convert this item into an atomic value, whose type corresponds to
     * the specified target type. requiredType should be one of the type
     * constants defined in {@link Type}. An {@link XPathException} is thrown
     * if the conversion is impossible.
     *
     * @param requiredType the required type, see {@link Type}
     * @return the converted value
     * @throws XPathException in case of a dynamic error
     */
    AtomicValue convertTo(int requiredType) throws XPathException;

    AtomicValue atomize() throws XPathException;

    void toSAX(DBBroker broker, ContentHandler handler, Properties properties) throws SAXException;

    void copyTo(DBBroker broker, DocumentBuilderReceiver receiver) throws SAXException;

    int conversionPreference(Class<?> javaClass);

    <T> T toJavaObject(Class<T> target) throws XPathException;

    /**
     * Nodes may implement this method to be informed of storage address
     * and node id changes after updates.
     *
     * @param oldNodeId the old node id
     * @param newNode the new node
     * @see org.exist.storage.UpdateListener
     */
    void nodeMoved(NodeId oldNodeId, NodeHandle newNode);  //TODO why is this here, it only pertains to Peristent nodes and NOT also in-memory nodes
}
