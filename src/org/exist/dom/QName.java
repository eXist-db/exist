/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2014 The eXist Project
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

import org.exist.interpreter.Context;
import org.exist.storage.ElementValue;
import org.exist.util.XMLChar;
import org.exist.xquery.Constants;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.XPathException;

import javax.xml.XMLConstants;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a QName, consisting of a local name, a namespace URI and a prefix.
 * 
 * @author Wolfgang <wolfgang@exist-db.org>
 */
public class QName implements Comparable<QName> {

    private static final String WILDCARD = "*";
    private static final char COLON = ':';

    public static final QName EMPTY_QNAME = new QName("", XMLConstants.NULL_NS_URI);
    public static final QName DOCUMENT_QNAME = EMPTY_QNAME;
    public static final QName TEXT_QNAME = EMPTY_QNAME;
    public static final QName COMMENT_QNAME = EMPTY_QNAME;
    public static final QName DOCTYPE_QNAME = EMPTY_QNAME;


    private final String localPart;
    private final String namespaceURI;
    private final String prefix;

    //TODO : use ElementValue.UNKNOWN and type explicitly ?
    private final byte nameType; // = ElementValue.ELEMENT;


    public QName(final String localPart, final String namespaceURI, final String prefix, final byte nameType) {
        this.localPart = localPart;
        if(namespaceURI == null) {
            this.namespaceURI = XMLConstants.NULL_NS_URI;
        } else {
            this.namespaceURI = namespaceURI;
        }
        this.prefix = prefix;
        this.nameType = nameType;
    }

    /**
     * Construct a QName. The prefix might be null for the default namespace or if no prefix 
     * has been defined for the QName. The namespace URI should be set to the empty 
     * string, if no namespace URI is defined.
     * 
     * @param localPart
     * @param namespaceURI
     * @param prefix
     */
    public QName(final String localPart, final String namespaceURI, final String prefix) {
        this(localPart, namespaceURI, prefix, ElementValue.ELEMENT);
    }

    public QName(final String localPart, final String namespaceURI, final byte nameType) {
        this(localPart, namespaceURI, null, nameType);
    }

    public QName(final String localPart, final String namespaceURI) {
        this(localPart, namespaceURI, null);
    }

    public QName(final QName other, final byte nameType) {
        this(other.localPart, other.namespaceURI, other.prefix, nameType);
    }

    public QName(final QName other) {
        this(other.localPart, other.namespaceURI, other.prefix, other.nameType);
    }

    public QName(final String name) {
        this(extractLocalName(name), XMLConstants.NULL_NS_URI, extractPrefix(name));
    }

    public String getLocalPart() {
        return localPart;
    }

    public String getNamespaceURI() {
        return namespaceURI;
    }

    /**
     * Returns true if the QName defines a non-default namespace
     * 
     */
    public boolean hasNamespace() {
        return !namespaceURI.equals(XMLConstants.NULL_NS_URI);
    }

    public String getPrefix() {
        return prefix;
    }

    public byte getNameType() {
        return nameType;
    }

    public String getStringValue() {
        if (prefix != null && prefix.length() > 0) {
            return prefix + COLON + localPart;
        }
        return localPart;
    }

    /**
     * (deprecated) : use for debugging purpose only,
     * use getStringValue() for production
     */
    @Override
    public String toString() {
        //TODO : remove this copy of getStringValue()
        return getStringValue();
        //TODO : replace by something like this
        /*
        if (prefix != null && prefix.length() > 0)
            return prefix + COLON + localPart;
        if (hasNamespace()) {
            if (prefix != null && prefix.length() > 0)
                return "{" + namespaceURI + "}" + prefix + COLON + localPart;
            return "{" + namespaceURI + "}" + localPart;
        } else 
            return localPart;
        */
    }

    /**
     * Compares two QNames by comparing namespace URI
     * and local names. The prefixes are not relevant.
     * 
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    @Override
    public int compareTo(final QName other) {
        final int c = namespaceURI.compareTo(other.namespaceURI);
        return c == Constants.EQUAL ? localPart.compareTo(other.localPart) : c;
    }

    /** 
     * Checks two QNames for equality. Two QNames are equal
     * if their namespace URIs and local names are equal.
     * 
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(final Object other) {
        if(other == this) {
            return true;
        } else if(!(other instanceof QName)) {
            return false;
        } else {
            final QName qnOther = (QName)other;
            return this.namespaceURI.equals(qnOther.namespaceURI)
                && this.localPart.equals(qnOther.localPart);
        }
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return namespaceURI.hashCode() ^ localPart.hashCode();
    }

    public javax.xml.namespace.QName toJavaQName() {
        return new javax.xml.namespace.QName(
            namespaceURI, localPart, prefix == null ? XMLConstants.DEFAULT_NS_PREFIX : prefix);
    }

    /**
     * Extract the prefix from a QName string.
     *  
     * @param qname
     * @return the prefix, if found
     * @exception IllegalArgumentException if the qname starts with a leading :
     */
    public static String extractPrefix(final String qname) throws IllegalArgumentException {
        final int p = qname.indexOf(COLON);

        if (p == Constants.STRING_NOT_FOUND) {
            return null;
        } else if (p == 0) {
            throw new IllegalArgumentException("Illegal QName: starts with a :");
        } else if (Character.isDigit(qname.substring(0,1).charAt(0))) {
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
    public static String extractLocalName(final String qname)
            throws IllegalArgumentException {

        final int p = qname.indexOf(COLON);

        if (p == Constants.STRING_NOT_FOUND) {
            return qname;
        } else if (p == 0) {
            throw new IllegalArgumentException("Illegal QName: starts with a ':'");
        } else if (p == qname.length()) {
            throw new IllegalArgumentException("Illegal QName: ends with a ':'");
        } else if (!isQName(qname)) {
            throw new IllegalArgumentException("Illegal QName: not a valid local name.");
        }

        return qname.substring(p + 1);
    }

    /**
     * Extract a QName from a namespace and qualified name string
     *
     * @param namespaceURI A namespace URI
     * @param qname A qualified named as a string e.g. 'my:name' or a local name e.g. 'name'
     *
     * @return The QName
     */
    public static QName parse(final String namespaceURI, final String qname) {
        final int p = qname.indexOf(COLON);
        if (p == Constants.STRING_NOT_FOUND) {
            return new QName(qname, namespaceURI);
        } else if(!isQName(qname)) {
            throw new IllegalArgumentException("Illegal QName: '" + qname + "'");
        } else {
            return new QName(qname.substring(p + 1), namespaceURI, qname.substring(0, p));
        }
    }

    private final static Pattern ptnClarkNotation = Pattern.compile("\\{([^&{}]*)\\}([^&{}:]+)");

    /**
     * Parses the given string into a QName. The method uses context to look up
     * a namespace URI for an existing prefix.
     * 
     * @param context
     * @param qname The QName may be either in Clark Notation
     *              e.g. `{namespace}local-part` or XDM literal qname form e.g. `prefix:local-part`.
     * @param defaultNS the default namespace to use if no namespace prefix is present.
     * @return QName
     * @exception IllegalArgumentException if no namespace URI is mapped to the prefix
     */
    public static QName parse(final Context context, final String qname, final String defaultNS)
            throws XPathException {

        final Matcher clarkNotation = ptnClarkNotation.matcher(qname);

        if(clarkNotation.matches()) {
            //parse as clark notation
            final String ns = clarkNotation.group(1);
            final String localPart = clarkNotation.group(2);
            return new QName(localPart, ns);
        } else {
            final String prefix = extractPrefix(qname);
            String namespaceURI;
            if (prefix != null) {
                namespaceURI = context.getURIForPrefix(prefix);
                if (namespaceURI == null) {
                    throw new XPathException(ErrorCodes.XPST0081, "No namespace defined for prefix " + prefix);
                }
            } else {
                namespaceURI = defaultNS;
            }
            if (namespaceURI == null) {
                namespaceURI = XMLConstants.NULL_NS_URI;
            }
            return new QName(extractLocalName(qname), namespaceURI, prefix);
        }
    }

    /**
     * Parses the given string into a QName. The method uses context to look up
     * a namespace URI for an optional existing prefix.
     * 
     * This method uses the default element namespace for qnames without prefix.
     * 
     * @param context
     * @param qname The QName may be either in Clark Notation
     *              e.g. `{namespace}local-part` or XDM literal qname form
     *              e.g. `prefix:local-part` or `local-part`.
     * @exception IllegalArgumentException if no namespace URI is mapped to the prefix
     */
    public static QName parse(final Context context, final String qname) throws XPathException {
        return parse(context, qname, context.getURIForPrefix(XMLConstants.DEFAULT_NS_PREFIX));
    }

    public final void isValid() throws XPathException {
    	if ((!(this instanceof WildcardLocalPartQName)) && !XMLChar.isValidNCName(localPart)) {
            throw new XPathException(ErrorCodes.XPTY0004, "Invalid localPart '" +  localPart + "' for QName '" + this + "'.");
        }
        
        if (prefix != null && !XMLChar.isValidNCName(prefix)) {
            throw new XPathException(ErrorCodes.XPTY0004, "Invalid prefix '" + prefix + "' for QName '" + this + "'.");
        }
    }

    public static final boolean isQName(final String name) {
        final int colon = name.indexOf(COLON);

        if (colon == Constants.STRING_NOT_FOUND) {
            return XMLChar.isValidNCName(name);
        } else if (colon == 0 || colon == name.length() - 1) {
            return false;
        } else if (!XMLChar.isValidNCName(name.substring(0, colon))) {
            return false;
        } else if (!XMLChar.isValidNCName(name.substring(colon + 1))) {
            return false;
        }

        return true;
    }

    public static QName fromJavaQName(final javax.xml.namespace.QName jQn) {
        return new QName(jQn.getLocalPart(), jQn.getNamespaceURI(), jQn.getPrefix());
    }

    public interface PartialQName{}

    public static class WildcardNamespaceURIQName extends QName implements PartialQName {
        public WildcardNamespaceURIQName(final String localPart) {
            super(localPart, WILDCARD);
        }

        public WildcardNamespaceURIQName(final String localPart, final byte nameType) {
            super(localPart, WILDCARD, nameType);
        }
    }

    public static class WildcardLocalPartQName extends QName implements PartialQName {
        public WildcardLocalPartQName(final String namespaceURI) {
            super(WILDCARD, namespaceURI);
        }

        public WildcardLocalPartQName(final String namespaceURI, final byte nameType) {
            super(WILDCARD, namespaceURI, nameType);
        }

        public WildcardLocalPartQName(final String namespaceURI, final String prefix) {
            super(WILDCARD, namespaceURI, prefix);
        }
    }
}
