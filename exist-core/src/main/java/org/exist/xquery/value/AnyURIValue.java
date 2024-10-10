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
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.Constants.Comparison;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;
import org.exist.xquery.functions.fn.FunEscapeURI;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.BitSet;

/**
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public class    AnyURIValue extends AtomicValue {

    public static final AnyURIValue EMPTY_URI = new AnyURIValue();
    static final int caseDiff = ('a' - 'A');
    static BitSet needEncoding;

    static {

        needEncoding = new BitSet(128);
        int i;
        for (i = 0x00; i <= 0x1F; i++) {
            needEncoding.set(i);
        }
        needEncoding.set(0x7F);
        needEncoding.set(0x20);
        needEncoding.set('<');
        needEncoding.set('>');
        needEncoding.set('"');
        needEncoding.set('{');
        needEncoding.set('}');
        needEncoding.set('|');
        needEncoding.set('\\');
        needEncoding.set('^');
        needEncoding.set('`');
    }

    /* Very important - this string does not need to be a valid uri.
     *
     * From XML Linking (see below for link), with some wording changes:
     * The value of the [anyURI] must be a URI reference as defined in
     * [IETF RFC 2396], or must result in a URI reference after the escaping
     * procedure described below is applied. The procedure is applied when
     * passing the URI reference to a URI resolver.
     *
     * Some characters are disallowed in URI references, even if they are
     * allowed in XML; the disallowed characters include all non-ASCII
     * characters, plus the excluded characters listed in Section 2.4 of
     * [IETF RFC 2396], except for the number sign (#) and percent sign (%)
     * and the square bracket characters re-allowed in [IETF RFC 2732].
     * Disallowed characters must be escaped as follows:
     * 1. Each disallowed character is converted to UTF-8 [IETF RFC 2279]
     *    as one or more bytes.
     * 2. Any bytes corresponding to a disallowed character are escaped
     *    with the URI escaping mechanism (that is, converted to %HH,
     *    where HH is the hexadecimal notation of the byte value).
     * 3. The original character is replaced by the resulting character
     *    sequence.
     *
     * See Section 5.4 of XML Linking:
     * http://www.w3.org/TR/2000/PR-xlink-20001220/#link-locators
     */
    private final String uri;
    //TODO: save escaped(URI) version?

    AnyURIValue() {
        super(null);
        this.uri = "";
    }

    AnyURIValue(final Expression expression) {
        super(expression);
        this.uri = "";
    }

    public AnyURIValue(final URI uri) {
        super(null);
        this.uri = uri.toString();
    }

    public AnyURIValue(final Expression expression, URI uri) {
        super(expression);
        this.uri = uri.toString();
    }

    public AnyURIValue(final XmldbURI uri) {
        super(null);
        this.uri = uri.toString();
    }

    public AnyURIValue(final Expression expression, XmldbURI uri) {
        super(expression);
        this.uri = uri.toString();
    }

    public AnyURIValue(String s) throws XPathException {
        this(null, s);
    }

    public AnyURIValue(final Expression expression, String s) throws XPathException {
        super(expression);
        final String wsTrimString = normalizeEscaped(StringValue.trimWhitespace(s));
        final String escapedString = escape(wsTrimString);
        try {
            new URI(escapedString);
        } catch (final URISyntaxException e) {
            try {
                XmldbURI.xmldbUriFor(escapedString);
            } catch (final URISyntaxException ex) {
                throw new XPathException(getExpression(), 
                        "Type error: the given string '" + s + "' cannot be cast to " + Type.getTypeName(getType()));
            }
        }
        /*
		The URI value is whitespace normalized according to the rules for the xs:anyURI type in [XML Schema]. 
		<xs:simpleType name="anyURI" id="anyURI">
			...
			<xs:restriction base="xs:anySimpleType">
				<xs:whiteSpace fixed="true" value="collapse" id="anyURI.whiteSpace"/>
			</xs:restriction>
		</xs:simpleType>
		*/
        //TODO : find a way to perform the 3 operations at the same time
        //s = StringValue.expand(s); //Should we have character entities
        s = StringValue.normalizeWhitespace(wsTrimString); //Should we have TABs, new lines...
        this.uri = StringValue.collapseWhitespace(s);
    }

    /**
     * This function accepts a String representation of an xs:anyURI and applies
     * the escaping method described in Section 5.4 of XML Linking (http://www.w3.org/TR/2000/PR-xlink-20001220/#link-locators)
     * to turn it into a valid URI
     *
     * @param uri The xs:anyURI to escape into a valid URI
     * @return An escaped string representation of the provided xs:anyURI
     * @see <a href="http://www.w3.org/TR/2000/PR-xlink-20001220/#link-locators">http://www.w3.org/TR/2000/PR-xlink-20001220/#link-locators</A>
     */
    public static String escape(String uri) {

        return FunEscapeURI.escape(uri, false);

        //TODO: TEST TEST TEST!
//			// basically copied from URLEncoder.encode
//		try {
//			boolean needToChange = false;
//			boolean wroteUnencodedChar = false;
//			int maxBytesPerChar = 10; // rather arbitrary limit, but safe for now
//			StringBuffer out = new StringBuffer(uri.length());
//			ByteArrayOutputStream buf = new ByteArrayOutputStream(maxBytesPerChar);
//
//			OutputStreamWriter writer = new OutputStreamWriter(buf, "UTF-8");
//
//			for (int i = 0; i < uri.length(); i++) {
//				int c = (int) uri.charAt(i);
//				if (c>127 || needEncoding.get(c)) {
//					try {
//						if (wroteUnencodedChar) { // Fix for 4407610
//							writer = new OutputStreamWriter(buf, "UTF-8");
//							wroteUnencodedChar = false;
//						}
//						writer.write(c);
//						/*
//						 * If this character represents the start of a Unicode
//						 * surrogate pair, then pass in two characters. It's not
//						 * clear what should be done if a bytes reserved in the
//						 * surrogate pairs range occurs outside of a legal
//						 * surrogate pair. For now, just treat it as if it were
//						 * any other character.
//						 */
//						if (c >= 0xD800 && c <= 0xDBFF) {
//							/*
//							 System.out.println(Integer.toHexString(c)
//							 + " is high surrogate");
//							 */
//							if ( (i+1) < uri.length()) {
//								int d = (int) uri.charAt(i+1);
//								/*
//								 System.out.println("\tExamining "
//								 + Integer.toHexString(d));
//								 */
//								if (d >= 0xDC00 && d <= 0xDFFF) {
//									/*
//									 System.out.println("\t"
//									 + Integer.toHexString(d)
//									 + " is low surrogate");
//									 */
//									writer.write(d);
//									i++;
//								}
//							}
//						}
//						writer.flush();
//					} catch(IOException e) {
//						buf.reset();
//						continue;
//					}
//					byte[] ba = buf.toByteArray();
//					for (int j = 0; j < ba.length; j++) {
//						out.append('%');
//						char ch = Character.forDigit((ba[j] >> 4) & 0xF, 16);
//						// converting to use uppercase letter as part of
//						// the hex value if ch is a letter.
//						if (Character.isLetter(ch)) {
//							ch -= caseDiff;
//						}
//						out.append(ch);
//						ch = Character.forDigit(ba[j] & 0xF, 16);
//						if (Character.isLetter(ch)) {
//							ch -= caseDiff;
//						}
//						out.append(ch);
//					}
//					buf.reset();
//					needToChange = true;
//				} else {
//					out.append((char)c);
//					wroteUnencodedChar = true;
//				}
//			}
//
//			return (needToChange? out.toString() : uri);
//		} catch(UnsupportedEncodingException e) {
//			throw new RuntimeException(e);
//		}
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.AtomicValue#getType()
     */
    public int getType() {
        return Type.ANY_URI;
    }

    @Override
    public String getStringValue() {
        return uri;
    }

    public boolean effectiveBooleanValue() throws XPathException {
        // If its operand is a singleton value of type xs:string, xs:anyURI, xs:untypedAtomic,
        //or a type derived from one of these, fn:boolean returns false if the operand value has zero length; otherwise it returns true.
        return !uri.isEmpty();
    }

    public AtomicValue convertTo(final int requiredType) throws XPathException {
        switch (requiredType) {
            case Type.ITEM:
            case Type.ANY_ATOMIC_TYPE:
            case Type.ANY_URI:
                return this;
            case Type.STRING:
                return new StringValue(getExpression(), uri);
            case Type.UNTYPED_ATOMIC:
                return new UntypedAtomicValue(getExpression(), getStringValue());
            default:
                throw new XPathException(getExpression(), ErrorCodes.FORG0001,
                        "Type error: cannot cast xs:anyURI to "
                                + Type.getTypeName(requiredType));
        }
    }

    @Override
    public boolean compareTo(Collator collator, Comparison operator, AtomicValue other) throws XPathException {
        if (other.getType() == Type.ANY_URI) {
            final String otherURI = other.getStringValue();
            final int cmp = uri.compareTo(otherURI);
            return switch (operator) {
                case EQ -> cmp == 0;
                case NEQ -> cmp != 0;
                case GT -> cmp > 0;
                case GTEQ -> cmp >= 0;
                case LT -> cmp < 0;
                case LTEQ -> cmp <= 0;
                default -> throw new XPathException(getExpression(), ErrorCodes.XPTY0004,
                        "cannot apply operator " + operator.generalComparisonSymbol + " to xs:anyURI");
            };
        } else {
            AtomicValue atomicValue = null;

            try {
                atomicValue = other.convertTo(Type.ANY_URI);

            } catch (XPathException ex) {

                if (ex.getErrorCode() == ErrorCodes.FORG0001) {
                    throw new XPathException(getExpression(), ErrorCodes.XPTY0004, "cannot apply operator " + operator.generalComparisonSymbol + " to xs:anyURI");
                }
                throw ex;
            }

            return compareTo(collator, operator, atomicValue);
        }
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.AtomicValue#compareTo(org.exist.xquery.value.AtomicValue)
     */
    public int compareTo(Collator collator, AtomicValue other) throws XPathException {
        if (other.getType() == Type.ANY_URI) {
            final String otherURI = other.getStringValue();
            return uri.compareTo(otherURI);
        } else {
            return compareTo(collator, other.convertTo(Type.ANY_URI));
        }
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.AtomicValue#max(org.exist.xquery.value.AtomicValue)
     */
    public AtomicValue max(Collator collator, AtomicValue other) throws XPathException {
        throw new XPathException(getExpression(), "max is not supported for values of type xs:anyURI");
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.AtomicValue#min(org.exist.xquery.value.AtomicValue)
     */
    public AtomicValue min(Collator collator, AtomicValue other) throws XPathException {
        throw new XPathException(getExpression(), "min is not supported for values of type xs:anyURI");
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Item#conversionPreference(java.lang.Class)
     */
    public int conversionPreference(Class<?> javaClass) {
        if (javaClass.isAssignableFrom(AnyURIValue.class)) {
            return 0;
        }
        if (javaClass == XmldbURI.class) {
            return 1;
        }
        if (javaClass == URI.class) {
            return 2;
        }
        if (javaClass == URL.class) {
            return 3;
        }
        if (javaClass == String.class || javaClass == CharSequence.class) {
            return 4;
        }
        if (javaClass == Object.class) {
            return 20;
        }
        return Integer.MAX_VALUE;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Item#toJavaObject(java.lang.Class)
     */
    @Override
    public <T> T toJavaObject(final Class<T> target) throws XPathException {
        if (target.isAssignableFrom(AnyURIValue.class)) {
            return (T) this;
        } else if (target == XmldbURI.class) {
            return (T) toXmldbURI();
        } else if (target == URI.class) {
            return (T) toURI();
        } else if (target == URL.class) {
            try {
                return (T) new URL(uri);
            } catch (final MalformedURLException e) {
                throw new XPathException(getExpression(), ErrorCodes.FORG0001,
                        "failed to convert " + uri + " into a Java URL: " + e.getMessage(),
                        e);
            }
        } else if (target == String.class || target == CharSequence.class) {
            return (T) uri;
        } else if (target == Object.class) {
            return (T) uri;
        }

        throw new XPathException(getExpression(), 
                "cannot convert value of type "
                        + Type.getTypeName(getType())
                        + " to Java object of type "
                        + target.getName());
    }

    public XmldbURI toXmldbURI() throws XPathException {
        try {
            return XmldbURI.xmldbUriFor(uri, false);
        } catch (final URISyntaxException e) {
            throw new XPathException(getExpression(), ErrorCodes.FORG0001,
                    "failed to convert " + uri + " into an XmldbURI: " + e.getMessage(),
                    e);
        }
    }

    public URI toURI() throws XPathException {
        try {
            return new URI(escape(uri));
        } catch (final URISyntaxException e) {
            throw new XPathException(getExpression(), ErrorCodes.FORG0001,
                    "failed to convert " + uri + " into an URI: " + e.getMessage(),
                    e);
        }
    }

    @Override
    public int hashCode() {
        return uri.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof AnyURIValue) {
            return ((AnyURIValue) obj).uri.equals(uri);
        }
        return false;
    }

    private String normalizeEscaped(String in) {
        final StringBuilder builder = new StringBuilder();
        for (int i = 0; i < in.length(); i++) {
            char ch = in.charAt(i);
            if (ch == '%') {
                builder.append(ch);
                for (int j = 1; j <= 2 && i + j < in.length(); j++) {
                    ch = in.charAt(i + j);
                    builder.append(Character.toUpperCase(ch));
                }
                i += 2;
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }
}
