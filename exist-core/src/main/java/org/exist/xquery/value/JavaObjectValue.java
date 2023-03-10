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

    public JavaObjectValue(final Expression expression, final Object object) {
        super(expression);
        this.object = object;
    }

    @Override
    public int getType() {
        return Type.JAVA_OBJECT;
    }

    public Object getObject() {
        return object;
    }

    @Override
    public String getStringValue() {
        return String.valueOf(object);
    }

    @Override
    public AtomicValue convertTo(final int requiredType) throws XPathException {
        if (requiredType == Type.JAVA_OBJECT) {
            return this;
        }
        throw new XPathException(getExpression(), ErrorCodes.FORG0001,
                "cannot convert Java object to " + Type.getTypeName(requiredType));
    }

    @Override
    public boolean effectiveBooleanValue() throws XPathException {
        throw new XPathException(getExpression(), "Called effectiveBooleanValue() on JavaObjectValue");
    }

    @Override
    public boolean compareTo(final Collator collator, final Comparison operator, final AtomicValue other) throws XPathException {
        throw new XPathException(getExpression(),
                "cannot compare Java object to " + Type.getTypeName(other.getType()));
    }

    @Override
    public int compareTo(final Collator collator, final AtomicValue other) throws XPathException {
        throw new XPathException(getExpression(),
                "cannot compare Java object to " + Type.getTypeName(other.getType()));
    }

    @Override
    public AtomicValue max(final Collator collator, final AtomicValue other) throws XPathException {
        throw new XPathException(getExpression(), "Invalid argument to aggregate function: cannot compare Java objects");
    }

    @Override
    public AtomicValue min(final Collator collator, final AtomicValue other) throws XPathException {
        throw new XPathException(getExpression(), "Invalid argument to aggregate function: cannot compare Java objects");
    }

    @Override
    public int conversionPreference(final Class<?> javaClass) {
        if (javaClass.isAssignableFrom(object.getClass())) {
            return 0;
        }

        return Integer.MAX_VALUE;
    }

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
