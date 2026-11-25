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
package org.exist.xquery.functions.fn;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FnOuterMostInnerMostMultiDocTest {

    @RegisterExtension
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    private static final String TEST_COLLECTION = "outermost-multi-doc-test";

    private Collection testCollection;

    @BeforeEach
    void setUp() throws XMLDBException {
        final CollectionManagementService cms = existEmbeddedServer.getRoot().getService(CollectionManagementService.class);
        testCollection = cms.createCollection(TEST_COLLECTION);
        assertNotNull(testCollection);

        storeXml("doc1.xml", "<a id='p1'><b id='p1b'/></a>");
        storeXml("doc2.xml", "<a id='p2'><b id='p2b'/></a>");
    }

    @AfterEach
    void tearDown() throws XMLDBException {
        if (testCollection != null) {
            final CollectionManagementService cms = existEmbeddedServer.getRoot().getService(CollectionManagementService.class);
            cms.removeCollection(TEST_COLLECTION);
            testCollection = null;
        }
    }

    private void storeXml(final String name, final String content) throws XMLDBException {
        final Resource resource = testCollection.createResource(name, XMLResource.class);
        resource.setContent(content);
        testCollection.storeResource(resource);
    }

    @Test
    void outermostPreservesEqualNodeIdsAcrossInMemoryDocuments() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                "let $doc1 := document { <a id='m1'><b id='m1b'/></a> } "
                        + "let $doc2 := document { <a id='m2'><b id='m2b'/></a> } "
                        + "return string-join(for $n in fn:outermost(($doc1//node(), $doc2//node())) order by $n/@id return string($n/@id), ',')"
        );
        assertEquals(1, result.getSize());
        assertEquals("m1,m2", result.getResource(0).getContent());
    }

    @Test
    void innermostPreservesEqualNodeIdsAcrossInMemoryDocuments() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                "let $doc1 := document { <a id='m1'><b id='m1b'/></a> } "
                        + "let $doc2 := document { <a id='m2'><b id='m2b'/></a> } "
                        + "return string-join(for $n in fn:innermost(($doc1//node(), $doc2//node())) order by $n/@id return string($n/@id), ',')"
        );
        assertEquals(1, result.getSize());
        assertEquals("m1b,m2b", result.getResource(0).getContent());
    }

    @Test
    void outermostAndInnermostDoNotTreatCrossDocumentNodeIdsAsAncestors() throws XMLDBException {
        final ResourceSet outerResult = existEmbeddedServer.executeQuery(
                "let $doc1 := document { <a id='m1'><b id='m1b'/></a> } "
                        + "let $doc2 := document { <a id='m2'><b id='m2b'/></a> } "
                        + "return string-join(for $n in fn:outermost(($doc1/a/b, $doc2/a)) order by $n/@id return string($n/@id), ',')"
        );
        assertEquals(1, outerResult.getSize());
        assertEquals("m1b,m2", outerResult.getResource(0).getContent());

        final ResourceSet innerResult = existEmbeddedServer.executeQuery(
                "let $doc1 := document { <a id='m1'><b id='m1b'/></a> } "
                        + "let $doc2 := document { <a id='m2'><b id='m2b'/></a> } "
                        + "return string-join(for $n in fn:innermost(($doc1/a, $doc2/a/b)) order by $n/@id return string($n/@id), ',')"
        );
        assertEquals(1, innerResult.getSize());
        assertEquals("m1,m2b", innerResult.getResource(0).getContent());
    }

    @Test
    void outermostAndInnermostAcrossPersistentAndMixedDocuments() throws XMLDBException {
        final ResourceSet persistentOuter = existEmbeddedServer.executeQuery(
                "let $d1 := doc('/db/outermost-multi-doc-test/doc1.xml') "
                        + "let $d2 := doc('/db/outermost-multi-doc-test/doc2.xml') "
                        + "return string-join(for $n in fn:outermost(($d1//node(), $d1//a, $d2//node())) order by $n/@id return string($n/@id), ',')"
        );
        assertEquals(1, persistentOuter.getSize());
        assertEquals("p1,p2", persistentOuter.getResource(0).getContent());

        final ResourceSet persistentInner = existEmbeddedServer.executeQuery(
                "let $d1 := doc('/db/outermost-multi-doc-test/doc1.xml') "
                        + "let $d2 := doc('/db/outermost-multi-doc-test/doc2.xml') "
                        + "return string-join(for $n in fn:innermost(($d1//node(), $d1//b, $d2//node())) order by $n/@id return string($n/@id), ',')"
        );
        assertEquals(1, persistentInner.getSize());
        assertEquals("p1b,p2b", persistentInner.getResource(0).getContent());

        final ResourceSet singleDocOrder = existEmbeddedServer.executeQuery(
                "let $doc := document { <a><b id='first'/><c id='second'/></a> } "
                        + "return string-join(fn:outermost(($doc/a/c, $doc/a/b, $doc/a/c))/@id, ',')"
        );
        assertEquals(1, singleDocOrder.getSize());
        assertEquals("first,second", singleDocOrder.getResource(0).getContent());

        final ResourceSet mixedOuter = existEmbeddedServer.executeQuery(
                "let $d1 := doc('/db/outermost-multi-doc-test/doc1.xml') "
                        + "let $m1 := document { <a id='m1'><b id='m1b'/></a> } "
                        + "return count(fn:outermost(($d1, $m1)))"
        );
        assertEquals(1, mixedOuter.getSize());
        assertEquals("2", mixedOuter.getResource(0).getContent());

        final ResourceSet mixedInner = existEmbeddedServer.executeQuery(
                "let $d1 := doc('/db/outermost-multi-doc-test/doc1.xml') "
                        + "let $m1 := document { <a id='m1'><b id='m1b'/></a> } "
                        + "return count(fn:innermost(($d1, $m1)))"
        );
        assertEquals(1, mixedInner.getSize());
        assertEquals("2", mixedInner.getResource(0).getContent());
    }
}
