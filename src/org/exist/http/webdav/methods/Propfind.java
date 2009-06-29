/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.http.webdav.methods;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentMetadata;
import org.exist.dom.LockToken;
import org.exist.dom.QName;
import org.exist.http.webdav.WebDAV;
import org.exist.http.webdav.WebDAVUtil;
import org.exist.security.Permission;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.util.LockException;
import org.exist.util.MimeType;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.xmldb.XmldbURI;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Implements the WebDAV PROPFIND method.
 *
 * @author wolf
 */
public class Propfind extends AbstractWebDAVMethod {
    
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
    private final static QName SUPPORTED_LOCK_PROP = new QName("supportedlock", WebDAV.DAV_NS, PREFIX);
    private final static QName EXCLUSIVE_LOCK_PROP = new QName("exclusive", WebDAV.DAV_NS, PREFIX);
    private final static QName WRITE_LOCK_PROP = new QName("write", WebDAV.DAV_NS, PREFIX);
    private final static QName ETAG_PROP = new QName("etag", WebDAV.DAV_NS, PREFIX);
    private final static QName STATUS_PROP = new QName("status", WebDAV.DAV_NS, PREFIX);
    private final static QName COLLECTION_PROP = new QName("collection", WebDAV.DAV_NS, PREFIX);
    
    private final static QName LOCK_DISCOVERY_PROP = new QName("lockdiscovery", WebDAV.DAV_NS, PREFIX);
    private final static QName ACTIVELOCK_PROP = new QName("activelock", WebDAV.DAV_NS, PREFIX);
    private final static QName LOCKTYPE_PROP = new QName("activelock", WebDAV.DAV_NS, PREFIX);
    private final static QName LOCK_SCOPE_PROP = new QName("lockscope", WebDAV.DAV_NS, PREFIX);
    private final static QName LOCK_DEPTH_PROP = new QName("depth", WebDAV.DAV_NS, PREFIX);
    private final static QName LOCK_OWNER_PROP = new QName("owner", WebDAV.DAV_NS, PREFIX);
    private final static QName LOCK_TIMEOUT_PROP = new QName("timeout", WebDAV.DAV_NS, PREFIX);
    private final static QName LOCK_TOKEN_PROP = new QName("locktocken", WebDAV.DAV_NS, PREFIX);
    
    
    private final static QName[] DEFAULT_COLLECTION_PROPS = {
        DISPLAY_NAME_PROP,
        RESOURCE_TYPE_PROP,
        CREATION_DATE_PROP,
        LAST_MODIFIED_PROP
    };
    
    private final static QName[] DEFAULT_RESOURCE_PROPS = {
        DISPLAY_NAME_PROP,
        RESOURCE_TYPE_PROP,
        CREATION_DATE_PROP,
        LAST_MODIFIED_PROP,
        CONTENT_TYPE_PROP,
        CONTENT_LENGTH_PROP,
        SUPPORTED_LOCK_PROP,
        LOCK_DISCOVERY_PROP
    };
    
    public Propfind(BrokerPool pool) {
        super(pool);
    }
    
    public void process(User user, HttpServletRequest request, HttpServletResponse response, XmldbURI path)
    throws ServletException, IOException {
        DBBroker broker = null;
        Collection collection = null;
        DocumentImpl resource = null;
        try {
            broker = pool.get(user);
            // open the collection or resource specified in the path
            collection = broker.openCollection(path, Lock.READ_LOCK);
            if(collection == null) {
                ///TODO : use dedicated function in XmldbURI
                // no collection found: check for a resource
                XmldbURI docUri = path.lastSegment();
                XmldbURI collUri = path.removeLastSegment();
                collection = broker.openCollection(collUri, Lock.READ_LOCK);
                if(collection == null) {
                    LOG.debug("No resource or collection found for path: " + path);
                    response.sendError(HttpServletResponse.SC_NOT_FOUND, NOT_FOUND_ERR);
                    return;
                }
                resource = collection.getDocumentWithLock(broker, docUri, Lock.READ_LOCK);
                if(resource == null) {
                    LOG.debug("No resource found for path: " + path);
                    response.sendError(HttpServletResponse.SC_NOT_FOUND, NOT_FOUND_ERR);
                    return;
                }
            }
            if(!collection.getPermissions().validate(user, Permission.READ)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
            //TODO : release collection lock here ?
            //Take care however : collection is still used below

            // parse the request contents
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            docFactory.setNamespaceAware(true);
            DocumentBuilder docBuilder;
            try {
                docBuilder = docFactory.newDocumentBuilder();
            } catch (ParserConfigurationException e1) {
                throw new ServletException(WebDAVUtil.XML_CONFIGURATION_ERR, e1);
            }
            Document doc = WebDAVUtil.parseRequestContent(request, response, docBuilder);

            int type = FIND_ALL_PROPERTIES;
            DAVProperties searchedProperties = new DAVProperties();

//                LOG.debug("input:\n"+xmlToString(doc));

            if(doc != null) {
                Element propfind = doc.getDocumentElement();
                if(!(propfind.getLocalName().equals("propfind") &&
                        propfind.getNamespaceURI().equals(WebDAV.DAV_NS))) {
                    LOG.debug(WebDAVUtil.UNEXPECTED_ELEMENT_ERR + propfind.getNodeName());
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                            WebDAVUtil.UNEXPECTED_ELEMENT_ERR + propfind.getNodeName());
                    return;
                }

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
                                    WebDAVUtil.UNEXPECTED_ELEMENT_ERR + currentNode.getNodeName());
                            return;
                        }
                    }
                }
            }

            // write response
            String servletPath = getServletPath(request);
            int depth = getDepth(request);
            StringWriter os = new StringWriter();
            SAXSerializer serializer = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);
            try {
                serializer.setOutput(os, WebDAV.OUTPUT_PROPERTIES);
                AttributesImpl attrs = new AttributesImpl();
                serializer.startDocument();
                serializer.startPrefixMapping(PREFIX, WebDAV.DAV_NS);
                serializer.startElement(WebDAV.DAV_NS, "multistatus", "D:multistatus", attrs);

                if(type == FIND_ALL_PROPERTIES || type == FIND_BY_PROPERTY) {
                    if(resource != null)
                        writeResourceProperties(user, searchedProperties, type, collection, resource, serializer, servletPath);
                    else
                        writeCollectionProperties(user, broker, searchedProperties, type, collection, serializer, servletPath, depth, 0);
                } else if(type == FIND_PROPERTY_NAMES)
                    writePropertyNames(collection, resource, serializer, servletPath);

                serializer.endElement(WebDAV.DAV_NS, "multistatus", "D:multistatus");
                serializer.endPrefixMapping(PREFIX);
                serializer.endDocument();
            } catch (SAXException e) {
                throw new ServletException("Exception while writing multistatus response: " + e.getMessage(), e);
            } finally {
                SerializerPool.getInstance().returnObject(serializer);
            }
            String content = os.toString();
//                LOG.debug("response:\n" + content);
            writeResponse(response, content);
        } catch (EXistException e) {
            throw new ServletException(e.getMessage(), e);
        } catch (LockException e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        } finally {
            pool.release(broker);
            if(resource != null)
                resource.getUpdateLock().release(Lock.READ_LOCK);
            if(collection != null)
                collection.release(Lock.READ_LOCK);
        }
    }
    
    private void writeCollectionProperties(User user, DBBroker broker, DAVProperties searchedProperties,
            int type, Collection collection, SAXSerializer serializer, String servletPath,
            int maxDepth, int currentDepth) throws SAXException {
        if(!collection.getPermissions().validate(user, Permission.READ))
            return;
        
        AttributesImpl attrs = new AttributesImpl();
        searchedProperties.reset();
        serializer.startElement(WebDAV.DAV_NS, "response", "D:response", attrs);
        // write D:href
        serializer.startElement(WebDAV.DAV_NS, "href", "D:href", attrs);
        serializer.characters(servletPath + collection.getURI());
        serializer.endElement(WebDAV.DAV_NS, "href", "D:href");
        
        serializer.startElement(WebDAV.DAV_NS, "propstat", "D:propstat", attrs);
        serializer.startElement(WebDAV.DAV_NS, "prop", "D:prop", attrs);
        
        if(shouldIncludeProperty(type, searchedProperties, DISPLAY_NAME_PROP)) {
            // write D:displayname
            String displayName = collection.getURI().lastSegment().toString();
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
        
        if(shouldIncludeProperty(type, searchedProperties, LAST_MODIFIED_PROP)) {
            // for collections, the last modification date is the same as the creation date
            long created = collection.getCreationTime();
            writeSimpleElement(LAST_MODIFIED_PROP, modificationDateFormat.format(new Date(created)), serializer);
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
                for(Iterator i = collection.iterator(broker); i.hasNext(); ) {
                    DocumentImpl doc = (DocumentImpl)i.next();
                    try {
                        doc.getUpdateLock().acquire(Lock.READ_LOCK);
                        writeResourceProperties(user, searchedProperties, type, collection, doc, serializer, servletPath);
                    } catch (LockException e) {
                        LOG.debug("Failed to acquire lock on document " + doc.getURI());
                    } finally {
                        doc.getUpdateLock().release(Lock.READ_LOCK);
                    }
                }
            }
            if(collection.getChildCollectionCount() > 0) {
                for(Iterator i = collection.collectionIterator(); i.hasNext(); ) {
                    XmldbURI child = (XmldbURI)i.next();
                    Collection childCollection = null;
                    try {
                        childCollection = broker.openCollection(collection.getURI().append(child), Lock.READ_LOCK);
                        if(childCollection != null)
                            writeCollectionProperties(user, broker, searchedProperties, type, childCollection, serializer,
                                    servletPath, maxDepth, currentDepth);
                    } catch (Exception e) {
                    	//Doh !
                    } finally {
                        if(childCollection != null)
                            childCollection.release(Lock.READ_LOCK);
                    }
                }
            }
        }
    }
    
    private void writeResourceProperties(User user, DAVProperties searchedProperties,
            int type, Collection collection, DocumentImpl resource, SAXSerializer serializer, String servletPath) throws SAXException {
        if(!resource.getPermissions().validate(user, Permission.READ))
            return;
        DocumentMetadata metadata = resource.getMetadata();
        AttributesImpl attrs = new AttributesImpl();
        searchedProperties.reset();
        serializer.startElement(WebDAV.DAV_NS, "response", "D:response", attrs);
        // write D:href
        serializer.startElement(WebDAV.DAV_NS, "href", "D:href", attrs);
        serializer.characters(servletPath + collection.getURI().append(resource.getFileURI()).toString());
        serializer.endElement(WebDAV.DAV_NS, "href", "D:href");
        
        serializer.startElement(WebDAV.DAV_NS, "propstat", "D:propstat", attrs);
        serializer.startElement(WebDAV.DAV_NS, "prop", "D:prop", attrs);
        
        if(shouldIncludeProperty(type, searchedProperties, DISPLAY_NAME_PROP)) {
            // write D:displayname
            writeSimpleElement(DISPLAY_NAME_PROP, resource.getFileURI().toString(), serializer);
        }
        
        if(shouldIncludeProperty(type, searchedProperties, RESOURCE_TYPE_PROP)) {
            writeEmptyElement(RESOURCE_TYPE_PROP, serializer);
        }
        
        if(shouldIncludeProperty(type, searchedProperties, CREATION_DATE_PROP)) {
            long created = metadata.getCreated();
            writeSimpleElement(CREATION_DATE_PROP, creationDateFormat.format(new Date(created)), serializer);
        }
        
        if(shouldIncludeProperty(type, searchedProperties, LAST_MODIFIED_PROP)) {
            long modified = metadata.getLastModified();
            writeSimpleElement(LAST_MODIFIED_PROP, modificationDateFormat.format(new Date(modified)), serializer);
        }
        
        if(shouldIncludeProperty(type, searchedProperties, CONTENT_LENGTH_PROP)) {
            writeSimpleElement(CONTENT_LENGTH_PROP, Long.toString(resource.getContentLength()), serializer);
        }
        
        if(shouldIncludeProperty(type, searchedProperties, CONTENT_TYPE_PROP)) {
            writeSimpleElement(CONTENT_TYPE_PROP, metadata.getMimeType(), serializer);
        }
        
        if(shouldIncludeProperty(type, searchedProperties, SUPPORTED_LOCK_PROP)) {
            serializer.startElement(WebDAV.DAV_NS, "supportedlock", "D:supportedlock", attrs);
            serializer.startElement(WebDAV.DAV_NS, "lockentry", "D:lockentry", attrs);
            serializer.startElement(WebDAV.DAV_NS, "lockscope", "D:lockscope", attrs);
            writeEmptyElement(EXCLUSIVE_LOCK_PROP, serializer);
            serializer.endElement(WebDAV.DAV_NS, "lockscope", "D:lockscope");
            serializer.startElement(WebDAV.DAV_NS, "locktype", "D:locktype", attrs);
            writeEmptyElement(WRITE_LOCK_PROP, serializer);
            serializer.endElement(WebDAV.DAV_NS, "locktype", "D:locktype");
            serializer.endElement(WebDAV.DAV_NS, "lockentry", "D:lockentry");
            serializer.endElement(WebDAV.DAV_NS, "supportedlock", "D:supportedlock");
        }
        
        if(shouldIncludeProperty(type, searchedProperties, LOCK_DISCOVERY_PROP)){
            DocumentMetadata meta = resource.getMetadata();
            LockToken token = null;
            
            if( meta!=null ){
                token = meta.getLockToken();
            } else {
                LOG.info("No Document meta data");
            }
            
            serializer.startElement(WebDAV.DAV_NS, "lockdiscovery", "D:lockdiscovery", attrs);
            if(token==null){
//                LOG.debug("No lock token");
            } else {               
                
                serializer.startElement(WebDAV.DAV_NS, "activelock", "D:activelock", attrs);
                
                String lockType;
                switch(token.getType()){
                    case LockToken.LOCK_TYPE_WRITE : lockType="write"; break;
                    default : lockType="none";
                }
                writeSimpleElement(LOCKTYPE_PROP, lockType, serializer);
                
                String lockScope;
                switch(token.getScope()){
                    case LockToken.LOCK_SCOPE_EXCLUSIVE : lockScope="exclusive"; break;
                    case LockToken.LOCK_SCOPE_SHARED : lockScope="shared"; break;
                    default : lockScope="none";
                }
                writeSimpleElement(LOCK_SCOPE_PROP, lockScope, serializer);
                
                String lockDepth;
                switch(token.getDepth()){
                    case LockToken.LOCK_DEPTH_INFINIY : lockDepth="Infinity"; break;
                    case LockToken.LOCK_DEPTH_1: lockDepth="1"; break;
                    case LockToken.LOCK_DEPTH_0: lockDepth="0"; break;
                    default : lockDepth="none";
                }
                writeSimpleElement(LOCK_DEPTH_PROP, lockDepth, serializer);
                
                writeSimpleElement(LOCK_OWNER_PROP, token.getOwner(), serializer);
                
                writeSimpleElement(LOCK_TIMEOUT_PROP, ""+token.getTimeOut(), serializer);
                
                
                serializer.startElement(WebDAV.DAV_NS, "locktoken", "D:locktoken", attrs);
                
                serializer.startElement(WebDAV.DAV_NS, "href", "D:href", attrs);
                serializer.characters( "opaquelocktoken:"+token.getOpaqueLockToken() );
                serializer.endElement(WebDAV.DAV_NS, "href", "D:href");
                
                serializer.endElement(WebDAV.DAV_NS, "locktoken", "D:locktoken");
                
                
                serializer.endElement(WebDAV.DAV_NS, "activelock", "D:activelock");
            }
            serializer.endElement(WebDAV.DAV_NS, "lockdiscovery", "D:lockdiscovery");
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
    
    private void writePropertyNames(Collection collection, DocumentImpl resource, SAXSerializer serializer,
            String servletPath)
            throws SAXException {
        AttributesImpl attrs = new AttributesImpl();
        serializer.startElement(WebDAV.DAV_NS, "response", "D:response", attrs);
        // write D:href
        serializer.startElement(WebDAV.DAV_NS, "href", "D:href", attrs);
        ///TODO : use dedicated function in XmldbURI
        String href = servletPath + (resource != null ? collection.getURI().toString() + "/" + resource.getFileURI() :
            collection.getURI().toString());
        serializer.characters(href);
        serializer.endElement(WebDAV.DAV_NS, "href", "D:href");
        
        serializer.startElement(WebDAV.DAV_NS, "propstat", "D:propstat", attrs);
        serializer.startElement(WebDAV.DAV_NS, "prop", "D:prop", attrs);
        QName[] defaults = resource == null ? DEFAULT_COLLECTION_PROPS : DEFAULT_RESOURCE_PROPS;
        for(int i = 0; i < defaults.length; i++) {
            writeEmptyElement(defaults[i], serializer);
        }
        serializer.endElement(WebDAV.DAV_NS, "prop", "D:prop");
        writeSimpleElement(STATUS_PROP, "HTTP/1.1 200 OK", serializer);
        
        serializer.endElement(WebDAV.DAV_NS, "propstat", "D:propstat");
        serializer.endElement(WebDAV.DAV_NS, "response", "D:response");
    }
    
    private boolean shouldIncludeProperty(int type, DAVProperties properties, QName name) {
        if(type == FIND_ALL_PROPERTIES)
            return true;
        return properties.includeProperty(name);
    }
    
    private void writeEmptyElement(QName qname, SAXSerializer serializer) throws SAXException {
        serializer.startElement(WebDAV.DAV_NS, qname.getLocalName(), qname.getStringValue(), new AttributesImpl());
        serializer.endElement(WebDAV.DAV_NS, qname.getLocalName(), qname.getStringValue());
    }
    
    private void writeSimpleElement(QName element, String content, SAXSerializer serializer)
    throws SAXException {
        serializer.startElement(WebDAV.DAV_NS, element.getLocalName(), element.getStringValue(), new AttributesImpl());
        serializer.characters(content);
        serializer.endElement(WebDAV.DAV_NS, element.getLocalName(), element.getStringValue());
    }
    
    private void writeResponse(HttpServletResponse response, String content)
    throws IOException {
        response.setStatus(WebDAV.SC_MULTI_STATUS);
        response.setContentType(MimeType.XML_CONTENT_TYPE.getName());
        byte[] data = content.getBytes("UTF-8");
        response.setContentLength(data.length);
        OutputStream os = response.getOutputStream();
        os.write(data);
        os.flush();
    }
    
    private String getServletPath(HttpServletRequest request) {
        String servletPath = request.getContextPath();
        if(servletPath.endsWith("/")) {
            servletPath = servletPath.substring(0, servletPath.length() - 1);
        }
        servletPath += request.getServletPath();
        if(servletPath.endsWith("/")) {
            servletPath = servletPath.substring(0, servletPath.length() - 1);
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
    
    private void getPropertyNames(Node propNode, DAVProperties properties) {
        NodeList childList = propNode.getChildNodes();
        for(int i = 0; i < childList.getLength(); i++) {
            Node currentNode = childList.item(i);
            if(currentNode.getNodeType() == Node.ELEMENT_NODE) {
                properties.add(currentNode);
            }
        }
    }
    
    public static String xmlToString(Node node) {
        try {
            Source source = new DOMSource(node);
            StringWriter stringWriter = new StringWriter();
            Result result = new StreamResult(stringWriter);
            TransformerFactory factory = TransformerFactory.newInstance();
            Transformer transformer = factory.newTransformer();
            transformer.transform(source, result);
            return stringWriter.getBuffer().toString();
        } catch (TransformerConfigurationException e) {
            e.printStackTrace();
        } catch (TransformerException e) {
            e.printStackTrace();
        }
        return null;
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
