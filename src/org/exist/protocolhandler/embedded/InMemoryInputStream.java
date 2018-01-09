/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2017 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.protocolhandler.embedded;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.LockedDocument;
import org.exist.protocolhandler.xmldb.XmldbURL;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.storage.serializers.Serializer;
import org.exist.util.io.FastByteArrayOutputStream;
import org.exist.xmldb.XmldbURI;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 */
public class InMemoryInputStream {

  private final static Logger LOG = LogManager.getLogger(InMemoryOutputStream.class);

  public static InputStream stream(XmldbURL xmldbURL) throws IOException {

    BrokerPool db;
    try {
      db = BrokerPool.getInstance();
    } catch (EXistException e) {
      throw new IOException(e);
    }

    try (final FastByteArrayOutputStream os = new FastByteArrayOutputStream();
         final DBBroker broker = db.getBroker()) {
      final XmldbURI path = XmldbURI.create(xmldbURL.getPath());

      // Test for collection
      try(final Collection collection = broker.openCollection(path, LockMode.READ_LOCK)) {
        if(collection != null) {
          // Collection
          throw new IOException("Resource " + xmldbURL.getPath() + " is a collection.");
        }

        try (final LockedDocument lockedDocument = broker.getXMLResource(path, LockMode.READ_LOCK)) {

//          // NOTE: early release of Collection lock inline with Asymmetrical Locking scheme
//          collection.close();

          if(lockedDocument == null) {
            // No collection, no document
            throw new IOException("Resource " + xmldbURL.getPath() + " not found.");
          }

          final DocumentImpl document = lockedDocument.getDocument();
          if (document.getResourceType() == DocumentImpl.XML_FILE) {
            final Serializer serializer = broker.getSerializer();
            serializer.reset();

            // Preserve doctype
            serializer.setProperty(EXistOutputKeys.OUTPUT_DOCTYPE, "yes");
            try(final Writer w = new OutputStreamWriter(os, "UTF-8")) {
              serializer.serialize(document, w);
            }

          } else {
            broker.readBinaryResource((BinaryDocument) document, os);
          }

          return os.toFastByteInputStream();
        }
      }
    } catch (final IOException ex) {
      LOG.error(ex,ex);
      throw ex;
    } catch (final Exception ex) {
      LOG.error(ex,ex);
      throw new IOException(ex.getMessage(), ex);
    }
  }
}
