/*
 * Created on Oct 26, 2003
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package org.exist.util.test;

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

	private final static String file = "samples/biblio.rdf";
	
	public static void main(String[] args) {
		junit.textui.TestRunner.run(DOMSerializerTest.class);
	}

	public DOMSerializerTest(String name) {
		super(name);
	}
	
	public void testSerialize() throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = factory.newDocumentBuilder();
		Document doc = builder.parse(new InputSource(file));
		
		StringWriter writer = new StringWriter();
		DOMSerializer serializer = new DOMSerializer(writer, null);
		serializer.serialize(doc.getDocumentElement());
		System.out.println(writer.toString());
	}

}
