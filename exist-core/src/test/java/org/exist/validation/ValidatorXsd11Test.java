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
package org.exist.validation;

import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.exist.security.AuthenticationException;
import org.exist.storage.BrokerPool;
import org.exist.test.ExistEmbeddedServer;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.exist.TestUtils.ADMIN_DB_PWD;
import static org.exist.TestUtils.ADMIN_DB_USER;
import static org.exist.util.PropertiesBuilder.propertiesBuilder;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link Validator#validateParse(InputStream, String, String)} (the org.exist.xmlrpc.RpcConnection
 * {@code isValid()} XML-RPC method's underlying implementation) builds its own validating SAX
 * {@link org.xml.sax.XMLReader} -- the same XSD-1.0-only dynamic-discovery pipeline {@code
 * org.exist.collections.MutableCollection}'s store-time validation used to be stuck with, before
 * an up-front {@code xsi:schemaLocation} peek + XSD 1.1 {@link javax.xml.validation.ValidatorHandler}
 * routing was added here too.
 *
 * @see <a href="https://github.com/eXist-db/exist/issues/6189">#6189</a>
 */
public class ValidatorXsd11Test {

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(
            propertiesBuilder().build(), true, true);

    private static final String XSD_1_1_ONLY_SCHEMA = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       xmlns:vc="http://www.w3.org/2007/XMLSchema-versioning" vc:minVersion="1.1">
                <xs:element name="root">
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element name="value1" type="xs:integer"/>
                            <xs:element name="value2" type="xs:integer"/>
                        </xs:sequence>
                        <xs:assert test="value2 gt value1"/>
                    </xs:complexType>
                </xs:element>
            </xs:schema>
            """;

    private static final String INSTANCE_TEMPLATE = """
            <root xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
            xsi:noNamespaceSchemaLocation="schema.xsd">
                <value1>%d</value1>
                <value2>%d</value2>
            </root>
            """;

    @Test
    public void conformingInstanceAgainstXsd11SchemaViaLocationHintIsValid() throws Exception {
        final Path tempDir = Files.createTempDirectory("validator-xsd11-conform-test");
        try {
            Files.writeString(tempDir.resolve("schema.xsd"), XSD_1_1_ONLY_SCHEMA, UTF_8);
            final String documentBaseUri = tempDir.resolve("instance.xml").toUri().toString();
            final String instance = INSTANCE_TEMPLATE.formatted(1, 2);

            final ValidationReport report = validate(instance, documentBaseUri);

            assertTrue("conforming instance against an XSD-1.1-only schema (via schemaLocation hint) should be valid: "
                    + describeFailure(report), report.isValid());
        } finally {
            Files.deleteIfExists(tempDir.resolve("schema.xsd"));
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    public void violatingInstanceAgainstXsd11SchemaViaLocationHintIsNotValid() throws Exception {
        final Path tempDir = Files.createTempDirectory("validator-xsd11-violate-test");
        try {
            Files.writeString(tempDir.resolve("schema.xsd"), XSD_1_1_ONLY_SCHEMA, UTF_8);
            final String documentBaseUri = tempDir.resolve("instance.xml").toUri().toString();
            // value2 (1) is not greater than value1 (2) -- violates the xs:assert.
            final String instance = INSTANCE_TEMPLATE.formatted(2, 1);

            final ValidationReport report = validate(instance, documentBaseUri);

            assertFalse("instance violating the xs:assert should not be valid", report.isValid());
        } finally {
            Files.deleteIfExists(tempDir.resolve("schema.xsd"));
            Files.deleteIfExists(tempDir);
        }
    }

    private static ValidationReport validate(final String instance, final String documentBaseUri) throws IOException, AuthenticationException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final Validator validator = new Validator(pool,
                pool.getSecurityManager().authenticate(ADMIN_DB_USER, ADMIN_DB_PWD));
        try (final InputStream is = new UnsynchronizedByteArrayInputStream(instance.getBytes(UTF_8))) {
            return validator.validate(is, null, documentBaseUri);
        }
    }

    private static String describeFailure(final ValidationReport report) {
        return report.getThrowable() != null ? report.getThrowable().getMessage() : String.join("; ", report.getValidationReportArray());
    }
}
