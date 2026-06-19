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
import org.exist.util.io.InputStreamUtil;
import org.junit.*;

import static org.exist.collections.CollectionConfiguration.DEFAULT_COLLECTION_CONFIG_FILE;
import static org.exist.samples.Samples.SAMPLES;
import static org.junit.Assert.*;
import static org.custommonkey.xmlunit.XMLAssert.assertXpathEvaluatesTo;

import java.io.IOException;
import java.io.InputStream;

import org.xml.sax.SAXException;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

/**
 * The {@code tournament/1.5} sample fixtures ship an XSD, an RNG, and a Schematron schema side by
 * side, all describing the same {@code Tournament} document shape. Their own embedded comment
 * explains why: {@code Tournament-valid.xml} and {@code Tournament-invalid.xml} only differ in a
 * co-occurrence constraint -- for a {@code Singles} tournament, {@code nbrParticipants} must equal
 * {@code nbrTeams} -- that neither RELAX NG nor W3C XML Schema can express on their own. Schematron
 * rules are embedded in both {@code Tournament.xsd} (via {@code xsd:appinfo}) and {@code
 * Tournament.rng} (via RELAX NG annotations) specifically to add that missing constraint, but {@code
 * validation:jaxp()}/{@code validation:jing()} only enforce the structural grammar, not embedded
 * Schematron annotations. {@link JingSchematronTest} validates the same pair against {@code
 * tournament-schema.sch} and correctly reports the second document as invalid.
 *
 * <p>Bare RNG bears this out directly: both documents are structurally valid against {@code
 * Tournament.rng} below, since it doesn't express the co-occurrence constraint either.</p>
 *
 * <p>Bare XSD does not -- both documents are structurally <em>invalid</em> against {@code
 * Tournament.xsd}, but for an unrelated reason: {@code Match} {@code m3} references a {@code Team}
 * {@code t5} that is never declared in {@code Teams} (only {@code t1}/{@code t2}/{@code t3} exist).
 * This is a pre-existing defect in this 2001-vintage third-party sample, present identically in both
 * documents -- caught by XSD's built-in {@code xsd:ID}/{@code xsd:IDREF} referential-integrity check
 * (which this {@code Tournament.rng} does not declare for the same elements), not by anything related
 * to the documented co-occurrence constraint. The two XSD tests below assert that both documents fail
 * with the exact same error, which is itself the point: XSD's verdict does not change based on whether
 * {@code nbrParticipants} matches {@code nbrTeams}, confirming it can't see that constraint either.</p>
 */
public class TournamentSchemaLanguageComparisonTest {

    private static final String[] TEST_RESOURCES =
            { "Tournament-valid.xml", "Tournament-invalid.xml", "Tournament.xsd", "Tournament.rng" };

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    private static final String noValidation = "<?xml version='1.0'?>" +
            "<collection xmlns='http://exist-db.org/collection-config/1.0'>" +
            "    <validation mode='no'/>" +
            "</collection>";

    @BeforeClass
    public static void prepareResources() throws Exception {

        // Switch off validation
        try (Collection conf = existEmbeddedServer.createCollection(existEmbeddedServer.getRoot(), "system/config/db/tournament")) {
            ExistXmldbEmbeddedServer.storeResource(conf, DEFAULT_COLLECTION_CONFIG_FILE, noValidation.getBytes());
        }

        try (Collection col15 = existEmbeddedServer.createCollection(existEmbeddedServer.getRoot(), "tournament/1.5")) {
            for (final String testResource : TEST_RESOURCES) {
                try (final InputStream is = SAMPLES.getSample("validation/tournament/1.5/" + testResource)) {
                    assertNotNull(is);
                    ExistXmldbEmbeddedServer.storeResource(col15, testResource, InputStreamUtil.readAll(is));
                }
            }
        }
    }

    @Test
    public void xsdStructureRejectsValidDocumentOnUnrelatedIdrefDefect() throws XMLDBException, SAXException, XpathException, IOException {
        // No xsi:schemaLocation hint on the instance -- resolved purely by Tournament.xsd's
        // targetNamespace via directory-search, the same mechanism JaxpXsdCatalogTest's
        // xsd_searched_* tests use. See the class javadoc for why this is "invalid".
        executeAndEvaluateMessage("validation:jaxp-report( doc('/db/tournament/1.5/Tournament-valid.xml'), false(), " +
                        "xs:anyURI('/db/tournament/1.5/') )", "invalid",
                "cvc-id.1: There is no ID/IDREF binding for IDREF 't5'.");
    }

    @Test
    public void xsdStructureRejectsCoOccurrenceViolatingDocumentIdentically() throws XMLDBException, SAXException, XpathException, IOException {
        // Bare XSD structural validation cannot see the Singles/nbrParticipants-vs-nbrTeams
        // co-occurrence constraint -- proven here by getting the exact same verdict and error as
        // the "valid" document above, despite the co-occurrence violation. Only the accompanying
        // Schematron rules (tested separately in JingSchematronTest) catch that constraint.
        executeAndEvaluateMessage("validation:jaxp-report( doc('/db/tournament/1.5/Tournament-invalid.xml'), false(), " +
                        "xs:anyURI('/db/tournament/1.5/') )", "invalid",
                "cvc-id.1: There is no ID/IDREF binding for IDREF 't5'.");
    }

    @Test
    public void rngStructureAcceptsValidDocument() throws XMLDBException, SAXException, XpathException, IOException {
        executeAndEvaluate("validation:jing-report( doc('/db/tournament/1.5/Tournament-valid.xml'), " +
                "doc('/db/tournament/1.5/Tournament.rng') )", "valid");
    }

    @Test
    public void rngStructureAcceptsCoOccurrenceViolatingDocument() throws XMLDBException, SAXException, XpathException, IOException {
        // Same co-occurrence limitation as the XSD case above, for RELAX NG.
        executeAndEvaluate("validation:jing-report( doc('/db/tournament/1.5/Tournament-invalid.xml'), " +
                "doc('/db/tournament/1.5/Tournament.rng') )", "valid");
    }

    private void executeAndEvaluate(final String query, final String expectedValue) throws XMLDBException, SAXException, IOException, XpathException {
        final ResourceSet results = existEmbeddedServer.executeQuery(query);
        assertEquals(1, results.getSize());

        final String r = (String) results.getResource(0).getContent();
        assertXpathEvaluatesTo(expectedValue, "//status/text()", r);
    }

    private void executeAndEvaluateMessage(final String query, final String expectedValue, final String expectedMessage)
            throws XMLDBException, SAXException, IOException, XpathException {
        final ResourceSet results = existEmbeddedServer.executeQuery(query);
        assertEquals(1, results.getSize());

        final String r = (String) results.getResource(0).getContent();
        assertXpathEvaluatesTo(expectedValue, "//status/text()", r);
        assertXpathEvaluatesTo(expectedMessage, "//message/text()", r);
    }
}
