/*
 * Created on Oct 19, 2003
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package org.exist.memtree.test;

import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.parsers.SAXParserFactory;

import junit.framework.TestCase;

import org.exist.memtree.MemTreeBuilder;
import org.exist.memtree.Receiver;
import org.exist.util.serializer.DOMSerializer;
import org.exist.xpath.StaticContext;
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

	private final static String file = "samples/biblio.rdf";
	private final static String xml =
		"<test count=\"1\" value=\"5543\" xmlns:x=\"http://foo.org\" xmlns=\"http://bla.org\"><x:title id=\"s1\">My title</x:title><paragraph>First paragraph</paragraph>"
			+ "<section><title>subsection</title></section></test>";

	public static void main(String[] args) {
		junit.textui.TestRunner.run(DOMTest.class);
	}

	public DOMTest(String name) {
		super(name);
	}
	
	public void testDocumentBuilder() throws Exception {
		Receiver receiver = new Receiver();
		SAXParserFactory factory = SAXParserFactory.newInstance();
		factory.setNamespaceAware(true);
		XMLReader reader = factory.newSAXParser().getXMLReader();
		reader.setContentHandler(receiver);
		reader.parse(new InputSource(new StringReader(xml)));

		Document doc = receiver.getDocument();
		Node node = doc.getFirstChild();

		StringWriter writer = new StringWriter();
		DOMSerializer serializer = new DOMSerializer(writer, null);
		serializer.serialize(node);
		System.out.println(writer.toString());
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
