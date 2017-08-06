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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.xml.transform.Templates;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TemplatesHandler;
import javax.xml.transform.sax.TransformerHandler;
import net.jcip.annotations.ThreadSafe;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.xquery.Constants;
import org.exist.xquery.value.NodeValue;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * Factory for stylesheet resolver and compiler instances
 * and if instance is safe for reuse then it cached.
 *
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 */
@ThreadSafe
public class TemplatesFactory {

  private final static ConcurrentMap<String, StylesheetResolverAndCompiler> cache = new ConcurrentHashMap<>();

  public static Stylesheet stylesheet(String stylesheet, String baseUri, Properties properties, boolean useCache) {

    if (useCache && properties == null) {
      return stylesheet(stylesheet, baseUri);
    }

    String uri = uri(stylesheet, baseUri);

    return new StylesheetResolverAndCompiler(uri, properties);
  }

  private static Stylesheet stylesheet(String stylesheet, String baseUri) {

    String uri = uri(stylesheet, baseUri);

    return cache.computeIfAbsent(uri, StylesheetResolverAndCompiler::new);
  }

  public static Stylesheet stylesheet(String stylesheet, String baseUri, boolean useCache) {
    if (useCache) {
      return stylesheet(stylesheet, baseUri);
    } else {
      return new StylesheetResolverAndCompiler(uri(stylesheet, baseUri));
    }
  }

  private static String uri(String stylesheet, String baseUri) {
    String uri = stylesheet;
    if (stylesheet.indexOf(':') == Constants.STRING_NOT_FOUND) {
      Path f = Paths.get(stylesheet).normalize();
      if (Files.isReadable(f)) {
        uri = f.toUri().toASCIIString();
      } else {
        f = Paths.get(baseUri, stylesheet).normalize();
        if (Files.isReadable(f)) {
          uri = f.toUri().toASCIIString();
        }
      }
    }
    return uri;
  }

  public static <E extends Exception> Stylesheet stylesheet(DBBroker broker, NodeValue node, String baseUri)
      throws E, TransformerConfigurationException
  {
    SAXTransformerFactory factory = TransformerFactoryAllocator
        .getTransformerFactory(broker.getBrokerPool());

    String base = baseUri;
    Document doc = node.getOwnerDocument();
    if (doc != null) {
      String uri = doc.getDocumentURI();

      /*
       * This must be checked because in the event the stylesheet is
       * an in-memory document, it will cause an NPE
       */
      if (uri != null) {
        base = uri.substring(0, uri.lastIndexOf('/'));
      }
    }

    if (base != null) {
      factory.setURIResolver(new EXistURIResolver(broker.getBrokerPool(), base));
    }

    return new Stylesheet() {
      @Override
      public <E extends Exception> Templates templates(DBBroker broker, XSLTErrorsListener<E> errorListener)
          throws E, TransformerConfigurationException, IOException, PermissionDeniedException, SAXException {

        final TemplatesHandler handler = factory.newTemplatesHandler();

        handler.startDocument();

        node.toSAX(broker, handler, null);

        handler.endDocument();

        final Templates t = handler.getTemplates();

        //check for errors
        errorListener.checkForErrors();

        return t;
      }

      @Override
      public <E extends Exception> TransformerHandler newTransformerHandler(DBBroker broker,
          XSLTErrorsListener<E> errorListener)
          throws E, PermissionDeniedException, SAXException, TransformerConfigurationException, IOException {
        TransformerHandler handler = factory
            .newTransformerHandler(templates(broker, errorListener));

        handler.getTransformer().setErrorListener(errorListener);

        return handler;
      }
    };
  }
}
