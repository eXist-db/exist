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

import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * How a predicate chooses between a node selection, a position and a boolean, and how a where clause is evaluated in one
 * step for all the items of its for clause, when the same expression is evaluated again over other input. Each query
 * is also run with the optimizer off, which gives the answer the optimized one must equal.
 */
public class PredicateAndWhereClauseEvaluationTest {

    @RegisterExtension
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static final String DOCUMENT = "<doc><div cat=\"zebra\" n=\"1\"><t>a</t></div><div cat=\"pony\" n=\"2\"/>"
            + "<div cat=\"zebra\" n=\"3\"><t>c</t></div></doc>";

    private static final String DIVS = "collection('/db/pw')//div";

    @BeforeAll
    static void store() throws Exception {
        execute("xmldb:create-collection('/db', 'pw'), xmldb:store('/db/pw', 'doc.xml', " + DOCUMENT + ")");
    }

    @AfterAll
    static void remove() throws Exception {
        execute("xmldb:remove('/db/pw')");
    }

    private static String execute(final String query) throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = pool.getXQueryService();
            final CompiledXQuery compiled = xquery.compile(new XQueryContext(pool), query);
            final var result = xquery.execute(broker, compiled, null);
            final StringBuilder out = new StringBuilder();
            for (int i = 0; i < result.getItemCount(); i++) {
                out.append(i == 0 ? "" : " ").append(result.itemAt(i).getStringValue());
            }
            return out.toString();
        }
    }

    /** The answer with the optimizer on, which has to be the one with the optimizer off. */
    private static String answer(final String query) throws Exception {
        final String off = execute("declare option exist:optimize 'enable=no'; " + query);
        final String on = execute(query);
        assertEquals(off, on, "optimizer on and off");
        return on;
    }

    /** A predicate whose value is a set of stored nodes keeps every context item if there are any, and none if not. */
    @Test
    void predicateThatIsAStoredNodeSet() throws Exception {
        assertEquals("3", answer("count(" + DIVS + "[" + DIVS + "[@cat = 'zebra']])"));
        assertEquals("0", answer("count(" + DIVS + "[collection('/db/pw')//nothing])"));
        assertEquals("3", answer("count(" + DIVS + "[if (true()) then " + DIVS + " else ()])"));
    }

    /** A predicate that is a node, over a context that is in memory or atomic, is a boolean. */
    @Test
    void predicateThatIsANodeOverAContextThatIsNotStored() throws Exception {
        assertEquals("2", answer("count(<r><i/><i/></r>/i[<a/>])"));
        assertEquals("3", answer("count((1, 2, 3)[<a/>])"));
        assertEquals("2", answer("count(('a', 'b')[<a/>])"));
    }

    /** A predicate whose value is a number is a position, whether the static type says so or only the value. */
    @Test
    void predicateThatIsAPosition() throws Exception {
        assertEquals("20", answer("string-join((10, 20, 30)[max((1, 2))], ',')"));
        assertEquals("30", answer("string-join((10, 20, 30)[last()], ',')"));
        assertEquals("20", answer("string-join((10, 20, 30)[position() = 2], ',')"));
        assertEquals("1", answer("count(" + DIVS + "[if (true()) then 2 else 'x'])"));
        assertEquals("1", answer("count(" + DIVS + "[@cat = 'zebra'][2])"));
        assertEquals("", answer("string-join((10, 20, 30)[()], ',')"));
    }

    @Test
    void predicateThatIsABooleanOverAtomicValues() throws Exception {
        assertEquals("20,30", answer("string-join((10, 20, 30)[. > 15], ',')"));
    }

    @Test
    void predicateThatIsSeveralNumbersIsAnError() {
        final var e = assertThrows(XPathException.class, () -> execute("(10, 20, 30)[(1, 2)]"));
        assertEquals(ErrorCodes.FORG0006, e.getErrorCode());
    }

    /** A where clause that navigates from the for variable is evaluated once for all the items. */
    @Test
    void whereClauseThatNavigatesFromTheVariable() throws Exception {
        assertEquals("3", answer("count(for $d in collection('/db/pw')/doc/div where $d/@cat return $d)"));
        assertEquals("2", answer("count(for $d in collection('/db/pw')/doc/div where $d/t return $d)"));
        assertEquals("2", answer("count(for $d in collection('/db/pw')/doc/div where $d/@cat = 'zebra' return $d)"));
        assertEquals("2", answer("count(for $d in collection('/db/pw')/doc/div where $d/t and $d/@cat = 'zebra' return $d)"));
    }

    @Test
    void whereClauseOverNothing() throws Exception {
        assertEquals("0", answer("count(for $d in collection('/db/pw')/doc/nothing where $d/@cat return $d)"));
    }

    /** A clause between the for and the where changes what the where can be evaluated for in one step. */
    @Test
    void whereClauseAfterOtherClauses() throws Exception {
        assertEquals("2", answer("count(for $d in collection('/db/pw')/doc/div let $x := 1 where $d/t return $d)"));
        assertEquals("1,3", answer("string-join(for $d at $p in collection('/db/pw')/doc/div where $d/t return string($p), ',')"));
        assertEquals("2", answer("count(for $d in collection('/db/pw')/doc/div order by $d/@n descending where $d/t return $d)"));
        assertEquals("4", answer("count(for $a in collection('/db/pw')/doc/div for $b in (1, 2) where $a/t return $a)"));
    }

    /** A where clause that uses the variable as a value is evaluated once per item. */
    @Test
    void whereClauseThatUsesTheVariableAsAValue() throws Exception {
        assertEquals("2", answer("count(for $d in collection('/db/pw')/doc/div where exists($d[t]) return $d)"));
        assertEquals("3", answer("count(for $d in collection('/db/pw')/doc/div where string($d/@n) = string($d/@n) return $d)"));
    }

    @Test
    void whereClauseOverNodesInMemory() throws Exception {
        assertEquals("1", answer("count(for $d in <r><i n=\"1\"/><i/></r>/i where $d/@n return $d)"));
    }

    /** The same where clause again, over input of another kind, in the same query. */
    @Test
    void whereClauseEvaluatedAgainOverOtherInput() throws Exception {
        assertEquals("2|1|2|0", answer("string-join(for $in in (1, 2, 3, 4) return string(count("
                + "for $d in (collection('/db/pw')/doc/div, <r><i n=\"1\"/></r>/i, ())[position() <= (4, 1, 3, 0)[$in]] "
                + "where $d/@cat = 'zebra' return $d)), '|')"));
    }
}
