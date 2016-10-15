package org.exist.dom.persistent;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.junit.Test;

public class XMLUtilTest {

	private String thisTestFileRelativePath = "test/src/org/exist/dom/persistent/";
	private String utf8TestFileName = "utf8.xml";
	private String utf16TestFileName =  "utf16.xml";
	
	@Test
	public void testGetXMLDeclWithUTF8() throws IOException {
		String existHome = System.getProperty("exist.home");
		Path existDir = existHome == null ? Paths.get(".") : Paths.get(existHome);
		existDir = existDir.normalize();
		Path thisTestFileDir = existDir.resolve(thisTestFileRelativePath);
		
		Path testFile = thisTestFileDir.resolve(utf8TestFileName);
		

		try(final ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Files.copy(testFile, out);

			String expectedDecl = "<?xml version=\"1.0\"?>";
			String decl = XMLUtil.getXMLDecl(out.toByteArray());
			assertEquals("XML Declaration for the UTF-8 encode example file wasn't resolved properly", expectedDecl, decl);
		}
	}

	@Test
	public void testGetXMLDeclWithUTF16() throws IOException {
		String existHome = System.getProperty("exist.home");
		Path existDir = existHome == null ? Paths.get(".") : Paths.get(existHome);
		existDir = existDir.normalize();
		Path thisTestFileDir = existDir.resolve(thisTestFileRelativePath);
		
		Path testFile = thisTestFileDir.resolve(utf16TestFileName);

		try(final ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Files.copy(testFile, out);

			String expectedDecl = "<?xml version=\"1.0\" encoding=\"UTF-16\" standalone=\"no\"?>";
			String decl = XMLUtil.getXMLDecl(out.toByteArray());
			assertEquals("XML Declaration for the UTF-16 encode example file wasn't resolved properly", expectedDecl, decl);
		}
	}
}
