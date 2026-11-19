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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression test for <a href="https://github.com/eXist-db/exist/issues/7058">GH-7058</a>:
 * an {@code (#exist:optimize#)} pragma over a let-bound sequence of stored nodes must not change
 * later evaluations once it has been evaluated over an empty sequence.
 *
 * <p>Over an empty sequence, every {@link Optimizable} trivially reports that it can optimize all
 * of its (zero) items, so the pragma used to take the optimized branch and pre-select. That left
 * an empty {@code preselectResult} in {@link GeneralComparison}, which then answered every later
 * evaluation of the comparison with an empty sequence. Since GH-6759 the optimizer attaches the
 * pragma to filtered function arguments like {@code count($d[pred])} by itself.
 */
class OptimizeEmptyContextTest {

    @RegisterExtension
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    private static final String DOC = "/db/optimize-empty-context.xml";

    private static final String MODELS = "for $model in doc('" + DOC + "')//model let $d := $model/param ";

    @BeforeAll
    static void storeDocument() throws XMLDBException {
        existEmbeddedServer.executeQuery("""
                xmldb:store('/db', 'optimize-empty-context.xml',
                    <m>
                        <model/>
                        <model><param name='content'/></model>
                        <model><param name='x'/></model>
                        <model><param name='content'/></model>
                    </m>)""");
    }

    private static String run(final String query) throws XMLDBException {
        return run("", query);
    }

    private static String run(final String prolog, final String query) throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(prolog + "string-join(" + query + ", ' ')");
        return (String) result.getResource(0).getContent();
    }

    @Test
    void countAfterEmptySequence() throws XMLDBException {
        assertEquals("0 1 0 1", run(MODELS + "return string(count($d[@name = 'content']))"));
    }

    @Test
    void emptyAfterEmptySequence() throws XMLDBException {
        assertEquals("true false true false", run(MODELS + "return string(empty($d[@name = 'content']))"));
    }

    @Test
    void explicitPragmaAfterEmptySequence() throws XMLDBException {
        assertEquals("0 1 0 1", run(MODELS + "return string(count((# exist:optimize #) { $d[@name = 'content'] }))"));
    }

    @Test
    void optimizerDisabled() throws XMLDBException {
        assertEquals("0 1 0 1", run("declare option exist:optimize 'enable=no';\n", MODELS + "return string(count($d[@name = 'content']))"));
    }
}
