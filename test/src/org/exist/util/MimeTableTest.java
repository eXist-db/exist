package org.exist.util;

import java.io.File;
import java.lang.reflect.Field;

import static org.junit.Assert.*;
import org.junit.*;

/**
 * Test case for mime-type mapping.
 * Tests the distribution edition of mime-types.xml
 * as well as variants that exploit the default mime type feature
 * 
 * @author Peter Ciuffetti
 */
public class MimeTableTest  {

	@After
	public void tearDown() throws Exception {
		// MimeTable is a singleton
		// We use reflection here to null-out the 'instance' field
		// so subsequent tests that call getInstance() will re-load 
		// the specified mime type config file
		Field field = MimeTable.class.getDeclaredField("instance");
		field.setAccessible(true);
		field.set(MimeTable.getInstance(), null);
	}

	/**
	 * This test checks the behavior of MimeTable.java
	 * with respect to the distribution version of mime-types.xml.
	 * The distribution version of mime-types.xml does not use the
	 * default mime type capability.
	 */
    @Test
	public void testDistributionVersionOfMimeTypesXml() {
		File existDir;
		String existHome = System.getProperty("exist.home");
		existDir = existHome == null ? new File(".") : new File(existHome);

		File file = new File(existDir, "mime-types.xml");

		MimeTable mimeTable = MimeTable.getInstance(file);
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
	public void testWithDefaultResourceTypeFeature() {
		File existDir;
		String existHome = System.getProperty("exist.home");
		existDir = existHome == null ? new File("./test/src/org/exist/util") : new File(existHome+"/test/src/org/exist/util");

		MimeTable mimeTable = MimeTable.getInstance(new File(existDir, "mime-types-xml-default.xml"));
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
	public void testWithDefaultMimeTypeFeature() {
		File existDir;
		String existHome = System.getProperty("exist.home");
		existDir = existHome == null ? new File("./test/src/org/exist/util") : new File(existHome+"/test/src/org/exist/util");

		MimeTable mimeTable = MimeTable.getInstance(new File(existDir, "mime-types-foo-default.xml"));
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
