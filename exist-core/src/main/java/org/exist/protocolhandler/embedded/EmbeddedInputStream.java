/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * $Id: EmbeddedInputStream.java 223 2007-04-21 22:13:05Z dizzzz $
 */

package org.exist.protocolhandler.embedded;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import com.evolvedbinary.j8fu.Either;
import com.evolvedbinary.j8fu.lazy.LazyValE;
import net.jcip.annotations.NotThreadSafe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.LockedDocument;
import org.exist.protocolhandler.xmldb.XmldbURL;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.storage.serializers.Serializer;
import org.exist.util.io.CloseNotifyingInputStream;
import org.exist.util.io.TemporaryFileManager;
import org.exist.xmldb.XmldbURI;
import org.xml.sax.SAXException;

import javax.annotation.Nullable;

import static com.evolvedbinary.j8fu.Either.Left;
import static com.evolvedbinary.j8fu.Either.Right;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Read document from embedded database as an InputStream.
 *
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 * @author Dannes Wessels
 */
@NotThreadSafe
public class EmbeddedInputStream extends InputStream {
    
    private static final Logger LOG = LogManager.getLogger(EmbeddedInputStream.class);
    private final XmldbURL url;
    private final LazyValE<InputStream, IOException> underlyingStream;
    private boolean closed = false;

    /**
     * @param url Location of document in database.
     *
     * @throws IOException if there is a problem accessing the database instance.
     */
    public EmbeddedInputStream(final XmldbURL url) throws IOException {
        this(null, url);
    }

    /**
     * @param brokerPool the database instance.
     * @param url Location of document in database.
     *
     * @throws IOException if there is a problem accessing the database instance.
     */
    public EmbeddedInputStream(@Nullable final BrokerPool brokerPool, final XmldbURL url) throws IOException {
        try {
            this.url = url;
            final BrokerPool pool = brokerPool == null ? BrokerPool.getInstance(url.getInstanceName()) : brokerPool;
            this.underlyingStream = new LazyValE<>(() -> openStream(pool, url));
        } catch (final EXistException e) {
            throw new IOException(e);
        }
    }

    private static Either<IOException, InputStream> openStream(final BrokerPool pool, final XmldbURL url) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Begin document download");
        }

        try {
            final XmldbURI path = XmldbURI.create(url.getPath());

            try (final DBBroker broker = pool.getBroker()) {

                try(final LockedDocument lockedResource = broker.getXMLResource(path, Lock.LockMode.READ_LOCK)) {

                    if (lockedResource == null) {
                        // Test for collection
                        try(final Collection collection = broker.openCollection(path, Lock.LockMode.READ_LOCK)) {
                            if (collection == null) {
                                // No collection, no document
                                return Left(new IOException("Resource " + url.getPath() + " not found."));

                            } else {
                                // Collection
                                return Left(new IOException("Resource " + url.getPath() + " is a collection."));
                            }
                        }

                    } else {
                        final DocumentImpl resource = lockedResource.getDocument();
                        if (resource.getResourceType() == DocumentImpl.XML_FILE) {
                            final Serializer serializer = broker.getSerializer();
                            serializer.reset();

                            // Preserve doctype
                            serializer.setProperty(EXistOutputKeys.OUTPUT_DOCTYPE, "yes");

                            // serialize the XML to a temporary file
                            final TemporaryFileManager tempFileManager = TemporaryFileManager.getInstance();
                            final Path tempFile = tempFileManager.getTemporaryFile();
                            try (final Writer writer = Files.newBufferedWriter(tempFile, UTF_8, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                                serializer.serialize(resource, writer);
                            }

                            // NOTE: the temp file will be returned to the manager when the InputStream is closed
                            return Right(new CloseNotifyingInputStream(Files.newInputStream(tempFile, StandardOpenOption.READ), () -> tempFileManager.returnTemporaryFile(tempFile)));

                        } else if (resource.getResourceType() == BinaryDocument.BINARY_FILE) {
                            return Right(broker.getBinaryResource((BinaryDocument) resource));

                        } else {
                            return Left(new IOException("Unknown resource type " + url.getPath() + ": " + resource.getResourceType()));
                        }
                    }
                } finally {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("End document download");
                    }
                }
            }
        } catch (final EXistException | PermissionDeniedException | SAXException e) {
            LOG.error(e);
            return Left(new IOException(e.getMessage(), e));
        } catch (final IOException e) {
            return Left(e);
        }
    }
    
    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        if (closed) {
            throw new IOException("The underlying stream is closed");
        }

        return underlyingStream.get().read(b, off, len);
    }
    
    @Override
    public int read(final byte[] b) throws IOException {
        if (closed) {
            throw new IOException("The underlying stream is closed");
        }

        return underlyingStream.get().read(b, 0, b.length);
    }
    
    @Override
    public long skip(final long n) throws IOException {
        if (closed) {
            throw new IOException("The underlying stream is closed");
        }

        return underlyingStream.get().skip(n);
    }

    @Override
    public boolean markSupported() {
        if (closed) {
            return false;
        }

        try {
            return underlyingStream.get().markSupported();
        } catch (final IOException e) {
            LOG.error(e);
            return false;
        }
    }

    @Override
    public void mark(final int readlimit) {
        if (closed) {
            return;
        }

        try {
            underlyingStream.get().mark(readlimit);
        } catch (final IOException e) {
            LOG.error(e);
        }
    }

    @Override
    public void reset() throws IOException {
        if (closed) {
            return;
        }

        underlyingStream.get().reset();
    }
    
    @Override
    public int read() throws IOException {
        if (closed) {
            return -1;
        }

        return underlyingStream.get().read();
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }

        try {
            if (underlyingStream.isInitialized()) {
                underlyingStream.get().close();
            }
        } finally {
            closed = true;
        }
    }

    @Override
    public int available() throws IOException {
        if (closed) {
            return 0;
        }

        return underlyingStream.get().available();
    }
}
