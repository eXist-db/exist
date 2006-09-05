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
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

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
		DocumentImpl docXQWS = null;
        XmldbURI pathUri = XmldbURI.create(path);        
        docXQWS = (DocumentImpl) broker.getXMLResource(pathUri, Lock.READ_LOCK);
        BinaryDocument bin = (BinaryDocument)docXQWS;
        byte[] xqwsData = broker.getBinaryResource(bin);
        
        // 2) move through the xqws char by char checking if a line contains the module namespace declaration
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
        
        //close the XQWS Document and release the read lock
        docXQWS.getUpdateLock().release();
        
        // 3): create an XQuery wrapper to access the module
        String query = "xquery version \"1.0\";" + SEPERATOR;
        query += SEPERATOR;
        query += "import " + sbNamespace.toString().substring(0, sbNamespace.length() - 2) + " at \"" + docXQWS.getFileURI().toString() + "\";" + SEPERATOR;
        query += SEPERATOR;
        query += "()";
        Source source = new StringSource(query);        
        
		// 4) Compile the XQuery
        XQuery xquery = broker.getXQueryService();
		XQueryPool pool = xquery.getXQueryPool();
        XQueryContext context;
        
        //try and get pre-compiled XQuery
        CompiledXQuery compiled = pool.borrowCompiledXQuery(broker, source);

        //Create the context and set a header to indicate cache status
        if(compiled == null)
        {
        	context = xquery.newContext(AccessContext.REST);
        	response.setHeader("X-XQuery-Cached", "false");
    	}
        else
    	{
        	context = compiled.getContext();
        	response.setHeader("X-XQuery-Cached", "true");
        }
        
        try
        {
	        //Setup the context
	        declareVariables(context, request, response);
	        context.setModuleLoadPath(XmldbURI.EMBEDDED_SERVER_URI.append(docXQWS.getCollection().getURI()).toString());
	        context.setStaticallyKnownDocuments(new XmldbURI[] { docXQWS.getCollection().getURI() });
	        
	        //no pre-compiled XQuery so compile, it
	        if(compiled == null)
	        {
	            try
	            {
	                compiled = xquery.compile(context, source);
	            }
	            catch (IOException e)
	            {
	                throw new BadRequestException("Failed to read query from " + docXQWS.getURI(), e);
	            }
	        }
        }
        catch (XPathException e)
		{
            response.setContentType("text/html");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeResponse(response, formatXPathException(null, path, e), ENCODING);
        }
        
        //store the compiled xqws for use later 
        pool.returnCompiledXQuery(source, compiled);
        
        // 5) Inspect the xqws function signatures and create a small xml doc to represent them
        Module xqws = compiled.getContext().getModule(sbNamespace.substring(sbNamespace.indexOf("\"")+1, sbNamespace.lastIndexOf("\"")));
        FunctionSignature[] xqwsFunctions = xqws.listFunctions();
        MemTreeBuilder builderWebserviceDoc = new MemTreeBuilder();
		builderWebserviceDoc.startDocument();
		builderWebserviceDoc.startElement(new QName("webservice", null, null), null);
		builderWebserviceDoc.startElement(new QName("name", null, null), null);
		builderWebserviceDoc.characters(docXQWS.getFileURI().toString().substring(0, docXQWS.getFileURI().toString().indexOf(WEBSERVICE_MODULE_EXTENSION)));
		builderWebserviceDoc.endElement();
		builderWebserviceDoc.startElement(new QName("description", null, null), null);
		builderWebserviceDoc.characters(xqws.getDescription());
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
					ConstructFunctionNode(xqwsFunctions[f], builderWebserviceDoc);
					break;
				}
			}
			else
			{
				//All Function Descriptions
				ConstructFunctionNode(xqwsFunctions[f], builderWebserviceDoc);
			}
        }
		builderWebserviceDoc.endElement();
		builderWebserviceDoc.endElement();
		builderWebserviceDoc.endDocument();
		org.exist.memtree.DocumentImpl docWebservice = builderWebserviceDoc.getDocument();
        
        // 6) Transform the XML document to either a human readable description, description of a specific function or WSDL
		DocumentImpl docStylesheet = null;
		
		//get the appropraite stylesheet
        if(request.getParameter("WSDL") != null || request.getParameter("wsdl") != null)
        {
        	//WSDL
        	docStylesheet = broker.getXMLResource(XmldbURI.create(XSLT_WEBSERVICE_WSDL), Lock.READ_LOCK);

        	//set output content type for wsdl
            response.setContentType("text/xml");
        }
        else if(request.getParameter("function") != null)
        {
        	//Specific Function Description
        	docStylesheet = broker.getXMLResource(XmldbURI.create(XSLT_WEBSERVICE_FUNCTION), Lock.READ_LOCK);
        }
        else
        {
        	//Human Readable Description
        	docStylesheet = broker.getXMLResource(XmldbURI.create(XSLT_WEBSERVICE_DESCRIPTION), Lock.READ_LOCK);
        }
        
        
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
			serializer.toSAX(docStylesheet);
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
			docWebservice.toSAX(broker, handler);
			handler.endDocument();
		}
        catch(TransformerConfigurationException tce)
        {
        	response.setContentType("text/html");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeResponse(response, formatXPathException(null, path, new XPathException(null, "SAX exception while transforming node: " + tce.getMessage(), tce)), ENCODING);
        }
		catch (SAXException e)
		{
			response.setContentType("text/html");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeResponse(response, formatXPathException(null, path, new XPathException(null, "SAX exception while transforming node: " + e.getMessage(), e)), ENCODING);
		}
		
        //close the Stylesheet Document and release the read lock
		docStylesheet.getUpdateLock().release();
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
	
	//TODO: process incomoing SOAP documents
	public void doPost(DBBroker broker, HttpServletRequest request, HttpServletResponse response, String path) throws BadRequestException, PermissionDeniedException, IOException
    {	
    
    }
	
	//builds an XML node called function
	/*
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
	 */
	private void ConstructFunctionNode(FunctionSignature signature, MemTreeBuilder builderFunction)
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
    private void writeResponse(HttpServletResponse response, String data, String encoding) throws IOException
    {
//        response.setCharacterEncoding(encoding);
        
        // possible format contentType: text/xml; charset=UTF-8
        String contentType = response.getContentType();
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
