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

import org.junit.Test;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The CI schema-governance workflow (.github/workflows/ci-schema-checks.yml)
 * enforces that {@code xs:schema/@version} is bumped whenever a native XSD or
 * its canonical template changes — but it diffs files via git path filters
 * and has no visibility into Java source, so a forgotten update to the
 * hand-copied constants in {@link SchemaVersion} would otherwise drift
 * silently. This test closes that gap directly: it runs on every {@code mvn
 * test}, independent of which files a PR happens to touch, and fails loudly
 * the moment a constant disagrees with its paired XSD.
 */
public class SchemaVersionSyncTest {

    private static final Map<String, String> SCHEMA_FILE_TO_CONSTANT = new LinkedHashMap<>();
    static {
        SCHEMA_FILE_TO_CONSTANT.put("conf.xsd", SchemaVersion.CONF);
        SCHEMA_FILE_TO_CONSTANT.put("collection.xconf.xsd", SchemaVersion.COLLECTION_XCONF);
        SCHEMA_FILE_TO_CONSTANT.put("descriptor.xsd", SchemaVersion.DESCRIPTOR);
        SCHEMA_FILE_TO_CONSTANT.put("mime-types.xsd", SchemaVersion.MIME_TYPES);
        SCHEMA_FILE_TO_CONSTANT.put("controller-config.xsd", SchemaVersion.CONTROLLER_CONFIG);
    }

    @Test
    public void schemaVersionConstantsMatchXsds() throws Exception {
        final Path schemaDir = resolveSchemaDir();
        assertTrue("schema/ directory not found at " + schemaDir + " (run from repo root: mvn test -pl exist-core)",
                Files.isDirectory(schemaDir));

        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);

        for (final Map.Entry<String, String> entry : SCHEMA_FILE_TO_CONSTANT.entrySet()) {
            final String fileName = entry.getKey();
            final Path xsdPath = schemaDir.resolve(fileName);
            assertTrue("Missing XSD: " + xsdPath, Files.exists(xsdPath));

            final Document doc = factory.newDocumentBuilder().parse(xsdPath.toFile());
            final String xsdVersion = doc.getDocumentElement().getAttribute("version");

            assertEquals("SchemaVersion.java is out of sync with schema/" + fileName
                            + " — update the matching constant in SchemaVersion.java"
                            + " whenever you bump xs:schema/@version",
                    xsdVersion, entry.getValue());
        }
    }

    private Path resolveSchemaDir() {
        final Path base = Path.of(System.getProperty("user.dir"));
        Path p = base.resolve("schema");
        if (!Files.isDirectory(p)) {
            p = base.getParent().resolve("schema");
        }
        return p;
    }
}
