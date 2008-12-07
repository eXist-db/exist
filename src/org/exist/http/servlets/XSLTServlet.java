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

import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.XPathException;
import org.exist.xquery.Constants;
import org.exist.xquery.XQueryContext;
import org.exist.xslt.TransformerFactoryAllocator;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.serializers.XIncludeFilter;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Permission;
import org.exist.security.User;
import org.exist.xmldb.XmldbURI;
import org.exist.dom.DocumentImpl;
import org.exist.collections.Collection;
import org.exist.EXistException;
import org.exist.util.serializer.Receiver;
import org.exist.util.serializer.ReceiverToSAX;
import org.exist.util.serializer.SAXToReceiver;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.InputSource;
import org.apache.log4j.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TemplatesHandler;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.Templates;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.Transformer;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.File;
import java.io.OutputStream;
import java.io.BufferedOutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;
import java.util.HashMap;

public class XSLTServlet extends HttpServlet {

    private final static Logger LOG = Logger.getLogger(XSLTServlet.class);

    private BrokerPool pool;

    private final Map cache = new HashMap();

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String stylesheet = (String) request.getAttribute("xslt.stylesheet");
        if (stylesheet == null)
            throw new ServletException("No stylesheet source specified!");

        Item inputNode = null;
        String sourceAttrib = (String) request.getAttribute("xslt.input");
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
            Templates templates = getSource(user, request, factory, stylesheet);
            //do the transformation
            DBBroker broker = null;
            try {
                broker = pool.get(user);
                OutputStream os = response.getOutputStream();
                OutputStream bufferedOutputStream = new BufferedOutputStream(os);
                StreamResult result = new StreamResult(bufferedOutputStream);
                TransformerHandler handler = factory.newTransformerHandler(templates);
                handler.setResult(result);
                String mediaType = handler.getTransformer().getOutputProperty("media-type");
                String encoding = handler.getTransformer().getOutputProperty("encoding");
                if (mediaType != null) {
                    if (encoding == null)
                        response.setContentType(mediaType);
                    else
                        response.setContentType(mediaType + "; charset=" + encoding);
                }
                Serializer serializer = broker.getSerializer();
                serializer.reset();
                Receiver receiver = new ReceiverToSAX(handler);
                try {
                    XIncludeFilter xinclude = new XIncludeFilter(serializer, receiver);
                    receiver = xinclude;
                    String requestPath = request.getRequestURI();
                    int p = requestPath.lastIndexOf("/");
                    if(p != Constants.STRING_NOT_FOUND)
                        requestPath = requestPath.substring(0, p);
                    String moduleLoadPath = getServletContext().getRealPath(requestPath.substring(request.getContextPath().length()));
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
                }
                bufferedOutputStream.close();
                response.flushBuffer();
            } catch (IOException e) {
                throw new ServletException("IO exception while transforming node: " + e.getMessage(), e);
            } catch (TransformerException e) {
                throw new ServletException("Exception while transforming node: " + e.getMessage(), e);
            } finally {
                pool.release(broker);
            }
        } catch (EXistException e) {
            throw new ServletException(e.getMessage(), e);
        }
    }

    private Templates getSource(User user, HttpServletRequest request, SAXTransformerFactory factory, String stylesheet)
            throws ServletException {
        String base;
        if(stylesheet.indexOf(':') == Constants.STRING_NOT_FOUND) {
            File f = new File(stylesheet);
            if (f.canRead())
                stylesheet = f.toURI().toASCIIString();
            else {
                stylesheet = request.getRealPath(stylesheet);
                f = new File(stylesheet);
                stylesheet = f.toURI().toASCIIString();
            }
        }
        int p = stylesheet.lastIndexOf("/");
        if(p != Constants.STRING_NOT_FOUND)
            base = stylesheet.substring(0, p);
        else
            base = stylesheet;
        CachedStylesheet cached = (CachedStylesheet)cache.get(stylesheet);
        if(cached == null) {
            cached = new CachedStylesheet(factory, user, stylesheet, base);
            cache.put(stylesheet, cached);
        }
        return cached.getTemplates(user);
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
                    if (doc != null && (templates == null || doc.getMetadata().getLastModified() > lastModified))
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
                    if(templates == null || modified > lastModified || modified == 0) {
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
