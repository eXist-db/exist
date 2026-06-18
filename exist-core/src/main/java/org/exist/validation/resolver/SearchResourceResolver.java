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
import org.exist.EXistException;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.LockedDocument;
import org.exist.resolver.ResolverFactory;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.Serializer;
import org.exist.validation.internal.DatabaseResources;
import org.exist.xmldb.XmldbURI;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xmlresolver.Resolver;
import org.xmlresolver.utils.SaxProducer;

import javax.xml.transform.OutputKeys;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Resolve a resource by searching in database. Schema's are queried
 * directly, DTD are searched in catalog files.
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 */
public class SearchResourceResolver implements XMLEntityResolver {
    private static final Logger LOG = LogManager.getLogger(SearchResourceResolver.class);

    private final String collectionPath;
    private final Subject subject;
    private final BrokerPool brokerPool;

    public SearchResourceResolver(final BrokerPool brokerPool, final Subject subject, final String collectionPath) {
        this.brokerPool = brokerPool;
        this.subject = subject;
        this.collectionPath = collectionPath;

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
        final DatabaseResources databaseResources = new DatabaseResources(brokerPool);

        if (xri.getNamespace() != null) {
            // XML Schema search
            if (LOG.isDebugEnabled()) {
                LOG.debug("Searching namespace '{}' in database from {}...", xri.getNamespace(), collectionPath);
            }

            resourcePath = databaseResources.findXSD(collectionPath, xri.getNamespace(), subject);

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
                    if (catalogPath.startsWith("xmldb:exist://")) {
                        catalogPath = ResolverFactory.fixupExistCatalogUri(catalogPath);
                        maybeSaxProducer = Optional.of(catalogSaxProducer(XmldbURI.create(catalogPath)));
                    } else if (catalogPath.startsWith("/db")) {
                        catalogPath = ResolverFactory.fixupExistCatalogUri(catalogPath);
                        maybeSaxProducer = Optional.of(catalogSaxProducer(XmldbURI.create(catalogPath)));
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

    private String getXriDetails(final XMLResourceIdentifier xrid) {
        return "PublicId='%s' BaseSystemId='%s' ExpandedSystemId='%s' LiteralSystemId='%s' Namespace='%s' ".formatted(
                xrid.getPublicId(), xrid.getBaseSystemId(), xrid.getExpandedSystemId(), xrid.getLiteralSystemId(), xrid.getNamespace());
    }

    private String getXisDetails(final XMLInputSource xis) {
        return "PublicId='%s' SystemId='%s' BaseSystemId='%s' Encoding='%s' ".formatted(
                xis.getPublicId(), xis.getSystemId(), xis.getBaseSystemId(), xis.getEncoding());
    }

    /**
     * Builds a {@link SaxProducer} that streams the SAX events of the catalog document stored
     * at {@code documentUri} directly to whatever {@link org.xml.sax.ContentHandler} the catalog
     * loader supplies, avoiding having to first serialize the document to a {@link String} and
     * have the catalog loader re-parse it from an {@link InputSource}.
     *
     * <p>The xmlresolver {@code ValidatingXmlLoader} invokes {@link SaxProducer#produce} twice
     * (once to validate the catalog against the OASIS XML Catalog RNG schema, once to actually
     * load the entries), so each invocation re-acquires the document lock and re-serializes it.</p>
     *
     * @param documentUri the URI of the catalog document stored in the database.
     * @return a producer that re-serializes the document's SAX events on each invocation.
     */
    private SaxProducer catalogSaxProducer(final XmldbURI documentUri) {
        return (contentHandler, dtdHandler, errorHandler) -> {
            try (final DBBroker broker = brokerPool.get(Optional.of(subject));
                 final LockedDocument lockedDocument = broker.getXMLResource(documentUri, Lock.LockMode.READ_LOCK)) {
                if (lockedDocument == null) {
                    throw new IOException("No such document: " + documentUri);
                }
                final DocumentImpl doc = lockedDocument.getDocument();

                final Properties outputProperties = new Properties();
                outputProperties.setProperty(OutputKeys.METHOD, "XML");
                outputProperties.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
                outputProperties.setProperty(OutputKeys.INDENT, "no");
                outputProperties.setProperty(OutputKeys.ENCODING, UTF_8.name());

                final Serializer serializer = broker.getSerializer();
                serializer.reset();
                serializer.setProperties(outputProperties);
                serializer.setSAXHandlers(contentHandler, null);
                serializer.toSAX(doc);
            } catch (final EXistException | PermissionDeniedException e) {
                throw new IOException(e.getMessage(), e);
            }
        };
    }
}
