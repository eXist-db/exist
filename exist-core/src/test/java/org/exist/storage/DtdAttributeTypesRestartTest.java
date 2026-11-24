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
package org.exist.storage;

import org.exist.storage.journal.Journal;
import org.exist.test.ExistEmbeddedServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The declarations of the internal subset are kept with the document type, so they must be read back from disk.
 */
public class DtdAttributeTypesRestartTest {

    private static final Path DATA_DIR;

    private static final Properties PROPERTIES = new Properties();

    static {
        try {
            DATA_DIR = Files.createTempDirectory("dtd-attribute-types");
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
        PROPERTIES.put(BrokerPool.PROPERTY_DATA_DIR, DATA_DIR);
        PROPERTIES.put(Journal.PROPERTY_RECOVERY_JOURNAL_DIR, DATA_DIR);
    }

    @RegisterExtension
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(PROPERTIES, true, false);

    private static final String XML = "<!DOCTYPE r [<!ATTLIST c id ID #IMPLIED><!ATTLIST c ref IDREF #IMPLIED>]><r><c id='x'/><c id='y' ref='x'/></r>";

    private static String run(final String query) throws Exception {
        return DtdAttributeTypesTestSupport.run(existEmbeddedServer, query);
    }

    private static String content() throws Exception {
        return DtdAttributeTypesTestSupport.content("/db/dtd-restart", "d.xml");
    }

    @Test
    void theDeclarationsAreReadBackAfterARestart() throws Exception {
        run("xmldb:create-collection('/db', 'dtd-restart')");
        run("xmldb:store('/db/dtd-restart', 'd.xml', \"" + XML + "\", 'application/xml')");
        final String before = content();
        assertTrue(before.contains("<!DOCTYPE r [<!ATTLIST c id ID #IMPLIED><!ATTLIST c ref IDREF #IMPLIED>]>"), before);

        existEmbeddedServer.restart(false);

        assertEquals(before, content());
        assertEquals("1", run("count(id('x', doc('/db/dtd-restart/d.xml')))"));
        assertEquals("1", run("count(idref('x', doc('/db/dtd-restart/d.xml')))"));
    }
}
