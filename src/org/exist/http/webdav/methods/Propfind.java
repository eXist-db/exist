/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
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
package org.exist.http.webdav.methods;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.DocumentImpl;
import org.exist.dom.QName;
import org.exist.http.webdav.WebDAV;
import org.exist.http.webdav.WebDAVMethod;
import org.exist.security.Permission;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SAXSerializerPool;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * @author wolf
 */
public class Propfind implements WebDAVMethod {
	
	// error messages
	private final static String PARSE_ERR = "Request content could not be parsed: ";
	private final static String XML_CONFIGURATION_ERR = "Failed to create XML parser: ";
	private final static String UNEXPECTED_ELEMENT_ERR = "Unexpected element found: ";
	
	// search types
	private final static int FIND_ALL_PROPERTIES = 0;
	private final static int FIND_BY_PROPERTY = 1;
	private final static int FIND_PROPERTY_NAMES = 2;
	
	private final static SimpleDateFormat creationDateFormat =
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
	private final static SimpleDateFormat modificationDateFormat =
        new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
	
	static {
		creationDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
		modificationDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
	}
	
	// default prefix for the webdav namespace
	private final static String PREFIX = "D";
	
	// constants for known properties
	private final static QName DISPLAY_NAME_PROP = new QName("displayname", WebDAV.DAV_NS, PREFIX);
	private final static QName CREATION_DATE_PROP = new QName("creationdate", WebDAV.DAV_NS, PREFIX);
	private final static QName RESOURCE_TYPE_PROP = new QName("resourcetype", WebDAV.DAV_NS, PREFIX);
	private final static QName CONTENT_TYPE_PROP = new QName("getcontenttype", WebDAV.DAV_NS, PREFIX);
	private final static QName CONTENT_LENGTH_PROP = new QName("getcontentlength", WebDAV.DAV_NS, PREFIX);
	private final static QName LAST_MODIFIED_PROP = new QName("getlastmodified", WebDAV.DAV_NS, PREFIX);
	private final static QName ETAG_PROP = new QName("etag", WebDAV.DAV_NS, PREFIX);
	private final static QName STATUS_PROP = new QName("status", WebDAV.DAV_NS, PREFIX);
	private final static QName COLLECTION_PROP = new QName("collection", WebDAV.DAV_NS, PREFIX);
	
	private DocumentBuilderFactory docFactory;
	private BrokerPool pool;
	
	public Propfind(BrokerPool pool) {
		this.pool = pool;
		docFactory = DocumentBuilderFactory.newInstance();
		docFactory.setNamespaceAware(true);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.http.webdav.WebDAVMethod#process(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	public void process(User user, HttpServletRequest request, HttpServletResponse response,
			Collection collection, DocumentImpl resource)
			throws ServletException, IOException {
		if(collection == null) {
			LOG.debug("No resource or collection found");
			response.sendError(HttpServletResponse.SC_NOT_FOUND, "No resource or collection found");
			return;
		}
		if(!collection.getPermissions().validate(user, Permission.READ)) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}
		if(resource != null && (!resource.getPermissions().validate(user, Permission.READ))) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}
		Document doc = parseRequestContent(request, response);
		if(doc == null)
			return;
		Element propfind = doc.getDocumentElement();
		if(!(propfind.getLocalName().equals("propfind") && 
				propfind.getNamespaceURI().equals(WebDAV.DAV_NS))) {
			LOG.debug(UNEXPECTED_ELEMENT_ERR + propfind.getNodeName());
			response.sendError(HttpServletResponse.SC_BAD_REQUEST,
					UNEXPECTED_ELEMENT_ERR + propfind.getNodeName());
			return;
		}
		
		int type = FIND_ALL_PROPERTIES;
		DAVProperties searchedProperties = new DAVProperties();
		NodeList childNodes = propfind.getChildNodes();
		for(int i = 0; i < childNodes.getLength(); i++) {
			Node currentNode = childNodes.item(i);
			if(currentNode.getNodeType() == Node.ELEMENT_NODE) {
				if(currentNode.getNamespaceURI().equals(WebDAV.DAV_NS)) {
					if(currentNode.getLocalName().equals("prop")) {
						type = FIND_BY_PROPERTY;
						getPropertyNames(currentNode, searchedProperties);
					}
					if(currentNode.getLocalName().equals("allprop"))
						type = FIND_ALL_PROPERTIES;
					if(currentNode.getLocalName().equals("propname"))
						type = FIND_PROPERTY_NAMES;
				} else {
					// Found an unknown element: return with 400 Bad Request
					LOG.debug("Unexpected child: " + currentNode.getNodeName());
					response.sendError(HttpServletResponse.SC_BAD_REQUEST,
							UNEXPECTED_ELEMENT_ERR + currentNode.getNodeName());
					return;
				}
			}
		}
		
		String servletPath = getServletPath(request);
		int depth = getDepth(request);
		StringWriter os = new StringWriter();
		SAXSerializer serializer = SAXSerializerPool.getInstance().borrowSAXSerializer();
		try {
			serializer.setWriter(os);
			serializer.setOutputProperties(WebDAV.OUTPUT_PROPERTIES);
			AttributesImpl attrs = new AttributesImpl();
			serializer.startDocument();
			serializer.startPrefixMapping(PREFIX, WebDAV.DAV_NS);
			serializer.startElement(WebDAV.DAV_NS, "multistatus", "D:multistatus", attrs);
			
			if(type == FIND_ALL_PROPERTIES || type == FIND_BY_PROPERTY) {
				if(resource != null)
					writeResourceProperties(user, searchedProperties, type, resource, serializer, servletPath);
				else
					writeCollectionProperties(user, searchedProperties, type, collection, serializer, servletPath, depth, 0);
			}
			
			serializer.endElement(WebDAV.DAV_NS, "multistatus", "D:multistatus");
			serializer.endPrefixMapping(PREFIX);
			serializer.endDocument();
		} catch (SAXException e) {
			throw new ServletException("Exception while writing multistatus response: " + e.getMessage(), e);
		} finally {
			SAXSerializerPool.getInstance().returnSAXSerializer(serializer);
		}
		String content = os.toString();
		LOG.debug("response:\n" + content);
		writeResponse(response, content);
	}
	
	private void writeCollectionProperties(User user, DAVProperties searchedProperties, 
			int type, Collection collection, SAXSerializer serializer, String servletPath, 
			int maxDepth, int currentDepth) throws SAXException {
		if(!collection.getPermissions().validate(user, Permission.READ))
			return;
		
		AttributesImpl attrs = new AttributesImpl();
		searchedProperties.reset();
		serializer.startElement(WebDAV.DAV_NS, "response", "D:response", attrs);
		// write D:href
		serializer.startElement(WebDAV.DAV_NS, "href", "D:href", attrs);
		serializer.characters(servletPath + collection.getName());
		serializer.endElement(WebDAV.DAV_NS, "href", "D:href");
		
		serializer.startElement(WebDAV.DAV_NS, "propstat", "D:propstat", attrs);
		serializer.startElement(WebDAV.DAV_NS, "prop", "D:prop", attrs);
		
		if(shouldIncludeProperty(type, searchedProperties, DISPLAY_NAME_PROP)) {
			// write D:displayname
			String displayName = collection.getName();
			int p = displayName.lastIndexOf('/');
			if(p > -1)
				displayName = displayName.substring(p + 1);
			writeSimpleElement(DISPLAY_NAME_PROP, displayName, serializer);
		}
		
		if(shouldIncludeProperty(type, searchedProperties, RESOURCE_TYPE_PROP)) {
			serializer.startElement(WebDAV.DAV_NS, "resourcetype", "D:resourcetype", attrs);
			writeEmptyElement(COLLECTION_PROP, serializer);
			serializer.endElement(WebDAV.DAV_NS, "resourcetype", "D:resourcetype");
		}
		
		if(shouldIncludeProperty(type, searchedProperties, CREATION_DATE_PROP)) {
			long created = collection.getCreationTime();
			writeSimpleElement(CREATION_DATE_PROP, creationDateFormat.format(new Date(created)), serializer);
		}
		
		serializer.endElement(WebDAV.DAV_NS, "prop", "D:prop");
		writeSimpleElement(STATUS_PROP, "HTTP/1.1 200 OK", serializer);
		
		serializer.endElement(WebDAV.DAV_NS, "propstat", "D:propstat");

		if(type == FIND_BY_PROPERTY) {
			List unvisited = searchedProperties.unvisitedProperties();
			if(unvisited.size() > 0) {
				// there were unsupported properties. Report these to the client.
				serializer.startElement(WebDAV.DAV_NS, "propstat", "D:propstat", attrs);
				serializer.startElement(WebDAV.DAV_NS, "prop", "D:prop", attrs);
				for(Iterator i = unvisited.iterator(); i.hasNext(); ) {
					writeEmptyElement((QName)i.next(), serializer);
				}
				serializer.endElement(WebDAV.DAV_NS, "prop", "D:prop");
				writeSimpleElement(STATUS_PROP, "HTTP/1.1 404 Not Found", serializer);
				serializer.endElement(WebDAV.DAV_NS, "propstat", "D:propstat");
			}
		}
		serializer.endElement(WebDAV.DAV_NS, "response", "D:response");
		
		if(currentDepth++ < maxDepth) {
			if(collection.getDocumentCount() > 0) {
				DBBroker broker = null;
				try {
					broker = pool.get();
					for(Iterator i = collection.iterator(broker); i.hasNext(); ) {
						DocumentImpl doc = (DocumentImpl)i.next();
						writeResourceProperties(user, searchedProperties, type, doc, serializer, servletPath);
					}
				} catch (EXistException e) {
				} finally {
					pool.release(broker);
				}
			}
			if(collection.getChildCollectionCount() > 0) {
				for(Iterator i = collection.collectionIterator(); i.hasNext(); ) {
					String child = (String)i.next();
					Collection childCollection = null;
					DBBroker broker = null;
					try {
						broker = pool.get();
						childCollection = broker.getCollection(collection.getName() + '/' + child);
					} catch (Exception e) {
					} finally {
						pool.release(broker);
					}
					if(childCollection != null)
						writeCollectionProperties(user, searchedProperties, type, childCollection, serializer,
							servletPath, maxDepth, currentDepth);
				}
			}
		}
	}
	
	private void writeResourceProperties(User user, DAVProperties searchedProperties, 
			int type, DocumentImpl resource, SAXSerializer serializer, String servletPath) throws SAXException {
		if(!resource.getPermissions().validate(user, Permission.READ))
			return;
		AttributesImpl attrs = new AttributesImpl();
		searchedProperties.reset();
		serializer.startElement(WebDAV.DAV_NS, "response", "D:response", attrs);
		// write D:href
		serializer.startElement(WebDAV.DAV_NS, "href", "D:href", attrs);
		serializer.characters(servletPath + resource.getFileName());
		serializer.endElement(WebDAV.DAV_NS, "href", "D:href");
		
		serializer.startElement(WebDAV.DAV_NS, "propstat", "D:propstat", attrs);
		serializer.startElement(WebDAV.DAV_NS, "prop", "D:prop", attrs);
		
		if(shouldIncludeProperty(type, searchedProperties, DISPLAY_NAME_PROP)) {
			// write D:displayname
			String displayName = resource.getFileName();
			int p = displayName.lastIndexOf('/');
			if(p > -1)
				displayName = displayName.substring(p + 1);
			writeSimpleElement(DISPLAY_NAME_PROP, displayName, serializer);
		}
		
		if(shouldIncludeProperty(type, searchedProperties, RESOURCE_TYPE_PROP)) {
			writeEmptyElement(RESOURCE_TYPE_PROP, serializer);
		}
		
		if(shouldIncludeProperty(type, searchedProperties, CREATION_DATE_PROP)) {
			long created = resource.getCreated();
			writeSimpleElement(CREATION_DATE_PROP, creationDateFormat.format(new Date(created)), serializer);
		}
		
		if(shouldIncludeProperty(type, searchedProperties, LAST_MODIFIED_PROP)) {
			long modified = resource.getLastModified();
			writeSimpleElement(LAST_MODIFIED_PROP, modificationDateFormat.format(new Date(modified)), serializer);
		}
		
		if(shouldIncludeProperty(type, searchedProperties, CONTENT_TYPE_PROP)) {
			if(resource.getResourceType() == DocumentImpl.XML_FILE)
				writeSimpleElement(CONTENT_TYPE_PROP, WebDAV.XML_CONTENT, serializer);
			else
				writeSimpleElement(CONTENT_TYPE_PROP, WebDAV.BINARY_CONTENT, serializer);
		}
		
		serializer.endElement(WebDAV.DAV_NS, "prop", "D:prop");
		writeSimpleElement(STATUS_PROP, "HTTP/1.1 200 OK", serializer);
		
		serializer.endElement(WebDAV.DAV_NS, "propstat", "D:propstat");

		if(type == FIND_BY_PROPERTY) {
			List unvisited = searchedProperties.unvisitedProperties();
			if(unvisited.size() > 0) {
				// there were unsupported properties. Report these to the client.
				serializer.startElement(WebDAV.DAV_NS, "propstat", "D:propstat", attrs);
				serializer.startElement(WebDAV.DAV_NS, "prop", "D:prop", attrs);
				for(Iterator i = unvisited.iterator(); i.hasNext(); ) {
					writeEmptyElement((QName)i.next(), serializer);
				}
				serializer.endElement(WebDAV.DAV_NS, "prop", "D:prop");
				writeSimpleElement(STATUS_PROP, "HTTP/1.1 404 Not Found", serializer);
				serializer.endElement(WebDAV.DAV_NS, "propstat", "D:propstat");
			}
		}
		serializer.endElement(WebDAV.DAV_NS, "response", "D:response");
	}
	
	private boolean shouldIncludeProperty(int type, DAVProperties properties, QName name) {
		if(type == FIND_ALL_PROPERTIES)
			return true;
		return properties.includeProperty(name);
	}
	
	private void writeEmptyElement(QName qname, SAXSerializer serializer) throws SAXException {
		serializer.startElement(WebDAV.DAV_NS, qname.getLocalName(), qname.toString(), new AttributesImpl());
		serializer.endElement(WebDAV.DAV_NS, qname.getLocalName(), qname.toString());
	}
	
	private void writeSimpleElement(QName element, String content, SAXSerializer serializer)
	throws SAXException {
		serializer.startElement(WebDAV.DAV_NS, element.getLocalName(), element.toString(), new AttributesImpl());
		serializer.characters(content);
		serializer.endElement(WebDAV.DAV_NS, element.getLocalName(), element.toString());
	}
	
	private void writeResponse(HttpServletResponse response, String content) 
	throws IOException {
		response.setStatus(WebDAV.SC_MULTI_STATUS);
		response.setContentType("text/xml; charset=UTF-8");
		byte[] data = content.getBytes("UTF-8");
		response.setContentLength(data.length);
		OutputStream os = response.getOutputStream();
		os.write(data);
		os.flush();
	}
	
	private String getServletPath(HttpServletRequest request) {
		String servletPath = request.getContextPath();
	    if(servletPath.endsWith("/")) {
	      servletPath = servletPath.substring(0, servletPath.length()-1);
	    }
	    servletPath += request.getServletPath();
	    if(servletPath.endsWith("/")) {
	      servletPath = servletPath.substring(0, servletPath.length()-1);
	    }
	    return servletPath;
	}
	
	protected int getDepth(HttpServletRequest req) {
	    int depth = 1; // Depth: infitiy
	    String depthStr = req.getHeader("Depth");
	    if (depthStr != null && depthStr.equals("0")) {
	      depth = 0;
	    }
	    return depth;
	 }
	
	private Document parseRequestContent(HttpServletRequest request, HttpServletResponse response) 
	throws ServletException, IOException {
		try {
			String content = getRequestContent(request);
			LOG.debug("request:\n" + content);
			
			DocumentBuilder docBuilder =docFactory.newDocumentBuilder();
			return docBuilder.parse(new InputSource(new StringReader(content)));
		} catch (ParserConfigurationException e) {
			throw new ServletException(XML_CONFIGURATION_ERR + e.getMessage(), e);
		} catch (SAXException e) {
			LOG.debug(e.getMessage(), e);
			response.sendError(HttpServletResponse.SC_BAD_REQUEST,
					PARSE_ERR + e.getMessage());
			return null;
		}
	}
	
	private String getRequestContent(HttpServletRequest request) throws IOException {
		String encoding = request.getCharacterEncoding();
		if(encoding == null)
			encoding = "UTF-8";
		try {
			ServletInputStream is = request.getInputStream();
			Reader  reader = new InputStreamReader(is, encoding);
			StringWriter content = new StringWriter();
			char ch[] = new char[4096];
			int len = 0;
			while((len = reader.read(ch)) > -1)
				content.write(ch, 0, len);
			return content.toString();
		} catch (UnsupportedEncodingException e) {
			throw new IOException("Unsupported character encoding in request content: " + encoding);
		}
	}
	
	private void getPropertyNames(Node propNode, DAVProperties properties) {
		NodeList childList = propNode.getChildNodes();
		for(int i = 0; i < childList.getLength(); i++) {
			Node currentNode = childList.item(i);
			if(currentNode.getNodeType() == Node.ELEMENT_NODE) {
				properties.add(currentNode);
			}
		}
	}
	
	private static class Visited {
		
		boolean visited = false;
		
		boolean isVisited() { return visited; }
		
		void setVisited(boolean visit) { visited = visit; }
		
	}
	
	private static class DAVProperties extends HashMap {
		
		DAVProperties() {
			super();
		}
		
		void add(Node node) {
			QName qname = new QName(node.getLocalName(), node.getNamespaceURI());
			if(node.getNamespaceURI().equals(WebDAV.DAV_NS))
				qname.setPrefix(PREFIX);
			else
				qname.setPrefix(node.getPrefix());
			if(!containsKey(qname))
				put(qname, new Visited());
		}
		
		boolean includeProperty(QName property) {
			Visited visited = (Visited)get(property);
			if(visited == null)
				return false;
			boolean include = !visited.isVisited();
			visited.setVisited(true);
			return include;
		}
		
		List unvisitedProperties() {
			List list = new ArrayList(5);
			for(Iterator i = entrySet().iterator(); i.hasNext(); ) {
				Map.Entry entry = (Map.Entry)i.next();
				if(!((Visited)entry.getValue()).visited)
					list.add(entry.getKey());
			}
			return list;
		}
		
		void reset() {
			for(Iterator i = values().iterator(); i.hasNext(); ) {
				Visited visited = (Visited)i.next();
				visited.setVisited(false);
			}
		}
	}
}
