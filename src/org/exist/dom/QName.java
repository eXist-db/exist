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
package org.exist.dom;

import org.exist.storage.ElementValue;
import org.exist.util.XMLChar;
import org.exist.xquery.Constants;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;

/**
 * Represents a QName, consisting of a local name, a namespace URI and a prefix.
 * 
 * @author Wolfgang <wolfgang@exist-db.org>
 */
public class QName implements Comparable {

	/*
    public final static QName DOCUMENT_QNAME = new QName("#document", "", null);
	public final static QName TEXT_QNAME = new QName("#text", "", null);
	public final static QName COMMENT_QNAME = new QName("#comment", "", null);
	public final static QName DOCTYPE_QNAME = new QName("#doctype", "", null);
	*/ 
	public final static QName EMPTY_QNAME = new QName("", "", null);
    public final static QName DOCUMENT_QNAME = EMPTY_QNAME;
	public final static QName TEXT_QNAME = EMPTY_QNAME;
	public final static QName COMMENT_QNAME = EMPTY_QNAME;
	public final static QName DOCTYPE_QNAME = EMPTY_QNAME; 
	
	private String localName_ = null;
	private String namespaceURI_ = null;
	private String prefix_ = null;
	//TODO ; use ElementValue.INKNOWN and type explicitely ?
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
		if(namespaceURI == null)
		    namespaceURI_ = "";
		else
		    namespaceURI_ = namespaceURI;
		prefix_ = prefix;
	}

	public QName(String localName, String namespaceURI) {
		this(localName, namespaceURI, null);
	}

	public QName(QName other) {
	    this(other.localName_, other.namespaceURI_, other.prefix_);
	    nameType_ = other.nameType_;
	}
	
	public QName(String name) {
		this(extractLocalName(name), null, extractPrefix(name));
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

	public String getStringValue() {
		if (prefix_ != null && prefix_.length() > 0)
			return prefix_ + ':' + localName_;
		else 
			return localName_;
	}

	/**
	 * (deprecated) : use for debugging purpose only,
	 * use getStringValue() for production
	 */
	public String toString() {
		//TODO : remove this copy of getStringValue()
		return getStringValue();
		//TODO : replace by something like this
		/*
		if (prefix_ != null && prefix_.length() > 0)
			return prefix_ + ':' + localName_;
		if (needsNamespaceDecl()) {
			if (prefix_ != null && prefix_.length() > 0)
				return "{" + namespaceURI_ + "}" + prefix_ + ':' + localName_;
			return "{" + namespaceURI_ + "}" + localName_;
		} else 
			return localName_;
		*/
	}

	/**
	 * Compares two QNames by comparing namespace URI
	 * and local names. The prefixes are not relevant.
	 * 
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	public int compareTo(Object o) {
		QName other = (QName) o;
		if(nameType_ != other.nameType_) {
			return nameType_ < other.nameType_ ? Constants.INFERIOR : Constants.SUPERIOR;
		}
		int c;
		if (namespaceURI_ == null)
			c = other.namespaceURI_ == null ? Constants.EQUAL : Constants.INFERIOR;
		else if (other.namespaceURI_ == null)
			c = Constants.SUPERIOR;
		else
			c = namespaceURI_.compareTo(other.namespaceURI_);
		return c == Constants.EQUAL ? localName_.compareTo(other.localName_) : c;
	}
	
	/** 
	 * Checks two QNames for equality. Two QNames are equal
	 * if their namespace URIs, local names and prefixes are equal.
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	public boolean equals(Object obj) {
		int cmp = compareTo(obj);
		if(cmp != 0)
			return false;
		QName other = (QName) obj;
		if(prefix_ == null)
			return other.prefix_ == null ? true : false;
		else if(other.prefix_ == null)
			return false;
		else
			return prefix_.equals(other.prefix_);
	}
	
	public boolean equalsSimple(QName other) {
        int c;
        if (namespaceURI_ == null)
            c = other.namespaceURI_ == null ? Constants.EQUAL : Constants.INFERIOR;
        else if (other.namespaceURI_ == null)
            c = Constants.SUPERIOR;
        else
            c = namespaceURI_.compareTo(other.namespaceURI_);
        if (c == Constants.EQUAL)
            return localName_.equals(other.localName_);
        return false;
    }
    
	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	public int hashCode() {
		int h = nameType_ + 31 + localName_.hashCode();
		h += 31*h + (namespaceURI_ == null ? 1 : namespaceURI_.hashCode());
		h += 31*h + (prefix_ == null ? 1 : prefix_.hashCode());
		return h;
	}

    public javax.xml.namespace.QName toJavaQName() {
        return new javax.xml.namespace.QName(namespaceURI_ == null ? "" : namespaceURI_, localName_,
                prefix_ == null ? "" : prefix_);
    }
    
    /**
	 * Extract the prefix from a QName string.
	 *  
	 * @param qname
	 * @return the prefix, if found
	 * @exception IllegalArgumentException if the qname starts with a leading :
	 */
	public static String extractPrefix(String qname) 
    throws IllegalArgumentException {
		int p = qname.indexOf(':');
		if (p == Constants.STRING_NOT_FOUND)
			return null;
		if (p == 0)
			throw new IllegalArgumentException("Illegal QName: starts with a :");
        // fixme! Should we not use isQName() here? /ljo
        if (Character.isDigit(qname.substring(0,1).charAt(0))) {
            throw new IllegalArgumentException("Illegal QName: starts with a digit");
        }
		return qname.substring(0, p);
	}

	/**
	 * Extract the local name from a QName string.
	 * 
	 * @param qname
	 * @exception IllegalArgumentException if the qname starts with a leading : or ends with a :
	 */
	public static String extractLocalName(String qname) 
        throws IllegalArgumentException {
		int p = qname.indexOf(':');
		if (p == Constants.STRING_NOT_FOUND)
			return qname;
		if (p == 0)
			throw new IllegalArgumentException("Illegal QName: starts with a :");
		if (p == qname.length())
			throw new IllegalArgumentException("Illegal QName: ends with a :");
        if (!isQName(qname)) {
			throw new IllegalArgumentException("Illegal QName: not a valid local name.");
        }

		return qname.substring(p + 1);
	}

	/**
	 * Parses the given string into a QName. The method uses context to look up
	 * a namespace URI for an existing prefix.
	 * 
	 * @param context
	 * @param qname
	 * @param defaultNS the default namespace to use if no namespace prefix is present.
     * @return QName
	 * @exception IllegalArgumentException if no namespace URI is mapped to the prefix
	 */
	public static QName parse(XQueryContext context, String qname, String defaultNS)
		throws XPathException {
		String prefix = extractPrefix(qname);
		String namespaceURI;
		if (prefix != null) {
			namespaceURI = context.getURIForPrefix(prefix);
			if (namespaceURI == null)
				throw new XPathException("XPST0081: No namespace defined for prefix " + prefix);
		} else
			namespaceURI = defaultNS;
		if (namespaceURI == null)
			namespaceURI = "";
		return new QName(extractLocalName(qname), namespaceURI, prefix);
	}
	
	/**
	 * Parses the given string into a QName. The method uses context to look up
	 * a namespace URI for an existing prefix.
	 * 
	 * This method uses the default element namespace for qnames without prefix.
	 * 
	 * @param context
	 * @param qname
	 * @exception IllegalArgumentException if no namespace URI is mapped to the prefix
	 */
	public static QName parse(XQueryContext context, String qname)
		throws XPathException {
		return parse(context, qname, context.getURIForPrefix(""));
	}
	
	public final static boolean isQName(String name) {
		int colon = name.indexOf(':');
		if (colon == Constants.STRING_NOT_FOUND)
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
