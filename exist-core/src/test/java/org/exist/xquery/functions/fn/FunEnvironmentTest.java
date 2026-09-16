/*
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This file was originally ported from FusionDB to eXist-db by
 * Evolved Binary, for the benefit of the eXist-db Open Source community.
 * Only the ported code as it appears in this file, at the time that
 * it was contributed to eXist-db, was re-licensed under The GNU
 * Lesser General Public License v2.1 only for use in eXist-db.
 *
 * This license grant applies only to a snapshot of the code as it
 * appeared when ported, it does not offer or infer any rights to either
 * updates of this source code or access to the original source code.
 *
 * The GNU Lesser General Public License v2.1 only license follows.
 *
 * ---------------------------------------------------------------------
 *
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; version 2.1.
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

import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.DatabaseConfigurationException;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class FunEnvironmentTest {

    @Parameterized.Parameters(name = "{0}")
    public static java.util.Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { "non-secure", null, false },
                { "secure", "conf-env-vars-admins-only.xml", true }
        });
    }

    @Parameterized.Parameter(value = 0)
    public String testTypeName;

    @Parameterized.Parameter(value = 1)
    public String confFileName;

    @Parameterized.Parameter(value = 2)
    public boolean shouldReturnEmptySequence;

    private ExistEmbeddedServer existEmbeddedServer = null;

    @Before
    public void setup() throws URISyntaxException, DatabaseConfigurationException, EXistException, IOException {
        if (confFileName == null) {
            existEmbeddedServer = new ExistEmbeddedServer(true, true);
        } else {
            final Path confFile = Paths.get(getClass().getResource(confFileName).toURI());
            existEmbeddedServer = new ExistEmbeddedServer(null, confFile, null, true, true);
        }
        existEmbeddedServer.startDb();
    }

    @After
    public void teardown() {
        if (existEmbeddedServer != null) {
            existEmbeddedServer.stopDb();
        }
        existEmbeddedServer = null;
    }

    @Test
    public void availableEnvironmentVariables() throws EXistException, XPathException, PermissionDeniedException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final XQuery xqueryService = pool.getXQueryService();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            final String query = "fn:available-environment-variables()";
            final Sequence result = xqueryService.execute(broker, query, null);

            if (shouldReturnEmptySequence) {
                assertTrue(result.isEmpty());
            } else {
                assertFalse(result.isEmpty());
            }
        }
    }

    @Test
    public void environmentVariable() throws EXistException, XPathException, PermissionDeniedException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final XQuery xqueryService = pool.getXQueryService();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            final String query;
            if (isWindows()) {
                query = "fn:environment-variable('Path')";
            } else {
                query = "fn:environment-variable('PATH')";
            }

            final Sequence result = xqueryService.execute(broker, query, null);

            if (shouldReturnEmptySequence) {
                assertTrue(result.isEmpty());
            } else {
                assertFalse(result.isEmpty());
            }
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().startsWith("win");
    }
}
