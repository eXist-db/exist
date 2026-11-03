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
package org.exist.xquery.xquf;

import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.util.DocUtils;
import org.exist.xquery.value.*;

import java.net.URISyntaxException;
import java.util.regex.Pattern;

/**
 * W3C XQuery Update Facility 3.0 - fn:put function.
 *
 * <pre>
 * fn:put($node as node(), $uri as xs:string) as empty-sequence()
 * </pre>
 *
 * Adds a put primitive to the PUL for deferred persistence.
 */
public class XQUFFnPut extends BasicFunction {

    public static final FunctionSignature SIGNATURE = new FunctionSignature(
            new QName("put", Function.BUILTIN_FUNCTION_NS, "fn"),
            "Stores a document node to a specified URI. The actual storage is deferred "
                    + "to the end of the snapshot (pending update list application).",
            new SequenceType[]{
                    new FunctionParameterSequenceType("node", Type.NODE, Cardinality.EXACTLY_ONE,
                            "The node to store"),
                    new FunctionParameterSequenceType("uri", Type.STRING, Cardinality.EXACTLY_ONE,
                            "The URI where the node should be stored")
            },
            new SequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE)
    );

    /** A URI scheme, as RFC 3986 defines it, at the start of a URI. */
    private static final Pattern SCHEME = Pattern.compile("[a-zA-Z][a-zA-Z0-9+.-]*:");

    static {
        // fn:put is an updating function: a call of it is an updating expression
        SIGNATURE.setUpdating(true);
    }

    public XQUFFnPut(final XQueryContext context) {
        super(context, SIGNATURE);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final NodeValue node = (NodeValue) args[0].itemAt(0);
        final String uri = resolveTarget(args[1].getStringValue());

        // XQUF 3.0 fn:put: storing any node kind other than document or element is implementation-defined, and eXist supports none
        final short nodeType = node.getNode().getNodeType();
        if (nodeType != org.w3c.dom.Node.DOCUMENT_NODE && nodeType != org.w3c.dom.Node.ELEMENT_NODE) {
            throw new XPathException(this, ErrorCodes.FOUP0001,
                    "fn:put requires a document or element node as its first argument.");
        }

        final PendingUpdateList pul = context.getPendingUpdateList();
        pul.addPrimitive(UpdatePrimitive.put(node.getNode(), uri, this));

        return Sequence.EMPTY_SEQUENCE;
    }

    /**
     * Resolve fn:put's URI. A relative URI is resolved against the static base URI: against a
     * database base URI the way fn:doc resolves it, so that fn:doc with the same URI reads the
     * stored document back, and against any other base URI as a URI. Whether eXist-db can store
     * to the result is only checked when the update is applied, after XUDY0031 has been checked
     * for the URIs of all fn:put calls.
     *
     * @throws XPathException FOUP0002 if the URI is not a valid xs:anyURI
     */
    private String resolveTarget(final String uri) throws XPathException {
        try {
            new AnyURIValue(this, uri);
        } catch (final XPathException e) {
            throw new XPathException(this, ErrorCodes.FOUP0002, "fn:put: not a valid URI: " + uri, e);
        }
        if (SCHEME.matcher(uri).lookingAt()) {
            return uri;
        }
        final AnyURIValue base = context.getBaseURI();
        if (base == null || isDatabaseLocation(base.toString())) {
            try {
                return DocUtils.resolveDatabasePath(context, uri).toString();
            } catch (final URISyntaxException e) {
                throw new XPathException(this, ErrorCodes.FOUP0002, "fn:put: not a valid database location: " + uri, e);
            }
        }
        final String resolved = DocUtils.resolveAgainstBaseUri(context, uri);
        return resolved != null ? resolved : uri;
    }

    /**
     * @param uri a resolved fn:put URI
     * @return whether it names a location in the database, the only place eXist-db can store to
     */
    static boolean isDatabaseLocation(final String uri) {
        return !SCHEME.matcher(uri).lookingAt() || uri.startsWith("xmldb:");
    }
}
