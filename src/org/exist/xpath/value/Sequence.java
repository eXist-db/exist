/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
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
package org.exist.xpath.value;

import org.exist.dom.NodeSet;
import org.exist.xpath.XPathException;

/**
 * This interface represents a sequence as defined in the XPath 2.0 specification.
 * 
 * A sequence is a sequence of items. Each item is either an atomic value or a
 * node. A single item is also a sequence, containing only the item. The base classes for 
 * {@link org.exist.xpath.value.AtomicValue atomic values} and {@link org.exist.dom.NodeProxy
 * nodes} thus implement the Sequence interface.
 * 
 * Also, a {@link org.exist.dom.NodeSet node set} is a special type of sequence, where all 
 * items are of type node.  
 */
public interface Sequence {
	
	/**
	 * Constant representing an empty sequence, i.e. a sequence with no item.
	 */
	public final static Sequence EMPTY_SEQUENCE = new EmptySequence();
	
	/**
	 * Add an item to the current sequence. An {@link XPathException} may be thrown
	 * if the item's type is incompatible with this type of sequence (e.g. if the sequence
	 * is a node set).
	 * 
	 * The sequence may or may not allow duplicate values.
	 * 
	 * @param item
	 * @throws XPathException
	 */
	public void add(Item item) throws XPathException;
	
	/**
	 * Add all items of the other sequence to this item. An {@link XPathException} may
	 * be thrown if the type of the items in the other sequence is incompatible with
	 * the primary type of this sequence.
	 * 
	 * @param other
	 * @throws XPathException
	 */
	public void addAll(Sequence other) throws XPathException;
	
	/**
	 * Return the primary type to which all items in this sequence belong. This is
	 * {@link org.exist.xpath.value.Type#NODE} for node sets, {@link Type#ITEM}
	 * for other sequences with mixed items.
	 * 
	 * @return the primary type of the items in this sequence.
	 */
	public int getItemType();
	
	/**
	 * Returns an iterator over all items in the sequence.
	 * 
	 * @return
	 */
	public SequenceIterator iterate();
	
	/**
	 * Returns the number of items contained in the sequence.
	 * @return
	 */
	public int getLength();
	
	/**
	 * Returns the item located at the specified position within
	 * this sequence. Items are counted beginning at 0.
	 * 
	 * @param pos
	 * @return
	 */
	public Item itemAt(int pos);
	
	/**
	 * Try to convert the sequence into an atomic value. The target type should be specified by
	 * using one of the constants defined in class {@link Type}. An {@link XPathException}
	 * is thrown if the conversion is impossible.
	 * 
	 * @param requiredType one of the type constants defined in class {@link Type}
	 * @return
	 * @throws XPathException
	 */
	public AtomicValue convertTo(int requiredType) throws XPathException;
	
	/**
	 * Convert the sequence to a string.
	 * 
	 * @return
	 */
	public String getStringValue() throws XPathException;
	
	/**
	 * Get the effective boolean value of this sequence. Will be false if the sequence is empty,
	 * true otherwise.
	 * 
	 * @return
	 * @throws XPathException
	 */
	public boolean effectiveBooleanValue() throws XPathException;
	
	/**
	 * Convert the sequence into a NodeSet. If the sequence contains items
	 * which are not nodes, an XPathException is thrown.
	 * @return
	 * @throws XPathException if the sequence contains items which are not nodes.
	 */
	public NodeSet toNodeSet() throws XPathException;
	
	public int conversionPreference(Class javaClass);
	
	public Object toJavaObject(Class target) throws XPathException;
}
