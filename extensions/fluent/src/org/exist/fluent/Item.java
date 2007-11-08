package org.exist.fluent;

import java.util.Date;

import javax.xml.datatype.Duration;
import javax.xml.datatype.XMLGregorianCalendar;

import org.exist.dom.NodeProxy;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.Serializer;
import org.exist.xquery.Constants;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.*;
import org.xml.sax.SAXException;

/**
 * An XML item in the database.  While most often used to represent XML
 * elements, it can also stand in for any DOM node or an atomic value.  However, it
 * is not used to represent entire XML documents (see {@link org.exist.fluent.XMLDocument}).
 * Not all operations are valid in all cases.
 * 
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 * @version $Revision: 1.13 $ ($Date: 2006/04/14 04:12:04 $)
 */
public class Item extends Resource {
	
	protected final org.exist.xquery.value.Item item;
	
	Item() {
		super(null, null);
		this.item = null;
	}
	
	Item(org.exist.xquery.value.Item item, NamespaceMap namespaceBindings, Database db) {
		super(namespaceBindings, db);
		// the item should've been tracked (Database.trackNode) before getting here!
		this.item = item;
	}
	
	/**
	 * Return this item cast as a node.
	 * 
	 * @return this item cast as a node
	 * @throws DatabaseException if this item is not a node
	 */
	public Node node() {
		throw new DatabaseException("this item is not a node: " + this);
	}
	
	@Override public boolean equals(Object o) {
		if (!(o instanceof Item)) return false;
		Item that = (Item) o;
		if (this.item == that.item) return true;
		if (this.item instanceof AtomicValue && that.item instanceof AtomicValue) {
			AtomicValue thisValue = (AtomicValue) this.item, thatValue = (AtomicValue) that.item;
			try {
				return
						thisValue.getType() == thatValue.getType()
						&& thisValue.compareTo(null, Constants.EQ, thatValue);
			} catch (XPathException e) {
				// fall through
			}
		}
		return false;
	}
	
	/**
	 * The hash code computation can be expensive, and the hash codes may not be very well distributed.
	 * You probably shouldn't use items in situations where they might get hashed.
	 */
	@Override public int hashCode() {
		if (item instanceof AtomicValue) {
			AtomicValue value = (AtomicValue) item;
			try {
				return value.getType() ^ value.getStringValue().hashCode();
			} catch (XPathException e) {
				return value.getType();
			}
		} else {
			return item.hashCode();
		}
	}
	
	/**
	 * Return whether this item really exists, or if it's a virtual placeholder equivalent to a <code>null</code>.
	 * 
	 * @return <code>true</code> if the item exists, <code>false</code> if it's a null placeholder returned as part of an optional query
	 */
	public boolean extant() {
		return true;
	}

	@Override	Sequence convertToSequence() {
		return item.toSequence();
	}

	/**
	 * @return the string value of this item if atomic, or the concatenation of its text content if a node
	 */
	public String value() {
		try {
			return item.getStringValue();
		} catch (XPathException e) {
			throw new DatabaseException(e);
		}
	}
	
	/**
	 * Return the converted boolean value following XQuery / XPath conversion rules.
	 * For numeric values, return false iff the value is 0.  For strings, return true if
	 * the value is 'true' or '1' and false if the value is 'false' or '0', fail otherwise.  For
	 * nodes, return the conversion of the effective string value.
	 *
	 * @return the boolean value of the item
	 * @throws DatabaseException if the conversion failed
	 */
	public boolean booleanValue() {
		try {
			return ((Boolean) item.toJavaObject(Boolean.class)).booleanValue();
		} catch (XPathException e) {
			throw new DatabaseException(e);
		}
	}
	
	/**
	 * Return the int value of this item.  For numeric atomic values, truncate to an int;
	 * for other values, request a conversion of the effective string value (which may fail).
	 * If the value is out of range for ints, return the smallest or largest int, as appropriate.
	 * If you think overflow may be a problem, check for these values.
	 *
	 * @return the int value of this item
	 * @throws DatabaseException if the conversion failed
	 */
	public int intValue() {
		try {
			return ((Integer) item.toJavaObject(Integer.class)).intValue();
		} catch (XPathException e) {
			throw new DatabaseException(e);
		}
	}
	
	/**
	 * Return the long value of this item.  For numeric atomic values, truncate to a long;
	 * for other values, request a conversion of the effective string value (which may fail).
	 * If the value is out of range for longs, return the smallest or largest long, as appropriate.
	 * If you think overflow may be a problem, check for these values.
	 *
	 * @return the long value of this item
	 * @throws DatabaseException if the conversion failed
	 */
	public long longValue() {
		try {
			return ((Long) item.toJavaObject(Long.class)).longValue();
		} catch (XPathException e) {
			throw new DatabaseException(e);
		}
	}
	
	/**
	 * Return the double value of this item.  For numeric atomic values, truncate to a double;
	 * for other values, request a conversion of the effective string value (which may fail).
	 * If the value is out of range for doubles, return positive or negative infinity, as appropriate.
	 * If you think overflow may be a problem, check for these values.
	 *
	 * @return the double value of this item
	 * @throws DatabaseException if the conversion failed
	 */
	public double doubleValue() {
		try {
			return ((Double) item.toJavaObject(Double.class)).doubleValue();
		} catch (XPathException e) {
			throw new DatabaseException(e);
		}
	}
	
	/**
	 * Return the duration value of this item by parsing its string representation as a duration.
	 *
	 * @return the duration value of this item
	 * @throws DatabaseException if the conversion failed
	 */
	public Duration durationValue() {
		try {
			return DataUtils.datatypeFactory().newDuration(value());
		} catch (IllegalArgumentException e) {
			throw new DatabaseException(e);
		}
	}
	
	/**
	 * Return the XML date/time value of this item by parsing its string representation.
	 *
	 * @return the XML date/time value of this item
	 * @throws DatabaseException if the conversion failed
	 */
	public XMLGregorianCalendar dateTimeValue() {
		try {
			return DataUtils.datatypeFactory().newXMLGregorianCalendar(value());
		} catch (IllegalArgumentException e) {
			throw new DatabaseException(e);
		}
	}
	
	/**
	 * Return the <code>java.util.Date</code> value of this item by parsing its string
	 * representation as an XML date/time value, then converting to a Java date.
	 *
	 * @return the Java time instant value of this item
	 * @throws DatabaseException if the conversion failed
	 */
	public Date instantValue() {
		return DataUtils.toDate(dateTimeValue());
	}
	
	/**
	 * Return the string representation of this item.  If the item is atomic, return its string
	 * value.  If it is a node, serialize it to a string.
	 * 
	 * @return the string representation of this item
	 */
	@Override
	public String toString() {
		if (item instanceof AtomicValue) {
			return value();
		}
		assert item instanceof NodeValue;
		DBBroker broker = null;
		try {
			broker = db.acquireBroker();
			Serializer serializer = broker.getSerializer();
			if (item instanceof NodeProxy) {
				NodeProxy proxy = (NodeProxy) item;
				if (proxy.isDocument()) {
					return serializer.serialize(proxy.getDocument());
				}
			}
			return serializer.serialize((NodeValue) item);
		} catch (SAXException e) {
			throw new DatabaseException(e);
		} finally {
			db.releaseBroker(broker);
		}
		
	}


	/**
	 * A null item, used as a placeholder where an actual <code>null</code> would be inappropriate.
	 * REMEMBER to duplicate all these methods in Node.NULL as well!
	 */
	static final Item NULL = new Item() {
		@Override public boolean booleanValue() {return false;}
		@Override public int intValue() {return 0;}
		@Override public long longValue() {return 0L;}
		@Override public double doubleValue() {return 0.0;}
		@Override public Duration durationValue() {return null;}
		@Override public XMLGregorianCalendar dateTimeValue() {return null;}
		@Override public Date instantValue() {return null;}
		@Override public Node node() {return Node.NULL;}
		@Override public boolean extant() {return false;}
		@Override public QueryService query() {return QueryService.NULL;}
		@Override public String value() {return null;}
		@Override public String toString() {return "NULL item";}
		@Override	Sequence convertToSequence() {return Sequence.EMPTY_SEQUENCE;}
	};


}
