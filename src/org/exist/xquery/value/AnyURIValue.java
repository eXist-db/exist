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

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.Collator;

import org.exist.xquery.Constants;
import org.exist.xquery.XPathException;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class AnyURIValue extends AtomicValue {

	private String uri;

	public AnyURIValue(URI uri) {
		this.uri = uri.toString();
	}
	public AnyURIValue(String s) throws XPathException {
		try {
			URI uri = new URI(s);
		} catch (URISyntaxException e) {
			throw new XPathException(
				"Type error: the given string " + s + " cannot be cast to xs:anyURI");
		}
		this.uri = s;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AtomicValue#getType()
	 */
	public int getType() {
		return Type.ANY_URI;
	}
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#getStringValue()
	 */
	public String getStringValue() throws XPathException {
		return uri;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#convertTo(int)
	 */
	public AtomicValue convertTo(int requiredType) throws XPathException {
		switch (requiredType) {
			case Type.ITEM :
			case Type.ATOMIC :
			case Type.ANY_URI :
				return this;
			case Type.STRING :
				return new StringValue(uri);
			case Type.UNTYPED_ATOMIC :
				return new UntypedAtomicValue(getStringValue());
			default :
				throw new XPathException(
					"Type error: cannot cast xs:anyURI to "
						+ Type.getTypeName(requiredType));
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AtomicValue#compareTo(int, org.exist.xquery.value.AtomicValue)
	 */
	public boolean compareTo(Collator collator, int operator, AtomicValue other) throws XPathException {
		if (other.getType() == Type.ANY_URI) {
			String otherURI = other.getStringValue();
			int cmp = uri.compareTo(otherURI);
			switch (operator) {
				case Constants.EQ :
					return cmp == 0;
				case Constants.NEQ :
					return cmp != 0;
				default :
					throw new XPathException(
						"Type error: cannot apply operator "
							+ Constants.OPS[operator]
							+ " to xs:anyURI");
			}
		} else
			return compareTo(collator, operator, other.convertTo(Type.ANY_URI));
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AtomicValue#compareTo(org.exist.xquery.value.AtomicValue)
	 */
	public int compareTo(Collator collator, AtomicValue other) throws XPathException {
		if (other.getType() == Type.ANY_URI) {
			String otherURI = other.getStringValue();
			return uri.compareTo(otherURI);
		} else {
			return compareTo(collator, other.convertTo(Type.ANY_URI));
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AtomicValue#max(org.exist.xquery.value.AtomicValue)
	 */
	public AtomicValue max(Collator collator, AtomicValue other) throws XPathException {
		throw new XPathException("max is not supported for values of type xs:anyURI");
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AtomicValue#min(org.exist.xquery.value.AtomicValue)
	 */
	public AtomicValue min(Collator collator, AtomicValue other) throws XPathException {
		throw new XPathException("min is not supported for values of type xs:anyURI");
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#conversionPreference(java.lang.Class)
	 */
	public int conversionPreference(Class javaClass) {
		if (javaClass.isAssignableFrom(AnyURIValue.class))
			return 0;
		if (javaClass == URI.class)
			return 1;
		if (javaClass == URL.class)
			return 2;
		if (javaClass == String.class || javaClass == CharSequence.class)
			return 3;
		if (javaClass == Object.class)
			return 20;
		return Integer.MAX_VALUE;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#toJavaObject(java.lang.Class)
	 */
	public Object toJavaObject(Class target) throws XPathException {
		if (target.isAssignableFrom(AnyURIValue.class))
			return this;
		else if (target == URI.class)
			try {
				return new URI(uri);
			} catch (URISyntaxException e) {
				throw new XPathException(
					"failed to convert " + uri + " into a Java URI: " + e.getMessage(),
					e);
			} else if (target == URL.class)
			try {
				return new URL(uri);
			} catch (MalformedURLException e) {
				throw new XPathException(
					"failed to convert " + uri + " into a Java URL: " + e.getMessage(),
					e);
			} else if (target == String.class || target == CharSequence.class)
			return uri;
		else if (target == Object.class)
			return uri;

		throw new XPathException(
			"cannot convert value of type "
				+ Type.getTypeName(getType())
				+ " to Java object of type "
				+ target.getName());
	}

}
