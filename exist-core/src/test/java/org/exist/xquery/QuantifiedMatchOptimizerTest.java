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
import org.exist.xmldb.IndexQueryService;
import org.exist.xquery.util.ExpressionDumper;
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests that {@code some $v in PATH satisfies matches($v, ...)} reaches the range index.
 *
 * <p>{@code fn:matches} takes {@code xs:string?}, so asking "does any of these match?" over a
 * multi-valued path is only expressible, within the specification, by quantifying over it. Until
 * the optimizer recognized that spelling, the conformant form was also the slow one: the
 * quantifier was evaluated item by item while {@code matches(PATH, ...)} -- which the
 * specification does not license for a multi-valued PATH -- got the index.</p>
 *
 * <p>These tests assert the rewrite actually fires, by inspecting the compiled expression for the
 * {@code (# exist:optimize #)} pragma, rather than only comparing result counts. A count
 * comparison cannot tell an optimized run from an unoptimized one, since both return the same
 * answer -- which is the whole point.</p>
 */
public class QuantifiedMatchOptimizerTest {

    private static final String OPTIMIZE = "declare option exist:optimize 'enable=yes'; ";
    private static final String NO_OPTIMIZE = "declare option exist:optimize 'enable=no'; ";
    private static final String SYSTEM_MODULE = "import module namespace system = 'http://exist-db.org/xquery/system'; ";

    private static final String COLLECTION_NAME = "quantified-match-test";
    private static final String DOC_NAME = "fixture.xml";

    /** The marker {@link ExpressionDumper} writes for a pragma; see {@code AbstractPragma.dump}. */
    private static final String OPTIMIZE_PRAGMA_MARKER = "(# exist:optimize";

    /**
     * The second speech carries two speakers, so the direct and quantified spellings are not
     * interchangeable on it -- which is what the conformance question on #59 turns on.
     */
    private static final String FIXTURE_XML = """
            <play>
              <speech><speaker>HAMLET</speaker><line>To be</line></speech>
              <speech><speaker>OPHELIA</speaker><speaker>HORATIO</speaker><line>Together</line></speech>
              <speech><speaker>HAMLET</speaker><line>Or not</line></speech>
              <speech><speaker>CLAUDIUS</speaker><line>My words fly up</line></speech>
            </play>
            """;

    private static final String COLLECTION_CONFIG = """
            <collection xmlns="http://exist-db.org/collection-config/1.0">
              <index>
                <create qname="speaker" type="xs:string"/>
              </index>
            </collection>
            """;

    @ClassRule
    public static final ExistXmldbEmbeddedServer server =
            new ExistXmldbEmbeddedServer(false, true, true);

    @BeforeClass
    public static void loadFixture() throws XMLDBException {
        final Collection root = server.getRoot();
        final CollectionManagementService cms = root.getService(CollectionManagementService.class);
        final Collection coll = cms.createCollection(COLLECTION_NAME);

        final IndexQueryService idxConf = coll.getService(IndexQueryService.class);
        idxConf.configureCollection(COLLECTION_CONFIG);

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

    /** Compiles and runs a query, returning the optimized expression tree as text. */
    private String plan(final String body) throws XMLDBException {
        final XQueryService svc = server.getRoot().getService(XQueryService.class);
        final CompiledExpression compiled = svc.compile(OPTIMIZE + docPrefix() + body);
        // Executing triggers analyze + optimize on the compiled tree.
        svc.execute(compiled);
        return ExpressionDumper.dump((PathExpr) compiled);
    }

    private static String docPrefix() {
        return "let $d := doc('/db/" + COLLECTION_NAME + "/" + DOC_NAME + "') return ";
    }

    private long count(final String body, final boolean optimize) throws XMLDBException {
        final XQueryService svc = server.getRoot().getService(XQueryService.class);
        final ResourceSet rs = svc.query((optimize ? OPTIMIZE : NO_OPTIMIZE) + docPrefix() + body);
        return rs.getSize();
    }

    /**
     * Runs a query with function tracing on and returns how many index uses the trace recorded as
     * {@code OPTIMIZED}, which is what a pre-selection through the optimize pragma records. The
     * plan cannot show this on its own: a pragma in the plan does not mean it found anything to
     * pre-select when the query ran.
     */
    private long optimizedIndexUses(final String body) throws XMLDBException {
        final XQueryService svc = server.getRoot().getService(XQueryService.class);
        svc.query(SYSTEM_MODULE + "system:clear-trace(), system:enable-tracing(true(), false())");
        try {
            svc.query(OPTIMIZE + docPrefix() + body);
            final ResourceSet rs = svc.query(SYSTEM_MODULE
                    + "declare namespace stats = 'http://exist-db.org/xquery/profiling'; "
                    + "count(system:trace()//stats:index[@optimization-level = 'OPTIMIZED'])");
            return Long.parseLong(rs.getResource(0).getContent().toString());
        } finally {
            svc.query(SYSTEM_MODULE + "system:enable-tracing(false())");
        }
    }

    /** The control for {@link #optimizedIndexUses}: the direct spelling is known to pre-select. */
    @Test
    public void theDirectFormReachesTheIndexAtRunTime() throws XMLDBException {
        assertTrue(optimizedIndexUses("$d//speech[matches(speaker, '^CLAUD')]") > 0);
    }

    @Test
    public void theQuantifiedFormReachesTheIndex() throws XMLDBException {
        final String dump = plan("$d//speech[some $s in speaker satisfies matches($s, '^HAM')]");
        assertTrue("some ... satisfies matches(...) should be wrapped in the optimize pragma. Plan:\n" + dump,
                dump.contains(OPTIMIZE_PRAGMA_MARKER));
    }

    /** The pragma in the plan must also pre-select from the index when the query runs. */
    @Test
    public void theQuantifiedFormReachesTheIndexAtRunTime() throws XMLDBException {
        assertTrue(optimizedIndexUses("$d//speech[some $s in speaker satisfies matches($s, '^HAM')]") > 0);
    }

    /** A pattern held in a variable other than the bound one is evaluated outside the quantifier. */
    @Test
    public void aPatternInAnotherVariableReachesTheIndex() throws XMLDBException {
        final String body = "let $p := '^HAM' return $d//speech[some $s in speaker satisfies matches($s, $p)]";
        assertTrue(optimizedIndexUses(body) > 0);
        assertEquals(2, count(body, true));
    }

    /**
     * The pre-selection finds candidate speeches; each is then checked against the bound item
     * itself. A speech whose matching speaker is not the one the binding selects must not qualify.
     */
    @Test
    public void onlyTheBoundItemsAreChecked() throws XMLDBException {
        final String body = "$d//speech[some $s in speaker[1] satisfies matches($s, '^HOR')]";
        assertEquals(count(body, false), count(body, true));
        assertEquals(0, count(body, true));
    }

    /**
     * {@code every} cannot use the index: a lookup returns the nodes that match, and every needs
     * to know about the ones that do not.
     */
    @Test
    public void theEveryFormIsLeftAlone() throws XMLDBException {
        final String dump = plan("$d//speech[every $s in speaker satisfies matches($s, '^HAM')]");
        assertFalse("every ... satisfies must not be optimized through the index. Plan:\n" + dump,
                dump.contains(OPTIMIZE_PRAGMA_MARKER));
        assertEquals(0, optimizedIndexUses("$d//speech[every $s in speaker satisfies matches($s, '^HAM')]"));
    }

    /**
     * Narrowing the candidate set is only sound when the satisfies clause is the match itself. A
     * disjunction could qualify a node through its other branch, which the index lookup never
     * sees.
     */
    @Test
    public void aDisjunctionInSatisfiesIsLeftAlone() throws XMLDBException {
        final String body = "$d//speech[some $s in speaker satisfies (matches($s, '^HAM') or $s = 'CLAUDIUS')]";
        final String dump = plan(body);
        assertFalse("a disjunction in the satisfies clause must not be optimized. Plan:\n" + dump,
                dump.contains(OPTIMIZE_PRAGMA_MARKER));
        assertEquals("and it must still return the right answer", 3, count(body, true));
        assertEquals(0, optimizedIndexUses(body));
    }

    /**
     * The pattern is evaluated once, outside the quantifier, so a pattern that mentions the bound
     * variable cannot be hoisted -- the variable is not in scope there.
     */
    @Test
    public void aPatternReferencingTheBoundVariableIsLeftAlone() throws XMLDBException {
        final String body = "$d//speech[some $s in speaker satisfies matches($s, $s)]";
        final String dump = plan(body);
        assertFalse("a pattern mentioning the bound variable must not be optimized. Plan:\n" + dump,
                dump.contains(OPTIMIZE_PRAGMA_MARKER));
        assertEquals("every speaker matches itself as a pattern", 4, count(body, true));
        assertEquals(0, optimizedIndexUses(body));
    }

    /**
     * The bound variable can also hide inside a nested quantified expression in the pattern, which
     * the expression visitors do not see into.
     */
    @Test
    public void aPatternReferencingTheBoundVariableInANestedQuantifierIsLeftAlone() throws XMLDBException {
        final String body = "$d//speech[some $s in speaker satisfies "
                + "matches($s, if (some $z in (1, 2) satisfies matches($s, '^H')) then '^HAM' else '^CLAUD')]";
        assertEquals(0, optimizedIndexUses(body));
        assertEquals(count(body, false), count(body, true));
        assertEquals(3, count(body, true));
    }

    /** Optimized and unoptimized runs must agree. */
    @Test
    public void optimizedAgreesWithUnoptimized() throws XMLDBException {
        final String body = "$d//speech[some $s in speaker satisfies matches($s, '^HAM')]";
        assertEquals(count(body, false), count(body, true));
        assertEquals(2, count(body, true));
    }

    /** A speech with several speakers is found when any one of them matches. */
    @Test
    public void anyOneOfSeveralBoundItemsMatching() throws XMLDBException {
        final String body = "$d//speech[some $s in speaker satisfies matches($s, '^HOR')]";
        assertEquals(count(body, false), count(body, true));
        assertEquals(1, count(body, true));
    }

    /** The optimization must not change which nodes come back, only how they are found. */
    @Test
    public void theIndexedAndUnindexedSpellingsSelectTheSameSpeeches() throws XMLDBException {
        final String quantified = "$d//speech[some $s in speaker satisfies matches($s, '^CLAUD')]/line/string()";
        final XQueryService svc = server.getRoot().getService(XQueryService.class);
        final ResourceSet rs = svc.query(OPTIMIZE + docPrefix() + quantified);
        assertEquals(1, rs.getSize());
        assertEquals("My words fly up", rs.getResource(0).getContent().toString());
    }
}
