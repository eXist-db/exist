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
package org.exist.protocolhandler.embedded;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.protocolhandler.xmldb.XmldbURL;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.LockManager;
import org.exist.storage.lock.ManagedDocumentLock;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.exist.xmldb.XmldbURI;
import org.xml.sax.InputSource;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 */
public class InMemoryOutputStream extends OutputStream {

  private final static Logger LOG = LogManager.getLogger(InMemoryOutputStream.class);

  private final XmldbURL xmldbURL;
  private final UnsynchronizedByteArrayOutputStream buffer;

  public InMemoryOutputStream(final XmldbURL xmldbURL) {
    this.xmldbURL = xmldbURL;
    this.buffer = new UnsynchronizedByteArrayOutputStream();
  }

  @Override
  public void write(final byte[] b) throws IOException {
    buffer.write(b);
  }

  @Override
  public void write(final byte[] b, final int off, final int len) throws IOException {
    buffer.write(b, off, len);
  }

  @Override
  public void flush() throws IOException {
    buffer.flush();
  }

  @Override
  public void write(final int b) throws IOException {
    buffer.write(b);
  }

  @Override
  public void close() throws IOException {
    buffer.close();

    final int length = buffer.size();
    stream(xmldbURL, buffer.toInputStream(),length);
  }

  public void stream(final XmldbURL xmldbURL, final byte[] data) throws IOException {
    try (final InputStream is = new UnsynchronizedByteArrayInputStream(data)) {
      stream(xmldbURL, is, data.length);
    }
  }

  public void stream(final XmldbURL xmldbURL, final InputStream is, final int length) throws IOException {
    BrokerPool db;
    try {
      db = BrokerPool.getInstance();
    } catch (EXistException e) {
      throw new IOException(e);
    }

    try (final DBBroker broker = db.getBroker()) {
      final XmldbURI collectionUri = XmldbURI.create(xmldbURL.getCollection());
      final XmldbURI documentUri = XmldbURI.create(xmldbURL.getDocumentName());

      final TransactionManager transact = db.getTransactionManager();
      try (final Txn txn = transact.beginTransaction();
           final Collection collection = broker.getOrCreateCollection(txn, collectionUri)) {

        if (collection == null) {
          throw new IOException("Resource " + collectionUri.toString() + " is not a collection.");
        }

        final LockManager lockManager = db.getLockManager();
        txn.acquireCollectionLock(() -> lockManager.acquireCollectionWriteLock(collectionUri));

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

        try(final ManagedDocumentLock lock = lockManager.acquireDocumentWriteLock(documentUri)) {
          if (mime.isXMLType()) {
            final InputSource inputsource = new InputSource(is);
            final IndexInfo info = collection.validateXMLResource(txn, broker, documentUri, inputsource);
            final DocumentImpl doc = info.getDocument();
            doc.getMetadata().setMimeType(contentType);
            collection.store(txn, broker, info, inputsource);
          } else {
            collection.addBinaryResource(txn, broker, documentUri, is, contentType, length);
          }
        }

        txn.commit();
      }
    } catch (final IOException ex) {
      LOG.debug(ex);
      throw ex;
    } catch (final Exception ex) {
      LOG.debug(ex);
      throw new IOException(ex.getMessage(), ex);
    }
  }
}
