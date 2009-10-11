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
package org.exist.xquery.functions.xmldb;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.log4j.Logger;

import org.exist.dom.QName;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.util.serializer.SAXSerializer;
import org.exist.xmldb.EXistResource;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AnyURIValue;
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
import org.xmldb.api.modules.XMLResource;

/**
 * @author wolf
 */
public class XMLDBStore extends XMLDBAbstractCollectionManipulator {
	
	protected static final Logger logger = Logger.getLogger(XMLDBStore.class);

	protected static final FunctionParameterSequenceType ARG_COLLECTION = new FunctionParameterSequenceType("collection-uri", Type.STRING, Cardinality.EXACTLY_ONE, "The collection URI");
	protected static final FunctionParameterSequenceType ARG_RESOURCE_NAME = new FunctionParameterSequenceType("resource-name", Type.STRING, Cardinality.ZERO_OR_ONE, "The resource name");
	protected static final FunctionParameterSequenceType ARG_CONTENTS = new FunctionParameterSequenceType("contents", Type.ITEM, Cardinality.EXACTLY_ONE, "The contents");
	protected static final FunctionParameterSequenceType ARG_MIME_TYPE = new FunctionParameterSequenceType("mime-type", Type.STRING, Cardinality.EXACTLY_ONE, "The mime type");
	
	protected static final FunctionReturnSequenceType RETURN_TYPE = new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE, "the path to new resource if sucessfully stored, otherwise the emtpty sequence");
	
	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName("store", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
			"Stores a new resource into the database. The resource is stored  " +
			"in the collection $collection-uri with the name $resource-name. " +
            XMLDBModule.COLLECTION_URI + 
            // fixit! - security -  if URI and possibly also file object
            // DBA role should be required/ljo
            // Of course we need to think of the node case too but it has at 
            // least been passed throught fn:doc() but since the retrieval
            // happens firstly who knows ... 
			" The contents $contents, is either a node, an xs:string, a Java file object or an xs:anyURI. " +
			"A node will be serialized to SAX. It becomes the root node of the new " +
			"document. If $contents is of type xs:anyURI, the resource is loaded " +
			"from that URI. " + 
            "Returns the path to the new document if successfully stored, " + 
            "otherwise an XPathException is thrown.",
			new SequenceType[] { ARG_COLLECTION, ARG_RESOURCE_NAME, ARG_CONTENTS },
			RETURN_TYPE),
		new FunctionSignature(
			new QName("store", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
			"Stores a new resource into the database. The resource is stored  " +
			"in the collection $collection-uri with the name $resource-name. " +
            XMLDBModule.COLLECTION_URI + 
            // fixit! - security -  if URI and possibly also file object 
            // DBA role should be required/ljo
            // Of course we need to think of the node case too but it has at 
            // least been passed through fn:doc() but since the retrieval
            // happens firstly who knows ... 

            " The contents $contents, is either a node, an xs:string, a Java file object or an xs:anyURI. " +
			"A node will be serialized to SAX. It becomes the root node of the new " +
			"document. If $contents is of type xs:anyURI, the resource is loaded " +
			"from that URI. The final argument $mime-type is used to specify " +
            "a mime type.  If the mime-type is not a xml based type, the " +
            "resource will be stored as a binary resource." +
            "Returns the path to the new document if successfully stored, " + 
		    "otherwise an XPathException is thrown.",
			new SequenceType[] { ARG_COLLECTION, ARG_RESOURCE_NAME, ARG_CONTENTS, ARG_MIME_TYPE },
			RETURN_TYPE)
	};

	/**
	 * @param context
	 * @param signature
	 */
	public XMLDBStore(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence evalWithCollection(Collection collection, Sequence args[],
		Sequence contextSequence)
		throws XPathException {
		String docName = args[1].isEmpty() ? null : args[1].getStringValue();
		if(docName != null && docName.length() == 0)
			docName = null;
		else if(docName != null)
			docName = new AnyURIValue(docName).toXmldbURI().toString();
		
        String mimeType = MimeType.XML_TYPE.getName();
		boolean binary = false;
		if(getSignature().getArgumentCount() == 4) {
		    mimeType = args[3].getStringValue();
		    MimeType mime = MimeTable.getInstance().getContentType(mimeType);
		    if (mime != null)
			binary = !mime.isXMLType();
            
		} else if (docName != null){
		    MimeType mime = MimeTable.getInstance().getContentTypeFor(docName);
            if (mime != null) {
                mimeType = mime.getName();
                binary = !mime.isXMLType();
            }
        }
		
		Item item =
			args[2].itemAt(0);
		Resource resource;
		try {
			if(Type.subTypeOf(item.getType(), Type.JAVA_OBJECT)) {
				Object obj = ((JavaObjectValue)item).getObject();
				if(!(obj instanceof File)) {
                    logger.error("Passed java object should be a File");
					throw new XPathException(this, "Passed java object should be a File");
                }
				resource = loadFromFile(collection, (File)obj, docName, binary, mimeType);

			} else if(Type.subTypeOf(item.getType(), Type.ANY_URI)) {
				try {
					URI uri = new URI(item.getStringValue());
					resource = loadFromURI(collection, uri, docName, binary, mimeType);

				} catch (URISyntaxException e) {
                    logger.error("Invalid URI: " + item.getStringValue());
					throw new XPathException(this, "Invalid URI: " + item.getStringValue(), e);
				}

			} else {
				if(binary) {
					resource = collection.createResource(docName, "BinaryResource");
                } else
					resource = collection.createResource(docName, "XMLResource");

				if(Type.subTypeOf(item.getType(), Type.STRING)) {
					resource.setContent(item.getStringValue());

				} else if(item.getType() == Type.BASE64_BINARY) {
					resource.setContent(item.toJavaObject(byte[].class));

				} else if(Type.subTypeOf(item.getType(), Type.NODE)) {
					if(binary) {
						StringWriter writer = new StringWriter();
						SAXSerializer serializer = new SAXSerializer();
						serializer.setOutput(writer, null);
						item.toSAX(context.getBroker(), serializer, null);
						resource.setContent(writer.toString());
					} else {
						ContentHandler handler = ((XMLResource)resource).setContentAsSAX();
						handler.startDocument();
						item.toSAX(context.getBroker(), handler, null);
						handler.endDocument();
					}
				} else {
                    logger.error("Data should be either a node or a string");
					throw new XPathException(this, "Data should be either a node or a string");
                }

                ((EXistResource) resource).setMimeType(mimeType);
				collection.storeResource(resource);
			}
            
		} catch (XMLDBException e) {
                        logger.error(e.getMessage(), e);
			throw new XPathException(this,
				"XMLDB reported an exception while storing document" + e, e);

		} catch (SAXException e) {
            logger.error(e.getMessage());
			throw new XPathException(this, "SAX reported an exception while storing document",	e);
		}

		if (resource == null) {
			return Sequence.EMPTY_SEQUENCE;

        } else {
			try {
                //TODO : use dedicated function in XmldbURI
				return new StringValue(collection.getName() + "/" + resource.getId());
			} catch (XMLDBException e) {
                logger.error(e.getMessage());
				throw new XPathException(this, "XMLDB reported an exception while retrieving the " +
						"stored document", e);
			}

        }
	}
	
	private Resource loadFromURI(Collection collection, URI uri, String docName, boolean binary, String mimeType) 
	throws XPathException {
		Resource resource;
		if("file".equals(uri.getScheme())) {
			String path = uri.getPath();
            if(path == null)
                throw new XPathException(this, "Cannot read from URI: " + uri.toASCIIString());
			File file = new File(path);
			if(!file.canRead())
				throw new XPathException(this, "Cannot read path: " + path);
			resource = loadFromFile(collection, file, docName, binary, mimeType);

		} else {
			try {
				File temp = File.createTempFile("existDBS", ".xml");
				// This is deleted later; is this necessary?
				temp.deleteOnExit();
				OutputStream os = new FileOutputStream(temp);
				InputStream is = uri.toURL().openStream();
				byte[] data = new byte[1024];
				int read = 0;
				while((read = is.read(data)) > -1) {
					os.write(data, 0, read);
				}
				is.close();
				os.close();
				resource = loadFromFile(collection, temp, docName, binary, mimeType);
				temp.delete();

			} catch (MalformedURLException e) {
				throw new XPathException(this, "Malformed URL: " + uri.toString(), e);

			} catch (IOException e) {
				throw new XPathException(this, "IOException while reading from URL: " +
						uri.toString(), e);
			}
		}
		return resource;
	}
	
	private Resource loadFromFile(Collection collection, File file, String docName, boolean binary, String mimeType) 
	throws XPathException {
		if(file.isFile()) {
			if(docName == null)
				docName = file.getName();
			try {
				Resource resource;
				if(binary) {
					resource = collection.createResource(docName, "BinaryResource");
                } else
					resource = collection.createResource(docName, "XMLResource");
                ((EXistResource)resource).setMimeType(mimeType);
				resource.setContent(file);
				collection.storeResource(resource);
				return resource;

			} catch (XMLDBException e) {
				throw new XPathException(this, "Could not store file " + file.getAbsolutePath() +
						": " + e.getMessage(), e);
			}
            
		} else
			throw new XPathException(this, file.getAbsolutePath() + " does not point to a file");
	}
}
