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
package org.exist.source;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.serializers.Serializer;
import org.exist.util.FileUtils;
import org.exist.xmldb.XmldbURI;
import org.xml.sax.SAXException;

import javax.annotation.Nullable;

/**
 * Factory to create a {@link org.exist.source.Source} object for a given
 * URL.
 *
 * @author wolf
 */
public class SourceFactory {

    private final static Logger LOG = LogManager.getLogger(SourceFactory.class);

    /**
     * Create a {@link Source} object for the given resource URL.
     *
     * As a special case, if the URL starts with "resource:", the resource
     * will be read from the current context class loader.
     *
     * @param broker the eXist-db DBBroker
     * @param contextPath the context path of the resource.
     * @param location the location of the resource (relative to the {@code contextPath}).
     * @param checkXQEncoding where we need to check the encoding of the XQuery.
     *
     * @return The Source of the resource, or null if the resource cannot be found.
     *
     * @throws PermissionDeniedException if the resource resides in the database,
     *     but the calling user does not have permission to access it.
     * @throws IOException if a general I/O error occurs whilst accessing the resource.
     */
    public static @Nullable Source getSource(final DBBroker broker, final String contextPath, final String location, final boolean checkXQEncoding) throws IOException, PermissionDeniedException {
        Source source = null;

        /* resource: */
        if (location.startsWith(ClassLoaderSource.PROTOCOL)
                || (contextPath != null && contextPath.startsWith(ClassLoaderSource.PROTOCOL))) {
            source = getSource_fromClasspath(contextPath, location);
        }

        /* xmldb */
        if (source == null
                && (location.startsWith(XmldbURI.XMLDB_URI_PREFIX)
                || (contextPath != null && contextPath.startsWith(XmldbURI.XMLDB_URI_PREFIX)))) {

            XmldbURI pathUri;
            try {
                if (contextPath == null) {
                    pathUri = XmldbURI.create(location);
                } else {
                    pathUri = XmldbURI.create(contextPath).append(location);
                }
            } catch (final IllegalArgumentException e) {
                // this is allowed if the location is already an absolute URI, below we will try using other schemes
                pathUri = null;
            }

            if (pathUri != null) {
                source = getSource_fromDb(broker, pathUri);
            }
        }

        /* /db */
        if (source == null
                && ((location.startsWith("/db") && !Files.exists(Paths.get(firstPathSegment(location))))
                || (contextPath != null && contextPath.startsWith("/db") && !Files.exists(Paths.get(firstPathSegment(contextPath)))))) {
            final XmldbURI pathUri;
            if (contextPath == null || ".".equals(contextPath)) {
                pathUri = XmldbURI.create(location);
            } else {
                pathUri = XmldbURI.create(contextPath).append(location);
            }
            source = getSource_fromDb(broker, pathUri);
        }

        /* file:// or location without scheme (:/) is assumed to be a file */
        if (source == null
                && (location.startsWith("file:/")
                || !location.contains(":/"))) {
            source = getSource_fromFile(contextPath, location, checkXQEncoding);
        }

        /* final attempt - any other URL */
        if (source == null
                && !(
                        location.startsWith(ClassLoaderSource.PROTOCOL)
                        || location.startsWith(XmldbURI.XMLDB_URI_PREFIX)
                        || location.startsWith("file:/"))
                ) {
            try {
                final URL url = new URL(location);
                source = new URLSource(url);
            } catch (final MalformedURLException e) {
                return null;
            }
        }

        return source;
    }

    private static String firstPathSegment(final String path) {
        return XmldbURI
                .create(path)
                .getPathSegments()[0]
                .getRawCollectionPath();
    }

    private static Source getSource_fromClasspath(final String contextPath, final String location) throws IOException {
        if (location.startsWith(ClassLoaderSource.PROTOCOL)) {
            return new ClassLoaderSource(location);
        }

        final Path rootPath = Paths.get(contextPath.substring(ClassLoaderSource.PROTOCOL.length()));

        // 1) try resolving location as child
        final Path childLocation = rootPath.resolve(location);
        try {
            return new ClassLoaderSource(ClassLoaderSource.PROTOCOL + childLocation.toString().replace('\\', '/'));
        } catch (final IOException e) {
            // no-op, we will try again below
        }

        // 2) try resolving location as sibling
        final Path siblingLocation = rootPath.resolveSibling(location);
        return new ClassLoaderSource(ClassLoaderSource.PROTOCOL + siblingLocation.toString().replace('\\', '/'));
    }

    /**
     * Get the resource source from the database.
     *
     * @param broker The database broker.
     * @param path The path to the resource in the database.
     *
     * @return the source, or null if there is no such resource in the db indicated by {@code path}.
     */
    private static @Nullable Source getSource_fromDb(final DBBroker broker, final XmldbURI path) throws PermissionDeniedException, IOException {
        Source source = null;
        try(final LockedDocument lockedResource = broker.getXMLResource(path, LockMode.READ_LOCK)) {
            if (lockedResource != null) {
                final DocumentImpl resource = lockedResource.getDocument();
                if (resource.getResourceType() == DocumentImpl.BINARY_FILE) {
                    source = new DBSource(broker, (BinaryDocument) resource, true);
                } else {
                    final Serializer serializer = broker.borrowSerializer();
                    try {
                        // XML document: serialize to string source so it can be read as a stream
                        // by fn:unparsed-text and friends
                        source = new StringSource(serializer.serialize(resource));
                    } catch (final SAXException e) {
                        throw new IOException(e.getMessage());
                    } finally {
                        broker.returnSerializer(serializer);
                    }
                }
            }
        }
        return source;
    }

    /**
     * Get the resource source from the filesystem.
     *
     * @param contextPath the context path of the resource.
     * @param location the location of the resource (relative to the {@code contextPath}).
     * @param checkXQEncoding where we need to check the encoding of the XQuery.
     *
     * @return the source, or null if there is no such resource in the db indicated by {@code path}.
     */
    private static @Nullable Source getSource_fromFile(final String contextPath, final String location, final boolean checkXQEncoding) {
        String locationPath = location.replaceAll("^(file:)?/*(.*)$", "$2");

        Source source = null;
        try {
            final Path p = Paths.get(contextPath, locationPath);
            if (Files.isReadable(p)) {
                locationPath = p.toUri().toASCIIString();
                source = new FileSource(p, checkXQEncoding);
            }
        } catch (final InvalidPathException e) {
            // continue trying
        }

        if (source == null) {
            try {
                final Path p2 = Paths.get(locationPath);
                if (Files.isReadable(p2)) {
                    locationPath = p2.toUri().toASCIIString();
                    source = new FileSource(p2, checkXQEncoding);
                }
            } catch (final InvalidPathException e) {
                // continue trying
            }
        }

        if (source == null && contextPath != null) {
            try {
                final Path p3 = Paths.get(contextPath).toAbsolutePath().resolve(locationPath);
                if (Files.isReadable(p3)) {
                    locationPath = p3.toUri().toASCIIString();
                    source = new FileSource(p3, checkXQEncoding);
                }
            } catch (final InvalidPathException e) {
                // continue trying
            }
        }

        if (source == null) {
            /*
             * Try to load as an absolute path
             */
            try {
                final Path p4 = Paths.get("/" + locationPath);
                if (Files.isReadable(p4)) {
                    locationPath = p4.toUri().toASCIIString();
                    source = new FileSource(p4, checkXQEncoding);
                }
            } catch (final InvalidPathException e) {
                // continue trying
            }
        }

        if (source == null && contextPath != null) {
            /*
             * Try to load from the folder of the contextPath
             */
            try {
                final Path p5 = Paths.get(contextPath).resolveSibling(locationPath);
                if (Files.isReadable(p5)) {
                    locationPath = p5.toUri().toASCIIString();
                    source = new FileSource(p5, checkXQEncoding);
                }
            } catch (final InvalidPathException e) {
                // continue trying
            }
        }

        if (source == null && contextPath != null) {
            /*
             * Try to load from the parent folder of the contextPath URL
             */
            try {
                Path p6 = null;
                if(contextPath.startsWith("file:/")) {
                    try {
                        p6 = Paths.get(new URI(contextPath)).resolveSibling(locationPath);
                    } catch (final URISyntaxException e) {
                        // continue trying
                    }
                }

                if(p6 == null) {
                    p6 = Paths.get(contextPath.replaceFirst("^file:/*(/.*)$", "$1")).resolveSibling(locationPath);
                }

                if (Files.isReadable(p6)) {
                    locationPath = p6.toUri().toASCIIString();
                    source = new FileSource(p6, checkXQEncoding);
                }
            } catch (final InvalidPathException e) {
                // continue trying
            }
        }

        if (source == null && contextPath != null) {
            /*
             * Try to load from the contextPath URL folder
             */
            try {
                Path p7 = null;
                if(contextPath.startsWith("file:/")) {
                    try {
                        p7 = Paths.get(new URI(contextPath)).resolve(locationPath);
                    } catch (final URISyntaxException e) {
                        // continue trying
                    }
                }

                if(p7 == null) {
                    p7 = Paths.get(contextPath.replaceFirst("^file:/*(/.*)$", "$1")).resolve(locationPath);
                }

                if (Files.isReadable(p7)) {
                    locationPath = p7.toUri().toASCIIString();
                    source = new FileSource(p7, checkXQEncoding);
                }
            } catch (final InvalidPathException e) {
                // continue trying
            }
        }

        if (source == null) {
            /*
             * Lastly we try to load it using EXIST_HOME as the reference point
             */
            Path p8 = null;
            try {
                p8 = FileUtils.resolve(BrokerPool.getInstance().getConfiguration().getExistHome(), locationPath);
                if (Files.isReadable(p8)) {
                    locationPath = p8.toUri().toASCIIString();
                    source = new FileSource(p8, checkXQEncoding);
                }
            } catch (final EXistException e) {
                LOG.warn(e);
            } catch (final InvalidPathException e) {
                // continue and abort below
            }
        }

        return source;
    }
}
