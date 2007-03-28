/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2007 The eXist team
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.http;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URI;
import java.util.Date;
import java.util.Iterator;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerConfigurationException;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.Namespaces;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentMetadata;
import org.exist.dom.DocumentSet;
import org.exist.http.servlets.HttpRequestWrapper;
import org.exist.http.servlets.HttpResponseWrapper;
import org.exist.http.servlets.RequestWrapper;
import org.exist.http.servlets.ResponseWrapper;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.xacml.AccessContext;
import org.exist.source.DBSource;
import org.exist.source.Source;
import org.exist.source.StringSource;
import org.exist.storage.DBBroker;
import org.exist.storage.XQueryPool;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionException;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.Constants;
import org.exist.xquery.Option;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.request.RequestModule;
import org.exist.xquery.functions.response.ResponseModule;
import org.exist.xquery.functions.session.SessionModule;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.DateTimeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xupdate.Modification;
import org.exist.xupdate.XUpdateProcessor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.AttributesImpl;

/**
 *
 * @author wolf
 *
 */
public class RESTServer {
    
    protected final static Logger LOG = Logger.getLogger(RESTServer.class);
    
    // Should we not obey the instance's defaults? /ljo
    protected final static Properties defaultProperties = new Properties();
    
    static {
        defaultProperties.setProperty(OutputKeys.INDENT, "yes");
        defaultProperties.setProperty(OutputKeys.ENCODING, "UTF-8");
        defaultProperties.setProperty(OutputKeys.MEDIA_TYPE, MimeType.XML_TYPE.getName());
        defaultProperties.setProperty(EXistOutputKeys.EXPAND_XINCLUDES, "yes");
        defaultProperties.setProperty(EXistOutputKeys.HIGHLIGHT_MATCHES, "elements");
        defaultProperties.setProperty(EXistOutputKeys.PROCESS_XSL_PI, "yes");
    }

    protected final static Properties defaultOutputKeysProperties = new Properties();
    
    static {
        defaultOutputKeysProperties.setProperty(OutputKeys.INDENT, "yes");
        defaultOutputKeysProperties.setProperty(OutputKeys.ENCODING, "UTF-8");
        defaultOutputKeysProperties.setProperty(OutputKeys.MEDIA_TYPE, MimeType.XML_TYPE.getName());
    }
    
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
    
    private String formEncoding;			//TODO: we may be able to remove this eventually, in favour of HttpServletRequestWrapper being setup in EXistServlet, currently used for doPost() but perhaps could be used for other Request Methods? - deliriumsky
    private String containerEncoding;
    private boolean useDynamicContentType;

    //Constructor
    public RESTServer(String formEncoding, String containerEncoding, boolean useDynamicContentType)
    {
        this.formEncoding = formEncoding;
        this.containerEncoding = containerEncoding;
        this.useDynamicContentType = useDynamicContentType;
    }
    
    /**
     * Handle GET request. In the simplest case just returns the document or
     * binary resource specified in the path. If the path leads to a collection,
     * a listing of the collection contents is returned. If it resolves to a binary
     * resource with mime-type "application/xquery", this resource will be
     * loaded and executed by the XQuery engine.
     *
     * The method also recognizes a number of predefined parameters:
     *
     * <ul>
     * <li>_xpath or _query: if specified, the given query is executed on the
     * current resource or collection.</li>
     *
     * <li>_howmany: defines how many items from the query result will be
     * returned.</li>
     *
     * <li>_start: a start offset into the result set.</li>
     *
     * <li>_wrap: if set to "yes", the query results will be wrapped into a
     * exist:result element.</li>
     *
     * <li>_indent: if set to "yes", the returned XML will be pretty-printed.
     * </li>
     *
     * <li>_source: if set to "yes" and a resource with mime-type "application/xquery" is requested
     * then the xquery will not be executed, instead the source of the document will be returned.
     * Must be enabled in descriptor.xml with the following syntax 
     * <xquery-app><allow-source><xquery path="/db/mycollection/myquery.xql"/></allow-source></xquery-app>
     * </li>
     * 
     * <li>_xsl: an URI pointing to an XSL stylesheet that will be applied to
     * the returned XML.</li>
     *
     * @param broker
     * @param request
     * @param response
     * @param path
     * @throws BadRequestException
     * @throws PermissionDeniedException
     * @throws NotFoundException
     */
    public void doGet(DBBroker broker, HttpServletRequest request, HttpServletResponse response, String path)
    throws BadRequestException, PermissionDeniedException,
            NotFoundException, IOException {
    	
    	//if required, set character encoding
    	if (request.getCharacterEncoding() == null)
			request.setCharacterEncoding(formEncoding);
    	
        // Process special parameters
        
        int howmany = 10;
        int start = 1;
        boolean wrap = true;
        boolean source = false;
        Properties outputProperties = new Properties(defaultOutputKeysProperties);
        String query = request.getParameter("_xpath");
        if (query == null)
            query = request.getParameter("_query");
        
        String p_howmany = request.getParameter("_howmany");
        if (p_howmany != null) {
            try {
                howmany = Integer.parseInt(p_howmany);
            } catch (NumberFormatException nfe) {
                throw new BadRequestException(
                        "Parameter _howmany should be an int");
            }
        }
        String p_start = request.getParameter("_start");
        if (p_start != null) {
            try {
                start = Integer.parseInt(p_start);
            } catch (NumberFormatException nfe) {
                throw new BadRequestException(
                        "Parameter _start should be an int");
            }
        }
        String option;
        if ((option = request.getParameter("_wrap")) != null) {
            wrap = option.equals("yes");
	}
        if ((option = request.getParameter("_indent")) != null) {
            outputProperties.setProperty(OutputKeys.INDENT, option);
	}
        if((option = request.getParameter("_source")) != null) {
        	source = option.equals("yes");
	}
        String stylesheet;
        if ((stylesheet = request.getParameter("_xsl")) != null) {
            if (stylesheet.equals("no")) {
                outputProperties.setProperty(EXistOutputKeys.PROCESS_XSL_PI, "no");
                outputProperties.remove(EXistOutputKeys.STYLESHEET);
                stylesheet = null;
            } else {
                outputProperties.setProperty(EXistOutputKeys.STYLESHEET,
                        stylesheet);
	    }
        } else {
            outputProperties.setProperty(EXistOutputKeys.PROCESS_XSL_PI, "yes");
	}
        LOG.debug("stylesheet = " + stylesheet);
        LOG.debug("query = " + query);
        String encoding;
        if ((encoding = request.getParameter("_encoding")) != null)
            outputProperties.setProperty(OutputKeys.ENCODING, encoding);
        else
            encoding = "UTF-8";

	String mimeType = outputProperties.getProperty(OutputKeys.MEDIA_TYPE);
        
        // Process the request
        DocumentImpl resource = null;
        XmldbURI pathUri = XmldbURI.create(path);
        try {
            // check if path leads to an XQuery resource
            resource = broker.getXMLResource(pathUri, Lock.READ_LOCK);
            if (resource != null) {
                if (resource.getResourceType() == DocumentImpl.BINARY_FILE && MimeType.XQUERY_TYPE.getName().equals(resource.getMetadata().getMimeType())) {
		    // found an XQuery resource
                    
		    //Should we display the source of the XQuery or execute it
		    Descriptor descriptor = Descriptor.getDescriptorSingleton();
		    if(source && descriptor != null) {
			//show the source
			
			//check are we allowed to show the xquery source - descriptor.xml
			if(descriptor.allowSourceXQuery(path)) {
			    //TODO: change writeResourceAs to use a serializer that will serialize xquery to syntax coloured xhtml, replace the asMimeType parameter with a method for specifying the serializer, or split the code into two methods. - deliriumsky
			    
			    //Show the source of the XQuery
			    writeResourceAs(resource, broker, stylesheet, encoding, MimeType.TEXT_TYPE.getName(), outputProperties, response);
			} else {
			    //we are not allowed to show the source - query not allowed in descriptor.xml
                            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Permission to view XQuery source for: " + path + " denied. Must be explicitly defined in descriptor.xml");
			    return;
			}
		    } else { //Execute the XQuery
			try {
                            String result = executeXQuery(broker, resource, request, response, outputProperties);
                            encoding = outputProperties.getProperty(OutputKeys.ENCODING);
			    mimeType = outputProperties.getProperty(OutputKeys.MEDIA_TYPE);
                            
			    //only write the response if it is not already committed,
			    //some xquery functions can write directly to the response
                            if(!response.isCommitted()) {
                           	writeResponse(response, result, mimeType, encoding);
                            }
                        } catch (XPathException e) {
                            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			    if (MimeType.XML_TYPE.getName().equals(mimeType)) {
				writeResponse(response, formatXPathException(query, path, e), mimeType, encoding);				
			    } else {
				writeResponse(response, formatXPathExceptionHtml(query, path, e), MimeType.HTML_TYPE.getName(), encoding);
			    }
                        }
		    }
                    return;
                }
            }
            if (query != null) {
                // query parameter specified
                try {
                    String result = search(broker, query, path, howmany, start, outputProperties, wrap, request, response);
                    encoding = outputProperties.getProperty(OutputKeys.ENCODING);
		    mimeType = outputProperties.getProperty(OutputKeys.MEDIA_TYPE);
                    
                	//only write the response if it is not already committed,
                	//some xquery functions can write directly to the response
                    if(!response.isCommitted()) {
                    	writeResponse(response, result, mimeType, encoding);
                    }
                    
                } catch (XPathException e) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
		    if (MimeType.XML_TYPE.getName().equals(mimeType)) {
			writeResponse(response, formatXPathException(query, path, e), mimeType, encoding);				
		    } else {
                        writeResponse(response, formatXPathExceptionHtml(query, path, e), MimeType.HTML_TYPE.getName(), encoding);
                    }
	       }
            } else {
                // no query parameter: try to load a document from the specified
                // path
                if (resource == null) {
                    // no document: check if path points to a collection
                    Collection collection = broker.getCollection(pathUri);
                    if (collection != null) {
                        if (!collection.getPermissions().validate(
			    broker.getUser(), Permission.READ))
                            throw new PermissionDeniedException(
                                    "Not allowed to read collection");
                        // return a listing of the collection contents
                        writeResponse(response, printCollection(broker, collection), MimeType.XML_TYPE.getName(), encoding);
                    } else {
                        throw new NotFoundException("Document " + path
                                + " not found");
                    }
                } else {
                    // document found: serialize it
                    writeResourceAs(resource, broker, stylesheet, encoding, null, outputProperties, response);
                }
            }
        } finally {
            if (resource != null)
                resource.getUpdateLock().release(Lock.READ_LOCK);
        }
    }
    
    //writes out a resource, uses asMimeType as the specified mime-type or if null uses the type of the resource
    private void writeResourceAs(DocumentImpl resource, DBBroker broker, String stylesheet, String encoding, String asMimeType, Properties outputProperties, HttpServletResponse response) throws BadRequestException, PermissionDeniedException, IOException {
    	//Do we have permission to read the resource
    	if (!resource.getPermissions().validate(broker.getUser(), Permission.READ)) {
	    throw new PermissionDeniedException("Not allowed to read resource");
	}
    	
        if (resource.getResourceType() == DocumentImpl.BINARY_FILE) {
	    // binary resource
	    if(asMimeType != null)  { //was a mime-type specified?
		
		response.setContentType(asMimeType);
	    } else {
		response.setContentType(resource.getMetadata().getMimeType());
	    }
	    OutputStream os = response.getOutputStream();
	    broker.readBinaryResource((BinaryDocument) resource, os);
	    os.flush();
        } else {
            // xml resource
            Serializer serializer = broker.getSerializer();
            serializer.reset();
            
	    
            //Serialize the document
            try {
                //use a stylesheet if specified in query parameters
                if (stylesheet != null) {
                    serializer.setStylesheet(resource, stylesheet);
                }
                serializer.setProperties(outputProperties);
                serializer.prepareStylesheets(resource);
                if(asMimeType != null) { //was a mime-type specified?
		    response.setContentType(asMimeType+"; charset="+encoding);
                } else {
		    if (serializer.isStylesheetApplied() || serializer.hasXSLPi(resource) != null) {
			asMimeType = serializer.getStylesheetProperty(OutputKeys.MEDIA_TYPE);
			if (!useDynamicContentType || asMimeType == null)
			    asMimeType = MimeType.HTML_TYPE.getName();
			LOG.debug("media-type: " + asMimeType);
			response.setContentType(asMimeType + "; charset="+encoding);
		    } else {
			response.setContentType(resource.getMetadata().getMimeType() + "; charset=" + encoding);
		    }
                }
                OutputStream is = response.getOutputStream();
                Writer w = new OutputStreamWriter(is, encoding);
                serializer.serialize(resource,w);
                w.flush();
                w.close();
            } catch (SAXException saxe) {
                LOG.warn(saxe);
                throw new BadRequestException("Error while serializing XML: " + saxe.getMessage());
            } catch (TransformerConfigurationException e) {
                LOG.warn(e);
                throw new BadRequestException(e.getMessageAndLocation());
            }
        }
    }
    
    public void doHead(DBBroker broker, HttpServletRequest request, HttpServletResponse response, String path)
    throws BadRequestException, PermissionDeniedException,
            NotFoundException, IOException {
        DocumentImpl resource = null;
        XmldbURI pathUri = XmldbURI.create(path);
        try {
            resource = broker.getXMLResource(pathUri, Lock.READ_LOCK);
            if(resource == null) {
                throw new NotFoundException("Resource " + pathUri + " not found");
            }
            if(!resource.getPermissions().validate(broker.getUser(), Permission.READ)) {
                throw new PermissionDeniedException("Permission to read resource " + path + " denied");
            }
            DocumentMetadata metadata = resource.getMetadata();
            response.setContentType(metadata.getMimeType());
            response.setContentLength(resource.getContentLength());
            response.addDateHeader("Last-Modified", metadata.getLastModified());
            response.addDateHeader("Created", metadata.getCreated());
        } finally {
            if(resource != null)
                resource.getUpdateLock().release(Lock.READ_LOCK);
        }
    }
    
    /**
     * Handles POST requests. If the path leads to a binary resource with
     * mime-type "application/xquery", that resource will be read and executed
     * by the XQuery engine. Otherwise, the request content is loaded and parsed
     * as XML. It may either contain an XUpdate or a query request.
     *
     * @param broker
     * @param request
     * @param response
     * @param path
     * @throws BadRequestException
     * @throws PermissionDeniedException
     */
    public void doPost(DBBroker broker, HttpServletRequest request, HttpServletResponse response, String path) throws BadRequestException, PermissionDeniedException, IOException
    {	
    	//if required, set character encoding
    	if (request.getCharacterEncoding() == null)
			request.setCharacterEncoding(formEncoding);
    
        Properties outputProperties = new Properties(defaultOutputKeysProperties);
        XmldbURI pathUri = XmldbURI.create(path);
        DocumentImpl resource = null;
	
	String encoding = outputProperties.getProperty(OutputKeys.ENCODING);
	String mimeType = outputProperties.getProperty(OutputKeys.MEDIA_TYPE);
        try {
            // check if path leads to an XQuery resource.
            // if yes, the resource is loaded and the XQuery executed.
            resource = broker.getXMLResource(pathUri, Lock.READ_LOCK);
            if (resource != null) {
                if (resource.getResourceType() == DocumentImpl.BINARY_FILE &&
		    MimeType.XQUERY_TYPE.getName().equals(resource.getMetadata().getMimeType())) {
                    // found an XQuery resource
                    try {
                        String result = executeXQuery(broker, resource, request, response, outputProperties);
			encoding = outputProperties.getProperty(OutputKeys.ENCODING);
                        mimeType = outputProperties.getProperty(OutputKeys.MEDIA_TYPE);
                        writeResponse(response, result, mimeType, encoding);
                    } catch (XPathException e) {
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			if (MimeType.XML_TYPE.getName().equals(mimeType)) {
			    writeResponse(response, formatXPathException(null, path, e), mimeType, encoding);				
			} else {
			    writeResponse(response, formatXPathExceptionHtml(null, path, e), MimeType.HTML_TYPE.getName(), encoding);
			}
                    }
                    return;
                }
            }
        } finally {
            if(resource != null)
                resource.getUpdateLock().release(Lock.READ_LOCK);
        }
        
        // third, normal POST: read the request content and check if
        // it is an XUpdate or a query request.        
        int howmany = 10;
        int start = 1;
        boolean enclose = true;
        String mime = MimeType.XML_TYPE.getName();
        String query = null;
        TransactionManager transact = broker.getBrokerPool().getTransactionManager();
        Txn transaction = transact.beginTransaction();
        try {
            String content = getRequestContent(request);
            InputSource src = new InputSource(new StringReader(content));
            DocumentBuilderFactory docFactory = DocumentBuilderFactory
                    .newInstance();
            docFactory.setNamespaceAware(true);
            DocumentBuilder docBuilder;
            try {
                docBuilder = docFactory.newDocumentBuilder();
            } catch (ParserConfigurationException e) {
                LOG.warn(e);
                transact.abort(transaction);
                throw new BadRequestException(e.getMessage());
            }
            Document doc = docBuilder.parse(src);
            Element root = doc.getDocumentElement();
            String rootNS = root.getNamespaceURI();
            if (rootNS != null && rootNS.equals(Namespaces.EXIST_NS)) {
                if (root.getLocalName().equals("query")) {
                    // process <query>xpathQuery</query>
                    String option = root.getAttribute("start");
                    if (option != null)
                        try {
                            start = Integer.parseInt(option);
                        } catch (NumberFormatException e) {
                        }
                    option = root.getAttribute("max");
                    if (option != null)
                        try {
                            howmany = Integer.parseInt(option);
                        } catch (NumberFormatException e) {
                        }
                    
                    option = root.getAttribute("enclose");
                    if (option != null) {
                        if (option.equals("no"))
                            enclose = false;
                    }
                    
                    option = root.getAttribute("mime");
                    mime = MimeType.XML_TYPE.getName();
                    if ((option != null) && (!option.equals(""))) {
                        mime = option;
                    }
                    
                    NodeList children = root.getChildNodes();
                    for (int i = 0; i < children.getLength(); i++) {
                        Node child = children.item(i);
                        if (child.getNodeType() == Node.ELEMENT_NODE
                                && child.getNamespaceURI().equals(Namespaces.EXIST_NS)) {
                            if (child.getLocalName().equals("text")) {
                                StringBuffer buf = new StringBuffer();
                                Node next = child.getFirstChild();
                                while (next != null) {
                                    if (next.getNodeType() == Node.TEXT_NODE
                                            || next.getNodeType() == Node.CDATA_SECTION_NODE)
                                        buf.append(next.getNodeValue());
                                    next = next.getNextSibling();
                                }
                                query = buf.toString();
                            } else if (child.getLocalName().equals("properties")) {
                                Node node = child.getFirstChild();
                                while (node != null) {
                                    if (node.getNodeType() == Node.ELEMENT_NODE
                                            && node.getNamespaceURI().equals(Namespaces.EXIST_NS)
                                            && node.getLocalName().equals("property")) {
                                        Element property = (Element) node;
                                        String key = property
                                                .getAttribute("name");
                                        String value = property
                                                .getAttribute("value");
                                        LOG.debug(key + " = " + value);
                                        if (key != null && value != null)
                                            outputProperties.setProperty(key,
                                                    value);
                                    }
                                    node = node.getNextSibling();
                                }
                            }
                        }
                    }
                }
                // execute query
                if (query != null) {
                    writeResponse(response, search(broker, query, path, howmany,
                            start, outputProperties, enclose, request, response), mime,
                            outputProperties.getProperty(OutputKeys.ENCODING));
                } else {
                    transact.abort(transaction);
                    throw new BadRequestException("No query specified");
                }
            } else if (rootNS != null && rootNS.equals(XUpdateProcessor.XUPDATE_NS)) {
                LOG.debug("Got xupdate request: " + content);
                DocumentSet docs = new DocumentSet();
                Collection collection = broker.getCollection(pathUri);
                if (collection != null) {
                    collection.allDocs(broker, docs, true, true);
                } else {
                    DocumentImpl xupdateDoc = (DocumentImpl) broker.getXMLResource(pathUri);
                    if (xupdateDoc != null) {
                        if (!xupdateDoc.getPermissions().validate(
                                broker.getUser(), Permission.READ)) {
                            transact.abort(transaction);
                            throw new PermissionDeniedException(
                                    "Not allowed to read collection");
                        }
                        docs.add(xupdateDoc);
                    } else
                        broker.getAllXMLResources(docs);
                }
                
                XUpdateProcessor processor = new XUpdateProcessor(broker, docs, AccessContext.REST);
                Modification modifications[] = processor.parse(new InputSource(
                        new StringReader(content)));
                long mods = 0;
                for (int i = 0; i < modifications.length; i++) {
                    mods += modifications[i].process(transaction);
                    broker.flush();
                }
                transact.commit(transaction);
                
                // FD : Returns an XML doc
                writeResponse(response,
                        "<?xml version='1.0'?>\n"
                        + "<exist:modifications xmlns:exist='" + Namespaces.EXIST_NS
                        + "' count='" + mods + "'>" + mods
                        + "modifications processed.</exist:modifications>",
                        MimeType.XML_TYPE.getName(), "UTF-8");
                // END FD
            } else {
                transact.abort(transaction);
                throw new BadRequestException("Unknown XML root element: "
                        + root.getNodeName());
            }
        } catch (SAXException e) {
            transact.abort(transaction);
            Exception cause = e;
            if (e.getException() != null)
                cause = e.getException();
            LOG.debug("SAX exception while parsing request: "
                    + cause.getMessage(), cause);
            throw new BadRequestException(
                    "SAX exception while parsing request: "
                    + cause.getMessage());
        } catch (ParserConfigurationException e) {
            transact.abort(transaction);
            throw new BadRequestException(
                    "Parser exception while parsing request: " + e.getMessage());
        } catch (XPathException e) {
            transact.abort(transaction);
            throw new BadRequestException(
                    "Query exception while parsing request: " + e.getMessage());
        } catch (IOException e) {
            transact.abort(transaction);
            throw new BadRequestException(
                    "IO exception while parsing request: " + e.getMessage());
        } catch (EXistException e) {
            transact.abort(transaction);
            throw new BadRequestException(e.getMessage());
        } catch (LockException e) {
            transact.abort(transaction);
            throw new PermissionDeniedException(e.getMessage());
        }
    }
    
    /**
     * Creates an input source from a URL location with an optional
     * known charset.
     */
    private InputSource createInputSource(String charset,URI location)
       throws java.io.IOException
    {
       if (charset==null) {
          return new InputSource(location.toASCIIString());
       } else {
          InputSource source = new InputSource(new InputStreamReader(location.toURL().openStream(),charset));
          source.setSystemId(location.toASCIIString());
          return source;
       }
    }

    /**
     * Handles PUT requests. The request content is stored as a new resource at
     * the specified location. If the resource already exists, it is overwritten if the
     * user has write permissions.
     *
     * The resource type depends on the content type specified in the HTTP header.
     * The content type will be looked up in the global mime table. If the corresponding
     * mime type is not a know XML mime type, the resource will be stored as a binary
     * resource.
     *
     * @param broker
     * @param tempFile The temp file from which the PUT will get its content
     * @param path The path to which the file should be stored
     * @param request
     * @param response
     * @throws BadRequestException
     * @throws PermissionDeniedException
     */
    public void doPut(DBBroker broker, File tempFile, XmldbURI path,
            HttpServletRequest request, HttpServletResponse response) throws BadRequestException,
            PermissionDeniedException, IOException {
        if (tempFile == null)
            throw new BadRequestException("No request content found for PUT");
        
        TransactionManager transact = broker.getBrokerPool().getTransactionManager();
        Txn transaction = transact.beginTransaction();
        try {
        	XmldbURI docUri = path.lastSegment();
        	XmldbURI collUri = path.removeLastSegment();

            if (docUri==null || collUri==null) {
                transact.abort(transaction);
                throw new BadRequestException("Bad path: " + path);
            }
            Collection collection = broker.getCollection(collUri);
            if (collection == null) {
                LOG.debug("creating collection " + collUri);
                collection = broker.getOrCreateCollection(transaction, collUri);
                broker.saveCollection(transaction, collection);
            }
            MimeType mime;
            String contentType = request.getContentType();
            String charset = null;
            if (contentType != null) {
                int semicolon = contentType.indexOf(';');
                if (semicolon>0) {
                    contentType = contentType.substring(0,semicolon).trim();
                    int equals = contentType.indexOf('=',semicolon);
                    if (equals>0) {
                        String param = contentType.substring(semicolon+1,equals).trim();
                        if (param.compareToIgnoreCase("charset=")==0) {
                            charset = param.substring(equals+1).trim();
                        }
                    }
                }
                mime = MimeTable.getInstance().getContentType(contentType);
            } else {
                mime = MimeTable.getInstance().getContentTypeFor(docUri);
                if (mime != null)
                    contentType = mime.getName();
            }
            if (mime == null)
                mime = MimeType.BINARY_TYPE;
            
            if (mime.isXMLType()) {
                URI url = tempFile.toURI();
                IndexInfo info = collection.validateXMLResource(transaction, broker, docUri, createInputSource(charset,url));
                info.getDocument().getMetadata().setMimeType(contentType);
                collection.store(transaction, broker, info, createInputSource(charset,url), false);
                response.sendError(HttpServletResponse.SC_OK, "Document " + docUri + " stored.");
            } else {

                FileInputStream is = new FileInputStream(tempFile);
                collection.addBinaryResource(transaction, broker, docUri, is, contentType, (int) tempFile.length());
                is.close();
                response.sendError(HttpServletResponse.SC_OK, "Document " + docUri + " stored as binary resource.");
            }
            
            transact.commit(transaction);
        } catch (SAXParseException e) {
            transact.abort(transaction);
            throw new BadRequestException("Parsing exception at "
                    + e.getLineNumber() + "/" + e.getColumnNumber() + ": "
                    + e.toString());
        } catch (SAXException e) {
            transact.abort(transaction);
            Exception o = e.getException();
            if (o == null)
                o = e;
            throw new BadRequestException("Parsing exception: "
                    + o.getMessage());
        } catch (EXistException e) {
            transact.abort(transaction);
            throw new BadRequestException("Internal error: " + e.getMessage());
        } catch (TriggerException e) {
            transact.abort(transaction);
            throw new PermissionDeniedException(e.getMessage());
        } catch (LockException e) {
            transact.abort(transaction);
            throw new PermissionDeniedException(e.getMessage());
        }
        return;
    }
    
    public void doDelete(DBBroker broker, XmldbURI path, HttpServletResponse response)
    throws PermissionDeniedException, NotFoundException, IOException {
        TransactionManager transact = broker.getBrokerPool().getTransactionManager();
        Txn txn = transact.beginTransaction();
        try {
            Collection collection = broker.getCollection(path);
            if (collection != null) {
                // remove the collection
                LOG.debug("removing collection " + path);
                broker.removeCollection(txn, collection);
                response.sendError(HttpServletResponse.SC_OK, "Collection " + path + " removed.");
            } else {
                DocumentImpl doc = (DocumentImpl) broker.getXMLResource(path);
                if (doc == null) {
                    transact.abort(txn);
                    throw new NotFoundException(
                            "No document or collection found " + "for path: "
                            + path);
                } else {
                    // remove the document
                    LOG.debug("removing document " + path);
                    if (doc.getResourceType() == DocumentImpl.BINARY_FILE)
                        doc.getCollection().removeBinaryResource(txn, broker,
                        		path.lastSegment());
                    else
                        doc.getCollection().removeXMLResource(txn, broker, path.lastSegment());
                    response.sendError(HttpServletResponse.SC_OK, "Document " + path + " removed.");
                }
            }
            transact.commit(txn);
        } catch (TriggerException e) {
            transact.abort(txn);
            throw new PermissionDeniedException("Trigger failed: "
                    + e.getMessage());
        } catch (LockException e) {
            transact.abort(txn);
            throw new PermissionDeniedException("Could not acquire lock: "
                    + e.getMessage());
        } catch (TransactionException e) {
            transact.abort(txn);
            LOG.warn("Transaction aborted: " + e.getMessage(), e);
        }
    }
    
    private String getRequestContent(HttpServletRequest request) throws IOException, UnsupportedEncodingException {
        String encoding = request.getCharacterEncoding();
        if(encoding == null)
            encoding = "UTF-8";
        InputStream is = request.getInputStream();
        Reader  reader = new InputStreamReader(is, encoding);
        StringWriter content = new StringWriter();
        char ch[] = new char[4096];
        int len = 0;
        while((len = reader.read(ch)) > -1)
            content.write(ch, 0, len);
        String xml = content.toString();
        return xml;
    }
    
    /**
     * TODO: pass request and response objects to XQuery.
     *
     * @throws XPathException
     */
    protected String search(DBBroker broker, String query, String path,
            int howmany, int start, Properties outputProperties, boolean wrap,
            HttpServletRequest request, HttpServletResponse response)
            throws BadRequestException, PermissionDeniedException,
            XPathException {
        XmldbURI pathUri = XmldbURI.create(path);
        try {
            Source source = new StringSource(query);
            XQuery xquery = broker.getXQueryService();
            XQueryPool pool = xquery.getXQueryPool();
            CompiledXQuery compiled = pool.borrowCompiledXQuery(broker, source);
            XQueryContext context;
            if (compiled == null)
                context = xquery.newContext(AccessContext.REST);
            else
                context = compiled.getContext();
            context.setStaticallyKnownDocuments(new XmldbURI[] { pathUri });
            context.setBaseURI(new AnyURIValue(pathUri.toString()));
            declareVariables(context, request, response);
            
            if (compiled == null)
                compiled = xquery.compile(context, source);
            checkPragmas(context, outputProperties);
            try {
                long startTime = System.currentTimeMillis();
                Sequence resultSequence = xquery.execute(compiled, null);
                long queryTime = System.currentTimeMillis() - startTime;
                if (LOG.isDebugEnabled())
                	LOG.debug("Found " + resultSequence.getLength() + " in "
                        + queryTime + "ms.");
                return printResults(broker, resultSequence, howmany, start,
                        queryTime, outputProperties, wrap);
            } finally {
                pool.returnCompiledXQuery(source, compiled);
            }
        } catch (IOException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
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
    private void declareVariables(XQueryContext context, HttpServletRequest request, HttpServletResponse response) throws XPathException {
        RequestWrapper reqw = new HttpRequestWrapper(request, formEncoding, containerEncoding);
        ResponseWrapper respw = new HttpResponseWrapper(response);
        //context.declareNamespace(RequestModule.PREFIX, RequestModule.NAMESPACE_URI);
        context.declareVariable(RequestModule.PREFIX + ":request", reqw);
        context.declareVariable(ResponseModule.PREFIX + ":response", respw);
        context.declareVariable(SessionModule.PREFIX + ":session", reqw.getSession());
    }
    
    /**
     * Directly execute an XQuery stored as a
     * binary document in the database.
     */
    private String executeXQuery(DBBroker broker, DocumentImpl resource,
            HttpServletRequest request, HttpServletResponse response,
            Properties outputProperties) throws XPathException, BadRequestException {
        Source source = new DBSource(broker, (BinaryDocument)resource, true);
        XQuery xquery = broker.getXQueryService();
        XQueryPool pool = xquery.getXQueryPool();
        XQueryContext context;
        CompiledXQuery compiled = pool.borrowCompiledXQuery(broker, source);
        if(compiled == null) {
        	// special header to indicate that the query is not returned from cache
        	response.setHeader("X-XQuery-Cached", "false");
            context = xquery.newContext(AccessContext.REST);
    	} else {
        	response.setHeader("X-XQuery-Cached", "true");
            context = compiled.getContext();
        }
        //TODO: don't hardcode this?
        context.setModuleLoadPath(XmldbURI.EMBEDDED_SERVER_URI.append(resource.getCollection().getURI()).toString());
        context.setStaticallyKnownDocuments(
                 new XmldbURI[] { resource.getCollection().getURI() }
        );
        declareVariables(context, request, response);
        if(compiled == null) {
            try {
                compiled = xquery.compile(context, source);
            } catch (IOException e) {
                throw new BadRequestException("Failed to read query from " + resource.getURI(), e);
            }
        }
        checkPragmas(context, outputProperties);
        try {
            Sequence result = xquery.execute(compiled, null);
            return printResults(broker, result, -1, 1, 0, outputProperties, false);
        } finally {
            pool.returnCompiledXQuery(source, compiled);
        }
    }
    
    /**
     * @param query
     * @param path
     * @param e
     */
    private String formatXPathExceptionHtml(String query, String path, XPathException e) {
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

    /**
     * @param query
     * @param path
     * @param e
     */
    private String formatXPathException(String query, String path, XPathException e) {
        StringWriter writer = new StringWriter();
        writer.write("<?xml version=\"1.0\" ?>");
        writer.write("<exception><path>");
        writer.write(path);
        writer.write("</path>");
        writer.write("<message>");
        writer.write(e.getMessage());
        writer.write("</message>");
        if(query != null) {
            writer.write("<query>");
            writer.write(query);
            writer.write("</query>");
        }
        writer.write("</exception>");
        return writer.toString();
    }
    
    /**
     * Check if the XQuery contains pragmas that define serialization settings.
     * If yes, copy the corresponding settings to the current set of output
     * properties.
     *
     * @param context
     */
    protected void checkPragmas(XQueryContext context, Properties properties)
    throws XPathException {
        Option pragma = context.getOption(Option.SERIALIZE_QNAME);
        if (pragma == null)
            return;
        String[] contents = pragma.tokenizeContents();
        for (int i = 0; i < contents.length; i++) {
            String[] pair = Option.parseKeyValuePair(contents[i]);
            if (pair == null)
                throw new XPathException("Unknown parameter found in "
                        + pragma.getQName().getStringValue() + ": '" + contents[i]
                        + "'");
            LOG.debug("Setting serialization property from pragma: " + pair[0]
                    + " = " + pair[1]);
            properties.setProperty(pair[0], pair[1]);
        }
    }
    
    protected String printCollection(DBBroker broker, Collection collection) {
        SAXSerializer serializer = null;
        StringWriter writer = new StringWriter();
        try {
            serializer = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);
            
            serializer.setOutput(writer, defaultProperties);
            AttributesImpl attrs = new AttributesImpl();
            
            serializer.startDocument();
            serializer.startPrefixMapping("exist", Namespaces.EXIST_NS);
            serializer.startElement(Namespaces.EXIST_NS, "result", "exist:result", attrs);
            
            attrs.addAttribute("", "name", "name", "CDATA", collection
                    .getURI().toString());
            printPermissions(attrs, collection.getPermissions());
            
            serializer.startElement(Namespaces.EXIST_NS, "collection", "exist:collection", attrs);
            
            for (Iterator i = collection.collectionIterator(); i.hasNext();) {
                XmldbURI child = (XmldbURI) i.next();
                 Collection childCollection = broker.getCollection(collection.getURI().append(child));
                if (childCollection!=null && childCollection.getPermissions().validate(broker.getUser(),
                        Permission.READ)) {
                    attrs.clear();
                    attrs.addAttribute("", "name", "name", "CDATA", child.toString());
                   
                    //add an attribute for the creation date as an xs:dateTime 
                    try
                    {
                    	DateTimeValue dtCreated = new DateTimeValue(new Date(childCollection.getCreationTime()));
                    	attrs.addAttribute("", "created", "created", "CDATA", dtCreated.getStringValue());
                    }
                    catch(XPathException e)
                    {
                    	//fallback to long value
                    	attrs.addAttribute("", "created", "created", "CDATA", String.valueOf(childCollection.getCreationTime()));
                    }
                    
                    printPermissions(attrs, childCollection.getPermissions());
                    serializer.startElement(Namespaces.EXIST_NS, "collection",
                            "exist:collection", attrs);
                    serializer.endElement(Namespaces.EXIST_NS, "collection", "exist:collection");
                }
            }
            
            for (Iterator i = collection.iterator(broker); i.hasNext();) {
                DocumentImpl doc = (DocumentImpl) i.next();
                if (doc.getPermissions().validate(broker.getUser(),
                        Permission.READ)) {
                    XmldbURI resource = doc.getFileURI();
                    DocumentMetadata metadata = doc.getMetadata();
                    attrs.clear();
                    attrs.addAttribute("", "name", "name", "CDATA", resource.toString());
                    
                    //add an attribute for the creation date as an xs:dateTime 
                    try
                    {
                    	DateTimeValue dtCreated = new DateTimeValue(new Date(metadata.getCreated()));
                    	attrs.addAttribute("", "created", "created", "CDATA", dtCreated.getStringValue());
                    }
                    catch(XPathException e)
                    {
                    	//fallback to long value
                    	attrs.addAttribute("", "created", "created", "CDATA", String.valueOf(metadata.getCreated()));
                    }
                    
                    //add an attribute for the last modified date as an xs:dateTime 
                    try
                    {
                    	DateTimeValue dtLastModified = new DateTimeValue(new Date(metadata.getLastModified()));
                    	attrs.addAttribute("", "last-mofified", "last-modified", "CDATA", dtLastModified.getStringValue());
                    }
                    catch(XPathException e)
                    {
                    	//fallback to long value
                    	attrs.addAttribute("", "last-modified", "last-modified", "CDATA", String.valueOf(metadata.getLastModified()));
                    }
                   
                    printPermissions(attrs, doc.getPermissions());
                    serializer.startElement(Namespaces.EXIST_NS, "resource", "exist:resource",
                            attrs);
                    serializer.endElement(Namespaces.EXIST_NS, "resource", "exist:resource");
                }
            }
            
            serializer.endElement(Namespaces.EXIST_NS, "collection", "exist:collection");
            serializer.endElement(Namespaces.EXIST_NS, "result", "exist:result");
            
            serializer.endDocument();
        } catch (SAXException e) {
            // should never happen
            LOG.warn("Error while serializing collection contents: "
                    + e.getMessage(), e);
        } finally {
            SerializerPool.getInstance().returnObject(serializer);
        }
        return writer.toString();
    }
    
    protected void printPermissions(AttributesImpl attrs, Permission perm) {
        attrs.addAttribute("", "owner", "owner", "CDATA", perm.getOwner());
        attrs.addAttribute("", "group", "group", "CDATA", perm.getOwnerGroup());
        attrs.addAttribute("", "permissions", "permissions", "CDATA", perm
                .toString());
    }
    
    protected String printResults(DBBroker broker, Sequence results,
            int howmany, int start, long queryTime,
            Properties outputProperties, boolean wrap)
            throws BadRequestException {        
        if (!results.isEmpty()) {
        	int rlen = results.getLength();
            if ((start < 1) || (start > rlen))
                throw new BadRequestException("Start parameter out of range");
            // FD : correct bound evaluation
            if (((howmany + start) > rlen) || (howmany <= 0))
                howmany = rlen - start + 1;
        } else
            howmany = 0;
        Serializer serializer = broker.getSerializer();
        serializer.reset();
        outputProperties.setProperty(Serializer.GENERATE_DOC_EVENTS, "false");
        SAXSerializer sax = null;
        try {
            StringWriter writer = new StringWriter();
            sax = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);
            sax.setOutput(writer, outputProperties);
            serializer.setProperties(outputProperties);
            serializer.setSAXHandlers(sax, sax);
            
            serializer.toSAX(results, start, howmany, wrap);
            
            SerializerPool.getInstance().returnObject(sax);
            return writer.toString();
        } catch (SAXException e) {
            LOG.warn(e);
            throw new BadRequestException("Error while serializing xml: "
                    + e.toString(), e);
        } catch (Exception e) {
            LOG.warn(e.getMessage(), e);
            throw new BadRequestException("Error while serializing xml: "
                    + e.toString(), e);
        }
    }
    
    private void writeResponse(HttpServletResponse response, String data, String contentType, String encoding) throws IOException
    {
    	//response.setCharacterEncoding(encoding);
        // possible format contentType: text/xml; charset=UTF-8
        if ( contentType != null && !response.isCommitted() ) {
            
            int semicolon = contentType.indexOf(';');
            if (semicolon != Constants.STRING_NOT_FOUND) {
                contentType = contentType.substring(0,semicolon);
            }
           
            response.setContentType(contentType + "; charset=" + encoding);
        }
        
        OutputStream is = response.getOutputStream();
        is.write(data.getBytes(encoding));
    }
}
