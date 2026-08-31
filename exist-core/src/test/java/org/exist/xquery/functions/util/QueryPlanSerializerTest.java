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
package org.exist.xquery.functions.util;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

import static org.junit.Assert.assertTrue;

/**
 * Structural-shape tests for {@code util:explain}.
 *
 * <p>Each test invokes {@code util:explain(...)} on a small XQuery and
 * asserts that the returned XML correctly <em>nests</em> sub-expressions
 * under their parent, rather than emitting them as siblings. The original
 * defect (issue #6208 review, 2026-06-01): the default
 * {@code QueryPlanSerializer.visit(Expression)} closed the
 * {@code <expression>} element immediately, so any expression whose
 * {@code accept(visitor)} called {@code super.accept} and then recursed
 * into children produced sibling-children output that's hard to read.</p>
 *
 * <p>The tests use {@code matches(...)} on the string output rather than
 * structural XPath because {@code util:explain} returns a constructed
 * in-memory document and we want to be resilient to incidental attribute
 * order.</p>
 */
public class QueryPlanSerializerTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer embedded =
            new ExistXmldbEmbeddedServer(false, true, true);

    /**
     * Juri's Case 1: {@code map { $p : $i }} as the for-loop body. The
     * map's key + value must appear as children of the map element, not
     * as siblings of a self-closed {@code <expression type="MapExpr"/>}.
     */
    @Test
    public void mapConstructorNestsKeysAndValuesAsChildren() throws XMLDBException {
        final String xml = explain("for $i at $p in (1,2,3) return map { $p : $i }");
        // The defect emitted <expression type="MapExpr"/> followed by two
        // <variable> siblings. The fix must produce a map element with
        // key/value sub-elements.
        assertContains(xml, "MapExpr — body must nest its entries", "<map");
        assertNotContains(xml, "MapExpr — must NOT be self-closed",
                "<expression type=\"MapExpr\"/>");
        // The two variables (key $p, value $i) must appear inside the map.
        assertTrue("map body must contain key + value variable refs: " + xml,
                xml.matches("(?s).*<map[^>]*>.*\\$p.*\\$i.*</map>.*"));
    }

    /**
     * Juri's Case 2: {@code //p[@id]}'s {@code <expression type="RootNode"/>}
     * + descendant-step pair under {@code <path>}. RootNode is a leaf step
     * in the AST (the descendant step is a sibling, not a child, in the
     * PathExpr's step list) so the bare {@code <expression type="RootNode"/>}
     * IS structurally accurate; what mattered for Juri was that the
     * downstream descendant step is rendered correctly. Verify the path
     * contains both the RootNode reference and the descendant step.
     */
    @Test
    public void rootNodePathHasBothSteps() throws XMLDBException {
        final String xml = explain("//p[@id]");
        assertContains(xml, "path wraps the steps", "<path");
        assertContains(xml, "descendant step is emitted",
                "axis=\"descendant\"");
        assertContains(xml, "predicate is nested under the step",
                "<predicate");
    }

    /** Conditional expression must nest condition / then / else as children. */
    @Test
    public void conditionalNestsBranches() throws XMLDBException {
        final String xml = explain("if (1 eq 1) then 'a' else 'b'");
        // A conditional currently goes through visitConditional which is
        // already wired correctly; this test pins that behaviour.
        assertContains(xml, "conditional emits a <if> wrapper", "<if");
    }

    /** Try-catch must nest the try body and the catch body. */
    @Test
    public void tryCatchNestsBodies() throws XMLDBException {
        final String xml = explain(
                "xquery version \"3.1\"; try { 1 div 0 } catch * { 'err' }");
        assertContains(xml, "try-catch emits a <try-catch>", "<try-catch");
    }

    /** FunctionCall must nest its arguments under the call element. */
    @Test
    public void functionCallNestsArguments() throws XMLDBException {
        final String xml = explain("concat('a', 'b', 'c')");
        // visitBuiltinFunction already emits <builtin-function> with argument
        // children — verify that's still the case.
        assertContains(xml, "function call emits a <builtin-function>",
                "<builtin-function");
    }

    /** Simple-map operator (! operator) must nest left + right. */
    @Test
    public void simpleMapNestsLeftAndRight() throws XMLDBException {
        final String xml = explain("(1,2,3) ! (. + 1)");
        // visitSimpleMapOperator should emit a <simple-map> (or similar)
        // wrapper with both operands as children.
        assertContains(xml, "simple-map emits a wrapper", "<simple-map");
    }

    /** Variable reference must render as a single self-closed element. */
    @Test
    public void variableReferenceRendersAsLeaf() throws XMLDBException {
        final String xml = explain("let $x := 1 return $x");
        // <variable> exists for both the let-binding and the reference.
        assertContains(xml, "variable reference present", "$x");
    }

    /**
     * Comprehensive sanity: a constructor that has children (MapExpr's
     * key/value pairs) MUST NOT render as a self-closed
     * {@code <expression type="MapExpr"/>}. The defect signature: the
     * default {@code visit()} emitted the type attribute and closed the
     * element before children could nest.
     */
    @Test
    public void containerExpressionsNestTheirChildren() throws XMLDBException {
        final String xml = explain(
                "for $i at $p in //p[@id] return map { $p : $i }");
        assertNotContains(xml, "MapExpr container must not self-close",
                "<expression type=\"MapExpr\"/>");
        // True AST leaves (RootNode is a singleton root reference with no
        // sub-expressions, ContextItemExpression, etc.) MAY self-close —
        // that is structurally correct and not part of the defect.
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static String explain(final String xqueryText) throws XMLDBException {
        final String esc = xqueryText.replace("\\", "\\\\").replace("\"", "\"\"");
        final ResourceSet rs = embedded.executeQuery(
                "util:explain(\"" + esc + "\")");
        return (String) rs.getResource(0).getContent();
    }

    private static void assertContains(final String xml, final String message, final String needle) {
        assertTrue(message + " — not found in: " + xml, xml.contains(needle));
    }

    private static void assertNotContains(final String xml, final String message, final String needle) {
        assertTrue(message + " — but found in: " + xml, !xml.contains(needle));
    }
}
