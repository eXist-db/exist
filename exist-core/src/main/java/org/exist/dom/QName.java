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
import org.exist.util.XMLNames;
import org.exist.xquery.Constants;

import javax.xml.XMLConstants;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.exist.dom.QName.Validity.*;

/**
 * Represents a QName, consisting of a local name, a namespace URI and a prefix.
 *
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang</a>
 */
public class QName implements Comparable<QName> {

    public static final String WILDCARD = "*";
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
     * @param namespaceURI Namespace URI of the <code>QName</code>
     * @param localPart local part of the <code>QName</code>
     * @param prefix prefix of the <code>QName</code>
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

    public QName(final String name) throws IllegalQNameException {
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
     * @return true if there is a non-default namespace.
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
     * @deprecated Use for debugging purpose only,
     * use {@link #getStringValue()} for production
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
     * Get a URIQualifiedName format of the QName.
     *
     * @return the URIQualifiedName
     */
    public final String toURIQualifiedName() {
        return '{' + getNamespaceURI() + '}' + getLocalPart();
    }

    /**
     * Constructs a QName from a URIQualifiedName.
     *
     * @param uriQualifiedName the URIQualifiedName.
     *
     * @return the QName
     */
    public static QName fromURIQualifiedName(final String uriQualifiedName) {
        final Matcher matcher = ptnClarkNotation.matcher(uriQualifiedName);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Argument is not a URIQualifiedName");
        }
        final String ns = matcher.group(1);
        final String localPart = matcher.group(2);
        return new QName(localPart, ns);
    }

    /**
     * Compares two QNames by comparing namespace URI
     * and local names. The prefixes are not relevant.
     *
     * @param other The other QName
     *
     * @return a negative integer, zero, or a positive integer as this object
     * is less than, equal to, or greater than the specified object.
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
     * @param other The other qname
     *
     * @return true if they are qual.
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

    /**
     * Determines whether two QNames match
     * similar to {@link #equals(Object)} but
     * incorporates wildcards on either side.
     *
     * @param qnOther Another QName to compare against this
     *
     * @return true if two qnames match
     */
    public boolean matches(final QName qnOther) {
        if(equals(qnOther)) {
            return true;
        } else {
            if(this == WildcardQName.instance || qnOther == WildcardQName.instance) {
                return true;
            } else if((localPart.equals(WILDCARD) || qnOther.localPart.equals(WILDCARD)) && this.namespaceURI.equals(qnOther.namespaceURI)) {
                return true;
            } else if((namespaceURI.equals(WILDCARD) || qnOther.namespaceURI.equals(WILDCARD)) && this.localPart.equals(qnOther.localPart)) {
                return true;
            } else if((namespaceURI.equals(WILDCARD) && localPart.equals(WILDCARD)) || (qnOther.namespaceURI.equals(WILDCARD) || qnOther.localPart.equals(WILDCARD))) {
                return true;
            } else {
                return false;
            }
        }
    }

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
     * @param qname The QName from which to extract a prefix
     *
     * @return the prefix, if found
     *
     * @throws IllegalQNameException if the qname starts with a leading <code>:</code>
     */
    public static String extractPrefix(final String qname) throws IllegalQNameException {
        final int p = qname.indexOf(COLON);

        if (p == Constants.STRING_NOT_FOUND) {
            return null;
        } else if (p == 0) {
            throw new IllegalQNameException(INVALID_PREFIX.val, "Illegal QName: starts with a :");
        } else if (Character.isDigit(qname.substring(0,1).charAt(0))) {
            throw new IllegalQNameException(INVALID_PREFIX.val, "Illegal QName: starts with a digit");
        }

        return qname.substring(0, p);
    }

    /**
     * Extract the local name from a QName string.
     *
     * @param qname The QName from which to extract the local name.
     * @return the local name of the given QName string
     * @throws IllegalQNameException if the qname starts with a leading : or ends with a :
     */
    public static String extractLocalName(final String qname) throws IllegalQNameException {
        final int p = qname.indexOf(COLON);

        if (p == Constants.STRING_NOT_FOUND) {
            return qname;
        } else if (p == 0 || p == qname.length() - 1) {
            throw new IllegalQNameException(ILLEGAL_FORMAT.val, "Illegal QName: starts or ends with a ':'");
        } else {
            final byte validity = isQName(qname);
            if(validity != VALID.val) {
                throw new IllegalQNameException(validity, "Illegal QName: '" + qname + "'.");
            }
        }

        return qname.substring(p + 1);
    }

    /**
     * Extract a QName from a namespace and qualified name string.
     *
     * @param namespaceURI A namespace URI
     * @param qname A qualified named as a string e.g. 'my:name' or a local name e.g. 'name'
     *
     * @return The QName
     *
     * @throws IllegalQNameException if the qname component is invalid
     */
    public static QName parse(final String namespaceURI, final String qname) throws IllegalQNameException {
        final int p = qname.indexOf(COLON);
        if (p == Constants.STRING_NOT_FOUND) {
            return new QName(qname, namespaceURI);
        } else {
            final byte validity = isQName(qname);
            if(validity != VALID.val) {
                throw new IllegalQNameException(validity, "Illegal QName: '" + qname + "'");
            } else {
                return new QName(qname.substring(p + 1), namespaceURI, qname.substring(0, p));
            }
        }
    }

    private final static Pattern ptnClarkNotation = Pattern.compile("\\{([^&{}]*)\\}([^&{}:]+)");

    /**
     * Parses the given string into a QName. The method uses context to look up
     * a namespace URI for an existing prefix.
     *
     * @param context the xquery context
     * @param qname The QName may be either in Clark Notation
     *              e.g. `{namespace}local-part` or XDM literal qname form e.g. `prefix:local-part`.
     * @param defaultNS the default namespace to use if no namespace prefix is present.
     * @return parsed QName
     *
     * @throws IllegalQNameException if the qname is invalid
     */
    public static QName parse(final Context context, final String qname, final String defaultNS)
            throws IllegalQNameException {

        // quick test if qname is in clark notation
        if (qname.length() > 0 && qname.charAt(0) == '{') {
            final Matcher clarkNotation = ptnClarkNotation.matcher(qname);

            // more expensive check
            if (clarkNotation.matches()) {
                //parse as clark notation
                final String ns = clarkNotation.group(1);
                final String localPart = clarkNotation.group(2);
                return new QName(localPart, ns);
            }
        }

        final String prefix = extractPrefix(qname);
        String namespaceURI;
        if (prefix != null) {
            namespaceURI = context.getURIForPrefix(prefix);
            if (namespaceURI == null) {
                throw new IllegalQNameException(INVALID_PREFIX.val, "No namespace defined for prefix " + prefix);
            }
        } else {
            namespaceURI = defaultNS;
        }
        if (namespaceURI == null) {
            namespaceURI = XMLConstants.NULL_NS_URI;
        }
        return new QName(extractLocalName(qname), namespaceURI, prefix);
    }

    /**
     * Parses the given string into a QName. The method uses context to look up
     * a namespace URI for an optional existing prefix.
     *
     * This method uses the default element namespace for qnames without prefix.
     *
     * @param context the xquery context
     * @param qname The QName may be either in Clark Notation
     *              e.g. `{namespace}local-part` or XDM literal qname form
     *              e.g. `prefix:local-part` or `local-part`.
     * @throws IllegalQNameException if no namespace URI is mapped to the prefix
     * @return the parse QName
     */
    public static QName parse(final Context context, final String qname) throws IllegalQNameException {
        return parse(context, qname, context.getURIForPrefix(XMLConstants.DEFAULT_NS_PREFIX));
    }

    /**
     * Determines if the local name and prefix of this QName are valid NCNames
     *
     * @param allowWildcards true if we should permit wildcards to be considered valid (not actually a valid NCName),
     *     false otherwise for strict NCName adherence.
     *
     * @return Either {@link Validity#VALID} or various validity codes XOR'd together
     */
    public final byte isValid(final boolean allowWildcards) {
        byte result = VALID.val;

        if(!(allowWildcards && this == QName.WildcardQName.getInstance())) {

            if ((!(this instanceof WildcardLocalPartQName && allowWildcards)) && !XMLNames.isNCName(localPart)) {
                result ^= INVALID_LOCAL_PART.val;
            }

            if (prefix != null && !XMLNames.isNCName(prefix)) {
                result ^= INVALID_PREFIX.val;
            }
        }

        return result;
    }

    public static final byte isQName(final String name) {
        final int colon = name.indexOf(COLON);

        if (colon == Constants.STRING_NOT_FOUND) {
            return XMLNames.isNCName(name) ? VALID.val : INVALID_LOCAL_PART.val;
        } else if (colon == 0 || colon == name.length() - 1) {
            return ILLEGAL_FORMAT.val;
        } else if (!XMLNames.isNCName(name.substring(0, colon))) {
            return INVALID_PREFIX.val;
        } else if (!XMLNames.isNCName(name.substring(colon + 1))) {
            return INVALID_LOCAL_PART.val;
        }

        return VALID.val;
    }

    public static QName fromJavaQName(final javax.xml.namespace.QName jQn) {
        return new QName(jQn.getLocalPart(), jQn.getNamespaceURI(), jQn.getPrefix());
    }

    public interface PartialQName{}

    public static class WildcardQName extends QName implements PartialQName {
        private final static WildcardQName instance = new WildcardQName();
        public static WildcardQName getInstance() {
            return instance;
        }

        private WildcardQName() {
            super(WILDCARD, WILDCARD, WILDCARD);
        }
    }

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

        /**
         * Parses the given prefix into a WildcardLocalPartQName. The method uses context to look up
         * a namespace URI for an existing prefix.
         *
         * @param context the xquery context
         * @param prefix The namepspace prefix
         * @param defaultNS the default namespace to use if no namespace prefix is present.
         * @return WildcardLocalPartQName
         * @throws IllegalQNameException if no namespace URI is mapped to the prefix
         */
        public static WildcardLocalPartQName parseFromPrefix(final Context context, final String prefix,
                                                             final String defaultNS) throws IllegalQNameException {
            String namespaceURI;
            if (prefix != null) {
                namespaceURI = context.getURIForPrefix(prefix);
                if (namespaceURI == null) {
                    throw new IllegalQNameException(INVALID_PREFIX.val, "No namespace defined for prefix " + prefix);
                }
            } else {
                namespaceURI = defaultNS;
            }
            if (namespaceURI == null) {
                namespaceURI = XMLConstants.NULL_NS_URI;
            }
            return new WildcardLocalPartQName(namespaceURI, prefix);
        }

        /**
         * Parses the given prefix into a WildcardLocalPartQName. The method uses context to look up
         * a namespace URI for an existing prefix.
         *
         * @param context the xquery context
         * @param prefix The namepspace prefix
         * @return WildcardLocalPartQName
         * @throws IllegalQNameException if no namespace URI is mapped to the prefix
         */
        public static WildcardLocalPartQName parseFromPrefix(final Context context, final String prefix)
                throws IllegalQNameException {
            return parseFromPrefix(context, prefix, context.getURIForPrefix(XMLConstants.DEFAULT_NS_PREFIX));
        }
    }

    public enum Validity {
        VALID((byte)0x0),
        INVALID_LOCAL_PART((byte)0x1),
        INVALID_NAMESPACE((byte)0x2),
        INVALID_PREFIX((byte)0x4),
        ILLEGAL_FORMAT((byte)0x8);

        public final byte val;

        Validity(final byte val) {
            this.val = val;
        }
    }

    public static class IllegalQNameException extends Exception {
        private final byte validity;

        public IllegalQNameException(final byte validity) {
            super(asMessage(validity));
            this.validity = validity;
            if(validity == Validity.VALID.val) {
                throw new IllegalArgumentException("Cannot construct an IllegalQNameException with validity == VALID");
            }
        }

        public IllegalQNameException(final byte validity, final String message) {
            super(message + ". " + asMessage(validity));
            this.validity = validity;
            if(validity == Validity.VALID.val) {
                throw new IllegalArgumentException("Cannot construct an IllegalQNameException with validity == VALID");
            }
        }

        public byte getValidity() {
            return validity;
        }

        private static String asMessage(final byte validity) {
            final StringBuilder builder = new StringBuilder("QName is invalid:");
            for(final Validity v : Validity.values()) {
                if((validity & v.val) == validity) {
                    builder.append(" ").append(v.name());
                }
            }
            return builder.toString();
        }
    }
}