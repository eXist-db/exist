/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2006 The eXist team
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software Foundation
 *  Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 *  $Id$
 */
package org.exist.http;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Properties;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TemplatesHandler;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import org.exist.Namespaces;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.dom.QName;
import org.exist.http.servlets.HttpRequestWrapper;
import org.exist.http.servlets.HttpResponseWrapper;
import org.exist.http.servlets.RequestWrapper;
import org.exist.http.servlets.ResponseWrapper;
import org.exist.memtree.DocumentBuilderReceiver;
import org.exist.memtree.MemTreeBuilder;
import org.exist.memtree.SAXAdapter;
import org.exist.security.PermissionDeniedException;
import org.exist.security.xacml.AccessContext;
import org.exist.source.Source;
import org.exist.source.StringSource;
import org.exist.storage.DBBroker;
import org.exist.storage.XQueryPool;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.serializers.WSDLFilter;
import org.exist.util.MimeType;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.Cardinality;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.Constants;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Module;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.request.RequestModule;
import org.exist.xquery.functions.response.ResponseModule;
import org.exist.xquery.functions.session.SessionModule;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 * @author Adam Retter <adam.retter@devon.gov.uk>
 * @author Jose Maria Fernandez
 * 
 * @serial 20070531T12:18:00
 * 
 * The SOAPServer allows Web Services to be written in XQuery; it translates a 
 * SOAP Request to an XQuery function call and then translates the result of the
 * XQuery function to a SOAP Response.
 * 
 * This is done by managing an internal representation of an XQWS (XQuery Web Service),
 * through this it is able to provide enough information to an XSLT proccessor to
 * generate WSDL and human readable descriptions of the web service and individual
 * functions.
 * 
 * XSLT's are provided for both document literal and RPC style Web Service's and are
 * located in $EXIST_HOME/tools/SOAPServer
 */
public class SOAPServer
{
	private String formEncoding;			//TODO: we may be able to remove this eventually, in favour of HttpServletRequestWrapper being setup in EXistServlet, currently used for doPost() but perhaps could be used for other Request Methods? - deliriumsky
	private String containerEncoding;
	
	private final static String ENCODING = "UTF-8";
	private final static String SEPERATOR = System.getProperty("line.separator");
	private final static String XSLT_WEBSERVICE_WSDL = "/db/system/webservice/wsdl.xslt";
	private final static String XSLT_WEBSERVICE_HUMAN_DESCRIPTION = "/db/system/webservice/human.description.xslt";
	private final static String XSLT_WEBSERVICE_FUNCTION_DESCRIPTION = "/db/system/webservice/function.description.xslt";
	private final static String XSLT_WEBSERVICE_SOAP_RESPONSE = "/db/system/webservice/soap.response.xslt";
	public final static String WEBSERVICE_MODULE_EXTENSION = ".xqws";

	private HashMap XQWSDescriptionsCache = new HashMap();
	
    //TODO: SHARE THIS FUNCTION WITH RESTServer (copied at the moment)
	private final static String QUERY_ERROR_HEAD =
        "<html>" +
        "<head>" +
        "<title>Query Error</title>" +
        "<style type=\"text/css\">" +
        ".errmsg {" +
        "  border: 1px solid black;" +
        "  padding: 15px;" +
        "  margin-left: 20px;" +
        "  margin-right: 20px;" +
        "}" +
        "h1 { color: #C0C0C0; }" +
        ".path {" +
        "  padding-bottom: 10px;" +
        "}" +
        ".high { " +
        "  color: #666699; " +
        "  font-weight: bold;" +
        "}" +
        "</style>" +
        "</head>" +
        "<body>" +
        "<h1>XQuery Error</h1>";
	
	/**
	 * Constructor
	 * 
	 * @param formEncoding	The character encoding method to be used for form data
	 * @param containerEncoding	The character encoding method to be used for the container  
	 */
    public SOAPServer(String formEncoding, String containerEncoding)
    {
        this.formEncoding = formEncoding;
        this.containerEncoding = containerEncoding;
    }

    /**
     * Compiles an XQuery or returns a cached version if one exists
     * 
     * @param broker	The Database Broker to use
     * @param xqSource	The XQuery source
     * @param staticallyKnownDocuments	An array of XmldbURI's for documents that should be considered statically known by the XQuery
     * @param xqwsCollectionUri	The XmldbUri of the collection where the XQWS resides
     * @param request	The HttpServletRequest for the XQWS
     * @param response	The HttpServletResponse for the XQWS
     * 
     * @return The compiled XQuery
     */
    private CompiledXQuery compileXQuery(DBBroker broker, Source xqSource, XmldbURI[] staticallyKnownDocuments, XmldbURI xqwsCollectionUri, HttpServletRequest request, HttpServletResponse response) throws XPathException
    {
    	//Get the xquery service
        XQuery xquery = broker.getXQueryService();
		XQueryPool pool = xquery.getXQueryPool();
        XQueryContext context;
        
        //try and get pre-compiled XQuery from the cache
        CompiledXQuery compiled = pool.borrowCompiledXQuery(broker, xqSource);
        
        //Create the context and set a header to indicate cache status
        if(compiled == null)
        {
        	context = xquery.newContext(AccessContext.REST);
        	//response.setHeader("X-XQuery-Cached", "false");
    	}
        else
    	{
        	context = compiled.getContext();
        	//response.setHeader("X-XQuery-Cached", "true");
        }
        
        //Setup the context
        declareVariables(context, request, response);
        context.setModuleLoadPath(XmldbURI.EMBEDDED_SERVER_URI.append(xqwsCollectionUri).toString());
        context.setStaticallyKnownDocuments(staticallyKnownDocuments);
        
        //no pre-compiled XQuery, so compile, it
        if(compiled == null)
        {
            try
            {
                compiled = xquery.compile(context, xqSource);
            }
            catch (IOException e)
            {
                throw new XPathException("Failed to compile query: " + xqSource.toString() , e);
            }
        }
        
        //store the compiled xqws for use later 
        pool.returnCompiledXQuery(xqSource, compiled);
        
        return compiled;
    }
    
    /**
     * Creates an XQuery to call an XQWS function from a SOAP Request
     * 
     * @param broker	The Database Broker to use
     * @param xqwsFileUri	The XmldbURI of the XQWS file
     * @param xqwsNamespace	The namespace of the xqws
     * @param xqwsCollectionUri	The XmldbUri of the collection where the XQWS resides
     * @param xqwsSOAPFunction	The Node from the SOAP request for the Function call from the Http Request
     * @param xqwsDescription	The internal description of the XQWS
     * @param request	The Http Servlet Request
     * @param response The Http Servlet Response
     * 
     * @return The compiled XQuery
     */
    private CompiledXQuery XQueryExecuteXQWSFunction(DBBroker broker, Node xqwsSOAPFunction, XQWSDescription xqwsDescription, HttpServletRequest request, HttpServletResponse response) throws XPathException
    {	
    	StringBuffer query = new StringBuffer();
    	query.append("xquery version \"1.0\";" + SEPERATOR);
    	query.append(SEPERATOR);
        query.append("import module namespace " + xqwsDescription.getNamespace().getLocalName() + "=\"" + xqwsDescription.getNamespace().getNamespaceURI() + "\" at \"" + xqwsDescription.getFileURI().toString() + "\";" + SEPERATOR);
        query.append(SEPERATOR);
        
        //add the function call to the xquery
        String functionName = xqwsSOAPFunction.getLocalName();
        if(functionName == null)
        {
        	functionName = xqwsSOAPFunction.getNodeName();
        }
        query.append(xqwsDescription.getNamespace().getLocalName() + ":" + functionName + "(");
        
        //add the arguments for the function call if any
        NodeList xqwsSOAPFunctionParams = xqwsSOAPFunction.getChildNodes();
        Node nInternalFunction = xqwsDescription.getFunction(functionName);
        NodeList nlInternalFunctionParams = xqwsDescription.getFunctionParameters(nInternalFunction);
        
        int j = 0;
        for(int i = 0; i < xqwsSOAPFunctionParams.getLength(); i++)
        {
        	Node nSOAPFunctionParam = xqwsSOAPFunctionParams.item(i);
        	if(nSOAPFunctionParam.getNodeType() == Node.ELEMENT_NODE)
        	{
	        	query.append(writeXQueryFunctionParameter(xqwsDescription.getFunctionParameterType(nlInternalFunctionParams.item(j)), xqwsDescription.getFunctionParameterCardinality(nlInternalFunctionParams.item(j)), nSOAPFunctionParam));
        		query.append(","); //add function seperator
	        	
	        	j++;
        	}
        }
        
        //remove last superflurous seperator
		if(query.charAt(query.length()-1) == ',')
		{
			query.deleteCharAt(query.length()-1);
		}
        
        query.append(")");
        
        //compile the query
        return compileXQuery(broker, new StringSource(query.toString()), new XmldbURI[]{xqwsDescription.getCollectionURI()}, xqwsDescription.getCollectionURI(), request, response);
    }
    

    /**
     * Writes the value of a parameter for an XQuery function call
     * 
     * @param param	This StringBuffer contains the serialization of the value for XQuery
     * @param nParamSeqItem	The parameter value node from the SOAP Message
     * @param prefix	The prefix for the value (casting syntax)
     * @param postfix	The postfix for the value (casting syntax)
     * @param isAtomic	Whether the value of this type should be atomic or not (or even both)
     */
	private void processParameterValue(StringBuffer param,Node nParamSeqItem,String prefix,String postfix,int isAtomic) throws XPathException
	{
		boolean justOnce = false;
		StringBuffer whiteContent = new StringBuffer();
		
		try
		{
			Transformer tr = TransformerFactory.newInstance().newTransformer();
			tr.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION,"yes");

			Node n = nParamSeqItem.getFirstChild();
			StringWriter sw = new StringWriter();
			StreamResult result = new StreamResult(sw);
			StringBuffer psw = sw.getBuffer();
			while(n != null)
			{
				switch(n.getNodeType())
				{
					case Node.ELEMENT_NODE:
						if(isAtomic>0)
						{
							throw new Exception("Content of " + nParamSeqItem.getNodeName() + " must be an atomic value");
						}
						isAtomic = -1;
						if(justOnce)
						{
							throw new Exception(nParamSeqItem.getNodeName() + " must have ONLY ONE element child");
						}
						DOMSource source = new DOMSource(n);
						tr.transform(source,result);
						// Only once!
						justOnce = true;
						break;
					case Node.TEXT_NODE:
					case Node.CDATA_SECTION_NODE:
						String nodeValue = n.getNodeValue();
						boolean isNotWhite =! nodeValue.matches("[ \n\r\t]+");
						if(isAtomic >= 0)
						{
							if(isNotWhite || isAtomic>0)
							{
								if(isAtomic == 0)
								{
									isAtomic = 1;
								}
								psw.append(nodeValue);
							}
							else if(isAtomic == 0)
							{
								whiteContent.append(nodeValue);
							}
						}
						else if(isNotWhite)
						{
							throw new Exception(nParamSeqItem.getNodeName() + " has mixed content, but it must have only one element child");
						}
						break;
				}
				n = n.getNextSibling();
			}
			if(isAtomic >= 0)
			{
				param.append(prefix);
			}
			if(isAtomic == 0)
			{
				param.append(whiteContent);
			}
			else
			{
				param.append(psw);
			}
			if(isAtomic >= 0)
			{
		    	param.append(postfix);
			}
		}
		catch(Exception e)
		{
			throw new XPathException(e.getMessage());
		}
	}

    /**
     * Writes a parameter for an XQuery function call
     * 
     * @param paramType	The type of the Parameter (from the internal description of the XQWS)
     * @param paramCardinality The cardinality of the Parameter (from the internal description of the XQWS)
     * @param SOAPParam	The Node from the SOAP request for the Paremeter of the Function call from the Http Request 
     * 
     * @return A String representation of the parameter, suitable for use in the function call 
     */
    private StringBuffer writeXQueryFunctionParameter(String paramType, int paramCardinality, Node nSOAPParam) throws XPathException
    {
    	String prefix = new String();
    	String postfix = prefix;
    	
    	//determine the type of the parameter
    	int type = Type.getType(paramType);
    	int isAtomic = (Type.subTypeOf(type,Type.ATOMIC)) ? 1 : ((Type.subTypeOf(type,Type.NODE)) ? -1 : 0);
    	
    	if(isAtomic >= 0)
    	{
    		if(isAtomic >0 && type != Type.STRING)
    		{
    			String typeName = Type.getTypeName(type);
    			if(typeName != null)
    			{
    				prefix = typeName + "(\"";
    				postfix = "\")";
    			}
    		}
    		else
    		{
    			prefix = "\"";
    			postfix = prefix;
    		}
    	}
    
    	StringBuffer param = new StringBuffer();
    
    	//determine the cardinality of the parameter
    	if(paramCardinality >= Cardinality.MANY)
    	{
    		//sequence
    		param.append("(");
    		
    		NodeList nlParamSequenceItems = nSOAPParam.getChildNodes();
    		for(int i = 0; i < nlParamSequenceItems.getLength(); i++)
    		{
    			Node nParamSeqItem = nlParamSequenceItems.item(i);
    			if(nParamSeqItem.getNodeType() == Node.ELEMENT_NODE)
    			{
    				processParameterValue(param, nParamSeqItem, prefix, postfix, isAtomic);
				
	        		param.append(",");	//seperator for next item in sequence
    			}
    		}
    		
    		//remove last superflurous seperator
    		if(param.charAt(param.length()-1) == ',')
    		{
    			param.deleteCharAt(param.length()-1);
    		}
    		
    		param.append(")");
    	}
    	else
    	{
    		processParameterValue(param, nSOAPParam, prefix, postfix, isAtomic);
    	}
	
    	return param;
    }
    
    /**
     * Get's an XQWS Description from the cache.
     * If the description in the cache is out of date it will be refreshed.
     * If there is no cached description a new one is created and added
     * to the cache.
     * 
     * @param broker	The Database Broker to use
     * @param path	The path of the http request
     * @param request	The HttpServletRequest for the XQWS
     * 
     * @return An object describing the XQWS
     */
    private XQWSDescription getXQWSDescription(DBBroker broker, String path, HttpServletRequest request) throws PermissionDeniedException, XPathException, SAXException, NotFoundException
    {
    	XQWSDescription description;
    	
    	//is there a description for this path
    	if(XQWSDescriptionsCache.containsKey(path))
    	{
    		//get the description from the cache
    		description = (XQWSDescription)XQWSDescriptionsCache.get(path);
    		
    		//is the description is invalid, refresh it
    		if(!description.isValid())
    		{
    			description.refresh(request);
    		}
    	}
    	else
    	{
        	//create a new description
    		description = new XQWSDescription(broker, path, request);
    	}
    	
    	//store description in the cache
    	XQWSDescriptionsCache.put(path, description);
    	
    	//return the description
    	return description;
    }
    
	/**
	 * HTTP GET
	 * Processes requests for description documents - WSDL, Human Readable and Human Readable for a specific function
	 * 
	 * TODO: I think simple webservices can also be called using GET, so we may need to cater for that as well
	 * but first it would be best to write the doPost() method, split the code out into functions and also use it for this.
	 */
    public void doGet(DBBroker broker, HttpServletRequest request, HttpServletResponse response, String path) throws BadRequestException, PermissionDeniedException, NotFoundException, IOException
	{
    	//set the encoding
		if (request.getCharacterEncoding() == null)
			request.setCharacterEncoding(formEncoding);
    	
		/* Process the request */		
		try
		{
			//Get a Description of the XQWS
			XQWSDescription description = getXQWSDescription(broker, path, request);
			
			//Get the approriate description for the user
			byte[] result = null;
	        if(request.getParameter("WSDL") != null || request.getParameter("wsdl") != null)
	        {
	        	//WSDL document literal
	        	result = description.getWSDL();

	        	//set output content type for wsdl
	            response.setContentType(MimeType.XML_TYPE.getName());
	        }
	        else if(request.getParameter("WSDLRPC") != null || request.getParameter("wsdlrpc") != null)
	        {
	        	//WSDL RPC
	        	result = description.getWSDL(false);

	        	//set output content type for wsdl
	            response.setContentType(MimeType.XML_TYPE.getName());
	        }
	        else if(request.getParameter("function") != null)
	        {
	        	//Specific Function Description
	        	result = description.getFunctionDescription(request.getParameter("function"));
	        }
	        else
	        {
	        	//Human Readable Description
	        	result = description.getHumanDescription();
	        }
			
	        //send the description to the http servlet response
			ServletOutputStream os = response.getOutputStream();
			BufferedOutputStream bos = new BufferedOutputStream(os);
			bos.write(result);
			bos.close();
			os.close();
		}
		catch(XPathException xpe)
		{
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			writeResponse(response, formatXPathException(null, path, xpe), "text/html", ENCODING);
		}
		catch(SAXException saxe)
		{
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			writeResponse(response, formatXPathException(null, path, new XPathException(null, "SAX exception while transforming node: " + saxe.getMessage(), saxe)), "text/html", ENCODING);
		}
		catch(TransformerConfigurationException tce)
		{
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			writeResponse(response, formatXPathException(null, path, new XPathException(null, "SAX exception while transforming node: " + tce.getMessage(), tce)), "text/html", ENCODING);
		}
    }
	
	//process incomoing SOAP requests
	public void doPost(DBBroker broker, HttpServletRequest request, HttpServletResponse response, String path) throws BadRequestException, PermissionDeniedException, NotFoundException, IOException
    {	
		/*
		 * Example incoming SOAP Request
		 * 
			<SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
    			<SOAP-ENV:Header/>
    			<SOAP-ENV:Body>
        			<echo xmlns="http://localhost:8080/exist/servlet/db/echo.xqws">
            			<arg1>adam</arg1>
        			</echo>
    			</SOAP-ENV:Body>
			</SOAP-ENV:Envelope>
		 */
		
		// 1) Read the incoming SOAP request
		InputStream is = request.getInputStream();
		byte[] buf = new byte[request.getContentLength()];
		int bytes = 0;
		int offset = 0;
		int max = 4096;
	    while((bytes = is.read(buf, offset, max)) != -1)
	    {
			offset += bytes;
	    }

		// 2) Create an XML Document from the SOAP Request
	    Document soapRequest = null;
		try
		{
			soapRequest = BuildXMLDocument(buf);
		}
		catch(Exception e)
		{
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			writeResponse(response, formatXPathException(null, path, new XPathException(null, "Unable to construct an XML document from the SOAP Request, probably an invalid request: " + e.getMessage(), e)), "text/html", ENCODING);
			return;
		}
		
		// 3) Validate the SOAP Request 
		//TODO: validate the SOAP Request
		
		// 4) Extract the function call from the SOAP Request
		NodeList nlBody = soapRequest.getDocumentElement().getElementsByTagNameNS(Namespaces.SOAP_ENVELOPE, "Body");
		Node nSOAPBody = nlBody.item(0);
		NodeList nlBodyChildren = nSOAPBody.getChildNodes();
		Node nSOAPFunction = null;
		for(int i = 0; i < nlBodyChildren.getLength(); i++)
		{
			Node bodyChild = nlBodyChildren.item(i);
			if(bodyChild.getNodeType() == Node.ELEMENT_NODE)
			{
				nSOAPFunction = bodyChild;
				break;
			}
		}
		
		// Check the namespace for the function in the SOAP document is the same as the request path?
		String funcNamespace =  nSOAPFunction.getNamespaceURI();
		
		if(funcNamespace != null)
		{
			if(!funcNamespace.equals(request.getRequestURL().toString()))
			{
				//function in SOAP request has an invalid namespace
				response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				writeResponse(response, "SOAP Function call has invalid namespace, got: " + funcNamespace + " but expected: " + request.getRequestURL().toString(), "text/html", ENCODING);
				return;
			}
		}
		else
		{
			//function in SOAP request has no namespace
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			writeResponse(response, "SOAP Function call has no namespace, expected: " + request.getRequestURL().toString(), "text/html", ENCODING);
			return;
		}
		
		// 4.5) Detemine encoding style
		String encodingStyle = ((org.w3c.dom.Element)nSOAPFunction).getAttributeNS(Namespaces.SOAP_ENVELOPE, "encodingStyle");
		boolean isRpcEncoded = (encodingStyle != null && encodingStyle.equals("http://schemas.xmlsoap.org/soap/encoding/"));
		
		// As this detection is a "quirk" which is not always available, let's use a better one...
		if(!isRpcEncoded)
		{
			NodeList nlSOAPFunction=nSOAPFunction.getChildNodes();
			for(int i = 0; i < nlSOAPFunction.getLength(); i++)
			{
				Node functionChild = nlSOAPFunction.item(i);
				if(functionChild.getNodeType() == Node.ELEMENT_NODE)
				{
					if(((org.w3c.dom.Element)functionChild).hasAttributeNS(Namespaces.SCHEMA_INSTANCE_NS, "type"))
					{
						isRpcEncoded = true;
						break;
					}
				}
			}
		}
		
		// 5) Execute the XQWS function indicated by the SOAP request  
		try
		{
			//Get the internal description for the function requested by SOAP (should be in the cache)
			XQWSDescription description = getXQWSDescription(broker, path, request);
			
			//Create an XQuery to call the XQWS function
			CompiledXQuery xqCallXQWS = XQueryExecuteXQWSFunction(broker, nSOAPFunction, description, request, response);
			
			//xqCallXQWS
			XQuery xqueryService = broker.getXQueryService();
			Sequence xqwsResult = xqueryService.execute(xqCallXQWS, null);
			
			// 6) Create a SOAP Response describing the Result
			String funcName = nSOAPFunction.getLocalName();
			if(funcName == null)
			{
				funcName = nSOAPFunction.getNodeName();
			}
        	byte[] result = description.getSOAPResponse(funcName, xqwsResult, request,isRpcEncoded);

        	// 7) Send the SOAP Response to the http servlet response
        	response.setContentType(MimeType.XML_TYPE.getName());
			ServletOutputStream os = response.getOutputStream();
			BufferedOutputStream bos = new BufferedOutputStream(os);
			bos.write(result);
			bos.close();
			os.close();
		}
		catch(XPathException xpe)
		{
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			writeResponse(response, formatXPathException(null, path, xpe), "text/html", ENCODING);
		}
		catch(SAXException saxe)
		{
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			writeResponse(response, formatXPathException(null, path, new XPathException(null, "SAX exception while transforming node: " + saxe.getMessage(), saxe)), "text/html", ENCODING);
		}
		catch(TransformerConfigurationException tce)
		{
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			writeResponse(response, formatXPathException(null, path, new XPathException(null, "SAX exception while transforming node: " + tce.getMessage(), tce)), "text/html", ENCODING);
		}
    }
	
	/**
	 * Builds an XML Document from a string representation
	 * 
	 * @param buf	The XML Document content
	 * 
	 * @return	DOM XML Document
	 */
	private Document BuildXMLDocument(byte[] buf) throws SAXException, ParserConfigurationException, IOException
	{
		//try and construct xml document from input stream, we use eXist's in-memory DOM implementation
		SAXParserFactory factory = SAXParserFactory.newInstance();
		factory.setNamespaceAware(true);	
		//TODO we should be able to cope with context.getBaseURI()				
		InputSource src = new InputSource(new ByteArrayInputStream(buf));
		SAXParser parser = factory.newSAXParser();
		XMLReader reader = parser.getXMLReader();
		SAXAdapter adapter = new SAXAdapter();
        reader.setContentHandler(adapter);
		reader.setContentHandler(adapter);
		reader.parse(src);
		
		//return receiver.getDocument();
		return adapter.getDocument();
	}
	
    /**
     * Pass the request, response and session objects to the XQuery
     * context.
     *
     * @param context
     * @param request
     * @param response
     * @throws XPathException
     */
    private void declareVariables(XQueryContext context, HttpServletRequest request, HttpServletResponse response) throws XPathException
    {
    	if(request != null)
    	{
	    	RequestWrapper reqw = new HttpRequestWrapper(request, formEncoding, containerEncoding);
	        context.declareVariable(RequestModule.PREFIX + ":request", reqw);
	        context.declareVariable(SessionModule.PREFIX + ":session", reqw.getSession());
    	}
        
    	if(response != null)
    	{
    		ResponseWrapper respw = new HttpResponseWrapper(response);
    		context.declareVariable(ResponseModule.PREFIX + ":response", respw);
    	}
        
    }
    
    //TODO: SHARE THIS FUNCTION WITH RESTServer (copied at the moment)
    /**
     * @param query
     * @param e
     */
    private String formatXPathException(String query, String path, XPathException e) {
        StringWriter writer = new StringWriter();
        writer.write(QUERY_ERROR_HEAD);
        writer.write("<p class=\"path\"><span class=\"high\">Path</span>: ");
        writer.write("<a href=\"");
        writer.write(path);
        writer.write("\">");
        writer.write(path);
        writer.write("</a></p>");
        
        writer.write("<p class=\"errmsg\">");
        writer.write(e.getMessage());
        writer.write("</p>");
        if(query != null) {
            writer.write("<p><span class=\"high\">Query</span>:</p><pre>");
            writer.write(query);
            writer.write("</pre>");
        }
        writer.write("</body></html>");
        return writer.toString();
    }
    
    //TODO: SHARE THIS FUNCTION WITH RESTServer (copied at the moment)
    private void writeResponse(HttpServletResponse response, String data, String contentType, String encoding) throws IOException
    {        
        // possible format contentType: text/xml; charset=UTF-8
        if ( contentType != null && !response.isCommitted() ) {
            
            int semicolon = contentType.indexOf(';');
            if (semicolon != Constants.STRING_NOT_FOUND) {
                contentType = contentType.substring(0,semicolon);
            }
           
            response.setContentType(contentType + "; charset=" + encoding);
        }
        
        OutputStream is = response.getOutputStream();
        is.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n".getBytes());
        is.write(data.getBytes(encoding));
    }
    
    
    private class XQWSDescription
    {
    	/**
    	 * Class describes an XQWS using an Internal XML Representation
    	 * 
    	 * @author Adam Retter <adam.retter@devon.gov.uk>
    	 * @serial 20061023T19:23:00
    	 */
    	
    	private DBBroker broker = null;
    	private String HttpServletRequestURL = null;
    	private String XQWSPath = null;
    	private XmldbURI xqwsFileURI = null;
    	private XmldbURI xqwsCollectionURI = null;
    	private QName xqwsNamespace = null;
    	
    	//cache for internal Description of an XQWS
    	private long lastModifiedXQWS = 0;
    	private Module modXQWS = null;
    	private org.exist.memtree.DocumentImpl docXQWSDescription = null;
    	
    	//cache for XQWS WSDL
    	private long lastModifiedWSDL = 0;
    	private byte[][] descriptionWSDL = {null, null};
	
    	//cache for XQWS Human Readable description
    	private long lastModifiedHuman = 0;
    	private byte[] descriptionHuman = null;
    	
    	//Cache for XQWS (Human Readable) Function description
    	private long lastModifiedFunction = 0;
    	private HashMap descriptionFunction = new HashMap(); //key: functionName as String, value: byte[]
    	
    	
    	/**
    	 * Constructor
    	 * 
    	 * @param broker	The Database Broker to use
    	 * @param XQWSPath	The path to the XQWS
    	 * @param request	The Http Request for the XQWS
    	 */
    	public XQWSDescription(DBBroker broker, String XQWSPath, HttpServletRequest request) throws XPathException, SAXException, PermissionDeniedException, NotFoundException
    	{
    		this.broker = broker;
    		this.HttpServletRequestURL = request.getRequestURL().toString();
    		this.XQWSPath = XQWSPath;
    		
    		//create an initial description of the XQWS
    		createInternalDescription(request);
    	}
    	
    	/**
    	 * Returns the URI of the XQWS file
    	 * 
    	 * @return The XmldbURI of the XQWS file
    	 */
    	public XmldbURI getFileURI()
    	{
    		return xqwsFileURI;
    	}
    	
    	/**
    	 * Returns the URI of the Collection containing the XQWS file
    	 * 
    	 * @return The XmldbURI of the Collection containing the XQWS file
    	 */
    	public XmldbURI getCollectionURI()
    	{
    		return xqwsCollectionURI;
    	}
    	
    	/**
    	 * Returns the Namespace of the XQWS
    	 * 
    	 * @return The QName for the Namespace of the XQWS
    	 */
    	public QName getNamespace()
    	{
    		return xqwsNamespace;
    	}
    	
    	/**
    	 * Determines if this description of the XQWS is valid
    	 * 
    	 * @return true if the description is valid, false otherwise
    	 */
    	public boolean isValid()
    	{
    		BinaryDocument docXQWS = null;
    		
    		try
    		{
    			docXQWS = getXQWS(broker, XQWSPath);
    			return (docXQWS.getMetadata().getLastModified() == lastModifiedXQWS);
    		}
    		catch(PermissionDeniedException e)
    		{
    			//TODO: log message
    			return false;
    		}
    		finally
    		{
    			if(docXQWS != null)
    			{
    				docXQWS.getUpdateLock().release(Lock.READ_LOCK);
    			}
    		}
    	}
    	
    	/**
    	 * Refreshes an XQWS Description by re-reading the XQWS
    	 * Should be called if isValid() returns false and an XQWS description is needed further 
    	 * 
    	 * @param request	The HttpServletRequest to update for
    	 */
    	public void refresh(HttpServletRequest request) throws XPathException, SAXException, PermissionDeniedException,NotFoundException
    	{
    		createInternalDescription(request);
    	}

    	/**
    	 * Returns the WSDL for the XQWS Description
    	 * Caches the result, however the cache is regenerated if
    	 * the StyleSheet used for the transformation changes
    	 * 
    	 * @return byte array containing the WSDL
    	 */
    	public byte[] getWSDL() throws PermissionDeniedException, TransformerConfigurationException, SAXException 
    	{
    		return getWSDL(true);
    	}
	
    	/**
    	 * Returns the WSDL for the XQWS Description
    	 * Caches the result, however the cache is regenerated if
    	 * the StyleSheet used for the transformation changes
    	 * 
    	 * @return byte array containing the WSDL
    	 */
    	public byte[] getWSDL(boolean isDocumentLiteral) throws PermissionDeniedException, TransformerConfigurationException, SAXException 
    	{
    		DocumentImpl docStyleSheet = null;
    		int wsdlIndex = isDocumentLiteral ? 0 : 1;
    		try
    		{
	    		//get the WSDL StyleSheet
    			docStyleSheet = broker.getXMLResource(XmldbURI.create(XSLT_WEBSERVICE_WSDL), Lock.READ_LOCK);
	    		
	    		//has the stylesheet changed, or is this the first call for this version
	    		if(docStyleSheet.getMetadata().getLastModified() != lastModifiedWSDL || descriptionWSDL[wsdlIndex] == null)
	    		{
	    			//TODO: validate the WSDL
	    			
	    			Properties params = new Properties();
	    			params.put("isDocumentLiteral", isDocumentLiteral ? "true" : "false");
				
	    			//yes, so re-run the transformation
	    			descriptionWSDL[wsdlIndex] = Transform(docXQWSDescription, docStyleSheet, params);
	    			lastModifiedWSDL = docStyleSheet.getMetadata().getLastModified();
	    		}
	    		
				//return the result of the transformation
				return descriptionWSDL[wsdlIndex];
    		}
    		finally
    		{
    			if(docStyleSheet != null)
    			{
	        		//close the Stylesheet Document and release the read lock
	    			docStyleSheet.getUpdateLock().release(Lock.READ_LOCK);
    			}
    		}
    	}
    	
    	/**
    	 * Returns the Human Readable description for the XQWS Description
    	 * Caches the result, however the cache is regenerated if
    	 * the StyleSheet used for the transformation changes
    	 * 
    	 * @return byte array containing the WSDL
    	 */
    	public byte[] getHumanDescription() throws PermissionDeniedException, TransformerConfigurationException, SAXException 
    	{
    		DocumentImpl docStyleSheet = null;
    		try
    		{
	    		//get the Human Description StyleSheet
	    		docStyleSheet = broker.getXMLResource(XmldbURI.create(XSLT_WEBSERVICE_HUMAN_DESCRIPTION), Lock.READ_LOCK);
	    		
	    		//has the stylesheet changed, or is this the first call for this version
	    		if(docStyleSheet.getMetadata().getLastModified() != lastModifiedHuman || descriptionHuman == null)
	    		{
	    			//yes, so re-run the transformation
	    			descriptionHuman = Transform(docXQWSDescription, docStyleSheet, null);
	    			lastModifiedHuman = docStyleSheet.getMetadata().getLastModified();
	    		}
				
				//return the result of the transformation
				return descriptionHuman;
    		} 
    		finally
    		{
    			if(docStyleSheet != null)
    			{
		    		//close the Stylesheet Document and release the read lock
	    			docStyleSheet.getUpdateLock().release(Lock.READ_LOCK);
    			}
    		}
    	}
    	    	
    	/**
    	 * Returns the (Human Readable) description of a Function for the XQWS Description
    	 * Caches the result, however the cache is regenerated if
    	 * the StyleSheet used for the transformation changes
    	 * 
    	 * @param functionName The name of the function to describe
    	 * 
    	 * @return byte array containing the Function Description
    	 */
    	public byte[] getFunctionDescription(String functionName) throws PermissionDeniedException, TransformerConfigurationException, SAXException 
    	{
    		DocumentImpl docStyleSheet = null;
    		try
    		{
	    		//get the Function Description StyleSheet
    			docStyleSheet = broker.getXMLResource(XmldbURI.create(XSLT_WEBSERVICE_FUNCTION_DESCRIPTION), Lock.READ_LOCK);
	    		
	    		//has the stylesheet changed?
	    		if(docStyleSheet.getMetadata().getLastModified() != lastModifiedFunction)
	    		{
	    			//yes, so empty the cache
	    			descriptionFunction.clear();
	    			
	    			//change the last modified date
	    			lastModifiedFunction = docStyleSheet.getMetadata().getLastModified();
	    		}
	    		
	    		//if there is not a pre-trasformed description in the cache
	    		if(!descriptionFunction.containsKey(functionName))
	    		{
	    			//do the transformation and store in the cache
	    			Properties params = new Properties();
	    			params.put("function", functionName);
	    			descriptionFunction.put(functionName, Transform(docXQWSDescription, docStyleSheet, params));
	    		}
	    		
				//return the result of the transformation from the cache
				return (byte[])descriptionFunction.get(functionName);
    		}
    		finally
    		{
    			if(docStyleSheet != null)
    			{
		    		//close the Stylesheet Document and release the read lock
	    			docStyleSheet.getUpdateLock().release(Lock.READ_LOCK);
    			}
    		}
   		}

    	/**
    	 * Returns the function node from the internal description
    	 * 
    	 * @param functionName	The name of the function to return
    	 * 
    	 * @return the node from the internal description
    	 */
    	public Node getFunction(String functionName)
    	{
    		//iterate through all the function nodes
    		NodeList nlFunctions = docXQWSDescription.getElementsByTagName("function");
    		for(int i = 0; i < nlFunctions.getLength(); i++)
    		{
    			//get the function node
    			Node nFunction = nlFunctions.item(i);
    		
    			//iterate through children of function, get value of <name> element
    			NodeList nlFunctionChildren = nFunction.getChildNodes();
    			for(int j = 0; j < nlFunctionChildren.getLength(); j++)
    			{
    				Node nFunctionChild = nlFunctionChildren.item(j);
    				if(nFunctionChild.getNodeType() == Node.ELEMENT_NODE)
    				{
    					//is this the function node we are looking for?
    					if(nFunctionChild.getNodeName().equals("name") && nFunctionChild.getFirstChild().getNodeValue().equals(functionName))
    					{
    						//yes so return it
    						return nFunction; 
    					}
    				}
    			}
    		}
    		
    		return null;
    	}
    	
    	/**
    	 * Returns the parameters for a function from the internal description
    	 * 
    	 * @param functionName	The name of the function to return parameters for
    	 * 
    	 * @return NodeList of parameter's
    	 */
    	public NodeList getFunctionParameters(String functionName)
    	{
    		Node internalFunction = getFunction(functionName);
    		if(internalFunction != null)
    		{
    			return getFunctionParameters(internalFunction);
    		}
    		return null;
    	}
    	
    	/**
    	 * Returns the parameters for a function from the internal description
    	 * 
    	 * @param internalFunction The internal function to return parameters for
    	 * 
    	 * @return NodeList of parameter's
    	 */
    	public NodeList getFunctionParameters(Node internalFunction)
    	{
    		NodeList nlChildren = internalFunction.getChildNodes();
    		for(int i = 0; i < nlChildren.getLength(); i++)
    		{
    			Node child = nlChildren.item(i);
    			if(child.getNodeName().equals("parameters"))
    			{
    				return child.getChildNodes();
    			}
    		}
    		
    		return null;
    	}
    	
    	/**
    	 * Returns the Name for the function parameter
    	 * 
    	 * @param internalFunctionParameter The internal function parameter to return the Name for
    	 * 
    	 * @return The Name of the parameter
    	 */
    	public String getFunctionParameterName(Node internalFunctionParameter)
    	{
    		//first element child of <parameter> is <name>
    		NodeList nlParamArgs = internalFunctionParameter.getChildNodes(); 
    		for(int i = 0; i < nlParamArgs.getLength(); i++)
    		{
    			Node nArg = nlParamArgs.item(i);
    			if(nArg.getNodeType() == Node.ELEMENT_NODE)
    			{
    				if(nArg.getNodeName().equals("name"))
    				{
    					return nArg.getFirstChild().getNodeValue();
    				}
    			}
    		}
    		
    		return null;
    	}
    	
    	/**
    	 * Returns the Type for the function parameter
    	 * 
    	 * @param internalFunctionParameter The internal function parameter to return the Type for
    	 * 
    	 * @return The Type of the parameter
    	 */
    	public String getFunctionParameterType(Node internalFunctionParameter)
    	{
    		//second element child of <parameter> is <type>
    		NodeList nlParamArgs = internalFunctionParameter.getChildNodes(); 
    		for(int i = 0; i < nlParamArgs.getLength(); i++)
    		{
    			Node nArg = nlParamArgs.item(i);
    			if(nArg.getNodeType() == Node.ELEMENT_NODE)
    			{
    				if(nArg.getNodeName().equals("type"))
    				{
    					return nArg.getFirstChild().getNodeValue();
    				}
    			}
    		}
    		
    		return null;
    	}
    	
    	/**
    	 * Returns the Cardinality for the function parameter
    	 * 
    	 * @param internalFunctionParameter The internal function parameter to return the Cardinality for
    	 * 
    	 * @return The Cardinality as defined by org.exist.xquery.Cardinality
    	 */
    	public int getFunctionParameterCardinality(Node internalFunctionParameter)
    	{
    		//third element child of <parameter> is <cardinality>
    		NodeList nlParamArgs = internalFunctionParameter.getChildNodes(); 
    		for(int i = 0; i < nlParamArgs.getLength(); i++)
    		{
    			Node nArg = nlParamArgs.item(i);
    			if(nArg.getNodeType() == Node.ELEMENT_NODE)
    			{
    				if(nArg.getNodeName().equals("cardinality"))
    				{
    					return Integer.valueOf(nArg.getFirstChild().getNodeValue()).intValue();
    				}
    			}
    		}
    		
    		//default cardinality
    		return Cardinality.EXACTLY_ONE;
    	}
    	
    	/**
    	 * Returns the SOAP Response for the XQWS Function
    	 * named with the result provided.
    	 * 
    	 * @param functionName	The name of the XQWS function that was called
    	 * @param functionResult	The Result of the XQWS function that was called
    	 * @param request	The Http Request for the XQWS
    	 * 
    	 * @return byte array containing the SOAP Response
    	 */
    	public byte[] getSOAPResponse(String functionName, Sequence functionResult, HttpServletRequest request,boolean isRpcEncoded) throws XPathException, PermissionDeniedException, TransformerConfigurationException, SAXException
    	{
    		//get the Result StyleSheet for the SOAP Response
    		DocumentImpl docStyleSheet = broker.getXMLResource(XmldbURI.create(XSLT_WEBSERVICE_SOAP_RESPONSE), Lock.READ_LOCK);
    		
    		//Get an internal description, containg just a single function with its result
    		org.exist.memtree.DocumentImpl docResult = describeWebService(modXQWS, xqwsFileURI, request, XQWSPath, functionName, functionResult);
    		
    		//return the SOAP Response
	    	Properties params = new Properties();
	    	params.put("isDocumentLiteral", isRpcEncoded ? "false" : "true");
    		return Transform(docResult, docStyleSheet, params);
    	}
    	
    	/**
    	 * Creates the internal Description of the XQWS
    	 * 
    	 * @param request The HttpServletRequest for which the description should be created
    	 */
    	private void createInternalDescription(HttpServletRequest request) throws XPathException, SAXException, PermissionDeniedException, NotFoundException
    	{
    		// 1) Get the XQWS
    		BinaryDocument docXQWS = getXQWS(broker, XQWSPath);
		
    		if(docXQWS == null)
    		{
    			throw new NotFoundException("Resource " + request.getRequestURL().toString() + " not found");
    		}
		
    		xqwsFileURI = docXQWS.getFileURI();
    		xqwsCollectionURI = docXQWS.getCollection().getURI();
    		byte[] xqwsData = getXQWSData(broker, docXQWS);
            
    		// 2) Store last modified date
    		lastModifiedXQWS = docXQWS.getMetadata().getLastModified();
    		
            // 3) Get the XQWS Namespace
            xqwsNamespace = getXQWSNamespace(xqwsData);
            
            // 4) Compile a Simple XQuery to access the module
            CompiledXQuery compiled = XQueryIncludeXQWS(broker, docXQWS.getFileURI(), xqwsNamespace, docXQWS.getCollection().getURI());
            
            // 5) Inspect the XQWS and its function signatures and create a small XML document to represent it
            modXQWS = compiled.getContext().getModule(xqwsNamespace.getNamespaceURI());
            docXQWSDescription = describeWebService(modXQWS, xqwsFileURI, request, XQWSPath, null, null);
    	}
    	
    	/**
         * Gets XQWS file from the db
         * 
         * @param broker 	The Database Broker to use
         * @param path		The Path to the XQWS
         * 
         * @return	The XQWS BinaryDocument
         */
        private BinaryDocument getXQWS(DBBroker broker, String path) throws PermissionDeniedException
        {
        	BinaryDocument docXQWS = null;
            try
            {
            	XmldbURI pathUri = XmldbURI.create(path);        
            	docXQWS = (BinaryDocument) broker.getXMLResource(pathUri, Lock.READ_LOCK);
            	return docXQWS;
            }
            finally
            {
                //close the XQWS Document and release the read lock
        	    if(docXQWS != null)
        	    {
        	    	docXQWS.getUpdateLock().release(Lock.READ_LOCK);
                }            	
            }
        }
        
        /**
         * Gets the data from an XQWS Binary Document
         * 
         * @param broker	The Database Broker to use
         * @param docXQWS	The XQWS Binary Document
         * 
         * @return	byte array containing the content of the XQWS Binary document
         */
        private byte[] getXQWSData(DBBroker broker, BinaryDocument docXQWS)
        {
        	byte[] data = broker.getBinaryResource(docXQWS);
        	
        	return data;
        }
        
        /**
         * Get's the namespace of the XQWS form the content of an XQWS
         *
         * @param xqwsData	The content of an XQWS file
         * 
         * @return The namespace QName
         */
        private QName getXQWSNamespace(byte[] xqwsData)
        {   
        	//move through the xqws char by char checking if a line contains the module namespace declaration     
            StringBuffer sbNamespace = new StringBuffer();
            ByteArrayInputStream bis = new ByteArrayInputStream(xqwsData);
            while(bis.available() > 0)
            {
            	char c = (char)bis.read();	//TODO: do we need encoding here?
            	sbNamespace.append(c);
            	if(c == SEPERATOR.charAt(SEPERATOR.length() -1))
            	{
            		if(sbNamespace.toString().startsWith("module namespace"))
            		{
            			//break out of the while loop, sbNamespace should now contain our namespace
            			break;
            		}
            		else
            		{
            			//empty the namespace buffer
            			sbNamespace.delete(0, sbNamespace.length());
            		}
            	}
            }
            
            //seperate the name and url
            String namespaceName = sbNamespace.substring("module namespace".length(), sbNamespace.indexOf("=")).trim();
            String namespaceURL = sbNamespace.substring(sbNamespace.indexOf("\"")+1, sbNamespace.lastIndexOf("\""));
            
            //return the XQWS namespace
            return new QName(namespaceName, namespaceURL);
        }
    	
        /**
         * Creates a simple XQuery to include an XQWS
         * 
         * @param broker	The Database Broker to use
         * @param xqwsFileUri	The XmldbURI of the XQWS file
         * @param xqwsNamespace	The namespace of the xqws
         * @param xqwsCollectionUri	The XmldbUri of the collection where the XQWS resides
         * 
         * @return The compiled XQuery
         */
        private CompiledXQuery XQueryIncludeXQWS(DBBroker broker, XmldbURI xqwsFileUri, QName xqwsNamespace, XmldbURI xqwsCollectionUri) throws XPathException
        {
            //Create a simple XQuery wrapper to access the module
            String query = "xquery version \"1.0\";" + SEPERATOR;
            query += SEPERATOR;
            query += "import module namespace " + xqwsNamespace.getLocalName() + "=\"" + xqwsNamespace.getNamespaceURI() + "\" at \"" + xqwsFileUri.toString() + "\";" + SEPERATOR;
            query += SEPERATOR;
            query += "()";
            
            //compile the query
            return compileXQuery(broker, new StringSource(query), new XmldbURI[]{xqwsCollectionUri}, xqwsCollectionUri, null, null);
        }
        
        /**
    	 * Describes an XQWS by building an XML node representation of the XQWS module
    	 * 
    	 * <webservice>
    	 * 	<name/>
    	 * 	<description/>
    	 * 	<host/>
    	 * 	<path/>
    	 * 	<URL/>
    	 * 	<functions>
    	 * 		<function/> { unbounded } { @see org.exist.http.SOAPServer#describeWebServiceFunction(org.exist.xquery.FunctionSignature, org.exist.memtree.MemTreeBuilder) }
    	 * 	</functions>
    	 * </webservice>
    	 *
    	 * @param modXQWS	The XQWS XQuery module
    	 * @param xqwsFileUri	The File URI of the XQWS
    	 * @param request	The Http Servlet request for this webservice
    	 * @param path	The request path
    	 * @param functionName	Used when only a single function should be described, linked to functionResult
    	 * @param functionResult For writting out the results of a function call, should be used with functionName 
    	 * @return	An in-memory document describing the webservice
    	 */
    	private org.exist.memtree.DocumentImpl describeWebService(Module modXQWS, XmldbURI xqwsFileUri, HttpServletRequest request, String path, String functionName, Sequence functionResult) throws XPathException,SAXException
    	{
    		FunctionSignature[] xqwsFunctions = modXQWS.listFunctions();
            MemTreeBuilder builderWebserviceDoc = new MemTreeBuilder(broker.getXQueryService().newContext(AccessContext.REST));
    		builderWebserviceDoc.startDocument();
    		builderWebserviceDoc.startElement(new QName("webservice", null, null), null);
    		builderWebserviceDoc.startElement(new QName("name", null, null), null);
    		builderWebserviceDoc.characters(xqwsFileUri.toString().substring(0, xqwsFileUri.toString().indexOf(WEBSERVICE_MODULE_EXTENSION)));
    		builderWebserviceDoc.endElement();
    		builderWebserviceDoc.startElement(new QName("description", null, null), null);
    		builderWebserviceDoc.characters(modXQWS.getDescription());
    		builderWebserviceDoc.endElement();
    		builderWebserviceDoc.startElement(new QName("host", null, null), null);
    		builderWebserviceDoc.characters(request.getServerName() + ":" + request.getServerPort());
    		builderWebserviceDoc.endElement();
    		builderWebserviceDoc.startElement(new QName("path", null, null), null);
    		builderWebserviceDoc.characters(path);
    		builderWebserviceDoc.endElement();
    		builderWebserviceDoc.startElement(new QName("URL", null, null), null);
    		builderWebserviceDoc.characters(request.getRequestURL());
    		builderWebserviceDoc.endElement();
    		builderWebserviceDoc.startElement(new QName("functions", null, null), null);
    		for(int f = 0; f < xqwsFunctions.length; f++)
            {
    			if(functionName == null)
    			{
    				//All Function Descriptions
    				describeWebServiceFunction(xqwsFunctions[f], builderWebserviceDoc, null);
    			}
    			else
    			{
    				//Only a Single Function Description for showing function call results
    				if(xqwsFunctions[f].getName().getLocalName().equals(functionName))
    				{
    					describeWebServiceFunction(xqwsFunctions[f], builderWebserviceDoc, functionResult);
    					break;
    				}
    			}
            }
    		builderWebserviceDoc.endElement();
    		builderWebserviceDoc.endElement();
    		builderWebserviceDoc.endDocument();
    		
    		return builderWebserviceDoc.getDocument();
    	}
    	
    	/**
    	 * Describes an XQWS function by building an XML node representation of the function signature
    	 * 
    	 * 	<function>
    	 * 		<name/>
    	 * 		<description/>
    	 * 		<parameters>
    	 * 			<parameter>	{ unbounded }
    	 * 				<name/>
    	 * 				<type/>
    	 * 				<cardinality/>
    	 * 			</parameter>
    	 * 		</parameters>
    	 * 		<return>
    	 * 			<type/>
    	 * 			<cardinality/>
    	 * 			<result>		{ Only displayed if this is after the function has been executed }
    	 * 				either {
    	 * 					<value/> or
    	 * 					<sequence>
    	 * 						<value/> { unbounded }
    	 * 					</sequence>
    	 * 				}
    	 * 			</result>
    	 * 		</return>
    	 * 	</function>
    	 * 
    	 * @param signature	The function signature to describe
    	 * @param builderFunction	The MemTreeBuilder to write the description to
    	 * @param functionResult	A Sequence containing the function results or null if the function has not yet been executed
    	 */
    	private void describeWebServiceFunction(FunctionSignature signature, MemTreeBuilder builderFunction, Sequence functionResult) throws XPathException,SAXException
    	{
    		//Generate an XML snippet for each function
        	builderFunction.startElement(new QName("function", null, null), null);
        	builderFunction.startElement(new QName("name", null, null), null);
        	builderFunction.characters(signature.getName().getLocalName());
        	builderFunction.endElement();
        	if(signature.getDescription() != null)
        	{
        		builderFunction.startElement(new QName("description", null, null), null);
        		builderFunction.characters(signature.getDescription());
        		builderFunction.endElement();
        	}
        	SequenceType[] xqwsArguments = signature.getArgumentTypes();
        	builderFunction.startElement(new QName("parameters", null, null), null);
        	for(int a = 0; a < xqwsArguments.length; a++)
        	{
        		builderFunction.startElement(new QName("parameter",null, null), null);
        		builderFunction.startElement(new QName("name",null, null), null);
        		//builderFunction.characters(xqwsArguments[a].getNodeName().getLocalName()); //TODO: how to get parameter name?
        		builderFunction.endElement();
        		builderFunction.startElement(new QName("type",null, null), null);
        		builderFunction.characters(Type.getTypeName(xqwsArguments[a].getPrimaryType()));
        		builderFunction.endElement();
        		builderFunction.startElement(new QName("cardinality",null, null), null);
        		builderFunction.characters(Integer.toString(xqwsArguments[a].getCardinality()));
        		builderFunction.endElement();
        		builderFunction.endElement();
        	}
        	builderFunction.endElement();
        	builderFunction.startElement(new QName("return",null, null), null);
        	builderFunction.startElement(new QName("type",null, null), null);
        	builderFunction.characters(Type.getTypeName(signature.getReturnType().getPrimaryType()));
        	builderFunction.endElement();
        	int iReturnCardinality = signature.getReturnType().getCardinality();
        	builderFunction.startElement(new QName("cardinality",null, null), null);
        	builderFunction.characters(Integer.toString(iReturnCardinality));
        	builderFunction.endElement();
        	if(functionResult != null)
        	{
        		builderFunction.startElement(new QName("result", null, null), null);
        		
        		//determine result cardinality
        		DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(builderFunction);
        		if(iReturnCardinality >= Cardinality.MANY)
        		{
        			//sequence of values
        			builderFunction.startElement(new QName("sequence", null, null), null);
        			
        			for(int i = 0; i < functionResult.getItemCount(); i++)
        			{
            			builderFunction.startElement(new QName("value", null, null), null);
            			functionResult.itemAt(i).copyTo(broker, receiver);
            			//builderFunction.characters(functionResult.itemAt(i).getStringValue());
            			builderFunction.endElement();
        			}
        			
        			builderFunction.endElement();
        		}
        		else
        		{
        			//atomic value
        			builderFunction.startElement(new QName("value", null, null), null);
        			functionResult.itemAt(0).copyTo(broker, receiver);
        			//builderFunction.characters(functionResult.itemAt(0).getStringValue());
        			builderFunction.endElement();
        		}
        		
        		builderFunction.endElement();
        	}
        	builderFunction.endElement();
        	builderFunction.endElement();
    	}
        
    	/**
         * Transforms a document with a stylesheet
         * 
         * @param docStyleSheet	A stylesheet document from the db
         * @param parameters	Any parameters to be passed to the stylesheet
         * 
         * @return byte array containing the result of the transformation
         */
        private byte[] Transform(org.exist.memtree.DocumentImpl srcDoc, DocumentImpl docStyleSheet, Properties parameters) throws  TransformerConfigurationException, SAXException
        {
            //Transform docXQWSDescription with the stylesheet
        	
        	/*
        	 * TODO: the code in this try statement (apart from the WSDLFilter use) was mostly extracted from
        	 * transform:stream-transform(), it would be better to be able to share that code somehow
        	 */
        	
	        SAXTransformerFactory factory = (SAXTransformerFactory)SAXTransformerFactory.newInstance();
			TemplatesHandler templatesHandler = factory.newTemplatesHandler();
			templatesHandler.startDocument();
			Serializer serializer = broker.getSerializer();
			serializer.reset();
			WSDLFilter wsdlfilter = new WSDLFilter(templatesHandler, HttpServletRequestURL);
			serializer.setSAXHandlers(wsdlfilter, null);
			serializer.toSAX(docStyleSheet);
			templatesHandler.endDocument();
			
			TransformerHandler handler = factory.newTransformerHandler(templatesHandler.getTemplates());
			
			//set parameters, if any
			if(parameters != null)
			{
				Transformer transformer = handler.getTransformer();
				Enumeration parameterKeys = parameters.keys();
				while(parameterKeys.hasMoreElements())
				{
					String paramName = (String)parameterKeys.nextElement();
					Object paramValue = parameters.get(paramName);
					transformer.setParameter(paramName, paramValue);
				}
			}
			
			ByteArrayOutputStream os = new ByteArrayOutputStream();
            StreamResult result = new StreamResult(os);
            handler.setResult(result);
            
			handler.startDocument();
			srcDoc.toSAX(broker, handler, null);
			handler.endDocument();
			
			return os.toByteArray();
        }
    }
    
}
