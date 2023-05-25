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
        var relativeURI = new URI(relative.getStringValue());
        if (relativeURI.isAbsolute()) {
            return relative;
        }
        var baseURI = new URI(base.getStringValue() );
        if (!baseURI.isAbsolute()) {
            return relative;
        }
        try {
            var xBase = XmldbURI.xmldbUriFor(baseURI);
            var resolved = xBase.getURI().resolve(relativeURI);
            return new AnyURIValue(XmldbURI.XMLDB_URI_PREFIX + resolved);
        } catch (URISyntaxException e) {
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
                var resolved = resolveURI(hrefURI, baseURI);
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
    static Source resolveDocument(final String location, final XQueryContext xQueryContext, Expression containingExpression) throws org.exist.xquery.XPathException {

        final Sequence document;
        try {
            document = DocUtils.getDocument(xQueryContext, location);
        } catch (final PermissionDeniedException e) {
            throw new org.exist.xquery.XPathException(containingExpression, ErrorCodes.FODC0002,
                "Can not access '" + location + "'" + e.getMessage());
        }
        if (document == null || document.isEmpty()) {
            throw new org.exist.xquery.XPathException(containingExpression, ErrorCodes.FODC0002,
                "No document found at location '"+ location);
        }
        if (document.hasOne() && Type.subTypeOf(document.getItemType(), Type.NODE)) {
            if (document instanceof NodeProxy proxy) {
                return new DOMSource(proxy.getNode());
            }
            else if (document.itemAt(0) instanceof Node node) {
                return new DOMSource(node);
            }
        }
        throw new org.exist.xquery.XPathException(containingExpression, ErrorCodes.FODC0002,
            "Location '"+ location + "' returns an item which is not a document node");
    }
}