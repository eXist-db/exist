/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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

import org.exist.dom.QName;
import org.exist.util.serializer.SAXSerializer;
import org.exist.xmldb.EXistResource;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
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

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName("store", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
			"Store a new resource into the database. The first " +
			"argument denotes the collection where the resource should be stored. " +
			"The collection can be either specified as a simple collection path, " +
			"an XMLDB URI, or a collection object as returned by the collection or " +
			"create-collection functions. The second argument is the name of the new " +
			"resource. The third argument is either a node, an xs:string, a Java file object or an xs:anyURI. " +
			"A node will be serialized to SAX. It becomes the root node of the new " +
			"document. If the argument is of type xs:anyURI, the resource is loaded " +
			"from that URI.",
			new SequenceType[] {
				new SequenceType(Type.ITEM, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE),
				new SequenceType(Type.ITEM, Cardinality.EXACTLY_ONE)},
			new SequenceType(Type.ITEM, Cardinality.EMPTY)),
		new FunctionSignature(
			new QName("store", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
			"Store a new resource into the database. The first " +
			"argument denotes the collection where the resource should be stored. " +
			"The collection can be either specified as a simple collection path, " +
			"an XMLDB URI, or a collection object as returned by the collection or " +
			"create-collection functions. The second argument is the name of the new " +
			"resource. The third argument is either a node, an xs:string, a Java file object or an xs:anyURI. " +
			"A node will be serialized to SAX. It becomes the root node of the new " +
			"document. If the argument is of type xs:anyURI, the resource is loaded " +
			"from that URI. The final argument $d is used to specify a mime-type.  If the mime-type " +
			"is something other than 'text/xml' or 'application/xml', the resource will be stored as " +
			"a binary resource.",
			new SequenceType[] {
				new SequenceType(Type.ITEM, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE),
				new SequenceType(Type.ITEM, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.ITEM, Cardinality.EMPTY))
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
		String docName = args[1].getLength() == 0 ? null : args[1].getStringValue();
		if(docName != null && docName.length() == 0)
			docName = null;
		
        String mimeType = "text/xml";
		boolean binary = false;
		if(getSignature().getArgumentCount() == 4) {
			mimeType = args[3].getStringValue();
			binary = !("text/xml".equals(mimeType) || "application/xml".equals(mimeType));
		}
		
		Item item =
			args[2].itemAt(0);
		try {
			if(Type.subTypeOf(item.getType(), Type.JAVA_OBJECT)) {
				Object obj = ((JavaObjectValue)item).getObject();
				if(!(obj instanceof File))
					throw new XPathException(getASTNode(), "Passed java object should be a File");
				loadFromFile(collection, (File)obj, docName, binary);
			}else if(Type.subTypeOf(item.getType(), Type.ANY_URI)) {
				try {
					URI uri = new URI(item.getStringValue());
					loadFromURI(collection, uri, docName, binary);
				} catch (URISyntaxException e) {
					throw new XPathException(getASTNode(), "Invalid URI: " + item.getStringValue(), e);
				}
			} else {
				Resource resource;
				if(binary) {
					resource = collection.createResource(docName, "BinaryResource");
                    ((EXistResource)resource).setMimeType(mimeType);
                } else
					resource = collection.createResource(docName, "XMLResource");
				if(Type.subTypeOf(item.getType(), Type.STRING)) {
					resource.setContent(item.getStringValue());
				} else if(Type.subTypeOf(item.getType(), Type.NODE)) {
					if(binary) {
						StringWriter writer = new StringWriter();
						SAXSerializer serializer = new SAXSerializer();
						serializer.setWriter(writer);
						item.toSAX(context.getBroker(), serializer);
						resource.setContent(writer.toString());
					} else {
						ContentHandler handler = ((XMLResource)resource).setContentAsSAX();
						handler.startDocument();
						item.toSAX(context.getBroker(), handler);
						handler.endDocument();
					}
				} else
					throw new XPathException("Data should be either a node or a string");
				collection.storeResource(resource);
				context.getRootExpression().resetState();
			}
		} catch (XMLDBException e) {
			throw new XPathException(
				"XMLDB reported an exception while storing document",
				e);
		} catch (SAXException e) {
			throw new XPathException(
				"SAX reported an exception while storing document",
				e);
		}
		return Sequence.EMPTY_SEQUENCE;
	}
	
	private void loadFromURI(Collection collection, URI uri, String docName, boolean binary) 
	throws XPathException {
		if("file".equals(uri.getScheme())) {
			String path = uri.getPath();
			File file = new File(path);
			if(!file.canRead())
				throw new XPathException(getASTNode(), "Cannot read path: " + path);
			loadFromFile(collection, file, docName, binary);
		} else {
			try {
				File temp = File.createTempFile("exist", ".xml");
				temp.deleteOnExit();
				OutputStream os = new FileOutputStream(temp);
				InputStream is = uri.toURL().openStream();
				byte[] data = new byte[1024];
				int read = 0;
				while((read = is.read(data)) > -1) {
					os.write(data, 0, read);
				}
				is.close();
				loadFromFile(collection, temp, docName, binary);
				temp.delete();
			} catch (MalformedURLException e) {
				throw new XPathException(getASTNode(), "Malformed URL: " + uri.toString(), e);
			} catch (IOException e) {
				throw new XPathException(getASTNode(), "IOException while reading from URL: " +
						uri.toString(), e);
			}
		}
	}
	
	private void loadFromFile(Collection collection, File file, String docName, boolean binary) 
	throws XPathException {
		if(file.isFile()) {
			if(docName == null)
				docName = file.getName();
			try {
				Resource resource;
				if(binary)
					resource = collection.createResource(docName, "BinaryResource");
				else
					resource = collection.createResource(docName, "XMLResource");
				resource.setContent(file);
				collection.storeResource(resource);
			} catch (XMLDBException e) {
				throw new XPathException(getASTNode(), "Could not store file " + file.getAbsolutePath() + 
						": " + e.getMessage(), e);
			}
		} else
			throw new XPathException(getASTNode(), file.getAbsolutePath() + " does not point to a file");
	}
}
