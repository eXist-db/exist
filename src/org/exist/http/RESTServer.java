/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2009 The eXist Project
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
import java.io.Writer;
import java.net.URI;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerConfigurationException;

import org.apache.log4j.Logger;

import org.exist.EXistException;
import org.exist.Namespaces;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.debuggee.Debuggee;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DefaultDocumentSet;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentMetadata;
import org.exist.dom.MutableDocumentSet;
import org.exist.dom.XMLUtil;
import org.exist.http.servlets.HttpRequestWrapper;
import org.exist.http.servlets.HttpResponseWrapper;
import org.exist.http.servlets.ResponseWrapper;
import org.exist.memtree.ElementImpl;
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
import org.exist.util.LockException;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.Constants;
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
	
	// Should we not obey the instance's defaults? /ljo
	protected final static Properties defaultProperties = new Properties();

	static {
		defaultProperties.setProperty(OutputKeys.INDENT, "yes");
		defaultProperties.setProperty(OutputKeys.ENCODING, "UTF-8");
		defaultProperties.setProperty(OutputKeys.MEDIA_TYPE, MimeType.XML_TYPE
				.getName());
		defaultProperties.setProperty(EXistOutputKeys.EXPAND_XINCLUDES, "yes");
		defaultProperties.setProperty(EXistOutputKeys.HIGHLIGHT_MATCHES,
				"elements");
		defaultProperties.setProperty(EXistOutputKeys.PROCESS_XSL_PI, "yes");
	}

	protected final static Properties defaultOutputKeysProperties = new Properties();

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

	private SessionManager sessionManager;

	// Constructor
	public RESTServer(BrokerPool pool, String formEncoding,
			String containerEncoding, boolean useDynamicContentType) {
		this.formEncoding = formEncoding;
		this.containerEncoding = containerEncoding;
		this.useDynamicContentType = useDynamicContentType;
		this.sessionManager = new SessionManager(pool);
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
	 * <li>_source: if set to "yes" and a resource with mime-type
	 * "application/xquery" is requested then the xquery will not be executed,
	 * instead the source of the document will be returned. Must be enabled in
	 * descriptor.xml with the following syntax <xquery-app><allow-source><xquery
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
	public void doGet(DBBroker broker, HttpServletRequest request,
			HttpServletResponse response, String path)
			throws BadRequestException, PermissionDeniedException,
			NotFoundException, IOException {

		// if required, set character encoding
		if (request.getCharacterEncoding() == null)
			request.setCharacterEncoding(formEncoding);

		String option;
		if ((option = request.getParameter("_release")) != null) {
			int sessionId = Integer.parseInt(option);
			sessionManager.release(sessionId);
			if (LOG.isDebugEnabled())
				LOG.debug("Released session " + sessionId);
			response.setStatus(HttpServletResponse.SC_OK);
			return;
		}

		// Process special parameters

		int howmany = 10;
		int start = 1;
		boolean wrap = true;
		boolean source = false;
		boolean cache = false;
		Properties outputProperties = new Properties(
				defaultOutputKeysProperties);

		String query = request.getParameter("_xpath");
		if (query == null)
			query = request.getParameter("_query");

		if ((option = request.getParameter("_howmany")) != null) {
			try {
				howmany = Integer.parseInt(option);
			} catch (NumberFormatException nfe) {
				throw new BadRequestException(
						"Parameter _howmany should be an int");
			}
		}
		if ((option = request.getParameter("_start")) != null) {
			try {
				start = Integer.parseInt(option);
			} catch (NumberFormatException nfe) {
				throw new BadRequestException(
						"Parameter _start should be an int");
			}
		}
		if ((option = request.getParameter("_wrap")) != null) {
			wrap = option.equals("yes");
            outputProperties.setProperty("_wrap", option);
		}
		if ((option = request.getParameter("_cache")) != null) {
			cache = option.equals("yes");
		}
		if ((option = request.getParameter("_indent")) != null) {
			outputProperties.setProperty(OutputKeys.INDENT, option);
		}
		if ((option = request.getParameter("_source")) != null) {
			source = option.equals("yes");
		}
		if ((option = request.getParameter("_session")) != null) {
			outputProperties
					.setProperty(Serializer.PROPERTY_SESSION_ID, option);
		}
		String stylesheet;
		if ((stylesheet = request.getParameter("_xsl")) != null) {
			if (stylesheet.equals("no")) {
				outputProperties.setProperty(EXistOutputKeys.PROCESS_XSL_PI,
						"no");
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

		if (query != null) {
			// query parameter specified, search method does all the rest of the
			// work
			try {
				search(broker, query, path, null, howmany, start, outputProperties,
						wrap, cache, request, response);

			} catch (XPathException e) {
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
		XmldbURI pathUri = XmldbURI.create( URLDecoder.decode( path , "UTF-8" ) );
		try {
			// check if path leads to an XQuery resource
			String xquery_mime_type = MimeType.XQUERY_TYPE.getName();
			String xproc_mime_type = MimeType.XPROC_TYPE.getName();
			resource = broker.getXMLResource(pathUri, Lock.READ_LOCK);

			if (null != resource && !isExecutableType(resource)) { 
				// return regular resource that is not an xquery and not is xproc
				writeResourceAs(resource, broker, stylesheet, encoding, null,
						outputProperties, request, response);
				return;
			}
			if (resource == null) { // could be request for a Collection
				// no document: check if path points to a collection
				Collection collection = broker.getCollection(pathUri);
				if (collection != null) {
					if (!collection.getPermissions().validate(broker.getUser(),
							Permission.READ))
						throw new PermissionDeniedException(
								"Not allowed to read collection");
					// return a listing of the collection contents
					writeCollection(response, encoding, broker, collection);
					return;
				} else if (source) {
					// didn't find regular resource, or user wants source
					// on a possible xquery resource that was not found
					throw new NotFoundException("Document " + path
							+ " not found");
				}
			}

			XmldbURI servletPath = pathUri;

			// if resource is still null, work up the url path to find an
			// xquery or xproc resource
			while (null == resource) {
				// traverse up the path looking for xquery objects
				servletPath = servletPath.removeLastSegment();
				if (servletPath == XmldbURI.EMPTY_URI)
					break;

				resource = broker.getXMLResource(servletPath, Lock.READ_LOCK);
				if (null != resource && isExecutableType(resource)) {
					break; 

				} else if (null != resource) {
					// not an xquery resource. This means we have a path
					// that cannot contain an xquery object even if we keep
					// moving up the path, so bail out now
					throw new NotFoundException("Document " + path
							+ " not found");
				}
			}

			if (null == resource) { // path search failed
				throw new NotFoundException("Document " + path + " not found");
			}

			// found an XQuery or XProc resource, fixup request values
			String pathInfo = pathUri.trimFromBeginning(servletPath).toString();

			// Should we display the source of the XQuery or XProc or execute it
			Descriptor descriptor = Descriptor.getDescriptorSingleton();
			if (source) {
				// show the source

				// check are we allowed to show the xquery source -
				// descriptor.xml
				if ((null != descriptor) && descriptor.allowSource(path)) {
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
					if (xquery_mime_type.equals(resource.getMetadata().getMimeType())){
						// Execute the XQuery
						executeXQuery(broker, resource, request, response,
								outputProperties, servletPath.toString(), pathInfo);
					} else if (xproc_mime_type.equals(resource.getMetadata().getMimeType())) {
						// Execute the XProc
						executeXProc(broker, resource, request, response,
								outputProperties, servletPath.toString(), pathInfo);
					}
				} catch (XPathException e) {
					if (LOG.isDebugEnabled())
						LOG.debug(e.getMessage(), e);
					if (MimeType.XML_TYPE.getName().equals(mimeType)) {
						writeXPathException(response, HttpServletResponse.SC_BAD_REQUEST, encoding, query, path, e);
					} else {
						writeXPathExceptionHtml(response, HttpServletResponse.SC_BAD_REQUEST, encoding, query,
								path, e);
					}
				}
			}
		} finally {
			if (resource != null)
				resource.getUpdateLock().release(Lock.READ_LOCK);
		}
	}

	public void doHead(DBBroker broker, HttpServletRequest request, HttpServletResponse response, String path) throws BadRequestException, PermissionDeniedException, NotFoundException, IOException
	{
		Properties outputProperties = new Properties(defaultOutputKeysProperties);
		String mimeType = outputProperties.getProperty(OutputKeys.MEDIA_TYPE);
		  
		String encoding;
		if ((encoding = request.getParameter("_encoding")) != null)
			outputProperties.setProperty(OutputKeys.ENCODING, encoding);
		else
			encoding = "UTF-8";
		
		DocumentImpl resource = null;
		XmldbURI pathUri = XmldbURI.create(path);
		try
		{
			resource = broker.getXMLResource(pathUri, Lock.READ_LOCK);
			
			if(resource != null)
			{
				if (!resource.getPermissions().validate(broker.getUser(),
						Permission.READ)) {
					throw new PermissionDeniedException(
							"Permission to read resource " + path + " denied");
				}
				DocumentMetadata metadata = resource.getMetadata();
				response.setContentType(metadata.getMimeType());
				response.setContentLength(resource.getContentLength());
				response.addDateHeader("Last-Modified", metadata.getLastModified());
				response.addDateHeader("Created", metadata.getCreated());
			}
			else
			{
				Collection col = broker.getCollection(pathUri);
				//no resource or collection
				if(col == null)
				{
					response.sendError(HttpServletResponse.SC_NOT_FOUND, "No resource at location: " + path);

					return;
				}
				
				if(!col.getPermissions().validate(broker.getUser(), Permission.READ))
				{
					throw new PermissionDeniedException(
							"Permission to read resource " + path + " denied");
				}
				response.setContentType(MimeType.XML_TYPE.getName() + "; charset=" + encoding);
	            response.addDateHeader("Last-Modified", col.getCreationTime());
				response.addDateHeader("Created", col.getCreationTime());
			}
		}
		finally
		{
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
	 * @throws BadRequestException
	 * @throws PermissionDeniedException
	 */
	public void doPost(DBBroker broker, HttpServletRequest request,
			HttpServletResponse response, String path)
			throws BadRequestException, PermissionDeniedException, IOException {
		// if required, set character encoding
		if (request.getCharacterEncoding() == null)
			request.setCharacterEncoding(formEncoding);

		Properties outputProperties = new Properties(
				defaultOutputKeysProperties);
		XmldbURI pathUri = XmldbURI.create(path);
		DocumentImpl resource = null;

		String encoding = outputProperties.getProperty(OutputKeys.ENCODING);
		String mimeType = outputProperties.getProperty(OutputKeys.MEDIA_TYPE);
		try {
			// check if path leads to an XQuery resource.
			// if yes, the resource is loaded and the XQuery executed.
			String xquery_mime_type = MimeType.XQUERY_TYPE.getName();
			String xproc_mime_type = MimeType.XPROC_TYPE.getName();
			resource = broker.getXMLResource(pathUri, Lock.READ_LOCK);

			XmldbURI servletPath = pathUri;

			// if resource is still null, work up the url path to find an
			// xquery resource
			while (null == resource) {
				// traverse up the path looking for xquery objects
				servletPath = servletPath.removeLastSegment();
				if (servletPath == XmldbURI.EMPTY_URI)
					break;

				resource = broker.getXMLResource(servletPath, Lock.READ_LOCK);
				if (null != resource
						&& (resource.getResourceType() == DocumentImpl.BINARY_FILE
							&& xquery_mime_type.equals(resource.getMetadata().getMimeType()) ||
							resource.getResourceType() == DocumentImpl.XML_FILE
							&& xproc_mime_type.equals(resource.getMetadata().getMimeType()))
							) {
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

			if (resource != null) {
				if (resource.getResourceType() == DocumentImpl.BINARY_FILE
						&& xquery_mime_type.equals(resource.getMetadata().getMimeType()) ||
					resource.getResourceType() == DocumentImpl.XML_FILE
						&& xproc_mime_type.equals(resource.getMetadata().getMimeType())
						) {

					// found an XQuery resource, fixup request values
					String pathInfo = pathUri.trimFromBeginning(servletPath).toString();
					try {
						if (xquery_mime_type.equals(resource.getMetadata().getMimeType())){
							// Execute the XQuery
							executeXQuery(broker, resource, request, response,
									outputProperties, servletPath.toString(), pathInfo);
						} else {
							// Execute the XProc
							executeXProc(broker, resource, request, response,
									outputProperties, servletPath.toString(), pathInfo);
						}
					} catch (XPathException e) {
						if (MimeType.XML_TYPE.getName().equals(mimeType)) {
							writeXPathException(response, HttpServletResponse.SC_BAD_REQUEST, encoding, null, path,
									e);
						} else {
							writeXPathExceptionHtml(response, HttpServletResponse.SC_BAD_REQUEST, encoding, null,
									path, e);
						}
					}
					return;
				}
			}
		} finally {
			if (resource != null)
				resource.getUpdateLock().release(Lock.READ_LOCK);
		}

		// third, normal POST: read the request content and check if
		// it is an XUpdate or a query request.
		int howmany = 10;
		int start = 1;
		boolean enclose = true;
		boolean cache = false;
		String mime = MimeType.XML_TYPE.getName();
		String query = null;
		TransactionManager transact = broker.getBrokerPool()
				.getTransactionManager();
		Txn transaction = transact.beginTransaction();
		try {
			String content = getRequestContent(request);
			NamespaceExtractor nsExtractor = new NamespaceExtractor();
			Element root = parseXML(content, nsExtractor);
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

					if ((option = root.getAttribute("cache")) != null) {
						cache = option.equals("yes");
					}

					if ((option = root.getAttribute("session")) != null
							&& option.length() > 0) {
						outputProperties.setProperty(
								Serializer.PROPERTY_SESSION_ID, option);
					}
					NodeList children = root.getChildNodes();
					for (int i = 0; i < children.getLength(); i++) {
						Node child = children.item(i);
						if (child.getNodeType() == Node.ELEMENT_NODE
								&& child.getNamespaceURI().equals(
										Namespaces.EXIST_NS)) {
							if (child.getLocalName().equals("text")) {
								StringBuilder buf = new StringBuilder();
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
											&& node.getNamespaceURI().equals(
													Namespaces.EXIST_NS)
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
					String result;
					try {
						search(broker, query, path, nsExtractor.getNamespaces(), howmany, start,
								outputProperties, enclose, cache, request,
								response);
					} catch (XPathException e) {
						result = e.getMessage();
                        if (MimeType.XML_TYPE.getName().equals(mimeType)) {
                            writeXPathException(response, HttpServletResponse.SC_ACCEPTED, encoding, null, path,
                                    e);
                        } else {
                            writeXPathExceptionHtml(response, HttpServletResponse.SC_ACCEPTED, encoding, null,
                                    path, e);
                        }
					}

				} else {
					transact.abort(transaction);
					throw new BadRequestException("No query specified");
				}
			} else if (rootNS != null
					&& rootNS.equals(XUpdateProcessor.XUPDATE_NS)) {
				LOG.debug("Got xupdate request: " + content);
				MutableDocumentSet docs = new DefaultDocumentSet();
				Collection collection = broker.getCollection(pathUri);
				if (collection != null) {
					collection.allDocs(broker, docs, true, true);
				} else {
					DocumentImpl xupdateDoc = (DocumentImpl) broker
							.getXMLResource(pathUri);
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

				XUpdateProcessor processor = new XUpdateProcessor(broker, docs,
						AccessContext.REST);
				Modification modifications[] = processor.parse(new InputSource(
						new StringReader(content)));
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

	private ElementImpl parseXML(String content, NamespaceExtractor nsExtractor) throws ParserConfigurationException, SAXException, IOException
	{
		SAXParserFactory factory = SAXParserFactory.newInstance();
		factory.setNamespaceAware(true);
		InputSource src = new InputSource(new StringReader(content));
		SAXParser parser = factory.newSAXParser();
		XMLReader reader = parser.getXMLReader();
		SAXAdapter adapter = new SAXAdapter();
		//reader.setContentHandler(adapter);
		//reader.parse(src);
		nsExtractor.setContentHandler(adapter);
		nsExtractor.setParent(reader);
		nsExtractor.parse(src);

		Document doc = adapter.getDocument();

		return (ElementImpl) doc.getDocumentElement();
	}
	
	private class NamespaceExtractor extends XMLFilterImpl
	{
		List namespaces = new ArrayList(); //<Namespace>
		
		public void startPrefixMapping(String prefix, String uri) throws SAXException
		{
            if (!Namespaces.EXIST_NS.equals(uri)) {
			    Namespace ns = new Namespace(prefix, uri);
			    namespaces.add(ns);
            }
			super.startPrefixMapping(prefix, uri);
		}
		
		public List  getNamespaces()
		{
			return namespaces;
		}
	}
	
	private class Namespace
	{
		private String prefix = null;
		private String uri = null;
		
		public Namespace(String prefix, String uri)
		{
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
	 * Creates an input source from a URL location with an optional known
	 * charset.
	 */
	private InputSource createInputSource(String charset, URI location)
			throws java.io.IOException {
		if (charset == null) {
			return new InputSource(location.toASCIIString());
		} else {
			InputSource source = new InputSource(new InputStreamReader(location
					.toURL().openStream(), charset));
			source.setSystemId(location.toASCIIString());
			return source;
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
	 * @param tempFile
	 *            The temp file from which the PUT will get its content
	 * @param path
	 *            The path to which the file should be stored
	 * @param request
	 * @param response
	 * @throws BadRequestException
	 * @throws PermissionDeniedException
	 */
	public void doPut(DBBroker broker, File tempFile, XmldbURI path,
			HttpServletRequest request, HttpServletResponse response)
			throws BadRequestException, PermissionDeniedException, IOException {
		if (tempFile == null)
			throw new BadRequestException("No request content found for PUT");

		TransactionManager transact = broker.getBrokerPool()
				.getTransactionManager();
		Txn transaction = transact.beginTransaction();
		try {
			XmldbURI docUri = path.lastSegment();
			XmldbURI collUri = path.removeLastSegment();

			if (docUri == null || collUri == null) {
				transact.abort(transaction);
				throw new BadRequestException("Bad path: " + path);
			}
			// TODO : use getOrCreateCollection() right now ?
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
				if (semicolon > 0) {
					contentType = contentType.substring(0, semicolon).trim();
					int equals = contentType.indexOf('=', semicolon);
					if (equals > 0) {
						String param = contentType.substring(semicolon + 1,
								equals).trim();
						if (param.compareToIgnoreCase("charset=") == 0) {
							charset = param.substring(equals + 1).trim();
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
				IndexInfo info = collection.validateXMLResource(transaction,
						broker, docUri, createInputSource(charset, url));
				info.getDocument().getMetadata().setMimeType(contentType);
				collection.store(transaction, broker, info, createInputSource(
						charset, url), false);
				response.setStatus(HttpServletResponse.SC_CREATED);
			} else {

				FileInputStream is = new FileInputStream(tempFile);
				collection.addBinaryResource(transaction, broker, docUri, is,
						contentType, (int) tempFile.length());
				is.close();
				response.setStatus(HttpServletResponse.SC_CREATED);
			}

			transact.commit(transaction);
		} catch (SAXParseException e) {
			transact.abort(transaction);
			throw new BadRequestException("Parsing exception at "
					+ e.getLineNumber() + "/" + e.getColumnNumber() + ": "
					+ e.toString());
        } catch (TriggerException e) {
            transact.abort(transaction);
            throw new PermissionDeniedException(e.getMessage());
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
		} catch (LockException e) {
			transact.abort(transaction);
			throw new PermissionDeniedException(e.getMessage());
		}
		return;
	}

	public void doDelete(DBBroker broker, XmldbURI path,
			HttpServletResponse response) throws PermissionDeniedException,
			NotFoundException, IOException {
		TransactionManager transact = broker.getBrokerPool()
				.getTransactionManager();
		Txn txn = transact.beginTransaction();
		try {
			Collection collection = broker.getCollection(path);
			if (collection != null) {
				// remove the collection
				LOG.debug("removing collection " + path);
				broker.removeCollection(txn, collection);
				response.setStatus(HttpServletResponse.SC_OK);
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
						doc.getCollection().removeXMLResource(txn, broker,
								path.lastSegment());
					response.setStatus(HttpServletResponse.SC_OK);
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

	private String getRequestContent(HttpServletRequest request)
			throws IOException {
		String encoding = request.getCharacterEncoding();
		if (encoding == null)
			encoding = "UTF-8";
		InputStream is = request.getInputStream();
		Reader reader = new InputStreamReader(is, encoding);
		StringWriter content = new StringWriter();
		char ch[] = new char[4096];
		int len = 0;
		while ((len = reader.read(ch)) > -1)
			content.write(ch, 0, len);
		String xml = content.toString();
		return xml;
	}

	/**
	 * TODO: pass request and response objects to XQuery.
	 * 
	 * @throws XPathException
	 */
	protected void search(DBBroker broker, String query, String path,
			List/*<Namespace>*/ namespaces, int howmany, int start, Properties outputProperties, boolean wrap,
			boolean cache, HttpServletRequest request,
			HttpServletResponse response) throws BadRequestException,
			PermissionDeniedException, XPathException {
		String sessionIdParam = outputProperties
				.getProperty(Serializer.PROPERTY_SESSION_ID);
		if (sessionIdParam != null) {
			try {
				int sessionId = Integer.parseInt(sessionIdParam);
				if (sessionId > -1) {
					Sequence cached = sessionManager.get(query, sessionId);
					if (cached != null) {
						LOG.debug("Returning cached query result");
						writeResults(response, broker, cached, howmany, start,
								outputProperties, wrap);
					} else {
						LOG
								.debug("Cached query result not found. Probably timed out. Repeating query.");
					}
				}
			} catch (NumberFormatException e) {
				throw new BadRequestException(
						"Invalid session id passed in query request: "
								+ sessionIdParam);
			}
		}
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
			declareNamespaces(context, namespaces);
			declareVariables(context, request, response);

			if (compiled == null)
				compiled = xquery.compile(context, source);
			else {
				compiled.getContext().updateContext(context);
                context.getWatchDog().reset();
            }
            
			try {
				long startTime = System.currentTimeMillis();
				Sequence resultSequence = xquery.execute(compiled, null, outputProperties);
				long queryTime = System.currentTimeMillis() - startTime;
				if (LOG.isDebugEnabled())
					LOG.debug("Found " + resultSequence.getItemCount() + " in "
							+ queryTime + "ms.");

				if (cache) {
					int sessionId = sessionManager.add(query, resultSequence);
					outputProperties.setProperty(
							Serializer.PROPERTY_SESSION_ID, Integer
									.toString(sessionId));
					if (!response.isCommitted())
						response.setIntHeader("X-Session-Id", sessionId);
				}

				writeResults(response, broker, resultSequence, howmany, start,
						outputProperties, wrap);
			} finally {
				pool.returnCompiledXQuery(source, compiled);
			}
		} catch (IOException e) {
			throw new BadRequestException(e.getMessage(), e);
		}
	}

	private void declareNamespaces(XQueryContext context, List/*<Namespace>*/ namespaces) throws XPathException
	{
		if(namespaces == null)
			return;
		
		for(int i = 0; i < namespaces.size(); i++)
		{
			Namespace ns = (Namespace)namespaces.get(i);
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
	private HttpRequestWrapper declareVariables(XQueryContext context,
			HttpServletRequest request, HttpServletResponse response)
			throws XPathException {
		HttpRequestWrapper reqw = new HttpRequestWrapper(request, formEncoding,
				containerEncoding);
		ResponseWrapper respw = new HttpResponseWrapper(response);
		// context.declareNamespace(RequestModule.PREFIX,
		// RequestModule.NAMESPACE_URI);
		context.declareVariable(RequestModule.PREFIX + ":request", reqw);
		context.declareVariable(ResponseModule.PREFIX + ":response", respw);
		context.declareVariable(SessionModule.PREFIX + ":session", reqw.getSession( false ));
		return reqw;
	}

	/**
	 * Directly execute an XQuery stored as a binary document in the database.
	 */
	private void executeXQuery(DBBroker broker, DocumentImpl resource,
			HttpServletRequest request, HttpServletResponse response,
			Properties outputProperties, String servletPath, String pathInfo)
			throws XPathException, BadRequestException {
		Source source = new DBSource(broker, (BinaryDocument) resource, true);
		XQuery xquery = broker.getXQueryService();
		XQueryPool pool = xquery.getXQueryPool();
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
		context.setModuleLoadPath(XmldbURI.EMBEDDED_SERVER_URI.append(
				resource.getCollection().getURI()).toString());
		context.setStaticallyKnownDocuments(new XmldbURI[] { resource
				.getCollection().getURI() });
		HttpRequestWrapper reqw = declareVariables(context, request, response);
		reqw.setServletPath(servletPath);
		reqw.setPathInfo(pathInfo);
		if (compiled == null) {
			try {
				compiled = xquery.compile(context, source);
			} catch (IOException e) {
				throw new BadRequestException("Failed to read query from "
						+ resource.getURI(), e);
			}
		}
		
		String xdebug = request.getParameter("XDEBUG_SESSION_START");
		if (xdebug != null)
			compiled.getContext().setDebugMode(true);
		else {
			//if have session
			xdebug = request.getParameter("XDEBUG_SESSION");
			if (xdebug != null) {
				compiled.getContext().setDebugMode(true);
			} else {
				//looking for session in cookies (FF XDebug Helper add-ons)
    			Cookie[] cookies = request.getCookies();
    			if (cookies != null) {
        			for (int i = 0; i < cookies.length; i++) {
        				if (cookies[i].getName().equals("XDEBUG_SESSION")) {
        					//TODO: check for value?? ("eXistDB_XDebug" ? or leave "default") -shabanovd 
        					compiled.getContext().setDebugMode(true);
            				break;
        				}
        			}
    			}
			}
		}
		boolean wrap = outputProperties.getProperty("_wrap") != null &&
            outputProperties.getProperty("_wrap").equals("yes");

		try {
			Sequence result = xquery.execute(compiled, null, outputProperties);
			writeResults(response, broker, result, -1, 1, outputProperties, wrap);
		} finally {
			pool.returnCompiledXQuery(source, compiled);
		}
	}

	/**
	 * Directly execute an XProc stored as a XML document in the database.
	 */
	private void executeXProc(DBBroker broker, DocumentImpl resource,
			HttpServletRequest request, HttpServletResponse response,
			Properties outputProperties, String servletPath, String pathInfo)
			throws XPathException, BadRequestException {
		URLSource source = new URLSource(this.getClass().getResource("run-xproc.xq"));
		XQuery xquery = broker.getXQueryService();
		XQueryPool pool = xquery.getXQueryPool();
		XQueryContext context;
		CompiledXQuery compiled = pool.borrowCompiledXQuery(broker, source);
		if (compiled == null) {
			context = xquery.newContext(AccessContext.REST);
		} else {
			context = compiled.getContext();
		}
		
		context.declareVariable("pipeline", resource.getURI().toString());
		
		String stdin = request.getParameter("stdin");
		context.declareVariable("stdin", stdin == null ? "" : stdin);
		
		String debug = request.getParameter("debug");
		context.declareVariable("debug",  debug == null ? "0" : debug);
		
		// TODO: don't hardcode this?
		context.setModuleLoadPath(XmldbURI.EMBEDDED_SERVER_URI.append(
				resource.getCollection().getURI()).toString());
		context.setStaticallyKnownDocuments(new XmldbURI[] { resource
				.getCollection().getURI() });
		HttpRequestWrapper reqw = declareVariables(context, request, response);
		reqw.setServletPath(servletPath);
		reqw.setPathInfo(pathInfo);
		if (compiled == null) {
			try {
				compiled = xquery.compile(context, source);
			} catch (IOException e) {
				throw new BadRequestException("Failed to read query from "
						+ source.getURL(), e);
			}
		}
		
		try {
			Sequence result = xquery.execute(compiled, null, outputProperties);
			writeResults(response, broker, result, -1, 1, outputProperties, false);
		} finally {
			pool.returnCompiledXQuery(source, compiled);
		}
	}
		
	// writes out a resource, uses asMimeType as the specified mime-type or if
	// null uses the type of the resource
	private void writeResourceAs(DocumentImpl resource, DBBroker broker,
			String stylesheet, String encoding, String asMimeType,
			Properties outputProperties, HttpServletRequest request, HttpServletResponse response)
			throws BadRequestException, PermissionDeniedException, IOException {

		// Do we have permission to read the resource
		if (!resource.getPermissions().validate(broker.getUser(),
				Permission.READ)) {
			throw new PermissionDeniedException("Not allowed to read resource");
		}

		DocumentMetadata metadata = resource.getMetadata();
        response.addDateHeader("Last-Modified", metadata.getLastModified());
		response.addDateHeader("Created", metadata.getCreated());
		if (resource.getResourceType() == DocumentImpl.BINARY_FILE) {
			// binary resource

			if (asMimeType == null) { // wasn't a mime-type specified?
				asMimeType = resource.getMetadata().getMimeType();
			}
			
			if (asMimeType.startsWith("text/")){
				response.setContentType(asMimeType + "; charset="
						+ encoding);
			} else {
				response.setContentType(asMimeType);
			}

			OutputStream os = response.getOutputStream();
			broker.readBinaryResource((BinaryDocument) resource, os);
			os.flush();
		} else {
			// xml resource

			SAXSerializer sax = null;
			Serializer serializer = broker.getSerializer();
			serializer.reset();

			//setup the http context
			HttpContext httpContext = serializer.new HttpContext();
			HttpRequestWrapper reqw = new HttpRequestWrapper(request, formEncoding, containerEncoding);
			httpContext.setRequest(reqw);
			httpContext.setSession(reqw.getSession(false));
			serializer.setHttpContext(httpContext);
			
			
			// Serialize the document
			try {
				sax = (SAXSerializer) SerializerPool.getInstance()
						.borrowObject(SAXSerializer.class);

				// use a stylesheet if specified in query parameters
				if (stylesheet != null) {
					serializer.setStylesheet(resource, stylesheet);
				}
				serializer.setProperties(outputProperties);
                serializer.prepareStylesheets(resource);

				if (asMimeType != null) { // was a mime-type specified?
					response.setContentType(asMimeType + "; charset="
							+ encoding);
				} else {
					if (serializer.isStylesheetApplied()
							|| serializer.hasXSLPi(resource) != null) {
						asMimeType = serializer.getStylesheetProperty(OutputKeys.MEDIA_TYPE);
						if (!useDynamicContentType || asMimeType == null)
							asMimeType = MimeType.HTML_TYPE.getName();
						LOG.debug("media-type: " + asMimeType);
						response.setContentType(asMimeType + "; charset="
								+ encoding);
					} else {
						asMimeType = resource.getMetadata().getMimeType();
						response.setContentType(asMimeType + "; charset="
								+ encoding);
					}
				}
				if (asMimeType.equals(MimeType.HTML_TYPE.getName())) {
					outputProperties.setProperty("method", "xhtml");
					outputProperties.setProperty("media-type", "text/html; charset="
									+ encoding);
					outputProperties.setProperty("ident", "yes");
					outputProperties.setProperty("omit-xml-declaration", "no");
				}

				OutputStreamWriter writer = new OutputStreamWriter(response
						.getOutputStream(), encoding);
				sax.setOutput(writer, outputProperties);
				serializer.setSAXHandlers(sax, sax);
				
				serializer.toSAX(resource);

				writer.flush();
				writer.close();
			} catch (SAXException saxe) {
				LOG.warn(saxe);
				throw new BadRequestException("Error while serializing XML: "
						+ saxe.getMessage());
			} catch (TransformerConfigurationException e) {
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
	private void writeXPathExceptionHtml(HttpServletResponse response, int httpStatusCode,
			String encoding, String query, String path, XPathException e)
			throws IOException {

		if( !response.isCommitted() ) {
			response.reset();
		}

        response.setStatus(httpStatusCode);
			
		response.setContentType(MimeType.HTML_TYPE.getName() + "; charset="
				+ encoding);

		OutputStreamWriter writer = new OutputStreamWriter(response
				.getOutputStream(), encoding);
		writer.write(QUERY_ERROR_HEAD);
		writer.write("<p class=\"path\"><span class=\"high\">Path</span>: ");
		writer.write("<a href=\"");
		writer.write(path);
		writer.write("\">");
		writer.write(path);
		writer.write("</a></p>");

		writer.write("<p class=\"errmsg\">");
		String message = e.getMessage() == null ? e.toString() : e.getMessage();
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
	private void writeXPathException(HttpServletResponse response, int httpStatusCode, String encoding, String query, String path, XPathException e)
			throws IOException {

		if( !response.isCommitted() ) {
			response.reset();
		}

        response.setStatus(httpStatusCode);

		response.setContentType(MimeType.XML_TYPE.getName() + "; charset="
				+ encoding);

		OutputStreamWriter writer = new OutputStreamWriter(response
				.getOutputStream(), encoding);

		writer.write("<?xml version=\"1.0\" ?>");
		writer.write("<exception><path>");
		writer.write(path);
		writer.write("</path>");
		writer.write("<message>");
		String message = e.getMessage() == null ? e.toString() : e.getMessage();
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
	private void writeXUpdateResult(HttpServletResponse response,
			String encoding, long updateCount) throws IOException {
		response.setContentType(MimeType.XML_TYPE.getName() + "; charset="
				+ encoding);

		OutputStreamWriter writer = new OutputStreamWriter(response
				.getOutputStream(), encoding);

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
	protected void writeCollection(HttpServletResponse response,
			String encoding, DBBroker broker, Collection collection)
			throws IOException {

		response.setContentType(MimeType.XML_TYPE.getName() + "; charset="
				+ encoding);
        response.addDateHeader("Last-Modified", collection.getCreationTime());
		response.addDateHeader("Created", collection.getCreationTime());

		OutputStreamWriter writer = new OutputStreamWriter(response
				.getOutputStream(), encoding);

		SAXSerializer serializer = null;

		try {
			serializer = (SAXSerializer) SerializerPool.getInstance()
					.borrowObject(SAXSerializer.class);

			serializer.setOutput(writer, defaultProperties);
			AttributesImpl attrs = new AttributesImpl();

			serializer.startDocument();
			serializer.startPrefixMapping("exist", Namespaces.EXIST_NS);
			serializer.startElement(Namespaces.EXIST_NS, "result",
					"exist:result", attrs);

			attrs.addAttribute("", "name", "name", "CDATA", collection.getURI()
					.toString());
			// add an attribute for the creation date as an xs:dateTime
			try {
				DateTimeValue dtCreated = new DateTimeValue(new Date(collection
						.getCreationTime()));
				attrs.addAttribute("", "created", "created", "CDATA", dtCreated
						.getStringValue());
			} catch (XPathException e) {
				// fallback to long value
				attrs.addAttribute("", "created", "created", "CDATA", String
						.valueOf(collection.getCreationTime()));
			}

			addPermissionAttributes(attrs, collection.getPermissions());

			serializer.startElement(Namespaces.EXIST_NS, "collection",
					"exist:collection", attrs);

			for (Iterator i = collection.collectionIterator(); i.hasNext();) {
				XmldbURI child = (XmldbURI) i.next();
				Collection childCollection = broker.getCollection(collection
						.getURI().append(child));
				if (childCollection != null
						&& childCollection.getPermissions().validate(
								broker.getUser(), Permission.READ)) {
					attrs.clear();
					attrs.addAttribute("", "name", "name", "CDATA", child
							.toString());

					// add an attribute for the creation date as an xs:dateTime
					try {
						DateTimeValue dtCreated = new DateTimeValue(new Date(
								childCollection.getCreationTime()));
						attrs.addAttribute("", "created", "created", "CDATA",
								dtCreated.getStringValue());
					} catch (XPathException e) {
						// fallback to long value
						attrs.addAttribute("", "created", "created", "CDATA",
								String.valueOf(childCollection
										.getCreationTime()));
					}

					addPermissionAttributes(attrs, childCollection
							.getPermissions());
					serializer.startElement(Namespaces.EXIST_NS, "collection",
							"exist:collection", attrs);
					serializer.endElement(Namespaces.EXIST_NS, "collection",
							"exist:collection");
				}
			}

			for (Iterator i = collection.iterator(broker); i.hasNext();) {
				DocumentImpl doc = (DocumentImpl) i.next();
				if (doc.getPermissions().validate(broker.getUser(),
						Permission.READ)) {
					XmldbURI resource = doc.getFileURI();
					DocumentMetadata metadata = doc.getMetadata();
					attrs.clear();
					attrs.addAttribute("", "name", "name", "CDATA", resource
							.toString());

					// add an attribute for the creation date as an xs:dateTime
					try {
						DateTimeValue dtCreated = new DateTimeValue(new Date(
								metadata.getCreated()));
						attrs.addAttribute("", "created", "created", "CDATA",
								dtCreated.getStringValue());
					} catch (XPathException e) {
						// fallback to long value
						attrs.addAttribute("", "created", "created", "CDATA",
								String.valueOf(metadata.getCreated()));
					}

					// add an attribute for the last modified date as an
					// xs:dateTime
					try {
						DateTimeValue dtLastModified = new DateTimeValue(
								new Date(metadata.getLastModified()));
						attrs.addAttribute("", "last-mofified",
								"last-modified", "CDATA", dtLastModified
										.getStringValue());
					} catch (XPathException e) {
						// fallback to long value
						attrs.addAttribute("", "last-modified",
								"last-modified", "CDATA", String
										.valueOf(metadata.getLastModified()));
					}

					addPermissionAttributes(attrs, doc.getPermissions());
					serializer.startElement(Namespaces.EXIST_NS, "resource",
							"exist:resource", attrs);
					serializer.endElement(Namespaces.EXIST_NS, "resource",
							"exist:resource");
				}
			}

			serializer.endElement(Namespaces.EXIST_NS, "collection",
					"exist:collection");
			serializer
					.endElement(Namespaces.EXIST_NS, "result", "exist:result");

			serializer.endDocument();

			writer.flush();
			writer.close();

		} catch (SAXException e) {
			// should never happen
			LOG.warn("Error while serializing collection contents: "
					+ e.getMessage(), e);
		} finally {
			if (serializer != null) {
				SerializerPool.getInstance().returnObject(serializer);
			}
		}
	}

	protected void addPermissionAttributes(AttributesImpl attrs, Permission perm) {
		attrs.addAttribute("", "owner", "owner", "CDATA", perm.getOwner());
		attrs.addAttribute("", "group", "group", "CDATA", perm.getOwnerGroup());
		attrs.addAttribute("", "permissions", "permissions", "CDATA", perm
				.toString());
	}

	protected void writeResults(HttpServletResponse response, DBBroker broker,
			Sequence results, int howmany, int start,
			Properties outputProperties, boolean wrap)
			throws BadRequestException {

		// some xquery functions can write directly to the output stream
		// (response:stream-binary() etc...)
		// so if output is already written then dont overwrite here
		if (response.isCommitted())
			return;

		// calculate number of results to return
		if (!results.isEmpty()) {
			int rlen = results.getItemCount();
			if ((start < 1) || (start > rlen))
				throw new BadRequestException("Start parameter out of range");
			// FD : correct bound evaluation
			if (((howmany + start) > rlen) || (howmany <= 0))
				howmany = rlen - start + 1;
		} else {
			howmany = 0;
		}

		// serialize the results to the response output stream
		Serializer serializer = broker.getSerializer();
		serializer.reset();
		outputProperties.setProperty(Serializer.GENERATE_DOC_EVENTS, "false");
		SAXSerializer sax = null;
		try {
			sax = (SAXSerializer) SerializerPool.getInstance().borrowObject(
					SAXSerializer.class);

			// set output headers
			String encoding = outputProperties.getProperty(OutputKeys.ENCODING);
			String mimeType = outputProperties
					.getProperty(OutputKeys.MEDIA_TYPE);
			if (mimeType != null) {
				int semicolon = mimeType.indexOf(';');
				if (semicolon != Constants.STRING_NOT_FOUND) {
					mimeType = mimeType.substring(0, semicolon);
				}
                if (wrap) {
                    mimeType = "text/xml";
                }
				response.setContentType(mimeType + "; charset=" + encoding);
			}
            if (wrap)
                outputProperties.setProperty("method", "xml");
			Writer writer = new OutputStreamWriter(response.getOutputStream(),
					encoding);
			sax.setOutput(writer, outputProperties);

			serializer.setProperties(outputProperties);
			serializer.setSAXHandlers(sax, sax);
			serializer.toSAX(results, start, howmany, wrap);

			writer.flush();
			writer.close();

		} catch (SAXException e) {
			LOG.warn(e);
			throw new BadRequestException("Error while serializing xml: "
					+ e.toString(), e);
		} catch (Exception e) {
			LOG.warn(e.getMessage(), e);
			throw new BadRequestException("Error while serializing xml: "
					+ e.toString(), e);
		} finally {
			if (sax != null) {
				SerializerPool.getInstance().returnObject(sax);
			}
		}
	}

    private boolean isExecutableType(DocumentImpl resource) {
	if (resource != null 
	    && (MimeType.XQUERY_TYPE.getName().equals(resource.getMetadata().getMimeType()) // a xquery
		|| MimeType.XPROC_TYPE.getName().equals(resource.getMetadata().getMimeType()))//a xproc
	    )
	    return true;
	else
	    return false;
	    
    }


}
