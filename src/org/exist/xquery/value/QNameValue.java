/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2007 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.value;

import java.text.Collator;

import org.exist.dom.QName;
import org.exist.xquery.Constants;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;

/**
 * Wrapper class around a {@link org.exist.dom.QName} value which extends
 * {@link org.exist.xquery.value.AtomicValue}.
 * 
 * @author wolf
 */
public class QNameValue extends AtomicValue {

	private XQueryContext context;
	private QName qname;

    /**
     * Constructs a new QNameValue by parsing the given name using
     * the namespace declarations in context.
     * 
     * @param context
     * @param name
     * @throws XPathException
     */
	public QNameValue(XQueryContext context, String name) throws XPathException {
        if (name.length() == 0)
            throw new XPathException("err:FORG0001: An empty string is not a valid lexical representation of xs:QName.");
	    this.context = context;
	    try {
	    	this.qname = QName.parse(context, name, context.getURIForPrefix(""));
	    } catch (Exception e) {
	    	throw new XPathException(e);
	    }
    }
    
	public QNameValue(XQueryContext context, QName name) {
		this.context = context;
		this.qname = name;
	}

	/**
	 * @see org.exist.xquery.value.AtomicValue#getType()
	 */
	public int getType() {
		return Type.QNAME;
	}

    /**
     * Returns the wrapped QName object.
     */
	public QName getQName() {
		return qname;
	}
	
	/**
	 * @see org.exist.xquery.value.Sequence#getStringValue()
	 */
	public String getStringValue() throws XPathException {
		String prefix = null;
        
	    if(qname.needsNamespaceDecl()) {
	    	prefix = context.getPrefixForURI(qname.getNamespaceURI());
			if (prefix != null)
				qname.setPrefix(prefix);
				//throw new XPathException(
				//	"namespace " + qname.getNamespaceURI() + " is not defined");
			
	    }
		if (prefix != null && prefix.length() > 0)
			return prefix + ':' + qname.getLocalName();
		else 
			return qname.getLocalName();
	}

	/**
	 * @see org.exist.xquery.value.Sequence#convertTo(int)
	 */
	public AtomicValue convertTo(int requiredType) throws XPathException {
		switch (requiredType) {
			case Type.ATOMIC :
			case Type.ITEM :
			case Type.QNAME :
				return this;
			case Type.STRING :
				return new StringValue( getStringValue() );
            case Type.UNTYPED_ATOMIC :
                return new UntypedAtomicValue(getStringValue());
			default :
				throw new XPathException(
					"A QName cannot be converted to " + Type.getTypeName(requiredType));
		}
	}

	/**
	 * @see org.exist.xquery.value.AtomicValue#compareTo(Collator, int, AtomicValue)
	 */
	public boolean compareTo(Collator collator, int operator, AtomicValue other) throws XPathException {
		if (other.getType() == Type.QNAME) {
			int cmp = qname.compareTo(((QNameValue) other).qname);
			switch (operator) {
				case Constants.EQ :
					return cmp == 0;
				case Constants.NEQ :
					return cmp != 0;
				/*
				 * QNames are unordered
				case Constants.GT :
					return cmp > 0;
				case Constants.GTEQ :
					return cmp >= 0;
				case Constants.LT :
					return cmp < 0;
				case Constants.LTEQ :
					return cmp >= 0;
				*/
				default :
					throw new XPathException("XPTY0004 : cannot apply operator to QName");
			}
		} else
			throw new XPathException(
				"Type error: cannot compare QName to "
					+ Type.getTypeName(other.getType()));
	}

	/**
	 * @see org.exist.xquery.value.AtomicValue#compareTo(Collator, AtomicValue)
	 */
	public int compareTo(Collator collator, AtomicValue other) throws XPathException {
		if (other.getType() == Type.QNAME) {
			return qname.compareTo(((QNameValue) other).qname);
		} else
			throw new XPathException(
				"Type error: cannot compare QName to "
					+ Type.getTypeName(other.getType()));
	}

	/**
	 * @see org.exist.xquery.value.AtomicValue#max(Collator, AtomicValue)
	 */
	public AtomicValue max(Collator collator, AtomicValue other) throws XPathException {
		throw new XPathException("Invalid argument to aggregate function: QName");
	}

	public AtomicValue min(Collator collator, AtomicValue other) throws XPathException {
		throw new XPathException("Invalid argument to aggregate function: QName");
	}

	/**
	 * @see org.exist.xquery.value.Item#conversionPreference(java.lang.Class)
	 */
	public int conversionPreference(Class javaClass) {
		if (javaClass.isAssignableFrom(QNameValue.class))
			return 0;
		if (javaClass == String.class)
			return 1;
		if (javaClass == Object.class)
			return 20;

		return Integer.MAX_VALUE;
	}

	/**
	 * @see org.exist.xquery.value.Item#toJavaObject(java.lang.Class)
	 */
	public Object toJavaObject(Class target) throws XPathException {
		if (target.isAssignableFrom(QNameValue.class))
			return this;
		else if (target == String.class)
			return getStringValue();
		else if (target == Object.class)
			return qname;

		throw new XPathException(
			"cannot convert value of type "
				+ Type.getTypeName(getType())
				+ " to Java object of type "
				+ target.getName());
	}
	
	public String toString() {
		try {
			return this.getStringValue();
		} catch (XPathException e) {
			return super.toString();
		}			
	}
    
    public boolean effectiveBooleanValue() throws XPathException {
        throw new XPathException("err:FORG0006: value of type " + Type.getTypeName(getType()) +
            " has no boolean value.");
    }
}
