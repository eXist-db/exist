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
import org.exist.xmldb.EXistXQueryService;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.CompiledExpression;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XQueryService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
    public void fundocsShapeMixedBranches() throws XMLDBException {
        // Replicates the search-everywhere shape from the function-documentation
        // app: an outer //(...) over branches that mix single predicated
        // LocationSteps with multi-step paths. line-o reported on PR #6310 that
        // this query takes 9s and PathExpr.replaceAllSteps is never called.
        final String body = """
                let $d := doc('/db/%s/%s')
                return $d//(
                    book[contains(title, 'One')]
                    | book[contains(author, 'Bob')]
                    | journal[contains(title, 'Three')]
                    | article[@id='a1']
                )
                """.formatted(COLLECTION_NAME, DOC_NAME);
        final XQueryService svc = server.getRoot().getService(XQueryService.class);
        final ResourceSet baseline = svc.query(NO_OPTIMIZE + body);
        final ResourceSet optimized = svc.query(OPTIMIZE + body);
        assertEquals("Optimized must match baseline", baseline.getSize(), optimized.getSize());
    }

    /**
     * AST-shape regression: assert that after the optimizer runs on a
     * parenthesised-union path expression like {@code $d//(A[pred] | B[pred])},
     * the resulting AST has been distributed -- the {@code Union} now sits
     * in step position with one fully-formed PathExpr branch per original
     * union arm, and the parser-produced parens-PathExpr wrapper that
     * previously contained the union is gone from the tree.
     *
     * <p>This test guards three concrete regressions:
     * <ol>
     *   <li>The optimization stops firing entirely (parens-PathExpr wrapper
     *       remains in the tree, no Union appears at the path level).</li>
     *   <li>The optimization fires but produces a Union with the wrong
     *       number of branches (e.g. nested-Union flattening breaks).</li>
     *   <li>A distributed branch loses its leading prefix steps -- a
     *       regression where {@link Optimizer#distributeBranch} stops
     *       copying outer prefix into each branch and the rewritten
     *       query no longer reaches the same nodes.</li>
     * </ol>
     *
     * <p>This test exists because the prior test {@code fundocsShapeMixedBranches}
     * passed on a broken intermediate fix (commit 1621fbafbf, which ran
     * union distribution AFTER super.visitPathExpr descended) where the
     * rewrite fired but the rewritten form ran no faster than the original
     * 9s parens form. Result-set equivalence held either way. Stronger
     * AST-shape assertions catch shape regressions at unit-test time
     * even when result-set parity is preserved.
     *
     * <p>Caveat: this test does NOT catch the specific 1621fbafbf bug
     * (post-descent ordering wrapped predicated steps against the
     * parens-context before distribution moved them; the resulting
     * structural tree is identical to the working form). Catching that
     * regression structurally requires either a perf-bound assertion
     * or wiring through visitor-call-order instrumentation -- both more
     * brittle than this shape check. The shape check covers the broader
     * class of "did distribution happen at all and produce a well-formed
     * Union" regressions.
     */
    @Test
    public void parensFormDistributesToUnionShape() throws XMLDBException {
        final EXistXQueryService svc = server.getRoot().getService(EXistXQueryService.class);
        final String prefix = "let $d := doc('/db/" + COLLECTION_NAME + "/" + DOC_NAME + "') return ";
        final String parens = OPTIMIZE + prefix
                + "$d//(book[contains(title, 'O')] | journal[contains(title, 'T')])";

        final CompiledExpression compiled = svc.compile(parens);
        // execute() triggers analyze + optimize on the compiled tree.
        svc.execute(compiled);
        final PathExpr root = (PathExpr) compiled;

        final Union union = findUnion(root);
        assertNotNull("After optimization, the parens form must contain a Union -- "
                + "distribution did not fire. AST: " + ExpressionDumperHelper.dump(root), union);

        // Distribution must produce one branch per original union arm
        // (binary case here: 2 branches).
        final java.util.List<PathExpr> branches = flattenUnionBranches(union);
        assertEquals("Distributed Union should have 2 branches (one per original union arm). AST: "
                + ExpressionDumperHelper.dump(union), 2, branches.size());

        // Each branch must contain the original element name (book or journal).
        // The branch's last LocationStep is whichever was the original union
        // arm; assert the qnames cover the expected set so we catch regressions
        // where distribution drops or duplicates branches.
        final java.util.Set<String> branchNames = new java.util.HashSet<>();
        for (final PathExpr branch : branches) {
            for (int i = 0; i < branch.getLength(); i++) {
                final Expression step = branch.getExpression(i);
                final LocationStep ls;
                if (step instanceof final LocationStep loc) {
                    ls = loc;
                } else if (step instanceof final ExtensionExpression ext
                        && ext.getExpression() instanceof final LocationStep loc) {
                    ls = loc;
                } else {
                    continue;
                }
                if (ls.getTest().getName() != null) {
                    branchNames.add(ls.getTest().getName().getLocalPart());
                }
            }
        }
        assertTrue("Distributed branch must reference 'book': " + branchNames
                        + " | AST: " + ExpressionDumperHelper.dump(union),
                branchNames.contains("book"));
        assertTrue("Distributed branch must reference 'journal': " + branchNames
                        + " | AST: " + ExpressionDumperHelper.dump(union),
                branchNames.contains("journal"));

        // Each distributed branch must include the outer prefix steps (the
        // VarRef $d and the descendant-or-self step from `//`). A regression
        // in distributeBranch that drops the prefix would still produce a
        // Union but the branches would only have the original union arm,
        // and the result set would change.
        for (final PathExpr branch : branches) {
            assertTrue("Distributed branch is missing outer prefix steps "
                            + "(expected at least 2 steps: VarRef + LocationStep + the branch arm). "
                            + "Branch length=" + branch.getLength()
                            + " | AST: " + ExpressionDumperHelper.dump(branch),
                    branch.getLength() >= 2);
            assertTrue("First step of distributed branch must be the outer VarRef $d "
                            + "or a fused descendant LocationStep -- not the inner union arm directly. "
                            + "Branch AST: " + ExpressionDumperHelper.dump(branch),
                    branch.getExpression(0) instanceof VariableReference
                            || branch.getExpression(0) instanceof LocationStep);
        }
    }

    /**
     * Walk an expression tree returning the first Union found, or null if
     * none. Uses DefaultExpressionVisitor's visitor-driven descent so
     * wrapper types (LetExpr, FilteredExpression, ...) are handled by their
     * canonical visit methods rather than the inherited getSubExpression
     * default, which returns 0 for many of them.
     */
    private static Union findUnion(final Expression e) {
        if (e == null) {
            return null;
        }
        final Union[] found = new Union[1];
        e.accept(new DefaultExpressionVisitor() {
            @Override
            public void visitUnionExpr(final Union union) {
                if (found[0] == null) {
                    found[0] = union;
                }
            }
        });
        return found[0];
    }

    private static java.util.List<PathExpr> flattenUnionBranches(final Union u) {
        final java.util.List<PathExpr> out = new java.util.ArrayList<>();
        flattenBranchInto(u.getLeft(), out);
        flattenBranchInto(u.getRight(), out);
        return out;
    }

    private static void flattenBranchInto(final PathExpr branch, final java.util.List<PathExpr> out) {
        if (branch.getLength() == 1 && branch.getExpression(0) instanceof final Union nested) {
            flattenBranchInto(nested.getLeft(), out);
            flattenBranchInto(nested.getRight(), out);
            return;
        }
        out.add(branch);
    }

    /**
     * Wrapper to keep the test file's static imports tidy. Calls the
     * {@link org.exist.xquery.util.ExpressionDumper#dump(Expression)} helper.
     */
    private static final class ExpressionDumperHelper {
        static String dump(final Expression e) {
            return org.exist.xquery.util.ExpressionDumper.dump(e);
        }
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
