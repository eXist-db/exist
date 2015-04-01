/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2015 The eXist Project
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
 */
package org.exist.xmldb;

import org.apache.xmlrpc.XmlRpcException;

import org.exist.Namespaces;
import org.exist.dom.persistent.DocumentTypeImpl;
import org.exist.util.MimeType;
import org.exist.util.serializer.DOMSerializer;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.VirtualTempFile;
import org.exist.xquery.value.StringValue;

import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Node;

import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.LexicalHandler;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.TransformerException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.Optional;

public class RemoteXMLResource
        extends AbstractRemoteResource
        implements XMLResource {

    /**
     * Use external XMLReader to parse XML.
     */
    private XMLReader xmlReader = null;

    private final Optional<String> id;
    private final int handle;
    private int pos = -1;
    private String content = null;

    private Properties outputProperties = null;
    private LexicalHandler lexicalHandler = null;

    public RemoteXMLResource(final RemoteCollection parent, final XmldbURI docId, final Optional<String> id)
            throws XMLDBException {
        this(parent, -1, -1, docId, id);
    }

    public RemoteXMLResource(
            final RemoteCollection parent,
            final int handle,
            final int pos,
            final XmldbURI docId,
            final Optional<String> id)
            throws XMLDBException {
        super(parent, docId, MimeType.XML_TYPE.getName());
        this.handle = handle;
        this.pos = pos;
        this.id = id;
    }

    @Override
    public String getId() throws XMLDBException {
        return id.map(x -> x.equals("1") ? getDocumentId() : getDocumentId() + '_' + id).orElse(getDocumentId());
    }

    @Override
    public String getResourceType() throws XMLDBException {
        return XMLResource.RESOURCE_TYPE;
    }

    @Override
    protected Properties getProperties() {
        return outputProperties == null ? super.getProperties() : outputProperties;
    }

    protected void setProperties(final Properties properties) {
        this.outputProperties = properties;
    }

    @Override
    public String getDocumentId() {
        return path.lastSegment().toString();
    }

    @Override
    public Object getContent() throws XMLDBException {
        if (content != null) {
            return new StringValue(content).getStringValue(true);
        }
        final Object res = super.getContent();
        if (res != null) {
            if (res instanceof byte[]) {
                return new String((byte[]) res, UTF_8);

            } else {
                return res;
            }
        }
        return null;
    }

    @Override
    public Node getContentAsDOM() throws XMLDBException {
        final InputSource is;
        InputStream cis = null;

        try {
            if (content != null) {
                is = new InputSource(new StringReader(content));
            } else {
                cis = getStreamContent();
                is = new InputSource(cis);
            }

            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setValidating(false);
            final DocumentBuilder builder = factory.newDocumentBuilder();
            final Document doc = builder.parse(is);
            // <frederic.glorieux@ajlsm.com> return a full DOM doc, with root PI and comments
            return doc;
        } catch (final SAXException | IOException | ParserConfigurationException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } finally {
            if (cis != null) {
                try {
                    cis.close();
                } catch (final IOException ioe) {
                    // IgnoreIT(R)
                }
            }
        }
    }

    @Override
    public void getContentAsSAX(final ContentHandler handler) throws XMLDBException {
        final InputSource is;
        InputStream cis = null;

        try {
            if (content != null) {
                is = new InputSource(new StringReader(content));
            } else {
                cis = getStreamContent();
                is = new InputSource(cis);
            }

            XMLReader reader = xmlReader;
            if (reader == null) {
                final SAXParserFactory saxFactory = SAXParserFactory.newInstance();
                saxFactory.setNamespaceAware(true);
                saxFactory.setValidating(false);
                final SAXParser sax = saxFactory.newSAXParser();
                reader = sax.getXMLReader();
            }

            reader.setContentHandler(handler);
            if (lexicalHandler != null) {
                reader.setProperty(Namespaces.SAX_LEXICAL_HANDLER, lexicalHandler);
            }
            reader.parse(is);
        } catch (final ParserConfigurationException | SAXException | IOException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } finally {
            if (cis != null) {
                try {
                    cis.close();
                } catch (final IOException ioe) {
                    // IgnoreIT(R)
                }
            }
        }
    }

    @Override
    public Object getExtendedContent() throws XMLDBException {
        return getExtendedContentInternal(content, idIsPresent(), handle, pos);
    }

    @Override
    public void setContent(final Object value) throws XMLDBException {
        content = null;
        if (!super.setContentInternal(value)) {
            if (value instanceof String) {
                content = (String) value;
            } else if (value instanceof byte[]) {
                content = new String((byte[]) value, UTF_8);

            } else {
                content = value.toString();
            }
        }
    }

    @Override
    public void setContentAsDOM(final Node root) throws XMLDBException {
        try {
            final VirtualTempFile vtmpfile = new VirtualTempFile();
            vtmpfile.setTempPrefix("eXistRXR");
            vtmpfile.setTempPostfix(".xml");

            try (final OutputStreamWriter osw = new OutputStreamWriter(vtmpfile, "UTF-8")) {
                final DOMSerializer xmlout = new DOMSerializer(osw, getProperties());

                final short type = root.getNodeType();
                if (type == Node.ELEMENT_NODE || type == Node.DOCUMENT_FRAGMENT_NODE || type == Node.DOCUMENT_NODE) {
                    xmlout.serialize(root);
                } else {
                    throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "invalid node type");
                }
            } finally {
                try {
                    vtmpfile.close();
                } catch (final IOException ioe) {
                    // IgnoreIT(R)
                }
            }
            setContent(vtmpfile);
        } catch (final TransformerException | IOException ioe) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, ioe.getMessage(), ioe);
        }
    }

    @Override
    public ContentHandler setContentAsSAX() throws XMLDBException {
        freeResources();
        content = null;
        return new InternalXMLSerializer();
    }

    public boolean idIsPresent() {
        return id.isPresent();
    }
    
    public String getNodeId() {
        return id.orElse("1");
    }

    /**
     * Sets the external XMLReader to use.
     *
     * @param xmlReader the XMLReader
     */
    public void setXMLReader(final XMLReader xmlReader) {
        this.xmlReader = xmlReader;
    }

    private class InternalXMLSerializer extends SAXSerializer {
        VirtualTempFile vtmpfile = null;
        OutputStreamWriter writer = null;

        public InternalXMLSerializer() {
            super();
        }

        @Override
        public void startDocument() throws SAXException {
            try {
                vtmpfile = new VirtualTempFile();
                vtmpfile.setTempPrefix("eXistRXR");
                vtmpfile.setTempPostfix(".xml");

                writer = new OutputStreamWriter(vtmpfile, "UTF-8");
                setOutput(writer, new Properties());

            } catch (final IOException ioe) {
                throw new SAXException("Unable to create temp file for serialization data", ioe);
            }

            super.startDocument();
        }

        @Override
        public void endDocument() throws SAXException {
            super.endDocument();

            try {
                if (writer != null) {
                    writer.close();
                }

                if (vtmpfile != null) {
                    vtmpfile.close();
                }

                setContent(vtmpfile);
            } catch (final IOException | XMLDBException e) {
                throw new SAXException("Unable to set file content containing serialized data", e);
            }
        }
    }

    @Override
    public boolean getSAXFeature(final String name)
            throws SAXNotRecognizedException, SAXNotSupportedException {
        return false;
    }

    @Override
    public void setSAXFeature(final String name, final boolean value)
            throws SAXNotRecognizedException, SAXNotSupportedException {
    }

    @Override
    public void setLexicalHandler(final LexicalHandler handler) {
        this.lexicalHandler = handler;
    }

    @Override
    public DocumentType getDocType() throws XMLDBException {
        final List params = new ArrayList(1);
        params.add(path.toString());

        try {
            final Object[] request = (Object[]) collection.getClient().execute("getDocType", params);
            final DocumentType result;
            if (!"".equals(request[0])) {
                result = new DocumentTypeImpl((String) request[0], (String) request[1], (String) request[2]);
            } else {
                result = null;
            }
            return result;
        } catch (final XmlRpcException e) {
            throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(), e);
        }
    }

    @Override
    public void setDocType(final DocumentType doctype) throws XMLDBException {
        if (doctype != null) {
            final List params = new ArrayList(4);
            params.add(path.toString());
            params.add(doctype.getName());
            params.add(doctype.getPublicId() == null ? "" : doctype.getPublicId());
            params.add(doctype.getSystemId() == null ? "" : doctype.getSystemId());

            try {
                collection.getClient().execute("setDocType", params);
            } catch (final XmlRpcException e) {
                throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(), e);
            }
        }
    }

    @Override
    public void getContentIntoAStream(final OutputStream os)
            throws XMLDBException {
        getContentIntoAStreamInternal(os, content, idIsPresent(), handle, pos);
    }

    @Override
    public InputStream getStreamContent() throws XMLDBException {
        return getStreamContentInternal(content, idIsPresent(), handle, pos);
    }

    @Override
    public long getStreamLength()
            throws XMLDBException {
        return getStreamLengthInternal(content);
    }
}
