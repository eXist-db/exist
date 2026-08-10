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
package org.exist.resolver;

import org.junit.Test;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xmlresolver.Resolver;

import javax.xml.parsers.SAXParserFactory;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Regression tests for two long-standing catalog-resolution issues, exercised directly against
 * {@link ResolverFactory} and {@link XercesXmlResolverAdapter} -- the same machinery {@code
 * org.exist.util.XMLReaderObjectFactory} wires into every pooled {@link XMLReader}, which is what
 * both {@code fn:parse-xml()} ({@code org.exist.xquery.functions.fn.ParsingFunctions}) and the
 * default SAX validation pipeline ({@code org.exist.xquery.functions.validation.Jaxp}) use to parse
 * documents.
 */
public class CatalogResolutionRegressionTest {

    /**
     * Regression test for <a href="https://github.com/eXist-db/exist/issues/1975">#1975</a>:
     * {@code fn:parse-xml()}/{@code util:parse()} parse an in-memory string with no inherent base
     * URI. A relative DOCTYPE {@code SYSTEM} identifier would be expanded against the JVM's working
     * directory before the catalog is even consulted (Xerces' own behaviour, not eXist's), so the
     * only base-URI-independent way a catalog can resolve such a DTD is by {@code PUBLIC} identifier
     * -- this proves that path still works, matching how
     * {@code DatabaseInsertResourcesWithValidationTest} relies on the same {@code -//PLAY//EN}
     * mechanism elsewhere in the test suite.
     */
    @Test
    public void catalogResolvesPublicEntityWithNoBaseUri() throws Exception {
        final Path tempDir = Files.createTempDirectory("catalog-1975-test");
        try {
            final Path dtd = tempDir.resolve("greeting.dtd");
            Files.writeString(dtd, "<!ENTITY greeting \"Hello from catalog\">");

            final Path catalog = tempDir.resolve("catalog.xml");
            Files.writeString(catalog, """
                    <?xml version="1.0"?>
                    <catalog xmlns="urn:oasis:names:tc:entity:xmlns:xml:catalog">
                        <public publicId="-//EXIST-TEST//GREETING//EN" uri="%s"/>
                    </catalog>
                    """.formatted(dtd.toUri()));

            final String resolved = parseWithCatalog(catalog.toUri().toString(),
                    "<!DOCTYPE root PUBLIC \"-//EXIST-TEST//GREETING//EN\" \"greeting.dtd\">"
                            + "<root>&greeting;</root>");
            assertEquals("Hello from catalog", resolved);
        } finally {
            Files.deleteIfExists(tempDir.resolve("greeting.dtd"));
            Files.deleteIfExists(tempDir.resolve("catalog.xml"));
            Files.deleteIfExists(tempDir);
        }
    }

    /**
     * Regression test for <a href="https://github.com/eXist-db/exist/issues/2476">#2476</a>:
     * a configured catalog must decline (return {@code null}) for a systemId it has no entry for,
     * rather than falling back to fetching the literal (document-author-controlled, here
     * attacker-controlled) URI itself -- which is what caused the original report's eXist process to
     * make tens of gigabytes of outbound requests to third-party hosts named in untrusted documents.
     * This is governed by {@code org.xmlresolver.ResolverFeature#ALWAYS_RESOLVE}, which {@link
     * ResolverFactory} never sets, relying on the library default of {@code false}.
     */
    @Test(timeout = 5000)
    public void catalogWithoutMatchingEntryDoesNotFetchUnmatchedRemoteSystemId() throws Exception {
        final Path tempDir = Files.createTempDirectory("catalog-2476-test");
        try {
            final Path catalog = tempDir.resolve("catalog.xml");
            Files.writeString(catalog, """
                    <?xml version="1.0"?>
                    <catalog xmlns="urn:oasis:names:tc:entity:xmlns:xml:catalog">
                        <public publicId="-//SOME//KNOWN//EN" uri="known.dtd"/>
                    </catalog>
                    """);

            final Resolver resolver = ResolverFactory.newResolver(
                    List.of(Tuple(catalog.toUri().toString(), Optional.<InputSource>empty())));

            // 203.0.113.1 is TEST-NET-3 (RFC 5737), guaranteed non-routable -- if the resolver
            // actually attempted to fetch this unmatched systemId itself (the ALWAYS_RESOLVE=true
            // behavior) rather than declining, this call would hang/time out instead of returning
            // promptly.
            final InputSource result = resolver.resolveEntity(null, null, null, "http://203.0.113.1:1/unmatched.dtd");
            assertNull("a catalog with no matching entry must decline to resolve, not fetch the literal URI itself",
                    result);
        } finally {
            Files.deleteIfExists(tempDir.resolve("catalog.xml"));
            Files.deleteIfExists(tempDir);
        }
    }

    private static String parseWithCatalog(final String catalogUri, final String xml) throws Exception {
        final Resolver resolver = ResolverFactory.newResolver(List.of(Tuple(catalogUri, Optional.<InputSource>empty())));

        final SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        final XMLReader xmlReader = factory.newSAXParser().getXMLReader();
        xmlReader.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", true);
        XercesXmlResolverAdapter.setXmlReaderEntityResolver(xmlReader, resolver);

        final StringBuilder characters = new StringBuilder();
        xmlReader.setContentHandler(new DefaultHandler() {
            @Override
            public void characters(final char[] ch, final int start, final int length) {
                characters.append(ch, start, length);
            }
        });

        // No systemId/base is set on the InputSource -- mirroring fn:parse-xml()/util:parse()
        // parsing an in-memory string with no inherent base URI, the exact scenario from issue #1975.
        xmlReader.parse(new InputSource(new StringReader(xml)));

        return characters.toString();
    }
}
