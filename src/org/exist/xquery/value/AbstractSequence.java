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
package org.exist.xquery.value;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.xquery.Cardinality;
import org.exist.xquery.XPathException;

/**
 * An abstract implementation of {@link org.exist.xquery.value.Sequence} with
 * default implementations for some methods.
 */
public abstract class AbstractSequence implements Sequence {

	protected AbstractSequence() {
	}
	
	public abstract int getItemType();

	public abstract SequenceIterator iterate();

	public abstract SequenceIterator unorderedIterator();
	
	public abstract int getLength();

	public int getCardinality() {
		switch(getLength()) {
			case 0:
				return Cardinality.EMPTY;
			case 1:
				return Cardinality.EXACTLY_ONE;
			default:
				return Cardinality.ONE_OR_MORE;
		}
	}
	
	public AtomicValue convertTo(int requiredType) throws XPathException {
		Item first = itemAt(0);
		if(Type.subTypeOf(first.getType(), Type.ATOMIC))
			return ((AtomicValue)first).convertTo(requiredType);
		else
			return new StringValue(first.getStringValue()).convertTo(requiredType);
	}
	
	public String getStringValue() throws XPathException {
		if(getLength() == 0)
			return "";
		Item first = iterate().nextItem();
		return first.getStringValue();
	}
	
	public String toString() {
	    try {
            return getStringValue();
        } catch (XPathException e) {
            return super.toString();
        }
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#add(org.exist.xquery.value.Item)
	 */
	public abstract void add(Item item) throws XPathException;

	public void addAll(Sequence other) throws XPathException {
		for(SequenceIterator i = other.iterate(); i.hasNext(); )
			add(i.nextItem());
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#itemAt(int)
	 */
	public abstract Item itemAt(int pos);

	
    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#getDocumentSet()
     */
    public DocumentSet getDocumentSet() {
        return DocumentSet.EMPTY_DOCUMENT_SET;
    }
    
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#effectiveBooleanValue()
	 */
	public boolean effectiveBooleanValue() throws XPathException {
		int len = getLength();
		if (len == 0)
			return false;
		if (len > 1)
			return true;
		Item first = itemAt(0);
		if(first instanceof StringValue)
			return ((StringValue)first).effectiveBooleanValue();
		else if(first instanceof BooleanValue)
			return ((BooleanValue)first).getValue();
		else if(first instanceof NumericValue)
			return ((NumericValue)first).effectiveBooleanValue();
		else
			return true;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#conversionPreference(java.lang.Class)
	 */
	public int conversionPreference(Class javaClass) {
		if(javaClass.isAssignableFrom(Sequence.class))
			return 0;
		else if(javaClass.isAssignableFrom(List.class) || javaClass.isArray())
			return 1;
		else if(javaClass == Object.class)
			return 20;
		
		if(getLength() > 0)
			return itemAt(0).conversionPreference(javaClass);
			
		return Integer.MAX_VALUE;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#toJavaObject(java.lang.Class)
	 */
	public Object toJavaObject(Class target) throws XPathException {
		if(target.isAssignableFrom(Sequence.class)) {
			return this;
		} else if(target.isArray()) {
			Class componentType = target.getComponentType();
			// assume single-dimensional, then double-check that instance really matches desired type
			Object array = Array.newInstance(componentType, getLength());
			if (!target.isInstance(array)) return null;
			int index = 0;
			for(SequenceIterator i = iterate(); i.hasNext(); index++) {
				Item item = i.nextItem();
				Object obj = item.toJavaObject(componentType);
				Array.set(array, index, obj);
			}
			return array;
		} else if(target.isAssignableFrom(List.class)) {
			List l = new ArrayList(getLength());
			for(SequenceIterator i = iterate(); i.hasNext(); ) {
				l.add(i.nextItem());
			}
			return l;
		}
			
		if(getLength() > 0)
			return itemAt(0).toJavaObject(target);
		return null;
	}
	
	public void setSelfAsContext() {
		Item next;
		for (SequenceIterator i = unorderedIterator(); i.hasNext();) {
			next = i.nextItem();
			if(Type.subTypeOf(next.getType(), Type.NODE)) {
				if (next instanceof NodeProxy) {
					NodeProxy n = (NodeProxy)next;
					n.addContextNode(n);
				}
			}
		}
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#isCached()
	 */
	public boolean isCached() {
		// always return false by default
		return false;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#setIsCached(boolean)
	 */
	public void setIsCached(boolean cached) {
		// ignore by default
	}
}
