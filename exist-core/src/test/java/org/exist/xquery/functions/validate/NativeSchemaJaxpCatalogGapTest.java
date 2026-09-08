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
package org.exist.xquery.functions.validate;

import org.custommonkey.xmlunit.exceptions.XpathException;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.xml.sax.SAXException;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.custommonkey.xmlunit.XMLAssert.assertXpathEvaluatesTo;
import static org.exist.collections.CollectionConfiguration.DEFAULT_COLLECTION_CONFIG_FILE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Closes the integration gap left by <a href="https://github.com/eXist-db/exist/pull/6528">#6528</a>:
 * native XSDs ship at {@code $EXIST_HOME/schema/} and {@code WEB-INF/catalog.xml} maps
 * {@code http://exist-db.org/collection-config/1.0} to {@code collection.xconf.xsd}, but nothing
 * asserts that {@code validation:jaxp*} can apply that grammar via an OASIS catalog the way
 * tools (e.g. eXide) are expected to.
 *
 * <p>{@link #jaxvXsd11AgainstNativeCollectionSchemaValid} /
 * {@link #jaxvXsd11AgainstNativeCollectionSchemaEmptyFailsAssert} are the control: the schema
 * file itself is fine when handed to {@code validation:jaxv-report} with the XSD 1.1 language URI.
 * {@link #jaxpViaOasisCatalogAgainstNativeCollectionSchemaValid} is the missing path — currently
 * fails with {@code cvc-elt.1.a} (declaration not found) even when the catalog {@code <uri>} entry
 * points at an absolute {@code file:} URI of the same XSD.</p>
 *
 * <p>Existing XSD 1.1 coverage in {@link JaxpXsdCatalogTest} only exercises
 * <em>directory-search</em> catalogs over {@code /db/.../}; {@link
 * org.exist.validation.CollectionConfigSchema6000Test} validates the native XSD via raw
 * {@code SchemaFactory}, not {@code validation:jaxp}.</p>
 *
 * @see <a href="https://github.com/eXist-db/exist/issues/6686">#6686</a>
 * @see <a href="https://github.com/eXist-db/eXide/issues/842">eXide#842</a>
 */
public class NativeSchemaJaxpCatalogGapTest {

    private static final String COLLECTION_CONFIG_NS = "http://exist-db.org/collection-config/1.0";
    private static final String XSD_1_1 = "http://www.w3.org/XML/XMLSchema/v1.1";

    private static final String VALID_COLLECTION_XCONF = """
            <collection xmlns="http://exist-db.org/collection-config/1.0">
                <index/>
            </collection>
            """;

    private static final String EMPTY_COLLECTION_XCONF = """
            <collection xmlns="http://exist-db.org/collection-config/1.0"/>
            """;

    private static final String NO_VALIDATION = """
            <?xml version='1.0'?>
            <collection xmlns='http://exist-db.org/collection-config/1.0'>
                <validation mode='no'/>
            </collection>
            """;

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer =
            new ExistXmldbEmbeddedServer(false, true, true);

    private static Path nativeSchemaPath;
    private static String nativeSchemaFileUri;

    @BeforeClass
    public static void prepareResources() throws XMLDBException, IOException {
        nativeSchemaPath = resolveSchemaPath();
        assertTrue("""
                Native schema not found at %s \
                (run from repo root: mvn test -pl exist-core -Dtest=NativeSchemaJaxpCatalogGapTest)
                """.formatted(nativeSchemaPath).strip(),
                Files.exists(nativeSchemaPath));
        nativeSchemaFileUri = nativeSchemaPath.toAbsolutePath().toUri().toString();

        try (Collection conf = existEmbeddedServer.createCollection(existEmbeddedServer.getRoot(),
                "system/config/db/native-schema-gap")) {
            ExistXmldbEmbeddedServer.storeResource(conf, DEFAULT_COLLECTION_CONFIG_FILE,
                    NO_VALIDATION.getBytes());
        }

        try (Collection col = existEmbeddedServer.createCollection(existEmbeddedServer.getRoot(),
                "native-schema-gap")) {
            // Mirrors WEB-INF/catalog.xml's collection-config mapping, but with an absolute
            // file: URI so the embedded harness does not depend on $EXIST_HOME layout.
            final String catalog = """
                    <?xml version="1.0"?>
                    <catalog xmlns="urn:oasis:names:tc:entity:xmlns:xml:catalog">
                        <uri name="%s" uri="%s"/>
                    </catalog>
                    """.formatted(COLLECTION_CONFIG_NS, nativeSchemaFileUri);
            ExistXmldbEmbeddedServer.storeResource(col, "catalog.xml", catalog.getBytes());
            ExistXmldbEmbeddedServer.storeResource(col, "valid.xml", VALID_COLLECTION_XCONF.getBytes());
            ExistXmldbEmbeddedServer.storeResource(col, "empty.xml", EMPTY_COLLECTION_XCONF.getBytes());
        }
    }

    @Before
    public void clearGrammarCache() throws XMLDBException {
        final ResourceSet results = existEmbeddedServer.executeQuery("validation:clear-grammar-cache()");
        results.getResource(0).getContent();
    }

    @Test
    public void jaxvXsd11AgainstNativeCollectionSchemaValid() throws Exception {
        final String query = """
                validation:jaxv-report(
                    doc('/db/native-schema-gap/valid.xml'),
                    xs:anyURI('%s'),
                    '%s')
                """.formatted(nativeSchemaFileUri, XSD_1_1);
        assertReportStatus(query, "valid");
    }

    @Test
    public void jaxvXsd11AgainstNativeCollectionSchemaEmptyFailsAssert() throws Exception {
        final String query = """
                validation:jaxv-report(
                    doc('/db/native-schema-gap/empty.xml'),
                    xs:anyURI('%s'),
                    '%s')
                """.formatted(nativeSchemaFileUri, XSD_1_1);
        final String report = executeOne(query);
        assertXpathEvaluatesTo("invalid", "//status/text()", report);
        assertTrue("expected xs:assert failure, got: " + report,
                report.contains("cvc-assertion") || report.contains("count(*)"));
    }

    /**
     * Expected RED on current develop: OASIS catalog → native {@code collection.xconf.xsd}
     * does not supply the grammar to {@code validation:jaxp-report} (cvc-elt.1.a).
     */
    @Test
    public void jaxpViaOasisCatalogAgainstNativeCollectionSchemaValid() throws Exception {
        final String query = """
                validation:jaxp-report(
                    doc('/db/native-schema-gap/valid.xml'),
                    false(),
                    xs:anyURI('/db/native-schema-gap/catalog.xml'))
                """;
        assertReportStatus(query, "valid");
    }

    private static void assertReportStatus(final String query, final String expected)
            throws XMLDBException, SAXException, IOException, XpathException {
        assertXpathEvaluatesTo(expected, "//status/text()", executeOne(query));
    }

    private static String executeOne(final String query) throws XMLDBException {
        final ResourceSet results = existEmbeddedServer.executeQuery(query);
        assertEquals(1, results.getSize());
        return (String) results.getResource(0).getContent();
    }

    private static Path resolveSchemaPath() {
        final Path base = Path.of(System.getProperty("user.dir"));
        Path p = base.resolve("schema").resolve("collection.xconf.xsd");
        if (!Files.exists(p)) {
            p = base.getParent().resolve("schema").resolve("collection.xconf.xsd");
        }
        return p;
    }
}
