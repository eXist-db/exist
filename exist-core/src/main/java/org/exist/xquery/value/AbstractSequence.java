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
package org.exist.xquery.value;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.collections.Collection;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.EmptyNodeSet;
import org.exist.dom.persistent.NodeHandle;
import org.exist.dom.persistent.NodeProxy;
import org.exist.numbering.NodeId;
import org.exist.xquery.*;

import javax.annotation.Nullable;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * An abstract implementation of {@link org.exist.xquery.value.Sequence} with
 * default implementations for some methods.
 */
public abstract class AbstractSequence implements Sequence {

    /**
     * To retain compatibility with eXist versions before september 20th 2005 ,
     * for conversion to boolean;
     *
     * {@see http://cvs.sourceforge.net/viewcvs.py/exist/eXist-1.0/src/org/exist/xquery/value/AbstractSequence.java?r1=1.11&r2=1.12}
     */
    private static final boolean OLD_EXIST_VERSION_COMPATIBILITY = false;

    protected boolean isEmpty = true;
    protected boolean hasOne = false;

    private static final Logger BASE_LOG = LogManager.getLogger(AbstractSequence.class);

    @Override
    public Cardinality getCardinality() {
        if (isEmpty()) {
            return Cardinality.EMPTY_SEQUENCE;
        }
        if (hasOne()) {
            return Cardinality.EXACTLY_ONE;
        }
        if (hasMany()) {
            return Cardinality._MANY;
        }
        throw new IllegalArgumentException("Illegal argument");
    }

    @Override
    public AtomicValue convertTo(final int requiredType) throws XPathException {
        if(isEmpty()) {
            return null;
        }

        final Item first = itemAt(0);
        if (Type.subTypeOf(first.getType(), Type.ANY_ATOMIC_TYPE)) {
            return first.convertTo(requiredType);
        } else {
            //TODO : clean atomization
            return new StringValue(first.getStringValue()).convertTo(requiredType);
        }
    }

    @Override
    public boolean hasMany() {
        return !isEmpty() && !hasOne();
    }

    @Override
    public Sequence tail() throws XPathException {
        final ValueSequence tmp = new ValueSequence(getItemCount() - 1);
        final SequenceIterator iterator = iterate();
        iterator.nextItem();
        while (iterator.hasNext()) {
            final Item item = iterator.nextItem();
            tmp.add(item);
        }
        return tmp;
    }

    @Override
    public String getStringValue() throws XPathException {
        if (isEmpty()) {
            return "";
        }
        final Item first = itemAt(0);
        return first.getStringValue();
    }

    @Override
    public String toString() {
        try {
            final StringBuilder buf = new StringBuilder();
            buf.append("(");
            boolean gotOne = false;
            for (final SequenceIterator i = iterate(); i.hasNext(); ) {
                if (gotOne) {
                    buf.append(", ");
                }
                buf.append(i.nextItem());
                gotOne = true;
            }
            buf.append(")");
            return buf.toString();
        } catch (final XPathException e) {
            return "toString() fails: " + e.getMessage();
        }
    }

    @Override
    public void addAll(final Sequence other) throws XPathException {
        for (final SequenceIterator i = other.iterate(); i.hasNext(); ) {
            add(i.nextItem());
        }
    }

    @Override
    public DocumentSet getDocumentSet() {
        return DocumentSet.EMPTY_DOCUMENT_SET;
    }

    @Override
    public Iterator<Collection> getCollectionIterator() {
        return EmptyNodeSet.EMPTY_COLLECTION_ITERATOR;
    }

    @Override
    public void nodeMoved(final NodeId oldNodeId, final NodeHandle newNode) {
        //Nothing to do
    }

    /**
     * See
     * <a href="http://www.w3.org/TR/xquery/#id-ebv">2.4.3 Effective Boolean Value</a>
     *
     * @see org.exist.xquery.value.Sequence#effectiveBooleanValue()
     */
    @Override
    public boolean effectiveBooleanValue() throws XPathException {
        if (isEmpty()) {
            return false;
        }
        final Item first = itemAt(0);
        //If its operand is a sequence whose first item is a node, fn:boolean returns true.		
        if (Type.subTypeOf(first.getType(), Type.NODE)) {
            return true;
        }
        if (hasMany()) {
            if (OLD_EXIST_VERSION_COMPATIBILITY) {
                return true;
            } else {
                throw new XPathException((Expression) null, ErrorCodes.FORG0006,
                        "effectiveBooleanValue: first item of '" +
                                (toString().length() < 20 ? toString() : toString().substring(0, 20) + "...") +
                                "' is not a node, and sequence length > 1");
            }
        }
        //From now, we'll work with singletons...
        //Not sure about this one : does it mean than any singleton, including false() and 0 will return true ?
        if (OLD_EXIST_VERSION_COMPATIBILITY) {
            return true;
        } else {
            return ((AtomicValue) first).effectiveBooleanValue();
        }
    }

    @Override
    public int conversionPreference(final Class<?> javaClass) {
        if (javaClass.isAssignableFrom(Sequence.class)) {
            return 0;
        } else if (javaClass.isAssignableFrom(List.class) || javaClass.isArray()) {
            return 1;
        } else if (javaClass == Object.class) {
            return 20;
        }
        if (!isEmpty()) {
            return itemAt(0).conversionPreference(javaClass);
        }
        return Integer.MAX_VALUE;
    }

    @Override
    public <T> T toJavaObject(final Class<T> target) throws XPathException {
        if (Sequence.class.isAssignableFrom(target)) {
            return (T) this;
        } else if (target.isArray()) {
            final Class<?> componentType = target.getComponentType();
            // assume single-dimensional, then double-check that instance really matches desired type
            final Object array = Array.newInstance(componentType, getItemCount());
            if (!target.isInstance(array)) {
                return null;
            }
            int index = 0;
            for (final SequenceIterator i = iterate(); i.hasNext(); index++) {
                final Item item = i.nextItem();
                final Object obj = item.toJavaObject(componentType);
                Array.set(array, index, obj);
            }
            return (T) array;
        } else if (target.isAssignableFrom(List.class)) {
            final List<Item> l = new ArrayList<>(getItemCount());
            for (final SequenceIterator i = iterate(); i.hasNext(); ) {
                l.add(i.nextItem());
            }
            return (T) l;
        }
        if (!isEmpty()) {
            return itemAt(0).toJavaObject(target);
        }
        return null;
    }

    @Override
    public void clearContext(final int contextId) throws XPathException {
        for (final SequenceIterator i = unorderedIterator(); i.hasNext(); ) {
            final Item next = i.nextItem();
            if (next instanceof NodeProxy) {
                ((NodeProxy) next).clearContext(contextId);
            }
        }
    }

    @Override
    public void setSelfAsContext(final int contextId) throws XPathException {
        for (final SequenceIterator i = unorderedIterator(); i.hasNext(); ) {
            final Item next = i.nextItem();
            if (Type.subTypeOf(next.getType(), Type.NODE)) {
                final NodeValue node = (NodeValue) next;
                node.addContextNode(contextId, node);
            }
        }
    }

    @Override
    public boolean isCached() {
        // always return false by default
        return false;
    }

    @Override
    public void setIsCached(final boolean cached) {
        // ignore by default
    }

    @Override
    public boolean isPersistentSet() {
        // always return false by default
        return false;
    }

    @Override
    public boolean isCacheable() {
        return false;
    }


    @Override
    public int getState() {
        return 0;
    }

    @Override
    public boolean hasChanged(final int previousState) {
        return true;
    }

    @Override
    public void destroy(final XQueryContext context, @Nullable final Sequence contextSequence) {
        // do nothing by default
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || this.getClass() != other.getClass()) {
            return false;
        }

        final AbstractSequence that = (AbstractSequence) other;
        if (this.isEmpty && that.isEmpty) {
            return true;
        }

        try {
            final SequenceIterator thisIterator = iterate();
            final SequenceIterator thatIterator = that.iterate();
            while (thisIterator.hasNext() && thatIterator.hasNext()) {
                final Item thisItem = thisIterator.nextItem();
                final Item thatItem = thatIterator.nextItem();
                if (!thisItem.equals(thatItem)) {
                    return false;
                }
            }

            if (thisIterator.hasNext() || thatIterator.hasNext()) {
                return false;
            }

        } catch (final XPathException e) {
            BASE_LOG.error(e.getMessage(), e);
            return false;  // best fallback option?
        }

        return true;
    }

    @Override
    public int hashCode() {
        int hashCode = 1;
        try {
            final SequenceIterator iterator = iterate();
            while (iterator.hasNext()) {
                final Item item = iterator.nextItem();
                hashCode = 31 * hashCode + item.hashCode();
            }
        } catch (final XPathException e) {
            BASE_LOG.error(e.getMessage(), e);
            hashCode = super.hashCode();  // best fallback option?
        }
        return hashCode;
    }
}
