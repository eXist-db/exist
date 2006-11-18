/*
 * TestEXistXMLSerialize.java
 *
 * Created on January 22, 2004, 11:01 AM
 */
package org.exist.xmldb;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.StringWriter;
import java.util.Properties;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import junit.framework.TestCase;

import org.apache.xml.serialize.OutputFormat;
import org.apache.xml.serialize.XMLSerializer;
import org.exist.storage.DBBroker;
import org.exist.util.serializer.DOMSerializer;
import org.exist.util.serializer.SAXSerializer;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.modules.XMLResource;

/**
 *
 * @author  bmadigan
 */
public class TestEXistXMLSerialize extends TestCase{

	private final static String XML_DATA =
    	"<test>" +
    	"<para>ääööüüÄÄÖÖÜÜßß</para>" +
		"<para>\uC5F4\uB2E8\uACC4</para>" +
    	"</test>";
    
    private final static String XSL_DATA =
    	"<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" " +
    	"version=\"1.0\">" +
		"<xsl:param name=\"testparam\"/>" +
		"<xsl:template match=\"test\"><test><xsl:apply-templates/></test></xsl:template>" +
		"<xsl:template match=\"para\">" +
		"<p><xsl:value-of select=\"$testparam\"/>: <xsl:apply-templates/></p></xsl:template>" +
		"</xsl:stylesheet>";
    static File existDir = null;
    static {
      String existHome = System.getProperty("exist.home");
      existDir = existHome==null ? new File(".") : new File(existHome);
       
    }
    
    /** Creates a new instance of TestEXistXMLSerialize */
    public TestEXistXMLSerialize(String name) {
        super(name);
    }

    Collection c = null;
    Database database = null;
    File testFile = new File(existDir,"test/src/org/exist/xmldb/PerformanceTest.xml");
    
    public void setUp() {
    	try {
	        Class cl = Class.forName("org.exist.xmldb.DatabaseImpl");
	        database = (Database) cl.newInstance();
	        database.setProperty("create-database", "true");
	        DatabaseManager.registerDatabase(database);
	        c = DatabaseManager.getCollection("xmldb:exist://" + DBBroker.ROOT_COLLECTION);
        } catch (Exception e) {            
            fail(e.getMessage()); 
        }	        
    }

    public void tearDown() {
    	try {
    		DatabaseManager.deregisterDatabase(database);
            c = null;
            database = null;
        } catch (Exception e) {            
            fail(e.getMessage()); 
        }    		
    }

    public void testSerialize1( ) {
    	try {
	        System.out.println("Xerces version: "+org.apache.xerces.impl.Version.getVersion( ));
	        XMLResource resource = (XMLResource) c.createResource(null, "XMLResource");
	
	        Document doc = javax.xml.parsers.DocumentBuilderFactory.newInstance( ).
				newDocumentBuilder().parse(testFile);
	
	        resource.setContentAsDOM(doc);
	        System.out.println("Storing resource: "+resource.getId( ));
	        c.storeResource(resource);
	
	        resource = (XMLResource)c.getResource(resource.getId());
	        assertNotNull(resource);
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
        } catch (Exception e) {            
            fail(e.getMessage()); 
        }	        

    }

    public void testSerialize2( ) {
    	try {
	    	System.out.println("Xerces version: "+org.apache.xerces.impl.Version.getVersion( ));
	    	Document doc = javax.xml.parsers.DocumentBuilderFactory.newInstance( ).newDocumentBuilder().parse(testFile);
	    	XMLResource resource = (XMLResource) c.createResource(null, "XMLResource");
	    	resource.setContentAsDOM(doc);
	    	System.out.println("Storing resource: "+resource.getId( ));
	    	c.storeResource(resource);
	
	    	resource = (XMLResource)c.getResource(resource.getId());
	        assertNotNull(resource);
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
        } catch (Exception e) {            
            fail(e.getMessage()); 
        }	    	
    }
    
    public void testSerialize3( ) {
    	try {
	    	System.out.println("Xerces version: "+org.apache.xerces.impl.Version.getVersion( ));
	    	Document doc = javax.xml.parsers.DocumentBuilderFactory.newInstance( ).newDocumentBuilder().parse(testFile);
	    	XMLResource resource = (XMLResource) c.createResource(null, "XMLResource");
	    	resource.setContentAsDOM(doc);
	    	System.out.println("Storing resource: "+resource.getId( ));
	    	c.storeResource(resource);
	
	    	resource = (XMLResource)c.getResource(resource.getId());
	        assertNotNull(resource);
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
        } catch (Exception e) {            
            fail(e.getMessage()); 
        }	    	
    }
    
    public void testSerialize4( ) {
    	try {
	    	System.out.println("Xerces version: "+org.apache.xerces.impl.Version.getVersion( ));
	    	Document doc = javax.xml.parsers.DocumentBuilderFactory.newInstance( ).newDocumentBuilder().parse(testFile);
	    	XMLResource resource = (XMLResource) c.createResource(null, "XMLResource");
	    	resource.setContentAsDOM(doc);
	    	System.out.println("Storing resource: "+resource.getId( ));
	    	c.storeResource(resource);
	
	    	resource = (XMLResource)c.getResource(resource.getId());
	        assertNotNull(resource);
	    	Node node = resource.getContentAsDOM();
	    	System.out.println("Attempting serialization using eXist's SAX serializer");
	    	StringWriter writer = new StringWriter();
	    	Properties outputProperties = new Properties();
	    	outputProperties.setProperty("indent", "yes");
	    	SAXSerializer serializer = new SAXSerializer(writer, outputProperties);
	    	resource.getContentAsSAX(serializer);
	    	
	    	System.out.println("Using org.exist.util.serializer.SAXSerializer");
	    	System.out.println("---------------------");
	    	System.out.println(writer.toString());
	    	System.out.println("---------------------");
        } catch (Exception e) {            
            fail(e.getMessage()); 
        }	    	
    }
    
    public void testSerialize5() {
    	try {
	    	XMLResource resource = (XMLResource) c.createResource("test.xml", "XMLResource");
	    	resource.setContent(XML_DATA);
	    	System.out.println("Storing resource: "+resource.getId( ));
	    	c.storeResource(resource);
	    	
	    	XMLResource style = (XMLResource) c.createResource("test.xsl", "XMLResource");
	    	style.setContent(XSL_DATA);
	    	System.out.println("Storing resource: "+style.getId( ));
	    	c.storeResource(style);
	    	
	    	Properties outputProperties = new Properties();
	    	outputProperties.setProperty("indent", "yes");
	    	c.setProperty("stylesheet", "test.xsl");
	    	c.setProperty("stylesheet-param.testparam", "TEST");
	    	StringWriter writer = new StringWriter();
	    	SAXSerializer serializer = new SAXSerializer(writer, outputProperties);
	    	resource.getContentAsSAX(serializer);
	    	
	    	System.out.println("Using org.exist.util.serializer.SAXSerializer");
	    	System.out.println("---------------------");
	    	System.out.println(writer.toString());
	    	System.out.println("---------------------");
        } catch (Exception e) {            
            fail(e.getMessage()); 
        }	    	
    }
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        junit.textui.TestRunner.run(TestEXistXMLSerialize.class);
    }

}
