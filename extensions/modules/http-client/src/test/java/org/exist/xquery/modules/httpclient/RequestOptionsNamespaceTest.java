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
package org.exist.xquery.modules.httpclient;

import org.exist.Namespaces;
import org.exist.xquery.XPathException;
import org.exist.xquery.modules.httpclient.config.RequestOptions;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import java.net.http.HttpClient;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class RequestOptionsNamespaceTest {

    private static Document createEmptyDocument() throws ParserConfigurationException {
        final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        final DocumentBuilder db = dbf.newDocumentBuilder();
        return db.newDocument();
    }

    @Test
    public void namespacedAttribute() throws XPathException, ParserConfigurationException {
        final Document doc = createEmptyDocument();
        final Element reqElem = doc.createElementNS(HttpClientModule.NAMESPACE_URI, "http:request");
        reqElem.setAttributeNS(HttpClientModule.NAMESPACE_URI, "follow-redirect", "false");
        doc.appendChild(reqElem);

        final RequestOptions options = RequestOptionsParser.parse(reqElem);
        assertFalse("follow-redirect should be false when set via namespaced attribute", options.requestOptions().followRedirect());
    }

    @Test
    public void existNamespacedAttribute() throws XPathException, ParserConfigurationException {
        final Document doc = createEmptyDocument();
        final Element reqElem = doc.createElementNS(HttpClientModule.NAMESPACE_URI, "http:request");
        reqElem.setAttributeNS(Namespaces.EXIST_NS, "follow-redirect", "false");
        doc.appendChild(reqElem);

        final RequestOptions options = RequestOptionsParser.parse(reqElem);
        assertFalse("follow-redirect should be false when set via exist-namespaced attribute", options.requestOptions().followRedirect());
    }

    @Test
    public void autoAcceptEncodingExistNamespaced() throws XPathException, ParserConfigurationException {
        final Document doc = createEmptyDocument();
        final Element reqElem = doc.createElementNS(HttpClientModule.NAMESPACE_URI, "http:request");
        reqElem.setAttributeNS(Namespaces.EXIST_NS, "auto-accept-encoding", "false");
        doc.appendChild(reqElem);

        final RequestOptions options = RequestOptionsParser.parse(reqElem);
        assertFalse("auto-accept-encoding should be false when set via exist-namespaced attribute", options.requestOptions().autoAcceptEncoding());
    }

    @Test
    public void autoAcceptEncodingNoNamespaceIgnored() throws XPathException, ParserConfigurationException {
        final Document doc = createEmptyDocument();
        final Element reqElem = doc.createElementNS(HttpClientModule.NAMESPACE_URI, "http:request");
        reqElem.setAttribute("auto-accept-encoding", "false");
        doc.appendChild(reqElem);

        final RequestOptions options = RequestOptionsParser.parse(reqElem);
        assertTrue("auto-accept-encoding should be true (default) when set via no-namespace attribute", options.requestOptions().autoAcceptEncoding());
    }

    @Test
    public void httpVersionExistNamespacedHttp11() throws XPathException, ParserConfigurationException {
        final Document doc = createEmptyDocument();
        final Element reqElem = doc.createElementNS(HttpClientModule.NAMESPACE_URI, "http:request");
        reqElem.setAttributeNS(Namespaces.EXIST_NS, "http-version", "1.1");
        doc.appendChild(reqElem);

        final RequestOptions options = RequestOptionsParser.parse(reqElem);
        assertEquals("http-version should be HTTP_1_1 when set via exist-namespaced attribute",
                HttpClient.Version.HTTP_1_1, options.requestOptions().httpVersion());
    }

    @Test
    public void httpVersionExistNamespacedHttp2() throws XPathException, ParserConfigurationException {
        final Document doc = createEmptyDocument();
        final Element reqElem = doc.createElementNS(HttpClientModule.NAMESPACE_URI, "http:request");
        reqElem.setAttributeNS(Namespaces.EXIST_NS, "http-version", "2");
        doc.appendChild(reqElem);

        final RequestOptions options = RequestOptionsParser.parse(reqElem);
        assertEquals("http-version should be HTTP_2 when set via exist-namespaced attribute",
                HttpClient.Version.HTTP_2, options.requestOptions().httpVersion());
    }

    @Test
    public void httpVersionExistNamespacedHttp123() throws XPathException, ParserConfigurationException {
        final Document doc = createEmptyDocument();
        final Element reqElem = doc.createElementNS(HttpClientModule.NAMESPACE_URI, "http:request");
        reqElem.setAttributeNS(Namespaces.EXIST_NS, "http-version", "1.2.3");
        doc.appendChild(reqElem);

        assertThrows(XPathException.class, () -> RequestOptionsParser.parse(reqElem));
    }

    @Test
    public void httpVersionNoNamespacedHttp2() throws XPathException, ParserConfigurationException {
        final Document doc = createEmptyDocument();
        final Element reqElem = doc.createElementNS(HttpClientModule.NAMESPACE_URI, "http:request");
        reqElem.setAttribute("http-version", "2");
        doc.appendChild(reqElem);

        final RequestOptions options = RequestOptionsParser.parse(reqElem);
        assertEquals("http-version should be HTTP_1_1 when set via exist-namespaced attribute",
                HttpClient.Version.HTTP_1_1, options.requestOptions().httpVersion());
    }

    @Test
    public void noHttpVersion() throws XPathException, ParserConfigurationException {
        final Document doc = createEmptyDocument();
        final Element reqElem = doc.createElementNS(HttpClientModule.NAMESPACE_URI, "http:request");
        doc.appendChild(reqElem);

        final RequestOptions options = RequestOptionsParser.parse(reqElem);
        assertEquals("http-version should be HTTP_1_1 when set via exist-namespaced attribute",
                HttpClient.Version.HTTP_1_1, options.requestOptions().httpVersion());
    }
}
