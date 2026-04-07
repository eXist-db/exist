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
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.webdav.exceptions.CollectionDoesNotExistException;
import org.exist.webdav.exceptions.CollectionExistsException;
import org.exist.xmldb.XmldbURI;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/**
 * Jackrabbit WebDAV resource representing an eXist-db collection.
 * Delegates all database operations to {@link ExistCollection}.
 *
 * @author Joe Wicentowski
 */
public class ExistDavCollection extends ExistDavResource {

    private ExistCollection existCollection;
    private boolean initialized = false;

    public ExistDavCollection(final Properties configuration, final XmldbURI xmldbUri,
            final BrokerPool brokerPool, final Subject subject,
            final DavResourceLocator locator, final DavSession session,
            final DavResourceFactory factory) {
        super(configuration, xmldbUri, brokerPool, subject, locator, session, factory);
    }

    /**
     * Lazily initialize the underlying ExistCollection and load its metadata.
     */
    private void initExistCollection() {
        if (!initialized) {
            existCollection = new ExistCollection(configuration, xmldbUri, brokerPool);
            existCollection.setUser(subject);
            existCollection.initMetadata();
            initialized = true;
        }
    }

    @Override
    protected ExistResource getExistResource() {
        initExistCollection();
        return existCollection;
    }

    @Override
    public boolean exists() {
        try (final DBBroker broker = brokerPool.get(Optional.of(subject));
                final Collection collection = broker.openCollection(xmldbUri, LockMode.READ_LOCK)) {
            return collection != null;
        } catch (final Exception e) {
            LOG.debug("Error checking existence of collection {}", xmldbUri, e);
            return false;
        }
    }

    @Override
    public boolean isCollection() {
        return true;
    }

    @Override
    public void spool(final OutputContext outputContext) throws IOException {
        if (!outputContext.hasStream()) {
            return;
        }

        initExistCollection();

        outputContext.setContentType("text/html; charset=UTF-8");
        outputContext.setModificationTime(getModificationTime());

        final OutputStream os = outputContext.getOutputStream();
        try (final Writer writer = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
            writer.write("<!DOCTYPE html>\n<html>\n<head>\n");
            writer.write("<title>Collection: " + xmldbUri + "</title>\n");
            writer.write("</head>\n<body>\n");
            writer.write("<h1>Collection: " + xmldbUri + "</h1>\n");
            writer.write("<ul>\n");

            // List sub-collections
            for (final XmldbURI childUri : existCollection.getCollectionURIs()) {
                final String name = childUri.lastSegment().toString();
                writer.write("<li><a href=\"" + name + "/\">" + name + "/</a></li>\n");
            }

            // List documents
            for (final XmldbURI childUri : existCollection.getDocumentURIs()) {
                final String name = childUri.lastSegment().toString();
                writer.write("<li><a href=\"" + name + "\">" + name + "</a></li>\n");
            }

            writer.write("</ul>\n</body>\n</html>\n");
            writer.flush();
        }
    }

    @Override
    public DavPropertySet getProperties() {
        final DavPropertySet properties = super.getProperties();

        // Collections have content type of httpd/unix-directory (common convention)
        properties.add(new DefaultDavProperty<>(
                DavPropertyName.GETCONTENTTYPE, "httpd/unix-directory"));

        return properties;
    }

    @Override
    public DavResource getCollection() {
        // Return the parent collection, or null if this is the root /db
        if (xmldbUri.equals(XmldbURI.ROOT_COLLECTION_URI)) {
            return null;
        }

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
        if (!exists()) {
            throw new DavException(DavServletResponse.SC_CONFLICT,
                    "Collection does not exist: " + xmldbUri);
        }

        initExistCollection();

        if (resource.isCollection()) {
            // Create a sub-collection (MKCOL)
            // RFC 4918 §9.3: MKCOL with unsupported body must return 415
            if (inputContext != null && inputContext.hasStream()) {
                throw new DavException(DavServletResponse.SC_UNSUPPORTED_MEDIA_TYPE,
                        "MKCOL with request body is not supported");
            }
            final String name = getResourceName(resource);
            try {
                existCollection.createCollection(name);
            } catch (final PermissionDeniedException e) {
                throw new DavException(DavServletResponse.SC_FORBIDDEN,
                        "Permission denied: " + e.getMessage());
            } catch (final CollectionExistsException e) {
                throw new DavException(DavServletResponse.SC_METHOD_NOT_ALLOWED,
                        "Collection already exists: " + name);
            } catch (final EXistException e) {
                throw new DavException(DavServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "Failed to create collection: " + e.getMessage());
            }
        } else {
            // Create or update a document (PUT)
            if (inputContext == null || !inputContext.hasStream()) {
                throw new DavException(DavServletResponse.SC_BAD_REQUEST,
                        "No input stream provided for document creation");
            }

            final String name = getResourceName(resource);
            final InputStream is = inputContext.getInputStream();
            final long length = inputContext.getContentLength();
            final String contentType = inputContext.getContentType();

            try {
                existCollection.createFile(name, is, length, contentType);
            } catch (final IOException e) {
                throw new DavException(DavServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "Failed to store document: " + e.getMessage());
            } catch (final PermissionDeniedException e) {
                throw new DavException(DavServletResponse.SC_FORBIDDEN,
                        "Permission denied: " + e.getMessage());
            } catch (final CollectionDoesNotExistException e) {
                throw new DavException(DavServletResponse.SC_CONFLICT,
                        "Parent collection does not exist: " + e.getMessage());
            }
        }
    }

    @Override
    public DavResourceIterator getMembers() {
        initExistCollection();

        final List<DavResource> members = new ArrayList<>();

        // Add child collections
        for (final XmldbURI childUri : existCollection.getCollectionURIs()) {
            try {
                final DavResourceLocator childLocator = locator.getFactory().createResourceLocator(
                        locator.getPrefix(), locator.getWorkspacePath(), childUri.toString());
                members.add(factory.createResource(childLocator, session));
            } catch (final DavException e) {
                LOG.error("Error creating resource for collection member {}", childUri, e);
            }
        }

        // Add child documents
        for (final XmldbURI childUri : existCollection.getDocumentURIs()) {
            try {
                final DavResourceLocator childLocator = locator.getFactory().createResourceLocator(
                        locator.getPrefix(), locator.getWorkspacePath(), childUri.toString());
                members.add(factory.createResource(childLocator, session));
            } catch (final DavException e) {
                LOG.error("Error creating resource for document member {}", childUri, e);
            }
        }

        return new DavResourceIteratorImpl(members);
    }

    @Override
    public void removeMember(final DavResource member) throws DavException {
        if (!(member instanceof ExistDavResource existMember)) {
            throw new DavException(DavServletResponse.SC_FORBIDDEN,
                    "Cannot remove non-eXist-db resource");
        }

        if (!member.exists()) {
            throw new DavException(DavServletResponse.SC_NOT_FOUND,
                    "Resource does not exist: " + existMember.getXmldbUri());
        }

        if (member instanceof ExistDavDocument) {
            // Delete a document
            final ExistDocument doc = new ExistDocument(
                    configuration, existMember.getXmldbUri(), brokerPool);
            doc.setUser(subject);
            doc.delete();
        } else if (member instanceof ExistDavCollection) {
            // Delete a sub-collection
            final ExistCollection col = new ExistCollection(
                    configuration, existMember.getXmldbUri(), brokerPool);
            col.setUser(subject);
            col.delete();
        }
    }

    @Override
    public void move(final DavResource destination) throws DavException {
        if (!exists()) {
            throw new DavException(DavServletResponse.SC_NOT_FOUND,
                    "Source collection does not exist: " + xmldbUri);
        }

        initExistCollection();

        final XmldbURI destCollectionUri = getDestinationCollectionUri(destination);
        final String destName = getDestinationName(destination);

        try {
            existCollection.resourceCopyMove(destCollectionUri, destName, ExistResource.Mode.MOVE);
        } catch (final EXistException e) {
            throw new DavException(DavServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Failed to move collection: " + e.getMessage());
        }
    }

    @Override
    public void copy(final DavResource destination, final boolean shallow) throws DavException {
        if (!exists()) {
            throw new DavException(DavServletResponse.SC_NOT_FOUND,
                    "Source collection does not exist: " + xmldbUri);
        }

        initExistCollection();

        final XmldbURI destCollectionUri = getDestinationCollectionUri(destination);
        final String destName = getDestinationName(destination);

        try {
            existCollection.resourceCopyMove(destCollectionUri, destName, ExistResource.Mode.COPY);
        } catch (final EXistException e) {
            throw new DavException(DavServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Failed to copy collection: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Utility methods
    // -----------------------------------------------------------------------

    /**
     * Extract the resource name from a DavResource for use in addMember().
     */
    private String getResourceName(final DavResource resource) {
        if (resource instanceof ExistDavResource existRes) {
            return existRes.getXmldbUri().lastSegment().toString();
        }
        // Fallback: extract from resource path
        final String path = resource.getResourcePath();
        final int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    private XmldbURI getDestinationCollectionUri(final DavResource destination) throws DavException {
        if (destination instanceof ExistDavResource existDest) {
            // For collection move/copy, the destination's parent is the target
            return existDest.getXmldbUri().removeLastSegment();
        }
        throw new DavException(DavServletResponse.SC_FORBIDDEN,
                "Destination is not an eXist-db resource");
    }

    private String getDestinationName(final DavResource destination) throws DavException {
        if (destination instanceof ExistDavResource existDest) {
            return existDest.getXmldbUri().lastSegment().toString();
        }
        throw new DavException(DavServletResponse.SC_FORBIDDEN,
                "Destination is not an eXist-db resource");
    }
}
