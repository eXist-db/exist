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

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.EmptyNodeSet;
import org.exist.dom.persistent.NodeHandle;
import org.exist.dom.persistent.NodeSet;
import org.exist.dom.memtree.DocumentBuilderReceiver;
import org.exist.numbering.NodeId;
import org.exist.storage.DBBroker;
import org.exist.storage.Indexable;
import org.exist.storage.ValueIndexFactory;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Constants;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.util.ExpressionDumper;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.text.Collator;
import java.util.Iterator;
import java.util.Properties;

/**
 * Represents an atomic value. All simple values that are not nodes extend AtomicValue.
 * As every single item is also a sequence, this class implements both: Item and Sequence.
 * 
 * @author wolf
 */
public abstract class AtomicValue implements Item, Sequence, Indexable {

    /** An empty atomic value */
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

	@Override
	public Sequence tail() throws XPathException {
		return Sequence.EMPTY_SEQUENCE;
	}
	
	public abstract AtomicValue convertTo(int requiredType) throws XPathException;

	public abstract boolean compareTo(Collator collator, int operator, AtomicValue other)
		throws XPathException;

	public abstract int compareTo(Collator collator, AtomicValue other) throws XPathException;

	public abstract AtomicValue max(Collator collator, AtomicValue other) throws XPathException;

	public abstract AtomicValue min(Collator collator, AtomicValue other) throws XPathException;

    /**
     * Compares this atomic value to another. Returns true if the current value is of type string
     * and its value starts with the string value of the other value.
     * 
     * @param collator Collator used for string comparison.
     * @param other
     * @throws XPathException if this is not a string.
     */
	public boolean startsWith(Collator collator, AtomicValue other) throws XPathException {
		throw new XPathException("Cannot call starts-with on value of type " + 
				Type.getTypeName(getType()));
	}
	
	/**
     * Compares this atomic value to another. Returns true if the current value is of type string
     * and its value ends with the string value of the other value.
     * 
     * @param collator Collator used for string comparison.
     * @param other
     * @throws XPathException if this is not a string.
     */
	public boolean endsWith(Collator collator, AtomicValue other) throws XPathException {
		throw new XPathException("Cannot call ends-with on value of type " + 
				Type.getTypeName(getType()));
	}
	
	/**
     * Compares this atomic value to another. Returns true if the current value is of type string
     * and its value contains the string value of the other value.
     * 
     * @param collator Collator used for string comparison.
     * @param other
     * @throws XPathException if this is not a string.
     */
	public boolean contains(Collator collator, AtomicValue other) throws XPathException {
		throw new XPathException("Cannot call contains on value of type " + 
				Type.getTypeName(getType()));
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#getLength()
	 */
	public int getItemCount() {
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
	public SequenceIterator iterate() throws XPathException {
		return new SingleItemIterator(this);
	}

	public SequenceIterator unorderedIterator() throws XPathException {
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
	public void toSAX(DBBroker broker, ContentHandler handler, Properties properties) throws SAXException {
		try {
			final String s = getStringValue();
			handler.characters(s.toCharArray(), 0, s.length());
		} catch (final XPathException e) {
			throw new SAXException(e);
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#copyTo(org.exist.storage.DBBroker, org.exist.dom.memtree.DocumentBuilderReceiver)
	 */
	public void copyTo(DBBroker broker, DocumentBuilderReceiver receiver) throws SAXException {
		try {
			final String s = getStringValue();
			receiver.characters(s);
		} catch (final XPathException e) {
			throw new SAXException(e);
		}
	}
	
	public boolean isEmpty() {
		return false;
	}
	
	public boolean hasOne() {
		return true;
	}
	
	public boolean hasMany() {
		return false;
	}	

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#add(org.exist.xquery.value.Item)
	 */
	public void add(Item item) throws XPathException {
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#addAll(org.exist.xquery.value.Sequence)
	 */
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
	public abstract boolean effectiveBooleanValue() throws XPathException;
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#toNodeSet()
	 */
	public NodeSet toNodeSet() throws XPathException {
		//TODO : solution that may be worth to investigate
		/*
		if (!effectiveBooleanValue())
			return NodeSet.EMPTY_SET;
		*/
		throw new XPathException(
				"cannot convert " + Type.getTypeName(getType()) + "('" + getStringValue() + "')"
					+ " to a node set");
	}

    public MemoryNodeSet toMemNodeSet() throws XPathException {
		throw new XPathException(
				"cannot convert " + Type.getTypeName(getType()) + "('" + getStringValue() + "')"
					+ " to a node set");
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#getDocumentSet()
     */
    public DocumentSet getDocumentSet() {
        return DocumentSet.EMPTY_DOCUMENT_SET;
    }

    public Iterator<Collection> getCollectionIterator() {
        return EmptyNodeSet.EMPTY_COLLECTION_ITERATOR;
    }
    
    public AtomicValue promote(AtomicValue otherValue) throws XPathException {
        if (getType() != otherValue.getType()) {
            if (Type.subTypeOf(getType(), Type.DECIMAL) && 
                    (Type.subTypeOf(otherValue.getType(), Type.DOUBLE) 
                        || Type.subTypeOf(otherValue.getType(), Type.FLOAT)))
                    {return convertTo(otherValue.getType());}
    
            if (Type.subTypeOf(getType(), Type.FLOAT) &&
                    Type.subTypeOf(otherValue.getType(), Type.DOUBLE))
                {return convertTo(Type.DOUBLE);}
    
            if (Type.subTypeOf(getType(), Type.ANY_URI) &&
                    Type.subTypeOf(otherValue.getType(), Type.STRING))
                {return convertTo(Type.STRING);}
        }
        return this;
    }
    
	/**
     * Dump a string representation of this value to the given 
     * ExpressionDumper.
     * 
	 * @param dumper
	 */
	public void dump(ExpressionDumper dumper) {
	    try {
            dumper.display(getStringValue());
        } catch (final XPathException e) {
        }
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#conversionPreference(java.lang.Class)
	 */
	public int conversionPreference(Class<?> javaClass) {
		return Integer.MAX_VALUE;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#toJavaObject(java.lang.Class)
	 */
        @Override
	public <T> T toJavaObject(final Class<T> target) throws XPathException {
		throw new XPathException(
			"cannot convert value of type "
				+ Type.getTypeName(getType())
				+ " to Java object of type "
				+ target.getName());
	}

	public String toString() {
		try {
			return getStringValue();
		} catch (final XPathException e) {
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
	
	public void clearContext(int contextId) throws XPathException {
		// ignore
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#setSelfAsContext()
	 */
	public void setSelfAsContext(int contextId) throws XPathException {
	}
	
        /* (non-Javadoc)
         * @see org.exist.xquery.value.Sequence#isPersistentSet()
         */
        public boolean isPersistentSet() {
            return false;
        }

        @Override
        public void nodeMoved(NodeId oldNodeId, NodeHandle newNode) {
        }
	
        /*
	public byte[] serialize(short collectionId)	throws EXistException {	
		//TODO : pass the factory as an argument
		return ValueIndexFactory.serialize(this, collectionId);
	}
	*/	


	/* (non-Javadoc)
	 * @deprecated
	 * @see org.exist.storage.Indexable#serialize(short, boolean)
	 */
	/*
	public byte[] serialize(short collectionId, boolean caseSensitive)	throws EXistException {	
		//TODO : pass the factory as an argument
		return ValueIndexFactory.serialize(this, collectionId, caseSensitive);
	}	
	*/
	
	public byte[] serializeValue(int offset) throws EXistException {		
		//TODO : pass the factory as an argument
		return ValueIndexFactory.serialize(this, offset);
	}
	
	/* (non-Javadoc)
	 * @deprecated
	 * @see org.exist.storage.Indexable#serializeValue(int, boolean)
	 */
	/*
	public byte[] serializeValue(int offset, boolean caseSensitive)	throws EXistException {		
		//TODO : pass the factory as an argument
		return ValueIndexFactory.serialize(this, offset, caseSensitive);
	}
	*/
	
	public int compareTo(Object other) {		
		throw new IllegalArgumentException("Invalid call to compareTo by " + Type.getTypeName(this.getItemType()));
	}

    public int getState() {
        return 0;
    }

    public boolean hasChanged(int previousState) {
        return false; // never changes
    }

    public boolean isCacheable() {
        return true;
    }

    @Override
    public void destroy(XQueryContext context, Sequence contextSequence) {
        // nothing to be done by default
    }

    private final static class EmptyValue extends AtomicValue {
		
    	public boolean hasOne() {
    		return false;
    	}

    	public boolean isEmpty() {
			return true;
		}

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
			switch (requiredType) {
				case Type.ATOMIC :
				case Type.ITEM :
				case Type.STRING :
					return StringValue.EMPTY_STRING;
				case Type.NORMALIZED_STRING:
				case Type.TOKEN:
				case Type.LANGUAGE:
				case Type.NMTOKEN:
				case Type.NAME:
				case Type.NCNAME:
				case Type.ID:
				case Type.IDREF:
				case Type.ENTITY:
					return new StringValue("", requiredType);
				case Type.ANY_URI :
					return AnyURIValue.EMPTY_URI;
				case Type.BOOLEAN :
					return BooleanValue.FALSE;
				//case Type.FLOAT :
					//return new FloatValue(value); 
				//case Type.DOUBLE :
				//case Type.NUMBER :
					//return new DoubleValue(this);
				//case Type.DECIMAL :
					//return new DecimalValue(value);
				//case Type.INTEGER :
				//case Type.NON_POSITIVE_INTEGER :
				//case Type.NEGATIVE_INTEGER :
				//case Type.POSITIVE_INTEGER :
				//case Type.LONG :
				//case Type.INT :
				//case Type.SHORT :
				//case Type.BYTE :
				//case Type.NON_NEGATIVE_INTEGER :
				//case Type.UNSIGNED_LONG :
				//case Type.UNSIGNED_INT :
				//case Type.UNSIGNED_SHORT :
				//case Type.UNSIGNED_BYTE :
					//return new IntegerValue(value, requiredType);
				//case Type.BASE64_BINARY :
					//return new Base64Binary(value);
	            //case Type.HEX_BINARY :
	                //return new HexBinary(value);
				//case Type.DATE_TIME :
					//return new DateTimeValue(value);
				//case Type.TIME :
					//return new TimeValue(value);
				//case Type.DATE :
					//return new DateValue(value);
				//case Type.DURATION :
					//return new DurationValue(value);
				//case Type.YEAR_MONTH_DURATION : 
					//return new YearMonthDurationValue(value);
				//case Type.DAY_TIME_DURATION :	
					//return new DayTimeDurationValue(value);
	            //case Type.GYEAR :
	                //return new GYearValue(value);
	            //case Type.GMONTH :
	                //return new GMonthValue(value);
	            //case Type.GDAY :
	                //return new GDayValue(value);
	            //case Type.GYEARMONTH :
	                //return new GYearMonthValue(value);
	            //case Type.GMONTHDAY :
	                //return new GMonthDayValue(value);
				//case Type.UNTYPED_ATOMIC :
					//return new UntypedAtomicValue(getStringValue());
				default :
					throw new XPathException("cannot convert empty value to " + requiredType);
			}
		}
		
		public boolean effectiveBooleanValue() throws XPathException {
			return false;
		}

		/* (non-Javadoc)
		 * @see org.exist.xquery.value.AtomicValue#compareTo(java.lang.Object)
		 */
		public int compareTo(Collator collator, AtomicValue other) throws XPathException {
			if (other instanceof EmptyValue)
				{return Constants.EQUAL;}
			else
				{return Constants.INFERIOR;}
		}

		/* (non-Javadoc)
		 * @see org.exist.xquery.value.AtomicValue#compareTo(int, org.exist.xquery.value.AtomicValue)
		 */
		public boolean compareTo(Collator collator, int operator, AtomicValue other) throws XPathException {
			return false;
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
		 * @see org.exist.xquery.value.AtomicValue#min(org.exist.xquery.value.AtomicValue)
		 */
		public AtomicValue min(Collator collator, AtomicValue other) throws XPathException {
			return this;
		}

		/* (non-Javadoc)
		 * @see org.exist.xquery.value.Item#conversionPreference(java.lang.Class)
		 */
		public int conversionPreference(Class<?> javaClass) {
			return Integer.MAX_VALUE;
		}

		/* (non-Javadoc)
		 * @see org.exist.xquery.value.Item#toJavaObject(java.lang.Class)
		 */
		@Override
                public <T> T toJavaObject(final Class<T> target) throws XPathException {
			throw new XPathException(
				"cannot convert value of type "
					+ Type.getTypeName(getType())
					+ " to Java object of type "
					+ target.getName());
		}
	}
}
