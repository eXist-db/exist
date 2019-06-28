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
 * $Id: EmbeddedOutputStream.java 223 2007-04-21 22:13:05Z dizzzz $
 */

package org.exist.protocolhandler.embedded;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import com.evolvedbinary.j8fu.Either;
import com.evolvedbinary.j8fu.function.RunnableE;
import com.evolvedbinary.j8fu.lazy.LazyValE;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.protocolhandler.xmldb.XmldbURL;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.*;
import org.exist.util.io.CloseNotifyingOutputStream;
import org.exist.util.io.TemporaryFileManager;
import org.exist.xmldb.XmldbURI;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.annotation.Nullable;

import static com.evolvedbinary.j8fu.Either.Left;
import static com.evolvedbinary.j8fu.Either.Right;

/**
 * Write document to local database (embedded) using output stream.
 *
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 * @author Dannes Wessels
 */
public class EmbeddedOutputStream extends OutputStream {

    private static final Logger LOG = LogManager.getLogger(EmbeddedOutputStream.class);
    private final XmldbURL url;
    private final LazyValE<OutputStream, IOException> underlyingStream;
    private boolean closed = false;

    /**
     * @param url Location of document in database.
     *
     * @throws IOException if there is a problem accessing the database instance.
     */
    public EmbeddedOutputStream(final XmldbURL url) throws IOException {
        this(null, url);
    }

    /**
     * @param brokerPool the database instance.
     * @param url Location of document in database.
     *
     * @throws IOException if there is a problem accessing the database instance.
     */
    public EmbeddedOutputStream(@Nullable final BrokerPool brokerPool, final XmldbURL url) throws IOException {
        try {
            this.url = url;
            final BrokerPool pool = brokerPool == null ? BrokerPool.getInstance(url.getInstanceName()) : brokerPool;
            this.underlyingStream = new LazyValE<>(() -> openStream(pool, url));
        } catch (final EXistException e) {
            throw new IOException(e);
        }
    }

    private static Either<IOException, OutputStream> openStream(final BrokerPool pool, final XmldbURL url) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Begin document download");
        }

        try {
            // get a temporary file
            final TemporaryFileManager tempFileManager = TemporaryFileManager.getInstance();
            final Path tempFile = tempFileManager.getTemporaryFile();
            final OutputStream osTemp = Files.newOutputStream(tempFile, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

            // upload the content of the temp file to the db when it is closed, then return the temp file
            final RunnableE<IOException> uploadOnClose = () -> {
                uploadToDb(pool, url, tempFile);
                tempFileManager.returnTemporaryFile(tempFile);
            };

            return Right(new CloseNotifyingOutputStream(osTemp, uploadOnClose));
        } catch (final IOException e) {
            return Left(e);
        }
    }

    private static void uploadToDb(final BrokerPool pool, final XmldbURL url, final Path tempFile) throws IOException {
        try(final DBBroker broker = pool.getBroker()) {

            final XmldbURI collectionUri = XmldbURI.create(url.getCollection());
            final XmldbURI documentUri = XmldbURI.create(url.getDocumentName());

            try(final Collection collection = broker.openCollection(collectionUri, Lock.LockMode.WRITE_LOCK)) {

                if (collection == null) {
                    throw new IOException("Resource " + collectionUri.toString() + " is not a collection.");
                }

                if (collection.hasChildCollection(broker, documentUri)) {
                    throw new IOException("Resource " + documentUri.toString() + " is a collection.");
                }

                MimeType mime = MimeTable.getInstance().getContentTypeFor(documentUri);
                String contentType = null;
                if (mime != null) {
                    contentType = mime.getName();
                } else {
                    mime = MimeType.BINARY_TYPE;
                }

                final TransactionManager transact = pool.getTransactionManager();
                try (final Txn txn = transact.beginTransaction()) {

                    if (mime.isXMLType()) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Storing XML resource");
                        }
                        final InputSource inputsource = new FileInputSource(tempFile);
                        final IndexInfo info = collection.validateXMLResource(txn, broker, documentUri, inputsource);
                        final DocumentImpl doc = info.getDocument();
                        doc.getMetadata().setMimeType(contentType);
                        collection.store(txn, broker, info, inputsource);

                    } else {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Storing Binary resource");
                        }
                        try (final InputStream is = Files.newInputStream(tempFile)) {
                            collection.addBinaryResource(txn, broker, documentUri, is, contentType, FileUtils.sizeQuietly(tempFile));
                        }
                    }

                    txn.commit();
                }
            }
        } catch (final EXistException | PermissionDeniedException | LockException | SAXException e) {
            LOG.error(e);
            throw new IOException(e.getMessage(), e);
        } finally {
            if (LOG.isDebugEnabled()) {
                LOG.debug("End document upload");
            }
        }
    }
    
    @Override
    public void write(final int b) throws IOException {
        if (closed) {
            throw new IOException("The underlying stream is closed");
        }

        underlyingStream.get().write(b);
    }

    @Override
    public void write(final byte[] b) throws IOException {
        if (closed) {
            throw new IOException("The underlying stream is closed");
        }

        underlyingStream.get().write(b, 0, b.length);
    }

    @Override
    public void write(final byte[] b, int off, int len) throws IOException {
        if (closed) {
            throw new IOException("The underlying stream is closed");
        }

        underlyingStream.get().write(b, off, len);
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
    public void flush() throws IOException {
        if (closed) {
            return;
        }

        underlyingStream.get().flush();
    }
}
