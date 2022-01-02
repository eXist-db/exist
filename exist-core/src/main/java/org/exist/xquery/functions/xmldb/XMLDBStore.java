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
package org.exist.xquery.functions.xmldb;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.dom.QName;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.util.FileUtils;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.util.io.TemporaryFileManager;
import org.exist.util.serializer.SAXSerializer;
import org.exist.xmldb.EXistResource;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.BinaryValue;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.BinaryResource;
import org.xmldb.api.modules.XMLResource;

/**
 * @author wolf
 */
public class XMLDBStore extends XMLDBAbstractCollectionManipulator {

    private static final Logger LOGGER = LogManager.getLogger(XMLDBStore.class);

    protected static final FunctionParameterSequenceType ARG_COLLECTION = new FunctionParameterSequenceType("collection-uri", Type.STRING, Cardinality.EXACTLY_ONE, "The collection URI");
    protected static final FunctionParameterSequenceType ARG_RESOURCE_NAME = new FunctionParameterSequenceType("resource-name", Type.STRING, Cardinality.ZERO_OR_ONE, "The resource name");
    protected static final FunctionParameterSequenceType ARG_CONTENTS = new FunctionParameterSequenceType("contents", Type.ITEM, Cardinality.EXACTLY_ONE, "The contents");
    protected static final FunctionParameterSequenceType ARG_MIME_TYPE = new FunctionParameterSequenceType("mime-type", Type.STRING, Cardinality.EXACTLY_ONE, "The mime type");

    protected static final FunctionReturnSequenceType RETURN_TYPE = new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE, "the path to new resource if sucessfully stored, otherwise the emtpty sequence");

    protected static final Properties SERIALIZATION_PROPERTIES = new Properties();

    static {
        SERIALIZATION_PROPERTIES.setProperty(EXistOutputKeys.EXPAND_XINCLUDES, "no");
    }

    public final static FunctionSignature[] signatures = {
            new FunctionSignature(
                    new QName("store", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
                    "Stores a new resource into the database. The resource is stored  "
                            + "in the collection $collection-uri with the name $resource-name. "
                            + XMLDBModule.COLLECTION_URI
                            // fixit! - security -  if URI and possibly also file object
                            // DBA role should be required/ljo
                            // Of course we need to think of the node case too but it has at
                            // least been passed throught fn:doc() but since the retrieval
                            // happens firstly who knows ...
                            + " The contents $contents, is either a node, an xs:string, a Java file object or an xs:anyURI. "
                            + "A node will be serialized to SAX. It becomes the root node of the new "
                            + "document. If $contents is of type xs:anyURI, the resource is loaded "
                            + "from that URI. "
                            + "Returns the path to the new document if successfully stored, "
                            + "otherwise an XPathException is thrown.",
                    new SequenceType[]{ARG_COLLECTION, ARG_RESOURCE_NAME, ARG_CONTENTS},
                    RETURN_TYPE),
            new FunctionSignature(
                    new QName("store", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
                    "Stores a new resource into the database. The resource is stored  "
                            + "in the collection $collection-uri with the name $resource-name. "
                            + XMLDBModule.COLLECTION_URI
                            // fixit! - security -  if URI and possibly also file object
                            // DBA role should be required/ljo
                            // Of course we need to think of the node case too but it has at
                            // least been passed through fn:doc() but since the retrieval
                            // happens firstly who knows ...
                            + " The contents $contents, is either a node, an xs:string, a Java file object or an xs:anyURI. "
                            + "A node will be serialized to SAX. It becomes the root node of the new "
                            + "document. If $contents is of type xs:anyURI, the resource is loaded "
                            + "from that URI. The final argument $mime-type is used to specify "
                            + "a mime type.  If the mime-type is not a xml based type, the "
                            + "resource will be stored as a binary resource."
                            + "Returns the path to the new document if successfully stored, "
                            + "otherwise an XPathException is thrown.",
                    new SequenceType[]{ARG_COLLECTION, ARG_RESOURCE_NAME, ARG_CONTENTS, ARG_MIME_TYPE},
                    RETURN_TYPE),
            new FunctionSignature(
                    new QName("store-as-binary", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
                    "Stores a new resource into the database. The resource is stored  "
                            + "in the collection $collection-uri with the name $resource-name. "
                            + XMLDBModule.COLLECTION_URI
                            // fixit! - security -  if URI and possibly also file object
                            // DBA role should be required/ljo
                            // Of course we need to think of the node case too but it has at
                            // least been passed throught fn:doc() but since the retrieval
                            // happens firstly who knows ...
                            + " The contents $contents, is either a node, an xs:string, a Java file object or an xs:anyURI. "
                            + "A node will be serialized to SAX. It becomes the root node of the new "
                            + "document. If $contents is of type xs:anyURI, the resource is loaded "
                            + "from that URI. "
                            + "Returns the path to the new document if successfully stored, "
                            + "otherwise an XPathException is thrown.",
                    new SequenceType[]{ARG_COLLECTION, ARG_RESOURCE_NAME, ARG_CONTENTS},
                    RETURN_TYPE)
    };

    public XMLDBStore(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence evalWithCollection(Collection collection, Sequence[] args, Sequence contextSequence) throws XPathException {

        String docName = args[1].isEmpty() ? null : args[1].getStringValue();
        if (docName != null && docName.isEmpty()) {
            docName = null;
        } else if (docName != null) {
            docName = new AnyURIValue(docName).toXmldbURI().toString();
        }

        final Item item = args[2].itemAt(0);

        // determine the mime type
        final boolean storeAsBinary = isCalledAs(FS_STORE_BINARY_NAME);
        MimeType mimeType = null;
        if (getSignature().getArgumentCount() == 4) {
            final String strMimeType = args[3].getStringValue();
            mimeType = MimeTable.getInstance().getContentType(strMimeType);
        }

        if (mimeType == null && docName != null) {
            mimeType = MimeTable.getInstance().getContentTypeFor(docName);
        }

        if (mimeType == null) {
            mimeType = (storeAsBinary || !Type.subTypeOf(item.getType(), Type.NODE)) ? MimeType.BINARY_TYPE : MimeType.XML_TYPE;
        } else if (storeAsBinary) {
            mimeType = new MimeType(mimeType.getName(), MimeType.BINARY);
        }

        Resource resource;
        try {
            if (Type.subTypeOf(item.getType(), Type.JAVA_OBJECT)) {
                final Object obj = ((JavaObjectValue) item).getObject();
                if(obj instanceof java.io.File) {
                    resource = loadFromFile(collection, ((java.io.File)obj).toPath(), docName, mimeType);
                } else if(obj instanceof java.nio.file.Path) {
                    resource = loadFromFile(collection, (Path)obj, docName, mimeType);
                } else {
                    LOGGER.error("Passed java object should be either a java.nio.file.Path or java.io.File");
                    throw new XPathException(this, "Passed java object should be either a java.nio.file.Path or java.io.File");
                }

            } else if (Type.subTypeOf(item.getType(), Type.ANY_URI)) {
                try {
                    final URI uri = new URI(item.getStringValue());
                    resource = loadFromURI(collection, uri, docName, mimeType);

                } catch (final URISyntaxException e) {
                    LOGGER.error("Invalid URI: {}", item.getStringValue());
                    throw new XPathException(this, "Invalid URI: " + item.getStringValue(), e);
                }

            } else {
                if (mimeType.isXMLType()) {
                    resource = collection.createResource(docName, "XMLResource");
                } else {
                    resource = collection.createResource(docName, "BinaryResource");
                }

                if (Type.subTypeOf(item.getType(), Type.STRING)) {
                    resource.setContent(item.getStringValue());

                } else if (item.getType() == Type.BASE64_BINARY) {
                    resource.setContent(((BinaryValue) item).toJavaObject());

                } else if (Type.subTypeOf(item.getType(), Type.NODE)) {
                    if (mimeType.isXMLType()) {
                        final ContentHandler handler = ((XMLResource) resource).setContentAsSAX();
                        handler.startDocument();
                        item.toSAX(context.getBroker(), handler, SERIALIZATION_PROPERTIES);
                        handler.endDocument();
                    } else {
                        try (final StringWriter writer = new StringWriter()) {
                            final SAXSerializer serializer = new SAXSerializer();
                            serializer.setOutput(writer, null);
                            item.toSAX(context.getBroker(), serializer, SERIALIZATION_PROPERTIES);
                            resource.setContent(writer.toString());
                        } catch (final IOException e) {
                            LOGGER.error(e.getMessage(), e);
                        }
                    }
                } else {
                    LOGGER.error("Data should be either a node or a string");
                    throw new XPathException(this, "Data should be either a node or a string");
                }

                ((EXistResource) resource).setMimeType(mimeType.getName());
                collection.storeResource(resource);
            }

        } catch (final XMLDBException e) {
            LOGGER.error(e.getMessage(), e);
            throw new XPathException(this,
                    "XMLDB reported an exception while storing document: " + e.getMessage(), e);

        } catch (final SAXException e) {
            LOGGER.error(e.getMessage());
            throw new XPathException(this, "SAX reported an exception while storing document", e);
        }

        if (resource == null) {
            return Sequence.EMPTY_SEQUENCE;

        } else {
            try {
                //TODO : use dedicated function in XmldbURI
                return new StringValue(collection.getName() + "/" + resource.getId());
            } catch (final XMLDBException e) {
                LOGGER.error(e.getMessage());
                throw new XPathException(this, "XMLDB reported an exception while retrieving the "
                        + "stored document", e);
            }

        }
    }

    private Resource loadFromURI(final Collection collection, final URI uri, final String docName, final MimeType mimeType)
            throws XPathException {
        Resource resource;
        if ("file".equals(uri.getScheme())) {
            final String path = uri.getPath();
            if (path == null) {
                throw new XPathException(this, "Cannot read from URI: " + uri.toASCIIString());
            }
            final Path file = Paths.get(path);
            if (!Files.isReadable(file)) {
                throw new XPathException(this, "Cannot read path: " + path);
            }
            resource = loadFromFile(collection, file, docName, mimeType);

        } else {
            final TemporaryFileManager temporaryFileManager = TemporaryFileManager.getInstance();
            Path temp = null;
            try {
                temp = temporaryFileManager.getTemporaryFile();
                try(final InputStream is = uri.toURL().openStream()) {
                    Files.copy(is, temp);
                    resource = loadFromFile(collection, temp, docName, mimeType);
                } finally {
                    if(temp != null) {
                        temporaryFileManager.returnTemporaryFile(temp);
                    }
                }
            } catch (final MalformedURLException e) {
                throw new XPathException(this, "Malformed URL: " + uri.toString(), e);

            } catch (final IOException e) {
                throw new XPathException(this, "IOException while reading from URL: "
                        + uri.toString(), e);
            }
        }
        return resource;
    }

    private Resource loadFromFile(final Collection collection, final Path file, String docName, final MimeType mimeType)
            throws XPathException {
        if (!Files.isDirectory(file)) {
            if (docName == null) {
                docName = FileUtils.fileName(file);
            }

            try {
                final Resource resource;
                if (mimeType.isXMLType()) {
                    resource = collection.createResource(docName, XMLResource.RESOURCE_TYPE);
                } else {
                    resource = collection.createResource(docName, BinaryResource.RESOURCE_TYPE);
                }
                ((EXistResource) resource).setMimeType(mimeType.getName());
                resource.setContent(file);
                collection.storeResource(resource);
                return resource;

            } catch (final XMLDBException e) {
                throw new XPathException(this, "Could not store file " + file.toAbsolutePath()
                        + ": " + e.getMessage(), e);
            }

        } else {
            throw new XPathException(this, file.toAbsolutePath() + " does not point to a file");
        }
    }
}
