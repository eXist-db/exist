/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-08 The eXist Project
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
package org.exist.xquery.modules.compression;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.StringTokenizer;

import org.exist.collections.Collection;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DefaultDocumentSet;
import org.exist.dom.DocumentImpl;
import org.exist.dom.MutableDocumentSet;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.Serializer;
import org.exist.util.Base64Decoder;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.Base64Binary;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * Compresses a sequence of resources and/or collections
 * 
 * @author Adam Retter <adam@exist-db.org>
 * @version 1.0
 */
public abstract class AbstractCompressFunction extends BasicFunction {

	public AbstractCompressFunction(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}
	
	private String removeLeadingOffset(String uri, String stripOffset){
		// remove leading offset
		if (uri.startsWith(stripOffset)) {
			uri = uri.substring(stripOffset.length());
		}
		// remove leading /
		if (uri.startsWith("/")) {
			uri = uri.substring(1);
		}
		return uri;
	}

	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
		// are there some uri's to tar?
		if (args[0].isEmpty()) {
			return Sequence.EMPTY_SEQUENCE;
		}

		// use a hierarchy in the tar file?
		boolean useHierarchy = args[1].effectiveBooleanValue();

		// Get offset
		String stripOffset = "";
		if (args.length == 3) {
			stripOffset = args[2].getStringValue();
		}

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		OutputStream os = stream(baos);

		// iterate through the argument sequence
		for (SequenceIterator i = args[0].iterate(); i.hasNext();) {
			Item item = i.nextItem();
			DocumentImpl doc = null;
			try {
				if (item instanceof Element){
					Element element = (Element) item; 
					compressElement(os, element, useHierarchy, stripOffset);
				} else {
					AnyURIValue uri = (AnyURIValue) item;
					// try for a doc
					doc = context.getBroker().getXMLResource(uri.toXmldbURI(),
							Lock.READ_LOCK);
					if (doc == null) {
						// no doc, try for a collection
						Collection col = context.getBroker().getCollection(
								uri.toXmldbURI());
						if (col != null) {
							// got a collection
							compressCollection(os, col, useHierarchy, stripOffset);
						} else {
							// no doc or collection
							throw new XPathException(getASTNode(), "Invalid URI: " + uri.toString());
						}
					} else {
						// got a doc
						compressResource(os, doc, useHierarchy, stripOffset);
					}
				}
			} catch (PermissionDeniedException pde) {
				throw new XPathException(getASTNode(), pde.getMessage());
			} catch (IOException ioe) {
				throw new XPathException(getASTNode(), ioe.getMessage());
			} catch (SAXException se) {
				throw new XPathException(getASTNode(), se.getMessage());
			} catch (LockException le) {
				throw new XPathException(getASTNode(), le.getMessage());
			} finally {
				if (doc != null) {
					doc.getUpdateLock().release(Lock.READ_LOCK);
				}
			}
		}
		try {
			os.close();
		} catch (IOException ioe) {
			throw new XPathException(getASTNode(), ioe.getMessage());
		}
		return new Base64Binary(baos.toByteArray());
	}

	/**
	 * Adds a element to a archive
	 * 
	 * @param os
	 *            The Output Stream to add the element to
	 * @param nodeValue
	 *            The element to add to the archive
	 * @param useHierarchy
	 *            Whether to use a folder hierarchy in the archive file that
	 *            reflects the collection hierarchy
	 */
	private void compressElement(OutputStream os, Element element, boolean useHierarchy, String stripOffset) throws XPathException, IOException, SAXException {
		 if (element.getNodeName().equals("entry") && element.getNamespaceURI().equals("")){
			String name = element.getAttribute("name");
			String type = element.getAttribute("type");
			if (name!=null){
				if (useHierarchy){
					name = removeLeadingOffset(name, stripOffset);
				} else {
					StringTokenizer tok = new StringTokenizer(name, "/");
			        while (tok.hasMoreTokens()) {
			            name = tok.nextToken();
			        }
				}
				if ("collection".equals(type)){
					name += "/"; 
				}
				Object entry = newEntry(name);
				putEntry(os, entry);
				if (element.getChildNodes().getLength()>1){
					throw new XPathException(getASTNode(), "Entry content is not valid XML fragment.");
				} else {
					if (!name.endsWith("/")){
						byte[] value;
						Node content = element.getFirstChild();
						if (content==null){
							value=new byte[0];
						} else {
							if (content.getNodeType() == Node.TEXT_NODE){
								String text = content.getNodeValue();
								Base64Decoder dec = new Base64Decoder();
								if ("binary".equals(type)){
									dec.translate(text);
									value = dec.getByteArray();
								} else {
									value = text.getBytes();
								}
							} else {
								Serializer serializer = context.getBroker().getSerializer();
								serializer.setUser(context.getUser());
								serializer.setProperty("omit-xml-declaration", "no");
								value = serializer.serialize((NodeValue) content).getBytes();
							}
						}
						os.write(value);
					}
				}
				closeEntry(os);
			} else {
				throw new XPathException(getASTNode(), "Entry must have name attribute.");
			}
		} else {
			throw new XPathException(getASTNode(), "Item must be type of xs:anyURI or element enry.");
		}
	}

	/**
	 * Adds a document to a archive
	 * 
	 * @param os
	 *            The Output Stream to add the document to
	 * @param doc
	 *            The document to add to the archive
	 * @param useHierarchy
	 *            Whether to use a folder hierarchy in the archive file that
	 *            reflects the collection hierarchy
	 */
	private void compressResource(OutputStream os, DocumentImpl doc, boolean useHierarchy, String stripOffset) throws IOException, SAXException {
		// create an entry in the Tar for the document
		Object entry = null;
		if (useHierarchy) {
			String docCollection = doc.getCollection().getURI().toString();
			XmldbURI collection = XmldbURI.create(removeLeadingOffset(docCollection, stripOffset));
			entry = newEntry(collection.append(doc.getFileURI()).toString());
		} else {
			entry = newEntry(doc.getFileURI().toString());
		}
		putEntry(os, entry);
		if (doc.getResourceType() == DocumentImpl.XML_FILE) {
			// xml file
			Serializer serializer = context.getBroker().getSerializer();
			serializer.setUser(context.getUser());
			serializer.setProperty("omit-xml-declaration", "no");
			String strDoc = serializer.serialize(doc);
			os.write(strDoc.getBytes());
		} else if (doc.getResourceType() == DocumentImpl.BINARY_FILE) {
			// binary file
            InputStream is = context.getBroker().getBinaryResource((BinaryDocument)doc);
			byte[] data = new byte[16384];
            int len = 0;
            while ((len=is.read(data,0,data.length))>0) {
            	os.write(data,0,len);
            }
            is.close();
		}
		// close the entry
		closeEntry(os);
	}
	
	/**
	 * Adds a Collection and its child collections and resources recursively to
	 * a archive
	 * 
	 * @param os
	 *            The Output Stream to add the document to
	 * @param col
	 *            The Collection to add to the archive
	 * @param useHierarchy
	 *            Whether to use a folder hierarchy in the archive file that
	 *            reflects the collection hierarchy
	 */
	private void compressCollection(OutputStream os, Collection col, boolean useHierarchy, String stripOffset) throws IOException, SAXException, LockException {
		// iterate over child documents
		MutableDocumentSet childDocs = new DefaultDocumentSet();
		col.getDocuments(context.getBroker(), childDocs, true);
		for (Iterator itChildDocs = childDocs.getDocumentIterator(); itChildDocs
				.hasNext();) {
			DocumentImpl childDoc = (DocumentImpl) itChildDocs.next();
			childDoc.getUpdateLock().acquire(Lock.READ_LOCK);
			try {
				compressResource(os, childDoc, useHierarchy, stripOffset);
			} finally {
				childDoc.getUpdateLock().release(Lock.READ_LOCK);
			}
		}
		// iterate over child collections
		for (Iterator itChildCols = col.collectionIterator(); itChildCols.hasNext();) {
			// get the child collection
			XmldbURI childColURI = (XmldbURI) itChildCols.next();
			Collection childCol = context.getBroker().getCollection(col.getURI().append(childColURI));
			// recurse
			compressCollection(os, childCol, useHierarchy, stripOffset);
		}
	}
	
	protected abstract OutputStream stream(ByteArrayOutputStream baos); 
	
	protected abstract Object newEntry(String name);
	
	protected abstract void putEntry(Object os, Object entry) throws IOException;

	protected abstract void closeEntry(Object os) throws IOException;

}
