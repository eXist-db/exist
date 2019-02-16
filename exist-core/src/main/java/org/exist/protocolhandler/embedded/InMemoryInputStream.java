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

    try (final FastByteArrayOutputStream os = new FastByteArrayOutputStream()) {

      try (DBBroker broker = db.getBroker()) {
        final XmldbURI path = XmldbURI.create(xmldbURL.getPath());

        DocumentImpl resource = null;
        Collection collection = null;
        try {
          resource = broker.getXMLResource(path, LockMode.READ_LOCK);
          if (resource == null) {
            // Test for collection
            collection = broker.openCollection(path, LockMode.READ_LOCK);
            if (collection == null) {
              // No collection, no document
              throw new IOException("Resource " + xmldbURL.getPath() + " not found.");

            } else {
              // Collection
              throw new IOException("Resource " + xmldbURL.getPath() + " is a collection.");
            }

          } else {
            if (resource.getResourceType() == DocumentImpl.XML_FILE) {
              final Serializer serializer = broker.getSerializer();
              serializer.reset();

              // Preserve doctype
              serializer.setProperty(EXistOutputKeys.OUTPUT_DOCTYPE, "yes");
              try (final Writer w = new OutputStreamWriter(os, "UTF-8")) {
                serializer.serialize(resource, w);
              }

            } else {
              broker.readBinaryResource((BinaryDocument) resource, os);
            }
          }
        } finally {
          if (collection != null) {
            collection.release(LockMode.READ_LOCK);
          }

          if (resource != null) {
            resource.getUpdateLock().release(LockMode.READ_LOCK);
          }
        }
      } catch (final IOException ex) {
        LOG.error(ex, ex);
        throw ex;
      } catch (final Exception ex) {
        LOG.error(ex, ex);
        throw new IOException(ex.getMessage(), ex);
      }

      return os.toFastByteInputStream();
    }
  }

}
