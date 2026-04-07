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
import org.apache.jackrabbit.webdav.io.InputContext;
import org.apache.jackrabbit.webdav.io.OutputContext;
import org.apache.jackrabbit.webdav.property.*;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.xmldb.XmldbURI;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Optional;
import java.util.Properties;

/**
 * Jackrabbit WebDAV resource representing an eXist-db document.
 * Delegates all database operations to {@link ExistDocument}.
 *
 * @author Joe Wicentowski
 */
public class ExistDavDocument extends ExistDavResource {

    private ExistDocument existDocument;
    private boolean initialized = false;

    public ExistDavDocument(final Properties configuration, final XmldbURI xmldbUri,
            final BrokerPool brokerPool, final Subject subject,
            final DavResourceLocator locator, final DavSession session,
            final DavResourceFactory factory) {
        super(configuration, xmldbUri, brokerPool, subject, locator, session, factory);
    }

    /**
     * Lazily initialize the underlying ExistDocument and load its metadata.
     */
    private void initExistDocument() {
        if (!initialized) {
            existDocument = new ExistDocument(configuration, xmldbUri, brokerPool);
            existDocument.setUser(subject);
            existDocument.initMetadata();
            initialized = true;
        }
    }

    @Override
    protected ExistResource getExistResource() {
        initExistDocument();
        return existDocument;
    }

    @Override
    public boolean exists() {
        try (final DBBroker broker = brokerPool.get(Optional.of(subject));
                final LockedDocument lockedDoc = broker.getXMLResource(xmldbUri, LockMode.READ_LOCK)) {
            return lockedDoc != null;
        } catch (final Exception e) {
            LOG.debug("Error checking existence of {}", xmldbUri, e);
            return false;
        }
    }

    @Override
    public boolean isCollection() {
        return false;
    }

    @Override
    public void spool(final OutputContext outputContext) throws IOException {
        if (!exists()) {
            throw new IOException("Document does not exist: " + xmldbUri);
        }

        initExistDocument();

        if (outputContext.hasStream()) {
            // Set response headers
            if (existDocument.getMimeType() != null) {
                outputContext.setContentType(existDocument.getMimeType());
            }
            // Don't set content length for XML — serialized size may differ from stored size.
            // Binary documents have exact sizes; XML documents are serialized on the fly.
            if (!existDocument.isXmlDocument()) {
                outputContext.setContentLength(existDocument.getContentLength());
            }
            outputContext.setModificationTime(getModificationTime());

            // Stream the document content
            final OutputStream os = outputContext.getOutputStream();
            try {
                existDocument.stream(os);
            } catch (final PermissionDeniedException e) {
                throw new IOException("Permission denied: " + xmldbUri, e);
            }
        }
    }

    @Override
    public DavPropertySet getProperties() {
        final DavPropertySet properties = super.getProperties();
        initExistDocument();

        // Content type
        if (existDocument.getMimeType() != null) {
            properties.add(new DefaultDavProperty<>(
                    DavPropertyName.GETCONTENTTYPE, existDocument.getMimeType()));
        }

        // Content length
        properties.add(new DefaultDavProperty<>(
                DavPropertyName.GETCONTENTLENGTH, String.valueOf(existDocument.getContentLength())));

        // ETag based on content length and modification time
        final String etag = "\"" + existDocument.getContentLength() + "-" + getModificationTime() + "\"";
        properties.add(new DefaultDavProperty<>(DavPropertyName.GETETAG, etag));

        return properties;
    }

    @Override
    public DavResource getCollection() {
        // Return the parent collection
        final XmldbURI parentUri = xmldbUri.removeLastSegment();
        try {
            final DavResourceLocator parentLocator = locator.getFactory().createResourceLocator(
                    locator.getPrefix(), locator.getWorkspacePath(), parentUri.toString());
            return factory.createResource(parentLocator, session);
        } catch (final DavException e) {
            LOG.error("Error getting parent collection for {}", xmldbUri, e);
            return null;
        }
    }

    @Override
    public void addMember(final DavResource resource, final InputContext inputContext)
            throws DavException {
        throw new DavException(DavServletResponse.SC_FORBIDDEN,
                "Cannot add members to a document resource");
    }

    @Override
    public DavResourceIterator getMembers() {
        return new DavResourceIteratorImpl(Collections.emptyList());
    }

    @Override
    public void removeMember(final DavResource member) throws DavException {
        throw new DavException(DavServletResponse.SC_FORBIDDEN,
                "Cannot remove members from a document resource");
    }

    @Override
    public void move(final DavResource destination) throws DavException {
        if (!exists()) {
            throw new DavException(DavServletResponse.SC_NOT_FOUND,
                    "Source document does not exist: " + xmldbUri);
        }

        initExistDocument();

        final XmldbURI destCollectionUri = getDestinationCollectionUri(destination);
        final String destName = getDestinationName(destination);

        try {
            existDocument.resourceCopyMove(destCollectionUri, destName, ExistResource.Mode.MOVE);
        } catch (final EXistException e) {
            throw new DavException(DavServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Failed to move document: " + e.getMessage());
        }
    }

    @Override
    public void copy(final DavResource destination, final boolean shallow) throws DavException {
        if (!exists()) {
            throw new DavException(DavServletResponse.SC_NOT_FOUND,
                    "Source document does not exist: " + xmldbUri);
        }

        initExistDocument();

        final XmldbURI destCollectionUri = getDestinationCollectionUri(destination);
        final String destName = getDestinationName(destination);

        // Verify destination collection exists (RFC 4918 §9.8.5)
        if (destination.getCollection() != null && !destination.getCollection().exists()) {
            throw new DavException(DavServletResponse.SC_CONFLICT,
                    "Destination collection does not exist");
        }

        try {
            existDocument.resourceCopyMove(destCollectionUri, destName, ExistResource.Mode.COPY);
        } catch (final EXistException e) {
            throw new DavException(DavServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Failed to copy document: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Utility methods
    // -----------------------------------------------------------------------

    private XmldbURI getDestinationCollectionUri(final DavResource destination) throws DavException {
        if (destination instanceof ExistDavResource existDest) {
            final XmldbURI destUri = existDest.getXmldbUri();
            // If the destination is a collection, the document goes into it
            if (destination.isCollection()) {
                return destUri;
            }
            // Otherwise, the parent of the destination is the target collection
            return destUri.removeLastSegment();
        }
        throw new DavException(DavServletResponse.SC_FORBIDDEN,
                "Destination is not an eXist-db resource");
    }

    private String getDestinationName(final DavResource destination) throws DavException {
        if (destination instanceof ExistDavResource existDest) {
            final XmldbURI destUri = existDest.getXmldbUri();
            if (destination.isCollection()) {
                // Moving into a collection - keep the original name
                return xmldbUri.lastSegment().toString();
            }
            return destUri.lastSegment().toString();
        }
        throw new DavException(DavServletResponse.SC_FORBIDDEN,
                "Destination is not an eXist-db resource");
    }
}
