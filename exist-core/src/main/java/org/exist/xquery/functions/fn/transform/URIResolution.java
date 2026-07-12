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

package org.exist.xquery.functions.fn.transform;

import org.exist.dom.persistent.NodeProxy;
import org.exist.security.PermissionDeniedException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.util.DocUtils;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.w3c.dom.Node;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.dom.DOMSource;
import java.net.URI;
import java.net.URISyntaxException;

public class URIResolution {

    /**
     * URI resolution, the core should be the same as for fn:resolve-uri
     * @param relative URI to resolve
     * @param base to resolve against
     * @return resolved URI
     * @throws URISyntaxException if resolution is not possible
     */
    static AnyURIValue resolveURI(final AnyURIValue relative, final AnyURIValue base) throws URISyntaxException, XPathException {
        final URI relativeURI = new URI(relative.getStringValue());
        if (relativeURI.isAbsolute()) {
            return relative;
        }
        final String baseString = base.getStringValue();
        final URI baseURI = new URI(baseString);
        // a database path such as /db/apps/app has no scheme, so URI#isAbsolute is false,
        // yet it is an absolute location within the database and can be resolved against
        final boolean isAbsoluteBase = baseURI.isAbsolute() || baseString.startsWith("/");
        if (!isAbsoluteBase) {
            return relative;
        }
        try {
            final XmldbURI xBase = XmldbURI.xmldbUriFor(baseURI);
            // NOTE: for an xmldb: base, XmldbURI#getURI has already stripped the xmldb: prefix,
            // but for the short form (xmldb:/db/...) it has not; only add the prefix if it is absent,
            // otherwise the result doubles up as xmldb:xmldb:/db/...
            final URI resolved = xBase.getURI().resolve(relativeURI);
            if (XmldbURI.XMLDB_SCHEME.equals(resolved.getScheme())) {
                return new AnyURIValue(resolved.toString());
            }
            return new AnyURIValue(XmldbURI.XMLDB_URI_PREFIX + resolved);
        } catch (final URISyntaxException e) {
            return new AnyURIValue(baseURI.resolve(relativeURI));
        }
    }

    public static class CompileTimeURIResolver implements URIResolver {

        private final XQueryContext xQueryContext;
        private final Expression containingExpression;

        public CompileTimeURIResolver(XQueryContext xQueryContext, Expression containingExpression) {
            this.xQueryContext = xQueryContext;
            this.containingExpression = containingExpression;
        }

        @Override
        public Source resolve(final String href, final String base) throws TransformerException {

            try {
                final AnyURIValue baseURI = new AnyURIValue(base);
                final AnyURIValue hrefURI = new AnyURIValue(href);
                final AnyURIValue resolved = resolveURI(hrefURI, baseURI);
                return resolveDocument(resolved.getStringValue());
            } catch (URISyntaxException e) {
                throw new TransformerException(
                    "Failed to resolve " + href + " against " + base, e);
            } catch (XPathException e) {
                throw new TransformerException(
                    "Failed to find document as result of resolving " + href + " against " + base, e);
            }
        }

        protected Source resolveDocument(final String location) throws XPathException {
            return URIResolution.resolveDocument(location, xQueryContext, containingExpression);
        }
    }

    /**
     * Resolve an absolute document location, stylesheet or included source
     *
     * @param location of the stylesheet
     * @return the resolved stylesheet as a source
     * @throws org.exist.xquery.XPathException if the item does not exist, or is not a document
     */
    static Source resolveDocument(final String location, final XQueryContext xQueryContext, Expression containingExpression) throws XPathException {

        final Sequence document;
        try {
            document = DocUtils.getDocument(xQueryContext, location);
        } catch (final PermissionDeniedException e) {
            throw new XPathException(containingExpression, ErrorCodes.FODC0002,
                "Can not access '" + location + "'" + e.getMessage());
        }
        if (document == null || document.isEmpty()) {
            throw new XPathException(containingExpression, ErrorCodes.FODC0002,
                "No document found at location '"+ location);
        }
        if (document.hasOne() && Type.subTypeOf(document.getItemType(), Type.NODE)) {
            if (document instanceof NodeProxy proxy) {
                final DOMSource source = new DOMSource(proxy.getNode());
                source.setSystemId(location);
                return source;
            }
            else if (document.itemAt(0) instanceof Node node) {
                final DOMSource source = new DOMSource(node);
                source.setSystemId(location);
                return source;
            }
        }
        throw new XPathException(containingExpression, ErrorCodes.FODC0002,
            "Location '"+ location + "' returns an item which is not a document node");
    }
}