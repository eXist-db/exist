/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.value;

import com.ibm.icu.text.Collator;
import org.exist.util.Collations;
import org.exist.xquery.Constants;
import org.exist.xquery.Constants.Comparison;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.XPathException;

/**
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public class UntypedAtomicValue extends AtomicValue {

    private final String value;

    public UntypedAtomicValue(String value) {
        this.value = value;
    }

    public static AtomicValue convertTo(String value, int requiredType) throws XPathException {
        return convertTo(null, value, requiredType);
    }

    public static AtomicValue convertTo(UntypedAtomicValue strVal, String value, int requiredType) throws XPathException {
        switch (requiredType) {
            case Type.ATOMIC:
            case Type.ITEM:
            case Type.UNTYPED_ATOMIC:
                return strVal == null ? new UntypedAtomicValue(value) : strVal;
            case Type.STRING:
                return new StringValue(value);
            case Type.ANY_URI:
                return new AnyURIValue(value);
            case Type.BOOLEAN:
                final String trimmed = StringValue.trimWhitespace(value);
                if ("0".equals(trimmed) || "false".equals(trimmed)) {
                    return BooleanValue.FALSE;
                } else if ("1".equals(trimmed) || "true".equals(trimmed)) {
                    return BooleanValue.TRUE;
                } else {
                    throw new XPathException(ErrorCodes.FORG0001, "cannot cast '" +
                            Type.getTypeName(Type.ATOMIC) + "(\"" + value + "\")' to " +
                            Type.getTypeName(requiredType));
                }
            case Type.FLOAT:
                return new FloatValue(value);
            case Type.DOUBLE:
                return new DoubleValue(value);
            case Type.NUMBER:
                //TODO : more complicated
                return new DoubleValue(value);
            case Type.DECIMAL:
                return new DecimalValue(value);
            case Type.INTEGER:
            case Type.POSITIVE_INTEGER:
            case Type.NON_POSITIVE_INTEGER:
            case Type.NEGATIVE_INTEGER:
            case Type.LONG:
            case Type.INT:
            case Type.SHORT:
            case Type.BYTE:
            case Type.NON_NEGATIVE_INTEGER:
            case Type.UNSIGNED_LONG:
            case Type.UNSIGNED_INT:
            case Type.UNSIGNED_SHORT:
            case Type.UNSIGNED_BYTE:
                return new IntegerValue(value, requiredType);


        /*
         The problem is that if this UntypedAtomicValue is constructed from a text() node
         stored in the database, which contains base64 or hex encoded data, then the string value could be huge
         and it has already been constructed and stored in memort by UntypedAtomicValue
         this should be defered

         TODO replace UntypedAtomicValue with something that can allow lazily reading text()
         values from the database.
         */
            case Type.BASE64_BINARY:
                return new BinaryValueFromBinaryString(new Base64BinaryValueType(), value);
            case Type.HEX_BINARY:
                return new BinaryValueFromBinaryString(new HexBinaryValueType(), value);


            case Type.DATE_TIME:
                return new DateTimeValue(value);
            case Type.TIME:
                return new TimeValue(value);
            case Type.DATE:
                return new DateValue(value);
            case Type.GYEAR:
                return new GYearValue(value);
            case Type.GMONTH:
                return new GMonthValue(value);
            case Type.GDAY:
                return new GDayValue(value);
            case Type.GYEARMONTH:
                return new GYearMonthValue(value);
            case Type.GMONTHDAY:
                return new GMonthDayValue(value);
            case Type.DURATION:
                return new DurationValue(value);
            case Type.YEAR_MONTH_DURATION:
                return new YearMonthDurationValue(value);
            case Type.DAY_TIME_DURATION:
                final DayTimeDurationValue dtdv = new DayTimeDurationValue(value);
                return new DayTimeDurationValue(dtdv.getCanonicalDuration());
            default:
                throw new XPathException(ErrorCodes.FORG0001, "cannot cast '" +
                        Type.getTypeName(Type.ATOMIC) + "(\"" + value + "\")' to " +
                        Type.getTypeName(requiredType));
        }
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.AtomicValue#getType()
     */
    @Override
    public int getType() {
        return Type.UNTYPED_ATOMIC;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#getStringValue()
     */
    @Override
    public String getStringValue() throws XPathException {
        return value;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#convertTo(int)
     */
    @Override
    public AtomicValue convertTo(int requiredType) throws XPathException {
        return convertTo(this, value, requiredType);
    }

    @Override
    public boolean compareTo(Collator collator, Comparison operator, AtomicValue other) throws XPathException {
        if (other.isEmpty()) {
            return false;
        }
        if (Type.subTypeOf(other.getType(), Type.STRING) ||
                Type.subTypeOf(other.getType(), Type.UNTYPED_ATOMIC)) {
            final int cmp = Collations.compare(collator, value, other.getStringValue());
            switch (operator) {
                case EQ:
                    return cmp == 0;
                case NEQ:
                    return cmp != 0;
                case LT:
                    return cmp < 0;
                case LTEQ:
                    return cmp <= 0;
                case GT:
                    return cmp > 0;
                case GTEQ:
                    return cmp >= 0;
                default:
                    throw new XPathException("Type error: cannot apply operand to string value");
            }
        }
        throw new XPathException(
                "Type error: operands are not comparable; expected xdt:untypedAtomic; got "
                        + Type.getTypeName(other.getType()));
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.AtomicValue#compareTo(org.exist.xquery.value.AtomicValue)
     */
    @Override
    public int compareTo(Collator collator, AtomicValue other) throws XPathException {
        return Collations.compare(collator, value, other.getStringValue());
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.AtomicValue#max(org.exist.xquery.value.AtomicValue)
     */
    @Override
    public AtomicValue max(Collator collator, AtomicValue other) throws XPathException {
        if (Type.subTypeOf(other.getType(), Type.UNTYPED_ATOMIC)) {
            return Collations.compare(collator, value, ((UntypedAtomicValue) other).value) > 0 ?
                    this : other;
        }
        return Collations.compare(collator, value, other.getStringValue()) > 0 ? this : other;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.AtomicValue#min(org.exist.xquery.value.AtomicValue)
     */
    @Override
    public AtomicValue min(Collator collator, AtomicValue other) throws XPathException {
        if (Type.subTypeOf(other.getType(), Type.UNTYPED_ATOMIC)) {
            return Collations.compare(collator, value, ((UntypedAtomicValue) other).value) < 0 ? this : other;
        }
        return Collations.compare(collator, value, other.getStringValue()) < 0 ?
                this : other;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.AtomicValue#startsWith(org.exist.xquery.value.AtomicValue)
     */
    @Override
    public boolean startsWith(Collator collator, AtomicValue other) throws XPathException {
        return Collations.startsWith(collator, value, other.getStringValue());
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.AtomicValue#endsWith(org.exist.xquery.value.AtomicValue)
     */
    @Override
    public boolean endsWith(Collator collator, AtomicValue other) throws XPathException {
        return Collations.endsWith(collator, value, other.getStringValue());
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.AtomicValue#contains(org.exist.xquery.value.AtomicValue)
     */
    @Override
    public boolean contains(Collator collator, AtomicValue other) throws XPathException {
        return Collations.indexOf(collator, value, other.getStringValue()) != Constants.STRING_NOT_FOUND;
    }

    @Override
    public boolean effectiveBooleanValue() throws XPathException {
        // If its operand is a singleton value of type xs:string, xs:anyURI, xs:untypedAtomic, 
        //or a type derived from one of these, fn:boolean returns false if the operand value has zero length; otherwise it returns true.
        return !value.isEmpty();
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Item#conversionPreference(java.lang.Class)
     */
    @Override
    public int conversionPreference(Class<?> javaClass) {
        if (javaClass.isAssignableFrom(StringValue.class)) {
            return 0;
        }
        if (javaClass == String.class || javaClass == CharSequence.class) {
            return 1;
        }
        if (javaClass == Character.class || javaClass == char.class) {
            return 2;
        }
        if (javaClass == Double.class || javaClass == double.class) {
            return 10;
        }
        if (javaClass == Float.class || javaClass == float.class) {
            return 11;
        }
        if (javaClass == Long.class || javaClass == long.class) {
            return 12;
        }
        if (javaClass == Integer.class || javaClass == int.class) {
            return 13;
        }
        if (javaClass == Short.class || javaClass == short.class) {
            return 14;
        }
        if (javaClass == Byte.class || javaClass == byte.class) {
            return 15;
        }
        if (javaClass == Boolean.class || javaClass == boolean.class) {
            return 16;
        }
        if (javaClass == Object.class) {
            return 20;
        }
        return Integer.MAX_VALUE;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Item#toJavaObject(java.lang.Class)
     */
    @Override
    public <T> T toJavaObject(final Class<T> target) throws XPathException {
        if (target.isAssignableFrom(UntypedAtomicValue.class)) {
            return (T) this;
        } else if (target == Object.class || target == String.class || target == CharSequence.class) {
            return (T) value;
        } else if (target == double.class || target == Double.class) {
            final DoubleValue v = (DoubleValue) convertTo(Type.DOUBLE);
            return (T) Double.valueOf(v.getValue());
        } else if (target == float.class || target == Float.class) {
            final FloatValue v = (FloatValue) convertTo(Type.FLOAT);
            return (T) Float.valueOf(v.value);
        } else if (target == long.class || target == Long.class) {
            final IntegerValue v = (IntegerValue) convertTo(Type.LONG);
            return (T) Long.valueOf(v.getInt());
        } else if (target == int.class || target == Integer.class) {
            final IntegerValue v = (IntegerValue) convertTo(Type.INT);
            return (T) Integer.valueOf(v.getInt());
        } else if (target == short.class || target == Short.class) {
            final IntegerValue v = (IntegerValue) convertTo(Type.SHORT);
            return (T) Short.valueOf((short) v.getInt());
        } else if (target == byte.class || target == Byte.class) {
            final IntegerValue v = (IntegerValue) convertTo(Type.BYTE);
            return (T) Byte.valueOf((byte) v.getInt());
        } else if (target == boolean.class || target == Boolean.class) {
            return (T) Boolean.valueOf(effectiveBooleanValue());
        } else if (target == char.class || target == Character.class) {
            if (value.length() > 1 || value.isEmpty()) {
                throw new XPathException("cannot convert string with length = 0 or length > 1 to Java character");
            }
            return (T) Character.valueOf(value.charAt(0));
        }

        throw new XPathException(
                "cannot convert value of type "
                        + Type.getTypeName(getType())
                        + " to Java object of type "
                        + target.getName());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof UntypedAtomicValue) {
            return value.equals(((UntypedAtomicValue) obj).value);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
