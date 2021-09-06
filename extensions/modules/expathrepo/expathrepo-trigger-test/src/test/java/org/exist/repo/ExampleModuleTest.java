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
package org.exist.repo;


import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.junit.ClassRule;
import org.junit.Test;
import org.w3c.dom.Document;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.Diff;

import javax.xml.transform.Source;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ExampleModuleTest {

    @ClassRule
    public static ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(false, true);

    @Test
    public void helloWorld() throws XPathException, PermissionDeniedException, EXistException {
        final String query =
                "declare namespace myjmod = \"https://my-organisation.com/exist-db/ns/app/my-java-module\";\n" +
                        "myjmod:hello-world()";
        final Sequence result = executeQuery(query);

        assertTrue(result.hasOne());

        final Source inExpected = Input.fromString("<hello>World</hello>").build();
        final Source inActual = Input.fromDocument((Document) result.itemAt(0)).build();

        final Diff diff = DiffBuilder.compare(inExpected)
                .withTest(inActual)
                .checkForSimilar()
                .build();

        assertFalse(diff.toString(), diff.hasDifferences());
    }

    @Test
    public void sayHello() throws XPathException, PermissionDeniedException, EXistException {
        final String query =
                "declare namespace myjmod = \"https://my-organisation.com/exist-db/ns/app/my-java-module\";\n" +
                        "myjmod:say-hello('Adam')";
        final Sequence result = executeQuery(query);

        assertTrue(result.hasOne());

        final Source inExpected = Input.fromString("<hello>Adam</hello>").build();
        final Source inActual = Input.fromDocument((Document) result.itemAt(0)).build();

        final Diff diff = DiffBuilder.compare(inExpected)
                .withTest(inActual)
                .checkForSimilar()
                .build();

        assertFalse(diff.toString(), diff.hasDifferences());
    }

    @Test
    public void sayHello_noName() throws XPathException, PermissionDeniedException, EXistException {
        final String query =
                "declare namespace myjmod = \"https://my-organisation.com/exist-db/ns/app/my-java-module\";\n" +
                        "myjmod:say-hello(())";
        final Sequence result = executeQuery(query);

        assertTrue(result.hasOne());

        final Source inExpected = Input.fromString("<hello>stranger</hello>").build();
        final Source inActual = Input.fromDocument((Document) result.itemAt(0)).build();

        final Diff diff = DiffBuilder.compare(inExpected)
                .withTest(inActual)
                .checkForSimilar()
                .build();

        assertFalse(diff.toString(), diff.hasDifferences());
    }

    @Test
    public void add() throws XPathException, PermissionDeniedException, EXistException {
        final String query =
                "declare namespace myjmod = \"https://my-organisation.com/exist-db/ns/app/my-java-module\";\n" +
                        "myjmod:add(123, 456)";
        final Sequence result = executeQuery(query);

        assertTrue(result.hasOne());

        assertEquals(579, ((IntegerValue)result.itemAt(0)).getInt());
    }


    private Sequence executeQuery(final String xquery) throws EXistException, PermissionDeniedException, XPathException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final XQuery xqueryService = pool.getXQueryService();

        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            return xqueryService.execute(broker, xquery, null);
        }
    }
}
