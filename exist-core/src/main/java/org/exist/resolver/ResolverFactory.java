/*
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This file was originally ported from FusionDB to eXist-db by
 * Evolved Binary, for the benefit of the eXist-db Open Source community.
 * Only the ported code as it appears in this file, at the time that
 * it was contributed to eXist-db, was re-licensed under The GNU
 * Lesser General Public License v2.1 only for use in eXist-db.
 *
 * This license grant applies only to a snapshot of the code as it
 * appeared when ported, it does not offer or infer any rights to either
 * updates of this source code or access to the original source code.
 *
 * The GNU Lesser General Public License v2.1 only license follows.
 *
 * ---------------------------------------------------------------------
 *
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; version 2.1.
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
package org.exist.resolver;

import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.exist.EXistException;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.Serializer;
import org.exist.xmldb.XmldbURI;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xmlresolver.CatalogManager;
import org.xmlresolver.Resolver;
import org.xmlresolver.ResolverFeature;
import org.xmlresolver.XMLResolverConfiguration;
import org.xmlresolver.utils.SaxProducer;

import javax.xml.transform.OutputKeys;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Factory for creating Resolvers.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public interface ResolverFactory {

    /**
     * Create a Resolver that is configured for specific catalogs.
     *
     * @param catalogs the list of catalogs, the first entry in the tuple is their URI (and/or location),
     *                 and the optional second argument is an InputSource for obtaining them directly.
     *
     * @return the resolver
     *
     * @throws URISyntaxException if one of the catalog URI is invalid
     */
    static Resolver newResolver(final List<Tuple2<String, Optional<InputSource>>> catalogs) throws URISyntaxException {
        final XMLResolverConfiguration resolverConfiguration = newCatalogConfiguration();

        for (final Tuple2<String, Optional<InputSource>> catalog : catalogs) {
            String strCatalogUri = catalog._1;
            strCatalogUri = sanitizeCatalogUri(strCatalogUri);
            if (catalog._2.isPresent()) {
                resolverConfiguration.addCatalog(new URI(strCatalogUri), catalog._2.get());
            } else {
                resolverConfiguration.addCatalog(strCatalogUri);
            }
        }

        return new Resolver(resolverConfiguration);
    }

    /**
     * Create a Resolver that is configured for specific catalogs, where catalogs that are
     * not retrievable directly via their URI (e.g. catalogs stored inside the database) are
     * instead supplied as a {@link SaxProducer} that streams the catalog's SAX events directly,
     * avoiding having to first serialize the catalog document to a {@link String}/{@link InputSource}
     * and then have the catalog loader re-parse it.
     *
     * @param catalogs the list of catalogs, the first entry in the tuple is their URI (and/or location),
     *                 and the optional second argument is a {@link SaxProducer} for obtaining their
     *                 content directly as SAX events.
     *
     * @return the resolver
     *
     * @throws URISyntaxException if one of the catalog URI is invalid
     */
    static Resolver newResolverFromSax(final List<Tuple2<String, Optional<SaxProducer>>> catalogs) throws URISyntaxException {
        final XMLResolverConfiguration resolverConfiguration = newCatalogConfiguration();

        final CatalogManager manager = resolverConfiguration.getFeature(ResolverFeature.CATALOG_MANAGER);

        for (final Tuple2<String, Optional<SaxProducer>> catalog : catalogs) {
            String strCatalogUri = catalog._1;
            strCatalogUri = sanitizeCatalogUri(strCatalogUri);
            if (catalog._2.isPresent()) {
                final URI catalogUri = new URI(strCatalogUri);
                // Register the catalog URI with the configuration, then have the manager
                // load it directly from SAX events -- the manager caches the result by
                // URI, so the resolver never tries to dereference it. This is the same
                // add-then-load order that XMLResolverConfiguration#addCatalog(URI,
                // InputSource) uses internally (verified against xmlresolver 6.0.23), just
                // split across two calls since that overload only accepts an InputSource.
                resolverConfiguration.addCatalog(strCatalogUri);
                manager.loadCatalog(catalogUri, catalog._2.get());
            } else {
                resolverConfiguration.addCatalog(strCatalogUri);
            }
        }

        return new Resolver(resolverConfiguration);
    }

    /**
     * Creates an {@link XMLResolverConfiguration} with the common features and catalog
     * setup shared by {@link #newResolver(List)} and {@link #newResolverFromSax(List)}.
     *
     * @return a new resolver configuration, ready for catalogs to be added.
     */
    private static XMLResolverConfiguration newCatalogConfiguration() {
        final XMLResolverConfiguration resolverConfiguration = new XMLResolverConfiguration();
        resolverConfiguration.setFeature(ResolverFeature.RESOLVER_LOGGER_CLASS, "org.xmlresolver.logging.SystemLogger");
        resolverConfiguration.setFeature(ResolverFeature.CATALOG_LOADER_CLASS, "org.xmlresolver.loaders.ValidatingXmlLoader");
        resolverConfiguration.setFeature(ResolverFeature.CLASSPATH_CATALOGS, true);
        resolverConfiguration.setFeature(ResolverFeature.URI_FOR_SYSTEM, true);

        resolverConfiguration.removeCatalog("./catalog.xml");

        return resolverConfiguration;
    }

    /**
     * Resolve a list of catalog URLs into a single {@link Resolver}, streaming any catalog
     * that is stored in the database directly from SAX events (see {@link #catalogSaxProducer(DBBroker, XmldbURI)})
     * rather than first serializing it to a {@link String}.
     *
     * @param broker the broker to use for reading any catalogs stored in the database.
     * @param catalogUrls the catalog URLs, e.g. as obtained from {@code Shared.getUrls(...)}.
     *
     * @return the resolver configured for the given catalogs.
     *
     * @throws URISyntaxException if one of the catalog URLs is invalid.
     */
    static Resolver resolveCatalogs(final DBBroker broker, final String[] catalogUrls) throws URISyntaxException {
        final List<Tuple2<String, Optional<SaxProducer>>> catalogs = new ArrayList<>();
        for (String catalogUrl : catalogUrls) {

            /* NOTE(AR): Catalog URL if stored in database must start with
               URI Scheme xmldb:// so that the XML Resolver can use
               org.exist.protocolhandler.protocols.xmldb.Handler
               to resolve any relative URI resources from the database.
             */
            final Optional<SaxProducer> maybeSaxProducer;
            if (catalogUrl.startsWith("xmldb:exist://") || catalogUrl.startsWith("/db")) {
                catalogUrl = fixupExistCatalogUri(catalogUrl);
                maybeSaxProducer = Optional.of(catalogSaxProducer(broker, XmldbURI.create(catalogUrl)));
            } else {
                maybeSaxProducer = Optional.empty();
            }

            catalogs.add(Tuple(catalogUrl, maybeSaxProducer));
        }
        return newResolverFromSax(catalogs);
    }

    /**
     * Builds a {@link SaxProducer} that streams the SAX events of the catalog document stored
     * at {@code documentUri} directly to whatever {@link ContentHandler} the catalog loader
     * supplies, avoiding having to first serialize the document to a {@link String} and have
     * the catalog loader re-parse it from an {@link InputSource}.
     *
     * <p>The xmlresolver {@code ValidatingXmlLoader} invokes {@link SaxProducer#produce} twice
     * (once to validate the catalog against the OASIS XML Catalog RNG schema, once to actually
     * load the entries). Re-serializing the document from the database on each invocation would
     * mean a second broker round-trip per catalog per call, and the two passes could see
     * different content if the document is concurrently modified in between -- so instead, the
     * first invocation's events are recorded (see {@link RecordingContentHandler}) and replayed
     * for any subsequent invocation, without touching the broker again.</p>
     *
     * @param broker the broker to use for reading the document. Must remain valid for as long
     *               as the returned {@link SaxProducer}'s first invocation.
     * @param documentUri the URI of the catalog document stored in the database.
     *
     * @return a producer that serializes the document's SAX events once, replaying them for any
     *         further invocation.
     */
    static SaxProducer catalogSaxProducer(final DBBroker broker, final XmldbURI documentUri) {
        final RecordingContentHandler recorder = new RecordingContentHandler();
        return (contentHandler, dtdHandler, errorHandler) -> {
            if (!recorder.hasRecording()) {
                streamCatalogDocument(broker, documentUri, recorder);
            }
            recorder.replay(contentHandler);
        };
    }

    /**
     * As {@link #catalogSaxProducer(DBBroker, XmldbURI)}, but acquires (and releases) a fresh
     * broker for the given {@code subject} only for the first invocation, for callers that do
     * not already hold a broker valid for the lifetime of the returned {@link SaxProducer}.
     *
     * @param brokerPool the broker pool to acquire a broker from for the first invocation.
     * @param subject the subject to acquire the broker as.
     * @param documentUri the URI of the catalog document stored in the database.
     *
     * @return a producer that serializes the document's SAX events once, replaying them for any
     *         further invocation.
     */
    static SaxProducer catalogSaxProducer(final BrokerPool brokerPool, final Subject subject, final XmldbURI documentUri) {
        final RecordingContentHandler recorder = new RecordingContentHandler();
        return (contentHandler, dtdHandler, errorHandler) -> {
            if (!recorder.hasRecording()) {
                try (final DBBroker broker = brokerPool.get(Optional.of(subject))) {
                    streamCatalogDocument(broker, documentUri, recorder);
                } catch (final EXistException e) {
                    throw new IOException(e.getMessage(), e);
                }
            }
            recorder.replay(contentHandler);
        };
    }

    private static void streamCatalogDocument(final DBBroker broker, final XmldbURI documentUri, final ContentHandler contentHandler)
            throws IOException, SAXException {
        try (final LockedDocument lockedDocument = broker.getXMLResource(documentUri, Lock.LockMode.READ_LOCK)) {
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
        } catch (final PermissionDeniedException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    /**
     * Sanitize the Catalog URI.
     *
     * Mainly deals with converting Windows file paths to URI.
     *
     * @param strCatalogUri The Catalog URI string
     *
     * @return The sanitized Catalog URI string
     */
    static String sanitizeCatalogUri(String strCatalogUri) {
        String sanitizedCatalogUri = strCatalogUri;
        if (sanitizedCatalogUri.indexOf('\\') > -1) {
            // convert from Windows file path
            sanitizedCatalogUri = Path.of(sanitizedCatalogUri).toUri().toString();
        }
        return sanitizedCatalogUri;
    }

    /**
     * Catalog URI if stored in database must start with
     * URI Scheme xmldb:// (and NOT xmldb:exist://) so that
     * the {@link Resolver} can use {@link org.exist.protocolhandler.protocols.xmldb.Handler}
     * to resolve any relative URI resources from the database.
     *
     * @param catalogs the catalog URIs
     *
     * @return the catalog URIs suitable for use with the {@link Resolver}.
     */
    static List<Tuple2<String, Optional<InputSource>>> fixupExistCatalogUris(final List<Tuple2<String, Optional<InputSource>>> catalogs) {
        return catalogs.stream().map(catalog -> Tuple(fixupExistCatalogUri(catalog._1), catalog._2)).collect(Collectors.toList());
    }

    /**
     * Catalog URI if stored in database must start with
     * URI Scheme xmldb:// (and NOT xmldb:exist://) so that
     * the {@link Resolver} can use {@link org.exist.protocolhandler.protocols.xmldb.Handler}
     * to resolve any relative URI resources from the database.
     *
     * @param catalogUri the catalog URI
     *
     * @return the catalog URI suitable for use with the {@link Resolver}.
     */
    static String fixupExistCatalogUri(String catalogUri) {
        if (catalogUri.startsWith("xmldb:exist://")) {
            catalogUri = catalogUri.replace("xmldb:exist://", "xmldb://");
        } else if (catalogUri.startsWith("/db")) {
            catalogUri = "xmldb://" + catalogUri;
        }
        return catalogUri;
    }

}
