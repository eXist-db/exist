/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  wolfgang@exist-db.org
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
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 *  $Id: RESTServer.java 3567 2006-05-19 13:37:34 +0000 (Fri, 19 May 2006) wolfgang_m $
 */
package org.exist.http;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TemplatesHandler;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.dom.QName;
import org.exist.http.servlets.HttpRequestWrapper;
import org.exist.http.servlets.HttpResponseWrapper;
import org.exist.http.servlets.RequestWrapper;
import org.exist.http.servlets.ResponseWrapper;
import org.exist.memtree.DocumentBuilderReceiver;
import org.exist.memtree.MemTreeBuilder;
import org.exist.security.PermissionDeniedException;
import org.exist.security.xacml.AccessContext;
import org.exist.source.Source;
import org.exist.source.StringSource;
import org.exist.storage.DBBroker;
import org.exist.storage.XQueryPool;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.serializers.WSDLFilter;
import org.exist.xmldb.XmldbURI;
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
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 *
 * @author Adam Retter (adam.retter@devon.gov.uk)
 */
public class SOAPServer
{
	private String formEncoding;			//TODO: we may be able to remove this eventually, in favour of HttpServletRequestWrapper being setup in EXistServlet, currently used for doPost() but perhaps could be used for other Request Methods? - deliriumsky
	private String containerEncoding;
	
	private final static String ENCODING = "UTF-8";
	private final static String SEPERATOR = System.getProperty("line.separator");
	private final static String XSLT_WEBSERVICE_WSDL = "/db/system/webservice/wsdl.xslt";
	private final static String XSLT_WEBSERVICE_DESCRIPTION = "/db/system/webservice/description.xslt";
	private final static String XSLT_WEBSERVICE_FUNCTION = "/db/system/webservice/function.xslt";
	public final static String WEBSERVICE_MODULE_EXTENSION = ".xqws";

	
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
	
	//Constructor
    public SOAPServer(String formEncoding, String containerEncoding)
    {
        this.formEncoding = formEncoding;
        this.containerEncoding = containerEncoding;
    }
	
    /**
     * Gets XQWS file from the db
     * 
     * @param broker 	The Database Broker to use
     * @param path		The Path to the XQWS
     * @param docXQWS	A reference to a Binary Document, will be the XQWS file when the function returns
     * 
     * @return	The XQWS BinaryDocument
     */
    private BinaryDocument getXQWS(DBBroker broker, String path) throws PermissionDeniedException
    {
        XmldbURI pathUri = XmldbURI.create(path);        
        BinaryDocument docXQWS = (BinaryDocument) broker.getXMLResource(pathUri, Lock.READ_LOCK);
        
        //close the XQWS Document and release the read lock
        docXQWS.getUpdateLock().release();
        
        return docXQWS;
    }
    
    /**
     * Gets the data from an XQWS Binary Document
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
        //context.setModuleLoadPath(XmldbURI.EMBEDDED_SERVER_URI.append(docXQWS.getCollection().getURI()).toString());
        context.setModuleLoadPath(XmldbURI.EMBEDDED_SERVER_URI.append(xqwsCollectionUri).toString());
        //context.setStaticallyKnownDocuments(new XmldbURI[] { docXQWS.getCollection().getURI() });
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
    
    private CompiledXQuery SimpleXQueryIncludeModule(DBBroker broker, XmldbURI xqwsFileUri, QName xqwsNamespace, XmldbURI xqwsCollectionUri, HttpServletRequest request, HttpServletResponse response) throws XPathException
    {
        //Create a simple XQuery wrapper to access the module
        String query = "xquery version \"1.0\";" + SEPERATOR;
        query += SEPERATOR;
        query += "import module namespace " + xqwsNamespace.getLocalName() + "=\"" + xqwsNamespace.getNamespaceURI() + "\" at \"" + xqwsFileUri.toString() + "\";" + SEPERATOR;
        query += SEPERATOR;
        query += "()";
        
        //compile the query
        return compileXQuery(broker, new StringSource(query), new XmldbURI[] { xqwsCollectionUri}, xqwsCollectionUri, request, response);
    }
    
    /**
     * Transforms a document with a stylesheet and streams the result to the http response
     * 
     * @param broker	The DBBroker to use
     * @param docWebService	An in-memory document describing the webservice
     * @param docStyleSheet	A stylesheet document from the db
     * @param request	The Http Servlets request for this webservice
     * @param response The Http Servlets response to the request for this webservice
     * @param path	The request path
     */
    private void StreamTransform(DBBroker broker, org.exist.memtree.DocumentImpl docWebService, DocumentImpl docStyleSheet, HttpServletRequest request, HttpServletResponse response, String path) throws IOException
    {
        //Transform docWebservice with the stylesheet
        MemTreeBuilder outputBuilder = new MemTreeBuilder();
        outputBuilder.startDocument();
        try
		{
        	/*
        	 * TODO: the code in this try statement (apart from the WSDLFilter use) was mostly extracted from
        	 * transform:stream-transform(), it would be better to be able to share that code somehow
        	 */
        	
	        SAXTransformerFactory factory = (SAXTransformerFactory)SAXTransformerFactory.newInstance();
			TemplatesHandler templatesHandler = factory.newTemplatesHandler();
			templatesHandler.startDocument();
			Serializer serializer = broker.getSerializer();
			serializer.reset();
			WSDLFilter wsdlfilter = new WSDLFilter(templatesHandler, request.getRequestURL().toString());
			serializer.setSAXHandlers(wsdlfilter, null);
			serializer.toSAX(docStyleSheet);
			templatesHandler.endDocument();
			
			TransformerHandler handler = factory.newTransformerHandler(templatesHandler.getTemplates());
			
			//START send result of transformation directly to response
			OutputStream os = new BufferedOutputStream(response.getOutputStream());
            StreamResult result = new StreamResult(os);
            handler.setResult(result);
			//END		
            
            /**
             * TODO: Validation should be done before WSDL is sent to the client. org.exist.validation.Validator
             * will need to make use of org.exist.validation.internal.BlockingOutputStream to connect to the Validator.
             * 
             */
            
			handler.startDocument();
			docWebService.toSAX(broker, handler);
			handler.endDocument();
		}
        catch(TransformerConfigurationException tce)
        {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeResponse(response, formatXPathException(null, path, new XPathException(null, "SAX exception while transforming node: " + tce.getMessage(), tce)), "text/html", ENCODING);
        }
		catch (SAXException saxe)
		{
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeResponse(response, formatXPathException(null, path, new XPathException(null, "SAX exception while transforming node: " + saxe.getMessage(), saxe)), "text/html", ENCODING);
		}
		finally
		{
			//close the Stylesheet Document and release the read lock
			docStyleSheet.getUpdateLock().release();
		}
    }
    
    //proceses requests for description documents (WSDL, human readable, human readable specific function)
	public void doGet(DBBroker broker, HttpServletRequest request, HttpServletResponse response, String path) throws BadRequestException, PermissionDeniedException, NotFoundException, IOException
	{
    	//set the encoding
		if (request.getCharacterEncoding() == null)
			request.setCharacterEncoding(formEncoding);
    	
		/* Process the request */
		
		/*
		 * TODO: I think simple webservices can also be called using GET, so we may need to cater for that as well
		 * but first it would be best to write the doPost() method, split the code out into functions and also use it for this.
		 */
		
		// 1) Get the xqws
		BinaryDocument docXQWS = getXQWS(broker, path);
		byte[] xqwsData = getXQWSData(broker, docXQWS);
        
        // 2) move through the xqws char by char checking if a line contains the module namespace declaration
        QName xqwsNamespace = getXQWSNamespace(xqwsData);
        
        // 3) Compile a Simple XQuery to access the module
        CompiledXQuery compiled;
        try
        {
        	compiled = SimpleXQueryIncludeModule(broker, docXQWS.getFileURI(), xqwsNamespace, docXQWS.getCollection().getURI(), request, response);
        }
        catch(XPathException e)
        {
        	response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeResponse(response, formatXPathException(null, path, new XPathException(null, "XPath exception while compiling xquery: " + e.getMessage(), e)), "text/html", ENCODING);
        	return;
        }
        
        // 5) Inspect the XQWS and its function signatures and create a small XML document to represent it
        Module modXQWS = compiled.getContext().getModule(xqwsNamespace.getNamespaceURI());
        org.exist.memtree.DocumentImpl docWebService = describeWebService(modXQWS, docXQWS.getFileURI(), request, path);
        
        // 6) Transform the XML document to either a human readable description, description of a specific function or WSDL
		DocumentImpl docStyleSheet = null;
		
		//get the appropraite stylesheet
        if(request.getParameter("WSDL") != null || request.getParameter("wsdl") != null)
        {
        	//WSDL
        	docStyleSheet = broker.getXMLResource(XmldbURI.create(XSLT_WEBSERVICE_WSDL), Lock.READ_LOCK);

        	//set output content type for wsdl
            response.setContentType("text/xml");
        }
        else if(request.getParameter("function") != null)
        {
        	//Specific Function Description
        	docStyleSheet = broker.getXMLResource(XmldbURI.create(XSLT_WEBSERVICE_FUNCTION), Lock.READ_LOCK);
        }
        else
        {
        	//Human Readable Description
        	docStyleSheet = broker.getXMLResource(XmldbURI.create(XSLT_WEBSERVICE_DESCRIPTION), Lock.READ_LOCK);
        }

        //do the transformation to the response
        StreamTransform(broker, docWebService, docStyleSheet, request, response, path);        
    }   
	 
	//returns the node with the name "definitions" from the NodeList or its children (recursively)
	public Node getDefinitionsNode(NodeList nl)
    {
		Node result = null;
		
        for(int i = 0; i < nl.getLength(); i ++)
        {
        	Node n = nl.item(i);
        	
        	if(n.getNodeType() == Type.ELEMENT)
        	{
            	if(n.getNodeName().equals("definitions"))
            	{
            		//found node
            		result = n;
            		break;
            	}
            	
            	if(n.hasChildNodes())
            	{
            		result = getDefinitionsNode(n.getChildNodes());
            	}
        	}
        }
        
        return result;
    }
	
	//process incomoing SOAP requests
	public void doPost(DBBroker broker, HttpServletRequest request, HttpServletResponse response, String path) throws BadRequestException, PermissionDeniedException, IOException
    {	
		//read the incoming SOAP request
		java.io.BufferedInputStream is = new java.io.BufferedInputStream(request.getInputStream());
		byte[] buf = new byte[is.available()];
		is.read(buf);
		
		//create an XML Document from the SOAP Request
		Document soapRequest = BuildXMLDocument(buf);
		
		//Examine the SOAP Request
		
		System.out.println(new String(buf, "UTF-8"));
    }
	

	private Document BuildXMLDocument(byte[] strXML)
	{
		try
		{ 
			//try and construct xml document from input stream, we use eXist's in-memory DOM implementation
			SAXParserFactory factory = SAXParserFactory.newInstance();
			factory.setNamespaceAware(true);	
			//TODO : we should be able to cope with context.getBaseURI()				
			InputSource src = new InputSource(new ByteArrayInputStream(strXML));
			SAXParser parser = factory.newSAXParser();
			XMLReader reader = parser.getXMLReader();
            MemTreeBuilder builder = new MemTreeBuilder();
            DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(builder);
			reader.setContentHandler(receiver);
			reader.parse(src);
			return receiver.getDocument();
		}
		catch (ParserConfigurationException e)
		{				
			//do nothing, we will default to trying to return a string below
		}
		catch (SAXException e)
		{
			//do nothing, we will default to trying to return a string below
		}
		catch (IOException e)
		{
			//do nothing, we will default to trying to return a string below
		}
		
		return null;
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
	 * 		@see org.exist.http.SOAPServer#describeFunction(org.exist.xquery.FunctionSignature, org.exist.memtree.MemTreeBuilder)
	 * 	</functions>
	 * </webservice>
	 *
	 * @param modXQWS	The XQWS XQuery module
	 * @param xqwsFileUri	The File URI of the XQWS
	 * @param request	The Http Servlet request for this webservice
	 * @param path	The request path
	 *  
	 * @return	An in-memory document describing the webservice
	 */
	private org.exist.memtree.DocumentImpl describeWebService(Module modXQWS, XmldbURI xqwsFileUri, HttpServletRequest request, String path)
	{
		FunctionSignature[] xqwsFunctions = modXQWS.listFunctions();
        MemTreeBuilder builderWebserviceDoc = new MemTreeBuilder();
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
			if(request.getParameter("function") != null)
			{
				//Specific Function Description
				if(xqwsFunctions[f].getName().getLocalName().equals(request.getParameter("function")))
				{
					describeFunction(xqwsFunctions[f], builderWebserviceDoc);
					break;
				}
			}
			else
			{
				//All Function Descriptions
				describeFunction(xqwsFunctions[f], builderWebserviceDoc);
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
	 * 			<parameter>
	 * 				<name/>
	 * 				<type/>
	 * 				<cardinality/>
	 * 			</parameter>
	 * 		</parameters>
	 * 		<return>
	 * 			<type/>
	 * 			<cardinality/>
	 * 		</return>
	 * 	</function>
	 * 
	 * @param signature	The function signature to describe
	 * @param builderFunction	The MemTreeBuilder to write the description to
	 */
	private void describeFunction(FunctionSignature signature, MemTreeBuilder builderFunction)
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
    	builderFunction.startElement(new QName("cardinality",null, null), null);
    	builderFunction.characters(Integer.toString(signature.getReturnType().getCardinality()));
    	builderFunction.endElement();
    	builderFunction.endElement();
    	builderFunction.endElement();
	}
	
    //TODO: SHARE THIS FUNCTION WITH RESTServer (copied at the moment)
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
        RequestWrapper reqw = new HttpRequestWrapper(request, formEncoding, containerEncoding);
        ResponseWrapper respw = new HttpResponseWrapper(response);
        //context.declareNamespace(RequestModule.PREFIX, RequestModule.NAMESPACE_URI);
        context.declareVariable(RequestModule.PREFIX + ":request", reqw);
        context.declareVariable(ResponseModule.PREFIX + ":response", respw);
        context.declareVariable(SessionModule.PREFIX + ":session", reqw.getSession());
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
//        response.setCharacterEncoding(encoding);
        
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
}
