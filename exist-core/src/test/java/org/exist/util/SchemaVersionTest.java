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
package org.exist.util;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.junit.Test;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.assertEquals;

public class SchemaVersionTest {

    private static final Logger LOG = LogManager.getLogger(SchemaVersionTest.class);

    @Test
    public void attributeNameIsSchemaVersion() {
        assertEquals("schemaVersion", SchemaVersion.ATTRIBUTE);
    }

    @Test
    public void logDocumentVersionAcceptsMatchingValue() throws Exception {
        final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        final var root = doc.createElement("exist");
        root.setAttribute(SchemaVersion.ATTRIBUTE, SchemaVersion.CONF);
        doc.appendChild(root);
        SchemaVersion.logDocumentVersion(LOG, root, SchemaVersion.CONF, "test conf.xml");
    }

    @Test
    public void logDocumentVersionAcceptsMissingAttribute() throws Exception {
        final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        final var root = doc.createElement("exist");
        doc.appendChild(root);
        SchemaVersion.logDocumentVersion(LOG, root, SchemaVersion.CONF, "legacy conf.xml");
    }
}
