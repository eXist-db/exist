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

package org.exist.validation.resolver;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.xerces.xni.XMLResourceIdentifier;
import org.apache.xerces.xni.XNIException;
import org.apache.xerces.xni.parser.XMLEntityResolver;
import org.apache.xerces.xni.parser.XMLInputSource;
import org.exist.resolver.ResolverFactory;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.validation.internal.DatabaseResources;
import org.exist.xmldb.XmldbURI;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xmlresolver.Resolver;
import org.xmlresolver.utils.SaxProducer;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Resolve a resource by searching in database. Schema's are queried
 * directly, DTD are searched in catalog files.
 *
 * <p>Implements both {@link XMLEntityResolver} (Xerces' XNI interface, used by the default
 * SAX-parser-based dynamic-discovery pipeline) and {@link LSResourceResolver} ({@code
 * javax.xml.validation}'s interface, used by the XSD-1.1-capable {@link javax.xml.validation.Validator}
 * pipeline and by {@code validation:jaxv()}'s {@link javax.xml.validation.SchemaFactory}) so that
 * directory-search catalogs work the same way regardless of which pipeline ends up validating.
 * {@link LSResourceResolver} is XSD-only (no DTD/catalog equivalent), so {@link #resolveResource}
 * only ever performs the XML-Schema-by-namespace search, mirroring {@link #resolveEntity}'s
 * namespace branch.</p>
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 */
public class SearchResourceResolver implements XMLEntityResolver, LSResourceResolver {
    private static final Logger LOG = LogManager.getLogger(SearchResourceResolver.class);

    private final String collectionPath;
    private final Subject subject;
    private final BrokerPool brokerPool;
    private final DatabaseResources databaseResources;

    public SearchResourceResolver(final BrokerPool brokerPool, final Subject subject, final String collectionPath) {
        this.brokerPool = brokerPool;
        this.subject = subject;
        this.collectionPath = collectionPath;
        this.databaseResources = new DatabaseResources(brokerPool);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Specified collectionPath={}", collectionPath);
        }
    }


    @Override
    public XMLInputSource resolveEntity(final XMLResourceIdentifier xri) throws XNIException, IOException {
        if (xri.getExpandedSystemId() == null && xri.getLiteralSystemId() == null && xri.getNamespace() == null && xri.getPublicId() == null) {
            // quick fail
            return null;
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Resolving XMLResourceIdentifier: {}", getXriDetails(xri));
        }


        String resourcePath = null;

        if (xri.getNamespace() != null) {
            // XML Schema search
            resourcePath = findXsdResourcePathByNamespace(xri.getNamespace());

        } else if (xri.getPublicId() != null) {
            // Catalog search
            if (LOG.isDebugEnabled()) {
                LOG.debug("Searching publicId '{}' in catalogs in database from {}...", xri.getPublicId(), collectionPath);
            }

            String catalogPath = databaseResources.findCatalogWithDTD(collectionPath, xri.getPublicId(), subject);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Found publicId in catalog '{}'", catalogPath);
            }

            if (catalogPath != null) {
                /* NOTE(AR): Catalog URL if stored in database must start with
                   URI Scheme xmldb:// so that the XML Resolver can use
                   org.exist.protocolhandler.protocols.xmldb.Handler
                   to resolve any relative URI resources from the database.
                 */
                try {
                    final Optional<SaxProducer> maybeSaxProducer;
                    if (catalogPath.startsWith("xmldb:exist://") || catalogPath.startsWith("/db")) {
                        catalogPath = ResolverFactory.fixupExistCatalogUri(catalogPath);
                        maybeSaxProducer = Optional.of(ResolverFactory.catalogSaxProducer(brokerPool, subject, XmldbURI.create(catalogPath)));
                    } else {
                        maybeSaxProducer = Optional.empty();
                    }

                    final Resolver resolver = ResolverFactory.newResolverFromSax(List.of(Tuple(catalogPath, maybeSaxProducer)));
                    final InputSource source = resolver.resolveEntity(xri.getPublicId(), "");
                    if (source != null) {
                        resourcePath = source.getSystemId();
                    } else {
                        resourcePath = null;
                    }
                } catch (final SAXException | URISyntaxException e) {
                    throw new XNIException(e.getMessage(), e);
                }
            }
        } else {
            resourcePath = null;
        }

        // Another escape route
        if (resourcePath == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("resourcePath=null");
            }
            return null;
        }

        resourcePath = ResolverFactory.fixupExistCatalogUri(resourcePath);

        if (LOG.isDebugEnabled()) {
            LOG.debug("resourcePath='{}'", resourcePath);
        }

        final InputStream is = URI.create(resourcePath).toURL().openStream();

        final XMLInputSource xis = new XMLInputSource(xri.getPublicId(), xri.getExpandedSystemId(), xri.getBaseSystemId(), is, UTF_8.name());

        if (LOG.isDebugEnabled()) {
            LOG.debug("XMLInputSource: {}", getXisDetails(xis));
        }

        return xis;
    }

    /**
     * Resolves an {@code xs:import}/{@code xs:include}, or the instance's own root schema
     * reference during dynamic discovery (confirmed by experiment: {@code javax.xml.validation}'s
     * dynamic-discovery {@link javax.xml.validation.Validator} consults the configured
     * {@link LSResourceResolver} for the root schema location too, not just nested imports), by
     * searching for an XSD declaring {@code namespaceURI} under {@code collectionPath} -- the same
     * lookup {@link #resolveEntity}'s namespace branch performs for the XNI pipeline.
     *
     * <p>{@code systemId}/{@code baseURI} are intentionally ignored: unlike {@link #resolveEntity},
     * there is no DTD/catalog case to consider here (LSResourceResolver is XSD-only), and the
     * directory-search contract is "find an XSD by namespace", not "fetch whatever URI is named" --
     * the result always comes from the permission-checked {@code findXSD} search, never from a
     * caller/document-supplied location.</p>
     */
    @Override
    @Nullable
    public LSInput resolveResource(final String type, @Nullable final String namespaceURI,
            @Nullable final String publicId, @Nullable final String systemId, @Nullable final String baseURI) {
        if (namespaceURI == null) {
            return null;
        }

        final String resourcePath = findXsdResourcePathByNamespace(namespaceURI);
        if (resourcePath == null) {
            return null;
        }

        try {
            final InputStream is = URI.create(resourcePath).toURL().openStream();
            return new DatabaseLSInput(publicId, systemId, baseURI, is);
        } catch (final IOException e) {
            LOG.error("Could not open resolved schema resource '{}': {}", resourcePath, e.getMessage());
            return null;
        }
    }

    /**
     * Searches {@code collectionPath} for an XSD declaring {@code namespaceURI} as its target
     * namespace, shared between {@link #resolveEntity}'s namespace branch and {@link #resolveResource}.
     * The result is already normalized via {@link ResolverFactory#fixupExistCatalogUri} (idempotent,
     * so {@link #resolveEntity}'s own uniform fixup at the end of that method is a harmless no-op
     * for this path).
     *
     * @return the fixed-up resource path, or {@code null} if no matching schema was found.
     */
    @Nullable
    private String findXsdResourcePathByNamespace(final String namespaceURI) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Searching namespace '{}' in database from {}...", namespaceURI, collectionPath);
        }

        final String resourcePath = databaseResources.findXSD(collectionPath, namespaceURI, subject);
        return resourcePath == null ? null : ResolverFactory.fixupExistCatalogUri(resourcePath);
    }

    /**
     * Minimal {@link LSInput} wrapping an already-opened {@link InputStream} -- there is no
     * JDK-stock implementation of this interface available to depend on.
     */
    private static final class DatabaseLSInput implements LSInput {
        private final String publicId;
        private final String systemId;
        private final String baseURI;
        private InputStream byteStream;

        DatabaseLSInput(@Nullable final String publicId, @Nullable final String systemId,
                @Nullable final String baseURI, final InputStream byteStream) {
            this.publicId = publicId;
            this.systemId = systemId;
            this.baseURI = baseURI;
            this.byteStream = byteStream;
        }

        @Override
        public Reader getCharacterStream() {
            return null;
        }

        @Override
        public void setCharacterStream(final Reader characterStream) {
            // not used: this implementation only ever supplies a byte stream
        }

        @Override
        public InputStream getByteStream() {
            return byteStream;
        }

        @Override
        public void setByteStream(final InputStream byteStream) {
            this.byteStream = byteStream;
        }

        @Override
        public String getStringData() {
            return null;
        }

        @Override
        public void setStringData(final String stringData) {
            // not used: this implementation only ever supplies a byte stream
        }

        @Override
        public String getSystemId() {
            return systemId;
        }

        @Override
        public void setSystemId(final String systemId) {
            // immutable: this instance is only ever built once per resolveResource() call
        }

        @Override
        public String getPublicId() {
            return publicId;
        }

        @Override
        public void setPublicId(final String publicId) {
            // immutable: this instance is only ever built once per resolveResource() call
        }

        @Override
        public String getBaseURI() {
            return baseURI;
        }

        @Override
        public void setBaseURI(final String baseURI) {
            // immutable: this instance is only ever built once per resolveResource() call
        }

        @Override
        public String getEncoding() {
            return null;
        }

        @Override
        public void setEncoding(final String encoding) {
            // not used: the schema document carries its own encoding declaration, if any
        }

        @Override
        public boolean getCertifiedText() {
            return false;
        }

        @Override
        public void setCertifiedText(final boolean certifiedText) {
            // not used
        }
    }

    private String getXriDetails(final XMLResourceIdentifier xrid) {
        return "PublicId='%s' BaseSystemId='%s' ExpandedSystemId='%s' LiteralSystemId='%s' Namespace='%s' ".formatted(
                xrid.getPublicId(), xrid.getBaseSystemId(), xrid.getExpandedSystemId(), xrid.getLiteralSystemId(), xrid.getNamespace());
    }

    private String getXisDetails(final XMLInputSource xis) {
        return "PublicId='%s' SystemId='%s' BaseSystemId='%s' Encoding='%s' ".formatted(
                xis.getPublicId(), xis.getSystemId(), xis.getBaseSystemId(), xis.getEncoding());
    }
}
