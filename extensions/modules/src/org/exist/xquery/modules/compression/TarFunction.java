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
import java.util.Iterator;

import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarOutputStream;
import org.exist.collections.Collection;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DefaultDocumentSet;
import org.exist.dom.DocumentImpl;
import org.exist.dom.MutableDocumentSet;
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
 * Compresses a sequence of resources and/or collections into a Tar file
 * 
 * @author Adam Retter <adam@exist-db.org>
 * @version 1.0
 */
public class TarFunction extends BasicFunction {

	public final static FunctionSignature signatures[] = {
			new FunctionSignature(
					new QName("tar", CompressionModule.NAMESPACE_URI,
							CompressionModule.PREFIX),
					"Tar's resources and/or collections. $a is a sequence of URI's, if a URI points to a collection"
							+ "then the collection, its resources and sub-collections are tarred recursively. "
							+ "$b indicates whether to use the collection hierarchy in the tar file.",
					new SequenceType[] {
							new SequenceType(Type.ANY_URI,
									Cardinality.ONE_OR_MORE),
							new SequenceType(Type.BOOLEAN,
									Cardinality.EXACTLY_ONE) },
					new SequenceType(Type.BASE64_BINARY,
							Cardinality.ZERO_OR_MORE)),
			new FunctionSignature(
					new QName("tar", CompressionModule.NAMESPACE_URI,
							CompressionModule.PREFIX),
					"Tar's resources and/or collections. $a is a sequence of URI's, if a URI points to a collection"
							+ "then the collection, its resources and sub-collections are tarred recursively. "
							+ "$b indicates whether to use the collection hierarchy in the tar file."
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

	public TarFunction(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
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
		TarOutputStream tos = new TarOutputStream(baos);

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
						tarCollection(tos, col, useHierarchy, stripOffset);
					} else {
						// no doc or collection
						throw new XPathException(getASTNode(), "Invalid URI: "
								+ uri.toString());
					}
				} else {
					// got a doc
					tarResource(tos, doc, useHierarchy, stripOffset);
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
			tos.close();
		} catch (IOException ioe) {
			throw new XPathException(getASTNode(), ioe.getMessage());
		}

		return new Base64Binary(baos.toByteArray());
	}

	/**
	 * Adds a document to a Tar
	 * 
	 * @param tos
	 *            The Tar Output Stream to add the document to
	 * @param doc
	 *            The document to add to the Tar
	 * @param useHierarchy
	 *            Whether to use a folder hierarchy in the Tar file that
	 *            reflects the collection hierarchy
	 */
	private void tarResource(TarOutputStream tos, DocumentImpl doc,
			boolean useHierarchy, String stripOffset) throws IOException,
			SAXException {
		// create an entry in the Tar for the document
		TarEntry entry = null;
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

			entry = new TarEntry(collection.append(doc.getFileURI()).toString());
		} else {
			entry = new TarEntry(doc.getFileURI().toString());
		}
		tos.putNextEntry(entry);

		// add the document to the Tar
		if (doc.getResourceType() == DocumentImpl.XML_FILE) {
			// xml file
			Serializer serializer = context.getBroker().getSerializer();
			serializer.setUser(context.getUser());
			serializer.setProperty("omit-xml-declaration", "no");
			String strDoc = serializer.serialize(doc);
			tos.write(strDoc.getBytes());
		} else if (doc.getResourceType() == DocumentImpl.BINARY_FILE) {
			// binary file
			byte[] data = context.getBroker().getBinaryResource(
					(BinaryDocument) doc);
			tos.write(data);
		}

		// close the entry in the Tar
		tos.closeEntry();
	}

	/**
	 * Adds a Collection and its child collections and resources recursively to
	 * a Tar
	 * 
	 * @param tos
	 *            The Tar Output Stream to add the document to
	 * @param col
	 *            The Collection to add to the Tar
	 * @param useHierarchy
	 *            Whether to use a folder hierarchy in the Tar file that
	 *            reflects the collection hierarchy
	 */
	private void tarCollection(TarOutputStream tos, Collection col,
			boolean useHierarchy, String stripOffset) throws IOException,
			SAXException, LockException {
		// iterate over child documents
		MutableDocumentSet childDocs = new DefaultDocumentSet();
		col.getDocuments(context.getBroker(), childDocs, true);
		for (Iterator itChildDocs = childDocs.getDocumentIterator(); itChildDocs
				.hasNext();) {
			DocumentImpl childDoc = (DocumentImpl) itChildDocs.next();
			childDoc.getUpdateLock().acquire(Lock.READ_LOCK);
			try {
				// tar the resource
				tarResource(tos, childDoc, useHierarchy, stripOffset);
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
			tarCollection(tos, childCol, useHierarchy, stripOffset);
		}
	}
}