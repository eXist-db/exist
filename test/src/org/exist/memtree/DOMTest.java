/*
 * Created on Oct 19, 2003
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package org.exist.memtree;

import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.parsers.SAXParserFactory;

import junit.framework.TestCase;

import org.exist.dom.QName;
import org.exist.util.serializer.DOMSerializer;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

/**
 * @author wolf
 *
 * To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
public class DOMTest extends TestCase {
	
	private final static String XML =
		"<test count=\"1\" value=\"5543\" xmlns:x=\"http://foo.org\" xmlns=\"http://bla.org\"><x:title id=\"s1\">My title</x:title><paragraph>First paragraph</paragraph>"
			+ "<section><title>subsection</title></section></test>";

	public static void main(String[] args) {
		junit.textui.TestRunner.run(DOMTest.class);
	}

	public DOMTest(String name) {
		super(name);
	}
	
	public void testDocumentBuilder() {
		try {
			DocumentBuilderReceiver receiver = new DocumentBuilderReceiver();
			SAXParserFactory factory = SAXParserFactory.newInstance();
			factory.setNamespaceAware(true);
			XMLReader reader = factory.newSAXParser().getXMLReader();
			reader.setContentHandler(receiver);
			reader.parse(new InputSource(new StringReader(XML)));
	
			Document doc = receiver.getDocument();
			Node node = doc.getFirstChild();
			assertNotNull(node);
	
			StringWriter writer = new StringWriter();
			DOMSerializer serializer = new DOMSerializer(writer, null);
			serializer.serialize(node);
			System.out.println(writer.toString());
    	} catch (Exception e) {
    		fail(e.getMessage()); 
    	}			
	}
	
	public void testGetChildNodes1() {
		MemTreeBuilder builder = new MemTreeBuilder();
		builder.startDocument();
		builder.startElement(new QName("top", null, null), null);
		builder.characters("text");
		builder.endElement();
		builder.endDocument();
		DocumentImpl doc = builder.getDocument();
		Node top = doc.getFirstChild();
		assertEquals(Node.ELEMENT_NODE, top.getNodeType());
		assertEquals("top", top.getNodeName());
		assertEquals(1, top.getChildNodes().getLength());
	}

	public void testGetChildNodes2() {
		MemTreeBuilder builder = new MemTreeBuilder();
		builder.startDocument();
		builder.startElement(new QName("top", null, null), null);
		builder.startElement(new QName("child1", null, null), null);
		builder.endElement();
		builder.startElement(new QName("child2", null, null), null);
		builder.endElement();
		builder.endElement();
		builder.endDocument();
		DocumentImpl doc = builder.getDocument();
		Node top = doc.getFirstChild();
		assertEquals(Node.ELEMENT_NODE, top.getNodeType());
		assertEquals("top", top.getNodeName());
		assertEquals(2, top.getChildNodes().getLength());
	}

	public void print(Node node) {
		while (node != null) {
			switch (node.getNodeType()) {
				case Node.ELEMENT_NODE :
					System.out.println('<' + node.getNodeName() + '>');
					break;
				case Node.TEXT_NODE :
					System.out.println(node.getNodeValue());
					break;
				default :
					System.out.println("unknown node type");
			}
			if (node.hasChildNodes())
				print(node.getFirstChild());
			node = node.getNextSibling();
		}
	}
}
