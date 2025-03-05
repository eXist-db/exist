/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.http.servlets;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.security.AuthenticationException;
import org.exist.security.Subject;
import org.exist.security.internal.web.HttpAccount;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.serializers.XIncludeFilter;
import org.exist.util.serializer.*;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.Constants;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;
import org.exist.xslt.Stylesheet;
import org.exist.xslt.TemplatesFactory;
import org.exist.xslt.TransformerFactoryAllocator;
import org.exist.xslt.XSLTErrorsListener;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.TransformerHandler;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.Optional;
import java.util.Properties;
import java.util.zip.GZIPInputStream;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * eXist-db servlet for XSLT transformations.
 *
 * @author Wolfgang
 */
public class XSLTServlet extends HttpServlet {

    private static final long serialVersionUID = -7258405385386062151L;

    private final static String REQ_ATTRIBUTE_PREFIX = "xslt.";

    private final static String REQ_ATTRIBUTE_STYLESHEET = "xslt.stylesheet";
    private final static String REQ_ATTRIBUTE_INPUT = "xslt.input";
    private final static String REQ_ATTRIBUTE_OUTPUT = "xslt.output.";
    private final static String REQ_ATTRIBUTE_BASE = "xslt.base";

    private final static Logger LOG = LogManager.getLogger(XSLTServlet.class);

    private final static XSLTErrorsListener<ServletException> errorListener =
            new XSLTErrorsListener<>(true, false) {

                @Override
                protected void raiseError(final String error, final TransformerException ex) throws ServletException {
                    throw new ServletException(error, ex);
                }
            };

    private BrokerPool pool;

    private Boolean caching = null;

    /**
     * @return Value of TransformerFactoryAllocator.PROPERTY_CACHING_ATTRIBUTE or TRUE if not present.
     */
    private boolean isCaching() {
        if (caching == null) {
            final Object property = pool.getConfiguration().getProperty(TransformerFactoryAllocator.PROPERTY_CACHING_ATTRIBUTE);
            if (property != null) {
                caching = (Boolean) property;
            } else {
                caching = true;
            }
        }
        return caching;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        final String uri = (String) request.getAttribute(REQ_ATTRIBUTE_STYLESHEET);
        if (uri == null) {
            throw new ServletException("No stylesheet source specified!");
        }

        Item inputNode = null;

        final String sourceAttrib = (String) request.getAttribute(REQ_ATTRIBUTE_INPUT);
        if (sourceAttrib != null) {

            Object sourceObj = request.getAttribute(sourceAttrib);
            if (sourceObj != null) {
                if (sourceObj instanceof ValueSequence seq) {

                    if (seq.size() == 1) {
                        sourceObj = seq.itemAt(0);
                    }
                }

                if (sourceObj instanceof Item) {
                    inputNode = (Item) sourceObj;
                    if (!Type.subTypeOf(inputNode.getType(), Type.NODE)) {
                        throw new ServletException("Input for XSLT servlet is not a node. Read from attribute " +
                                sourceAttrib);
                    }

                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Taking XSLT input from request attribute {}", sourceAttrib);
                    }

                } else {
                    throw new ServletException("Input for XSLT servlet is not a node. Read from attribute " +
                            sourceAttrib);
                }
            }
        }

        try {
            pool = BrokerPool.getInstance();
        } catch (final EXistException e) {
            throw new ServletException(e.getMessage(), e);
        }

        Subject user = pool.getSecurityManager().getGuestSubject();

        Subject requestUser = HttpAccount.getUserFromServletRequest(request);
        if (requestUser != null) {
            user = requestUser;
        }

        // Retrieve username / password from HTTP request attributes
        final String userParam = (String) request.getAttribute("xslt.user");
        final String passwd = (String) request.getAttribute("xslt.password");

        if (userParam != null) {
            try {
                user = pool.getSecurityManager().authenticate(userParam, passwd);
            } catch (final AuthenticationException e1) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Wrong password or user");
                return;
            }
        }

        final Stylesheet stylesheet = stylesheet(uri, request, response);
        if (stylesheet == null) {
            return;
        }

        //do the transformation
        try (final DBBroker broker = pool.get(Optional.of(user))) {

            final TransformerHandler handler = stylesheet.newTransformerHandler(broker, errorListener);
            setTransformerParameters(request, handler.getTransformer());

            final Properties properties = handler.getTransformer().getOutputProperties();
            setOutputProperties(request, properties);

            String encoding = properties.getProperty("encoding");
            if (encoding == null) {
                encoding = UTF_8.name();
            }
            response.setCharacterEncoding(encoding);

            final String mediaType = properties.getProperty("media-type");
            if (mediaType != null) {
                //check, do mediaType have "charset"
                if (!mediaType.contains("charset")) {
                    response.setContentType(mediaType + "; charset=" + encoding);
                } else {
                    response.setContentType(mediaType);
                }
            }

            final SAXSerializer sax = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);
            final Writer writer = new BufferedWriter(response.getWriter());
            sax.setOutput(writer, properties);

            final SAXResult result = new SAXResult(sax);
            handler.setResult(result);

            final Serializer serializer = broker.borrowSerializer();
            Receiver receiver = new ReceiverToSAX(handler);
            try {
                XIncludeFilter xinclude = new XIncludeFilter(serializer, receiver);
                receiver = xinclude;

                String baseUri;
                final String base = (String) request.getAttribute(REQ_ATTRIBUTE_BASE);
                if (base != null) {
                    baseUri = getServletContext().getRealPath(base);
                } else if (uri.startsWith("xmldb:exist://")) {
                    baseUri = XmldbURI.xmldbUriFor(uri).getCollectionPath();
                } else {
                    baseUri = getCurrentDir(request).toAbsolutePath().toString();
                }
                xinclude.setModuleLoadPath(baseUri);

                serializer.setReceiver(receiver);
                if (inputNode != null) {
                    serializer.toSAX((NodeValue) inputNode);

                } else {
                    final SAXToReceiver saxreceiver = new SAXToReceiver(receiver);
                    final XMLReader reader = pool.getParserPool().borrowXMLReader();
                    try {
                        reader.setContentHandler(saxreceiver);

                        //Handle gziped input stream
                        InputStream stream;

                        InputStream inStream = new BufferedInputStream(request.getInputStream());
                        inStream.mark(10);
                        try {
                            stream = new GZIPInputStream(inStream);
                        } catch (final IOException e) {
                            inStream.reset();
                            stream = inStream;
                        }

                        reader.parse(new InputSource(stream));
                    } finally {
                        pool.getParserPool().returnXMLReader(reader);
                    }
                }

            } catch (final SAXParseException e) {
                LOG.error(e.getMessage());
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());

            } catch (final SAXException e) {
                throw new ServletException("SAX exception while transforming node: " + e.getMessage(), e);

            } finally {
                SerializerPool.getInstance().returnObject(sax);
                broker.returnSerializer(serializer);
            }

            writer.flush();
            response.flushBuffer();

        } catch (final IOException e) {
            throw new ServletException("IO exception while transforming node: " + e.getMessage(), e);

        } catch (final TransformerException e) {
            throw new ServletException("Exception while transforming node: " + e.getMessage(), e);

        } catch (final Throwable e) {
            LOG.error(e);
            throw new ServletException("An error occurred: " + e.getMessage(), e);

        }
    }

    /*
     * Please add comments to this method. make assumption clear. These might not be valid.
     */
    private Stylesheet stylesheet(String stylesheet, HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        // Check if stylesheet contains an URI. If not, try to resolve from file system
        if (stylesheet.indexOf(':') == Constants.STRING_NOT_FOUND) {
            // replace double slash
            stylesheet = stylesheet.replaceAll("//", "/");
            Path f = Paths.get(stylesheet).normalize();
            if (Files.isReadable(f)) {
                // Found file, get URI
                stylesheet = f.toUri().toASCIIString();

            } else {
                // if the stylesheet path is absolute, it must be resolved relative to the webapp root
                // f.isAbsolute is problematic on windows.
                if (stylesheet.startsWith("/")) {

                    final String url = getServletContext().getRealPath(stylesheet);
                    if (url == null) {
                        response.sendError(HttpServletResponse.SC_NOT_FOUND,
                                "Stylesheet not found (URL: " + stylesheet + ")");
                        return null;
                    }

                    f = Paths.get(url);
                    stylesheet = f.toUri().toASCIIString();

                } else {
                    // relative path is relative to the current working directory
                    f = getCurrentDir(request).resolve(stylesheet);
                    stylesheet = f.toUri().toASCIIString();
                }

                if (!Files.isReadable(f)) {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND,
                            "Stylesheet not found (URL: " + stylesheet + ")");
                    return null;
                }
            }
        }

        return TemplatesFactory.stylesheet(stylesheet, "", isCaching());
    }

    /*
     * Please explain what this method is about. Write about assumptions / input.
     */
    private Path getCurrentDir(HttpServletRequest request) {
        String path = request.getPathTranslated();
        if (path == null) {
            path = request.getRequestURI().substring(request.getContextPath().length());
            final int p = path.lastIndexOf('/');
            if (p != Constants.STRING_NOT_FOUND) {
                path = path.substring(0, p);
            }
            path = getServletContext().getRealPath(path);
        }

        final Path file = Paths.get(path).normalize();
        if (Files.isDirectory(file)) {
            return file;
        } else {
            return file.getParent();
        }
    }

    /**
     * Copy "xslt." attributes from HTTP request to transformer. Does not copy 'input', 'output'
     * and 'styleheet' attributes.
     */
    private void setTransformerParameters(HttpServletRequest request, Transformer transformer) throws XPathException {

        for (final Enumeration<String> e = request.getAttributeNames(); e.hasMoreElements(); ) {

            final String name = e.nextElement();
            if (name.startsWith(REQ_ATTRIBUTE_PREFIX) &&
                    !(name.startsWith(REQ_ATTRIBUTE_OUTPUT) || REQ_ATTRIBUTE_INPUT.equals(name)
                            || REQ_ATTRIBUTE_STYLESHEET.equals(name))) {
                Object value = request.getAttribute(name);
                if (value instanceof NodeValue nv) {
                    if (nv.getImplementationType() == NodeValue.IN_MEMORY_NODE) {
                        value = nv.toMemNodeSet();
                    }
                }
                transformer.setParameter(name, value);
                transformer.setParameter(name.substring(REQ_ATTRIBUTE_PREFIX.length()), value);
            }
        }
    }

    /**
     * Copies 'output' attributes to properties object.
     */
    private void setOutputProperties(HttpServletRequest request, Properties properties) {
        for (final Enumeration<String> e = request.getAttributeNames(); e.hasMoreElements(); ) {
            final String name = e.nextElement();
            if (name.startsWith(REQ_ATTRIBUTE_OUTPUT)) {
                final Object value = request.getAttribute(name);
                if (value != null) {
                    properties.setProperty(name.substring(REQ_ATTRIBUTE_OUTPUT.length()), value.toString());
                }
            }
        }
    }
}
