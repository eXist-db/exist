/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
 *  meier@ifs.tu-darmstadt.de
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
package org.exist.dom;

import org.exist.storage.ElementValue;
import org.exist.util.XMLChar;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.XPathException;

/**
 * Represents a QName, consisting of a local name, a namespace URI and a prefix.
 * 
 * @author Wolfgang <wolfgang@exist-db.org>
 */
public class QName implements Comparable {

	public final static QName TEXT_QNAME = new QName("#text", "", null);
	public final static QName COMMENT_QNAME = new QName("#comment", "", null);
	public final static QName DOCTYPE_QNAME = new QName("#doctype", "", null);

	private String localName_ = null;
	private String namespaceURI_ = null;
	private String prefix_ = null;
	private byte nameType_ = ElementValue.ELEMENT;

	/**
	 * Construct a QName. The prefix might be null for the default namespace or if no prefix 
	 * has been defined for the QName. The namespace URI should be set to the empty 
	 * string, if no namespace URI is defined.
	 * 
	 * @param localName
	 * @param namespaceURI
	 * @param prefix
	 */
	public QName(String localName, String namespaceURI, String prefix) {
		localName_ = localName;
		namespaceURI_ = namespaceURI;
		prefix_ = prefix;
	}

	public QName(String localName, String namespaceURI) {
		this(localName, namespaceURI, null);
	}

	public String getLocalName() {
		return localName_;
	}

	public void setLocalName(String name) {
		localName_ = name;
	}

	public String getNamespaceURI() {
		return namespaceURI_;
	}

	public void setNamespaceURI(String namespaceURI) {
		namespaceURI_ = namespaceURI;
	}

	/**
	 * Returns true if the QName defines a namespace URI.
	 * 
	 * @return
	 */
	public boolean needsNamespaceDecl() {
		return namespaceURI_ != null && namespaceURI_.length() > 0;
	}

	public String getPrefix() {
		return prefix_;
	}

	public void setPrefix(String prefix) {
		prefix_ = prefix;
	}

	public void setNameType(byte type) {
		nameType_ = type;
	}

	public byte getNameType() {
		return nameType_;
	}

	public String toString() {
		if (prefix_ != null && prefix_.length() > 0)
			return prefix_ + ':' + localName_;
		else
			return localName_;
	}

	/* (non-Javadoc)
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	public int compareTo(Object o) {
		QName other = (QName) o;
		if(nameType_ != other.nameType_) {
			return nameType_ < other.nameType_ ? -1 : 1;
		}
		int c;
		if (namespaceURI_ == null)
			c = other.namespaceURI_ == null ? 0 : -1;
		else if (other.namespaceURI_ == null)
			c = 1;
		else
			c = namespaceURI_.compareTo(other.namespaceURI_);
		return c == 0 ? localName_.compareTo(other.localName_) : c;
	}

	/**
	 * Extract the prefix from a QName string.
	 *  
	 * @param qname
	 * @return the prefix, if found
	 * @exception IllegalArgumentException if the qname starts with a leading :
	 */
	public static String extractPrefix(String qname) {
		int p = qname.indexOf(':');
		if (p < 0)
			return null;
		if (p == 0)
			throw new IllegalArgumentException("Illegal QName: starts with a :");
		return qname.substring(0, p);
	}

	/**
	 * Extract the local name from a QName string.
	 * 
	 * @param qname
	 * @return
	 * @exception IllegalArgumentException if the qname starts with a leading : or ends with a :
	 */
	public static String extractLocalName(String qname) {
		int p = qname.indexOf(':');
		if (p < 0)
			return qname;
		if (p == 0)
			throw new IllegalArgumentException("Illegal QName: starts with a :");
		if (p == qname.length())
			throw new IllegalArgumentException("Illegal QName: ends with a :");
		return qname.substring(p + 1);
	}

	/**
	 * Parses the given string into a QName. The method uses context to look up
	 * a namespace URI for an existing prefix.
	 * 
	 * @param context
	 * @param qname
	 * @return
	 * @exception IllegalArgumentException if no namespace URI is mapped to the prefix
	 */
	public static QName parse(XQueryContext context, String qname)
		throws XPathException {
		String prefix = extractPrefix(qname);
		String namespaceURI;
		if (prefix != null) {
			namespaceURI = context.getURIForPrefix(prefix);
			if (namespaceURI == null)
				throw new XPathException("No namespace defined for prefix " + prefix);
		} else
			namespaceURI = context.getURIForPrefix("");
		if (namespaceURI == null)
			namespaceURI = "";
		return new QName(extractLocalName(qname), namespaceURI, prefix);
	}

	public static QName parseAttribute(XQueryContext context, String qname)
		throws XPathException {
		String prefix = extractPrefix(qname);
		String namespaceURI = null;
		if (prefix != null) {
			namespaceURI = context.getURIForPrefix(prefix);
			if (namespaceURI == null)
				throw new XPathException("No namespace defined for prefix " + prefix);
		}
		if (namespaceURI == null)
			namespaceURI = "";
		return new QName(extractLocalName(qname), namespaceURI, prefix);
	}

	public static QName parseFunction(XQueryContext context, String qname)
		throws XPathException {
		String prefix = extractPrefix(qname);
		String namespaceURI;
		if (prefix != null) {
			namespaceURI = context.getURIForPrefix(prefix);
			if (namespaceURI == null)
				throw new XPathException("No namespace defined for prefix " + prefix);
		} else
			namespaceURI = context.getDefaultFunctionNamespace();
		if (namespaceURI == null)
			namespaceURI = "";
		return new QName(extractLocalName(qname), namespaceURI, prefix);
	}

	public final static boolean isQName(String name) {
		int colon = name.indexOf(':');
		if (colon < 0)
			return XMLChar.isValidNCName(name);
		if (colon == 0 || colon == name.length() - 1)
			return false;
		if (!XMLChar.isValidNCName(name.substring(0, colon)))
			return false;
		if (!XMLChar.isValidNCName(name.substring(colon + 1)))
			return false;
		return true;
	}
}
