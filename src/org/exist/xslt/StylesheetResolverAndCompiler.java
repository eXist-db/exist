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
package org.exist.xslt;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;
import java.util.Properties;
import javax.xml.transform.Templates;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TemplatesHandler;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamSource;
import net.jcip.annotations.ThreadSafe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.serializers.Serializer;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.Constants;
import org.xml.sax.SAXException;

/**
 * {@link javax.xml.transform.Templates} resolver and compiler.
 *
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 */
@ThreadSafe
public class StylesheetResolverAndCompiler implements Stylesheet {

  protected final static Logger LOG = LogManager.getLogger(StylesheetResolverAndCompiler.class);

  SAXTransformerFactory factory;

  long lastModified = -1;
  Templates templates = null;

  String uri;
  String base;

  Properties properties;

  public StylesheetResolverAndCompiler(String uri) {
    this.uri = uri;

    final int p = uri.lastIndexOf("/");
    if (p != Constants.STRING_NOT_FOUND) {
      base = uri.substring(0, p);
    } else {
      base = uri;
    }
  }

  public StylesheetResolverAndCompiler(String uri, Properties properties) {
    this(uri);

    this.properties = properties;
  }

  public <E extends Exception> Templates templates(DBBroker broker, XSLTErrorsListener<E> errorListener)
      throws E, TransformerConfigurationException, IOException, PermissionDeniedException, SAXException {

    if (uri.startsWith(XmldbURI.EMBEDDED_SERVER_URI_PREFIX)) {
      final String docPath = uri.substring(XmldbURI.EMBEDDED_SERVER_URI_PREFIX.length());
      try (final LockedDocument lockedDocument = broker.getXMLResource(XmldbURI.create(docPath), LockMode.READ_LOCK)) {
        if (lockedDocument == null) {
          throw new IOException("XSL stylesheet not found: "+docPath);
        }
        final DocumentImpl doc = lockedDocument.getDocument();
        if (templates == null || doc.getMetadata().getLastModified() > lastModified) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("compiling stylesheet " + doc.getURI());
          }
          templates = compileTemplates(broker, doc, errorListener);
          lastModified = doc.getMetadata().getLastModified();
        }
      }

    } else {
      final URL url = new URL(uri);
      final URLConnection connection = url.openConnection();
      long modified = connection.getLastModified();
      if (templates == null || modified > lastModified || modified == 0) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("compiling stylesheet " + url);
        }
        try (final InputStream is = connection.getInputStream()) {
          templates = factory(broker.getBrokerPool(), errorListener).newTemplates(new StreamSource(is));
        }
      }
      lastModified = modified;
    }

    return templates;
  }

  @Override
  public <E extends Exception> TransformerHandler newTransformerHandler(DBBroker broker, XSLTErrorsListener<E> errorListener)
      throws E, PermissionDeniedException, SAXException, TransformerConfigurationException, IOException {

    TransformerHandler handler = cachedFactory(broker.getBrokerPool())
        .newTransformerHandler(templates(broker, errorListener));

    handler.getTransformer().setErrorListener(errorListener);

    return handler;
  }

  private <E extends Exception> Templates compileTemplates(
      DBBroker broker,
      DocumentImpl stylesheet,
      XSLTErrorsListener<E> errorListener)
      throws E, TransformerConfigurationException, SAXException
  {
    //factory.setURIResolver(new EXistURIResolver(broker, stylesheet.getCollection().getURI().toString()));

    final TemplatesHandler handler = factory(broker.getBrokerPool(), errorListener).newTemplatesHandler();

    handler.startDocument();

    final Serializer serializer = broker.newSerializer();
    serializer.reset();
    serializer.setSAXHandlers(handler, null);
    serializer.toSAX(stylesheet);

    handler.endDocument();

    final Templates t = handler.getTemplates();

    //check for errors
    errorListener.checkForErrors();

    return t;
  }

  private SAXTransformerFactory cachedFactory(BrokerPool db) {
    if (factory == null) {
      factory = TransformerFactoryAllocator.getTransformerFactory(db);

      if (properties != null) {
        //set any attributes
        for (final Map.Entry<Object, Object> attribute : properties.entrySet()) {
          factory.setAttribute((String) attribute.getKey(), attribute.getValue());
        }
      }
      factory.setURIResolver(new EXistURIResolver(db, base));
    }
    return factory;
  }

  private <E extends Exception> SAXTransformerFactory factory(BrokerPool db, XSLTErrorsListener<E> errorListener) {
    SAXTransformerFactory newFactory = TransformerFactoryAllocator.getTransformerFactory(db);

    if (properties != null) {
      //set any attributes
      for (final Map.Entry<Object, Object> attribute : properties.entrySet()) {
        newFactory.setAttribute((String) attribute.getKey(), attribute.getValue());
      }
    }
    newFactory.setURIResolver(new EXistURIResolver(db, base));
    newFactory.setErrorListener(errorListener);
    return newFactory;
  }
}
