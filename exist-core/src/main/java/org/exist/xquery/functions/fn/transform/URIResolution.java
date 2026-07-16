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
import org.exist.util.SaxonConfiguration;
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
import org.xmlresolver.Resolver;

import javax.annotation.Nullable;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.dom.DOMSource;
import java.net.URI;
import java.net.URISyntaxException;

public class URIResolution {

    /**
     * URI resolution, the core should be the same as for fn:resolve-uri
     * <p>
     *     A location within the database is resolved against the base as if the base
     *     were a collection whenever {@link #assumeCollection(String)} holds, and as
     *     if it were a document otherwise. A base outside the database (for instance a
     *     {@code file:} or {@code http:} URI) is always resolved strictly according to
     *     RFC 3986, that is, as if it were a document.
     * </p>
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
            final URI resolved = asResolutionBase(xBase.getURI()).resolve(relativeURI);
            if (XmldbURI.XMLDB_SCHEME.equals(resolved.getScheme())) {
                return new AnyURIValue(resolved.toString());
            }
            return new AnyURIValue(XmldbURI.XMLDB_URI_PREFIX + resolved);
        } catch (final URISyntaxException e) {
            return new AnyURIValue(baseURI.resolve(relativeURI));
        }
    }

    /**
     * Prepare a location within the database to be resolved against.
     * <p>
     *     RFC 3986 discards the last segment of the base unless it is empty, which is
     *     correct for a document but not for a collection: resolving {@code style.xsl}
     *     against the collection {@code /db/apps/app} would yield {@code /db/apps/style.xsl}.
     *     A collection is therefore given the trailing slash that marks it as a
     *     "directory" before it is resolved against.
     * </p>
     *
     * @param base location within the database
     * @return the location to resolve against
     */
    private static URI asResolutionBase(final URI base) {
        final String baseString = base.toString();
        if (!baseString.endsWith("/") && assumeCollection(baseString)) {
            return URI.create(baseString + "/");
        }
        return base;
    }

    /**
     * Whether a location within the database is assumed to be a collection.
     * <p>
     *     A collection and a document are not distinguishable by their path alone, so
     *     the absence of an extension in the last segment is taken to mean a collection.
     *     This is a heuristic: a document stored without an extension (which is legal,
     *     if unusual) is mistaken for a collection.
     * </p>
     *
     * @param location within the database
     * @return true if the location is assumed to be a collection
     */
    private static boolean assumeCollection(final String location) {
        final String lastSegment = location.substring(location.lastIndexOf('/') + 1);
        return lastSegment.indexOf('.') == -1;
    }

    public static class CompileTimeURIResolver implements URIResolver {

        private final XQueryContext xQueryContext;
        private final Expression containingExpression;

        /**
         * Fetched once here rather than per-href in {@link #resolveViaCatalog(String, String)} --
         * this resolver doesn't change for the lifetime of one compile, so re-fetching it from
         * {@link org.exist.util.Configuration} on every {@code href} encountered while compiling a
         * stylesheet (every {@code xsl:import}/{@code xsl:include}/{@code doc()}) was redundant.
         */
        @Nullable
        private final Resolver catalogResolver;

        public CompileTimeURIResolver(XQueryContext xQueryContext, Expression containingExpression) {
            this.xQueryContext = xQueryContext;
            this.containingExpression = containingExpression;
            this.catalogResolver = xQueryContext.getBroker() == null
                    ? null
                    : SaxonConfiguration.resolveCatalogResolver(xQueryContext.getBroker().getBrokerPool().getConfiguration());
        }

        @Override
        public Source resolve(final String href, final String base) throws TransformerException {

            // Try the system catalog (webapp/WEB-INF/catalog.xml by default) first, the same way
            // XsltURIResolverHelper tries it before any network-risking fallback -- a catalog miss
            // declines promptly (no fetch attempt), but resolveDocument()/DocUtils.getDocument()
            // below will itself attempt a live fetch for an absolute http(s) URI, so the catalog
            // must run first to avoid a slow/hanging network round-trip on every catalog hit (#350).
            final Source catalogSource = resolveViaCatalog(href, base);
            if (catalogSource != null) {
                return catalogSource;
            }

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

        private Source resolveViaCatalog(final String href, final String base) throws TransformerException {
            if (catalogResolver == null) {
                return null;
            }
            return catalogResolver.resolve(href, base);
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