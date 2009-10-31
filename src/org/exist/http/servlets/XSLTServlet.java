/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
 * $Id$
 */
package org.exist.http.servlets;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.DocumentImpl;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.serializers.XIncludeFilter;
import org.exist.util.serializer.*;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.Constants;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Type;
import org.exist.xslt.TransformerFactoryAllocator;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TemplatesHandler;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamSource;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class XSLTServlet extends HttpServlet {

    private final static String REQ_ATTRIBUTE_PREFIX = "xslt.";
    
    private final static String REQ_ATTRIBUTE_STYLESHEET = "xslt.stylesheet";
    private final static String REQ_ATTRIBUTE_INPUT = "xslt.input";
    private final static String REQ_ATTRIBUTE_PROPERTIES = "xslt.output.";
    private final static String REQ_ATTRIBUTE_BASE = "xslt.base";
    
    private final static Logger LOG = Logger.getLogger(XSLTServlet.class);

    private BrokerPool pool;

    private final Map cache = new HashMap();
    private Boolean caching = null;
    
    private boolean isCaching() {
    	if (caching == null) {
    		Object property = pool.getConfiguration().getProperty(TransformerFactoryAllocator.PROPERTY_CACHING_ATTRIBUTE);
			if (property != null)
				caching = (Boolean) property;
			else 
				caching = true;
    	}
    	return caching;
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String stylesheet = (String) request.getAttribute(REQ_ATTRIBUTE_STYLESHEET);
        if (stylesheet == null)
            throw new ServletException("No stylesheet source specified!");
        Item inputNode = null;
        String sourceAttrib = (String) request.getAttribute(REQ_ATTRIBUTE_INPUT);
        if (sourceAttrib != null) {
            Object sourceObj = request.getAttribute(sourceAttrib);
            if (sourceObj != null) {
                if (sourceObj instanceof Item) {
                    inputNode = (Item) sourceObj;
                    if (!Type.subTypeOf(inputNode.getType(), Type.NODE))
                        throw new ServletException("Input for XSLT servlet is not a node. Read from attribute " +
                                sourceAttrib);
                    LOG.debug("Taking XSLT input from request attribute " + sourceAttrib);
                } else
                    throw new ServletException("Input for XSLT servlet is not a node. Read from attribute " +
                            sourceAttrib);
            }
        }

        String userParam = (String) request.getAttribute("xslt.user");
        String passwd = (String) request.getAttribute("xslt.password");
        if (userParam == null) {
            userParam = org.exist.security.SecurityManager.GUEST_USER;
            passwd = userParam;
        }

        try {
            pool = BrokerPool.getInstance();
            User user = pool.getSecurityManager().getUser(userParam);
            if (user != null) {
                if (!user.validate(passwd)) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Wrong password or user");
                }
            }

            SAXTransformerFactory factory = TransformerFactoryAllocator.getTransformerFactory(pool);
            Templates templates = getSource(user, request, response, factory, stylesheet);
            if (templates == null)
                return;
            //do the transformation
            DBBroker broker = null;
            try {
                broker = pool.get(user);
                TransformerHandler handler = factory.newTransformerHandler(templates);
                setParameters(request, handler.getTransformer());
                Properties properties = handler.getTransformer().getOutputProperties();
                setOutputProperties(request, properties);

                String mediaType = properties.getProperty("media-type");
                String encoding = properties.getProperty("encoding");
                if (encoding == null)
                    encoding = "UTF-8";
                response.setCharacterEncoding(encoding);
                if (mediaType != null) {
                    if (encoding == null)
                        response.setContentType(mediaType);
                    
                    //check, do mediaType have "charset"
                    else if (mediaType.indexOf("charset") == -1)
                        response.setContentType(mediaType + "; charset=" + encoding);
                    else 
                        response.setContentType(mediaType);
                }

                SAXSerializer sax = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);
                Writer writer = new BufferedWriter(response.getWriter());
                sax.setOutput(writer, properties);
                SAXResult result = new SAXResult(sax);
                handler.setResult(result);
                
                Serializer serializer = broker.getSerializer();
                serializer.reset();
                Receiver receiver = new ReceiverToSAX(handler);
                try {
                    XIncludeFilter xinclude = new XIncludeFilter(serializer, receiver);
                    receiver = xinclude;
                    String moduleLoadPath;
                    String base = (String) request.getAttribute(REQ_ATTRIBUTE_BASE);
                    if (base != null)
                        moduleLoadPath = getServletContext().getRealPath(base);
                    else
                        moduleLoadPath = getCurrentDir(request).getAbsolutePath();
                    xinclude.setModuleLoadPath(moduleLoadPath);
                    serializer.setReceiver(receiver);
                    if (inputNode != null)
                        serializer.toSAX((NodeValue)inputNode);
                    else {
                        SAXToReceiver saxreceiver = new SAXToReceiver(receiver);
                        XMLReader reader = pool.getParserPool().borrowXMLReader();
                        reader.setContentHandler(saxreceiver);
                        reader.parse(new InputSource(request.getInputStream()));
                    }
                } catch (SAXException e) {
                    throw new ServletException("SAX exception while transforming node: " + e.getMessage(), e);
                } finally {
                    SerializerPool.getInstance().returnObject(sax);
                }
                writer.flush();
                response.flushBuffer();

            } catch (IOException e) {
                throw new ServletException("IO exception while transforming node: " + e.getMessage(), e);

            } catch (TransformerException e) {
                throw new ServletException("Exception while transforming node: " + e.getMessage(), e);
                
            } catch (Throwable e){
                LOG.error(e);
                throw new ServletException("An error occurred: " + e.getMessage(), e);

            } finally {
                pool.release(broker);
            }
        } catch (EXistException e) {
            throw new ServletException(e.getMessage(), e);
        }
    }

    private Templates getSource(User user, HttpServletRequest request, HttpServletResponse response,
                                SAXTransformerFactory factory, String stylesheet)
        throws ServletException, IOException {
        String base;
        if(stylesheet.indexOf(':') == Constants.STRING_NOT_FOUND) {
            File f = new File(stylesheet);
            if (f.canRead())
                stylesheet = f.toURI().toASCIIString();
            else {
                if (f.isAbsolute()) {
                    f = new File(getServletContext().getRealPath(stylesheet));
                    stylesheet = f.toURI().toASCIIString();
                } else {
                    f = new File(getCurrentDir(request), stylesheet);
                    stylesheet = f.toURI().toASCIIString();
                }
                if (!f.canRead()) {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND, "Stylesheet not found");
                    return null;
                }
            }
        }
        int p = stylesheet.lastIndexOf("/");
        if(p != Constants.STRING_NOT_FOUND)
            base = stylesheet.substring(0, p);
        else
            base = stylesheet;
        if (LOG.isDebugEnabled())
            LOG.debug("Loading stylesheet from " + stylesheet);
        CachedStylesheet cached = (CachedStylesheet)cache.get(stylesheet);
        if(cached == null) {
            cached = new CachedStylesheet(factory, user, stylesheet, base);
            cache.put(stylesheet, cached);
        }
        return cached.getTemplates(user);
    }

    private File getCurrentDir(HttpServletRequest request) {
        String path = request.getPathTranslated();
        if (path == null) {
            path = request.getRequestURI().substring(request.getContextPath().length());
            int p = path.lastIndexOf(';');
            if(p != Constants.STRING_NOT_FOUND)
                path = path.substring(0, p);
            path = getServletContext().getRealPath(path);
        }
        File file = new File(path);
        if (file.isDirectory())
            return file;
        else
            return file.getParentFile();
    }

    private void setParameters(HttpServletRequest request, Transformer transformer) throws XPathException {
        for (Enumeration e = request.getAttributeNames(); e.hasMoreElements(); ) {
            String name = (String) e.nextElement();
            if (name.startsWith(REQ_ATTRIBUTE_PREFIX) &&
                !(name.startsWith(REQ_ATTRIBUTE_PROPERTIES) || REQ_ATTRIBUTE_INPUT.equals(name) || REQ_ATTRIBUTE_STYLESHEET.equals(name))) {
                Object value = request.getAttribute(name);
                if (value instanceof NodeValue) {
                    NodeValue nv = (NodeValue) value;
                    if (nv.getImplementationType() == NodeValue.IN_MEMORY_NODE) {
                        value = nv.toMemNodeSet();
                    }
                }
                transformer.setParameter(name, value);
                transformer.setParameter(name.substring(REQ_ATTRIBUTE_PREFIX.length()), value);
            }
        }
    }

    private void setOutputProperties(HttpServletRequest request, Properties properties) {
        for (Enumeration e = request.getAttributeNames(); e.hasMoreElements(); ) {
            String name = (String) e.nextElement();
            if (name.startsWith(REQ_ATTRIBUTE_PROPERTIES)) {
                Object value = request.getAttribute(name);
                if (value != null)
                    properties.setProperty(name.substring(REQ_ATTRIBUTE_PROPERTIES.length()), value.toString());
            }
        }
    }

    private class CachedStylesheet {

        SAXTransformerFactory factory;
        long lastModified = -1;
        Templates templates = null;
        String uri;

        public CachedStylesheet(SAXTransformerFactory factory, User user, String uri, String baseURI) throws ServletException {
            this.factory = factory;
            this.uri = uri;
            if (!baseURI.startsWith("xmldb:exist://"))
                factory.setURIResolver(new ExternalResolver(baseURI));
            getTemplates(user);
        }

        public Templates getTemplates(User user) throws ServletException {
            if (uri.startsWith("xmldb:exist://")) {
                String docPath = uri.substring("xmldb:exist://".length());
                DocumentImpl doc = null;
                DBBroker broker = null;
                try {
                    broker = pool.get(user);
                    doc = broker.getXMLResource(XmldbURI.create(docPath), Lock.READ_LOCK);
                    if (!isCaching() || (doc != null && (templates == null || doc.getMetadata().getLastModified() > lastModified)))
                        templates = getSource(broker, doc);
                    lastModified = doc.getMetadata().getLastModified();
                } catch (PermissionDeniedException e) {
                    throw new ServletException("Permission denied to read stylesheet: " + uri, e);
                } catch (EXistException e) {
                    throw new ServletException("Error while reading stylesheet source from db: " + e.getMessage(), e);
                } finally {
                    pool.release(broker);
                    doc.getUpdateLock().release(Lock.READ_LOCK);
                }
            } else {
                try {
                    URL url = new URL(uri);
                    URLConnection connection = url.openConnection();
                    long modified = connection.getLastModified();
                    if(!isCaching() || (templates == null || modified > lastModified || modified == 0)) {
                        LOG.debug("compiling stylesheet " + url.toString());
                        templates = factory.newTemplates(new StreamSource(connection.getInputStream()));
                    }
                    lastModified = modified;
                } catch (IOException e) {
                    throw new ServletException("Error while reading stylesheet source from uri: " + uri +
                            ": " + e.getMessage(), e);
                } catch (TransformerConfigurationException e) {
                    throw new ServletException("Error while reading stylesheet source from uri: " + uri +
                            ": " + e.getMessage(), e);
                }
            }
            return templates;
        }

        private Templates getSource(DBBroker broker, DocumentImpl stylesheet)
                throws ServletException {
            factory.setURIResolver(new DatabaseResolver(broker, stylesheet));
            try {
                TemplatesHandler handler = factory.newTemplatesHandler();
                handler.startDocument();
                Serializer serializer = broker.getSerializer();
                serializer.reset();
                serializer.setSAXHandlers(handler, null);
                serializer.toSAX(stylesheet);
                handler.endDocument();
                return handler.getTemplates();
            } catch (SAXException e) {
                throw new ServletException("A SAX exception occurred while compiling the stylesheet: " + e.getMessage(), e);
            } catch (TransformerConfigurationException e) {
                throw new ServletException("A configuration exception occurred while compiling the stylesheet: " + e.getMessage(), e);
            }
        }
    }

    private class ExternalResolver implements URIResolver {

        private String baseURI;

        public ExternalResolver(String base) {
            this.baseURI = base;
        }

        /* (non-Javadoc)
           * @see javax.xml.transform.URIResolver#resolve(java.lang.String, java.lang.String)
           */
        public Source resolve(String href, String base)
                throws TransformerException {
            URL url;
            try {
                //TODO : use dedicated function in XmldbURI
                url = new URL(baseURI + "/"  + href);
                URLConnection connection = url.openConnection();
                return new StreamSource(connection.getInputStream());
            } catch (MalformedURLException e) {
                return null;
            } catch (IOException e) {
                return null;
            }
        }
    }

    private class DatabaseResolver implements URIResolver {

        DocumentImpl doc;
        DBBroker broker;

        public DatabaseResolver(DBBroker broker, DocumentImpl myDoc) {
            this.broker = broker;
            this.doc = myDoc;
        }


        /* (non-Javadoc)
           * @see javax.xml.transform.URIResolver#resolve(java.lang.String, java.lang.String)
           */
        public Source resolve(String href, String base)
                throws TransformerException {
            Collection collection = doc.getCollection();
            String path;
            //TODO : use dedicated function in XmldbURI
            if(href.startsWith("/"))
                path = href;
            else
                path = collection.getURI() + "/" + href;
            DocumentImpl xslDoc;
            try {
                xslDoc = (DocumentImpl) broker.getXMLResource(XmldbURI.create(path));
            } catch (PermissionDeniedException e) {
                throw new TransformerException(e.getMessage(), e);
            }
            if(xslDoc == null) {
                LOG.debug("Document " + href + " not found in collection " + collection.getURI());
                return null;
            }
            if(!xslDoc.getPermissions().validate(broker.getUser(), Permission.READ))
                throw new TransformerException("Insufficient privileges to read resource " + path);
            DOMSource source = new DOMSource(xslDoc);
            return source;
        }
    }
}
