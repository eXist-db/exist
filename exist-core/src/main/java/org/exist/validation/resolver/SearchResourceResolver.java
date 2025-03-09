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

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

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
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.validation.internal.DatabaseResources;
import org.exist.xmldb.XmldbURI;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xmlresolver.Resolver;

import javax.xml.transform.OutputKeys;

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
            LOG.debug("Specified collectionPath=" + collectionPath);
        }
    }


    @Override
    public XMLInputSource resolveEntity(final XMLResourceIdentifier xri) throws XNIException, IOException {
        if (xri.getExpandedSystemId() == null && xri.getLiteralSystemId() == null && xri.getNamespace() == null && xri.getPublicId() == null) {
            // quick fail
            return null;
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Resolving XMLResourceIdentifier: " + getXriDetails(xri));
        }


        String resourcePath = null;
        final DatabaseResources databaseResources = new DatabaseResources(brokerPool);

        if (xri.getNamespace() != null) {
            // XML Schema search
            if (LOG.isDebugEnabled()) {
                LOG.debug("Searching namespace '" + xri.getNamespace() + "' in database from " + collectionPath + "...");
            }

            resourcePath = databaseResources.findXSD(collectionPath, xri.getNamespace(), subject);

        } else if (xri.getPublicId() != null) {
            // Catalog search
            if (LOG.isDebugEnabled()) {
                LOG.debug("Searching publicId '" + xri.getPublicId() + "' in catalogs in database from " + collectionPath + "...");
            }

            String catalogPath = databaseResources.findCatalogWithDTD(collectionPath, xri.getPublicId(), subject);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Found publicId in catalog '" + catalogPath + "'");
            }

            if (catalogPath != null) {
                /* NOTE(AR): Catalog URL if stored in database must start with
                   URI Scheme xmldb:// so that the XML Resolver can use
                   org.exist.protocolhandler.protocols.xmldb.Handler
                   to resolve any relative URI resources from the database.
                 */
                try {
                    final Optional<InputSource> maybeInputSource;
                    if (catalogPath.startsWith("xmldb:exist://")) {
                        catalogPath = ResolverFactory.fixupExistCatalogUri(catalogPath);
                        maybeInputSource = Optional.of(new InputSource(new StringReader(serializeDocument(XmldbURI.create(catalogPath)))));
                    } else if (catalogPath.startsWith("/db")) {
                        catalogPath = ResolverFactory.fixupExistCatalogUri(catalogPath);
                        maybeInputSource = Optional.of(new InputSource(new StringReader(serializeDocument(XmldbURI.create(catalogPath)))));
                    } else {
                        maybeInputSource = Optional.empty();
                    }

                    if (maybeInputSource.isPresent()) {
                        maybeInputSource.get().setSystemId(catalogPath);
                    }

                    final Resolver resolver = ResolverFactory.newResolver(List.of(Tuple(catalogPath, maybeInputSource)));
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
            LOG.debug("resourcePath='" + resourcePath + "'");
        }
        
        final InputStream is = new URL(resourcePath).openStream();

        final XMLInputSource xis = new XMLInputSource(xri.getPublicId(), xri.getExpandedSystemId(), xri.getBaseSystemId(), is, UTF_8.name());

        if (LOG.isDebugEnabled()) {
            LOG.debug("XMLInputSource: " + getXisDetails(xis));
        }

        return xis;
    }

    private String getXriDetails(final XMLResourceIdentifier xrid) {
        return String.format("PublicId='%s' BaseSystemId='%s' ExpandedSystemId='%s' LiteralSystemId='%s' Namespace='%s' ",
                xrid.getPublicId(), xrid.getBaseSystemId(), xrid.getExpandedSystemId(), xrid.getLiteralSystemId(), xrid.getNamespace());
    }

    private String getXisDetails(final XMLInputSource xis) {
        return String.format("PublicId='%s' SystemId='%s' BaseSystemId='%s' Encoding='%s' ",
                xis.getPublicId(), xis.getSystemId(), xis.getBaseSystemId(), xis.getEncoding());
    }

    // TODO(AR) remove this when PR https://github.com/xmlresolver/xmlresolver/pull/98 is merged
    private String serializeDocument(final XmldbURI documentUri) throws SAXException, IOException {
        try (final DBBroker broker = brokerPool.get(Optional.of(subject));
             final LockedDocument lockedDocument = broker.getXMLResource(documentUri, Lock.LockMode.READ_LOCK)) {
            if (lockedDocument == null) {
                throw new IOException("No such document: " + documentUri);
            }
            final DocumentImpl doc = lockedDocument.getDocument();

            try (final StringWriter stringWriter = new StringWriter()) {
                final Properties outputProperties = new Properties();
                outputProperties.setProperty(OutputKeys.METHOD, "XML");
                outputProperties.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
                outputProperties.setProperty(OutputKeys.INDENT, "no");
                outputProperties.setProperty(OutputKeys.ENCODING, UTF_8.name());

                final Serializer serializer = broker.getSerializer();
                serializer.reset();
                SAXSerializer sax = null;
                try {
                    sax = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);
                    sax.setOutput(stringWriter, outputProperties);
                    serializer.setProperties(outputProperties);
                    serializer.setSAXHandlers(sax, sax);
                    serializer.toSAX(doc);
                } catch (final SAXNotSupportedException | SAXNotRecognizedException e) {
                    throw new SAXException(e.getMessage(), e);
                } finally {
                    if (sax != null) {
                        SerializerPool.getInstance().returnObject(sax);
                    }
                }

                return stringWriter.toString();
            }
        } catch (final EXistException | PermissionDeniedException e) {
            throw new IOException(e.getMessage(), e);
        }
    }
}
