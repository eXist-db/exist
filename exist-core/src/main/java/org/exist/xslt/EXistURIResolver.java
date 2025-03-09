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
package org.exist.xslt;

import java.io.IOException;
import java.lang.reflect.Array;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionException;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;

/**
 * Implementation of URIResolver which
 * will resolve paths from the eXist database
 */
public class EXistURIResolver implements URIResolver {

  private static final Logger LOG = LogManager.getLogger(EXistURIResolver.class);

  final BrokerPool db;
  final String basePath;

  public EXistURIResolver(final BrokerPool db, final String docPath) {
    this.db = db;
    this.basePath = normalize(docPath);
    if (LOG.isDebugEnabled()) {
      LOG.debug("EXistURIResolver base path set to {}", basePath);
    }
  }

  private String normalize(String uri) {
    if (uri.startsWith(XmldbURI.EMBEDDED_SERVER_URI_PREFIX)) {
      return uri.substring(XmldbURI.EMBEDDED_SERVER_URI_PREFIX.length());
    }

    return uri;
  }

  /**
   * Simplify a path removing any "." and ".." path elements.
   * Assumes an absolute path is given.
   */
  private String normalizePath(final String path) {
    if (!path.startsWith("/")) {
      throw new IllegalArgumentException("normalizePath may only be applied to an absolute path; " +
          "argument was: " + path + "; base: " + basePath);
    }

    final String[] pathComponents = path.substring(1).split("/");

    final int numPathComponents = Array.getLength(pathComponents);
    final String[] simplifiedComponents = new String[numPathComponents];
    int numSimplifiedComponents = 0;

    for (final String s : pathComponents) {
      // Remove empty elements ("/")
      if (s.isEmpty()) {
        continue;
      }
      // Remove identity elements (".")
      if (".".equals(s)) {
        continue;
      }
      // Remove parent elements ("..") unless at the root
      if ("..".equals(s)) {
        if (numSimplifiedComponents > 0) {
          numSimplifiedComponents--;
        }
        continue;
      }
      simplifiedComponents[numSimplifiedComponents++] = s;
    }

    if (numSimplifiedComponents == 0) {
      return "/";
    }

    final StringBuilder sb = new StringBuilder(path.length());
    for (int x = 0; x < numSimplifiedComponents; x++) {
      sb.append("/").append(simplifiedComponents[x]);
    }

    if (path.endsWith("/")) {
      sb.append("/");
    }

    return sb.toString();
  }

  @Override
  public Source resolve(final String href, String base) throws TransformerException {
    String path;

    if (href.isEmpty()) {
      path = base;
    } else {
      URI hrefURI = null;
      try {
        hrefURI = new URI(href);
      } catch (final URISyntaxException e) {
      }
      if (hrefURI != null && hrefURI.isAbsolute()) {
        path = href;
      } else {
        if (href.startsWith("/")) {
          path = href;
        } else if (href.startsWith(XmldbURI.EMBEDDED_SERVER_URI_PREFIX)) {
          path = href.substring(XmldbURI.EMBEDDED_SERVER_URI_PREFIX.length());
        } else if (base == null || base.isEmpty()) {
          path = basePath + "/" + href;
        } else {
          // Maybe base never contains this prefix?  Check to be sure.
          if (base.startsWith(XmldbURI.EMBEDDED_SERVER_URI_PREFIX)) {
            base = base.substring(XmldbURI.EMBEDDED_SERVER_URI_PREFIX.length());
          }
          path = base.substring(0, base.lastIndexOf('/') + 1) + href;
        }
      }
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("Resolving path {} with base {} to {}", href, base, path);
      // + " (URI = " + uri.toASCIIString() + ")");
    }

    if (path.startsWith("/")) {
      path = normalizePath(path);
      return databaseSource(path);
    } else {
      return urlSource(path);
    }
  }

  private Source urlSource(final String path) throws TransformerException {
    try {
      final URL url = new URL(path);
      return new StreamSource(url.openStream());
    } catch (final IOException e) {
      throw new TransformerException(e.getMessage(), e);
    }
  }

  private Source databaseSource(final String path) throws TransformerException {
    final XmldbURI uri = XmldbURI.create(path);

    final DBBroker broker = db.getActiveBroker();

    final DocumentImpl doc;
    try {
      doc = broker.getResource(uri, Permission.READ);
      if (doc == null) {
        LOG.error("Document {} not found", path);
        throw new TransformerException("Resource " + path + " not found in database.");
      }

      final Source source;
      if (doc instanceof BinaryDocument) {

        /*
         * NOTE: this is extremely unpleasant as we let a reference to the blob file
         * escape from the closure into the StreamSource. This means that the file could have been deleted
         * by time the user comes to access the StreamSource, however this was also
         * the case with eXist-db's previous design, and due to the lack of resource
         * management of the StreamSource class, there is little we can do to improve
         * the situation - AR.
         */
        try (final Txn transaction = broker.getBrokerPool().getTransactionManager().beginTransaction()) {
          source = broker.withBinaryFile(transaction, (BinaryDocument) doc, p -> {
            final StreamSource source1 = new StreamSource(p.toFile());
            source1.setSystemId(p.toUri().toString());
            return source1;
          });

          transaction.commit();

          return source;
        }

      } else {
        source = new EXistDbSource(broker, doc);
        source.setSystemId(uri.toASCIIString());
        return source;
      }
    } catch (final PermissionDeniedException | TransactionException | IOException e) {
      throw new TransformerException(e.getMessage(), e);
    }
  }
}
