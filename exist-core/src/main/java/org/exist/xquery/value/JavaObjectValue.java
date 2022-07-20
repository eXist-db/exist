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
import org.exist.xquery.Constants.Comparison;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;

/**
 * Represents a reference to an arbitrary Java object which is treated as an
 * atomic value.
 *
 * @author wolf
 */
public class JavaObjectValue extends AtomicValue {

    private final Object object;

    public JavaObjectValue(final Object object) {
        this(null, object);
    }

    public JavaObjectValue(final Expression expression, Object object) {
        super(expression);
        this.object = object;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.AtomicValue#getType()
     */
    public int getType() {
        return Type.JAVA_OBJECT;
    }

    public Object getObject() {
        return object;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#getStringValue()
     */
    public String getStringValue() {
        return String.valueOf(object);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#convertTo(int)
     */
    public AtomicValue convertTo(int requiredType) throws XPathException {
        if (requiredType == Type.JAVA_OBJECT) {
            return this;
        }
        throw new XPathException(getExpression(), ErrorCodes.FORG0001,
                "cannot convert Java object to " + Type.getTypeName(requiredType));
    }

    public boolean effectiveBooleanValue() throws XPathException {
        throw new XPathException(getExpression(), "Called effectiveBooleanValue() on JavaObjectValue");
    }

    @Override
    public boolean compareTo(Collator collator, Comparison operator, AtomicValue other) throws XPathException {
        throw new XPathException(getExpression(), 
                "cannot compare Java object to " + Type.getTypeName(other.getType()));
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.AtomicValue#compareTo(org.exist.xquery.value.AtomicValue)
     */
    public int compareTo(Collator collator, AtomicValue other) throws XPathException {
        throw new XPathException(getExpression(), 
                "cannot compare Java object to " + Type.getTypeName(other.getType()));
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.AtomicValue#max(org.exist.xquery.value.AtomicValue)
     */
    public AtomicValue max(Collator collator, AtomicValue other) throws XPathException {
        throw new XPathException(getExpression(), "Invalid argument to aggregate function: cannot compare Java objects");
    }

    public AtomicValue min(Collator collator, AtomicValue other) throws XPathException {
        throw new XPathException(getExpression(), "Invalid argument to aggregate function: cannot compare Java objects");
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Item#conversionPreference(java.lang.Class)
     */
    public int conversionPreference(Class<?> javaClass) {
        if (javaClass.isAssignableFrom(object.getClass())) {
            return 0;
        }

        return Integer.MAX_VALUE;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Item#toJavaObject(java.lang.Class)
     */
    @Override
    public <T> T toJavaObject(final Class<T> target) throws XPathException {
        if (target.isAssignableFrom(object.getClass())) {
            return (T) object;
        } else if (target == Object.class) {
            return (T) object;
        }

        throw new XPathException(getExpression(), "cannot convert value of type " + Type.getTypeName(getType()) + " to Java object of type " + target.getName());
    }
}
