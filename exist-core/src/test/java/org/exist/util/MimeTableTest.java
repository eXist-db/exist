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

import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test case for mime-type mapping.
 * Tests the distribution edition of mime-types.xml
 * as well as variants that exploit the default mime type feature
 * 
 * @author Peter Ciuffetti
 */
@Execution(ExecutionMode.CONCURRENT)
class MimeTableTest  {

    /**
     * This test checks the behavior of MimeTable.java
     * with respect to the distribution version of mime-types.xml.
     * The distribution version of mime-types.xml does not use the
     * default mime type capability.
     */
    @org.junit.jupiter.api.Test
    void testDistributionVersionOfMimeTypesXml() throws URISyntaxException {
		final Path mimeTypes = Path.of(getClass().getResource("mime-types.xml").toURI());

		MimeTable mimeTable = new MimeTable(mimeTypes);
		assertNotNull(mimeTable, "Mime table not found");

		MimeType mt;

		mt = mimeTable.getContentTypeFor("test.xml");
		assertNotNull(mt, "Mime type not found for test.xml");
		assertEquals("application/xml", mt.getName(), "Incorrect mime type");
		assertEquals(MimeType.XML, mt.getType(), "Incorrect resource type");

		mt = mimeTable.getContentTypeFor("test.html");
		assertNotNull(mt, "Mime type not found for test.html");
		assertEquals("text/html", mt.getName(), "Incorrect mime type");
		assertEquals(MimeType.XML, mt.getType(), "Incorrect resource type");

		mt = mimeTable.getContentTypeFor("test.jpg");
		assertNotNull(mt, "Mime type not found for test.jpg");
		assertEquals("image/jpeg", mt.getName(), "Incorrect mime type");
		assertEquals(MimeType.BINARY, mt.getType(), "Incorrect resource type");

		mt = mimeTable.getContentTypeFor("foo");
		assertNull(mt, "Should return null mime type for file without extension");

		mt = mimeTable.getContentTypeFor("foo.bar");
		assertNull(mt, "Should return null mime type for file with extension not configured in mime-types.xml");
	}

    /**
     * This test checks the behavior of the mime-types@default-resource-type attribute
     * The test config assigns all resources to application/xml
     */
    @org.junit.jupiter.api.Test
    void testWithDefaultResourceTypeFeature() throws URISyntaxException {
		final Path mimeTypes = Path.of(getClass().getResource("mime-types-xml-default.xml").toURI());

		MimeTable mimeTable = new MimeTable(mimeTypes);
		assertNotNull(mimeTable, "Mime table not found");

		MimeType mt;

		mt = mimeTable.getContentTypeFor("test.xml");
		assertNotNull(mt, "Mime type not found for test.xml");
		assertEquals("application/xml", mt.getName(), "Incorrect mime type");
		assertEquals(MimeType.XML, mt.getType(), "Incorrect resource type");

		mt = mimeTable.getContentTypeFor("test.html");
		assertNotNull(mt, "Mime type not found for test.html");
		assertEquals("application/xml", mt.getName(), "Incorrect mime type");
		assertEquals(MimeType.XML, mt.getType(), "Incorrect resource type");

		mt = mimeTable.getContentTypeFor("test.jpg");
		assertNotNull(mt, "Mime type not found for test.jpg");
		assertEquals("application/xml", mt.getName(), "Incorrect mime type");
		assertEquals(MimeType.XML, mt.getType(), "Incorrect resource type");

		mt = mimeTable.getContentTypeFor("foo");
		assertNotNull(mt, "Mime type not found for foo");
		assertEquals("application/xml", mt.getName(), "Incorrect mime type");
		assertEquals(MimeType.XML, mt.getType(), "Incorrect resource type");

		mt = mimeTable.getContentTypeFor("foo.bar");
		assertNotNull(mt, "Mime type not found for test.jpg");
		assertEquals("application/xml", mt.getName(), "Incorrect mime type");
		assertEquals(MimeType.XML, mt.getType(), "Incorrect resource type");
	}

    /**
     * This test checks the behavior of the mime-types@default-mime-type attribute
     * The test config assigns all resources to foo/bar (BINARY)
     */
    @org.junit.jupiter.api.Test
    void testWithDefaultMimeTypeFeature() throws URISyntaxException {
		final Path mimeTypes = Path.of(getClass().getResource("mime-types-foo-default.xml").toURI());

		MimeTable mimeTable = new MimeTable(mimeTypes);
		assertNotNull(mimeTable, "Mime table not found");

		MimeType mt;

		mt = mimeTable.getContentTypeFor("test.xml");
		assertNotNull(mt, "Mime type not found for test.xml");
		assertEquals("foo/bar", mt.getName(), "Incorrect mime type");
		assertEquals(MimeType.BINARY, mt.getType(), "Incorrect resource type");

		mt = mimeTable.getContentTypeFor("test.html");
		assertNotNull(mt, "Mime type not found for test.html");
		assertEquals("foo/bar", mt.getName(), "Incorrect mime type");
		assertEquals(MimeType.BINARY, mt.getType(), "Incorrect resource type");

		mt = mimeTable.getContentTypeFor("test.jpg");
		assertNotNull(mt, "Mime type not found for test.jpg");
		assertEquals("foo/bar", mt.getName(), "Incorrect mime type");
		assertEquals(MimeType.BINARY, mt.getType(), "Incorrect resource type");

		mt = mimeTable.getContentTypeFor("foo");
		assertNotNull(mt, "Mime type not found for foo");
		assertEquals("foo/bar", mt.getName(), "Incorrect mime type");
		assertEquals(MimeType.BINARY, mt.getType(), "Incorrect resource type");

		mt = mimeTable.getContentTypeFor("foo.bar");
		assertNotNull(mt, "Mime type not found for test.jpg");
		assertEquals("foo/bar", mt.getName(), "Incorrect mime type");
		assertEquals(MimeType.BINARY, mt.getType(), "Incorrect resource type");
	}

    @org.junit.jupiter.api.Test
    void testClasspathDefaultIncludesApplicationXquery() {
		final MimeTable mimeTable = new MimeTable();
		final MimeType xquery = mimeTable.getContentType("application/xquery");
		assertNotNull(xquery, "application/xquery must be registered in the default mime-types.xml");
		assertEquals("application/xquery", xquery.getName());
	}

    @org.junit.jupiter.api.Test
    void testUnreadablePathThrows() {
		final Path missing = Path.of("/nonexistent/mime-types-does-not-exist.xml");
		final IllegalStateException ex = assertThrows(IllegalStateException.class, () -> new MimeTable(missing));
		assertTrue(ex.getMessage().contains("not readable"));
	}

    @org.junit.jupiter.api.Test
    void testInvalidXmlThrows() throws Exception {
		final Path broken = Files.createTempFile("mime-types-broken", ".xml");
		try {
			Files.writeString(broken, "<not-valid-xml");
			final IllegalStateException ex = assertThrows(IllegalStateException.class, () -> new MimeTable(broken));
			assertTrue(ex.getMessage().contains("Failed to load mime-type table"));
		} finally {
			Files.deleteIfExists(broken);
		}
	}
}
