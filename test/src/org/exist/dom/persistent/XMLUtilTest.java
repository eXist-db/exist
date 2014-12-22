package org.exist.dom.persistent;

import org.exist.dom.persistent.XMLUtil;
import static org.junit.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.junit.Test;

public class XMLUtilTest {

	private String thisTestFileRelativePath = "test/src/org/exist/dom/persistent/";
	private String utf8TestFileName = "utf8.xml";
	private String utf16TestFileName =  "utf16.xml";
	
	@Test
	public void testGetXMLDeclWithUTF8() throws IOException {
		
		File existDir;
		File thisTestFileDir;
		String existHome = System.getProperty("exist.home");
		existDir = existHome == null ? new File(".") : new File(existHome);
		thisTestFileDir = new File(existDir, thisTestFileRelativePath);
		
		File testFile = new File(thisTestFileDir, utf8TestFileName);
		
		InputStream in;
		try {
			in = new FileInputStream(testFile);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			fail("Test file not found");
			return;
		}
		
		byte[] chunk = new byte[512];
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		int l;
		do {
			l = in.read(chunk);
			if (l > 0)
				out.write(chunk, 0, l);

		} while (l > -1);
		in.close();
		byte[] data = out.toByteArray();
		
		String expectedDecl = "<?xml version=\"1.0\"?>";
		String decl = XMLUtil.getXMLDecl(data);
		assertEquals("XML Declaration for the UTF-8 encode example file wasn't resolved properly", expectedDecl, decl);
	}

	@Test
	public void testGetXMLDeclWithUTF16() throws IOException {
		
		File existDir;
		File thisTestFileDir;
		String existHome = System.getProperty("exist.home");
		existDir = existHome == null ? new File(".") : new File(existHome);
		thisTestFileDir = new File(existDir, thisTestFileRelativePath);
		
		File testFile = new File(thisTestFileDir, utf16TestFileName);
		
		InputStream in;
		try {
			in = new FileInputStream(testFile);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			fail("Test file not found");
			return;
		}
		
		byte[] chunk = new byte[512];
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		int l;
		do {
			l = in.read(chunk);
			if (l > 0)
				out.write(chunk, 0, l);

		} while (l > -1);
		in.close();
		byte[] data = out.toByteArray();

		String expectedDecl = "<?xml version=\"1.0\" encoding=\"UTF-16\" standalone=\"no\"?>";
		String decl = XMLUtil.getXMLDecl(data);
		assertEquals("XML Declaration for the UTF-16 encode example file wasn't resolved properly", expectedDecl, decl);
	}
}
