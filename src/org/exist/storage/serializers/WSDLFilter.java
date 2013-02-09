/**
 * 
 */
package org.exist.storage.serializers;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

/**
 * Add the xmlns:tns and xmlns:soapenc namespaces to the definitions element of the WSDL stylesheet
 * we do this here as a workaround for Xalan, Xalan has no easy
 * way to declare additional dynamic namespaces.
 * There are two possible known Xalan hacks to do this -
 * 	
 * 	1) declare a dummy attribute with the namespace.
 * 	This seems to break WSDL compatibilty though!
 * 	2) declare a variable containing an element with namespace and copy the
 * 	namespace using xsl:copy-of and xalan:nodeset() but this doesnt seem to work!
 * 
 * http://sources.redhat.com/ml/xsl-list/2001-09/msg01204.html
 * 
 * If we were using Saxon instead of Xalan then the workaround is easy as XSLT 2.0
 * supports xsl:namespace for declaring dynamic namespaces
 * 
 * 
 * @author Adam Retter (adam.retter@devon.gov.uk)
 */
public class WSDLFilter implements ContentHandler
{
	protected ContentHandler outputHandler = null;
	protected String tnsNamespaceUri = null;
	protected final static String soapencNamespaceUri = "http://schemas.xmlsoap.org/soap/encoding/";
	
	
	public WSDLFilter(ContentHandler outputHandler, String tnsNamespaceUri)
	{
			this.outputHandler = outputHandler;
			this.tnsNamespaceUri = tnsNamespaceUri;
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#setDocumentLocator(org.xml.sax.Locator)
	 */
	public void setDocumentLocator(Locator locator)
	{
		outputHandler.setDocumentLocator(locator);
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#startDocument()
	 */
	public void startDocument() throws SAXException
	{
		outputHandler.startDocument();
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#endDocument()
	 */
	public void endDocument() throws SAXException
	{
		outputHandler.endDocument();
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#startPrefixMapping(java.lang.String, java.lang.String)
	 */
	public void startPrefixMapping(String prefix, String uri) throws SAXException
	{
		outputHandler.startPrefixMapping(prefix, uri);
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#endPrefixMapping(java.lang.String)
	 */
	public void endPrefixMapping(String prefix) throws SAXException
	{
		outputHandler.endPrefixMapping(prefix);
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
	 */
	public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException
	{
		if("definitions".equals(qName))
		{
			outputHandler.startPrefixMapping("tns", tnsNamespaceUri);
			outputHandler.startPrefixMapping("soapenc", soapencNamespaceUri);
		}
		outputHandler.startElement(uri, localName, qName, atts);
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#endElement(java.lang.String, java.lang.String, java.lang.String)
	 */
	public void endElement(String uri, String localName, String qName) throws SAXException
	{
		if("definitions".equals(qName))
		{
			outputHandler.endPrefixMapping("tns");
			outputHandler.endPrefixMapping("soapenc");
		}
		outputHandler.endElement(uri, localName, qName);
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#characters(char[], int, int)
	 */
	public void characters(char[] ch, int start, int length) throws SAXException
	{
		outputHandler.characters(ch, start, length);
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#ignorableWhitespace(char[], int, int)
	 */
	public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException
	{
		outputHandler.ignorableWhitespace(ch, start, length);
	}
	

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#processingInstruction(java.lang.String, java.lang.String)
	 */
	public void processingInstruction(String target, String data) throws SAXException
	{
		outputHandler.processingInstruction(target, data);
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#skippedEntity(java.lang.String)
	 */
	public void skippedEntity(String name) throws SAXException
	{
		outputHandler.skippedEntity(name);
	}

}
