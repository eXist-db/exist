/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
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
 *  $Id$
 */
package org.exist.http;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.http.servlets.HttpRequestWrapper;
import org.exist.http.servlets.HttpResponseWrapper;
import org.exist.http.servlets.RequestWrapper;
import org.exist.http.servlets.ResponseWrapper;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.source.DBSource;
import org.exist.source.Source;
import org.exist.source.StringSource;
import org.exist.storage.DBBroker;
import org.exist.storage.XQueryPool;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.storage.serializers.Serializer;
import org.exist.util.Lock;
import org.exist.util.LockException;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SAXSerializerPool;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.Pragma;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.request.RequestModule;
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

    protected final static String NS = "http://exist.sourceforge.net/NS/exist";

    protected final static String XUPDATE_NS = "http://www.xmldb.org/xupdate";

    protected final static Logger LOG = Logger.getLogger(RESTServer.class);

    protected final static Properties defaultProperties = new Properties();

    static {
        defaultProperties.setProperty(OutputKeys.INDENT, "yes");
        defaultProperties.setProperty(OutputKeys.ENCODING, "UTF-8");
        defaultProperties.setProperty(EXistOutputKeys.EXPAND_XINCLUDES, "yes");
        defaultProperties.setProperty(EXistOutputKeys.HIGHLIGHT_MATCHES,
                "elements");
        defaultProperties.setProperty(EXistOutputKeys.PROCESS_XSL_PI, "yes");
    }

    private final static DateFormat dateFormat = new SimpleDateFormat(
            "MMM d, yyyy hh:mm:ss");

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
    
    private String formEncoding;
	private String containerEncoding;
	
    public RESTServer(String formEncoding, String containerEncoding) {
    	this.formEncoding = formEncoding;
    	this.containerEncoding = containerEncoding;
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
     * <li>_xsl: an URI pointing to an XSL stylesheet that will be applied to
     * the returned XML.</li>
     * 
     * @param broker
     * @param parameters
     * @param path
     * @return
     * @throws BadRequestException
     * @throws PermissionDeniedException
     * @throws NotFoundException
     */
    public void doGet(DBBroker broker, HttpServletRequest request, HttpServletResponse response, String path)
            throws BadRequestException, PermissionDeniedException,
            NotFoundException, IOException {

        // Process special parameters

        int howmany = 10;
        int start = 1;
        boolean wrap = true;
        Properties outputProperties = new Properties();
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
        String p_start = (String) request.getParameter("_start");
        if (p_start != null) {
            try {
                start = Integer.parseInt(p_start);
            } catch (NumberFormatException nfe) {
                throw new BadRequestException(
                        "Parameter _start should be an int");
            }
        }
        String option;
        if ((option = request.getParameter("_wrap")) != null)
            wrap = option.equals("yes");
        if ((option = request.getParameter("_indent")) != null)
            outputProperties.setProperty(OutputKeys.INDENT, option);
        String stylesheet;
        if ((stylesheet = request.getParameter("_xsl")) != null) {
            if (stylesheet.equals("no"))
                outputProperties.setProperty(EXistOutputKeys.PROCESS_XSL_PI,
                        stylesheet);
            else
                outputProperties.setProperty(EXistOutputKeys.STYLESHEET,
                        stylesheet);
        } else
            outputProperties.setProperty(EXistOutputKeys.PROCESS_XSL_PI, "yes");
        LOG.debug("stylesheet = " + stylesheet);
        LOG.debug("query = " + query);
        String encoding;
        if ((encoding = request.getParameter("_encoding")) != null)
            outputProperties.setProperty(OutputKeys.ENCODING, encoding);
        else
            encoding = "UTF-8";
        
        // Process the request
        DocumentImpl resource = null;
        try {
            // first, check if path leads to an XQuery resource
            resource = (DocumentImpl) broker.openDocument(path, Lock.READ_LOCK);
            if (resource != null) {
                if (resource.getResourceType() == DocumentImpl.BINARY_FILE &&
                        "application/xquery".equals(resource.getMimeType())) {
                    // found an XQuery resource
                    try {
                    	String result = executeXQuery(broker, resource, request, response, outputProperties);
                    	encoding = outputProperties.getProperty(OutputKeys.ENCODING, encoding);
                    	String mimeType = outputProperties.getProperty(OutputKeys.MEDIA_TYPE, "text/html");
                        response.setContentType(mimeType);
                        writeResponse(response, result, encoding);
                    } catch (XPathException e) {
                        response.setContentType("text/html");
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        writeResponse(response, formatXPathException(query, path, e), encoding);
                    }
                    return;
                }
            }
            if (query != null) {
                // query parameter specified
                try {
                	String result = search(broker, query, path, howmany, start, outputProperties, wrap, request, response);
                	encoding = outputProperties.getProperty(OutputKeys.ENCODING, encoding);
                	String mimeType = outputProperties.getProperty(OutputKeys.MEDIA_TYPE, "text/html");
                    response.setContentType(mimeType);
                    writeResponse(response, result, encoding);
                } catch (XPathException e) {
                    response.setContentType("text/html");
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    writeResponse(response, formatXPathException(query, path, e), encoding);
                }
            } else {
                // no query parameter: try to load a document from the specified
                // path
                if (resource == null) {
                    // no document: check if path points to a collection
                    Collection collection = broker.getCollection(path);
                    if (collection != null) {
                        if (!collection.getPermissions().validate(
                                broker.getUser(), Permission.READ))
                            throw new PermissionDeniedException(
                                    "Not allowed to read collection");
                        // return a listing of the collection contents
                        writeResponse(response, printCollection(broker, collection), encoding);
                    } else {
                        throw new NotFoundException("Document " + path
                                + " not found");
                    }
                } else {
                    // document found: serialize it
                    if (!resource.getPermissions().validate(broker.getUser(),
                            Permission.READ))
                        throw new PermissionDeniedException(
                                "Not allowed to read resource");
                    if (resource.getResourceType() == DocumentImpl.BINARY_FILE) {
                        // binary resource
                        response.setContentType(resource.getMimeType());
                        writeResponse(response, broker.getBinaryResourceData((BinaryDocument) resource));
                    } else {
                        // xml resource
                        Serializer serializer = broker.getSerializer();
                        serializer.reset();

                        if (stylesheet != null) {
                            serializer.setStylesheet(resource, stylesheet);
                            response.setContentType("text/html");
                        }
                        try {
                            serializer.setProperties(outputProperties);
                            if (serializer.isStylesheetApplied())
                                response.setContentType("text/html");
                            writeResponse(response, serializer.serialize(resource), encoding);
                        } catch (SAXException saxe) {
                            LOG.warn(saxe);
                            throw new BadRequestException(
                                    "Error while serializing XML: "
                                            + saxe.getMessage());
                        }
                    }
                }
            }
        } finally {
            if (resource != null)
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
     * @return
     * @throws BadRequestException
     * @throws PermissionDeniedException
     */
    public void doPost(DBBroker broker, HttpServletRequest request, 
            HttpServletResponse response, String path)
            throws BadRequestException, PermissionDeniedException, IOException {
        Properties outputProperties = new Properties(defaultProperties);
        // first, check if path leads to an XQuery resource.
        // if yes, the resource is loaded and the XQuery executed.
        DocumentImpl resource = null;
        try {
            resource = (DocumentImpl) broker.openDocument(path, Lock.READ_LOCK);
            if (resource != null) {
                if (resource.getResourceType() == DocumentImpl.BINARY_FILE &&
                        "application/xquery".equals(resource.getMimeType())) {
                    // found an XQuery resource
                    try {
                    	String result = executeXQuery(broker, resource, request, response, outputProperties);
                    	String encoding = outputProperties.getProperty(OutputKeys.ENCODING, "UTF-8");
                    	String mimeType = outputProperties.getProperty(OutputKeys.MEDIA_TYPE, "text/html");
                        response.setContentType(mimeType);
                        writeResponse(response, result, encoding);
                    } catch (XPathException e) {
                        response.setContentType("text/html");
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        writeResponse(response, formatXPathException(null, path, e), "UTF-8");
                    }
                    return;
                }
            }
        } finally {
            if(resource != null)
                resource.getUpdateLock().release(Lock.READ_LOCK);
        }
        
        // normal POST: read the request content and check if
        // it is an XUpdate or a query request.
        boolean indent = true;
        boolean summary = false;
        int howmany = 10;
        int start = 1;
        boolean enclose = true;
        String mime = "text/xml";
        String query = null;
        try {
            String content = getRequestContent(request);
            InputSource src = new InputSource(new StringReader(content));
            DocumentBuilderFactory docFactory = DocumentBuilderFactory
                    .newInstance();
            docFactory.setNamespaceAware(true);
            DocumentBuilder docBuilder = null;
            try {
                docBuilder = docFactory.newDocumentBuilder();
            } catch (ParserConfigurationException e) {
                LOG.warn(e);
                throw new BadRequestException(e.getMessage());
            }
            Document doc = docBuilder.parse(src);
            Element root = doc.getDocumentElement();
            String rootNS = root.getNamespaceURI();
            if (rootNS != null && rootNS.equals(NS)) {
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
                    mime = "text/xml";
                    if ((option != null) && (!option.equals(""))) {
                        mime = option;
                    }

                    NodeList children = root.getChildNodes();
                    for (int i = 0; i < children.getLength(); i++) {
                        Node child = children.item(i);
                        if (child.getNodeType() == Node.ELEMENT_NODE
                                && child.getNamespaceURI().equals(NS)) {
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
                            } else if (child.getLocalName()
                                    .equals("properties")) {
                                Node node = child.getFirstChild();
                                while (node != null) {
                                    if (node.getNodeType() == Node.ELEMENT_NODE
                                            && node.getNamespaceURI()
                                                    .equals(NS)
                                            && node.getLocalName().equals(
                                                    "property")) {
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
                    response.setContentType(mime);
                    writeResponse(response, search(broker, query, path, howmany,
                            start, outputProperties, enclose, request, response),
                            outputProperties.getProperty(OutputKeys.ENCODING, "UTF-8"));
                } else
                    throw new BadRequestException("No query specified");
            } else if (rootNS != null && rootNS.equals(XUPDATE_NS)) {
                LOG.debug("Got xupdate request: " + content);
                DocumentSet docs = new DocumentSet();
                Collection collection = broker.getCollection(path);
                if (collection != null) {
                    collection.allDocs(broker, docs, true, true);
                } else {
                    DocumentImpl xupdateDoc = (DocumentImpl) broker
                            .getDocument(path);
                    if (xupdateDoc != null) {
                        if (!xupdateDoc.getPermissions().validate(
                                broker.getUser(), Permission.READ))
                            throw new PermissionDeniedException(
                                    "Not allowed to read collection");
                        docs.add(xupdateDoc);
                    } else
                        broker.getAllDocuments(docs);
                }
                XUpdateProcessor processor = new XUpdateProcessor(broker, docs);
                Modification modifications[] = processor.parse(new InputSource(
                        new StringReader(content)));
                long mods = 0;
                for (int i = 0; i < modifications.length; i++) {
                    mods += modifications[i].process();
                    broker.flush();
                }
                // FD : Returns an XML doc
                writeResponse(response,
                        "<?xml version='1.0'?>\n"
                        + "<exist:modifications mlns:exist='" + NS
                        + "' count='" + mods + "'>" + mods
                        + "modifications processed.</exist:modifications>",
                        "UTF-8");
                // END FD
            } else
                throw new BadRequestException("Unknown XML root element: "
                        + root.getNodeName());
        } catch (SAXException e) {
            Exception cause = e;
            if (e.getException() != null)
                cause = e.getException();
            LOG.debug("SAX exception while parsing request: "
                    + cause.getMessage(), cause);
            throw new BadRequestException(
                    "SAX exception while parsing request: "
                            + cause.getMessage());
        } catch (ParserConfigurationException e) {
            throw new BadRequestException(
                    "Parser exception while parsing request: " + e.getMessage());
        } catch (XPathException e) {
            throw new BadRequestException(
                    "Query exception while parsing request: " + e.getMessage());
        } catch (IOException e) {
            throw new BadRequestException(
                    "IO exception while parsing request: " + e.getMessage());
        } catch (EXistException e) {
            throw new BadRequestException(e.getMessage());
        } catch (LockException e) {
            throw new PermissionDeniedException(e.getMessage());
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
     * @param tempFile
     * @param contentType
     * @param docPath
     * @return
     * @throws BadRequestException
     * @throws PermissionDeniedException
     */
    public void doPut(DBBroker broker, File tempFile, String docPath,
            HttpServletRequest request, HttpServletResponse response) throws BadRequestException,
            PermissionDeniedException, IOException {
        if (tempFile == null)
            throw new BadRequestException("No request content found for PUT");
        
        try {
            int p = docPath.lastIndexOf('/');
            if (p < 0 || p == docPath.length() - 1)
                throw new BadRequestException("Bad path: " + docPath);
            else {
                String collectionName = docPath.substring(0, p);
                docPath = docPath.substring(p + 1);
                Collection collection = broker.getCollection(collectionName);
                if (collection == null) {
                    LOG.debug("creating collection " + collectionName);
                    collection = broker.getOrCreateCollection(collectionName);
                    broker.saveCollection(collection);
                }
                String url = tempFile.toURI().toASCIIString();
                MimeType mime;
                String contentType = request.getContentType();
                if (contentType != null)
                    mime = MimeTable.getInstance().getContentType(contentType);
                else {
                    mime = MimeTable.getInstance().getContentTypeFor(docPath);
                    if (mime != null)
                        contentType = mime.getName();
                }
                if (mime == null)
                    mime = MimeType.BINARY_TYPE;
                
                if (mime.isXMLType()) {
                    DocumentImpl doc = collection.addDocument(broker, docPath,
                            new InputSource(url), contentType);
                    response.sendError(HttpServletResponse.SC_OK, "Document " + docPath + " stored.");
                } else {
                    byte[] chunk = new byte[4096];
                    ByteArrayOutputStream os = new ByteArrayOutputStream();
                    FileInputStream is = new FileInputStream(tempFile);
                    int l;
                    while ((l = is.read(chunk)) > -1) {
                        os.write(chunk, 0, l);
                    }
                    collection.addBinaryResource(broker, docPath, os
                            .toByteArray(), contentType);
                    response.sendError(HttpServletResponse.SC_OK, "Document " + docPath + " stored as binary resource.");
                }
            }
        } catch (SAXParseException e) {
            throw new BadRequestException("Parsing exception at "
                    + e.getLineNumber() + "/" + e.getColumnNumber() + ": "
                    + e.toString());
        } catch (SAXException e) {
            Exception o = e.getException();
            if (o == null)
                o = e;
            throw new BadRequestException("Parsing exception: "
                    + o.getMessage());
        } catch (EXistException e) {
            throw new BadRequestException("Internal error: " + e.getMessage());
        } catch (TriggerException e) {
            throw new PermissionDeniedException(e.getMessage());
        } catch (LockException e) {
            throw new PermissionDeniedException(e.getMessage());
        }
        return;
    }

    public void doDelete(DBBroker broker, String path, HttpServletResponse response)
            throws PermissionDeniedException, NotFoundException, IOException {
        try {
            Collection collection = broker.getCollection(path);
            if (collection != null) {
                // remove the collection
                LOG.debug("removing collection " + path);
                broker.removeCollection(collection);
                response.sendError(HttpServletResponse.SC_OK, "Collection " + path + " removed.");
            } else {
                DocumentImpl doc = (DocumentImpl) broker.getDocument(path);
                if (doc == null)
                    throw new NotFoundException(
                            "No document or collection found " + "for path: "
                                    + path);
                else {
                    // remove the document
                    LOG.debug("removing document " + path);
                    int p = path.lastIndexOf('/');
                    String docName = p < 0 || p == path.length() - 1 ? path
                            : path.substring(p + 1);
                    if (doc.getResourceType() == DocumentImpl.BINARY_FILE)
                        doc.getCollection().removeBinaryResource(broker,
                                docName);
                    else
                        doc.getCollection().removeDocument(broker, docName);
                    response.sendError(HttpServletResponse.SC_OK, "Document " + path + " removed.");
                }
            }
        } catch (TriggerException e) {
            throw new PermissionDeniedException("Trigger failed: "
                    + e.getMessage());
        } catch (LockException e) {
            throw new PermissionDeniedException("Could not acquire lock: "
                    + e.getMessage());
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
        String result = null;
        try {
            Source source = new StringSource(query);
            XQuery xquery = broker.getXQueryService();
            XQueryPool pool = xquery.getXQueryPool();
            CompiledXQuery compiled = pool.borrowCompiledXQuery(broker, source);
            XQueryContext context;
            if (compiled == null)
                context = xquery.newContext();
            else
                context = compiled.getContext();
            context.setStaticallyKnownDocuments(new String[] { path });
            declareVariables(context, request, response);
            
            if (compiled == null)
                compiled = xquery.compile(context, source);
            checkPragmas(context, outputProperties);
            try {
                long startTime = System.currentTimeMillis();
                Sequence resultSequence = xquery.execute(compiled, null);
                long queryTime = System.currentTimeMillis() - startTime;
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
		context.declareNamespace(RequestModule.PREFIX, RequestModule.NAMESPACE_URI);
		context.declareVariable(RequestModule.PREFIX + ":request", reqw);
		context.declareVariable(RequestModule.PREFIX + ":response", respw);
		context.declareVariable(RequestModule.PREFIX + ":session", reqw.getSession());
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
        if(compiled == null)
            context = xquery.newContext();
        else
            context = compiled.getContext();
        context.setModuleLoadPath("xmldb:exist://" + resource.getCollection().getName());
        context.setStaticallyKnownDocuments(
                new String[] { resource.getCollection().getName() }
        );
        declareVariables(context, request, response);
        if(compiled == null) {
            try {
                compiled = xquery.compile(context, source);
            } catch (IOException e) {
                throw new BadRequestException("Failed to read query from " + resource.getName(), e);
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

    /**
     * Check if the XQuery contains pragmas that define serialization settings.
     * If yes, copy the corresponding settings to the current set of output
     * properties.
     * 
     * @param context
     */
    protected void checkPragmas(XQueryContext context, Properties properties)
            throws XPathException {
        Pragma pragma = context.getPragma(Pragma.SERIALIZE_QNAME);
        if (pragma == null)
            return;
        String[] contents = pragma.tokenizeContents();
        for (int i = 0; i < contents.length; i++) {
            String[] pair = Pragma.parseKeyValuePair(contents[i]);
            if (pair == null)
                throw new XPathException("Unknown parameter found in "
                        + pragma.getQName().toString() + ": '" + contents[i]
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
            serializer = SAXSerializerPool.getInstance().borrowSAXSerializer();
            serializer.setWriter(writer);
            serializer.setOutputProperties(defaultProperties);
            AttributesImpl attrs = new AttributesImpl();

            serializer.startDocument();
            serializer.startPrefixMapping("exist", NS);
            serializer.startElement(NS, "result", "exist:result", attrs);

            attrs.addAttribute("", "name", "name", "CDATA", collection
                    .getName());
            printPermissions(attrs, collection.getPermissions());

            serializer
                    .startElement(NS, "collection", "exist:collection", attrs);

            for (Iterator i = collection.collectionIterator(); i.hasNext();) {
                String child = (String) i.next();
                Collection childCollection = broker.getCollection(collection
                        .getName()
                        + '/' + child);
                if (childCollection.getPermissions().validate(broker.getUser(),
                        Permission.READ)) {
                    attrs.clear();
                    attrs.addAttribute("", "name", "name", "CDATA", child);

                    attrs.addAttribute("", "created", "created", "CDATA",
                            dateFormat.format(new Date(childCollection
                                    .getCreationTime())));
                    printPermissions(attrs, childCollection.getPermissions());
                    serializer.startElement(NS, "collection",
                            "exist:collection", attrs);
                    serializer.endElement(NS, "collection", "exist:collection");
                }
            }

            for (Iterator i = collection.iterator(broker); i.hasNext();) {
                DocumentImpl doc = (DocumentImpl) i.next();
                if (doc.getPermissions().validate(broker.getUser(),
                        Permission.READ)) {
                    String resource = doc.getFileName();
                    int p = resource.lastIndexOf('/');
                    attrs.clear();
                    attrs.addAttribute("", "name", "name", "CDATA",
                            p < 0 ? resource : resource.substring(p + 1));
                    attrs.addAttribute("", "created", "created", "CDATA",
                            dateFormat.format(new Date(doc.getCreated())));
                    attrs.addAttribute("", "last-modified", "last-modified",
                            "CDATA", dateFormat.format(new Date(doc
                                    .getLastModified())));
                    printPermissions(attrs, doc.getPermissions());
                    serializer.startElement(NS, "resource", "exist:resource",
                            attrs);
                    serializer.endElement(NS, "resource", "exist:resource");
                }
            }

            serializer.endElement(NS, "collection", "exist:collection");
            serializer.endElement(NS, "result", "exist:result");

            serializer.endDocument();
        } catch (SAXException e) {
            // should never happen
            LOG.warn("Error while serializing collection contents: "
                    + e.getMessage(), e);
        } finally {
            SAXSerializerPool.getInstance().returnSAXSerializer(serializer);
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
        int rlen = results.getLength();
        if (rlen > 0) {
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
            sax = SAXSerializerPool.getInstance().borrowSAXSerializer();
            sax.setWriter(writer);
            sax.setOutputProperties(outputProperties);
            serializer.setProperties(outputProperties);
            serializer.setSAXHandlers(sax, sax);

            serializer.toSAX(results, start, howmany, wrap);

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
    
    private void writeResponse(HttpServletResponse response, byte[] data)
    throws IOException {
        OutputStream is = response.getOutputStream();
        is.write(data);
    }
    
    private void writeResponse(HttpServletResponse response, String data, String encoding)
    throws IOException {
        response.setCharacterEncoding(encoding);
        OutputStream is = response.getOutputStream();
        is.write(data.getBytes(encoding));
    }
}
