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

	/** To retain compatibility with eXist versions before september 20th 2005 ,
	 * for conversion to boolean;
	 * @see http://cvs.sourceforge.net/viewcvs.py/exist/eXist-1.0/src/org/exist/xquery/value/AbstractSequence.java?r1=1.11&r2=1.12 */
	private static final boolean OLD_EXIST_VERSION_COMPATIBILITY = false;
	
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
    	StringBuffer buf = new StringBuffer();
    	buf.append("(");
    	boolean gotOne = false;
    	for(SequenceIterator i = iterate(); i.hasNext(); ) {
    		if (gotOne)
    			buf.append(", ");
    		buf.append(i.nextItem());
    		gotOne = true;
    	}
    	buf.append(")");
    	return buf.toString();
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
    
	/** See
	 * <a <href="http://www.w3.org/TR/xquery/#id-ebv">2.4.3 Effective Boolean Value</a>
	 * @see org.exist.xquery.value.Sequence#effectiveBooleanValue()
	 */
	public boolean effectiveBooleanValue() throws XPathException {
		int len = getLength();
		if (len == 0)
			return false;

		if ( OLD_EXIST_VERSION_COMPATIBILITY )
			if (len > 1)
				return true;

		Item first = itemAt(0);
		
		// If operand is a sequence whose first item is a node, fn:boolean returns true.		
		if (Type.subTypeOf(first.getType(), Type.NODE )) {
			return true;
		}

		if ( ! OLD_EXIST_VERSION_COMPATIBILITY )
			if (len > 1)
			throw new XPathException(
				"error FORG0006: effectiveBooleanValue: first item of '" + 
                (toString().length() < 20 ? toString() : toString().substring(0, 20)+ "...") + 
                            "' is not a node, and sequence length > 1");

		// If $arg is a singleton value of type xs:boolean or a derived from xs:boolean, fn:boolean returns $arg.
		if(first instanceof StringValue)
			return ((StringValue)first).effectiveBooleanValue();
		else if(first instanceof BooleanValue)
			return ((BooleanValue)first).getValue();
		else if(first instanceof NumericValue)
			return ((NumericValue)first).effectiveBooleanValue();
		else {
			if ( OLD_EXIST_VERSION_COMPATIBILITY )
				return true;
			// In all other cases, fn:boolean raises a type error [err:FORG0006].
			throw new XPathException(
				"error FORG0006: effectiveBooleanValue: sequence of length 1, but not castable to a number or Boolean");
		}
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
		if(Sequence.class.isAssignableFrom(target)) {
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
	
	public void clearContext(int contextId) {
		Item next;
		for (SequenceIterator i = unorderedIterator(); i.hasNext(); ) {
			next = i.nextItem();
			if (next instanceof NodeProxy)
				((NodeProxy)next).clearContext(contextId);
		}
	}
	
	public void setSelfAsContext(int contextId) {
		Item next;
        NodeValue node;
		for (SequenceIterator i = unorderedIterator(); i.hasNext();) {
			next = i.nextItem();
			if(Type.subTypeOf(next.getType(), Type.NODE)) {
                node = (NodeValue) next;
                node.addContextNode(contextId, node);
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
    
    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#isPersistentSet()
     */
    public boolean isPersistentSet() {
        // always return false by default
        return false;
    }
}
