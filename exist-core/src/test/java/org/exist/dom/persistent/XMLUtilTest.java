package org.exist.dom.persistent;

import static org.junit.Assert.*;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class XMLUtilTest {

	private static final String utf8TestFileName = "utf8.xml";
	private static final String utf16TestFileName =  "utf16.xml";
	
	@Test
	public void testGetXMLDeclWithUTF8() throws IOException, URISyntaxException {
		final URL testFileUrl = getClass().getResource(utf8TestFileName);
		final Path testFile = Paths.get(testFileUrl.toURI());

		final String expectedDecl = "<?xml version=\"1.0\"?>";
		final String decl = XMLUtil.getXMLDecl(Files.readAllBytes(testFile));
		assertEquals("XML Declaration for the UTF-8 encode example file wasn't resolved properly", expectedDecl, decl);
	}

	@Test
	public void testGetXMLDeclWithUTF16() throws IOException, URISyntaxException {
		final URL testFileUrl = getClass().getResource(utf16TestFileName);
		final Path testFile = Paths.get(testFileUrl.toURI());

		final String expectedDecl = "<?xml version=\"1.0\" encoding=\"UTF-16\" standalone=\"no\"?>";
		final String decl = XMLUtil.getXMLDecl(Files.readAllBytes(testFile));
		assertEquals("XML Declaration for the UTF-16 encode example file wasn't resolved properly", expectedDecl, decl);
	}
}
