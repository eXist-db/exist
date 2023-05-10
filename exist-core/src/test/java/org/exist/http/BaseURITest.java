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

import org.apache.commons.codec.binary.Base64;
import org.exist.Namespaces;
import org.junit.ClassRule;
import org.junit.Test;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.HashMap;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.exist.http.RESTLib.*;

public class BaseURITest {

  @ClassRule
  public static final RESTLib.ExistRESTServer eXistRESTServer = new RESTLib.ExistRESTServer();

  static final String credentials = Base64.encodeBase64String("admin:".getBytes(UTF_8));

  @Test public void baseURIQuery() throws IOException, ParserConfigurationException, SAXException {

    final var root = eXistRESTServer.getRootURI("/test");
    final var result = parseResponseElement(httpQueryGet(root, "static-base-uri()"));
    final var value = result.getElementsByTagNameNS(Namespaces.EXIST_NS, "value");
    assertThat(value.getLength()).isEqualTo(1);
    final var item = value.item(0);
    assertThat(item.getTextContent()).isEqualTo("what should ?_query return ? Not /db/test as it does");
  }

  private static final String XML_QUERY_BASE_URI = """
      xquery version "3.1";
      <tests>
          <static-base-uri>{ static-base-uri() }</static-base-uri>
          <does-sbu-exist>{ exists(static-base-uri()) }</does-sbu-exist>
          <rel>{ resolve-uri('#foobar') }</rel>
      </tests>
      """;

  @Test public void baseURIScript() throws IOException, ParserConfigurationException, SAXException {
    final var test = eXistRESTServer.getRootURI("/test/test.xq");
    final var properties = new HashMap<String, String>();
    properties.put("Authorization", "Basic " + credentials);
    properties.put("Content-Type", "application/xquery; charset=UTF-8");
    httpPut(test, XML_QUERY_BASE_URI, properties);

    final var getProperties = new HashMap<String, String>();
    getProperties.put("Authorization", "Basic " + credentials);
    final var result = parseResponseElement(httpGet(test, getProperties));
    final var value = result.getElementsByTagNameNS(Namespaces.EXIST_NS, "value");
    assertThat(value.getLength()).isEqualTo(1);
    final var item = value.item(0);
    assertThat(item.getTextContent()).isEqualTo("what should execute script via get() return ? At present it throws a 500");
  }

  private static final String XML_QUERY_EXTENDED_BASE_URI = """
      <query xmlns="http://exist.sourceforge.net/NS/exist" start="1" max="10">
      <text><![CDATA[%s]]></text>
      </query>
      """.formatted(XML_QUERY_BASE_URI);

  @Test public void baseURIExtendedQuery() throws IOException, ParserConfigurationException, SAXException {

    final var root = eXistRESTServer.getRootURI("/test");
    final var properties = new HashMap<String, String>();
    properties.put("Authorization", "Basic " + credentials);
    properties.put("Content-Type", "application/xml; charset=UTF-8");
    final var result = parseResponseElement(httpPost(root, XML_QUERY_EXTENDED_BASE_URI, properties));
    final var tests = result.getElementsByTagName("tests").item(0);
    final var children = tests.getChildNodes();
    var staticBaseURI = "/not/found";
    for (int i = 0; i < children.getLength(); i++) {
      if (children.item(i) instanceof Element element) {
        if (element.getTagName().equals("static-base-uri")) {
          staticBaseURI = element.getTextContent();
          break;
        }
      }
    }
    assertThat(staticBaseURI).isEqualTo("/not/db/test");
    assertThat(1).isEqualTo(2);
  }

  private static final String RESTXQ_BASE_URI = """
      xquery version "3.1";
      module namespace ex = "http://example/restxq/1";
      import module namespace rest = "http://exquery.org/ns/restxq";
      
      declare
          %rest:GET
      function ex:base-uri-using-restxq() {
          <result>We should call base-uri for real!</result>
      };
      """;

  /**
   * Put a RESTXQ handler in the database, and then invoke it
   *
   * @throws IOException
   * @throws ParserConfigurationException
   * @throws SAXException
   */
  @Test
  public void baseURIRESTXQ() throws IOException, ParserConfigurationException, SAXException {

    final var test = eXistRESTServer.getRootURI("/test/test-restxq.xq");
    final var properties = new HashMap<String, String>();
    properties.put("Authorization", "Basic " + credentials);
    properties.put("Content-Type", "application/xquery; charset=UTF-8");
    httpPut(test, RESTXQ_BASE_URI, properties);

    final var restXQ = eXistRESTServer.getRestXQURI("/any/random/path");
    final var result = parseResponseElement(httpGet(restXQ, new HashMap<>()));
    assertThat(result.getAttribute("non-existent")).isEqualTo("42");
  }
}
