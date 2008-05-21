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
package org.exist.xquery.modules.compression;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.exist.collections.Collection;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.QName;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.Serializer;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.Base64Binary;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xml.sax.SAXException;

/**
 * Compresses a sequence of resources and/or collections into a Zip file
 * 
 * @author Adam Retter <adam.retter@devon.gov.uk>
 * @version 1.0
 */
public class ZipFunction extends BasicFunction {

	public final static FunctionSignature signatures[] = {
			new FunctionSignature(
					new QName("zip", CompressionModule.NAMESPACE_URI,
							CompressionModule.PREFIX),
					"Zip's resources and/or collections. $a is a sequence of URI's, if a URI points to a collection"
							+ "then the collection, its resources and sub-collections are zipped recursively. "
							+ "$b indicates whether to use the collection hierarchy in the zip file.",
					new SequenceType[] {
							new SequenceType(Type.ANY_URI,
									Cardinality.ONE_OR_MORE),
							new SequenceType(Type.BOOLEAN,
									Cardinality.EXACTLY_ONE) },
					new SequenceType(Type.BASE64_BINARY,
							Cardinality.ZERO_OR_MORE)),
			new FunctionSignature(
					new QName("zip", CompressionModule.NAMESPACE_URI,
							CompressionModule.PREFIX),
					"Zip's resources and/or collections. $a is a sequence of URI's, if a URI points to a collection"
							+ "then the collection, its resources and sub-collections are zipped recursively. "
							+ "$b indicates whether to use the collection hierarchy in the zip file."
							+ "$c is removed from the beginning of each file path.",
					new SequenceType[] {
							new SequenceType(Type.ANY_URI,
									Cardinality.ONE_OR_MORE),
							new SequenceType(Type.BOOLEAN,
									Cardinality.EXACTLY_ONE),
							new SequenceType(Type.STRING,
									Cardinality.EXACTLY_ONE) },
					new SequenceType(Type.BASE64_BINARY,
							Cardinality.ZERO_OR_MORE)) };

	public ZipFunction(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
		// are there some uri's to zip?
		if (args[0].isEmpty()) {
			return Sequence.EMPTY_SEQUENCE;
		}

		// use a hierarchy in the zip file?
		boolean useHierarchy = args[1].effectiveBooleanValue();

		// Get offset
		String stripOffset = "";
		if (args.length == 3) {
			stripOffset = args[2].getStringValue();
		}

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ZipOutputStream zos = new ZipOutputStream(baos);

		// iterate through the argument sequence
		for (SequenceIterator i = args[0].iterate(); i.hasNext();) {
			AnyURIValue uri = (AnyURIValue) i.nextItem();
			DocumentImpl doc = null;
			try {
				// try for a doc
				doc = context.getBroker().getXMLResource(uri.toXmldbURI(),
						Lock.READ_LOCK);

				if (doc == null) {
					// no doc, try for a collection
					Collection col = context.getBroker().getCollection(
							uri.toXmldbURI());

					if (col != null) {
						// got a collection
						zipCollection(zos, col, useHierarchy, stripOffset);
					} else {
						// no doc or collection
						throw new XPathException(getASTNode(), "Invalid URI: "
								+ uri.toString());
					}
				} else {
					// got a doc
					zipResource(zos, doc, useHierarchy, stripOffset);
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
			zos.close();
		} catch (IOException ioe) {
			throw new XPathException(getASTNode(), ioe.getMessage());
		}

		return new Base64Binary(baos.toByteArray());
	}

	/**
	 * Adds a document to a Zip
	 * 
	 * @param zos
	 *            The Zip Output Stream to add the document to
	 * @param doc
	 *            The document to add to the Zip
	 * @param useHierarchy
	 *            Whether to use a folder hierarchy in the Zip file that
	 *            reflects the collection hierarchy
	 */
	private void zipResource(ZipOutputStream zos, DocumentImpl doc,
			boolean useHierarchy, String stripOffset) throws IOException,
			SAXException {
		// create an entry in the Zip for the document
		ZipEntry entry = null;
		if (useHierarchy) {
			String docCollection = doc.getCollection().getURI().toString();

			// remove leading offset
			if (docCollection.startsWith(stripOffset)) {
				docCollection = docCollection.substring(stripOffset.length());
			}

			// remove leading /
			if (docCollection.startsWith("/")) {
				docCollection = docCollection.substring(1);
			}

			XmldbURI collection = XmldbURI.create(docCollection);

			entry = new ZipEntry(collection.append(doc.getFileURI()).toString());
		} else {
			entry = new ZipEntry(doc.getFileURI().toString());
		}
		zos.putNextEntry(entry);

		// add the document to the Zip
		if (doc.getResourceType() == DocumentImpl.XML_FILE) {
			// xml file
			Serializer serializer = context.getBroker().getSerializer();
			serializer.setUser(context.getUser());
			serializer.setProperty("omit-xml-declaration", "no");
			String strDoc = serializer.serialize(doc);
			zos.write(strDoc.getBytes());
		} else if (doc.getResourceType() == DocumentImpl.BINARY_FILE) {
			// binary file
			byte[] data = context.getBroker().getBinaryResource(
					(BinaryDocument) doc);
			zos.write(data);
		}

		// close the entry in the Zip
		zos.closeEntry();
	}

	/**
	 * Adds a Collection and its child collections and resources recursively to
	 * a Zip
	 * 
	 * @param zos
	 *            The Zip Output Stream to add the document to
	 * @param col
	 *            The Collection to add to the Zip
	 * @param useHierarchy
	 *            Whether to use a folder hierarchy in the Zip file that
	 *            reflects the collection hierarchy
	 */
	private void zipCollection(ZipOutputStream zos, Collection col,
			boolean useHierarchy, String stripOffset) throws IOException,
			SAXException, LockException {
		// iterate over child documents

		DocumentSet childDocs = new DocumentSet();
		col.getDocuments(context.getBroker(), childDocs, true);
		for (Iterator itChildDocs = childDocs.iterator(); itChildDocs.hasNext();) {
			DocumentImpl childDoc = (DocumentImpl) itChildDocs.next();
			childDoc.getUpdateLock().acquire(Lock.READ_LOCK);
			try {
				// zip the resource
				zipResource(zos, childDoc, useHierarchy, stripOffset);
			} finally {
				childDoc.getUpdateLock().release(Lock.READ_LOCK);
			}
		}

		// iterate over child collections
		for (Iterator itChildCols = col.collectionIterator(); itChildCols
				.hasNext();) {
			// get the child collection
			XmldbURI childColURI = (XmldbURI) itChildCols.next();
			Collection childCol = context.getBroker().getCollection(
					col.getURI().append(childColURI));

			// recurse
			zipCollection(zos, childCol, useHierarchy, stripOffset);
		}
	}
}
