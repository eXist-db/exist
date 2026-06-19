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
package org.exist.xquery.functions.validation;

import org.exist.util.XMLReaderObjectFactory;
import org.exist.validation.ValidationReport;
import org.junit.Test;
import org.xml.sax.InputSource;
import org.xml.sax.Parser;
import org.xml.sax.XMLReader;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pins {@link Jaxp#isMissingElementDeclaration(ValidationReport)}'s match against the bundled
 * Xerces fork's <em>actual</em>, real {@code cvc-elt.1.a} message text -- by driving a real
 * validating parse of a genuinely XSD-1.1-only schema (an {@code xs:assert}, which the bundled
 * fork's dynamic-discovery SAX pipeline cannot understand) through the same validating-SAX-parser
 * setup {@code Jaxp.getXMLReader()} uses, rather than asserting against a hand-typed guess at the
 * message string.
 *
 * <p>If a future upgrade of {@code org.exist-db.thirdparty.xerces:xercesImpl} rewords or
 * restructures this message, this test fails directly and immediately -- instead of the retry
 * safety net in {@code Jaxp.retryWithXsd11ValidatorIfNeeded} silently stopping to fire, which
 * would otherwise only surface indirectly (and much less diagnosably) via {@code
 * JaxpXsdCatalogTest#xsd11SearchedValid}/{@code xsd11SearchedInvalid} failing.</p>
 */
public class IsMissingElementDeclarationTest {

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
            </xs:schema>""";

    private static final String CONFORMING_INSTANCE = """
            <root xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
            xsi:noNamespaceSchemaLocation="schema.xsd">
                <value1>1</value1>
                <value2>2</value2>
            </root>""";

    @Test
    public void realCvcElt1aFailureIsRecognized() throws Exception {
        final Path tempDir = Files.createTempDirectory("is-missing-element-declaration-test");
        try {
            Files.writeString(tempDir.resolve("schema.xsd"), XSD_1_1_ONLY_SCHEMA, StandardCharsets.UTF_8);
            final Path instance = tempDir.resolve("instance.xml");
            Files.writeString(instance, CONFORMING_INSTANCE, StandardCharsets.UTF_8);

            final ValidationReport report = new ValidationReport();

            final SAXParserFactory saxFactory = SAXParserFactory.newInstance();
            saxFactory.setValidating(true);
            saxFactory.setNamespaceAware(true);
            saxFactory.setFeature(XMLReaderObjectFactory.APACHE_FEATURES_VALIDATION_SCHEMA, true);

            final SAXParser saxParser = saxFactory.newSAXParser();
            final XMLReader xmlReader = saxParser.getXMLReader();
            xmlReader.setErrorHandler(report);

            // Force English error messages, regardless of the running JVM's default locale --
            // matching Jaxp.getXMLReader(), since isMissingElementDeclaration() matches on this
            // exact formatted message text.
            if (xmlReader instanceof Parser legacyParser) {
                legacyParser.setLocale(Locale.ENGLISH);
            }

            try (var instanceStream = Files.newInputStream(instance)) {
                final InputSource instanceSource = new InputSource(instanceStream);
                instanceSource.setSystemId(instance.toUri().toString());
                xmlReader.parse(instanceSource);
            }

            assertFalse("a conforming XSD-1.1-only instance must fail under the XSD-1.0-only " +
                    "dynamic-discovery pipeline (that's the whole reason the retry/up-front XSD " +
                    "1.1 pipeline exists)", report.isValid());
            assertTrue("Jaxp.isMissingElementDeclaration() must recognize the real cvc-elt.1.a " +
                    "message this Xerces version actually produces for an XSD-1.1-only schema",
                    Jaxp.isMissingElementDeclaration(report));
        } finally {
            Files.deleteIfExists(tempDir.resolve("schema.xsd"));
            Files.deleteIfExists(tempDir.resolve("instance.xml"));
            Files.deleteIfExists(tempDir);
        }
    }
}
