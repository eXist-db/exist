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
package org.exist.webdav;

import org.apache.jackrabbit.webdav.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.collections.Collection;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.util.FileUtils;
import org.exist.xmldb.XmldbURI;

import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

/**
 * Factory for creating Jackrabbit WebDAV resource objects from eXist-db
 * database resources. Determines whether a given path refers to a collection
 * or document and returns the appropriate {@link ExistDavCollection} or
 * {@link ExistDavDocument} instance.
 *
 * @author Joe Wicentowski
 */
public class ExistDavResourceFactory implements DavResourceFactory {

    private static final Logger LOG = LogManager.getLogger(ExistDavResourceFactory.class);

    private final BrokerPool brokerPool;
    private final ExistLockManager lockManager;
    private final Properties webDavOptions = new Properties();

    /**
     * Constructor.
     *
     * @param pool the eXist-db broker pool
     */
    public ExistDavResourceFactory(final BrokerPool pool) {
        this.brokerPool = pool;
        this.lockManager = new ExistLockManager();

        // Load WebDAV serialization options
        loadConfiguration();
    }

    private void loadConfiguration() {
        // 1) try and read default config from classpath
        try (final InputStream is = getClass().getResourceAsStream("webdav.properties")) {
            if (is != null) {
                LOG.info("Read default WebDAV configuration from classpath");
                webDavOptions.load(is);
            }
        } catch (final Throwable ex) {
            LOG.error("Error reading WebDAV configuration from classpath", ex);
        }

        // 2) try and find overridden config relative to EXIST_HOME/etc
        try {
            final Optional<Path> eXistHome = brokerPool.getConfiguration().getExistHome();
            final Path config = FileUtils.resolve(eXistHome, "etc").resolve("webdav.properties");
            if (Files.isReadable(config)) {
                LOG.info("Read WebDAV configuration from {}", config.toAbsolutePath());
                try (final InputStream is = Files.newInputStream(config)) {
                    webDavOptions.load(is);
                }
            }
        } catch (final Throwable ex) {
            LOG.error("Error reading WebDAV configuration from etc/", ex);
        }
    }

    @Override
    public DavResource createResource(final DavResourceLocator locator,
            final DavServletRequest request, final DavServletResponse response)
            throws DavException {
        return createResource(locator, request.getDavSession(),
                "MKCOL".equalsIgnoreCase(request.getMethod()));
    }

    @Override
    public DavResource createResource(final DavResourceLocator locator,
            final DavSession session) throws DavException {
        return createResource(locator, session, false);
    }

    private DavResource createResource(final DavResourceLocator locator,
            final DavSession session, final boolean forMkcol) throws DavException {

        final String path = locator.getResourcePath();
        if (path == null) {
            throw new DavException(DavServletResponse.SC_BAD_REQUEST, "Resource path is null");
        }

        // Get the subject from the session
        final Subject subject;
        if (session instanceof ExistDavSession existSession) {
            subject = existSession.getSubject();
        } else {
            subject = brokerPool.getSecurityManager().getGuestSubject();
        }

        XmldbURI xmldbUri;
        try {
            xmldbUri = XmldbURI.xmldbUriFor(path);
        } catch (final URISyntaxException e) {
            throw new DavException(DavServletResponse.SC_BAD_REQUEST,
                    "Invalid resource path: " + path);
        }

        // Determine resource type
        final ResourceType type = getResourceType(xmldbUri, subject);

        return switch (type) {
            case COLLECTION -> {
                final ExistDavCollection collection = new ExistDavCollection(
                        webDavOptions, xmldbUri, brokerPool, subject, locator, session, this);
                collection.addLockManager(lockManager);
                yield collection;
            }
            case DOCUMENT -> {
                final ExistDavDocument document = new ExistDavDocument(
                        webDavOptions, xmldbUri, brokerPool, subject, locator, session, this);
                document.addLockManager(lockManager);
                yield document;
            }
            case NOT_EXISTING -> {
                if (forMkcol) {
                    // MKCOL: create a non-existing collection resource
                    final ExistDavCollection collection = new ExistDavCollection(
                            webDavOptions, xmldbUri, brokerPool, subject, locator, session, this);
                    collection.addLockManager(lockManager);
                    yield collection;
                } else {
                    // PUT: create a non-existing document resource
                    final ExistDavDocument document = new ExistDavDocument(
                            webDavOptions, xmldbUri, brokerPool, subject, locator, session, this);
                    document.addLockManager(lockManager);
                    yield document;
                }
            }
        };
    }

    /**
     * Determine whether the given URI refers to a collection, document,
     * or non-existing resource.
     */
    private ResourceType getResourceType(final XmldbURI xmldbUri, final Subject subject) {
        try (final DBBroker broker = brokerPool.get(Optional.of(subject));
                final Collection collection = broker.openCollection(xmldbUri, LockMode.READ_LOCK)) {

            if (collection != null) {
                return ResourceType.COLLECTION;
            }

            try (final LockedDocument lockedDoc = broker.getXMLResource(xmldbUri, LockMode.READ_LOCK)) {
                if (lockedDoc != null) {
                    return ResourceType.DOCUMENT;
                }
            }

        } catch (final Exception e) {
            LOG.error("Error determining resource type for {}", xmldbUri, e);
        }

        return ResourceType.NOT_EXISTING;
    }

    /**
     * Get the lock manager shared across all resources.
     *
     * @return the lock manager
     */
    public ExistLockManager getLockManager() {
        return lockManager;
    }

    private enum ResourceType {
        DOCUMENT, COLLECTION, NOT_EXISTING
    }
}
