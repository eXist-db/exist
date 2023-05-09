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
package org.exist.http;

import org.eclipse.jetty.http.HttpStatus;
import org.exist.Namespaces;
import org.exist.dom.memtree.SAXAdapter;
import org.exist.test.ExistWebServer;
import org.exist.util.ExistSAXParserFactory;
import org.exist.xmldb.XmldbURI;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

class RESTLib {

  static HttpURLConnection getConnection(final String url) throws IOException {
    final URL u = new URL(url);
    return (HttpURLConnection) u.openConnection();
  }

  static String readResponse(final InputStream is) throws IOException {
    try(final BufferedReader reader = new BufferedReader(new InputStreamReader(is, UTF_8))) {
      String line;
      final StringBuilder out = new StringBuilder();
      while ((line = reader.readLine()) != null) {
        out.append(line);
        out.append("\r\n");
      }
      return out.toString();
    }
  }


  static int parseResponse(final String data) throws IOException, SAXException, ParserConfigurationException {

    final Element root = parseResponseElement(data);
    final String hits = root.getAttributeNS(Namespaces.EXIST_NS, "hits");
    return Integer.parseInt(hits);
  }

  static Element parseResponseElement(final String data) throws IOException, SAXException, ParserConfigurationException {

    final SAXParserFactory factory = ExistSAXParserFactory.getSAXParserFactory();
    factory.setNamespaceAware(true);
    final InputSource src = new InputSource(new StringReader(data));
    final SAXParser parser = factory.newSAXParser();
    final XMLReader reader = parser.getXMLReader();
    final SAXAdapter adapter = new SAXAdapter();
    reader.setContentHandler(adapter);
    reader.parse(src);

    return adapter.getDocument().getDocumentElement();
  }

  static String queryGet(final String collectionURI, final String query) throws IOException {
    final String uri = collectionURI
        + "?_query="
        + URLEncoder
        .encode(query, UTF_8.displayName());
    final HttpURLConnection connect = getConnection(uri);
    try {
      connect.setRequestMethod("GET");
      connect.connect();

      final int r = connect.getResponseCode();
      assertEquals("Server returned response code " + r, HttpStatus.OK_200, r);

      return readResponse(connect.getInputStream());
    } finally {
      connect.disconnect();
    }
  }

  static String get(final String uri, final Map<String, String> properties) throws IOException {
    final HttpURLConnection connect = getConnection(uri);
    try {
      connect.setRequestMethod("GET");
      properties.forEach(connect::setRequestProperty);
      connect.connect();

      final int code = connect.getResponseCode();
      if (HttpStatus.isSuccess(code)) {
        return readResponse(connect.getInputStream());
      }
      throw new IOException("Server returned response code " + code + " " + readResponse(connect.getErrorStream()));
    } finally {
      connect.disconnect();
    }
  }

  static void put(final String resourceURI, final String xmlData, final Map<String, String> properties) throws IOException {
    final HttpURLConnection connect = getConnection(resourceURI);
    try {
      connect.setRequestMethod("PUT");
      connect.setDoOutput(true);
      properties.forEach(connect::setRequestProperty);

      connect.connect();
      try (final Writer writer = new OutputStreamWriter(connect.getOutputStream(), UTF_8)) {
        writer.write(xmlData);
      }

      final int code = connect.getResponseCode();
      assertEquals("Server returned response code " + code, HttpStatus.CREATED_201, code);
    } finally {
      connect.disconnect();
    }
  }


  static class ExistRESTServer extends ExistWebServer {

    ExistRESTServer() {
      super(true, false, true, true);
    }

    String getServerUri() {
      return "http://localhost:" + getPort() + "/rest";
    }

    String getServerUriRedirected() {
      return "http://localhost:" + getPort();
    }

    String getRootURI() {
      return getServerUri() + XmldbURI.ROOT_COLLECTION;
    }

    String getRootURI(final String path) {
      return getServerUri() + XmldbURI.ROOT_COLLECTION + path;
    }
  }
}
