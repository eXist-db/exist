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
package org.exist.validation;

import org.junit.Test;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;

/**
 * Regression test for GitHub #6000: range index field @type should be optional in XSD.
 * <p>
 * The schema change (typeReq -&gt; typeOpt for newRangeIndexFieldType) allows configs
 * where field lacks @type; Java defaults to xs:string. This test validates a minimal
 * collection.xconf with such a field against the XSD.
 * </p>
 * <p>
 * Without the fix (develop): schema has typeReq, validation fails.
 * With the fix: schema has typeOpt, validation passes.
 * </p>
 *
 * @see <a href="https://github.com/eXist-db/exist/issues/6000">#6000</a>
 */
public class CollectionConfigSchema6000Test {

    /**
     * Minimal collection.xconf with range field lacking @type.
     * Schema (with typeOpt) must accept this; with typeReq it fails.
     */
    private static final String CONFIG_FIELD_WITHOUT_TYPE = """
        <collection xmlns="http://exist-db.org/collection-config/1.0">
            <index>
                <range>
                    <create qname="item">
                        <field name="val" match="val"/>
                    </create>
                </range>
            </index>
        </collection>
        """;

    @Test
    public void xsdAcceptsFieldWithoutType() throws Exception {
        final Path schemaPath = resolveSchemaPath();
        assertTrue("Schema not found at " + schemaPath + " (run from repo root: mvn test -pl exist-core)",
            Files.exists(schemaPath));

        /* Schema uses vc:minVersion="1.1"; Xerces XSD 1.1 required */
        final SchemaFactory factory = SchemaFactory.newInstance("http://www.w3.org/XML/XMLSchema/v1.1");
        final Schema schema = factory.newSchema(schemaPath.toFile());
        final Validator validator = schema.newValidator();

        final Path tmpFile = Files.createTempFile("collection-config-6000-", ".xml");
        try {
            Files.writeString(tmpFile, CONFIG_FIELD_WITHOUT_TYPE);
            final Source src = new StreamSource(tmpFile.toFile());
            validator.validate(src);
        } catch (org.xml.sax.SAXException e) {
            fail("Schema should accept field without @type (GitHub #6000). Validation error: " + e.getMessage());
        } finally {
            Files.deleteIfExists(tmpFile);
        }
    }

    private Path resolveSchemaPath() {
        final Path base = Path.of(System.getProperty("user.dir"));
        Path p = base.resolve("schema").resolve("collection.xconf.xsd");
        if (!Files.exists(p)) {
            p = base.getParent().resolve("schema").resolve("collection.xconf.xsd");
        }
        return p;
    }
}
