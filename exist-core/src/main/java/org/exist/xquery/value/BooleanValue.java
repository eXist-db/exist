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

import com.ibm.icu.text.Collator;
import org.exist.xquery.Constants;
import org.exist.xquery.Constants.Comparison;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.XPathException;

public class BooleanValue extends AtomicValue {

    public final static BooleanValue TRUE = new BooleanValue(true);
    public final static BooleanValue FALSE = new BooleanValue(false);

    private final boolean value;

    public BooleanValue(boolean bool) {
        value = bool;
    }

    /**
     * Returns one of the static fields TRUE or FALSE depending on
     * the value of the parameter.
     *
     * @param bool the boolean value to map
     * @return either {@link #TRUE} or {@link #FALSE}
     */
    public final static BooleanValue valueOf(boolean bool) {
        return bool ? TRUE : FALSE;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.AtomicValue#getType()
     */
    public int getType() {
        return Type.BOOLEAN;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Item#getStringValue()
     */
    public String getStringValue() throws XPathException {
        return value ? "true" : "false";
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.AtomicValue#convertTo(int)
     */
    public AtomicValue convertTo(int requiredType) throws XPathException {
        switch (requiredType) {
            case Type.BOOLEAN:
            case Type.ATOMIC:
            case Type.ITEM:
                return this;
            case Type.NUMBER:
            case Type.INTEGER:
                return new IntegerValue(value ? 1 : 0);
            case Type.DECIMAL:
                return new DecimalValue(value ? 1 : 0);
            case Type.FLOAT:
                return new FloatValue(value ? 1 : 0);
            case Type.DOUBLE:
                return new DoubleValue(value ? 1 : 0);
            case Type.STRING:
                return new StringValue(getStringValue());
            case Type.UNTYPED_ATOMIC:
                return new UntypedAtomicValue(getStringValue());
            default:
                throw new XPathException(ErrorCodes.XPTY0004,
                        "cannot convert 'xs:boolean(" + value + ")' to " + Type.getTypeName(requiredType));
        }
    }

    @Override
    public boolean compareTo(Collator collator, Comparison operator, AtomicValue other) throws XPathException {
        if (Type.subTypeOf(other.getType(), Type.BOOLEAN)) {
            boolean otherVal = ((BooleanValue) other).getValue();
            switch (operator) {
                case EQ:
                    return value == otherVal;
                case NEQ:
                    return value != otherVal;
                case LT:
                    return (!value) && otherVal;
                case LTEQ:
                    return value == otherVal || (!value) && otherVal;
                case GT:
                    return value && (!otherVal);
                case GTEQ:
                    return value == otherVal || value && (!otherVal);
                default:
                    throw new XPathException("Type error: cannot apply this operator to a boolean value");
            }
        }
        throw new XPathException(ErrorCodes.XPTY0004,
                "cannot convert 'xs:boolean(" + value + ")' to " + Type.getTypeName(other.getType()));
    }

    public int compareTo(Collator collator, AtomicValue other) throws XPathException {
        final boolean otherVal = other.effectiveBooleanValue();
        if (otherVal == value) {
            return Constants.EQUAL;
        } else if (value) {
            return Constants.SUPERIOR;
        } else {
            return Constants.INFERIOR;
        }
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.AtomicValue#effectiveBooleanValue()
     */
    public boolean effectiveBooleanValue() throws XPathException {
        return value;
    }

    public boolean getValue() {
        return value;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.AtomicValue#max(org.exist.xquery.value.AtomicValue)
     */
    public AtomicValue max(Collator collator, AtomicValue other) throws XPathException {
        if (other.getType() == Type.BOOLEAN) {
            boolean otherValue = ((BooleanValue) other).value;
            return value && (!otherValue) ? this : other;
        } else {
            throw new XPathException(
                    "Invalid argument to aggregate function: expected boolean, got: "
                            + Type.getTypeName(other.getType()));
        }
    }

    public AtomicValue min(Collator collator, AtomicValue other) throws XPathException {
        if (other.getType() == Type.BOOLEAN) {
            final boolean otherValue = ((BooleanValue) other).value;
            return (!value) && otherValue ? this : other;
        } else {
            throw new XPathException(
                    "Invalid argument to aggregate function: expected boolean, got: "
                            + Type.getTypeName(other.getType()));
        }
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Item#conversionPreference(java.lang.Class)
     */
    public int conversionPreference(Class<?> javaClass) {
        if (javaClass.isAssignableFrom(BooleanValue.class)) {
            return 0;
        }
        if (javaClass == Boolean.class || javaClass == boolean.class) {
            return 1;
        }
        if (javaClass == Object.class) {
            return 20;
        }
        if (javaClass == String.class || javaClass == CharSequence.class) {
            return 2;
        }

        return Integer.MAX_VALUE;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Item#toJavaObject(java.lang.Class)
     */
    @Override
    public <T> T toJavaObject(final Class<T> target) throws XPathException {
        if (target.isAssignableFrom(BooleanValue.class)) {
            return (T) this;
        } else if (target == Boolean.class || target == boolean.class || target == Object.class) {
            return (T) Boolean.valueOf(value);
        } else if (target == String.class || target == CharSequence.class) {
            final StringValue v = (StringValue) convertTo(Type.STRING);
            return (T) v.value;
        }

        throw new XPathException("cannot convert value of type " + Type.getTypeName(getType()) +
                " to Java object of type " + target.getName());
    }

    /* (non-Javadoc)
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    public int compareTo(Object o) {
        final AtomicValue other = (AtomicValue) o;
        if (Type.subTypeOf(other.getType(), Type.BOOLEAN)) {
            if (value == ((BooleanValue) other).value) {
                return Constants.EQUAL;
            } else if (value) {
                return Constants.SUPERIOR;
            } else {
                return Constants.INFERIOR;
            }
        } else {
            return getType() < other.getType() ? Constants.INFERIOR : Constants.SUPERIOR;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof BooleanValue) {
            return value == ((BooleanValue) obj).value;
        }
        return false;
    }
}
