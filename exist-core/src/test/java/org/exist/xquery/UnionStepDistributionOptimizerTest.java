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
package org.exist.xquery;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XQueryService;

import static org.junit.Assert.assertEquals;

/**
 * Tests for the union-step distribution rewrite in
 * {@link Optimizer#visitPathExpr(PathExpr)}.
 *
 * <p>The optimizer rewrites {@code outer//(A | B [| C ...])} into
 * {@code outer//A | outer//B [| outer//C ...]} so each branch dispatches
 * through the structural index by qname. This file verifies correctness:
 * results with the optimizer enabled match results with it disabled, across
 * a range of shapes (binary unions, n-ary unions, prefix and suffix steps,
 * mixed axes, predicates inside branches).
 *
 * <p>Performance is verified separately in {@code exist-indexes-jmh}; here we
 * only assert semantic equivalence.
 */
public class UnionStepDistributionOptimizerTest {

    private static final String OPTIMIZE = "declare option exist:optimize 'enable=yes'; ";
    private static final String NO_OPTIMIZE = "declare option exist:optimize 'enable=no'; ";

    private static final String COLLECTION_NAME = "union-step-distribution-test";
    private static final String DOC_NAME = "fixture.xml";

    private static final String FIXTURE_XML = """
            <library>
              <book id="b1">
                <title>One</title>
                <author>Alice</author>
                <chapter n="1"><para>p1</para></chapter>
                <chapter n="2"><para>p2</para></chapter>
              </book>
              <book id="b2">
                <title>Two</title>
                <author>Bob</author>
                <author>Carol</author>
                <chapter n="1"><para>p3</para></chapter>
              </book>
              <journal id="j1">
                <title>Three</title>
                <article><author>Dave</author><para>p4</para></article>
              </journal>
              <article id="a1"><title>Four</title><para>p5</para></article>
            </library>
            """;

    @ClassRule
    public static final ExistXmldbEmbeddedServer server =
            new ExistXmldbEmbeddedServer(false, true, true);

    @BeforeClass
    public static void loadFixture() throws XMLDBException {
        final Collection root = server.getRoot();
        final CollectionManagementService cms = root.getService(CollectionManagementService.class);
        final Collection coll = cms.createCollection(COLLECTION_NAME);
        final XMLResource res = coll.createResource(DOC_NAME, XMLResource.class);
        res.setContent(FIXTURE_XML);
        coll.storeResource(res);
    }

    @AfterClass
    public static void cleanup() throws XMLDBException {
        final Collection root = server.getRoot();
        final CollectionManagementService cms = root.getService(CollectionManagementService.class);
        cms.removeCollection(COLLECTION_NAME);
    }

    /**
     * Run a query with and without the optimizer; assert the result sizes
     * match. The query body is interpolated against the fixture document.
     */
    private void assertOptimizerParity(final String body) throws XMLDBException {
        final XQueryService svc = server.getRoot().getService(XQueryService.class);
        final String docPrefix = "let $d := doc('/db/" + COLLECTION_NAME + "/" + DOC_NAME + "') return ";
        final ResourceSet baseline = svc.query(NO_OPTIMIZE + docPrefix + body);
        final ResourceSet optimized = svc.query(OPTIMIZE + docPrefix + body);
        assertEquals("Optimized result should match baseline for: " + body,
                baseline.getSize(), optimized.getSize());
    }

    /** Shorthand: assert both forms return the given count. */
    private void assertCount(final long expected, final String body) throws XMLDBException {
        final XQueryService svc = server.getRoot().getService(XQueryService.class);
        final String docPrefix = "let $d := doc('/db/" + COLLECTION_NAME + "/" + DOC_NAME + "') return ";
        final ResourceSet baseline = svc.query(NO_OPTIMIZE + docPrefix + body);
        final ResourceSet optimized = svc.query(OPTIMIZE + docPrefix + body);
        assertEquals("Baseline (no opt) size: " + body, expected, baseline.getSize());
        assertEquals("Optimized size: " + body, expected, optimized.getSize());
    }

    @Test
    public void binaryUnionUnderDescendant() throws XMLDBException {
        // //(book | journal) should match all books and journals
        assertCount(3, "$d//(book | journal)");
    }

    @Test
    public void binaryUnionUnderChild() throws XMLDBException {
        // /library/(book | article) reaches one of each at the top level
        assertCount(3, "$d/library/(book | article)");
    }

    @Test
    public void naryUnionThreeBranches() throws XMLDBException {
        // //(book | journal | article) -- the n-ary case parses as
        // Union(Union(book, journal), article); distribution must produce
        // three independent paths. Fixture: 2 books + 1 journal +
        // 2 articles (one nested inside the journal, one top-level) = 5.
        assertCount(5, "$d//(book | journal | article)");
    }

    @Test
    public void naryUnionFourBranches() throws XMLDBException {
        // four-branch union to exercise deeper recursion in the
        // distribution helper. Fixture: 4 titles + 4 authors + 5 paras +
        // 3 chapters = 16.
        assertCount(16, "$d//(title | author | para | chapter)");
    }

    @Test
    public void unionWithSuffixStep() throws XMLDBException {
        // outer//(A | B)/c shape -- the optimizer must preserve the
        // trailing step on every distributed branch. Fixture: 4 elements
        // with a direct child <title> (b1, b2, j1, a1).
        assertCount(4, "$d//(book | journal | article)/title");
    }

    @Test
    public void unionWithPrefixAndSuffix() throws XMLDBException {
        // /library/(book | journal)/title -- prefix and trailing step
        assertCount(3, "$d/library/(book | journal)/title");
    }

    @Test
    public void unionInsidePredicate() throws XMLDBException {
        // a predicate that contains a union step expression should still
        // produce the correct count -- the rewrite needs to leave inner
        // path expressions inside predicates evaluable
        assertOptimizerParity("$d//book[(title | author)]");
    }

    @Test
    public void unionStepInsidePredicatePath() throws XMLDBException {
        // Regression for UnionTest.unionInPredicate_*: the union step lives
        // INSIDE a predicate's path expression. The predicate engine tracks
        // candidate-to-result mapping via per-step contextId (see
        // Predicate.selectByNodeSet). Rewriting the predicate's inner path
        // into a Union loses the contextId thread, so distribution must be
        // suppressed when predicates > 0.
        // Use exists() to keep the predicate body a node-set expression
        // (avoids an unrelated pre-existing GeneralComparison cast issue
        // when the right-hand side is a sequence literal).
        assertCount(2, "$d//book[exists(chapter/(para | title))]");
    }

    @Test
    public void unionWithFilteredBranches() throws XMLDBException {
        // each branch carries its own predicate -- distribution must
        // preserve per-branch filtering
        assertCount(2, "$d//(book[@id='b1'] | journal[@id='j1'])");
    }

    @Test
    public void attributeUnionUnderDescendant() throws XMLDBException {
        // attribute axis branches: //(@id | @n)
        assertCount(7, "$d//(@id | @n)");
    }

    @Test
    public void unionUnderVariableContextPath() throws XMLDBException {
        // a variable-bound context followed by //(A|B) -- the outer prefix
        // includes a variable reference and a descendant-or-self step
        assertOptimizerParity("$d/library//(book | journal)");
    }

    @Test
    public void emptyUnionBranchProducesNoMatch() throws XMLDBException {
        // a name that doesn't exist in the fixture -- distribution must
        // still return the existing branch's matches (just b1)
        assertCount(1, "$d//(book[@id='b1'] | nonexistent)");
    }

    @Test
    public void nestedPathInsideUnionBranch() throws XMLDBException {
        // //(book/title | journal/title) -- each branch is a multi-step
        // path; both steps in each branch are LocationSteps so the rewrite
        // applies and each branch gets the outer descendant prefix
        assertCount(3, "$d//(book/title | journal/title)");
    }

    @Test
    public void unionPreservesDocumentOrder() throws XMLDBException {
        // | is a set union with document-order sort and dedup. The
        // distributed form must preserve this ordering.
        final XQueryService svc = server.getRoot().getService(XQueryService.class);
        final String body = "let $d := doc('/db/" + COLLECTION_NAME + "/" + DOC_NAME + "') "
                + "return string-join($d//(book | journal | article)/@id, ',')";
        final String baseline = svc.query(NO_OPTIMIZE + body).getResource(0).getContent().toString();
        final String optimized = svc.query(OPTIMIZE + body).getResource(0).getContent().toString();
        assertEquals(baseline, optimized);
    }

    @Test
    public void unionInsideForLoopBody() throws XMLDBException {
        // a FLWOR body containing a union-step path -- the optimizer
        // visits FLWOR-internal expressions; the rewrite must work when
        // the outer path's parent is not a RewritableExpression
        assertOptimizerParity(
                "for $b in $d//book return $b//(title | chapter/para)");
    }

    @Test
    public void unionInsideFunctionCallArgument() throws XMLDBException {
        // count() argument is the union path; the parent of the rewrite
        // target is a FunctionCall, not a RewritableExpression
        assertOptimizerParity("count($d//(book | journal | article))");
    }

    @Test
    public void redundantUnionDeduped() throws XMLDBException {
        // //(book | book) -- both branches match the same nodes; the
        // distributed Union still dedups (per CombiningExpression eval)
        assertCount(2, "$d//(book | book)");
    }

    @Test
    public void unionWithNonNodeReturningSuffix() throws XMLDBException {
        // outer/(A|B)/string() -- the suffix returns strings, not nodes.
        // Distribution would push /string() into each branch, making the
        // resulting Union try to combine string sequences and fail
        // CombiningExpression's node-only invariant. The optimizer must
        // detect this and skip distribution for this shape.
        // (Mirrors a regression caught by xmlts UnionInPath tests.)
        assertOptimizerParity(
                "let $a := <el><el1/><el2 att='val'/><el3/></el> "
                        + "return $a/el2/(@*[1]|@*[1])/string()");
    }
}
