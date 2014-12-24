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

import java.util.Properties;

import org.exist.dom.memtree.DocumentBuilderReceiver;
import org.exist.dom.persistent.NodeHandle;
import org.exist.numbering.NodeId;
import org.exist.storage.DBBroker;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

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
	 */
	public int getType();
	
	/**
	 * Return the string value of this item (see the definition of string value in XPath).
	 * 
	 */
	public String getStringValue() throws XPathException;
	
	/**
	 * Convert this item into a sequence, containing only the item.
	 *  
	 */
	public Sequence toSequence();

    /**
     * Clean up any resources used by the items in this sequence.
     */
    void destroy(XQueryContext context, Sequence contextSequence);

	/**
	 * Convert this item into an atomic value, whose type corresponds to
	 * the specified target type. requiredType should be one of the type
	 * constants defined in {@link Type}. An {@link XPathException} is thrown
	 * if the conversion is impossible.
	 * 
	 * @param requiredType
	 * @throws XPathException
	 */
	public AtomicValue convertTo(int requiredType) throws XPathException;
	
	public AtomicValue atomize() throws XPathException;
	
	public void toSAX(DBBroker broker, ContentHandler handler, Properties properties) throws SAXException;

	public void copyTo(DBBroker broker, DocumentBuilderReceiver receiver) throws SAXException;
	
	public int conversionPreference(Class<?> javaClass);
	
	public <T> T toJavaObject(Class<T> target) throws XPathException;

        /**
         * Nodes may implement this method to be informed of storage address
         * and node id changes after updates.
         *
         * @see org.exist.storage.UpdateListener
         *
         * @param oldNodeId
         * @param newNode
         */
        void nodeMoved(NodeId oldNodeId, NodeHandle newNode);  //TODO why is this here, it only pertains to Peristent nodes and NOT also in-memory nodes
}
