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
package org.exist.xquery.functions.system;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilderFactory;

import org.exist.dom.memtree.DocumentImpl;
import org.exist.dom.memtree.MemTreeBuilder;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationRedactorTest {

    private static Document parse(final String xml) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static DocumentImpl redact(final String xml) throws Exception {
        final Document source = parse(xml);
        final MemTreeBuilder builder = new MemTreeBuilder();
        builder.startDocument();
        ConfigurationRedactor.copyRedacted(source, builder);
        builder.endDocument();
        return builder.getDocument();
    }

    @Test
    void nonCredentialValuesPassThroughUnchanged() throws Exception {
        final DocumentImpl result = redact("<exist><db-connection pool-size=\"20\"/></exist>");
        final Element dbConnection = (Element) result.getElementsByTagName("db-connection").item(0);
        assertThat(dbConnection.getAttribute("pool-size")).isEqualTo("20");
    }

    @Test
    void credentialShapedAttributeIsRedacted() throws Exception {
        final DocumentImpl result = redact("<exist><ldap bind-password=\"hunter2\"/></exist>");
        final Element ldap = (Element) result.getElementsByTagName("ldap").item(0);
        assertThat(ldap.getAttribute("bind-password")).isEqualTo(ConfigurationRedactor.REDACTED);
    }

    @Test
    void credentialShapedElementTextIsRedacted() throws Exception {
        final DocumentImpl result = redact("<exist><password>plaintext-secret</password></exist>");
        final Element password = (Element) result.getElementsByTagName("password").item(0);
        assertThat(password.getTextContent()).isEqualTo(ConfigurationRedactor.REDACTED);
    }

    @Test
    void parameterNameValueIdiomIsRedacted() throws Exception {
        // eXist's <parameter name="smtp-password" value="..."/> idiom: the secret is in
        // "value", not in an attribute literally named "password".
        final DocumentImpl result = redact(
                "<exist><mail-module><parameter name=\"smtp-password\" value=\"hunter2\"/></mail-module></exist>");
        final Element parameter = (Element) result.getElementsByTagName("parameter").item(0);
        assertThat(parameter.getAttribute("name")).isEqualTo("smtp-password");
        assertThat(parameter.getAttribute("value")).isEqualTo(ConfigurationRedactor.REDACTED);
    }

    @Test
    void parameterNameValueIdiomLeavesNonCredentialValuesAlone() throws Exception {
        final DocumentImpl result = redact(
                "<exist><module><parameter name=\"pool-size\" value=\"20\"/></module></exist>");
        final Element parameter = (Element) result.getElementsByTagName("parameter").item(0);
        assertThat(parameter.getAttribute("value")).isEqualTo("20");
    }

    @Test
    void structuralShapeIsPreserved() throws Exception {
        final DocumentImpl result = redact(
                "<exist><a><b><c password=\"x\">text</c></b></a></exist>");
        assertThat(result.getElementsByTagName("a").getLength()).isEqualTo(1);
        assertThat(result.getElementsByTagName("b").getLength()).isEqualTo(1);
        assertThat(result.getElementsByTagName("c").getLength()).isEqualTo(1);
    }

    @Test
    void keyAndTokenAreNotTreatedAsCredentialMarkers() {
        // intentionally excluded — too broad (cache key sizes, MIME type tokens)
        assertThat(ConfigurationRedactor.isCredentialMarker("key")).isFalse();
        assertThat(ConfigurationRedactor.isCredentialMarker("token")).isFalse();
        assertThat(ConfigurationRedactor.isCredentialMarker("cache-key-size")).isFalse();
    }
}
