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
    final var result = parseResponseElement(queryGet(root, "static-base-uri()"));
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
    put(test, XML_QUERY_BASE_URI, properties);

    final var getProperties = new HashMap<String, String>();
    getProperties.put("Authorization", "Basic " + credentials);
    final var result = parseResponseElement(get(test, getProperties));
    final var value = result.getElementsByTagNameNS(Namespaces.EXIST_NS, "value");
    assertThat(value.getLength()).isEqualTo(1);
    final var item = value.item(0);
    assertThat(item.getTextContent()).isEqualTo("what should execute script via get() return ? At present it throws a 500");
  }
}
