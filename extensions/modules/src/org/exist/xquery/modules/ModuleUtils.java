package org.exist.xquery.modules;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.exist.memtree.DocumentBuilderReceiver;
import org.exist.memtree.MemTreeBuilder;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.NodeValue;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 * Utility Functions for XQuery Extension Modules
 * 
 * @author Adam Retter <adam.retter@devon.gov.uk>
 * @serial 2006-11-28
 * @version 1.0
 */
public class ModuleUtils
{
	/**
	 * Takes a String of XML and Creates an XML Node from it using SAX in the context of the query
	 * 
	 * @param xml	The String of XML
	 * 
	 * @return	The NodeValue of XML 
	 * */
	public static NodeValue stringToXML(XQueryContext context, String xml) throws XPathException
	{
		context.pushDocumentContext();
		try
		{ 
			//try and construct xml document from input stream, we use eXist's in-memory DOM implementation
			SAXParserFactory factory = SAXParserFactory.newInstance();
			factory.setNamespaceAware(true);	
			//TODO : we should be able to cope with context.getBaseURI()				
			InputSource src = new InputSource(new ByteArrayInputStream(xml.getBytes()));
			SAXParser parser = factory.newSAXParser();
			XMLReader reader = parser.getXMLReader();
			MemTreeBuilder builder = context.getDocumentBuilder();
			DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(builder);
			reader.setContentHandler(receiver);
			reader.parse(src);
			Document doc = receiver.getDocument();
			return (NodeValue)doc;
		}
		catch (ParserConfigurationException e)
		{				
			throw new XPathException(e.getMessage());
		}
		catch (SAXException e)
		{
			throw new XPathException(e.getMessage());
		}
		catch (IOException e)
		{
			throw new XPathException(e.getMessage());
		}
		finally
		{
         context.popDocumentContext();
		}
	}
}
