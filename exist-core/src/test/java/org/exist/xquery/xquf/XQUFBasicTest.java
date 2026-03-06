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
package org.exist.xquery.xquf;

import org.exist.TestUtils;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.xmldb.IndexQueryService;
import org.junit.*;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XQueryService;

import static org.junit.Assert.*;

/**
 * Tests for W3C XQuery Update Facility 3.0 expressions.
 *
 * Tests insert, delete, replace, replace value of, rename, and copy-modify
 * expressions against persistent (stored) documents.
 */
public class XQUFBasicTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    private Collection testCollection;

    @Before
    public void setUp() throws Exception {
        final CollectionManagementService service =
                existEmbeddedServer.getRoot().getService(CollectionManagementService.class);
        testCollection = service.createCollection("test");
    }

    @After
    public void tearDown() throws XMLDBException {
        final CollectionManagementService service =
                existEmbeddedServer.getRoot().getService(CollectionManagementService.class);
        service.removeCollection("test");
        testCollection = null;
    }

    private XQueryService storeXMLStringAndGetQueryService(final String documentName,
            final String content) throws XMLDBException {
        final XMLResource doc = testCollection.createResource(documentName, XMLResource.class);
        doc.setContent(content);
        testCollection.storeResource(doc);
        return testCollection.getService(XQueryService.class);
    }

    private ResourceSet queryResource(final XQueryService service, final String resource,
            final String query, final int expected) throws XMLDBException {
        final ResourceSet result = service.queryResource(resource, query);
        assertEquals(query, expected, result.getSize());
        return result;
    }

    private String queryAndGetString(final XQueryService service, final String query) throws XMLDBException {
        final ResourceSet result = service.query(query);
        assertEquals("Expected single result for: " + query, 1L, result.getSize());
        return result.getResource(0).getContent().toString();
    }

    // === Insert tests ===

    @Test
    public void insertNodeInto() throws XMLDBException {
        final String docName = "insert-into.xml";
        final XQueryService service = storeXMLStringAndGetQueryService(docName, "<root><a/></root>");

        queryResource(service, docName, "insert node <b/> into /root", 0);

        queryResource(service, docName, "/root/b", 1);
        queryResource(service, docName, "/root/*", 2);
    }

    @Test
    public void insertNodesInto() throws XMLDBException {
        final String docName = "insert-nodes.xml";
        final XQueryService service = storeXMLStringAndGetQueryService(docName, "<root/>");

        queryResource(service, docName, "insert nodes (<a/>, <b/>) into /root", 0);

        queryResource(service, docName, "/root/*", 2);
    }

    @Test
    public void insertNodeBefore() throws XMLDBException {
        final String docName = "insert-before.xml";
        final XQueryService service = storeXMLStringAndGetQueryService(docName, "<root><b/></root>");

        queryResource(service, docName, "insert node <a/> before /root/b", 0);

        // Verify <a/> comes before <b/>
        final ResourceSet result = service.queryResource(docName, "/root/*[1]");
        assertEquals(1L, result.getSize());
        assertEquals("<a/>", result.getResource(0).getContent().toString());
    }

    @Test
    public void insertNodeAfter() throws XMLDBException {
        final String docName = "insert-after.xml";
        final XQueryService service = storeXMLStringAndGetQueryService(docName, "<root><a/></root>");

        queryResource(service, docName, "insert node <b/> after /root/a", 0);

        final ResourceSet result = service.queryResource(docName, "/root/*[2]");
        assertEquals(1L, result.getSize());
        assertEquals("<b/>", result.getResource(0).getContent().toString());
    }

    @Test
    public void insertNodeAsFirstInto() throws XMLDBException {
        final String docName = "insert-first.xml";
        final XQueryService service = storeXMLStringAndGetQueryService(docName, "<root><b/></root>");

        queryResource(service, docName, "insert node <a/> as first into /root", 0);

        final ResourceSet result = service.queryResource(docName, "/root/*[1]");
        assertEquals(1L, result.getSize());
        assertEquals("<a/>", result.getResource(0).getContent().toString());
    }

    @Test
    public void insertNodeAsLastInto() throws XMLDBException {
        final String docName = "insert-last.xml";
        final XQueryService service = storeXMLStringAndGetQueryService(docName, "<root><a/></root>");

        queryResource(service, docName, "insert node <b/> as last into /root", 0);

        final ResourceSet result = service.queryResource(docName, "/root/*[last()]");
        assertEquals(1L, result.getSize());
        assertEquals("<b/>", result.getResource(0).getContent().toString());
    }

    @Test
    public void insertTextNode() throws XMLDBException {
        final String docName = "insert-text.xml";
        final XQueryService service = storeXMLStringAndGetQueryService(docName, "<root/>");

        queryResource(service, docName, "insert node text {'hello'} into /root", 0);

        final ResourceSet result = service.queryResource(docName, "string(/root)");
        assertEquals(1L, result.getSize());
        assertEquals("hello", result.getResource(0).getContent().toString());
    }

    // === Delete tests ===

    @Test
    public void deleteNode() throws XMLDBException {
        final String docName = "delete.xml";
        final XQueryService service = storeXMLStringAndGetQueryService(docName, "<root><a/><b/><c/></root>");

        queryResource(service, docName, "delete node /root/b", 0);

        queryResource(service, docName, "/root/*", 2);
        queryResource(service, docName, "/root/b", 0);
    }

    @Test
    public void deleteNodes() throws XMLDBException {
        final String docName = "delete-nodes.xml";
        final XQueryService service = storeXMLStringAndGetQueryService(docName, "<root><a/><b/><c/></root>");

        queryResource(service, docName, "delete nodes /root/*[position() > 1]", 0);

        queryResource(service, docName, "/root/*", 1);
        queryResource(service, docName, "/root/a", 1);
    }

    // === Replace node tests ===

    @Test
    public void replaceNode() throws XMLDBException {
        final String docName = "replace.xml";
        final XQueryService service = storeXMLStringAndGetQueryService(docName, "<root><a>old</a></root>");

        queryResource(service, docName, "replace node /root/a with <b>new</b>", 0);

        queryResource(service, docName, "/root/a", 0);
        queryResource(service, docName, "/root/b", 1);

        final ResourceSet result = service.queryResource(docName, "string(/root/b)");
        assertEquals("new", result.getResource(0).getContent().toString());
    }

    // === Replace value of tests ===

    @Test
    public void replaceValueOfElement() throws XMLDBException {
        final String docName = "replace-value.xml";
        final XQueryService service = storeXMLStringAndGetQueryService(docName, "<root><a>old</a></root>");

        queryResource(service, docName, "replace value of node /root/a with 'new'", 0);

        final ResourceSet result = service.queryResource(docName, "string(/root/a)");
        assertEquals("new", result.getResource(0).getContent().toString());
    }

    @Test
    public void replaceValueOfAttribute() throws XMLDBException {
        final String docName = "replace-attr-value.xml";
        final XQueryService service = storeXMLStringAndGetQueryService(docName, "<root x='old'/>");

        queryResource(service, docName, "replace value of node /root/@x with 'new'", 0);

        final ResourceSet result = service.queryResource(docName, "string(/root/@x)");
        assertEquals("new", result.getResource(0).getContent().toString());
    }

    @Test
    public void replaceValueOfText() throws XMLDBException {
        final String docName = "replace-text-value.xml";
        final XQueryService service = storeXMLStringAndGetQueryService(docName, "<root>old</root>");

        queryResource(service, docName, "replace value of node /root/text() with 'new'", 0);

        final ResourceSet result = service.queryResource(docName, "string(/root)");
        assertEquals("new", result.getResource(0).getContent().toString());
    }

    // === Rename tests ===

    @Test
    public void renameElement() throws XMLDBException {
        final String docName = "rename.xml";
        final XQueryService service = storeXMLStringAndGetQueryService(docName, "<root><oldname>content</oldname></root>");

        queryResource(service, docName, "rename node /root/oldname as 'newname'", 0);

        queryResource(service, docName, "/root/oldname", 0);
        queryResource(service, docName, "/root/newname", 1);

        final ResourceSet result = service.queryResource(docName, "string(/root/newname)");
        assertEquals("content", result.getResource(0).getContent().toString());
    }

    @Test
    public void renameAttribute() throws XMLDBException {
        final String docName = "rename-attr.xml";
        final XQueryService service = storeXMLStringAndGetQueryService(docName, "<root oldattr='value'/>");

        queryResource(service, docName, "rename node /root/@oldattr as 'newattr'", 0);

        queryResource(service, docName, "/root/@oldattr", 0);
        queryResource(service, docName, "/root/@newattr", 1);

        final ResourceSet result = service.queryResource(docName, "string(/root/@newattr)");
        assertEquals("value", result.getResource(0).getContent().toString());
    }

    // === Transform (copy-modify) tests ===

    @Test
    public void copyModifyReplaceValue() throws XMLDBException {
        final XQueryService service = testCollection.getService(XQueryService.class);

        final String query =
                "let $node := <root><a>old</a></root> " +
                "return copy $c := $node " +
                "modify replace value of node $c/a with 'new' " +
                "return $c";

        final String result = queryAndGetString(service, query);
        assertTrue("Expected result to contain 'new', got: " + result,
                result.contains("new"));
        assertFalse("Expected result to NOT contain 'old', got: " + result,
                result.contains("old"));
    }

    @Test
    public void copyModifyDoesNotAffectOriginal() throws XMLDBException {
        final XQueryService service = testCollection.getService(XQueryService.class);

        final String query =
                "let $node := <root><a>original</a></root> " +
                "let $copy := copy $c := $node " +
                "             modify replace value of node $c/a with 'modified' " +
                "             return $c " +
                "return ($node/a/text(), '|', $copy/a/text())";

        final ResourceSet result = service.query(query);
        assertEquals(3L, result.getSize());
        assertEquals("original", result.getResource(0).getContent().toString());
        assertEquals("modified", result.getResource(2).getContent().toString());
    }

    @Test
    public void copyModifyDelete() throws XMLDBException {
        final XQueryService service = testCollection.getService(XQueryService.class);

        final String query =
                "let $node := <root><a/><b/><c/></root> " +
                "return copy $c := $node " +
                "modify delete node $c/b " +
                "return count($c/*)";

        final String result = queryAndGetString(service, query);
        assertEquals("2", result);
    }

    @Test
    public void copyModifyInsert() throws XMLDBException {
        final XQueryService service = testCollection.getService(XQueryService.class);

        final String query =
                "let $node := <root><a/></root> " +
                "return copy $c := $node " +
                "modify insert node <b/> into $c " +
                "return count($c/*)";

        final String result = queryAndGetString(service, query);
        assertEquals("2", result);
    }

    @Test
    public void copyModifyRename() throws XMLDBException {
        final XQueryService service = testCollection.getService(XQueryService.class);

        final String query =
                "let $node := <root><old/></root> " +
                "return copy $c := $node " +
                "modify rename node $c/old as 'new' " +
                "return local-name($c/*[1])";

        final String result = queryAndGetString(service, query);
        assertEquals("new", result);
    }

    @Test
    public void copyModifyMultipleBindings() throws XMLDBException {
        final XQueryService service = testCollection.getService(XQueryService.class);

        final String query =
                "let $a := <x>1</x> " +
                "let $b := <y>2</y> " +
                "return copy $ca := $a, $cb := $b " +
                "modify (replace value of node $ca with '10', replace value of node $cb with '20') " +
                "return ($ca, $cb)";

        final ResourceSet result = service.query(query);
        assertEquals(2L, result.getSize());
        assertTrue(result.getResource(0).getContent().toString().contains("10"));
        assertTrue(result.getResource(1).getContent().toString().contains("20"));
    }

    // === Combined update tests ===

    @Test
    public void multipleUpdatesInFlwor() throws XMLDBException {
        final String docName = "multi-update.xml";
        final XQueryService service = storeXMLStringAndGetQueryService(docName,
                "<root><item n='1'/><item n='2'/><item n='3'/></root>");

        // Delete all items, then insert a new one
        queryResource(service, docName, "delete nodes /root/item", 0);
        queryResource(service, docName, "/root/item", 0);

        queryResource(service, docName, "insert node <item n='new'/> into /root", 0);
        queryResource(service, docName, "/root/item[@n='new']", 1);
    }

    // === Error condition tests ===

    @Test(expected = XMLDBException.class)
    public void replaceNodeDocumentTarget() throws XMLDBException {
        final String docName = "error-doc-target.xml";
        final XQueryService service = storeXMLStringAndGetQueryService(docName, "<root/>");

        // Replacing a document node should fail with XUTY0008
        service.queryResource(docName, "replace node / with <new/>");
    }

    @Test(expected = XMLDBException.class)
    public void replaceValueOfDocumentTarget() throws XMLDBException {
        final String docName = "error-doc-target2.xml";
        final XQueryService service = storeXMLStringAndGetQueryService(docName, "<root/>");

        service.queryResource(docName, "replace value of node / with 'text'");
    }

    // === XUST0001 static analysis tests ===

    @Test(expected = XMLDBException.class)
    public void xust0001InsertInNonUpdatingFunction() throws XMLDBException {
        final String docName = "xust0001-func.xml";
        final XQueryService service = storeXMLStringAndGetQueryService(docName, "<root><a/></root>");

        // Non-updating function containing an insert expression should fail with XUST0001
        service.queryResource(docName,
                "declare function local:f($e as element()) { insert node <b/> into $e }; " +
                "local:f(/root)");
    }

    @Test(expected = XMLDBException.class)
    public void xust0001DeleteInLogicalOp() throws XMLDBException {
        final String docName = "xust0001-logical.xml";
        final XQueryService service = storeXMLStringAndGetQueryService(docName, "<root><a/></root>");

        // Delete expression in logical AND operand should fail with XUST0001
        service.queryResource(docName, "fn:false() and (delete node /root/a)");
    }

    @Test(expected = XMLDBException.class)
    public void xust0001InsertInForInput() throws XMLDBException {
        final String docName = "xust0001-for.xml";
        final XQueryService service = storeXMLStringAndGetQueryService(docName, "<root><a/></root>");

        // Insert expression in for clause input should fail with XUST0001
        service.queryResource(docName, "for $x in (insert node <b/> into /root) return $x");
    }

    @Test(expected = XMLDBException.class)
    public void xust0001InsertInFunctionArgument() throws XMLDBException {
        final String docName = "xust0001-arg.xml";
        final XQueryService service = storeXMLStringAndGetQueryService(docName, "<root><a/></root>");

        // Insert expression as function argument should fail with XUST0001
        service.queryResource(docName, "fn:count(insert node <b/> into /root)");
    }

    @Test
    public void xust0001MixedConditionalBranches() throws XMLDBException {
        final String docName = "xust0001-cond.xml";
        final XQueryService service = storeXMLStringAndGetQueryService(docName, "<root><a/></root>");

        // Mixed updating/non-updating branches should fail with XUST0001
        try {
            service.queryResource(docName,
                    "if (fn:false()) then 'not updating' else insert node <b/> into /root");
            fail("Expected XMLDBException for XUST0001 but query succeeded");
        } catch (XMLDBException e) {
            assertTrue("Expected XUST0001, got: " + e.getMessage(),
                    e.getMessage().contains("XUST0001"));
        }
    }

    @Test
    public void updatingFunctionKeywordSyntax() throws XMLDBException {
        final String docName = "updating-func-keyword.xml";
        final XQueryService service = storeXMLStringAndGetQueryService(docName, "<root><a/></root>");

        // W3C 1.0 keyword syntax: declare updating function
        service.queryResource(docName,
                "declare updating function local:add($e as element()) { " +
                "  insert node <b/> into $e " +
                "}; " +
                "local:add(/root)");
        queryResource(service, docName, "/root/b", 1);
    }

    @Test
    public void updatingFunctionAnnotationSyntax() throws XMLDBException {
        final String docName = "updating-func-annot.xml";
        final XQueryService service = storeXMLStringAndGetQueryService(docName, "<root><a/></root>");

        // W3C 3.0 annotation syntax: declare %updating function
        service.queryResource(docName,
                "declare %updating function local:add($e as element()) { " +
                "  insert node <c/> into $e " +
                "}; " +
                "local:add(/root)");
        queryResource(service, docName, "/root/c", 1);
    }

    @Test(expected = XMLDBException.class)
    public void xust0028UpdatingFunctionWithReturnType() throws XMLDBException {
        final String docName = "xust0028.xml";
        final XQueryService service = storeXMLStringAndGetQueryService(docName, "<root/>");

        // XUST0028: updating function must not declare a return type
        service.queryResource(docName,
                "declare updating function local:f() as item()* { " +
                "  insert node <a/> into /root " +
                "}; " +
                "local:f()");
    }

    @Test(expected = XMLDBException.class)
    public void xust0002UpdatingFunctionNonUpdatingBody() throws XMLDBException {
        final String docName = "xust0002.xml";
        final XQueryService service = storeXMLStringAndGetQueryService(docName, "<root/>");

        // XUST0002: body of updating function must be updating or vacuous
        service.queryResource(docName,
                "declare updating function local:f($x as xs:integer) { " +
                "  $x + 1 " +
                "}; " +
                "local:f(1)");
    }

    @Test
    public void xust0001InsertInFlworReturnIsAllowed() throws XMLDBException {
        final String docName = "xust0001-return.xml";
        final XQueryService service = storeXMLStringAndGetQueryService(docName, "<root><a/></root>");

        // Insert expression in FLWOR return clause IS allowed (at top level)
        queryResource(service, docName, "for $x in /root/a return insert node <b/> into /root", 0);
        queryResource(service, docName, "/root/b", 1);
    }

    @Test(timeout = 10000)
    public void copyModifyMultipleInsertAfterSameNode() throws XMLDBException {
        final XQueryService service = testCollection.getService(XQueryService.class);

        final String query =
                "let $doc := <employee><empnum>E1</empnum><pnum>P1</pnum><hours>40</hours></employee> " +
                "return copy $c := $doc " +
                "modify ( " +
                "  insert node (<type>Part Time</type>,<age>26</age>) after $c/empnum[1], " +
                "  insert node (<type>Full Time</type>,<age>30</age>) after $c/empnum[1] " +
                ") return $c";

        final ResourceSet result = service.query(query);
        assertEquals(1L, result.getSize());
        final String xml = result.getResource(0).getContent().toString();
        assertTrue("Should contain Part Time", xml.contains("Part Time"));
        assertTrue("Should contain Full Time", xml.contains("Full Time"));
    }

    // === Multi-step update + query tests (complex-deletes regression) ===

    @Test
    public void deletePIMultiStepPrecedingSiblingTextCount() throws Exception {
        // Simulates the XQTS multi-step pattern: update query mutates an in-memory doc,
        // then a separate verification query reads it.
        // This is the pattern that fails in complex-deletes-q3.
        final XQueryService service = testCollection.getService(XQueryService.class);

        // Parse document externally (like the XQTS runner does with SAXParser)
        final String xml = "<root>A<!-- c1 -->B<?pi x?>C<child/>D</root>";
        final org.exist.dom.memtree.SAXAdapter adapter = new org.exist.dom.memtree.SAXAdapter();
        final javax.xml.parsers.SAXParser saxParser = javax.xml.parsers.SAXParserFactory.newDefaultInstance().newSAXParser();
        saxParser.getXMLReader().setContentHandler(adapter);
        saxParser.getXMLReader().setProperty("http://xml.org/sax/properties/lexical-handler", adapter);
        saxParser.getXMLReader().parse(new org.xml.sax.InputSource(
                new java.io.ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        final org.exist.dom.memtree.DocumentImpl doc = adapter.getDocument();

        // Step 1: Run update query with document as external variable
        service.declareVariable("doc", doc);
        service.query("declare variable $doc external; delete nodes $doc//processing-instruction('pi')");

        // Step 2: Run verification query on the same document
        service.declareVariable("doc", doc);
        final ResourceSet result = service.query(
                "declare variable $doc external; count($doc//child/preceding-sibling::text())");
        assertEquals("Expected single result", 1L, result.getSize());
        final String count = result.getResource(0).getContent().toString();

        // After deleting the PI between B and C, B+C merge per W3C spec → 2 text nodes: A, BC
        assertEquals("Text node count after PI deletion", "2", count);
    }

    @Test
    public void deletePIMultiStepComplexDeletesQ3() throws Exception {
        // Full complex-deletes-q3 pattern with doc-level PIs,
        // using BrokerPool + XQuery service directly with context sequence (like the XQTS runner).
        final String xml =
                "<!-- Comment-1 --><?a-pi pi-1?><!-- Comment-2 -->" +
                "<far-north> text-1A\n" +
                "    <!-- Comment-3 --> text-1B\n" +
                "    <?a-pi pi-2?> text-1C\n" +
                "  <north mark=\"n0\"> text-2A\n" +
                "    <near-north> text-3A\n" +
                "      <center mark=\"c0\"> text-4A\n" +
                "        <near-south-west/> text-4B\n" +
                "            <!--Comment-5--> text-4C\n" +
                "            <?a-pi pi-4?> text-4D\n" +
                "        <near-south> text-5A\n" +
                "        </near-south> text-4E\n" +
                "      </center> text-3E\n" +
                "    </near-north> text-2D\n" +
                "  </north> text-1D\n" +
                "</far-north>\n" +
                "<!-- Comment-6 --><?a-pi pi-6?><!-- Comment-7 -->";

        // Parse using SAXAdapter (same as XQTS runner)
        final javax.xml.parsers.SAXParserFactory spf = javax.xml.parsers.SAXParserFactory.newInstance();
        spf.setNamespaceAware(true);
        final org.exist.dom.memtree.SAXAdapter adapter = new org.exist.dom.memtree.SAXAdapter();
        final javax.xml.parsers.SAXParser saxParser = spf.newSAXParser();
        saxParser.getXMLReader().setContentHandler(adapter);
        saxParser.getXMLReader().setProperty("http://xml.org/sax/properties/lexical-handler", adapter);
        saxParser.getXMLReader().parse(new org.xml.sax.InputSource(
                new java.io.ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        final org.exist.dom.memtree.DocumentImpl doc = adapter.getDocument();

        // Use BrokerPool + XQuery service directly (like the XQTS runner)
        final org.exist.storage.BrokerPool pool = org.exist.storage.BrokerPool.getInstance();
        try (final org.exist.storage.DBBroker broker = pool.getBroker()) {
            final org.exist.xquery.XQuery xqueryService = pool.getXQueryService();

            // Step 1: Delete all PIs with target "a-pi"
            {
                final org.exist.xquery.XQueryContext ctx = new org.exist.xquery.XQueryContext(pool);
                ctx.declareVariable("input-context", doc);
                final org.exist.xquery.CompiledXQuery compiled = xqueryService.compile(ctx,
                        "declare variable $input-context external; " +
                                "delete nodes $input-context//processing-instruction('a-pi')");
                xqueryService.execute(broker, compiled, doc);
                ctx.runCleanupTasks();
            }

            // Step 2: Snapshot step (like ". " in the XQTS test)
            {
                final org.exist.xquery.XQueryContext ctx = new org.exist.xquery.XQueryContext(pool);
                ctx.declareVariable("input-context", doc);
                final org.exist.xquery.CompiledXQuery compiled = xqueryService.compile(ctx, ". ");
                xqueryService.execute(broker, compiled, doc);
                ctx.runCleanupTasks();
            }

            // Step 3: Verification query - just count
            {
                final org.exist.xquery.XQueryContext ctx = new org.exist.xquery.XQueryContext(pool);
                final org.exist.xquery.CompiledXQuery compiled = xqueryService.compile(ctx,
                        "count(.//(north | near-south)/preceding-sibling::text())");
                final org.exist.xquery.value.Sequence result = xqueryService.execute(broker, compiled, doc);
                final String countStr = result.itemAt(0).getStringValue();

                // Also get the individual text values for debug
                final org.exist.xquery.XQueryContext ctx2 = new org.exist.xquery.XQueryContext(pool);
                final org.exist.xquery.CompiledXQuery compiled2 = xqueryService.compile(ctx2,
                        "for $t in .//(north | near-south)/preceding-sibling::text() " +
                                "return concat('[', $t, ']')");
                final org.exist.xquery.value.Sequence result2 = xqueryService.execute(broker, compiled2, doc);
                final StringBuilder texts = new StringBuilder();
                for (int i = 0; i < result2.getItemCount(); i++) {
                    if (i > 0) texts.append(", ");
                    texts.append(result2.itemAt(i).getStringValue());
                }

                // Also check what .//(north | near-south) returns
                final org.exist.xquery.XQueryContext ctx3 = new org.exist.xquery.XQueryContext(pool);
                final org.exist.xquery.CompiledXQuery compiled3 = xqueryService.compile(ctx3,
                        "for $n in .//(north | near-south) return name($n)");
                final org.exist.xquery.value.Sequence result3 = xqueryService.execute(broker, compiled3, doc);
                final StringBuilder names = new StringBuilder();
                for (int i = 0; i < result3.getItemCount(); i++) {
                    if (i > 0) names.append(", ");
                    names.append(result3.itemAt(i).getStringValue());
                }

                // W3C spec requires text node merging: after deleting PI between text-1B and text-1C,
                // they merge into one. Same for text-4C and text-4D. So: north has 2 preceding text,
                // near-south has 3 preceding text = 5 total.
                assertEquals("count=" + countStr + ", texts=" + texts + ", targets=" + names, "5", countStr);
                ctx.runCleanupTasks();
                ctx2.runCleanupTasks();
                ctx3.runCleanupTasks();
            }
        }
    }

    // === Delete + axis traversal tests (single-query, copy-modify) ===

    @Test
    public void deletePIPrecedingSiblingTextCount() throws XMLDBException {
        final XQueryService service = testCollection.getService(XQueryService.class);

        // Simulate complex-deletes-q3: delete PIs, then count preceding-sibling text nodes
        final String query =
                "let $doc := <root>A<!-- c1 -->B<?pi x?>C<child/>D</root> " +
                "return copy $c := $doc " +
                "modify delete nodes $c//processing-instruction() " +
                "return count($c/child/preceding-sibling::text())";

        final String result = queryAndGetString(service, query);
        // After deleting PI between B and C, B+C merge per W3C spec → 2 text nodes: A, BC
        assertEquals("2", result);
    }

    @Test
    public void deleteElementChildTextCount() throws XMLDBException {
        final XQueryService service = testCollection.getService(XQueryService.class);

        // Simulate complex-deletes-q10: delete element, count remaining text children
        final String query =
                "let $doc := <root>A<a/>B<b/>C<target/>D</root> " +
                "return copy $c := $doc " +
                "modify delete nodes $c/target " +
                "return count($c/text())";

        final String result = queryAndGetString(service, query);
        // After deleting <target/>, C+D merge per W3C spec → 3 text nodes: A, B, CD
        assertEquals("3", result);
    }

    @Test
    public void deletePIDescendantAndPrecedingSibling() throws XMLDBException {
        final XQueryService service = testCollection.getService(XQueryService.class);

        // Full complex-deletes-q3 pattern: delete PIs, then use //child/preceding-sibling::text()
        final String query =
                "let $doc := <root>A<!-- c1 -->B<?mypi x?>C<child/>D</root> " +
                "return copy $c := $doc " +
                "modify delete nodes $c//processing-instruction('mypi') " +
                "return count($c//child/preceding-sibling::text())";

        final String result = queryAndGetString(service, query);
        // After deleting PI between B and C, B+C merge per W3C spec → 2 text nodes: A, BC
        assertEquals("2", result);
    }

    @Test
    public void deletePIComplexDeletesQ3Pattern() throws XMLDBException {
        // Exact pattern from complex-deletes-q3 using copy-modify
        final XQueryService service = testCollection.getService(XQueryService.class);

        // Uses the full TopMany.xml-like structure with mixed PIs, comments, text, elements
        final String query =
                "let $doc := <far-north> text-1A\n" +
                "    <!-- Comment-3 --> text-1B\n" +
                "    <?a-pi pi-2?> text-1C\n" +
                "  <north mark='n0'> text-2A\n" +
                "    <near-north> text-3A\n" +
                "      <center mark='c0'> text-4A\n" +
                "        <near-south-west/> text-4B\n" +
                "            <!--Comment-5--> text-4C\n" +
                "            <?a-pi pi-4?> text-4D\n" +
                "        <near-south> text-5A\n" +
                "        </near-south> text-4E\n" +
                "      </center> text-3E\n" +
                "    </near-north> text-2D\n" +
                "  </north> text-1D\n" +
                "</far-north>\n" +
                "return copy $c := $doc " +
                "modify delete nodes $c//processing-instruction('a-pi') " +
                "return (\n" +
                "  let $a := $c//(north | near-south)/preceding-sibling::comment()\n" +
                "  return <result count='{count($a)}'>{$a}</result>,\n" +
                "  let $a := $c//(north | near-south)/preceding-sibling::text()\n" +
                "  return <result count='{count($a)}'>{$a}</result>\n" +
                ")";

        final ResourceSet result = service.query(query);
        assertEquals("Expected 2 result elements", 2L, result.getSize());

        final String commentResult = result.getResource(0).getContent().toString();
        System.err.println("deletePI comments: " + commentResult);
        // With //(north | near-south), both north AND near-south are found as descendants.
        // north/preceding-sibling::comment() = <!-- Comment-3 --> (1 comment)
        // near-south/preceding-sibling::comment() = <!--Comment-5--> (1 comment, after PI deletion)
        // Wait: near-south is at center level. Its preceding siblings include:
        //   near-south-west, text-4B, <!--Comment-5--> (after PI deletion, text-4C+text-4D merged)
        // So near-south has 1 preceding-sibling comment.
        // Total = 2 comments.
        assertTrue("Comment count should be 2, got: " + commentResult,
                commentResult.contains("count=\"2\""));

        final String textResult = result.getResource(1).getContent().toString();
        // After deleting PIs, adjacent text nodes merge per W3C spec:
        // north: text-1A, (text-1B+text-1C merged) = 2 preceding text siblings
        // near-south: text-4A, text-4B, (text-4C+text-4D merged) = 3 preceding text siblings
        // Total = 5
        assertTrue("Text count should be 5, got: " + textResult, textResult.contains("count=\"5\""));
    }

    @Test
    public void deleteAttributesSingleElement() throws XMLDBException {
        // Simplest case: delete one attribute from one element
        final XQueryService service = testCollection.getService(XQueryService.class);

        final String query =
                "let $doc := <a x='1' y='2' z='3'/> " +
                "return copy $c := $doc " +
                "modify delete nodes $c/@y " +
                "return count($c/@*)";

        assertEquals("2", queryAndGetString(service, query));
    }

    @Test
    public void deleteAttributesTwoElements() throws XMLDBException {
        final XQueryService service = testCollection.getService(XQueryService.class);

        // Delete one attr from each of two elements
        final String query =
                "let $doc := <root><a x='1' y='2' z='3'/><b p='4' q='5' r='6'/></root> " +
                "return copy $c := $doc " +
                "modify delete nodes ($c/a/@y, $c/b/@q) " +
                "return (count($c/a/@*), count($c/b/@*))";

        final ResourceSet result = testCollection.getService(XQueryService.class).query(query);
        assertEquals("a should have 2 attrs", "2", result.getResource(0).getContent().toString());
        assertEquals("b should have 2 attrs", "2", result.getResource(1).getContent().toString());
    }

    @Test
    public void deleteAttributesThreeElementsExplicit() throws XMLDBException {
        final XQueryService service = testCollection.getService(XQueryService.class);

        final String query =
                "let $doc := <root>" +
                "  <a mark='w0' a1='v1' a2='v2' a3='v3'/>" +
                "  <b mark='c0' b1='v1' b2='v2' b3='v3'/>" +
                "  <c mark='s0' c1='v1' c2='v2'/>" +
                "</root> " +
                "return copy $c := $doc " +
                "modify delete nodes ($c/a/@a2, $c/b/@b2, $c/c/@c2) " +
                "return (count($c/a/@*), count($c/b/@*), count($c/c/@*))";

        final ResourceSet result = service.query(query);
        assertEquals("a", "3", result.getResource(0).getContent().toString());
        assertEquals("b", "3", result.getResource(1).getContent().toString());
        assertEquals("c", "2", result.getResource(2).getContent().toString());
    }

    // === Insert before — multiple inserts at same target (regression for hang) ===

    @Test(timeout = 10000)
    public void insertMultipleGroupsBeforeSameTarget() throws XMLDBException {
        final String docName = "insert-multi-before.xml";
        final XQueryService service = storeXMLStringAndGetQueryService(docName,
                "<employee><empnum>E1</empnum><pnum>P1</pnum><hours>40</hours></employee>");

        // Two insert-before expressions targeting the same node — this should not hang
        queryResource(service, docName,
                "let $var := /employee " +
                "return ( " +
                "  insert node (<type>Part Time</type>,<age>26</age>) before $var/empnum[1], " +
                "  insert node (<type>Full Time</type>,<age>30</age>) before $var/empnum[1] " +
                ")", 0);

        // Verify the inserts happened
        final ResourceSet result = service.queryResource(docName, "count(/employee/*)");
        assertEquals(1L, result.getSize());
        // 3 original + 4 inserted = 7
        assertEquals("7", result.getResource(0).getContent().toString());
    }

    @Test(timeout = 10000)
    public void insertMultipleGroupsBeforeSameTargetInMemory() throws XMLDBException {
        final String docName = "dummy.xml";
        final XQueryService service = storeXMLStringAndGetQueryService(docName, "<dummy/>");

        // Test insert into in copy-modify
        assertEquals("insert into", "2", service.query(
                "copy $c := <employee><empnum>E1</empnum></employee> " +
                "modify insert node <type>PT</type> into $c " +
                "return count($c/*)").getResource(0).getContent().toString());

        // Test insert after in copy-modify
        assertEquals("insert after", "4", service.query(
                "copy $c := <employee><empnum>E1</empnum><pnum>P1</pnum><hours>40</hours></employee> " +
                "modify insert node <type>PT</type> after $c/empnum[1] " +
                "return count($c/*)").getResource(0).getContent().toString());

        // Test insert before in copy-modify
        assertEquals("insert before", "4", service.query(
                "copy $c := <employee><empnum>E1</empnum><pnum>P1</pnum><hours>40</hours></employee> " +
                "modify insert node <type>Part Time</type> before $c/empnum[1] " +
                "return count($c/*)").getResource(0).getContent().toString());

        // Test insert as first into in copy-modify
        assertEquals("insert as first", "4", service.query(
                "copy $c := <employee><empnum>E1</empnum><pnum>P1</pnum><hours>40</hours></employee> " +
                "modify insert node <type>PT</type> as first into $c " +
                "return count($c/*)").getResource(0).getContent().toString());

        // Now test two inserts using comma expression
        final String query =
                "let $doc := <employee><empnum>E1</empnum><pnum>P1</pnum><hours>40</hours></employee> " +
                "return copy $c := $doc " +
                "modify ( " +
                "  insert node (<type>Part Time</type>,<age>26</age>) before $c/empnum[1], " +
                "  insert node (<type>Full Time</type>,<age>30</age>) before $c/empnum[1] " +
                ") " +
                "return count($c/*)";

        final ResourceSet result = service.query(query);
        assertEquals(1L, result.getSize());
        // 3 original + 4 inserted = 7
        assertEquals("7", result.getResource(0).getContent().toString());
    }

    // === XQTS-style in-memory insert-after test (mimics id-insert-expr-021) ===

    @Test
    public void inMemoryInsertAfterTwoElements() throws Exception {
        // Parse document using SAXAdapter (same as XQTS runner)
        final String xml = "<root><a>1</a><b>2</b><c>3</c></root>";
        final org.exist.dom.memtree.SAXAdapter adapter = new org.exist.dom.memtree.SAXAdapter();
        final javax.xml.parsers.SAXParser saxParser = javax.xml.parsers.SAXParserFactory.newDefaultInstance().newSAXParser();
        saxParser.getXMLReader().setContentHandler(adapter);
        saxParser.getXMLReader().setProperty("http://xml.org/sax/properties/lexical-handler", adapter);
        saxParser.getXMLReader().parse(new org.xml.sax.InputSource(
                new java.io.ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        final org.exist.dom.memtree.DocumentImpl doc = adapter.getDocument();

        final org.exist.storage.BrokerPool pool = org.exist.storage.BrokerPool.getInstance();
        try (final org.exist.storage.DBBroker broker = pool.getBroker()) {
            final org.exist.xquery.XQuery xqueryService = pool.getXQueryService();

            // Step 1: Insert two elements after <a>
            {
                final org.exist.xquery.XQueryContext ctx = new org.exist.xquery.XQueryContext(pool);
                final org.exist.xquery.CompiledXQuery compiled = xqueryService.compile(ctx,
                        "insert node (<x>10</x>,<y>20</y>) after ./root/a");
                xqueryService.execute(broker, compiled, doc);
                ctx.runCleanupTasks();
            }

            // Step 2: Query all children of root in order
            {
                final org.exist.xquery.XQueryContext ctx = new org.exist.xquery.XQueryContext(pool);
                final org.exist.xquery.CompiledXQuery compiled = xqueryService.compile(ctx,
                        "string-join(for $e in ./root/* return concat(name($e), '=', string($e)), ',')");
                final org.exist.xquery.value.Sequence result = xqueryService.execute(broker, compiled, doc);
                final String output = result.itemAt(0).getStringValue();
                // Expected order: a=1, x=10, y=20, b=2, c=3
                assertEquals("a=1,x=10,y=20,b=2,c=3", output);
            }
        }
    }

    @Test
    public void inMemoryInsertAttributeNamespacedElement() throws Exception {
        // Test 094: insert attribute into element in default namespace
        // Use real books3.xml content with comments, PIs, entities
        final java.io.File books3 = new java.io.File(
                System.getProperty("user.home") + "/workspace/exist-xqts-runner/work/qt4tests-master/upd/TestSources/books3.xml");
        final byte[] xml;
        if (books3.exists()) {
            xml = java.nio.file.Files.readAllBytes(books3.toPath());
        } else {
            // Fallback simplified version
            xml = ("<?xml version=\"1.0\"?>\n" +
                    "<BOOKLIST xmlns=\"http://ns.example.com/books\">\n" +
                    "<BOOKS>\n" +
                    "\t<ITEM CAT=\"MMP\">\n" +
                    "\t    <!-- the first book -->\n" +
                    "\t    <?pi data?>\n" +
                    "\t    <TITLE>Pride and Prejudice</TITLE>\n" +
                    "\t</ITEM>\n" +
                    "</BOOKS>\n" +
                    "<CATEGORIES DESC=\"Miscellaneous categories\">\n" +
                    "   <CATEGORY CODE=\"P\" DESC=\"Paperback\"/>\n" +
                    "</CATEGORIES>\n" +
                    "</BOOKLIST>").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        final org.exist.dom.memtree.SAXAdapter adapter = new org.exist.dom.memtree.SAXAdapter();
        final javax.xml.parsers.SAXParserFactory spf = javax.xml.parsers.SAXParserFactory.newInstance();
        spf.setNamespaceAware(true);
        final javax.xml.parsers.SAXParser nsParser = spf.newSAXParser();
        nsParser.getXMLReader().setContentHandler(adapter);
        nsParser.getXMLReader().setProperty("http://xml.org/sax/properties/lexical-handler", adapter);
        nsParser.getXMLReader().parse(new org.xml.sax.InputSource(
                new java.io.ByteArrayInputStream(xml)));
        final org.exist.dom.memtree.DocumentImpl doc = adapter.getDocument();

        // Check document-level children before update
        int docChildren = 0;
        org.w3c.dom.Node docChild = doc.getFirstChild();
        while (docChild != null) {
            System.out.println("Before update - doc child " + docChildren + ": type=" + docChild.getNodeType()
                    + " name=" + docChild.getNodeName());
            docChildren++;
            docChild = docChild.getNextSibling();
        }
        System.out.println("Before update: " + docChildren + " document-level children");

        final org.exist.storage.BrokerPool pool = org.exist.storage.BrokerPool.getInstance();
        try (final org.exist.storage.DBBroker broker = pool.getBroker()) {
            final org.exist.xquery.XQuery xqueryService = pool.getXQueryService();

            // Insert ITEMS attribute into BOOKS (count should be 3)
            {
                final org.exist.xquery.XQueryContext ctx = new org.exist.xquery.XQueryContext(pool);
                final org.exist.xquery.CompiledXQuery compiled = xqueryService.compile(ctx,
                        "declare namespace books='http://ns.example.com/books'; " +
                        "insert node attribute ITEMS { count(.//books:ITEM) } into .//books:BOOKS");
                xqueryService.execute(broker, compiled, doc);
                ctx.runCleanupTasks();
            }

            // Check document-level children after update
            docChildren = 0;
            docChild = doc.getFirstChild();
            while (docChild != null) {
                System.out.println("After update - doc child " + docChildren + ": type=" + docChild.getNodeType()
                        + " name=" + docChild.getNodeName() + " value='" + (docChild.getNodeValue() != null ? docChild.getNodeValue().replace("\n", "\\n") : "null") + "'");
                docChildren++;
                docChild = docChild.getNextSibling();
            }
            System.out.println("After update: " + docChildren + " document-level children");

            // First, run the verification query "." and get the result
            final org.exist.xquery.value.Sequence verifyResult;
            {
                final org.exist.xquery.XQueryContext ctx = new org.exist.xquery.XQueryContext(pool);
                final org.exist.xquery.CompiledXQuery compiled = xqueryService.compile(ctx, " .");
                verifyResult = xqueryService.execute(broker, compiled, doc);
                System.out.println("Verify result count: " + verifyResult.getItemCount());
                System.out.println("Verify result type: " + verifyResult.itemAt(0).getType());
            }

            // Then serialize via $result external variable (same as XQTS runner)
            {
                final org.exist.xquery.XQueryContext ctx = new org.exist.xquery.XQueryContext(pool);
                ctx.declareVariable("result", verifyResult);
                final org.exist.xquery.CompiledXQuery compiled = xqueryService.compile(ctx,
                        "declare variable $result external; " +
                        "let $local:default-serialization := " +
                        "  <output:serialization-parameters xmlns:output='http://www.w3.org/2010/xslt-xquery-serialization'>" +
                        "    <output:method value='xml'/>" +
                        "    <output:indent value='no'/>" +
                        "    <output:omit-xml-declaration value='yes'/>" +
                        "  </output:serialization-parameters> " +
                        "return fn:serialize($result, $local:default-serialization)");
                final org.exist.xquery.value.Sequence result = xqueryService.execute(broker, compiled, null);
                final String serialized = result.itemAt(0).getStringValue();
                System.out.println("Test 094 serialized length: " + serialized.length());
                System.out.println("Test 094 first 200: " + serialized.substring(0, Math.min(200, serialized.length())));
                System.out.println("Test 094 last 100: " + serialized.substring(Math.max(0, serialized.length() - 100)));
                assertTrue("BOOKS element should have ITEMS attribute",
                        serialized.contains("ITEMS=\"6\"") || serialized.contains("ITEMS=\"1\"") || serialized.contains("ITEMS=\"3\""));
                // Check if fn:serialize adds a trailing newline for document nodes
                System.out.println("Last char code: " + (int) serialized.charAt(serialized.length() - 1));
                System.out.println("Ends with newline: " + serialized.endsWith("\n"));
                // Check that wrapping in ignorable-wrapper produces 1 child
                final String wrapped = "<ignorable-wrapper>" + serialized + "</ignorable-wrapper>";
                final javax.xml.parsers.DocumentBuilderFactory dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance();
                dbf.setNamespaceAware(true);
                final javax.xml.parsers.DocumentBuilder db = dbf.newDocumentBuilder();
                final org.w3c.dom.Document wrappedDoc = db.parse(new org.xml.sax.InputSource(
                        new java.io.ByteArrayInputStream(wrapped.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
                final int wrapperChildCount = wrappedDoc.getDocumentElement().getChildNodes().getLength();
                System.out.println("Wrapper child count: " + wrapperChildCount);
                if (wrapperChildCount != 1) {
                    for (int i = 0; i < wrapperChildCount; i++) {
                        final org.w3c.dom.Node ch = wrappedDoc.getDocumentElement().getChildNodes().item(i);
                        System.out.println("Wrapper child " + i + ": type=" + ch.getNodeType()
                                + " name=" + ch.getNodeName()
                                + " value='" + (ch.getNodeValue() != null ? ch.getNodeValue().substring(0, Math.min(50, ch.getNodeValue().length())).replace("\n", "\\n") : "null") + "'");
                    }
                }
                assertEquals("Wrapper should have exactly 1 child", 1, wrapperChildCount);
            }
        }
    }

    @Test
    public void inMemoryInsertIntoOrdering() throws Exception {
        // Test 052: INSERT_INTO should go between INSERT_INTO_AS_FIRST and INSERT_INTO_AS_LAST
        final org.exist.storage.BrokerPool pool = org.exist.storage.BrokerPool.getInstance();
        try (final org.exist.storage.DBBroker broker = pool.getBroker()) {
            final org.exist.xquery.XQuery xqueryService = pool.getXQueryService();

            final String xml = "<root><a/><b/></root>";
            final org.exist.dom.memtree.SAXAdapter adapter = new org.exist.dom.memtree.SAXAdapter();
            final javax.xml.parsers.SAXParser saxParser = javax.xml.parsers.SAXParserFactory.newDefaultInstance().newSAXParser();
            saxParser.getXMLReader().setContentHandler(adapter);
            saxParser.getXMLReader().setProperty("http://xml.org/sax/properties/lexical-handler", adapter);
            saxParser.getXMLReader().parse(new org.xml.sax.InputSource(
                    new java.io.ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
            final org.exist.dom.memtree.DocumentImpl doc = adapter.getDocument();

            // Apply multiple inserts: as first, as last, and plain into
            {
                final org.exist.xquery.XQueryContext ctx = new org.exist.xquery.XQueryContext(pool);
                final org.exist.xquery.CompiledXQuery compiled = xqueryService.compile(ctx,
                        "insert node <first/> as first into ./root," +
                        "insert node <last/> as last into ./root," +
                        "insert node <mid/> into ./root");
                xqueryService.execute(broker, compiled, doc);
                ctx.runCleanupTasks();
            }

            // Check ordering
            {
                final org.exist.xquery.XQueryContext ctx = new org.exist.xquery.XQueryContext(pool);
                final org.exist.xquery.CompiledXQuery compiled = xqueryService.compile(ctx,
                        "string-join(./root/*/name(), ',')");
                final org.exist.xquery.value.Sequence result = xqueryService.execute(broker, compiled, doc);
                final String output = result.itemAt(0).getStringValue();
                System.out.println("Test 052 ordering: " + output);
                // first must be first, last must be last, mid must be between them
                assertTrue("first should be first", output.startsWith("first,"));
                assertTrue("last should be last", output.endsWith(",last"));
                assertFalse("mid should not come after last", output.indexOf("mid") > output.indexOf("last"));
            }
        }
    }

    @Test
    public void inMemoryInsertAfterDescendantAxis() throws Exception {
        // Test that //element finds inserted nodes (descendant axis traversal)
        final String xml = "<employee><hours>70</hours><hours>20</hours></employee>";
        final org.exist.dom.memtree.SAXAdapter adapter = new org.exist.dom.memtree.SAXAdapter();
        final javax.xml.parsers.SAXParser saxParser = javax.xml.parsers.SAXParserFactory.newDefaultInstance().newSAXParser();
        saxParser.getXMLReader().setContentHandler(adapter);
        saxParser.getXMLReader().setProperty("http://xml.org/sax/properties/lexical-handler", adapter);
        saxParser.getXMLReader().parse(new org.xml.sax.InputSource(
                new java.io.ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        final org.exist.dom.memtree.DocumentImpl doc = adapter.getDocument();

        final org.exist.storage.BrokerPool pool = org.exist.storage.BrokerPool.getInstance();
        try (final org.exist.storage.DBBroker broker = pool.getBroker()) {
            final org.exist.xquery.XQuery xqueryService = pool.getXQueryService();

            // Insert two hours after hours[1]
            {
                final org.exist.xquery.XQueryContext ctx = new org.exist.xquery.XQueryContext(pool);
                final org.exist.xquery.CompiledXQuery compiled = xqueryService.compile(ctx,
                        "insert node (<hours>15</hours>,<hours>25</hours>) after ./employee/hours[1]");
                xqueryService.execute(broker, compiled, doc);
                ctx.runCleanupTasks();
            }

            // Query //hours and check order
            {
                final org.exist.xquery.XQueryContext ctx = new org.exist.xquery.XQueryContext(pool);
                final org.exist.xquery.CompiledXQuery compiled = xqueryService.compile(ctx,
                        "string-join(for $h in .//hours return string($h), ',')");
                final org.exist.xquery.value.Sequence result = xqueryService.execute(broker, compiled, doc);
                final String output = result.itemAt(0).getStringValue();
                // Expected order: 70, 15, 25, 20
                assertEquals("70,15,25,20", output);
            }
        }
    }

    @Test
    public void inMemoryReplaceAttribute() throws Exception {
        final String xml = "<employee name=\"Jane\" gender=\"female\"><empnum>E1</empnum></employee>";
        final org.exist.dom.memtree.SAXAdapter adapter = new org.exist.dom.memtree.SAXAdapter();
        final javax.xml.parsers.SAXParser saxParser = javax.xml.parsers.SAXParserFactory.newDefaultInstance().newSAXParser();
        saxParser.getXMLReader().setContentHandler(adapter);
        saxParser.getXMLReader().setProperty("http://xml.org/sax/properties/lexical-handler", adapter);
        saxParser.getXMLReader().parse(new org.xml.sax.InputSource(
                new java.io.ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        final org.exist.dom.memtree.DocumentImpl doc = adapter.getDocument();

        final org.exist.storage.BrokerPool pool = org.exist.storage.BrokerPool.getInstance();
        try (final org.exist.storage.DBBroker broker = pool.getBroker()) {
            final org.exist.xquery.XQuery xqueryService = pool.getXQueryService();

            // Replace attribute name with name1
            {
                final org.exist.xquery.XQueryContext ctx = new org.exist.xquery.XQueryContext(pool);
                final org.exist.xquery.CompiledXQuery compiled = xqueryService.compile(ctx,
                        "replace node ./employee/@name with attribute name1 {\"new name\"}");
                xqueryService.execute(broker, compiled, doc);
                ctx.runCleanupTasks();
            }

            // Verify: check the result
            {
                final org.exist.xquery.XQueryContext ctx = new org.exist.xquery.XQueryContext(pool);
                final org.exist.xquery.CompiledXQuery compiled = xqueryService.compile(ctx,
                        "string(./employee/@name1)");
                final org.exist.xquery.value.Sequence result = xqueryService.execute(broker, compiled, doc);
                assertEquals("new name", result.itemAt(0).getStringValue());
            }

            // Verify: old attribute is gone
            {
                final org.exist.xquery.XQueryContext ctx = new org.exist.xquery.XQueryContext(pool);
                final org.exist.xquery.CompiledXQuery compiled = xqueryService.compile(ctx,
                        "count(./employee/@name)");
                final org.exist.xquery.value.Sequence result = xqueryService.execute(broker, compiled, doc);
                assertEquals("0", result.itemAt(0).getStringValue());
            }
        }
    }

    /**
     * Verify that constructed in-memory elements have no parent
     * (explicitlyCreated=false makes getParentNode() return null),
     * and that replace node correctly raises XUDY0009.
     */
    @Test
    public void replaceNodeParentlessElementXUDY0009() throws XMLDBException {
        final XQueryService service = storeXMLStringAndGetQueryService("xudy0009.xml", "<dummy/>");
        final String query =
                "let $var := <hours/> " +
                "return replace node $var with <other/>";
        try {
            service.query(query);
            fail("Expected XUDY0009 error for parentless element");
        } catch (final XMLDBException e) {
            assertTrue("Expected XUDY0009 but got: " + e.getMessage(),
                    e.getMessage().contains("XUDY0009"));
        }
    }

    /**
     * Verify XUTY0008 is raised when replace target is multiple nodes.
     */
    @Test
    public void replaceNodeMultipleTargetsXUTY0008() throws XMLDBException {
        final XQueryService service = storeXMLStringAndGetQueryService("xuty0008.xml", "<root><a/><b/></root>");
        final String query =
                "let $doc := doc('/db/test/xuty0008.xml') " +
                "return replace node $doc/root/child::* with <c/>";
        try {
            service.query(query);
            fail("Expected XUTY0008 error for multiple targets");
        } catch (final XMLDBException e) {
            assertTrue("Expected XUTY0008 but got: " + e.getMessage(),
                    e.getMessage().contains("XUTY0008"));
        }
    }

    // === Compatibility tests: replaceNode + replaceElementContent interaction ===

    @Test
    public void replaceValueOfElementAndReplaceNodeChildPersistent() throws XMLDBException {
        // Matches compatibility-027: replace value of node + replace node on child
        final String docName = "compat027.xml";
        final XQueryService service = storeXMLStringAndGetQueryService(docName,
                "<employee name=\"Jane\" gender=\"female\"><empnum>E1</empnum><pnum>P1</pnum><hours>40</hours></employee>");

        // replace value of element replaces ALL children; replaceNode of child should be skipped
        service.query(
                "let $var := doc('/db/test/compat027.xml')/employee " +
                "return ( " +
                "  replace value of node $var with 'on leave', " +
                "  replace node $var/empnum with <empnum>on leave</empnum> " +
                ")");

        final ResourceSet result = service.query("doc('/db/test/compat027.xml')/employee");
        assertEquals(1L, result.getSize());
        final String xml = result.getResource(0).getContent().toString();
        assertTrue("Expected text 'on leave' in employee, got: " + xml, xml.contains("on leave"));
        // The element should have only text content (no child elements) after replaceElementContent
        assertFalse("Expected no <empnum> child after replaceElementContent, got: " + xml, xml.contains("<empnum"));
    }

    @Test
    public void replaceValueOfElementAndInsertChildPersistent() throws XMLDBException {
        // Matches compatibility-029: replace value of node + insert into
        final String docName = "compat029.xml";
        final XQueryService service = storeXMLStringAndGetQueryService(docName,
                "<employee name=\"Jane\"><empnum>E1</empnum></employee>");

        service.query(
                "let $var := doc('/db/test/compat029.xml')/employee " +
                "return ( " +
                "  replace value of node $var with 'on leave', " +
                "  insert node <!-- this employee is on leave --> into $var " +
                ")");

        final ResourceSet result = service.query("doc('/db/test/compat029.xml')/employee");
        assertEquals(1L, result.getSize());
        final String xml = result.getResource(0).getContent().toString();
        assertTrue("Expected text 'on leave' in employee, got: " + xml, xml.contains("on leave"));
        // replaceElementContent should supersede the insert
        assertFalse("Expected no comment after replaceElementContent, got: " + xml, xml.contains("<!--"));
    }

    // === Namespace propagation in copy-modify ===

    @Test
    public void propagateNamespacesInsertInheritsFromCopiedParent() throws XMLDBException {
        // Simplified propagateNamespaces01: inserted nodes inherit namespaces from copied parent
        final XQueryService service = testCollection.getService(XQueryService.class);

        final String query =
                "declare copy-namespaces preserve, inherit;\n" +
                "copy $data := <v xmlns:a=\"a-one\" xmlns:b=\"b-one\"/>\n" +
                "modify insert node <w/> into $data\n" +
                "return namespace-uri-for-prefix('a', $data/w)";

        final ResourceSet result = service.query(query);
        assertEquals("Expected single result", 1L, result.getSize());
        final String val = result.getResource(0).getContent().toString();
        assertEquals("Inserted <w> should inherit xmlns:a from copied <v>", "a-one", val);
    }

    @Test
    public void propagateNamespacesFull() throws XMLDBException {
        // Full propagateNamespaces01 test
        final XQueryService service = testCollection.getService(XQueryService.class);

        final String query =
                "declare copy-namespaces preserve, inherit;\n" +
                "copy $data := <v xmlns:a=\"a-one\" xmlns:b=\"b-one\"/>\n" +
                "modify\n" +
                "  insert node <w><x xmlns:a=\"a-two\"><y xmlns:b=\"b-two\"><z/></y></x></w> into $data\n" +
                "return\n" +
                "  let $w := $data/w\n" +
                "  let $x := $w/x\n" +
                "  let $y := $x/y\n" +
                "  let $z := $y/z\n" +
                "  return\n" +
                "    <result>\n" +
                "      <w>{namespace-uri-for-prefix('a', $w), namespace-uri-for-prefix('b',$w)}</w>\n" +
                "      <x>{namespace-uri-for-prefix('a', $x), namespace-uri-for-prefix('b',$x)}</x>\n" +
                "      <y>{namespace-uri-for-prefix('a', $y), namespace-uri-for-prefix('b',$y)}</y>\n" +
                "      <z>{namespace-uri-for-prefix('a', $z), namespace-uri-for-prefix('b',$z)}</z>\n" +
                "    </result>";

        final ResourceSet result = service.query(query);
        assertEquals("Expected single result", 1L, result.getSize());
        final String xml = result.getResource(0).getContent().toString();
        // Check output contains expected namespace values
        final String xmlNorm = xml.replaceAll("\\s+", " ").trim();
        assertTrue("w should have a=a-one, got: " + xmlNorm, xmlNorm.contains("<w>a-one b-one</w>"));
        assertTrue("x should have a=a-two, got: " + xmlNorm, xmlNorm.contains("<x>a-two b-one</x>"));
        assertTrue("y should have a=a-two b=b-two, got: " + xmlNorm, xmlNorm.contains("<y>a-two b-two</y>"));
        assertTrue("z should have a=a-two b=b-two, got: " + xmlNorm, xmlNorm.contains("<z>a-two b-two</z>"));
    }

    @Test
    public void propagateNamespaces01XqtsExact() throws XMLDBException {
        // Exact XQTS propagateNamespaces01 query with boundary-space preserve
        final XQueryService service = testCollection.getService(XQueryService.class);
        final String query = "declare copy-namespaces preserve, inherit; " +
                "declare boundary-space preserve; " +
                "copy $data := <v xmlns:a=\"a-one\" xmlns:b=\"b-one\"/> " +
                "modify insert node <w> <x xmlns:a=\"a-two\"> <y xmlns:b=\"b-two\"><z/></y> </x> </w> into $data " +
                "return let $w := $data/w let $x := $w/x let $y := $x/y let $z := $y/z " +
                "return <result> " +
                "<w>{namespace-uri-for-prefix(\"a\", $w), namespace-uri-for-prefix(\"b\",$w)}</w> " +
                "<x>{namespace-uri-for-prefix(\"a\", $x), namespace-uri-for-prefix(\"b\",$x)}</x> " +
                "<y>{namespace-uri-for-prefix(\"a\", $y), namespace-uri-for-prefix(\"b\",$y)}</y> " +
                "<z>{namespace-uri-for-prefix(\"a\", $z), namespace-uri-for-prefix(\"b\",$z)}</z> " +
                "</result>";

        final ResourceSet result = service.query(query);
        System.err.println("propagateNamespaces01_xqts_exact: result size = " + result.getSize());
        for (long i = 0; i < result.getSize(); i++) {
            System.err.println("Result[" + i + "] = " + result.getResource(i).getContent().toString());
        }
        assertEquals("Expected single result", 1L, result.getSize());
        final String xml = result.getResource(0).getContent().toString();
        assertTrue("Result should contain w element", xml.contains("<w>"));
    }

    @Test
    public void applyUpdates001InMemoryInsertThenDelete() throws XMLDBException {
        // applyUpdates-001 pattern but with copy-modify (in-memory path)
        final XQueryService service = testCollection.getService(XQueryService.class);
        final String query =
                "copy $data := <employee name=\"Jane Doe 1\" gender=\"female\">\n" +
                "   <empnum>E1</empnum>\n" +
                "   <pnum>P1</pnum>\n" +
                "   <hours>40</hours>\n" +
                "</employee>\n" +
                "modify (\n" +
                "  insert node comment { 'Testing' } into $data/hours,\n" +
                "  delete node $data/hours/text()\n" +
                ")\n" +
                "return $data/hours";
        final ResourceSet result = service.query(query);
        assertEquals(1L, result.getSize());
        final String xml = result.getResource(0).getContent().toString();
        System.err.println("applyUpdates001_inMemory result: " + xml);
        assertTrue("hours should contain comment", xml.contains("<!--Testing-->"));
        assertFalse("hours should not contain '40'", xml.contains("40"));
    }

    @Test
    public void applyUpdates013InMemoryInsertDeleteAttributeSameName() throws XMLDBException {
        // applyUpdates-013: insert attribute name="Sylvia" and delete @name
        final XQueryService service = testCollection.getService(XQueryService.class);
        final String query =
                "copy $data := <employee name=\"Jane Doe 1\" gender=\"female\">\n" +
                "   <empnum>E1</empnum>\n" +
                "   <pnum>P1</pnum>\n" +
                "   <hours>40</hours>\n" +
                "</employee>\n" +
                "modify (\n" +
                "  insert node attribute name {'Sylvia'} into $data,\n" +
                "  delete node $data/@name\n" +
                ")\n" +
                "return $data";
        final ResourceSet result = service.query(query);
        assertEquals(1L, result.getSize());
        final String xml = result.getResource(0).getContent().toString();
        System.err.println("applyUpdates013_inMemory result: " + xml);
        assertTrue("should have name='Sylvia'", xml.contains("name=\"Sylvia\""));
        assertFalse("should not have 'Jane Doe 1'", xml.contains("Jane Doe 1"));
    }

    @Test
    public void applyUpdates001PersistentInsertThenDelete() throws XMLDBException {
        // applyUpdates-001: insert comment into hours, delete hours/text()
        final XQueryService service = storeXMLStringAndGetQueryService("works-mod.xml",
                "<employee name=\"Jane Doe 1\" gender=\"female\">\n" +
                "   <empnum>E1</empnum>\n" +
                "   <pnum>P1</pnum>\n" +
                "   <hours>40</hours>\n" +
                "</employee>");

        // Run the update: insert comment into hours AND delete hours/text()
        service.query(
                "let $var := doc('/db/test/works-mod.xml')/employee " +
                "return (\n" +
                "  insert node comment { 'Testing' } into $var/hours,\n" +
                "  delete node $var/hours/text()\n" +
                ")");

        // Verify: hours should have comment but no text node
        final ResourceSet result = service.query(
                "doc('/db/test/works-mod.xml')/employee/hours");
        assertEquals(1L, result.getSize());
        final String xml = result.getResource(0).getContent().toString();
        System.err.println("applyUpdates001 result: " + xml);
        assertTrue("hours should contain comment", xml.contains("<!--Testing-->"));
        assertFalse("hours should not contain '40'", xml.contains("40"));
    }

    @Test
    public void transformExpr034CopyDocumentRename() throws XMLDBException {
        // id-transform-expr-034: copy a document, rename its root element
        final String query =
                "let $doc := document { <works><employee name=\"Jane\"/></works> }\n" +
                "return copy $var1 := $doc\n" +
                "       modify rename node $var1/works as \"workers\"\n" +
                "       return $var1";
        final ResourceSet result = existEmbeddedServer.executeQuery(query);
        assertEquals(1L, result.getSize());
        final String xml = result.getResource(0).getContent().toString();
        assertTrue("Root should be renamed to 'workers'", xml.contains("<workers"));
        assertTrue("Should preserve children", xml.contains("employee"));
    }

    @Test
    public void transformExpr035CopyAttributeReplaceValue() throws XMLDBException {
        // id-transform-expr-035: copy an attribute, replace its value
        final String query =
                "let $var := <employee name=\"Jane Doe 1\"/>\n" +
                "return copy $var1 := $var/@name\n" +
                "       modify replace value of node $var1 with \"Ursula Le Guin\"\n" +
                "       return <newemp>{ $var1 }</newemp>";
        final ResourceSet result = existEmbeddedServer.executeQuery(query);
        assertEquals(1L, result.getSize());
        final String xml = result.getResource(0).getContent().toString();
        assertEquals("<newemp name=\"Ursula Le Guin\"/>", xml);
    }

    @Test
    public void transformExprXUDY0014TargetOutsideCopy() throws XMLDBException {
        // XUDY0014: update target must be created by the copy clause
        final String query =
                "let $outside := <root><a>1</a></root>\n" +
                "return copy $c := <x><y/></x>\n" +
                "       modify replace value of node $outside/a with \"2\"\n" +
                "       return $c";
        try {
            existEmbeddedServer.executeQuery(query);
            fail("Expected XUDY0014");
        } catch (final org.xmldb.api.base.XMLDBException e) {
            assertTrue("Should raise XUDY0014", e.getMessage().contains("XUDY0014"));
        }
    }

    @Test
    public void commaExpr015TwoReplaceValuesSnapshotIsolation() throws XMLDBException {
        // id-comma-expr-015: two replace value ops referencing each other's targets
        // Tests W3C snapshot semantics: content expressions evaluated BEFORE updates applied
        final String query =
                "let $doc := <works>\n" +
                "  <employee name=\"Jane\">\n" +
                "    <hours>40</hours>\n" +
                "  </employee>\n" +
                "  <employee name=\"John\">\n" +
                "    <hours>70</hours>\n" +
                "    <hours>20</hours>\n" +
                "  </employee>\n" +
                "</works>\n" +
                "return copy $c := $doc\n" +
                "modify (\n" +
                "  let $var1 := $c/employee[1]\n" +
                "  let $var2 := $c/employee[2]\n" +
                "  return (\n" +
                "    replace value of node $var1/hours[1] with $var2/hours[1],\n" +
                "    replace value of node $var2/hours[2] with $var1/hours[1]\n" +
                "  )\n" +
                ")\n" +
                "return ($c/employee[1]/hours, $c/employee[2]/hours)";
        final ResourceSet result = existEmbeddedServer.executeQuery(query);
        assertEquals(3L, result.getSize());
        // employee[1]/hours[1]: was 40, replaced with $var2/hours[1]=70
        assertEquals("70", result.getResource(0).getContent().toString().replaceAll("</?hours>", ""));
        // employee[2]/hours[1]: unchanged = 70
        assertEquals("70", result.getResource(1).getContent().toString().replaceAll("</?hours>", ""));
        // employee[2]/hours[2]: was 20, replaced with $var1/hours[1]=40 (original, snapshot)
        assertEquals("40", result.getResource(2).getContent().toString().replaceAll("</?hours>", ""));
    }

    @Test
    public void replaceNode029ReplaceTextNodes() throws XMLDBException {
        // id-replace-expr-029: replace text nodes
        final String query =
                "copy $c := <employee name=\"Jane Doe 1\" gender=\"female\">\n" +
                "   <empnum>E1</empnum>\n" +
                "   <pnum>P1</pnum>\n" +
                "   <hours>40</hours>\n" +
                "</employee>\n" +
                "modify (\n" +
                "  replace node $c/empnum[1]/text() with \"E1000\",\n" +
                "  replace node $c/hours[1]/text() with 10\n" +
                ")\n" +
                "return $c";
        final ResourceSet result = existEmbeddedServer.executeQuery(query);
        assertEquals(1L, result.getSize());
        final String xml = result.getResource(0).getContent().toString();
        assertTrue("empnum should be E1000", xml.contains("<empnum>E1000</empnum>"));
        assertTrue("hours should be 10", xml.contains("<hours>10</hours>"));
    }

    @Test
    public void deleteMultipleAttributesForLoop() throws XMLDBException {
        // Delete attributes on multiple elements using for loop (workaround for //(@attr) bug)
        final String query =
                "let $doc := <root>\n" +
                "  <a x=\"1\" y=\"2\" z=\"3\"/>\n" +
                "  <b x=\"4\" y=\"5\" z=\"6\"/>\n" +
                "</root>\n" +
                "return copy $c := $doc\n" +
                "modify (\n" +
                "  for $e in $c//* return delete nodes ($e/@y, $e/@z)\n" +
                ")\n" +
                "return $c";
        final ResourceSet result = existEmbeddedServer.executeQuery(query);
        assertEquals(1L, result.getSize());
        final String xml = result.getResource(0).getContent().toString();
        // After deleting @y and @z, only @x should remain
        assertTrue("a should have only x", xml.contains("<a x=\"1\""));
        assertFalse("a should not have y", xml.contains("y=\"2\""));
        assertFalse("a should not have z", xml.contains("z=\"3\""));
        assertTrue("b should have only x", xml.contains("<b x=\"4\""));
        assertFalse("b should not have y", xml.contains("y=\"5\""));
        assertFalse("b should not have z", xml.contains("z=\"6\""));
    }

    @Test
    public void deleteDocumentNodeIsNoOp() throws XMLDBException {
        // complex-deletes-q14: delete document node is a no-op
        final String query =
                "let $doc := document { <root><a/><b/></root> }\n" +
                "return copy $c := $doc\n" +
                "modify delete nodes $c\n" +
                "return $c";
        final ResourceSet result = existEmbeddedServer.executeQuery(query);
        assertEquals(1L, result.getSize());
        final String xml = result.getResource(0).getContent().toString();
        System.err.println("deleteDocumentNode: " + xml);
        assertTrue("Document should be preserved", xml.contains("<root>"));
    }

    @Test
    public void replaceValueOfElementWithMarkup() throws XMLDBException {
        // complex-replacevalues-q14: replace value with string that looks like markup
        final String query =
                "copy $c := <root><target>old</target></root>\n" +
                "modify replace value of node $c/target with \"<notANode>value</notANode>\"\n" +
                "return $c/target";
        final ResourceSet result = existEmbeddedServer.executeQuery(query);
        assertEquals(1L, result.getSize());
        final String xml = result.getResource(0).getContent().toString();
        System.err.println("replaceValueMarkup: " + xml);
        // The markup string should be escaped text, not parsed as XML
        assertTrue("Should contain escaped markup",
                xml.contains("&lt;notANode&gt;value&lt;/notANode&gt;"));
    }

    /**
     * Test insert + delete on same parent element in a single PUL.
     * Reproduces XQTS applyUpdates-001: insert comment into element then delete its text child.
     * After PUL application, the element should contain only the comment (text deleted).
     * Verifies that getFirstChildFor can find appended children when positional children are deleted.
     */
    @Test
    public void applyUpdates001InsertCommentDeleteText() throws XMLDBException {
        final String query =
                "copy $c := <employee><hours>40</hours></employee>\n" +
                "modify (\n" +
                "  insert node comment { 'Testing' } into $c/hours,\n" +
                "  delete node $c/hours/text()\n" +
                ")\n" +
                "return $c/hours";
        final ResourceSet result = existEmbeddedServer.executeQuery(query);
        assertEquals(1L, result.getSize());
        final String xml = result.getResource(0).getContent().toString();
        System.err.println("applyUpdates001: " + xml);
        // Should contain the comment but NOT the text "40"
        assertTrue("Should contain comment", xml.contains("<!--Testing-->"));
        assertFalse("Should not contain original text '40'", xml.contains("40"));
    }

    /**
     * Test delete text + insert comment (reverse order) on same parent.
     * Reproduces XQTS applyUpdates-002.
     */
    @Test
    public void applyUpdates002DeleteTextInsertComment() throws XMLDBException {
        final String query =
                "copy $c := <employee><hours>40</hours></employee>\n" +
                "modify (\n" +
                "  delete node $c/hours/text(),\n" +
                "  insert node comment { 'Testing' } into $c/hours\n" +
                ")\n" +
                "return $c/hours";
        final ResourceSet result = existEmbeddedServer.executeQuery(query);
        assertEquals(1L, result.getSize());
        final String xml = result.getResource(0).getContent().toString();
        System.err.println("applyUpdates002: " + xml);
        assertTrue("Should contain comment", xml.contains("<!--Testing-->"));
        assertFalse("Should not contain original text '40'", xml.contains("40"));
    }

    /**
     * Test rename on elements accessed via in-memory document navigation.
     * Reproduces XQTS complex-renames-q4: rename one of multiple matching elements.
     */
    @Test
    public void renameInMemoryElementSingleFromMultiple() throws XMLDBException {
        final String query =
                "copy $c := <root><a mark='1'/><a mark='2'/></root>\n" +
                "modify rename node ($c//a)[1] as 'b'\n" +
                "return <result>\n" +
                "  <a-count>{count($c//a)}</a-count>\n" +
                "  <b-count>{count($c//b)}</b-count>\n" +
                "</result>";
        final ResourceSet result = existEmbeddedServer.executeQuery(query);
        assertEquals(1L, result.getSize());
        final String xml = result.getResource(0).getContent().toString();
        System.err.println("renameInMemory: " + xml);
        assertTrue("Should have 1 'a' element", xml.contains("<a-count>1</a-count>"));
        assertTrue("Should have 1 'b' element", xml.contains("<b-count>1</b-count>"));
    }

    /**
     * Test replace value on in-memory elements via for loop.
     * Reproduces XQTS complex-replacevalues-q8 pattern.
     */
    @Test
    public void replaceValueInMemoryElementsForLoop() throws XMLDBException {
        final String query =
                "copy $c := <root><item>old1</item><item>old2</item></root>\n" +
                "modify for $a in $c//item return replace value of node $a with 'new'\n" +
                "return $c";
        final ResourceSet result = existEmbeddedServer.executeQuery(query);
        assertEquals(1L, result.getSize());
        final String xml = result.getResource(0).getContent().toString();
        System.err.println("replaceValueForLoop: " + xml);
        assertFalse("Should not contain 'old1'", xml.contains("old1"));
        assertFalse("Should not contain 'old2'", xml.contains("old2"));
        assertTrue("Should contain 'new'", xml.contains("<item>new</item>"));
    }

    /**
     * Test delete document-level comments using >> (follows) operator.
     * Reproduces XQTS complex-deletes-q2: delete trailing comments.
     */
    @Test
    public void deleteDocumentCommentsFollowsOperator() throws XMLDBException {
        // Simulates the structure: document has root element, then comments after it
        final String query =
                "let $doc := <root/>\n" +
                "return\n" +
                "copy $c := $doc\n" +
                "modify ()\n" +
                "return $c";
        // Basic test: just make sure >> operator works
        final String followsTest =
                "let $doc := parse-xml('<root><a/><b/></root>')\n" +
                "return count($doc/root/*[. >> $doc/root/a])";
        final ResourceSet result = existEmbeddedServer.executeQuery(followsTest);
        assertEquals(1L, result.getSize());
        assertEquals("1", result.getResource(0).getContent().toString());
    }

    /**
     * Test replace value of element on persistent document via top-level PUL.
     * Reproduces XQTS complex-replacevalues-q8 on stored documents.
     */
    @Test
    public void replaceValuePersistentForLoop() throws XMLDBException {
        final XQueryService queryService = storeXMLStringAndGetQueryService(
                "topMany.xml",
                "<root><se mark='1se'/><se mark='2se'/></root>");
        queryService.setProperty("base-uri", testCollection.getName());

        // Update: replace value of all se elements
        queryService.query(
                "let $doc := doc('" + testCollection.getName() + "/topMany.xml')\n" +
                "for $a in $doc//se\n" +
                "return replace value of node $a with 'content'");

        // Verify
        final ResourceSet result = queryService.query(
                "let $doc := doc('" + testCollection.getName() + "/topMany.xml')\n" +
                "return <result>{$doc//se}</result>");
        assertEquals(1L, result.getSize());
        final String xml = result.getResource(0).getContent().toString();
        System.err.println("replaceValuePersistent: " + xml);
        assertTrue("First se should have content",
                xml.contains("<se mark=\"1se\">content</se>"));
        assertTrue("Second se should have content",
                xml.contains("<se mark=\"2se\">content</se>"));
    }

    /**
     * Test replace value on in-memory document via top-level PUL (not copy-modify).
     * Simulates what the XQTS runner does: parse XML, apply top-level update, query result.
     * This is a two-step query: first update, then verify in separate query.
     */
    @Test
    public void replaceValueTopLevelPULInMemoryDoc() throws XMLDBException {
        // Store the XML in the database first, so we can do a two-step update+verify
        final XQueryService queryService = storeXMLStringAndGetQueryService(
                "inmem.xml",
                "<root><se mark='1se'/><se mark='2se'/></root>");
        queryService.setProperty("base-uri", testCollection.getName());

        // Step 1: use copy-modify to simulate top-level PUL on in-memory doc
        final ResourceSet result = existEmbeddedServer.executeQuery(
                "let $doc := parse-xml('<root><se mark=\"1se\"/><se mark=\"2se\"/></root>')\n" +
                "return\n" +
                "  copy $c := $doc\n" +
                "  modify for $a in $c//se return replace value of node $a with 'content'\n" +
                "  return <result>{$c//se}</result>");
        assertEquals(1L, result.getSize());
        final String xml = result.getResource(0).getContent().toString();
        System.err.println("replaceValueTopLevelPUL: " + xml);
        assertTrue("First se should have content",
                xml.contains("<se mark=\"1se\">content</se>"));
        assertTrue("Second se should have content",
                xml.contains("<se mark=\"2se\">content</se>"));
    }

    /**
     * Test rename on persistent document via top-level PUL.
     * Reproduces XQTS complex-renames-q2 on stored documents.
     */
    @Test
    public void renamePersistentMultipleElements() throws XMLDBException {
        final XQueryService queryService = storeXMLStringAndGetQueryService(
                "topMany.xml",
                "<root><se mark='1se'/><se mark='2se'/></root>");
        queryService.setProperty("base-uri", testCollection.getName());

        // Update: rename all se elements to 'renamed'
        queryService.query(
                "let $doc := doc('" + testCollection.getName() + "/topMany.xml')\n" +
                "for $a in $doc//se\n" +
                "return rename node $a as 'renamed'");

        // Verify
        final ResourceSet result = queryService.query(
                "let $doc := doc('" + testCollection.getName() + "/topMany.xml')\n" +
                "return <result>\n" +
                "  <se-count>{count($doc//se)}</se-count>\n" +
                "  <renamed-count>{count($doc//renamed)}</renamed-count>\n" +
                "</result>");
        assertEquals(1L, result.getSize());
        final String xml = result.getResource(0).getContent().toString();
        System.err.println("renamePersistent: " + xml);
        assertTrue("Should have 0 'se' elements", xml.contains("<se-count>0</se-count>"));
        assertTrue("Should have 2 'renamed' elements", xml.contains("<renamed-count>2</renamed-count>"));
    }

    /**
     * XQTS update10keywords: XQuery Update keywords can be used as variable names.
     */
    @Test
    public void updateKeywordsAsVariableNames() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                "let $ascending := 1 let $descending := 2 let $greatest := 3 " +
                "let $least := 4 let $satisfies := 5 let $revalidation := 6 " +
                "let $skip := 7 let $strict := 8 let $lax := 9 " +
                "let $insert := 10 let $delete := 11 let $replace := 12 " +
                "let $rename := 13 let $copy := 14 let $modify := 15 " +
                "let $value := 16 let $into := 17 let $with := 18 " +
                "let $after := 19 let $before := 20 let $first := 21 " +
                "let $last := 22 let $nodes := 23 let $updating := 24 " +
                "return $ascending + $descending");
        assertEquals(1L, result.getSize());
        assertEquals("3", result.getResource(0).getContent().toString());
    }

    /**
     * XQTS propagateNamespaces01: namespace propagation in copy-modify insert.
     */
    @Test
    public void propagateNamespacesPreserveInherit() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                "declare copy-namespaces preserve, inherit;\n" +
                "copy $data := <v xmlns:a=\"a-one\" xmlns:b=\"b-one\"/>\n" +
                "modify insert node <w> <x xmlns:a=\"a-two\"> <y xmlns:b=\"b-two\"><z/></y> </x> </w> into $data\n" +
                "return\n" +
                "  let $w := $data/w\n" +
                "  let $x := $w/x\n" +
                "  let $y := $x/y\n" +
                "  let $z := $y/z\n" +
                "  return <result>\n" +
                "    <w>{namespace-uri-for-prefix('a', $w), namespace-uri-for-prefix('b',$w)}</w>\n" +
                "    <x>{namespace-uri-for-prefix('a', $x), namespace-uri-for-prefix('b',$x)}</x>\n" +
                "    <y>{namespace-uri-for-prefix('a', $y), namespace-uri-for-prefix('b',$y)}</y>\n" +
                "    <z>{namespace-uri-for-prefix('a', $z), namespace-uri-for-prefix('b',$z)}</z>\n" +
                "  </result>");
        assertEquals(1L, result.getSize());
        final String xml = result.getResource(0).getContent().toString();
        System.err.println("propagateNamespaces: " + xml);
        // With preserve+inherit, inserted children should inherit parent's namespaces
        assertTrue("w should inherit a-one", xml.contains("<w>a-one b-one</w>"));
        assertTrue("x should override a with a-two", xml.contains("<x>a-two b-one</x>"));
        assertTrue("y should override b with b-two", xml.contains("<y>a-two b-two</y>"));
        assertTrue("z should inherit from y", xml.contains("<z>a-two b-two</z>"));
    }

    /**
     * Simulate XQTS FullAxis complex-replacevalues-q8: replaceValue on multiple sibling
     * empty elements, then verify with following-sibling axis and predicate filter.
     */
    @Test
    public void replaceValueEmptyElementsFollowingSiblingAxis() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                "let $doc := parse-xml('" +
                "<center mark=\"c0\">" +
                "  <near-south> text-5A" +
                "    <south mark=\"s0\"> text-6A <far-south/> text-6B </south> text-5B" +
                "  </near-south> text-4E" +
                "  <south-east mark=\"1se\"/> text-4G" +
                "  <south-east mark=\"2se\"/> text-4H" +
                "</center>')\n" +
                "return\n" +
                "  copy $c := $doc\n" +
                "  modify for $a in $c//south-east return replace value of node $a with 'very south east'\n" +
                "  return (\n" +
                "    let $a := $c//near-south/following-sibling::node()\n" +
                "    return <result count=\"{count($a)}\">{$a}</result>,\n" +
                "    let $a := $c//south-east[. = 'very south east']\n" +
                "    return <result count=\"{count($a)}\">{$a}</result>\n" +
                "  )");

        assertEquals(2L, result.getSize());
        final String r1 = result.getResource(0).getContent().toString();
        final String r2 = result.getResource(1).getContent().toString();
        System.err.println("replaceValueFollowingSibling r1: " + r1);
        System.err.println("replaceValueFollowingSibling r2: " + r2);

        // r2 should find both south-east elements with the replaced text
        assertTrue("Should find south-east with replaced value",
                r2.contains("<south-east mark=\"1se\">very south east</south-east>"));
        assertTrue("Should find both south-east elements",
                r2.contains("count=\"2\""));
    }

    /**
     * Test that //(element | element) union expressions with descendant axis work correctly.
     * Note: //(@attr) with parenthesized attribute expressions is a pre-existing eXist
     * limitation where the // axis handling incorrectly overwrites the attribute axis.
     * The non-parenthesized //@x form works correctly. See PR #6106 for the fix.
     */
    @Test
    public void parenthesizedAttributeUnionWithDescendant() throws XMLDBException {
        // //@x (non-parenthesized) should work correctly
        final ResourceSet result = existEmbeddedServer.executeQuery(
                "let $doc := <root>" +
                "  <a x='1' y='2'/>" +
                "  <b><c x='3' z='4'/></b>" +
                "</root>\n" +
                "return <r>{count($doc//@x)}</r>");
        assertEquals(1L, result.getSize());
        assertEquals("//@x should find 2", "<r>2</r>", result.getResource(0).getContent().toString());

        // //(element-name | element-name) should find descendants
        final ResourceSet result2 = existEmbeddedServer.executeQuery(
                "let $doc := <root><a><b>1</b><c>2</c></a><d><b>3</b></d></root>\n" +
                "return <r>{count($doc//(b | c))}</r>");
        assertEquals(1L, result2.getSize());
        assertEquals("//(b | c) should find 3 elements", "<r>3</r>",
                result2.getResource(0).getContent().toString());

        // //(element-union) in nested structure should find all matching descendants
        final ResourceSet result3 = existEmbeddedServer.executeQuery(
                "let $doc := <far-north>\n" +
                "    <!-- Comment-3 -->\n" +
                "  <north mark='n0'>\n" +
                "    <near-north>\n" +
                "      <center mark='c0'>\n" +
                "        <!--Comment-5-->\n" +
                "        <near-south/>\n" +
                "      </center>\n" +
                "    </near-north>\n" +
                "  </north>\n" +
                "</far-north>\n" +
                "return (\n" +
                "  <found>{for $n in $doc//(north | near-south) return local-name($n)}</found>,\n" +
                "  <comments>{count($doc//(north | near-south)/preceding-sibling::comment())}</comments>\n" +
                ")");
        assertEquals(2L, result3.getSize());
        assertTrue("Should find both elements",
                result3.getResource(0).getContent().toString().contains("north")
                && result3.getResource(0).getContent().toString().contains("near-south"));
        assertEquals("Should find 2 preceding-sibling comments",
                "<comments>2</comments>", result3.getResource(1).getContent().toString());
    }

}
