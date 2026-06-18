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
import org.xml.sax.InputSource;
import org.xmlresolver.CatalogManager;
import org.xmlresolver.Resolver;
import org.xmlresolver.ResolverFeature;
import org.xmlresolver.XMLResolverConfiguration;
import org.xmlresolver.utils.SaxProducer;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;

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
        final XMLResolverConfiguration resolverConfiguration = new XMLResolverConfiguration();
        resolverConfiguration.setFeature(ResolverFeature.RESOLVER_LOGGER_CLASS, "org.xmlresolver.logging.SystemLogger");
        resolverConfiguration.setFeature(ResolverFeature.CATALOG_LOADER_CLASS, "org.xmlresolver.loaders.ValidatingXmlLoader");
        resolverConfiguration.setFeature(ResolverFeature.CLASSPATH_CATALOGS, true);
        resolverConfiguration.setFeature(ResolverFeature.URI_FOR_SYSTEM, true);

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
        final XMLResolverConfiguration resolverConfiguration = new XMLResolverConfiguration();
        resolverConfiguration.setFeature(ResolverFeature.RESOLVER_LOGGER_CLASS, "org.xmlresolver.logging.SystemLogger");
        resolverConfiguration.setFeature(ResolverFeature.CATALOG_LOADER_CLASS, "org.xmlresolver.loaders.ValidatingXmlLoader");
        resolverConfiguration.setFeature(ResolverFeature.CLASSPATH_CATALOGS, true);
        resolverConfiguration.setFeature(ResolverFeature.URI_FOR_SYSTEM, true);

        final CatalogManager manager = resolverConfiguration.getFeature(ResolverFeature.CATALOG_MANAGER);

        for (final Tuple2<String, Optional<SaxProducer>> catalog : catalogs) {
            String strCatalogUri = catalog._1;
            strCatalogUri = sanitizeCatalogUri(strCatalogUri);
            if (catalog._2.isPresent()) {
                final URI catalogUri = new URI(strCatalogUri);
                // Register the catalog URI with the configuration first (mirrors what
                // XMLResolverConfiguration#addCatalog(URI, InputSource) does internally),
                // then have the manager load it directly from SAX events -- the manager
                // caches the result by URI, so the resolver never tries to dereference it.
                resolverConfiguration.addCatalog(strCatalogUri);
                manager.loadCatalog(catalogUri, catalog._2.get());
            } else {
                resolverConfiguration.addCatalog(strCatalogUri);
            }
        }

        return new Resolver(resolverConfiguration);
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
