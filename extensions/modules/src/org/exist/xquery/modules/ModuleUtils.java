package org.exist.xquery.modules;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.log4j.Logger;
import org.exist.memtree.DocumentImpl;
import org.exist.memtree.DocumentBuilderReceiver;
import org.exist.memtree.MemTreeBuilder;
import org.exist.memtree.SAXAdapter;
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
 * @serial 2007-04-17
 * @version 1.0
 */
public class ModuleUtils
{
	protected final static Logger LOG = Logger.getLogger(ModuleUtils.class);
	
	/**
	 * Takes a String of XML and Creates an XML Node from it using SAX in the context of the query
	 * 
	 * @param context	The Context of the calling XQuery
	 * @param xml	The String of XML
	 * 
	 * @return	The NodeValue of XML 
	 * */
	public static NodeValue stringToXML(XQueryContext context, String xml) throws XPathException, SAXException
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
			//return (NodeValue)doc.getDocumentElement();
			return (NodeValue)doc;
		}
		catch (ParserConfigurationException e)
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
	
	/**
	 * Takes a HTML InputSource and creates an XML representation of the HTML by tidying it (uses NekoHTML)
	 * 
	 * @param context	The Context of the calling XQuery
	 * @param srcHtml	The InputSource for the HTML
	 * 
	 * @return	An in-memory Document representing the XML'ised HTML
	 * */
	public static DocumentImpl htmlToXHtml(XQueryContext context, String url, InputSource srcHtml) throws XPathException, SAXException
	{
		//we use eXist's in-memory DOM implementation
		org.exist.memtree.DocumentImpl memtreeDoc = null;
		
		//use Neko to parse the HTML content to XML
		XMLReader reader = null;
		try
		{
            LOG.debug("Converting HTML to XML using NekoHTML parser for: " + url);
            reader = (XMLReader)Class.forName("org.cyberneko.html.parsers.SAXParser").newInstance();
            
            //do not modify the case of elements and attributes
            reader.setProperty("http://cyberneko.org/html/properties/names/elems", "match");
            reader.setProperty("http://cyberneko.org/html/properties/names/attrs", "no-change");  
        }
		catch(Exception e)
		{
            String errorMsg = "Error while involing NekoHTML parser. ("+e.getMessage()
            + "). If you want to parse non-wellformed HTML files, put "
            + "nekohtml.jar into directory 'lib/user'."; 
			LOG.error(errorMsg, e);
            
			throw new XPathException(errorMsg, e);
		}
		
		SAXAdapter adapter = new SAXAdapter();
		reader.setContentHandler(adapter);
		try
		{
			reader.parse(srcHtml);
		}
		catch(IOException e)
		{
			throw new XPathException(e.getMessage(), e);
		}
		Document doc = adapter.getDocument();
		memtreeDoc = (DocumentImpl)doc;
		memtreeDoc.setContext(context);
		return memtreeDoc;
	}
}
