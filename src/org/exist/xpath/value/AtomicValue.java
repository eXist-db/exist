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
import org.exist.storage.DBBroker;
import org.exist.xpath.XPathException;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public abstract class AtomicValue implements Item, Sequence  {

	public final static AtomicValue EMPTY_VALUE = new EmptyValue();
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Item#getType()
	 */
	public int getType() {
		return Type.ATOMIC;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Item#getStringValue()
	 */
	public abstract String getStringValue() throws XPathException;

	public abstract AtomicValue convertTo(int requiredType) throws XPathException;
	
	public abstract boolean compareTo(int operator, AtomicValue other) throws XPathException;
	
	public abstract int compareTo(AtomicValue other) throws XPathException;
	
	public abstract AtomicValue max(AtomicValue other) throws XPathException;
	
	public abstract AtomicValue min(AtomicValue other) throws XPathException;
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Sequence#getLength()
	 */
	public int getLength() {
		return 1;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Sequence#iterate()
	 */
	public SequenceIterator iterate() {
		return new SingleItemIterator(this);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Sequence#getItemType()
	 */
	public int getItemType() {
		return getType();
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Sequence#itemAt(int)
	 */
	public Item itemAt(int pos) {
		return pos > 0 ? null : this;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Item#toSequence()
	 */
	public Sequence toSequence() {
		return this;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Item#toSAX(org.exist.storage.DBBroker, org.xml.sax.ContentHandler)
	 */
	public void toSAX(DBBroker broker, ContentHandler handler)
		throws SAXException {
		String s;
		try {
			s = getStringValue();
			handler.characters(s.toCharArray(), 0, s.length());
		} catch (XPathException e) {
			throw new SAXException(e);
		}
	}
		
	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Sequence#add(org.exist.xpath.value.Item)
	 */
	public void add(Item item) throws XPathException {
	}
	
	public void addAll(Sequence other) throws XPathException {
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Item#atomize()
	 */
	public AtomicValue atomize() throws XPathException {
		return this;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Item#effectiveBooleanValue()
	 */
	public boolean effectiveBooleanValue() throws XPathException {
		return getStringValue().length() > 0;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Sequence#toNodeSet()
	 */
	public NodeSet toNodeSet() throws XPathException {
		throw new XPathException("cannot convert value of type " +
			Type.getTypeName(getType()) + " to a node set");
	}

	public String pprint() {
		try {
			return getStringValue();
		} catch (XPathException e) {
			return "";
		}
	}
	
	private final static class EmptyValue extends AtomicValue {
		
		/* (non-Javadoc)
		 * @see org.exist.xpath.value.AtomicValue#getStringValue()
		 */
		public String getStringValue() {
			return "";
		}
		
		/* (non-Javadoc)
		 * @see org.exist.xpath.value.AtomicValue#convertTo(int)
		 */
		public AtomicValue convertTo(int requiredType) throws XPathException {
			throw new XPathException("cannot convert empty value to " + requiredType);
		}

		/* (non-Javadoc)
		 * @see org.exist.xpath.value.AtomicValue#compareTo(java.lang.Object)
		 */
		public int compareTo(AtomicValue other) throws XPathException {
			if(other instanceof EmptyValue)
				return 0;
			else
				return -1;
		}
				
		/* (non-Javadoc)
		 * @see org.exist.xpath.value.AtomicValue#itemAt(int)
		 */
		public Item itemAt(int pos) {
			return null;
		}
		
		/* (non-Javadoc)
		 * @see org.exist.xpath.value.Item#toSequence()
		 */
		public Sequence toSequence() {
			return this;
		}

		/* (non-Javadoc)
		 * @see org.exist.xpath.value.AtomicValue#max(org.exist.xpath.value.AtomicValue)
		 */
		public AtomicValue max(AtomicValue other) throws XPathException {
			return this;
		}
		
		/* (non-Javadoc)
		 * @see org.exist.xpath.value.Sequence#add(org.exist.xpath.value.Item)
		 */
		public void add(Item item) throws XPathException {
		}
		
		/* (non-Javadoc)
		 * @see org.exist.xpath.value.AtomicValue#compareTo(int, org.exist.xpath.value.AtomicValue)
		 */
		public boolean compareTo(int operator, AtomicValue other)
			throws XPathException {
			throw new XPathException("Cannot compare operand to empty value");
		}

		/* (non-Javadoc)
		 * @see org.exist.xpath.value.AtomicValue#min(org.exist.xpath.value.AtomicValue)
		 */
		public AtomicValue min(AtomicValue other) throws XPathException {
			return this;
		}
	}
}
