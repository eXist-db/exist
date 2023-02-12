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

import com.ibm.icu.text.Collator;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.memtree.DocumentBuilderReceiver;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.EmptyNodeSet;
import org.exist.dom.persistent.NodeHandle;
import org.exist.dom.persistent.NodeSet;
import org.exist.numbering.NodeId;
import org.exist.storage.DBBroker;
import org.exist.storage.Indexable;
import org.exist.storage.ValueIndexFactory;
import org.exist.xquery.*;
import org.exist.xquery.Constants.Comparison;
import org.exist.xquery.util.ExpressionDumper;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.Properties;

/**
 * Represents an atomic value. All simple values that are not nodes extend AtomicValue.
 * As every single item is also a sequence, this class implements both: Item and Sequence.
 *
 * @author wolf
 */
public abstract class AtomicValue implements Item, Sequence, Indexable {

    /**
     * An empty atomic value
     */
    public static final AtomicValue EMPTY_VALUE = new EmptyValue();

    /** the expression from which this value derives, or null */
    private Expression expression;

    /**
     * Gets the expression from which this value derives.
     *
     * @return the expression from which this value derives, or null
     */
    @Override
    public Expression getExpression() { return expression; }

    protected AtomicValue() { this(null); }

    protected AtomicValue(final Expression expression) { this.expression = expression; }

    @Override
    public int getType() {
        return Type.ANY_ATOMIC_TYPE;
    }

    @Override
    public abstract String getStringValue() throws XPathException;

    @Override
    public Sequence tail() throws XPathException {
        return Sequence.EMPTY_SEQUENCE;
    }

    public abstract boolean compareTo(final Collator collator, final Comparison operator, final AtomicValue other)
            throws XPathException;

    public abstract int compareTo(final Collator collator, final AtomicValue other) throws XPathException;

    public abstract AtomicValue max(final Collator collator, final AtomicValue other) throws XPathException;

    public abstract AtomicValue min(final Collator collator, final AtomicValue other) throws XPathException;

    /**
     * Compares this atomic value to another. Returns true if the current value is of type string
     * and its value starts with the string value of the other value.
     *
     * @param collator Collator used for string comparison.
     * @param other the other value.
     * @return true if the value starts with the other value
     * @throws XPathException if this is not a string.
     */
    public boolean startsWith(final Collator collator, final AtomicValue other) throws XPathException {
        throw new XPathException(getExpression(), "Cannot call starts-with on value of type " +
                Type.getTypeName(getType()));
    }

    /**
     * Compares this atomic value to another. Returns true if the current value is of type string
     * and its value ends with the string value of the other value.
     *
     * @param collator Collator used for string comparison.
     * @param other the other value.
     * @return true if the value ends with the other value
     * @throws XPathException if this is not a string.
     */
    public boolean endsWith(final Collator collator, final AtomicValue other) throws XPathException {
        throw new XPathException(getExpression(), "Cannot call ends-with on value of type " +
                Type.getTypeName(getType()));
    }

    /**
     * Compares this atomic value to another. Returns true if the current value is of type string
     * and its value contains the string value of the other value.
     *
     * @param collator Collator used for string comparison.
     * @param other the other value.
     * @throws XPathException if this is not a string.
     * @return true if the value contains the passed in value
     */
    public boolean contains(final Collator collator, final AtomicValue other) throws XPathException {
        throw new XPathException(getExpression(), "Cannot call contains on value of type " +
                Type.getTypeName(getType()));
    }

    @Override
    public long getItemCountLong() {
        return 1;
    }

    @Override
    public Cardinality getCardinality() {
        return Cardinality.EXACTLY_ONE;
    }

    @Override
    public void removeDuplicates() {
        // this is a single value, so there are no duplicates to remove
    }

    @Override
    public SequenceIterator iterate() {
        return new SingleItemIterator(this);
    }

    @Override
    public SequenceIterator unorderedIterator() {
        return new SingleItemIterator(this);
    }

    @Override
    public int getItemType() {
        return getType();
    }

    @Override
    public Item itemAt(final int pos) {
        return pos > 0 ? null : this;
    }

    @Override
    public Sequence toSequence() {
        return this;
    }

    @Override
    public void toSAX(final DBBroker broker, final ContentHandler handler, final Properties properties) throws SAXException {
        try {
            final String s = getStringValue();
            handler.characters(s.toCharArray(), 0, s.length());
        } catch (final XPathException e) {
            throw new SAXException(e);
        }
    }

    @Override
    public void copyTo(final DBBroker broker, final DocumentBuilderReceiver receiver) throws SAXException {
        try {
            final String s = getStringValue();
            receiver.characters(s);
        } catch (final XPathException e) {
            throw new SAXException(e);
        }
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean hasOne() {
        return true;
    }

    @Override
    public boolean hasMany() {
        return false;
    }

    @Override
    public void add(final Item item) {
    }

    @Override
    public void addAll(final Sequence other) {
    }

    @Override
    public AtomicValue atomize() throws XPathException {
        return this;
    }

    @Override
    public NodeSet toNodeSet() throws XPathException {
        //TODO : solution that may be worth to investigate
        /*
		if (!effectiveBooleanValue())
			return NodeSet.EMPTY_SET;
		*/
        throw new XPathException(getExpression(), 
                "cannot convert " + Type.getTypeName(getType()) + "('" + getStringValue() + "')"
                        + " to a node set");
    }

    @Override
    public MemoryNodeSet toMemNodeSet() throws XPathException {
        throw new XPathException(getExpression(), 
                "cannot convert " + Type.getTypeName(getType()) + "('" + getStringValue() + "')"
                        + " to a node set");
    }

    @Override
    public DocumentSet getDocumentSet() {
        return DocumentSet.EMPTY_DOCUMENT_SET;
    }

    @Override
    public Iterator<Collection> getCollectionIterator() {
        return EmptyNodeSet.EMPTY_COLLECTION_ITERATOR;
    }

    public AtomicValue promote(final AtomicValue otherValue) throws XPathException {
        if (getType() != otherValue.getType()) {
            if (Type.subTypeOf(getType(), Type.DECIMAL) &&
                    (Type.subTypeOf(otherValue.getType(), Type.DOUBLE)
                            || Type.subTypeOf(otherValue.getType(), Type.FLOAT))) {
                return convertTo(otherValue.getType());
            }

            if (Type.subTypeOf(getType(), Type.FLOAT) &&
                    Type.subTypeOf(otherValue.getType(), Type.DOUBLE)) {
                return convertTo(Type.DOUBLE);
            }

            if (Type.subTypeOf(getType(), Type.ANY_URI) &&
                    Type.subTypeOf(otherValue.getType(), Type.STRING)) {
                return convertTo(Type.STRING);
            }
        }
        return this;
    }

    /**
     * Dump a string representation of this value to the given
     * ExpressionDumper.
     *
     * @param dumper the expression dumper
     */
    public void dump(final ExpressionDumper dumper) {
        try {
            dumper.display(getStringValue());
        } catch (final XPathException e) {
        }
    }

    @Override
    public int conversionPreference(final Class<?> javaClass) {
        return Integer.MAX_VALUE;
    }

    @Override
    public <T> T toJavaObject(final Class<T> target) throws XPathException {
        throw new XPathException(getExpression(), 
                "cannot convert value of type "
                        + Type.getTypeName(getType())
                        + " to Java object of type "
                        + target.getName());
    }

    @Override
    public String toString() {
        try {
            return getStringValue();
        } catch (final XPathException e) {
            return super.toString();
        }
    }

    @Override
    public boolean isCached() {
        // always returns false by default
        return false;
    }

    @Override
    public void setIsCached(final boolean cached) {
        // ignore
    }

    @Override
    public void clearContext(final int contextId) {
        // ignore
    }

    @Override
    public void setSelfAsContext(final int contextId) {
    }

    @Override
    public boolean isPersistentSet() {
        return false;
    }

    @Override
    public void nodeMoved(final NodeId oldNodeId, final NodeHandle newNode) {
    }

    @Override
    public byte[] serializeValue(final int offset) throws EXistException {
        //TODO : pass the factory as an argument
        return ValueIndexFactory.serialize(this, offset);
    }

    @Override
    public int compareTo(final Object other) {
        throw new IllegalArgumentException("Invalid call to compareTo by " + Type.getTypeName(this.getItemType()));
    }

    @Override
    public int getState() {
        return 0;
    }

    @Override
    public boolean hasChanged(final int previousState) {
        return false; // never changes
    }

    @Override
    public boolean isCacheable() {
        return true;
    }

    @Override
    public boolean containsReference(final Item item) {
        return this == item;
    }

    @Override
    public boolean contains(final Item item) {
        return equals(item);
    }

    @Override
    public void destroy(final XQueryContext context, @Nullable final Sequence contextSequence) {
        // nothing to be done by default
    }

    private final static class EmptyValue extends AtomicValue {

        @Override
        public boolean hasOne() {
            return false;
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public String getStringValue() {
            return "";
        }

        @Override
        public AtomicValue convertTo(final int requiredType) throws XPathException {
            switch (requiredType) {
                case Type.ANY_ATOMIC_TYPE:
                case Type.ITEM:
                case Type.STRING:
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
                    return new StringValue(getExpression(), "", requiredType);
                case Type.ANY_URI:
                    return AnyURIValue.EMPTY_URI;
                case Type.BOOLEAN:
                    return BooleanValue.FALSE;
                //case Type.FLOAT :
                //return new FloatValue(value);
                //case Type.DOUBLE :
                //case Type.NUMERIC :
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
                default:
                    throw new XPathException(getExpression(), "cannot convert empty value to " + requiredType);
            }
        }

        @Override
        public boolean effectiveBooleanValue() {
            return false;
        }

        @Override
        public int compareTo(final Collator collator, final AtomicValue other) {
            if (other instanceof EmptyValue) {
                return Constants.EQUAL;
            } else {
                return Constants.INFERIOR;
            }
        }

        @Override
        public boolean compareTo(final Collator collator, final Comparison operator, final AtomicValue other) {
            return false;
        }

        @Override
        public Item itemAt(final int pos) {
            return null;
        }

        @Override
        public Sequence toSequence() {
            return this;
        }

        @Override
        public AtomicValue max(final Collator collator, final AtomicValue other) {
            return this;
        }

        @Override
        public void add(final Item item) {
        }

        @Override
        public AtomicValue min(final Collator collator, final AtomicValue other) {
            return this;
        }

        @Override
        public int conversionPreference(final Class<?> javaClass) {
            return Integer.MAX_VALUE;
        }

        @Override
        public <T> T toJavaObject(final Class<T> target) throws XPathException {
            throw new XPathException(getExpression(), 
                    "cannot convert value of type "
                            + Type.getTypeName(getType())
                            + " to Java object of type "
                            + target.getName());
        }
    }
}
