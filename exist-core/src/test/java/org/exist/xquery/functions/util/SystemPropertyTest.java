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
package org.exist.xquery.functions.util;

import org.exist.EXistException;
import org.exist.ExistSystemProperties;
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
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class SystemPropertyTest {

    @Parameterized.Parameters(name = "{0}")
    public static java.util.Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { "non-secure", null, false },
                { "secure", "conf-sys-props-admins-only.xml", true }
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
    public void availableSystemProperties() throws EXistException, XPathException, PermissionDeniedException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final XQuery xqueryService = pool.getXQueryService();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            final String query = "util:available-system-properties()";
            final Sequence result = xqueryService.execute(broker, query, null);
            assertFalse(result.isEmpty());

            final Set<String> set = new HashSet<>(result.getItemCount());
            for (int i = 0; i < result.getItemCount(); i++) {
                set.add(result.itemAt(i).getStringValue());
            }

            assertTrue(set.contains(ExistSystemProperties.PROP_PRODUCT_VERSION));

            if (shouldReturnEmptySequence) {
                assertFalse(set.contains("os.name"));
            } else {
                assertTrue(set.contains("os.name"));
            }
        }
    }

    @Test
    public void systemProperty() throws EXistException, XPathException, PermissionDeniedException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final XQuery xqueryService = pool.getXQueryService();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            String query = "util:system-property('" + ExistSystemProperties.PROP_PRODUCT_NAME + "')";
            Sequence result = xqueryService.execute(broker, query, null);
            assertEquals(1, result.getItemCount());

            query = "util:system-property('os.name')";
            result = xqueryService.execute(broker, query, null);

            if (shouldReturnEmptySequence) {
                assertTrue(result.isEmpty());
            } else {
                assertFalse(result.isEmpty());
            }
        }
    }
}
