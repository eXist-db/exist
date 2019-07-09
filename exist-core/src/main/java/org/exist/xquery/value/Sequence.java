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

import org.exist.collections.Collection;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.NodeHandle;
import org.exist.dom.persistent.NodeSet;
import org.exist.numbering.NodeId;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;

import javax.annotation.Nullable;
import java.util.Iterator;

/**
 * This interface represents a sequence as defined in the XPath 2.0 specification.
 *
 * A sequence is a sequence of items. Each item is either an atomic value or a
 * node. A single item is also a sequence, containing only the item. The base classes for
 * {@link org.exist.xquery.value.AtomicValue atomic values} and {@link org.exist.dom.persistent.NodeProxy
 * nodes} thus implement the Sequence interface.
 *
 * Also, a {@link org.exist.dom.persistent.NodeSet node set} is a special type of sequence, where all
 * items are of type node.
 */
public interface Sequence {

    /**
     * Constant representing an empty sequence, i.e. a sequence with no item.
     */
    Sequence EMPTY_SEQUENCE = new EmptySequence();

    /**
     * The purpose of ordered and unordered flag is to set the ordering mode
     * in the static context to ordered or unordered for a certain region in a query.
     *
     * @param flag
     */
//	public void keepUnOrdered(boolean flag);

    /**
     * Add an item to the current sequence. An {@link XPathException} may be thrown
     * if the item's type is incompatible with this type of sequence (e.g. if the sequence
     * is a node set).
     *
     * The sequence may or may not allow duplicate values.
     *
     * @param item the item to add
     * @throws XPathException if an error occurs
     */
    void add(Item item) throws XPathException;

    /**
     * Add all items of the other sequence to this item. An {@link XPathException} may
     * be thrown if the type of the items in the other sequence is incompatible with
     * the primary type of this sequence.
     *
     * @param other the other sequence
     *
     * @throws XPathException if an error occurs
     */
    void addAll(Sequence other) throws XPathException;

    /**
     * Return the primary type to which all items in this sequence belong. This is
     * {@link org.exist.xquery.value.Type#NODE} for node sets, {@link Type#ITEM}
     * for other sequences with mixed items.
     *
     * @return the primary type of the items in this sequence.
     */
    int getItemType();

    /**
     * Returns an iterator over all items in the sequence. The
     * items are returned in document order where applicable.
     *
     * @return the iterator
     *
     * @throws XPathException if an error occurs
     */
    SequenceIterator iterate() throws XPathException;

    /**
     * Returns an iterator over all items in the sequence. The returned
     * items may - but need not - to be in document order.
     *
     * @return the iterator
     *
     * @throws XPathException if an error occurs
     */
    SequenceIterator unorderedIterator() throws XPathException;

    /**
     * Returns the number of items contained in the sequence.
     *
     * NOTE: this is just a legacy convenience
     *     for {@link #getItemCountLong()}.
     *
     * If the sequence has more items than {@link Integer#MAX_VALUE}
     * then this function will likely return a negative value,
     * at which point you should consider instead
     * using {@link #getItemCountLong()}.
     *
     * @return The number of items in the sequence.
     */
    default int getItemCount() {
        return (int) getItemCountLong();
    }

    /**
     * Returns the number of items contained in the sequence.
     *
     * Thought should be given before calling this method,
     * as for some sequence types this can be a
     * <strong>very</strong> expensive operation, whereas
     * for others it may be almost free!
     *
     * @return The number of items in the sequence.
     */
    long getItemCountLong();

    /**
     * Returns whether the sequence is empty or not.
     *
     * @return <code>true</code> is the sequence is empty
     */
    boolean isEmpty();

    /**
     * Returns whether the sequence has just one item or not.
     *
     * @return <code>true</code> is the sequence has just one item
     */
    boolean hasOne();

    /**
     * Returns whether the sequence more than one item or not.
     *
     * @return <code>true</code> is the sequence more than one item
     */
    boolean hasMany();

    /**
     * Explicitly remove all duplicate nodes from this sequence.
     */
    void removeDuplicates();

    /**
     * Returns the cardinality of this sequence. The returned
     * value is a combination of flags as defined in
     * {@link org.exist.xquery.Cardinality}.
     *
     * @return the cardinality
     *
     * @see org.exist.xquery.Cardinality
     */
    int getCardinality();

    /**
     * Returns the item located at the specified position within
     * this sequence. Items are counted beginning at 0.
     *
     * @param pos the position
     * @return the item at the position
     */
    Item itemAt(int pos);

    Sequence tail() throws XPathException;

    /**
     * Try to convert the sequence into an atomic value. The target type should be specified by
     * using one of the constants defined in class {@link Type}. An {@link XPathException}
     * is thrown if the conversion is impossible.
     *
     * @param requiredType one of the type constants defined in class {@link Type}
     *
     * @return the converted value or null
     *
     * @throws XPathException if an error occurs
     */
    @Nullable AtomicValue convertTo(int requiredType) throws XPathException;

    /**
     * Convert the sequence to a string.
     *
     * @return the string value
     * @throws XPathException in case of dynamic error
     */
    String getStringValue() throws XPathException;

    /**
     * Get the effective boolean value of this sequence. Will be false if the sequence is empty,
     * true otherwise.
     *
     * @return the effective boolean value
     *
     * @throws XPathException if an error occurs
     */
    boolean effectiveBooleanValue() throws XPathException;

    /**
     * Convert the sequence into a NodeSet. If the sequence contains items
     * which are not nodes, an XPathException is thrown.
     *
     * @return the node set
     *
     * @throws XPathException if the sequence contains items which are not nodes.
     */
    NodeSet toNodeSet() throws XPathException;

    /**
     * Convert the sequence into an in-memory node set. If the sequence contains
     * items which are not nodes, an XPathException is thrown. For persistent
     * node sets, this method will return null. Call {@link #isPersistentSet()} to check
     * if the sequence is a persistent node set.
     *
     * @return the in memory node set
     *
     * @throws XPathException if the sequence contains items which are not nodes or is
     *                        a persistent node set
     */
    MemoryNodeSet toMemNodeSet() throws XPathException;

    /**
     * Returns the set of documents from which the node items in this sequence
     * have been selected. This is for internal use only.
     *
     * @return the document set
     */
    DocumentSet getDocumentSet();

    /**
     * Return an iterator on all collections referenced by documents
     * contained in this sequence..
     *
     * @return the iterator
     */
    Iterator<Collection> getCollectionIterator();

    /**
     * Returns a preference indicator, indicating the preference of
     * a value to be converted into the given Java class. Low numbers mean
     * that the value can be easily converted into the given class.
     *
     * @param javaClass the java class
     *
     * @return the preference
     */
    int conversionPreference(Class<?> javaClass);

    /**
     * Convert the value into an instance of the specified
     * Java class.
     *
     * @param <T> the class to which the instance is converted
     * @param target the target class
     *
     * @return the Java object.
     *
     * @throws XPathException if an error occurs
     */
    <T> T toJavaObject(Class<T> target) throws XPathException;

    /**
     * Returns true if the sequence is the result of a previous operation
     * and has been cached.
     *
     * @return true if the sequence has been cached
     */
    boolean isCached();

    /**
     * Indicates that the sequence is the result of a previous operation
     * and has not been recomputed.
     *
     * @param cached true if the sequence should be cached
     */
    void setIsCached(boolean cached);

    /**
     * For every item in the sequence, clear any context-dependant
     * information that is stored during query processing. This
     * feature is used for node sets, which may store information
     * about their context node.
     *
     * @param contextId the context id
     *
     * @throws XPathException if an error occurs whilst clearing the context
     */
    void clearContext(int contextId) throws XPathException;

    void setSelfAsContext(int contextId) throws XPathException;

    boolean isPersistentSet();

    /**
     * Node sets may implement this method to be informed of storage address
     * and node id changes after updates.
     *
     * @param oldNodeId the old node id
     * @param newNode the new node
     */
    void nodeMoved(NodeId oldNodeId, NodeHandle newNode);  //TODO why is this here, it only pertains to Persistent nodes and NOT also in-memory nodes

    boolean isCacheable();

    int getState();

    boolean hasChanged(int previousState);

    /**
     * Clean up any resources used by the items in this sequence.
     *
     * @param context the XQuery context
     * @param contextSequence the context sequence
     */
    void destroy(XQueryContext context, Sequence contextSequence);
}
