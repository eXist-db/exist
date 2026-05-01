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
package org.exist.xquery.functions.fn;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.QNameValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import javax.xml.XMLConstants;

/**
 * Implements XQuery 4.0 fn:parse-QName.
 *
 * fn:parse-QName($value as xs:string?) as xs:QName?
 *
 * Parses an EQName string to xs:QName. Supports:
 * - NCName (no namespace)
 * - prefix:local (resolved from static context)
 * - Q{uri}local (URIQualifiedName)
 */
public class FnParseQName extends BasicFunction {

    private static final ErrorCodes.ErrorCode FOCA0002 = new ErrorCodes.ErrorCode("FOCA0002",
            "Invalid lexical form for xs:QName");

    public static final FunctionSignature FN_PARSE_QNAME = new FunctionSignature(
            new QName("parse-QName", Function.BUILTIN_FUNCTION_NS),
            "Parses an EQName string to xs:QName.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("value", Type.STRING, Cardinality.ZERO_OR_ONE, "The EQName string to parse")
            },
            new FunctionReturnSequenceType(Type.QNAME, Cardinality.ZERO_OR_ONE, "the parsed QName"));

    public FnParseQName(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        if (args[0].isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final String value = args[0].getStringValue().strip();

        if (value.isEmpty()) {
            throw new XPathException(this, FOCA0002, "Empty string is not a valid EQName");
        }

        // URIQualifiedName: Q{uri}local
        if (value.startsWith("Q{")) {
            return parseURIQualifiedName(value);
        }

        // Prefixed QName: prefix:local
        final int colon = value.indexOf(':');
        if (colon > 0) {
            final String prefix = value.substring(0, colon);
            final String local = value.substring(colon + 1);

            if (!isNCName(prefix) || !isNCName(local)) {
                throw new XPathException(this, FOCA0002,
                        "Invalid prefixed QName: " + value);
            }

            final String uri = context.getURIForPrefix(prefix);
            if (uri == null || uri.isEmpty()) {
                throw new XPathException(this, ErrorCodes.FONS0004,
                        "Undeclared prefix: " + prefix);
            }

            return new QNameValue(this, context, new QName(local, uri, prefix));
        }

        // NCName (no namespace)
        if (!isNCName(value)) {
            throw new XPathException(this, FOCA0002,
                    "Invalid NCName: " + value);
        }

        return new QNameValue(this, context, new QName(value, XMLConstants.NULL_NS_URI));
    }

    private Sequence parseURIQualifiedName(final String value) throws XPathException {
        final int closeBrace = value.indexOf('}');
        if (closeBrace < 0) {
            throw new XPathException(this, FOCA0002,
                    "Missing closing '}' in URIQualifiedName: " + value);
        }

        final String uri = value.substring(2, closeBrace);
        final String rest = value.substring(closeBrace + 1);

        if (rest.isEmpty()) {
            throw new XPathException(this, FOCA0002,
                    "Missing local name after Q{...}: " + value);
        }

        // rest may be prefix:local or just local
        final int colon = rest.indexOf(':');
        final String local;
        final String prefix;
        if (colon > 0) {
            prefix = rest.substring(0, colon);
            local = rest.substring(colon + 1);
        } else {
            prefix = XMLConstants.DEFAULT_NS_PREFIX;
            local = rest;
        }

        if (!isNCName(local) || (colon > 0 && !isNCName(prefix))) {
            throw new XPathException(this, FOCA0002,
                    "Invalid URIQualifiedName: " + value);
        }

        return new QNameValue(this, context, new QName(local, uri, prefix));
    }

    private static boolean isNCName(final String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        if (!isNCNameStart(s.charAt(0))) {
            return false;
        }
        for (int i = 1; i < s.length(); i++) {
            if (!isNCNameChar(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isNCNameStart(final char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isNCNameChar(final char c) {
        return Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_'
                || c == '\u00B7'
                || (c >= '\u0300' && c <= '\u036F')
                || (c >= '\u203F' && c <= '\u2040');
    }
}
