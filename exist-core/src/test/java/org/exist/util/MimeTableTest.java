package org.exist.util;

import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

import com.googlecode.junittoolbox.ParallelRunner;
import org.junit.*;
import org.junit.runner.RunWith;

/**
 * Test case for mime-type mapping.
 * Tests the distribution edition of mime-types.xml
 * as well as variants that exploit the default mime type feature
 * 
 * @author Peter Ciuffetti
 */
@RunWith(ParallelRunner.class)
public class MimeTableTest  {

	/**
	 * This test checks the behavior of MimeTable.java
	 * with respect to the distribution version of mime-types.xml.
	 * The distribution version of mime-types.xml does not use the
	 * default mime type capability.
	 */
    @Test
	public void testDistributionVersionOfMimeTypesXml() throws URISyntaxException {
		final Path mimeTypes = Paths.get(getClass().getResource("mime-types.xml").toURI());

		MimeTable mimeTable = new MimeTable(mimeTypes);
		assertNotNull("Mime table not found", mimeTable);

		MimeType mt;

		mt = mimeTable.getContentTypeFor("test.xml");
		assertNotNull("Mime type not found for test.xml", mt);
		assertEquals("Incorrect mime type", "application/xml", mt.getName());
		assertEquals("Incorrect resource type", MimeType.XML, mt.getType());

		mt = mimeTable.getContentTypeFor("test.html");
		assertNotNull("Mime type not found for test.html", mt);
		assertEquals("Incorrect mime type", "text/html", mt.getName());
		assertEquals("Incorrect resource type", MimeType.XML, mt.getType());

		mt = mimeTable.getContentTypeFor("test.jpg");
		assertNotNull("Mime type not found for test.jpg", mt);
		assertEquals("Incorrect mime type", "image/jpeg", mt.getName());
		assertEquals("Incorrect resource type", MimeType.BINARY, mt.getType());

		mt = mimeTable.getContentTypeFor("foo");
		assertNull("Should return null mime type for file without extension", mt);

		mt = mimeTable.getContentTypeFor("foo.bar");
		assertNull("Should return null mime type for file with extension not configured in mime-types.xml", mt);
	}

	/**
	 * This test checks the behavior of the mime-types@default-resource-type attribute
	 * The test config assigns all resources to application/xml
	 */
    @Test
	public void testWithDefaultResourceTypeFeature() throws URISyntaxException {
		final Path mimeTypes = Paths.get(getClass().getResource("mime-types-xml-default.xml").toURI());

		MimeTable mimeTable = new MimeTable(mimeTypes);
		assertNotNull("Mime table not found", mimeTable);

		MimeType mt;

		mt = mimeTable.getContentTypeFor("test.xml");
		assertNotNull("Mime type not found for test.xml", mt);
		assertEquals("Incorrect mime type", "application/xml", mt.getName());
		assertEquals("Incorrect resource type", MimeType.XML, mt.getType());

		mt = mimeTable.getContentTypeFor("test.html");
		assertNotNull("Mime type not found for test.html", mt);
		assertEquals("Incorrect mime type", "application/xml", mt.getName());
		assertEquals("Incorrect resource type", MimeType.XML, mt.getType());

		mt = mimeTable.getContentTypeFor("test.jpg");
		assertNotNull("Mime type not found for test.jpg", mt);
		assertEquals("Incorrect mime type", "application/xml", mt.getName());
		assertEquals("Incorrect resource type", MimeType.XML, mt.getType());

		mt = mimeTable.getContentTypeFor("foo");
		assertNotNull("Mime type not found for foo", mt);
		assertEquals("Incorrect mime type", "application/xml", mt.getName());
		assertEquals("Incorrect resource type", MimeType.XML, mt.getType());

		mt = mimeTable.getContentTypeFor("foo.bar");
		assertNotNull("Mime type not found for test.jpg", mt);
		assertEquals("Incorrect mime type", "application/xml", mt.getName());
		assertEquals("Incorrect resource type", MimeType.XML, mt.getType());
	}

	/**
	 * This test checks the behavior of the mime-types@default-mime-type attribute
	 * The test config assigns all resources to foo/bar (BINARY)
	 */
    @Test
	public void testWithDefaultMimeTypeFeature() throws URISyntaxException {
		final Path mimeTypes = Paths.get(getClass().getResource("mime-types-foo-default.xml").toURI());

		MimeTable mimeTable = new MimeTable(mimeTypes);
		assertNotNull("Mime table not found", mimeTable);

		MimeType mt;

		mt = mimeTable.getContentTypeFor("test.xml");
		assertNotNull("Mime type not found for test.xml", mt);
		assertEquals("Incorrect mime type", "foo/bar", mt.getName());
		assertEquals("Incorrect resource type", MimeType.BINARY, mt.getType());

		mt = mimeTable.getContentTypeFor("test.html");
		assertNotNull("Mime type not found for test.html", mt);
		assertEquals("Incorrect mime type", "foo/bar", mt.getName());
		assertEquals("Incorrect resource type", MimeType.BINARY, mt.getType());

		mt = mimeTable.getContentTypeFor("test.jpg");
		assertNotNull("Mime type not found for test.jpg", mt);
		assertEquals("Incorrect mime type", "foo/bar", mt.getName());
		assertEquals("Incorrect resource type", MimeType.BINARY, mt.getType());

		mt = mimeTable.getContentTypeFor("foo");
		assertNotNull("Mime type not found for foo", mt);
		assertEquals("Incorrect mime type", "foo/bar", mt.getName());
		assertEquals("Incorrect resource type", MimeType.BINARY, mt.getType());

		mt = mimeTable.getContentTypeFor("foo.bar");
		assertNotNull("Mime type not found for test.jpg", mt);
		assertEquals("Incorrect mime type", "foo/bar", mt.getName());
		assertEquals("Incorrect resource type", MimeType.BINARY, mt.getType());
	}
}
