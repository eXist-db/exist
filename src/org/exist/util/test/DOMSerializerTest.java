/*
 * Created on Oct 26, 2003
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package org.exist.util.test;

import java.io.File;
import java.io.StringWriter;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.exist.util.serializer.DOMSerializer;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import junit.framework.TestCase;

/**
 * @author wolf
 *
 * To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
public class DOMSerializerTest extends TestCase {

        static File existDir;
        static {
           String existHome = System.getProperty("exist.home");
           existDir = existHome==null ? new File(".") : new File(existHome);
        }
	private final static String file = (new File(existDir,"samples/biblio.rdf")).getAbsolutePath();
	
	public static void main(String[] args) {
		junit.textui.TestRunner.run(DOMSerializerTest.class);
	}

	public DOMSerializerTest(String name) {
		super(name);
	}
	
	public void testSerialize() {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			assertNotNull(factory);
			DocumentBuilder builder = factory.newDocumentBuilder();
			assertNotNull(builder);
			Document doc = builder.parse(new InputSource(file));
			assertNotNull(doc);
			StringWriter writer = new StringWriter();
			DOMSerializer serializer = new DOMSerializer(writer, null);
			serializer.serialize(doc.getDocumentElement());
			System.out.println(writer.toString());
        } catch (Exception e) {            
            fail(e.getMessage());  
        }
	}

}
