/*
 * TestEXistXMLSerialize.java
 *
 * Created on January 22, 2004, 11:01 AM
 */
package org.exist.xmldb.test;

import org.xml.sax.InputSource;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.modules.*;
import org.xmldb.api.base.*;

import org.apache.xml.serialize.OutputFormat;
import org.apache.xml.serialize.XMLSerializer;
import org.exist.util.serializer.DOMSerializer;
import org.w3c.dom.*;

import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.StringWriter;
import java.util.Properties;

import junit.framework.*;

/**
 *
 * @author  bmadigan
 */
public class TestEXistXMLSerialize extends TestCase{

    /** Creates a new instance of TestEXistXMLSerialize */
    public TestEXistXMLSerialize(String name) {
        super(name);
    }

    Collection c = null;
    Database database = null;
    File testFile = new File("src/org/exist/xmldb/test/PerformanceTest.xml");
    
    public void setUp( )throws Exception{
        Class cl = Class.forName("org.exist.xmldb.DatabaseImpl");
        database = (Database) cl.newInstance();
        database.setProperty("create-database", "true");
        DatabaseManager.registerDatabase(database);
        c = DatabaseManager.getCollection("xmldb:exist:///db");
    }

    public void tearDown() throws Exception{
        DatabaseManager.deregisterDatabase(database);
    }

    public void testSerialize1( ) throws Exception{
        System.out.println("Xerces version: "+org.apache.xerces.impl.Version.getVersion( ));
        XMLResource resource = (XMLResource) c.createResource(null, "XMLResource");

        Document doc = javax.xml.parsers.DocumentBuilderFactory.newInstance( ).
			newDocumentBuilder().parse(testFile);

        resource.setContentAsDOM(doc);
        System.out.println("Storing resource: "+resource.getId( ));
        c.storeResource(resource);

        resource = (XMLResource)c.getResource(resource.getId());
        Node node = resource.getContentAsDOM( );
        node = node.getOwnerDocument();
        
        System.out.println("Attempting serialization 1");
        DOMSource source = new DOMSource(node);
        ByteArrayOutputStream out = new ByteArrayOutputStream( );
        StreamResult result = new StreamResult(out);

        Transformer xformer = TransformerFactory.newInstance().newTransformer();
        xformer.transform(source, result);

        System.out.println("Using javax.xml.transform.Transformer");
        System.out.println("---------------------");
        System.out.println(new String(out.toByteArray()));
        System.out.println("--------------------- ");

    }

    public void testSerialize2( ) throws Exception{
    	System.out.println("Xerces version: "+org.apache.xerces.impl.Version.getVersion( ));
    	Document doc = javax.xml.parsers.DocumentBuilderFactory.newInstance( ).newDocumentBuilder().parse(testFile);
    	XMLResource resource = (XMLResource) c.createResource(null, "XMLResource");
    	resource.setContentAsDOM(doc);
    	System.out.println("Storing resource: "+resource.getId( ));
    	c.storeResource(resource);

    	resource = (XMLResource)c.getResource(resource.getId());
    	Node node = resource.getContentAsDOM();
    	System.out.println("Attempting serialization using XMLSerializer");
    	OutputFormat format = new OutputFormat( );
    	format.setLineWidth(0);
    	format.setIndent(5);
    	format.setPreserveSpace(true);
    	ByteArrayOutputStream out = new ByteArrayOutputStream( );
    	XMLSerializer serializer = new XMLSerializer(out, format);

    	if(node instanceof Document){
    		serializer.serialize((Document) node);
    	}else if(node instanceof Element){
    		serializer.serialize((Element) node);
    	}else{
    		throw new Exception("Can't serialize node type: "+node);
    	}
    	System.out.println("Using org.apache.xml.serialize.XMLSerializer");
    	System.out.println("---------------------");
    	System.out.println(new String(out.toByteArray()));
    	System.out.println("--------------------- ");
    }
    
    public void testSerialize3( ) throws Exception{
    	System.out.println("Xerces version: "+org.apache.xerces.impl.Version.getVersion( ));
    	Document doc = javax.xml.parsers.DocumentBuilderFactory.newInstance( ).newDocumentBuilder().parse(testFile);
    	XMLResource resource = (XMLResource) c.createResource(null, "XMLResource");
    	resource.setContentAsDOM(doc);
    	System.out.println("Storing resource: "+resource.getId( ));
    	c.storeResource(resource);

    	resource = (XMLResource)c.getResource(resource.getId());
    	Node node = resource.getContentAsDOM();
    	System.out.println("Attempting serialization using eXist's serializer");
    	StringWriter writer = new StringWriter();
    	Properties outputProperties = new Properties();
    	outputProperties.setProperty("indent", "yes");
    	DOMSerializer serializer = new DOMSerializer(writer, outputProperties);
    	serializer.serialize(node);
    	
    	System.out.println("Using org.exist.util.serializer.DOMSerializer");
    	System.out.println("---------------------");
    	System.out.println(writer.toString());
    	System.out.println("---------------------");
    }
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception{
        junit.textui.TestRunner.run(TestEXistXMLSerialize.class);
    }

}
