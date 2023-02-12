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
import org.exist.dom.QName;
import org.exist.xquery.Constants.Comparison;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;


/**
 * Wrapper class around a {@link org.exist.dom.QName} value which extends
 * {@link org.exist.xquery.value.AtomicValue}.
 *
 * @author wolf
 */
public class QNameValue extends AtomicValue {

    private final QName qname;
    private final String stringValue;

    /**
     * Constructs a new QNameValue by parsing the given name using
     * the namespace declarations in context.
     *
     * @param context current context
     * @param name name string to parse into QName
     * @throws XPathException in case of dynamic error
     */
    public QNameValue(final XQueryContext context, final String name) throws XPathException {
        this(null, context, name);
    }

    public QNameValue(final Expression expression, XQueryContext context, String name) throws XPathException {
        super(expression);
        if (name.isEmpty()) {
            throw new XPathException(getExpression(), ErrorCodes.FORG0001, "An empty string is not a valid lexical representation of xs:QName.");
        }

        try {
            qname = QName.parse(context, name, context.getURIForPrefix(""));
        } catch (final QName.IllegalQNameException iqe) {
            throw new XPathException(getExpression(), ErrorCodes.XPST0081, "No namespace defined for prefix " + name);
        }
        stringValue = computeStringValue();
    }

    public QNameValue(XQueryContext context, QName name) {
        this(null, context, name);
    }

    public QNameValue(final Expression expression, XQueryContext context, QName name) {
        super(expression);
        this.qname = name;
        stringValue = computeStringValue();
    }

    /**
     * @see org.exist.xquery.value.AtomicValue#getType()
     */
    public int getType() {
        return Type.QNAME;
    }

    /**
     * Returns the wrapped QName object.
     *
     * @return the wrapped QName
     */
    public QName getQName() {
        return qname;
    }

    /**
     * @see org.exist.xquery.value.Sequence#getStringValue()
     */
    public String getStringValue() throws XPathException {
        //TODO : previous approach was to resolve the qname when needed. We now try to keep the original qname
        return stringValue;
    }

    private String computeStringValue() {
        //TODO : previous approach was to resolve the qname when needed. We now try to keep the original qname
        final String prefix = qname.getPrefix();
        //Not clear what to work with here...
        // WM: Changing the prefix is problematic (e.g. if a module
        // defines different prefixes than the main module). We should
        // keep the current in-scope prefix.
//	    if((prefix == null || "".equals(prefix)) && qname.hasNamespace()) {
//	    	prefix = context.getPrefixForURI(qname.getNamespaceURI());
//			if (prefix != null)
//				qname.setPrefix(prefix);
//				//throw new XPathException(getExpression(), 
//				//	"namespace " + qname.getNamespaceURI() + " is not defined");
//
//	    }
        //TODO : check that the prefix matches the URI in the current context ?
        if (prefix != null && !prefix.isEmpty()) {
            return prefix + ':' + qname.getLocalPart();
        } else {
            return qname.getLocalPart();
        }
    }

    @Override
    public AtomicValue convertTo(final int requiredType) throws XPathException {
        switch (requiredType) {
            case Type.ANY_ATOMIC_TYPE:
            case Type.ITEM:
            case Type.QNAME:
                return this;
            case Type.STRING:
                return new StringValue(getExpression(), getStringValue());
            case Type.UNTYPED_ATOMIC:
                return new UntypedAtomicValue(getExpression(), getStringValue());
            default:
                throw new XPathException(getExpression(), ErrorCodes.FORG0001,
                        "A QName cannot be converted to " + Type.getTypeName(requiredType));
        }
    }

    @Override
    public boolean compareTo(Collator collator, Comparison operator, AtomicValue other) throws XPathException {
        if (other.getType() == Type.QNAME) {
            final int cmp = qname.compareTo(((QNameValue) other).qname);
            return switch (operator) {
                case EQ -> cmp == 0;
                case NEQ -> cmp != 0;
                /*
				 * QNames are unordered
				case GT :
					return cmp > 0;
				case GTEQ :
					return cmp >= 0;
				case LT :
					return cmp < 0;
				case LTEQ :
					return cmp >= 0;
				*/
                default ->
                        throw new XPathException(getExpression(), ErrorCodes.XPTY0004, "cannot apply operator to QName");
            };
        } else {
            throw new XPathException(getExpression(), ErrorCodes.XPTY0004, "Type error: cannot compare QName to "
                    + Type.getTypeName(other.getType()));
        }
    }

    /**
     * @see org.exist.xquery.value.AtomicValue#compareTo(Collator, AtomicValue)
     */
    public int compareTo(Collator collator, AtomicValue other) throws XPathException {
        if (other.getType() == Type.QNAME) {
            return qname.compareTo(((QNameValue) other).qname);
        } else {
            throw new XPathException(getExpression(), 
                    "Type error: cannot compare QName to "
                            + Type.getTypeName(other.getType()));
        }
    }

    /**
     * @see org.exist.xquery.value.AtomicValue#max(Collator, AtomicValue)
     */
    public AtomicValue max(Collator collator, AtomicValue other) throws XPathException {
        throw new XPathException(getExpression(), "Invalid argument to aggregate function: QName");
    }

    public AtomicValue min(Collator collator, AtomicValue other) throws XPathException {
        throw new XPathException(getExpression(), "Invalid argument to aggregate function: QName");
    }

    /**
     * @see org.exist.xquery.value.Item#conversionPreference(java.lang.Class)
     */
    public int conversionPreference(Class<?> javaClass) {
        if (javaClass.isAssignableFrom(QNameValue.class)) {
            return 0;
        }
        if (javaClass == String.class) {
            return 1;
        }
        if (javaClass == Object.class) {
            return 20;
        }

        return Integer.MAX_VALUE;
    }

    /**
     * @see org.exist.xquery.value.Item#toJavaObject(java.lang.Class)
     */
    @Override
    public <T> T toJavaObject(final Class<T> target) throws XPathException {
        if (target.isAssignableFrom(QNameValue.class)) {
            return (T) this;
        } else if (target == String.class) {
            return (T) getStringValue();
        } else if (target == Object.class) {
            return (T) qname;
        }

        throw new XPathException(getExpression(), 
                "cannot convert value of type "
                        + Type.getTypeName(getType())
                        + " to Java object of type "
                        + target.getName());
    }

    public String toString() {
        try {
            return this.getStringValue();
        } catch (final XPathException e) {
            return super.toString();
        }
    }

    public boolean effectiveBooleanValue() throws XPathException {
        throw new XPathException(getExpression(), ErrorCodes.FORG0006,
                "value of type " + Type.getTypeName(getType()) +
                        " has no boolean value.");
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof QNameValue) {
            return ((QNameValue) obj).qname.equals(qname);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return qname.hashCode();
    }
}
