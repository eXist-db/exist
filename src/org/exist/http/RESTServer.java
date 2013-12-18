/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2013 The eXist Project
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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerConfigurationException;
import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.Namespaces;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.debuggee.DebuggeeFactory;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DefaultDocumentSet;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentMetadata;
import org.exist.dom.MutableDocumentSet;
import org.exist.dom.QName;
import org.exist.dom.XMLUtil;
import static org.exist.http.RESTServerParameter.*;
import org.exist.http.servlets.HttpRequestWrapper;
import org.exist.http.servlets.HttpResponseWrapper;
import org.exist.http.servlets.ResponseWrapper;
import org.exist.http.urlrewrite.XQueryURLRewrite;
import org.exist.memtree.ElementImpl;
import org.exist.memtree.NodeImpl;
import org.exist.memtree.SAXAdapter;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.xacml.AccessContext;
import org.exist.source.DBSource;
import org.exist.source.Source;
import org.exist.source.StringSource;
import org.exist.source.URLSource;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.XQueryPool;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.serializers.Serializer.HttpContext;
import org.exist.storage.txn.TransactionException;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.util.LockException;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.util.VirtualTempFile;
import org.exist.util.VirtualTempFileInputSource;
import org.exist.util.io.FilterInputStreamCacheFactory.FilterInputStreamCacheConfiguration;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.util.serializer.json.*;
import org.exist.xmldb.XmldbURI;
import org.exist.xqj.Marshaller;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.Constants;
import org.exist.xquery.NameTest;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.request.RequestModule;
import org.exist.xquery.functions.response.ResponseModule;
import org.exist.xquery.functions.session.SessionModule;
import org.exist.xquery.value.*;
import org.exist.xupdate.Modification;
import org.exist.xupdate.XUpdateProcessor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.XMLFilterImpl;
/**
 *
 * @author wolf
 * @author ljo
 * @author adam
 * @author gev
 *
 */
public class RESTServer {

    protected final static Logger LOG = Logger.getLogger(RESTServer.class);
    public final static String SERIALIZATION_METHOD_PROPERTY = "output-as";
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
    public final static Properties defaultOutputKeysProperties = new Properties();

    static {
        defaultOutputKeysProperties.setProperty(OutputKeys.INDENT, "yes");
        defaultOutputKeysProperties.setProperty(OutputKeys.ENCODING, "UTF-8");
        defaultOutputKeysProperties.setProperty(OutputKeys.MEDIA_TYPE,
                MimeType.XML_TYPE.getName());
    }
    private final static String QUERY_ERROR_HEAD = "<html>" + "<head>"
            + "<title>Query Error</title>" + "<style type=\"text/css\">"
            + ".errmsg {" + "  border: 1px solid black;" + "  padding: 15px;"
            + "  margin-left: 20px;" + "  margin-right: 20px;" + "}"
            + "h1 { color: #C0C0C0; }" + ".path {" + "  padding-bottom: 10px;"
            + "}" + ".high { " + "  color: #666699; " + "  font-weight: bold;"
            + "}" + "</style>" + "</head>" + "<body>" + "<h1>XQuery Error</h1>";
    private String formEncoding; // TODO: we may be able to remove this
    // eventually, in favour of
    // HttpServletRequestWrapper being setup in
    // EXistServlet, currently used for doPost()
    // but perhaps could be used for other
    // Request Methods? - deliriumsky
    private String containerEncoding;
    private boolean useDynamicContentType;
    private boolean safeMode = false;
    private SessionManager sessionManager;

    //EXQuery Request Module details
    private String xqueryContextExqueryRequestAttribute = null;
    private Constructor cstrHttpServletRequestAdapter = null;
    
    // Constructor
    public RESTServer(final BrokerPool pool, final String formEncoding,
            final String containerEncoding, final boolean useDynamicContentType, final boolean safeMode) {
        this.formEncoding = formEncoding;
        this.containerEncoding = containerEncoding;
        this.useDynamicContentType = useDynamicContentType;
        this.safeMode = safeMode;
        this.sessionManager = new SessionManager(pool);
        
        //get (optiona) EXQuery Request Module details
        try {
            Class clazz = Class.forName("org.exist.extensions.exquery.modules.request.RequestModule");
            if(clazz != null) {
                final Field fldExqRequestAttr = clazz.getDeclaredField("EXQ_REQUEST_ATTR");
                if(fldExqRequestAttr != null) {
                    this.xqueryContextExqueryRequestAttribute = (String)fldExqRequestAttr.get(null);
                    
                    if(this.xqueryContextExqueryRequestAttribute != null) {
                        clazz = Class.forName("org.exist.extensions.exquery.restxq.impl.adapters.HttpServletRequestAdapter");
                        if(clazz != null) {
                            this.cstrHttpServletRequestAdapter = clazz.getConstructor(HttpServletRequest.class, FilterInputStreamCacheConfiguration.class);
                        }
                    }
                    
                }
            }
        } catch(final Exception e) {
            if(LOG.isDebugEnabled()) {
                LOG.debug("EXQuery Request Module is not present: " + e.getMessage(), e);
            }
        }
    }
    
    /**
     * Retrieves a parameter from the Query String of the request
     */
    private String getParameter(final HttpServletRequest request, final RESTServerParameter parameter) {
        return request.getParameter(parameter.queryStringKey());
    }

    /**
     * Handle GET request. In the simplest case just returns the document or
     * binary resource specified in the path. If the path leads to a collection,
     * a listing of the collection contents is returned. If it resolves to a
     * binary resource with mime-type "application/xquery", this resource will
     * be loaded and executed by the XQuery engine.
     *
     * The method also recognizes a number of predefined parameters:
     *
     * <ul> <li>_xpath or _query: if specified, the given query is executed on
     * the current resource or collection.</li>
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
     * <li>_source: if set to "yes" and a resource with mime-type
     * "application/xquery" is requested then the xquery will not be executed,
     * instead the source of the document will be returned. Must be enabled in
     * descriptor.xml with the following syntax
     * <xquery-app><allow-source><xquery
     * path="/db/mycollection/myquery.xql"/></allow-source></xquery-app> </li>
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
    public void doGet(final DBBroker broker, final HttpServletRequest request,
            final HttpServletResponse response, final String path)
            throws BadRequestException, PermissionDeniedException,
            NotFoundException, IOException {

        // if required, set character encoding
        if (request.getCharacterEncoding() == null) {
            request.setCharacterEncoding(formEncoding);
        }

        String option;
        if ((option = getParameter(request, Release)) != null) {
            final int sessionId = Integer.parseInt(option);
            sessionManager.release(sessionId);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Released session " + sessionId);
            }
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        // Process special parameters

        int howmany = 10;
        int start = 1;
        boolean typed = false;
        boolean wrap = true;
        boolean source = false;
        boolean cache = false;
        final Properties outputProperties = new Properties(defaultOutputKeysProperties);

        String query = null;
        if (!safeMode) {
            query = getParameter(request, XPath);
            if (query == null) {
                query = getParameter(request, Query);
            }
        }
        final String _var = getParameter(request, Variables);
        List /*<Namespace>*/ namespaces = null;
        ElementImpl variables = null;
        try {
            if (_var != null) {
                final NamespaceExtractor nsExtractor = new NamespaceExtractor();
                variables = parseXML(_var, nsExtractor);
                namespaces = nsExtractor.getNamespaces();
            }
        } catch (final SAXException e) {
            final XPathException x = new XPathException(e.toString());
            writeXPathException(response, HttpServletResponse.SC_BAD_REQUEST, "UTF-8", query, path, x);
        } catch (final ParserConfigurationException e) {
            final XPathException x = new XPathException(e.toString());
            writeXPathException(response, HttpServletResponse.SC_BAD_REQUEST, "UTF-8", query, path, x);
        }

        if ((option = getParameter(request, HowMany)) != null) {
            try {
                howmany = Integer.parseInt(option);
            } catch (final NumberFormatException nfe) {
                throw new BadRequestException(
                        "Parameter _howmany should be an int");
            }
        }
        if ((option = getParameter(request, Start)) != null) {
            try {
                start = Integer.parseInt(option);
            } catch (final NumberFormatException nfe) {
                throw new BadRequestException(
                        "Parameter _start should be an int");
            }
        }
        if ((option = getParameter(request, Typed)) != null) {
            if ("yes".equals(option.toLowerCase())) {
                typed = true;
            }
        }
        if ((option = getParameter(request, Wrap)) != null) {
            wrap = "yes".equals(option);
            outputProperties.setProperty("_wrap", option);
        }
        if ((option = getParameter(request, Cache)) != null) {
            cache = "yes".equals(option);
        }
        if ((option = getParameter(request, Indent)) != null) {
            outputProperties.setProperty(OutputKeys.INDENT, option);
        }
        if ((option = getParameter(request, Source)) != null && !safeMode) {
            source = "yes".equals(option);
        }
        if ((option = getParameter(request, Session)) != null) {
            outputProperties.setProperty(Serializer.PROPERTY_SESSION_ID, option);
        }
        String stylesheet;
        if ((stylesheet = getParameter(request, XSL)) != null) {
            if ("no".equals(stylesheet)) {
                outputProperties.setProperty(EXistOutputKeys.PROCESS_XSL_PI, "no");
                outputProperties.remove(EXistOutputKeys.STYLESHEET);
                stylesheet = null;
            } else {
                outputProperties.setProperty(EXistOutputKeys.STYLESHEET, stylesheet);
            }
        } else {
            outputProperties.setProperty(EXistOutputKeys.PROCESS_XSL_PI, "yes");
        }
        LOG.debug("stylesheet = " + stylesheet);
        LOG.debug("query = " + query);
        String encoding;
        if ((encoding = getParameter(request, Encoding)) != null) {
            outputProperties.setProperty(OutputKeys.ENCODING, encoding);
        } else {
            encoding = "UTF-8";
        }

        final String mimeType = outputProperties.getProperty(OutputKeys.MEDIA_TYPE);

        if (query != null) {
            // query parameter specified, search method does all the rest of the work
            try {
                search(broker, query, path, namespaces, variables, howmany, start, typed, outputProperties,
                        wrap, cache, request, response);

            } catch (final XPathException e) {
                if (MimeType.XML_TYPE.getName().equals(mimeType)) {
                    writeXPathException(response, HttpServletResponse.SC_BAD_REQUEST, encoding, query, path, e);
                } else {
                    writeXPathExceptionHtml(response, HttpServletResponse.SC_BAD_REQUEST, encoding, query, path, e);
                }
            }
            return;
        }
        // Process the request
        DocumentImpl resource = null;
        final XmldbURI pathUri = XmldbURI.create(URLDecoder.decode(path, "UTF-8"));
        try {
            // check if path leads to an XQuery resource
            final String xquery_mime_type = MimeType.XQUERY_TYPE.getName();
            final String xproc_mime_type = MimeType.XPROC_TYPE.getName();
            resource = broker.getXMLResource(pathUri, Lock.READ_LOCK);

            if (null != resource && !isExecutableType(resource)) {
                // return regular resource that is not an xquery and not is xproc
                writeResourceAs(resource, broker, stylesheet, encoding, null,
                        outputProperties, request, response);
                return;
            }
            if (resource == null) { // could be request for a Collection

                // no document: check if path points to a collection
                final Collection collection = broker.getCollection(pathUri);
                if (collection != null) {
                    if (safeMode || !collection.getPermissionsNoLock().validate(broker.getSubject(), Permission.READ)) {
                        throw new PermissionDeniedException("Not allowed to read collection");
                    }
                    // return a listing of the collection contents
                    try {
                        writeCollection(response, encoding, broker, collection);
                        return;
                    } catch (final LockException le) {
                        if (MimeType.XML_TYPE.getName().equals(mimeType)) {
                            writeXPathException(response, HttpServletResponse.SC_BAD_REQUEST, encoding, query, path, new XPathException(le.getMessage(), le));
                        } else {
                            writeXPathExceptionHtml(response, HttpServletResponse.SC_BAD_REQUEST, encoding, query, path, new XPathException(le.getMessage(), le));
                        }
                    }

                } else if (source) {
                    // didn't find regular resource, or user wants source
                    // on a possible xquery resource that was not found
                    throw new NotFoundException("Document " + path + " not found");
                }
            }

            XmldbURI servletPath = pathUri;

            // if resource is still null, work up the url path to find an
            // xquery or xproc resource
            while (null == resource) {
                // traverse up the path looking for xquery objects
                servletPath = servletPath.removeLastSegment();
                if (servletPath == XmldbURI.EMPTY_URI) {
                    break;
                }

                resource = broker.getXMLResource(servletPath, Lock.READ_LOCK);
                if (null != resource && isExecutableType(resource)) {
                    break;

                } else if (null != resource) {
                    //unlocked at finally block

                    // not an xquery resource. This means we have a path
                    // that cannot contain an xquery object even if we keep
                    // moving up the path, so bail out now
                    throw new NotFoundException("Document " + path + " not found");
                }
            }

            if (null == resource) { // path search failed
                throw new NotFoundException("Document " + path + " not found");
            }

            // found an XQuery or XProc resource, fixup request values
            final String pathInfo = pathUri.trimFromBeginning(servletPath).toString();

            // Should we display the source of the XQuery or XProc or execute it
            final Descriptor descriptor = Descriptor.getDescriptorSingleton();
            if (source) {
                // show the source

                // check are we allowed to show the xquery source -
                // descriptor.xml
                if ((null != descriptor)
                        && descriptor.allowSource(path)
                        && resource.getPermissions().validate(
                        broker.getSubject(), Permission.READ)) {

                    // TODO: change writeResourceAs to use a serializer
                    // that will serialize xquery to syntax coloured
                    // xhtml, replace the asMimeType parameter with a
                    // method for specifying the serializer, or split
                    // the code into two methods. - deliriumsky

                    if (xquery_mime_type.equals(resource.getMetadata().getMimeType())) {
                        // Show the source of the XQuery
                        writeResourceAs(resource, broker, stylesheet, encoding,
                                MimeType.TEXT_TYPE.getName(), outputProperties,
                                request, response);
                    } else if (xproc_mime_type.equals(resource.getMetadata().getMimeType())) {
                        // Show the source of the XProc
                        writeResourceAs(resource, broker, stylesheet, encoding,
                                MimeType.XML_TYPE.getName(), outputProperties,
                                request, response);
                    }
                } else {
                    // we are not allowed to show the source - query not
                    // allowed in descriptor.xml
                    // or descriptor not found, so assume source view not
                    // allowed
                    response
                            .sendError(
                            HttpServletResponse.SC_FORBIDDEN,
                            "Permission to view XQuery source for: "
                            + path
                            + " denied. Must be explicitly defined in descriptor.xml");
                    return;
                }
            } else {
                try {
                    if (xquery_mime_type.equals(resource.getMetadata().getMimeType())) {
                        // Execute the XQuery
                        executeXQuery(broker, resource, request, response,
                                outputProperties, servletPath.toString(), pathInfo);
                    } else if (xproc_mime_type.equals(resource.getMetadata().getMimeType())) {
                        // Execute the XProc
                        executeXProc(broker, resource, request, response,
                                outputProperties, servletPath.toString(), pathInfo);
                    }
                } catch (final XPathException e) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug(e.getMessage(), e);
                    }
                    if (MimeType.XML_TYPE.getName().equals(mimeType)) {
                        writeXPathException(response, HttpServletResponse.SC_BAD_REQUEST, encoding, query, path, e);
                    } else {
                        writeXPathExceptionHtml(response, HttpServletResponse.SC_BAD_REQUEST, encoding, query,
                                path, e);
                    }
                }
            }
        } finally {
            if (resource != null) {
                resource.getUpdateLock().release(Lock.READ_LOCK);
            }
        }
    }

    public void doHead(final DBBroker broker, final HttpServletRequest request,
            final HttpServletResponse response, final String path)
            throws BadRequestException, PermissionDeniedException,
            NotFoundException, IOException {
        
        final XmldbURI pathUri = XmldbURI.create(path);
        if (checkForXQueryTarget(broker, pathUri, request, response)) {
            return;
        }

        final Properties outputProperties = new Properties(defaultOutputKeysProperties);

        String encoding;
        if ((encoding = getParameter(request, Encoding)) != null) {
            outputProperties.setProperty(OutputKeys.ENCODING, encoding);
        } else {
            encoding = "UTF-8";
        }

        DocumentImpl resource = null;
        try {
            resource = broker.getXMLResource(pathUri, Lock.READ_LOCK);

            if (resource != null) {
                if (!resource.getPermissions().validate(broker.getSubject(), Permission.READ)) {
                    throw new PermissionDeniedException(
                            "Permission to read resource " + path + " denied");
                }
                final DocumentMetadata metadata = resource.getMetadata();
                response.setContentType(metadata.getMimeType());
                // As HttpServletResponse.setContentLength is limited to integers,
                // (see http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4187336)
                // next sentence:
                //	response.setContentLength(resource.getContentLength());
                // must be set so
                response.addHeader("Content-Length", Long.toString(resource.getContentLength()));
                setCreatedAndLastModifiedHeaders(response, metadata.getCreated(), metadata.getLastModified());
            } else {
                final Collection col = broker.getCollection(pathUri);
                //no resource or collection
                if (col == null) {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND, "No resource at location: " + path);

                    return;
                }

                if (!col.getPermissionsNoLock().validate(broker.getSubject(), Permission.READ)) {
                    throw new PermissionDeniedException(
                            "Permission to read resource " + path + " denied");
                }
                response.setContentType(MimeType.XML_TYPE.getName() + "; charset=" + encoding);
                setCreatedAndLastModifiedHeaders(response, col.getCreationTime(), col.getCreationTime());
            }
        } finally {
            if (resource != null) {
                resource.getUpdateLock().release(Lock.READ_LOCK);
            }
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
     * @throws NotFoundException
     */
    public void doPost(final DBBroker broker, final HttpServletRequest request,
            final HttpServletResponse response, final String path)
            throws BadRequestException, PermissionDeniedException, IOException,
            NotFoundException {

        // if required, set character encoding
        if (request.getCharacterEncoding() == null) {
            request.setCharacterEncoding(formEncoding);
        }

        final Properties outputProperties = new Properties(defaultOutputKeysProperties);
        final XmldbURI pathUri = XmldbURI.create(path);
        DocumentImpl resource = null;

        final String encoding = outputProperties.getProperty(OutputKeys.ENCODING);
        String mimeType = outputProperties.getProperty(OutputKeys.MEDIA_TYPE);
        try {
            // check if path leads to an XQuery resource.
            // if yes, the resource is loaded and the XQuery executed.
            final String xquery_mime_type = MimeType.XQUERY_TYPE.getName();
            final String xproc_mime_type = MimeType.XPROC_TYPE.getName();
            resource = broker.getXMLResource(pathUri, Lock.READ_LOCK);

            XmldbURI servletPath = pathUri;

            // if resource is still null, work up the url path to find an
            // xquery resource
            while (null == resource) {
                // traverse up the path looking for xquery objects
                servletPath = servletPath.removeLastSegment();
                if (servletPath == XmldbURI.EMPTY_URI) {
                    break;
                }

                resource = broker.getXMLResource(servletPath, Lock.READ_LOCK);
                if (null != resource
                        && (resource.getResourceType() == DocumentImpl.BINARY_FILE
                        && xquery_mime_type.equals(resource.getMetadata().getMimeType())
                        || resource.getResourceType() == DocumentImpl.XML_FILE
                        && xproc_mime_type.equals(resource.getMetadata().getMimeType()))) {
                    break; // found a binary file with mime-type xquery or XML file with mime-type xproc

                } else if (null != resource) {
                    // not an xquery or xproc resource. This means we have a path
                    // that cannot contain an xquery or xproc object even if we keep
                    // moving up the path, so bail out now
                    resource.getUpdateLock().release(Lock.READ_LOCK);
                    resource = null;
                    break;
                }
            }

            // either xquery binary file or xproc xml file
            if (resource != null) {
                if (resource.getResourceType() == DocumentImpl.BINARY_FILE
                        && xquery_mime_type.equals(resource.getMetadata().getMimeType())
                        || resource.getResourceType() == DocumentImpl.XML_FILE
                        && xproc_mime_type.equals(resource.getMetadata().getMimeType())) {

                    // found an XQuery resource, fixup request values
                    final String pathInfo = pathUri.trimFromBeginning(servletPath).toString();
                    try {
                        if (xquery_mime_type.equals(resource.getMetadata().getMimeType())) {
                            // Execute the XQuery
                            executeXQuery(broker, resource, request, response,
                                    outputProperties, servletPath.toString(), pathInfo);
                        } else {
                            // Execute the XProc
                            executeXProc(broker, resource, request, response,
                                    outputProperties, servletPath.toString(), pathInfo);
                        }

                    } catch (final XPathException e) {
                        if (MimeType.XML_TYPE.getName().equals(mimeType)) {
                            writeXPathException(response, HttpServletResponse.SC_BAD_REQUEST, encoding, null, path, e);

                        } else {
                            writeXPathExceptionHtml(response, HttpServletResponse.SC_BAD_REQUEST, encoding, null, path, e);
                        }
                    }
                    return;
                }
            }

        } finally {
            if (resource != null) {
                resource.getUpdateLock().release(Lock.READ_LOCK);
            }
        }

        // check the content type to see if its XML or a parameter string
        String requestType = request.getContentType();
        if (requestType != null) {
            final int semicolon = requestType.indexOf(';');
            if (semicolon > 0) {
                requestType = requestType.substring(0, semicolon).trim();
            }
        }

        // content type != application/x-www-form-urlencoded
        if (requestType == null || !requestType.equals(MimeType.URL_ENCODED_TYPE.getName())) {
            // third, normal POST: read the request content and check if
            // it is an XUpdate or a query request.
            int howmany = 10;
            int start = 1;
            boolean typed = false;
            ElementImpl variables = null;
            boolean enclose = true;
            boolean cache = false;
            String query = null;

            final TransactionManager transact = broker.getBrokerPool().getTransactionManager();
            final Txn transaction = transact.beginTransaction();

            try {
                final String content = getRequestContent(request);
                final NamespaceExtractor nsExtractor = new NamespaceExtractor();
                final ElementImpl root = parseXML(content, nsExtractor);
                final String rootNS = root.getNamespaceURI();
                
                if (rootNS != null && rootNS.equals(Namespaces.EXIST_NS)) {

                    if (Query.xmlKey().equals(root.getLocalName())) {
                        // process <query>xpathQuery</query>
                        String option = root.getAttribute(Start.xmlKey());
                        if (option != null) {
                            try {
                                start = Integer.parseInt(option);
                            } catch (final NumberFormatException e) {
                                //
                            }
                        }

                        option = root.getAttribute(Max.xmlKey());
                        if (option != null) {
                            try {
                                howmany = Integer.parseInt(option);
                            } catch (final NumberFormatException e) {
                                //
                            }
                        }

                        option = root.getAttribute(Enclose.xmlKey());
                        if (option != null) {
                            if ("no".equals(option)) {
                                enclose = false;
                            }
                        } else {
                            option = root.getAttribute(Wrap.xmlKey());
                            if (option != null) {
                                if ("no".equals(option)) {
                                    enclose = false;
                                }
                            }
                        }

                        option = root.getAttribute(Method.xmlKey());
                        if ((option != null) && (!"".equals(option))) {
                            outputProperties.setProperty(SERIALIZATION_METHOD_PROPERTY, option);
                        }

                        option = root.getAttribute(Typed.xmlKey());
                        if (option != null) {
                            if ("yes".equals(option)) {
                                typed = true;
                            }
                        }

                        option = root.getAttribute(Mime.xmlKey());
                        if ((option != null) && (!"".equals(option))) {
                            mimeType = option;
                        }

                        if ((option = root.getAttribute(Cache.xmlKey())) != null) {
                            cache = "yes".equals(option);
                        }

                        if ((option = root.getAttribute(Session.xmlKey())) != null
                                && option.length() > 0) {
                            outputProperties.setProperty(
                                    Serializer.PROPERTY_SESSION_ID, option);
                        }

                        final NodeList children = root.getChildNodes();
                        for (int i = 0; i < children.getLength(); i++) {

                            final Node child = children.item(i);
                            if (child.getNodeType() == Node.ELEMENT_NODE
                                    && child.getNamespaceURI().equals(Namespaces.EXIST_NS)) {

                                if (Text.xmlKey().equals(child.getLocalName())) {
                                    final StringBuilder buf = new StringBuilder();
                                    Node next = child.getFirstChild();
                                    while (next != null) {
                                        if (next.getNodeType() == Node.TEXT_NODE
                                                || next.getNodeType() == Node.CDATA_SECTION_NODE) {
                                            buf.append(next.getNodeValue());
                                        }
                                        next = next.getNextSibling();
                                    }
                                    query = buf.toString();

                                } else if (Variables.xmlKey().equals(child.getLocalName())) {
                                    variables = (ElementImpl) child;

                                } else if (Properties.xmlKey().equals(child.getLocalName())) {
                                    Node node = child.getFirstChild();
                                    while (node != null) {
                                        if (node.getNodeType() == Node.ELEMENT_NODE
                                                && node.getNamespaceURI().equals(Namespaces.EXIST_NS)
                                                && Property.xmlKey().equals(node.getLocalName())) {

                                            final Element property = (Element) node;
                                            final String key = property.getAttribute("name");
                                            final String value = property.getAttribute("value");
                                            LOG.debug(key + " = " + value);

                                            if (key != null && value != null) {
                                                outputProperties.setProperty(key, value);
                                            }
                                        }
                                        node = node.getNextSibling();
                                    }
                                }
                            }
                        }
                    }
                    
                    // execute query
                    if (query != null) {

                        try {
                            search(broker, query, path, nsExtractor.getNamespaces(), variables,
                                    howmany, start, typed, outputProperties,
                                    enclose, cache, request, response);

                            transact.commit(transaction);

                        } catch (final XPathException e) {
                            if (MimeType.XML_TYPE.getName().equals(mimeType)) {
                                writeXPathException(response, HttpServletResponse.SC_ACCEPTED,
                                        encoding, null, path, e);
                            } else {
                                writeXPathExceptionHtml(response, HttpServletResponse.SC_ACCEPTED,
                                        encoding, null, path, e);
                            }
                        }

                    } else {
                        transact.abort(transaction);
                        throw new BadRequestException("No query specified");
                    }

                } else if (rootNS != null && rootNS.equals(XUpdateProcessor.XUPDATE_NS)) {

                    LOG.debug("Got xupdate request: " + content);
                    final MutableDocumentSet docs = new DefaultDocumentSet();
                    final Collection collection = broker.getCollection(pathUri);
                    if (collection != null) {
                        collection.allDocs(broker, docs, true);

                    } else {
                        final DocumentImpl xupdateDoc = broker.getResource(pathUri, Permission.READ);

                        if (xupdateDoc != null) {
                            docs.add(xupdateDoc);

                        } else {
                            broker.getAllXMLResources(docs);
                        }
                    }

                    final XUpdateProcessor processor = new XUpdateProcessor(broker, docs, AccessContext.REST);
                    final Modification modifications[] = processor.parse(new InputSource(new StringReader(content)));
                    long mods = 0;
                    for (int i = 0; i < modifications.length; i++) {
                        mods += modifications[i].process(transaction);
                        broker.flush();
                    }

                    transact.commit(transaction);

                    // FD : Returns an XML doc
                    writeXUpdateResult(response, encoding, mods);
                    // END FD

                } else {
                    transact.abort(transaction);
                    throw new BadRequestException("Unknown XML root element: " + root.getNodeName());
                }

            } catch (final SAXException e) {
                transact.abort(transaction);
                Exception cause = e;
                if (e.getException() != null) {
                    cause = e.getException();
                }
                LOG.debug("SAX exception while parsing request: " + cause.getMessage(), cause);
                throw new BadRequestException("SAX exception while parsing request: " + cause.getMessage());

            } catch (final ParserConfigurationException e) {
                transact.abort(transaction);
                throw new BadRequestException("Parser exception while parsing request: " + e.getMessage());

            } catch (final XPathException e) {
                transact.abort(transaction);
                throw new BadRequestException("Query exception while parsing request: " + e.getMessage());

            } catch (final IOException e) {
                transact.abort(transaction);
                throw new BadRequestException("IO exception while parsing request: " + e.getMessage());

            } catch (final EXistException e) {
                transact.abort(transaction);
                throw new BadRequestException(e.getMessage());

            } catch (final LockException e) {
                transact.abort(transaction);
                throw new PermissionDeniedException(e.getMessage());
            } finally {
                transact.close(transaction);
            }

            // content type = application/x-www-form-urlencoded
        } else {
            doGet(broker, request, response, path);
        }
    }

    private ElementImpl parseXML(final String content,
            final NamespaceExtractor nsExtractor)
            throws ParserConfigurationException, SAXException, IOException {
        
        final SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        final InputSource src = new InputSource(new StringReader(content));
        final SAXParser parser = factory.newSAXParser();
        final XMLReader reader = parser.getXMLReader();
        final SAXAdapter adapter = new SAXAdapter();
        //reader.setContentHandler(adapter);
        //reader.parse(src);
        nsExtractor.setContentHandler(adapter);
        nsExtractor.setParent(reader);
        nsExtractor.parse(src);

        final Document doc = adapter.getDocument();

        return (ElementImpl) doc.getDocumentElement();
    }

    private class NamespaceExtractor extends XMLFilterImpl {

        final List<Namespace> namespaces = new ArrayList<Namespace>();

        @Override
        public void startPrefixMapping(final String prefix, final String uri)
            throws SAXException {
            if (!Namespaces.EXIST_NS.equals(uri)) {
                final Namespace ns = new Namespace(prefix, uri);
                namespaces.add(ns);
            }
            super.startPrefixMapping(prefix, uri);
        }

        public List<Namespace> getNamespaces() {
            return namespaces;
        }
    }

    public class Namespace {

        private final String prefix;
        private final String uri;

        public Namespace(final String prefix, final String uri) {
            this.prefix = prefix;
            this.uri = uri;
        }

        public String getPrefix() {
            return prefix;
        }

        public String getUri() {
            return uri;
        }
    }

    /**
     * Handles PUT requests. The request content is stored as a new resource at
     * the specified location. If the resource already exists, it is overwritten
     * if the user has write permissions.
     *
     * The resource type depends on the content type specified in the HTTP
     * header. The content type will be looked up in the global mime table. If
     * the corresponding mime type is not a know XML mime type, the resource
     * will be stored as a binary resource.
     *
     * @param broker
     * @param path The path to which the file should be stored
     * @param request
     * @param response
     * @throws BadRequestException
     * @throws PermissionDeniedException
     */
    public void doPut(final DBBroker broker, final XmldbURI path,
            final HttpServletRequest request, final HttpServletResponse response)
            throws BadRequestException, PermissionDeniedException, IOException,
            NotFoundException {

        if (checkForXQueryTarget(broker, path, request, response)) {
            return;
        }

        final TransactionManager transact = broker.getBrokerPool().getTransactionManager();
        Txn transaction = null;
        VirtualTempFile vtempFile = null;
        try {
            // fourth, process the request
            InputStream is = request.getInputStream();
            long len = request.getContentLength();
            final String lenstr = request.getHeader("Content-Length");
            if (lenstr != null) {
                len = Long.parseLong(lenstr);
            }
            // put may send a lot of data, so save it
            // to a temporary file first.

            vtempFile = new VirtualTempFile();
            vtempFile.setTempPrefix("existSRV");
            vtempFile.setTempPostfix(".tmp");
            vtempFile.write(is, len);
            vtempFile.close();

            final XmldbURI docUri = path.lastSegment();
            final XmldbURI collUri = path.removeLastSegment();

            if (docUri == null || collUri == null) {
                //transact.abort(transaction);
                throw new BadRequestException("Bad path: " + path);
            }
            // TODO : use getOrCreateCollection() right now ?
            Collection collection = broker.getCollection(collUri);
            if (collection == null) {
                LOG.debug("creating collection " + collUri);
                transaction = transact.beginTransaction();
                collection = broker.getOrCreateCollection(transaction, collUri);
                broker.saveCollection(transaction, collection);
            }
            MimeType mime;
            String contentType = request.getContentType();
            String charset = null;
            if (contentType != null) {
                final int semicolon = contentType.indexOf(';');
                if (semicolon > 0) {
                    contentType = contentType.substring(0, semicolon).trim();
                    final int equals = contentType.indexOf('=', semicolon);
                    if (equals > 0) {
                        final String param = contentType.substring(semicolon + 1,
                                equals).trim();
                        if (param.compareToIgnoreCase("charset=") == 0) {
                            charset = param.substring(equals + 1).trim();
                        }
                    }
                }
                mime = MimeTable.getInstance().getContentType(contentType);
            } else {
                mime = MimeTable.getInstance().getContentTypeFor(docUri);
                if (mime != null) {
                    contentType = mime.getName();
                }
            }
            if (mime == null) {
                mime = MimeType.BINARY_TYPE;
                contentType = mime.getName();
            }

            if (transaction == null) {
                transaction = transact.beginTransaction();
            }

            if (mime.isXMLType()) {
                final InputSource vtfis = new VirtualTempFileInputSource(vtempFile, charset);

                final IndexInfo info = collection.validateXMLResource(transaction, broker, docUri, vtfis);
                info.getDocument().getMetadata().setMimeType(contentType);
                collection.store(transaction, broker, info, vtfis, false);
                response.setStatus(HttpServletResponse.SC_CREATED);
            } else {

                is = vtempFile.getByteStream();
                try {
                    collection.addBinaryResource(transaction, broker, docUri, is,
                            contentType, vtempFile.length());
                } finally {
                    is.close();
                }
                response.setStatus(HttpServletResponse.SC_CREATED);
            }

            transact.commit(transaction);
        } catch (final SAXParseException e) {
            transact.abort(transaction);
            throw new BadRequestException("Parsing exception at "
                    + e.getLineNumber() + "/" + e.getColumnNumber() + ": "
                    + e.toString());
        } catch (final TriggerException e) {
            transact.abort(transaction);
            throw new PermissionDeniedException(e.getMessage());
        } catch (SAXException e) {
            transact.abort(transaction);
            Exception o = e.getException();
            if (o == null) {
                o = e;
            }
            throw new BadRequestException("Parsing exception: " + o.getMessage());
        } catch (final EXistException e) {
            transact.abort(transaction);
            throw new BadRequestException("Internal error: " + e.getMessage());
        } catch (final LockException e) {
            transact.abort(transaction);
            throw new PermissionDeniedException(e.getMessage());
        } finally {
            transact.close(transaction);
            if (vtempFile != null) {
                vtempFile.delete();
            }
        }
        return;
    }

    public void doDelete(final DBBroker broker, final String path, final HttpServletRequest request, final HttpServletResponse response)
            throws PermissionDeniedException, NotFoundException, IOException, BadRequestException {
        final XmldbURI pathURI = XmldbURI.create(path);
        if (checkForXQueryTarget(broker, pathURI, request, response)) {
            return;
        }

        final TransactionManager transact = broker.getBrokerPool().getTransactionManager();
        Txn txn = null;
        try {
            final Collection collection = broker.getCollection(pathURI);
            if (collection != null) {
                // remove the collection
                LOG.debug("removing collection " + path);

                txn = transact.beginTransaction();

                broker.removeCollection(txn, collection);
                response.setStatus(HttpServletResponse.SC_OK);

            } else {
                final DocumentImpl doc = (DocumentImpl) broker.getResource(pathURI, Permission.WRITE);
                if (doc == null) {
                    //transact.abort(txn);
                    throw new NotFoundException("No document or collection found for path: " + path);
                } else {
                    // remove the document
                    LOG.debug("removing document " + path);
                    txn = transact.beginTransaction();

                    if (doc.getResourceType() == DocumentImpl.BINARY_FILE) {
                        doc.getCollection().removeBinaryResource(txn, broker, pathURI.lastSegment());
                    } else {
                        doc.getCollection().removeXMLResource(txn, broker, pathURI.lastSegment());
                    }

                    response.setStatus(HttpServletResponse.SC_OK);
                }
            }
            if (txn != null) //should not happen, just in case ...
            {
                transact.commit(txn);
            }

        } catch (final TriggerException e) {
            transact.abort(txn);
            throw new PermissionDeniedException("Trigger failed: " + e.getMessage());
        } catch (final LockException e) {
            transact.abort(txn);
            throw new PermissionDeniedException("Could not acquire lock: " + e.getMessage());
        } catch (final TransactionException e) {
            transact.abort(txn);
            LOG.warn("Transaction aborted: " + e.getMessage(), e);
        } finally {
            transact.close(txn);
        }
    }

    private boolean checkForXQueryTarget(final DBBroker broker,
        final XmldbURI path, final HttpServletRequest request,
        final HttpServletResponse response) throws PermissionDeniedException,
        NotFoundException, IOException, BadRequestException {
        
        if (request.getAttribute(XQueryURLRewrite.RQ_ATTR) == null) {
            return false;
        }
        final String xqueryType = MimeType.XQUERY_TYPE.getName();
        final Collection collection = broker.getCollection(path);
        if (collection == null) {
            XmldbURI servletPath = path;
            DocumentImpl resource = null;
            // work up the url path to find an
            // xquery resource
            while (null == resource) {
                // traverse up the path looking for xquery objects

                resource = broker.getXMLResource(servletPath, Lock.READ_LOCK);
                if (null != resource
                        && (resource.getResourceType() == DocumentImpl.BINARY_FILE
                        && xqueryType.equals(resource.getMetadata().getMimeType()))) {
                    break; // found a binary file with mime-type xquery or XML file with mime-type xproc
                } else if (null != resource) {
                    // not an xquery or xproc resource. This means we have a path
                    // that cannot contain an xquery or xproc object even if we keep
                    // moving up the path, so bail out now
                    resource.getUpdateLock().release(Lock.READ_LOCK);
                    resource = null;
                    break;
                }
                servletPath = servletPath.removeLastSegment();
                if (servletPath == XmldbURI.EMPTY_URI) {
                    break;
                }
            }
            // xquery binary file found
            if (resource != null) {
                // found an XQuery resource, fixup request values
                final String pathInfo = path.trimFromBeginning(servletPath).toString();
                final Properties outputProperties = new Properties(defaultOutputKeysProperties);
                try {
                    // Execute the XQuery
                    executeXQuery(broker, resource, request, response,
                            outputProperties, servletPath.toString(), pathInfo);
                } catch (final XPathException e) {
                    writeXPathExceptionHtml(response, HttpServletResponse.SC_BAD_REQUEST, "UTF-8", null, path.toString(), e);
                } finally {
                    resource.getUpdateLock().release(Lock.READ_LOCK);
                }
                return true;
            }
        }
        return false;
    }

    private String getRequestContent(final HttpServletRequest request) throws IOException {

        String encoding = request.getCharacterEncoding();
        if (encoding == null) {
            encoding = "UTF-8";
        }

        final InputStream is = request.getInputStream();
        final Reader reader = new InputStreamReader(is, encoding);
        final StringWriter content = new StringWriter();
        final char ch[] = new char[4096];
        int len = 0;
        while ((len = reader.read(ch)) > -1) {
            content.write(ch, 0, len);
        }

        final String xml = content.toString();
        return xml;
    }

    /**
     * TODO: pass request and response objects to XQuery.
     *
     * @throws XPathException
     */
    protected void search(final DBBroker broker, final String query,
        final String path, final List<Namespace> namespaces,
        final ElementImpl variables, final int howmany, final int start,
        final boolean typed, final Properties outputProperties,
        final boolean wrap, final boolean cache,
        final HttpServletRequest request,
        final HttpServletResponse response) throws BadRequestException,
        PermissionDeniedException, XPathException {

        final String sessionIdParam = outputProperties.getProperty(Serializer.PROPERTY_SESSION_ID);
        if (sessionIdParam != null) {
            try {
                final int sessionId = Integer.parseInt(sessionIdParam);
                if (sessionId > -1) {
                    final Sequence cached = sessionManager.get(query, sessionId);
                    if (cached != null) {
                        LOG.debug("Returning cached query result");
                        writeResults(response, broker, cached, howmany, start, typed, outputProperties, wrap);

                    } else {
                        LOG.debug("Cached query result not found. Probably timed out. Repeating query.");
                    }
                }

            } catch (final NumberFormatException e) {
                throw new BadRequestException("Invalid session id passed in query request: " + sessionIdParam);
            }
        }

        final XmldbURI pathUri = XmldbURI.create(path);
        try {
            final Source source = new StringSource(query);
            final XQuery xquery = broker.getXQueryService();
            final XQueryPool pool = xquery.getXQueryPool();
            CompiledXQuery compiled = pool.borrowCompiledXQuery(broker, source);

            XQueryContext context;
            if (compiled == null) {
                context = xquery.newContext(AccessContext.REST);
            } else {
                context = compiled.getContext();
            }

            context.setStaticallyKnownDocuments(new XmldbURI[]{pathUri});
            context.setBaseURI(new AnyURIValue(pathUri.toString()));

            declareNamespaces(context, namespaces);
            declareVariables(context, variables, request, response);

            if (compiled == null) {
                compiled = xquery.compile(context, source);
            } else {
                compiled.getContext().updateContext(context);
                context.getWatchDog().reset();
            }

            try {
                final long startTime = System.currentTimeMillis();
                final Sequence resultSequence = xquery.execute(compiled, null, outputProperties);
                final long queryTime = System.currentTimeMillis() - startTime;

                if (LOG.isDebugEnabled()) {
                    LOG.debug("Found " + resultSequence.getItemCount() + " in " + queryTime + "ms.");
                }

                if (cache) {
                    final int sessionId = sessionManager.add(query, resultSequence);
                    outputProperties.setProperty(Serializer.PROPERTY_SESSION_ID, Integer.toString(sessionId));
                    if (!response.isCommitted()) {
                        response.setIntHeader("X-Session-Id", sessionId);
                    }
                }

                writeResults(response, broker, resultSequence, howmany, start, typed, outputProperties, wrap);

            } finally {
                pool.returnCompiledXQuery(source, compiled);
            }

        } catch (final IOException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    private void declareNamespaces(final XQueryContext context,
        final List<Namespace> namespaces) throws XPathException {
        
        if (namespaces == null) {
            return;
        }

        for (final Namespace ns : namespaces) {
            context.declareNamespace(ns.getPrefix(), ns.getUri());
        }
    }

    /**
     * Pass the request, response and session objects to the XQuery context.
     *
     * @param context
     * @param request
     * @param response
     * @throws XPathException
     */
    private HttpRequestWrapper declareVariables(final XQueryContext context,
        final ElementImpl variables, final HttpServletRequest request,
        final HttpServletResponse response) throws XPathException {

        final HttpRequestWrapper reqw = new HttpRequestWrapper(request, formEncoding, containerEncoding);
        final ResponseWrapper respw = new HttpResponseWrapper(response);

        // context.declareNamespace(RequestModule.PREFIX,
        // RequestModule.NAMESPACE_URI);
        context.declareVariable(RequestModule.PREFIX + ":request", reqw);
        context.declareVariable(ResponseModule.PREFIX + ":response", respw);
        context.declareVariable(SessionModule.PREFIX + ":session", reqw.getSession(false));

        //enable EXQuery Request Module (if present)
        try { 
            if(xqueryContextExqueryRequestAttribute != null && cstrHttpServletRequestAdapter != null) {
                final Object exqueryRequestAdapter = cstrHttpServletRequestAdapter.newInstance(request, new FilterInputStreamCacheConfiguration(){
                    @Override
                    public String getCacheClass() {
                        return (String)context.getBroker().getConfiguration().getProperty(Configuration.BINARY_CACHE_CLASS_PROPERTY);
                    }
                });

                if(exqueryRequestAdapter != null) {
                    context.setAttribute(xqueryContextExqueryRequestAttribute, exqueryRequestAdapter);
                }
            }     
        } catch(final Exception e) {
            if(LOG.isDebugEnabled()) {
                LOG.debug("EXQuery Request Module is not present: " + e.getMessage(), e);
            }
        }
        
        if (variables != null) {
            declareExternalAndXQJVariables(context, variables);
        }

        return reqw;
    }
    
    private void declareExternalAndXQJVariables(final XQueryContext context,
        final ElementImpl variables) throws XPathException {

        final ValueSequence varSeq = new ValueSequence();
        variables.selectChildren(new NameTest(Type.ELEMENT, new QName(Variable.xmlKey(), Namespaces.EXIST_NS)), varSeq);
        for (final SequenceIterator i = varSeq.iterate(); i.hasNext();) {
            final ElementImpl variable = (ElementImpl) i.nextItem();
            // get the QName of the variable
            final ElementImpl qname = (ElementImpl) variable.getFirstChild(new NameTest(Type.ELEMENT, new QName("qname", Namespaces.EXIST_NS)));
            String localname = null, prefix = null, uri = null;
            NodeImpl child = (NodeImpl) qname.getFirstChild();
            while (child != null) {
                if ("localname".equals(child.getLocalName())) {
                    localname = child.getStringValue();

                } else if ("namespace".equals(child.getLocalName())) {
                    uri = child.getStringValue();

                } else if ("prefix".equals(child.getLocalName())) {
                    prefix = child.getStringValue();

                }
                child = (NodeImpl) child.getNextSibling();
            }

            if (uri != null && prefix != null) {
                context.declareNamespace(prefix, uri);
            }

            if (localname == null) {
                continue;
            }

            final QName q;
            if (prefix != null && localname != null) {
                q = new QName(localname, uri, prefix);
            } else {
                q = new QName(localname, uri, XMLConstants.DEFAULT_NS_PREFIX);
            }

            // get serialized sequence
            final NodeImpl value = variable.getFirstChild(new NameTest(Type.ELEMENT, Marshaller.ROOT_ELEMENT_QNAME));
            final Sequence sequence;
            try {
                sequence = value == null ? Sequence.EMPTY_SEQUENCE : Marshaller.demarshall(value);
            } catch (final XMLStreamException xe) {
                throw new XPathException(xe.toString());
            }

            // now declare variable
            if (prefix != null) {
                context.declareVariable(q.getPrefix() + ":" + q.getLocalName(), sequence);
            } else {
                context.declareVariable(q.getLocalName(), sequence);
            }
        }
    }

    /**
     * Directly execute an XQuery stored as a binary document in the database.
     *
     * @throws PermissionDeniedException
     */
    private void executeXQuery(final DBBroker broker, final DocumentImpl resource,
            final HttpServletRequest request, final HttpServletResponse response,
            final Properties outputProperties, final String servletPath, final String pathInfo)
            throws XPathException, BadRequestException, PermissionDeniedException {

        final Source source = new DBSource(broker, (BinaryDocument) resource, true);
        final XQuery xquery = broker.getXQueryService();
        final XQueryPool pool = xquery.getXQueryPool();
        XQueryContext context;

        CompiledXQuery compiled = pool.borrowCompiledXQuery(broker, source);
        if (compiled == null) {
            // special header to indicate that the query is not returned from
            // cache
            response.setHeader("X-XQuery-Cached", "false");
            context = xquery.newContext(AccessContext.REST);

        } else {
            response.setHeader("X-XQuery-Cached", "true");
            context = compiled.getContext();
        }

        // TODO: don't hardcode this?
        context.setModuleLoadPath(
                XmldbURI.EMBEDDED_SERVER_URI.append(
                resource.getCollection().getURI()).toString());

        context.setStaticallyKnownDocuments(
                new XmldbURI[]{resource.getCollection().getURI()});

        final HttpRequestWrapper reqw = declareVariables(context, null, request, response);
        reqw.setServletPath(servletPath);
        reqw.setPathInfo(pathInfo);

        if (compiled == null) {
            try {
                compiled = xquery.compile(context, source);
            } catch (final IOException e) {
                throw new BadRequestException("Failed to read query from " + resource.getURI(), e);
            }
        }

        DebuggeeFactory.checkForDebugRequest(request, context);

        boolean wrap = outputProperties.getProperty("_wrap") != null
                && "yes".equals(outputProperties.getProperty("_wrap"));

        try {
            final Sequence result = xquery.execute(compiled, null, outputProperties);
            writeResults(response, broker, result, -1, 1, false, outputProperties, wrap);

        } finally {
            context.runCleanupTasks();
            pool.returnCompiledXQuery(source, compiled);
        }
    }

    /**
     * Directly execute an XProc stored as a XML document in the database.
     *
     * @throws PermissionDeniedException
     */
    private void executeXProc(final DBBroker broker, final DocumentImpl resource,
            final HttpServletRequest request, final HttpServletResponse response,
            final Properties outputProperties, final String servletPath, final String pathInfo)
            throws XPathException, BadRequestException, PermissionDeniedException {

        final URLSource source = new URLSource(this.getClass().getResource("run-xproc.xq"));
        final XQuery xquery = broker.getXQueryService();
        final XQueryPool pool = xquery.getXQueryPool();
        XQueryContext context;
        CompiledXQuery compiled = pool.borrowCompiledXQuery(broker, source);
        if (compiled == null) {
            context = xquery.newContext(AccessContext.REST);
        } else {
            context = compiled.getContext();
        }

        context.declareVariable("pipeline", resource.getURI().toString());
        
        final String stdin = request.getParameter("stdin");
        context.declareVariable("stdin", stdin == null ? "" : stdin);

        final String debug = request.getParameter("debug");
        context.declareVariable("debug", debug == null ? "0" : "1");

        final String bindings = request.getParameter("bindings");
        context.declareVariable("bindings", bindings == null ? "<bindings/>" : bindings);

        final String autobind = request.getParameter("autobind");
        context.declareVariable("autobind", autobind == null ? "0" : "1");

        final String options = request.getParameter("options");
        context.declareVariable("options", options == null ? "<options/>" : options);

        // TODO: don't hardcode this?
        context.setModuleLoadPath(
                XmldbURI.EMBEDDED_SERVER_URI.append(
                resource.getCollection().getURI()).toString());

        context.setStaticallyKnownDocuments(
                new XmldbURI[]{resource.getCollection().getURI()});

        final HttpRequestWrapper reqw = declareVariables(context, null, request, response);
        reqw.setServletPath(servletPath);
        reqw.setPathInfo(pathInfo);
        if (compiled == null) {
            try {
                compiled = xquery.compile(context, source);
            } catch (final IOException e) {
                throw new BadRequestException("Failed to read query from "
                        + source.getURL(), e);
            }
        }

        try {
            final Sequence result = xquery.execute(compiled, null, outputProperties);
            writeResults(response, broker, result, -1, 1, false, outputProperties, false);
        } finally {
            pool.returnCompiledXQuery(source, compiled);
        }
    }

    public void setCreatedAndLastModifiedHeaders(
        final HttpServletResponse response, long created, long lastModified) {

        /**
         * Jetty ignores the milliseconds component -
         * https://bugs.eclipse.org/bugs/show_bug.cgi?id=342712 So lets work
         * around this by rounding up to the nearest whole second
         */
        final long lastModifiedMillisComp = lastModified % 1000;
        if (lastModifiedMillisComp > 0) {
            lastModified += 1000 - lastModifiedMillisComp;
        }
        final long createdMillisComp = created % 1000;
        if (createdMillisComp > 0) {
            created += 1000 - createdMillisComp;
        }

        response.addDateHeader("Last-Modified", lastModified);
        response.addDateHeader("Created", created);
    }

    // writes out a resource, uses asMimeType as the specified mime-type or if
    // null uses the type of the resource
    private void writeResourceAs(final DocumentImpl resource, final DBBroker broker,
        final String stylesheet, final String encoding, String asMimeType,
        final Properties outputProperties, final HttpServletRequest request,
        final HttpServletResponse response) throws BadRequestException,
        PermissionDeniedException, IOException {

        // Do we have permission to read the resource
        if (!resource.getPermissions().validate(broker.getSubject(), Permission.READ)) {
            throw new PermissionDeniedException("Not allowed to read resource");
        }

        //get the document metadata
        final DocumentMetadata metadata = resource.getMetadata();
        final long lastModified = metadata.getLastModified();
        setCreatedAndLastModifiedHeaders(response, metadata.getCreated(), lastModified);


        /**
         * HTTP 1.1 RFC 2616 Section 14.25 *
         */
        //handle If-Modified-Since request header
        try {
            final long ifModifiedSince = request.getDateHeader("If-Modified-Since");
            if (ifModifiedSince > -1) {

                /*
                 a) A date which is later than the server's
                 current time is invalid.
                 */
                if (ifModifiedSince <= System.currentTimeMillis()) {

                    /*
                     b) If the variant has been modified since the If-Modified-Since
                     date, the response is exactly the same as for a normal GET.
                     */
                    if (lastModified <= ifModifiedSince) {

                        /*
                         c) If the variant has not been modified since a valid If-
                         Modified-Since date, the server SHOULD return a 304 (Not
                         Modified) response.
                         */
                        response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                        return;
                    }
                }
            }
        } catch (final IllegalArgumentException iae) {
            LOG.warn("Illegal If-Modified-Since HTTP Header sent on request, ignoring. " + iae.getMessage(), iae);
        }

        if (resource.getResourceType() == DocumentImpl.BINARY_FILE) {
            // binary resource

            if (asMimeType == null) { // wasn't a mime-type specified?
                asMimeType = resource.getMetadata().getMimeType();
            }

            if (asMimeType.startsWith("text/")) {
                response.setContentType(asMimeType + "; charset=" + encoding);
            } else {
                response.setContentType(asMimeType);
            }

            // As HttpServletResponse.setContentLength is limited to integers,
            // (see http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4187336)
            // next sentence:
            //	response.setContentLength(resource.getContentLength());
            // must be set so
            response.addHeader("Content-Length", Long.toString(resource.getContentLength()));
            final OutputStream os = response.getOutputStream();
            broker.readBinaryResource((BinaryDocument) resource, os);
            os.flush();
        } else {
            // xml resource

            SAXSerializer sax = null;
            final Serializer serializer = broker.getSerializer();
            serializer.reset();

            //setup the http context
            final HttpContext httpContext = serializer.new HttpContext();
            final HttpRequestWrapper reqw = new HttpRequestWrapper(request, formEncoding, containerEncoding);
            httpContext.setRequest(reqw);
            httpContext.setSession(reqw.getSession(false));
            serializer.setHttpContext(httpContext);


            // Serialize the document
            try {
                sax = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);

                // use a stylesheet if specified in query parameters
                if (stylesheet != null) {
                    serializer.setStylesheet(resource, stylesheet);
                }
                serializer.setProperties(outputProperties);
                serializer.prepareStylesheets(resource);

                if (asMimeType != null) { // was a mime-type specified?
                    response.setContentType(asMimeType + "; charset=" + encoding);
                } else {
                    if (serializer.isStylesheetApplied()
                            || serializer.hasXSLPi(resource) != null) {

                        asMimeType = serializer.getStylesheetProperty(OutputKeys.MEDIA_TYPE);
                        if (!useDynamicContentType || asMimeType == null) {
                            asMimeType = MimeType.HTML_TYPE.getName();
                        }

                        if (LOG.isDebugEnabled()) {
                            LOG.debug("media-type: " + asMimeType);
                        }

                        response.setContentType(asMimeType + "; charset=" + encoding);
                    } else {
                        asMimeType = resource.getMetadata().getMimeType();
                        response.setContentType(asMimeType + "; charset=" + encoding);
                    }
                }
                if (asMimeType.equals(MimeType.HTML_TYPE.getName())) {
                    outputProperties.setProperty("method", "xhtml");
                    outputProperties.setProperty("media-type", "text/html; charset=" + encoding);
                    outputProperties.setProperty("indent", "yes");
                    outputProperties.setProperty("omit-xml-declaration", "no");
                }

                final OutputStreamWriter writer = new OutputStreamWriter(response.getOutputStream(), encoding);
                sax.setOutput(writer, outputProperties);
                serializer.setSAXHandlers(sax, sax);

                serializer.toSAX(resource);

                writer.flush();
                writer.close();
            } catch (final SAXException saxe) {
                LOG.warn(saxe);
                throw new BadRequestException("Error while serializing XML: " + saxe.getMessage());
            } catch (final TransformerConfigurationException e) {
                LOG.warn(e);
                throw new BadRequestException(e.getMessageAndLocation());
            } finally {
                if (sax != null) {
                    SerializerPool.getInstance().returnObject(sax);
                }
            }
        }
    }

    /**
     * @param response
     * @param encoding
     * @param query
     * @param path
     * @param e
     *
     */
    private void writeXPathExceptionHtml(final HttpServletResponse response,
        final int httpStatusCode, final String encoding, final String query,
        final String path, final XPathException e) throws IOException {

        if (!response.isCommitted()) {
            response.reset();
        }

        response.setStatus(httpStatusCode);

        response.setContentType(MimeType.HTML_TYPE.getName() + "; charset=" + encoding);

        final OutputStreamWriter writer = new OutputStreamWriter(response.getOutputStream(), encoding);
        writer.write(QUERY_ERROR_HEAD);
        writer.write("<p class=\"path\"><span class=\"high\">Path</span>: ");
        writer.write("<a href=\"");
        writer.write(path);
        writer.write("\">");
        writer.write(path);
        writer.write("</a></p>");

        writer.write("<p class=\"errmsg\">");
        final String message = e.getMessage() == null ? e.toString() : e.getMessage();
        writer.write(XMLUtil.encodeAttrMarkup(message));
        writer.write("</p>");
        if (query != null) {
            writer.write("<p><span class=\"high\">Query</span>:</p><pre>");
            writer.write(XMLUtil.encodeAttrMarkup(query));
            writer.write("</pre>");
        }
        writer.write("</body></html>");

        writer.flush();
        writer.close();
    }

    /**
     * @param response
     * @param encoding
     * @param query
     * @param path
     * @param e
     */
    private void writeXPathException(final HttpServletResponse response,
        final int httpStatusCode, final String encoding, final String query,
        final String path, final XPathException e) throws IOException {

        if (!response.isCommitted()) {
            response.reset();
        }

        response.setStatus(httpStatusCode);

        response.setContentType(MimeType.XML_TYPE.getName() + "; charset=" + encoding);

        final OutputStreamWriter writer =
                new OutputStreamWriter(response.getOutputStream(), encoding);

        writer.write("<?xml version=\"1.0\" ?>");
        writer.write("<exception><path>");
        writer.write(path);
        writer.write("</path>");
        writer.write("<message>");
        final String message = e.getMessage() == null ? e.toString() : e.getMessage();
        writer.write(XMLUtil.encodeAttrMarkup(message));
        writer.write("</message>");
        if (query != null) {
            writer.write("<query>");
            writer.write(XMLUtil.encodeAttrMarkup(query));
            writer.write("</query>");
        }
        writer.write("</exception>");

        writer.flush();
        writer.close();
    }

    /**
     * @param response
     * @param encoding
     * @param updateCount
     */
    private void writeXUpdateResult(final HttpServletResponse response,
        final String encoding, final long updateCount) throws IOException {

        response.setContentType(MimeType.XML_TYPE.getName() + "; charset=" + encoding);

        final OutputStreamWriter writer =
                new OutputStreamWriter(response.getOutputStream(), encoding);

        writer.write("<?xml version=\"1.0\" ?>");
        writer.write("<exist:modifications xmlns:exist=\""
                + Namespaces.EXIST_NS + "\" count=\"" + updateCount + "\">");
        writer.write(updateCount + " modifications processed.");
        writer.write("</exist:modifications>");

        writer.flush();
        writer.close();
    }

    /**
     * @param response
     * @param encoding
     * @param broker
     * @param collection
     */
    protected void writeCollection(final HttpServletResponse response,
        final String encoding, final DBBroker broker, final Collection collection)
            throws IOException, PermissionDeniedException, LockException {

        response.setContentType(MimeType.XML_TYPE.getName() + "; charset=" + encoding);

        setCreatedAndLastModifiedHeaders(response, collection.getCreationTime(), collection.getCreationTime());

        final OutputStreamWriter writer =
                new OutputStreamWriter(response.getOutputStream(), encoding);

        SAXSerializer serializer = null;

        try {
            serializer = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);

            serializer.setOutput(writer, defaultProperties);
            final AttributesImpl attrs = new AttributesImpl();

            serializer.startDocument();
            serializer.startPrefixMapping("exist", Namespaces.EXIST_NS);
            serializer.startElement(Namespaces.EXIST_NS, "result",
                    "exist:result", attrs);

            attrs.addAttribute("", "name", "name", "CDATA", collection.getURI()
                    .toString());
            // add an attribute for the creation date as an xs:dateTime
            try {
                final DateTimeValue dtCreated =
                        new DateTimeValue(new Date(collection.getCreationTime()));
                attrs.addAttribute("", "created", "created", "CDATA",
                        dtCreated.getStringValue());
            } catch (final XPathException e) {
                // fallback to long value
                attrs.addAttribute("", "created", "created", "CDATA",
                        String.valueOf(collection.getCreationTime()));
            }

            addPermissionAttributes(attrs, collection.getPermissionsNoLock());

            serializer.startElement(Namespaces.EXIST_NS, "collection",
                    "exist:collection", attrs);

            for (final Iterator<XmldbURI> i = collection.collectionIterator(broker); i.hasNext();) {
                final XmldbURI child = i.next();
                final Collection childCollection = broker.getCollection(collection
                        .getURI().append(child));
                if (childCollection != null
                        && childCollection.getPermissionsNoLock().validate(broker.getSubject(), Permission.READ)) {
                    attrs.clear();
                    attrs.addAttribute("", "name", "name", "CDATA", child.toString());

                    // add an attribute for the creation date as an xs:dateTime
                    try {
                        final DateTimeValue dtCreated =
                                new DateTimeValue(new Date(childCollection.getCreationTime()));
                        attrs.addAttribute("", "created", "created", "CDATA", dtCreated.getStringValue());
                    } catch (final XPathException e) {
                        // fallback to long value
                        attrs.addAttribute("", "created", "created", "CDATA",
                                String.valueOf(childCollection.getCreationTime()));
                    }

                    addPermissionAttributes(attrs, childCollection.getPermissionsNoLock());
                    serializer.startElement(Namespaces.EXIST_NS, "collection", "exist:collection", attrs);
                    serializer.endElement(Namespaces.EXIST_NS, "collection", "exist:collection");
                }
            }

            for (final Iterator<DocumentImpl> i = collection.iterator(broker); i.hasNext();) {
                final DocumentImpl doc = i.next();
                if (doc.getPermissions().validate(broker.getSubject(), Permission.READ)) {
                    final XmldbURI resource = doc.getFileURI();
                    final DocumentMetadata metadata = doc.getMetadata();
                    attrs.clear();
                    attrs.addAttribute("", "name", "name", "CDATA", resource.toString());

                    // add an attribute for the creation date as an xs:dateTime
                    try {
                        final DateTimeValue dtCreated =
                                new DateTimeValue(new Date(metadata.getCreated()));
                        attrs.addAttribute("", "created", "created", "CDATA",
                                dtCreated.getStringValue());
                    } catch (final XPathException e) {
                        // fallback to long value
                        attrs.addAttribute("", "created", "created", "CDATA",
                                String.valueOf(metadata.getCreated()));
                    }

                    // add an attribute for the last modified date as an
                    // xs:dateTime
                    try {
                        final DateTimeValue dtLastModified = new DateTimeValue(
                                new Date(metadata.getLastModified()));
                        attrs.addAttribute("", "last-modified",
                                "last-modified", "CDATA", dtLastModified.getStringValue());
                    } catch (final XPathException e) {
                        // fallback to long value
                        attrs.addAttribute("", "last-modified",
                                "last-modified", "CDATA", String.valueOf(metadata.getLastModified()));
                    }

                    addPermissionAttributes(attrs, doc.getPermissions());
                    serializer.startElement(Namespaces.EXIST_NS, "resource", "exist:resource", attrs);
                    serializer.endElement(Namespaces.EXIST_NS, "resource", "exist:resource");
                }
            }

            serializer.endElement(Namespaces.EXIST_NS, "collection", "exist:collection");
            serializer.endElement(Namespaces.EXIST_NS, "result", "exist:result");

            serializer.endDocument();

            writer.flush();
            writer.close();

        } catch (final SAXException e) {
            // should never happen
            LOG.warn("Error while serializing collection contents: "
                    + e.getMessage(), e);
        } finally {
            if (serializer != null) {
                SerializerPool.getInstance().returnObject(serializer);
            }
        }
    }

    protected void addPermissionAttributes(final AttributesImpl attrs, final Permission perm) {
        attrs.addAttribute("", "owner", "owner", "CDATA", perm.getOwner().getName());
        attrs.addAttribute("", "group", "group", "CDATA", perm.getGroup().getName());
        attrs.addAttribute("", "permissions", "permissions", "CDATA", perm.toString());
    }

    protected void writeResults(final HttpServletResponse response, final DBBroker broker,
            final Sequence results, int howmany, final int start, final boolean typed,
            final Properties outputProperties, final boolean wrap)
            throws BadRequestException {

        // some xquery functions can write directly to the output stream
        // (response:stream-binary() etc...)
        // so if output is already written then dont overwrite here
        if (response.isCommitted()) {
            return;
        }

        // calculate number of results to return
        if (!results.isEmpty()) {
            final int rlen = results.getItemCount();
            if ((start < 1) || (start > rlen)) {
                throw new BadRequestException("Start parameter out of range");
            }
            // FD : correct bound evaluation
            if (((howmany + start) > rlen) || (howmany <= 0)) {
                howmany = rlen - start + 1;
            }
        } else {
            howmany = 0;
        }
        final String method = outputProperties.getProperty(SERIALIZATION_METHOD_PROPERTY, "xml");

        if ("json".equals(method)) {
            writeResultJSON(response, broker, results, howmany, start, outputProperties, wrap);
        } else {
            writeResultXML(response, broker, results, howmany, start, typed, outputProperties, wrap);
        }

    }

    private void writeResultXML(final HttpServletResponse response,
        final DBBroker broker, final Sequence results, final int howmany,
        final int start, final boolean typed, final Properties outputProperties,
        final boolean wrap) throws BadRequestException {
        
        // serialize the results to the response output stream
        final Serializer serializer = broker.getSerializer();
        serializer.reset();
        outputProperties.setProperty(Serializer.GENERATE_DOC_EVENTS, "false");
        SAXSerializer sax = null;
        try {
            sax = (SAXSerializer) SerializerPool.getInstance().borrowObject(
                    SAXSerializer.class);

            // set output headers
            final String encoding = outputProperties.getProperty(OutputKeys.ENCODING);
            if (!response.containsHeader("Content-Type")) {
                String mimeType = outputProperties.getProperty(OutputKeys.MEDIA_TYPE);
                if (mimeType != null) {
                    final int semicolon = mimeType.indexOf(';');
                    if (semicolon != Constants.STRING_NOT_FOUND) {
                        mimeType = mimeType.substring(0, semicolon);
                    }
                    if (wrap) {
                        mimeType = "application/xml";
                    }
                    response.setContentType(mimeType + "; charset=" + encoding);
                }
            }
            if (wrap) {
                outputProperties.setProperty("method", "xml");
            }
            final Writer writer = new OutputStreamWriter(response.getOutputStream(), encoding);
            sax.setOutput(writer, outputProperties);

            serializer.setProperties(outputProperties);
            serializer.setSAXHandlers(sax, sax);

            //Marshaller.marshall(broker, results, start, howmany, serializer.getContentHandler());
            serializer.toSAX(results, start, howmany, wrap, typed);

            writer.flush();
            writer.close();

        } catch (final SAXException e) {
            LOG.warn(e);
            throw new BadRequestException("Error while serializing xml: "
                    + e.toString(), e);
        } catch (final Exception e) {
            LOG.warn(e.getMessage(), e);
            throw new BadRequestException("Error while serializing xml: "
                    + e.toString(), e);
        } finally {
            if (sax != null) {
                SerializerPool.getInstance().returnObject(sax);
            }
        }
    }

    private void writeResultJSON(final HttpServletResponse response,
        final DBBroker broker, final Sequence results, int howmany,
        int start, final Properties outputProperties, final boolean wrap)
            throws BadRequestException {
        
        // calculate number of results to return
        final int rlen = results.getItemCount();
        if (!results.isEmpty()) {
            if ((start < 1) || (start > rlen)) {
                throw new BadRequestException("Start parameter out of range");
            }
            // FD : correct bound evaluation
            if (((howmany + start) > rlen) || (howmany <= 0)) {
                howmany = rlen - start + 1;
            }
        } else {
            howmany = 0;
        }

        final Serializer serializer = broker.getSerializer();
        serializer.reset();
        outputProperties.setProperty(Serializer.GENERATE_DOC_EVENTS, "false");
        try {
            serializer.setProperties(outputProperties);
            final Writer writer = new OutputStreamWriter(response.getOutputStream(), outputProperties.getProperty(OutputKeys.ENCODING));
            final JSONObject root = new JSONObject();
            root.addObject(new JSONSimpleProperty("start", Integer.toString(start), true));
            root.addObject(new JSONSimpleProperty("count", Integer.toString(howmany), true));
            root.addObject(new JSONSimpleProperty("hits", Integer.toString(results.getItemCount()), true));
            if (outputProperties.getProperty(Serializer.PROPERTY_SESSION_ID) != null) {
                root.addObject(new JSONSimpleProperty("session",
                        outputProperties.getProperty(Serializer.PROPERTY_SESSION_ID)));
            }

            final JSONObject data = new JSONObject("data");
            root.addObject(data);

            Item item;
            for (int i = --start; i < start + howmany; i++) {
                item = results.itemAt(i);
                if (Type.subTypeOf(item.getType(), Type.NODE)) {
                    final NodeValue value = (NodeValue) item;
                    JSONValue json;
                    if ("json".equals(outputProperties.getProperty("method", "xml"))) {
                        json = new JSONValue(serializer.serialize(value), false);
                        json.setSerializationType(JSONNode.SerializationType.AS_LITERAL);
                    } else {
                        json = new JSONValue(serializer.serialize(value));
                        json.setSerializationType(JSONNode.SerializationType.AS_ARRAY);
                    }
                    data.addObject(json);
                } else {
                    final JSONValue json = new JSONValue(item.getStringValue());
                    json.setSerializationType(JSONNode.SerializationType.AS_ARRAY);
                    data.addObject(json);
                }
            }

            root.serialize(writer, true);

            writer.flush();
            writer.close();
        } catch (final IOException e) {
            throw new BadRequestException("Error while serializing xml: " + e.toString(), e);
        } catch (final SAXException e) {
            throw new BadRequestException("Error while serializing xml: " + e.toString(), e);
        } catch (final XPathException e) {
            throw new BadRequestException("Error while serializing xml: " + e.toString(), e);
        }
    }

    private boolean isExecutableType(final DocumentImpl resource) {
        if (resource != null
                && (MimeType.XQUERY_TYPE.getName().equals(resource.getMetadata().getMimeType()) // a xquery
                || MimeType.XPROC_TYPE.getName().equals(resource.getMetadata().getMimeType()))//a xproc
                ) {
            return true;
        } else {
            return false;
        }
    }
}
