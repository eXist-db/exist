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

import org.exist.EXistException;
import org.exist.TestUtils;
import org.exist.collections.triggers.TriggerException;
import org.exist.security.PermissionDeniedException;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.LockException;
import org.exist.xmldb.IndexQueryService;
import org.exist.xmldb.XmldbURI;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XQueryService;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 * Regression test for GH-3918: a compound predicate of the form
 * {@code path[indexed-pred][contains(literal, literal)]} runs 10-10,000x
 * slower than the same path without the constant {@code contains(...)}
 * predicate. The {@code (# exist:no-index #)} workaround restores normal
 * speed, proving the slowdown comes from the optimizer wrapping the
 * location step in {@code (# exist:optimize #)} and running an index
 * pre-select for a query whose result is statically empty.
 *
 * <p>The fix in {@link Optimizer} skips the optimize-pragma wrap when one
 * predicate is structurally context-free and folds to effective-boolean
 * false at compile time. The tests below assert correctness for several
 * shapes of constant predicate; the wrap-skip behavior itself is asserted
 * indirectly by comparing optimized and {@code (# exist:no-index #)}
 * results, which must match.
 *
 * @see <a href="https://github.com/eXist-db/exist/issues/3918">GH-3918</a>
 */
public class ContextFreePredicateRegressionTest {

    private static final String COLLECTION_CONFIG =
            """
            <collection xmlns="http://exist-db.org/collection-config/1.0">
                <index>
                    <create qname="bar" type="xs:string"/>
                </index>
            </collection>
            """;

    private static Collection testCollection;

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer =
            new ExistEmbeddedServer(true, true);

    @BeforeClass
    public static void initDatabase() throws ClassNotFoundException, IllegalAccessException,
            InstantiationException, XMLDBException {
        final Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
        final Database database = (Database) cl.newInstance();
        database.setProperty("create-database", "true");
        DatabaseManager.registerDatabase(database);

        final Collection root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, "admin", "");
        final CollectionManagementService service = root.getService(CollectionManagementService.class);
        testCollection = service.createCollection("issue-3918");
        Assert.assertNotNull(testCollection);

        final IndexQueryService idxConf = testCollection.getService(IndexQueryService.class);
        idxConf.configureCollection(COLLECTION_CONFIG);

        // Small corpus is sufficient: the assertions are correctness-shaped
        // and the fix behavior is verifiable on a single-document index.
        final XQueryService xqs = testCollection.getService(XQueryService.class);
        xqs.query(
                "let $doc := <root>{(1 to 100) ! <foo><bar>{.}</bar></foo>}</root> "
                + "return xmldb:store('" + testCollection.getName() + "', 'test.xml', $doc)");
    }

    @AfterClass
    public static void cleanupDb() throws LockException, TriggerException,
            PermissionDeniedException, EXistException, IOException {
        TestUtils.cleanupDB();
    }

    private ResourceSet execute(final String query) throws XMLDBException {
        final XQueryService xqs = testCollection.getService(XQueryService.class);
        return xqs.query(query);
    }

    /**
     * The exact shape from the issue reproducer must return zero results.
     * Pre-fix this query took 10-10,000x longer than the same query with
     * either predicate removed; correctness was preserved but the wrap's
     * index pre-select dominated query time.
     */
    @Test
    public void compoundPredicateWithConstantContainsReturnsEmpty() throws XMLDBException {
        final ResourceSet result = execute(
                "//foo[bar = '999'][contains('abc', '123')]");
        assertEquals(0, result.getSize());
    }

    /**
     * The {@code (# exist:no-index #)} workaround referenced in the issue
     * report must produce the same result as the optimized query. Any
     * divergence indicates the wrap-skip gate has changed observable
     * semantics, not just performance.
     */
    @Test
    public void noIndexPragmaReturnsSameResult() throws XMLDBException {
        final ResourceSet wrapped = execute(
                "//foo[bar = '999'][contains('abc', '123')]");
        final ResourceSet noIndex = execute(
                "(# exist:no-index #) { //foo[bar = '999'][contains('abc', '123')] }");
        assertEquals(0, wrapped.getSize());
        assertEquals(wrapped.getSize(), noIndex.getSize());
    }

    /**
     * A bare {@code [false()]} predicate must also short-circuit the wrap.
     * This is the simplest constant-false shape and exercises the visitor's
     * builtin-function-with-no-args branch.
     */
    @Test
    public void constantFalsePredicateReturnsEmpty() throws XMLDBException {
        final ResourceSet result = execute("//foo[bar = '5'][false()]");
        assertEquals(0, result.getSize());
    }

    /**
     * A compile-time-false comparison ({@code 1 = 0}) must short-circuit
     * the wrap via the operator-recursion path in
     * {@code ContextFreeChecker}.
     */
    @Test
    public void constantFalseComparisonReturnsEmpty() throws XMLDBException {
        final ResourceSet result = execute("//foo[bar = '5'][1 = 0]");
        assertEquals(0, result.getSize());
    }

    /**
     * Constant-true predicates must be left alone — the wrap is still
     * useful for the indexed predicate. This guards against an over-eager
     * gate that strips the wrap whenever a predicate is context-free.
     */
    @Test
    public void constantTruePredicateDoesNotInterfereWithIndex() throws XMLDBException {
        // [true()] is a no-op; the indexed predicate still selects one <foo>.
        final ResourceSet result = execute("//foo[bar = '5'][true()]");
        assertEquals(1, result.getSize());
    }

    /**
     * The single-indexed-predicate baseline: the gate must not strip the
     * wrap when there is no constant predicate. This is a correctness-only
     * assertion; the perf benefit of the wrap is exercised by other
     * optimizer tests.
     */
    @Test
    public void singleIndexedPredicateStillReturnsExpectedResults() throws XMLDBException {
        final ResourceSet result = execute("//foo[bar = '42']");
        assertEquals(1, result.getSize());
    }

    /**
     * A predicate that references a variable is structurally context-
     * dependent and the gate must NOT fire. Even when the variable is
     * known statically to make the predicate false, evaluating the
     * predicate at compile time would change semantics if the variable's
     * value changed at runtime (e.g., via a let-binding the optimizer
     * cannot inline).
     */
    @Test
    public void predicateReferencingVariableDoesNotTriggerGate() throws XMLDBException {
        final ResourceSet result = execute(
                "let $needle := 'no-match-anywhere' "
                        + "return //foo[bar = '5'][contains($needle, 'x')]");
        assertEquals(0, result.getSize());
    }
}
