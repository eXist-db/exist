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

import java.text.Collator;

import org.exist.dom.DocumentSet;
import org.exist.dom.NodeSet;
import org.exist.memtree.DocumentBuilderReceiver;
import org.exist.storage.DBBroker;
import org.exist.xquery.Cardinality;
import org.exist.xquery.XPathException;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public abstract class AtomicValue implements Item, Sequence {

	public final static AtomicValue EMPTY_VALUE = new EmptyValue();

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#getType()
	 */
	public int getType() {
		return Type.ATOMIC;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#getStringValue()
	 */
	public abstract String getStringValue() throws XPathException;

	public abstract AtomicValue convertTo(int requiredType) throws XPathException;

	public abstract boolean compareTo(Collator collator, int operator, AtomicValue other)
		throws XPathException;

	public abstract int compareTo(Collator collator, AtomicValue other) throws XPathException;

	public abstract AtomicValue max(Collator collator, AtomicValue other) throws XPathException;

	public abstract AtomicValue min(Collator collator, AtomicValue other) throws XPathException;

	public boolean startsWith(Collator collator, AtomicValue other) throws XPathException {
		throw new XPathException("Cannot call starts-with on value of type " + 
				Type.getTypeName(getType()));
	}
	
	public boolean endsWith(Collator collator, AtomicValue other) throws XPathException {
		throw new XPathException("Cannot call ends-with on value of type " + 
				Type.getTypeName(getType()));
	}
	
	public boolean contains(Collator collator, AtomicValue other) throws XPathException {
		throw new XPathException("Cannot call contains on value of type " + 
				Type.getTypeName(getType()));
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#getLength()
	 */
	public int getLength() {
		return 1;
	}

	public int getCardinality() {
		return Cardinality.EXACTLY_ONE;
	}
	
    public void removeDuplicates() {
        // this is a single value, so there are no duplicates to remove
    }
    
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#iterate()
	 */
	public SequenceIterator iterate() {
		return new SingleItemIterator(this);
	}

	public SequenceIterator unorderedIterator() {
		return new SingleItemIterator(this);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#getItemType()
	 */
	public int getItemType() {
		return getType();
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#itemAt(int)
	 */
	public Item itemAt(int pos) {
		return pos > 0 ? null : this;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#toSequence()
	 */
	public Sequence toSequence() {
		return this;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#toSAX(org.exist.storage.DBBroker, org.xml.sax.ContentHandler)
	 */
	public void toSAX(DBBroker broker, ContentHandler handler) throws SAXException {
		try {
			final String s = getStringValue();
			handler.characters(s.toCharArray(), 0, s.length());
		} catch (XPathException e) {
			throw new SAXException(e);
		}
	}

	public void copyTo(DBBroker broker, DocumentBuilderReceiver receiver) throws SAXException {
		try {
			final String s = getStringValue();
			receiver.characters(s);
		} catch (XPathException e) {
			throw new SAXException(e);
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#add(org.exist.xquery.value.Item)
	 */
	public void add(Item item) throws XPathException {
	}

	public void addAll(Sequence other) throws XPathException {
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#atomize()
	 */
	public AtomicValue atomize() throws XPathException {
		return this;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#effectiveBooleanValue()
	 */
	public boolean effectiveBooleanValue() throws XPathException {
		return getStringValue().length() > 0;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#toNodeSet()
	 */
	public NodeSet toNodeSet() throws XPathException {
		throw new XPathException(
			"cannot convert value of type "
				+ Type.getTypeName(getType())
				+ " to a node set");
	}

	
    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#getDocumentSet()
     */
    public DocumentSet getDocumentSet() {
        return DocumentSet.EMPTY_DOCUMENT_SET;
    }
    
	public String pprint() {
		try {
			return getStringValue();
		} catch (XPathException e) {
			return "";
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#conversionPreference(java.lang.Class)
	 */
	public int conversionPreference(Class javaClass) {
		return Integer.MAX_VALUE;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#toJavaObject(java.lang.Class)
	 */
	public Object toJavaObject(Class target) throws XPathException {
		throw new XPathException(
			"cannot convert value of type "
				+ Type.getTypeName(getType())
				+ " to Java object of type "
				+ target.getName());
	}

	public String toString() {
		try {
			return getStringValue();
		} catch (XPathException e) {
			return super.toString();
		}
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#isCached()
	 */
	public boolean isCached() {
		// always returns false by default
		return false;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#setIsCached(boolean)
	 */
	public void setIsCached(boolean cached) {
		// ignore
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#setSelfAsContext()
	 */
	public void setSelfAsContext() {
	}
	
	private final static class EmptyValue extends AtomicValue {

		/* (non-Javadoc)
		 * @see org.exist.xquery.value.AtomicValue#getStringValue()
		 */
		public String getStringValue() {
			return "";
		}

		/* (non-Javadoc)
		 * @see org.exist.xquery.value.AtomicValue#convertTo(int)
		 */
		public AtomicValue convertTo(int requiredType) throws XPathException {
			throw new XPathException("cannot convert empty value to " + requiredType);
		}

		/* (non-Javadoc)
		 * @see org.exist.xquery.value.AtomicValue#compareTo(java.lang.Object)
		 */
		public int compareTo(Collator collator, AtomicValue other) throws XPathException {
			if (other instanceof EmptyValue)
				return 0;
			else
				return -1;
		}

		/* (non-Javadoc)
		 * @see org.exist.xquery.value.AtomicValue#itemAt(int)
		 */
		public Item itemAt(int pos) {
			return null;
		}

		/* (non-Javadoc)
		 * @see org.exist.xquery.value.Item#toSequence()
		 */
		public Sequence toSequence() {
			return this;
		}

		/* (non-Javadoc)
		 * @see org.exist.xquery.value.AtomicValue#max(org.exist.xquery.value.AtomicValue)
		 */
		public AtomicValue max(Collator collator, AtomicValue other) throws XPathException {
			return this;
		}

		/* (non-Javadoc)
		 * @see org.exist.xquery.value.Sequence#add(org.exist.xquery.value.Item)
		 */
		public void add(Item item) throws XPathException {
		}

		/* (non-Javadoc)
		 * @see org.exist.xquery.value.AtomicValue#compareTo(int, org.exist.xquery.value.AtomicValue)
		 */
		public boolean compareTo(Collator collator, int operator, AtomicValue other) throws XPathException {
			throw new XPathException("Cannot compare operand to empty value");
		}
		
		/* (non-Javadoc)
		 * @see org.exist.xquery.value.AtomicValue#min(org.exist.xquery.value.AtomicValue)
		 */
		public AtomicValue min(Collator collator, AtomicValue other) throws XPathException {
			return this;
		}

		/* (non-Javadoc)
		 * @see org.exist.xquery.value.Item#conversionPreference(java.lang.Class)
		 */
		public int conversionPreference(Class javaClass) {
			return Integer.MAX_VALUE;
		}

		/* (non-Javadoc)
		 * @see org.exist.xquery.value.Item#toJavaObject(java.lang.Class)
		 */
		public Object toJavaObject(Class target) throws XPathException {
			throw new XPathException(
				"cannot convert value of type "
					+ Type.getTypeName(getType())
					+ " to Java object of type "
					+ target.getName());
		}
	}
}
