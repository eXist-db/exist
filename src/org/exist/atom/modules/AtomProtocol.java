/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2006-2012 The eXist Project
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
 *  $Id$
 */
package org.exist.atom.modules;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.log4j.Logger;

import org.exist.EXistException;
import org.exist.Namespaces;
import org.exist.atom.Atom;
import org.exist.atom.IncomingMessage;
import org.exist.atom.OutgoingMessage;
import org.exist.atom.util.DOM;
import org.exist.atom.util.DOMDB;
import org.exist.atom.util.DateFormatter;
import org.exist.atom.util.NodeHandler;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.config.ConfigurationException;
import org.exist.dom.DocumentImpl;
import org.exist.dom.ElementImpl;
import org.exist.dom.NodeIndexListener;
import org.exist.dom.StoredNode;
import org.exist.http.BadRequestException;
import org.exist.http.NotFoundException;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.UUIDGenerator;
import org.exist.storage.DBBroker;
import org.exist.storage.StorageAddress;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.util.SyntaxException;
import org.exist.xmldb.XmldbURI;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * 
 * @author R. Alexander Milowski
 */
public class AtomProtocol extends AtomFeeds implements Atom {

	protected final static Logger LOG = Logger.getLogger(AtomProtocol.class);
	public static final String FEED_DOCUMENT_NAME = ".feed.atom";
	public static final String ENTRY_COLLECTION_NAME = ".feed.entry";
	public static final XmldbURI FEED_DOCUMENT_URI = XmldbURI.create(FEED_DOCUMENT_NAME);
	public static final XmldbURI ENTRY_COLLECTION_URI = XmldbURI.create(ENTRY_COLLECTION_NAME);

	// private static final String ENTRY_XPOINTER = "xpointer(/entry)";

	final static class NodeListener implements NodeIndexListener {

		StoredNode node;

		public NodeListener(StoredNode node) {
			this.node = node;
		}

		@Override
		public void nodeChanged(StoredNode newNode) {
			final long address = newNode.getInternalAddress();
			if (StorageAddress.equals(node.getInternalAddress(), address)) {
				node = newNode;
			}
		}
	}

	/** Creates a new instance of AtomProtocol */
	public AtomProtocol() {
	}

	@Override
	public void doPost(DBBroker broker, IncomingMessage request,
			OutgoingMessage response) throws BadRequestException,
			PermissionDeniedException, NotFoundException, EXistException {
		
		final XmldbURI pathUri = XmldbURI.create(request.getPath());
		String contentType = request.getHeader("Content-Type");
		String charset = getContext().getDefaultCharset();

		MimeType mime = MimeType.BINARY_TYPE;
		if (contentType != null) {
			final int semicolon = contentType.indexOf(';');
			if (semicolon > 0) {
				contentType = contentType.substring(0, semicolon).trim();
			}
			mime = MimeTable.getInstance().getContentType(contentType);
			if (mime == null) {
				mime = MimeType.BINARY_TYPE;
			}
			final int equals = contentType.indexOf('=', semicolon);
			if (equals > 0) {
				final String param = contentType.substring(semicolon + 1, equals)
						.trim();
				if (param.compareToIgnoreCase("charset=") == 0) {
					charset = param.substring(equals + 1).trim();
				}
			}
		}

		final String currentDateTime = DateFormatter.toXSDDateTime(new Date());

		Collection collection = broker.getCollection(pathUri);

		if (mime.getName().equals(Atom.MIME_TYPE)) {
			final DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
			docFactory.setNamespaceAware(true);
			DocumentBuilder docBuilder = null;
			Document doc = null;
			try {
				final InputSource src = new InputSource(
						new InputStreamReader(request.getInputStream(), charset)
				);
				docBuilder = docFactory.newDocumentBuilder();
				doc = docBuilder.parse(src);
			} catch (final IOException e) {
				LOG.warn(e);
				throw new BadRequestException(e.getMessage());
			} catch (final SAXException e) {
				LOG.warn(e);
				throw new BadRequestException(e.getMessage());
			} catch (final ParserConfigurationException e) {
				LOG.warn(e);
				throw new BadRequestException(e.getMessage());
			}

			final Element root = doc.getDocumentElement();
			final String ns = root.getNamespaceURI();
			if (ns == null || !ns.equals(Atom.NAMESPACE_STRING)) {
				throw new BadRequestException(
						"Any content posted with the Atom mime type must be in the Atom namespace.");
			}

			if ("feed".equals(root.getLocalName())) {
				DocumentImpl feedDoc = null;
				final TransactionManager transact = broker.getBrokerPool().getTransactionManager();
				final Txn transaction = transact.beginTransaction();
				try {
					if (collection != null) {
						feedDoc = collection.getDocument(broker, FEED_DOCUMENT_URI);
						if (feedDoc != null) {
							throw new PermissionDeniedException(
									"Collection at " + request.getPath()
											+ " already exists.");
						}
					} else {
						collection = broker.getOrCreateCollection(transaction, pathUri);
						setPermissions(broker, root, collection);
						broker.saveCollection(transaction, collection);
					}

					final String id = UUIDGenerator.getUUID();
					DOM.replaceTextElement(root, 
							Atom.NAMESPACE_STRING,
							"updated", currentDateTime, true);
					DOM.replaceTextElement(root, 
							Atom.NAMESPACE_STRING,
							"id", "urn:uuid:" + id, true);
					
					Element editLink = findLink(root, "edit");
					if (editLink != null) {
						throw new BadRequestException(
								"An edit link relation cannot be specified in the feed.");
					}
					editLink = doc.createElementNS(Atom.NAMESPACE_STRING, "link");
					editLink.setAttribute("rel", "edit");
					editLink.setAttribute("type", Atom.MIME_TYPE);
					editLink.setAttribute("href", "#");
					root.appendChild(editLink);

					Element selfLink = findLink(root, "self");
					if (selfLink != null) {
						throw new BadRequestException(
								"A self link relation cannot be specified in the feed.");
					}
					selfLink = doc.createElementNS(Atom.NAMESPACE_STRING, "link");
					selfLink.setAttribute("rel", "self");
					selfLink.setAttribute("type", Atom.MIME_TYPE);
					selfLink.setAttribute("href", "#");
					root.appendChild(selfLink);

					final IndexInfo info = collection.validateXMLResource(transaction, broker, FEED_DOCUMENT_URI, doc);
					setPermissions(broker, root, info.getDocument());
					// TODO : We should probably unlock the collection here
					collection.store(transaction, broker, info, doc, false);
					transact.commit(transaction);
					response.setStatusCode(204);
					response.setHeader("Location", request.getModuleBase() + request.getPath());

				} catch (final IOException ex) {
					transact.abort(transaction);
					throw new EXistException("IO error: " + ex.getMessage(), ex);
				} catch (final TriggerException ex) {
					transact.abort(transaction);
					throw new EXistException("Trigger failed: " + ex.getMessage(), ex);
				} catch (final SAXException ex) {
					transact.abort(transaction);
					throw new EXistException("SAX error: " + ex.getMessage(), ex);
				} catch (final LockException ex) {
					transact.abort(transaction);
					throw new EXistException("Cannot acquire write lock.", ex);
				} finally {
                    transact.close(transaction);
                }
			} else if ("entry".equals(root.getLocalName())) {

				if (collection == null) {
					throw new BadRequestException("Collection "
							+ request.getPath() + " does not exist.");
				}

				LOG.debug("Adding entry to " + request.getPath());
				DocumentImpl feedDoc = null;
				feedDoc = collection.getDocument(broker, FEED_DOCUMENT_URI);

				if (!feedDoc.getPermissions().validate(broker.getSubject(), Permission.WRITE))
					{throw new PermissionDeniedException(
							"Permission denied to update feed " + collection.getURI());}

				final TransactionManager transact = broker.getBrokerPool().getTransactionManager();
				final Txn transaction = transact.beginTransaction();
				final String uuid = UUIDGenerator.getUUID();
				final String id = "urn:uuid:" + uuid;
				final Element publishedE = DOM.replaceTextElement(root,
						Atom.NAMESPACE_STRING, "published", currentDateTime, true, true);
				DOM.replaceTextElement(root, Atom.NAMESPACE_STRING, "updated", currentDateTime, true, true);
				DOM.replaceTextElement(root, Atom.NAMESPACE_STRING, "id", id, true, true);

				Element editLink = findLink(root, "edit");
				final Element editLinkSrc = findLink(root, "edit-media");
				if (editLink != null || editLinkSrc != null) {
					throw new BadRequestException(
							"An edit link relation cannot be specified in the entry.");
				}
				editLink = doc.createElementNS(Atom.NAMESPACE_STRING, "link");
				editLink.setAttribute("rel", "edit");
				editLink.setAttribute("type", Atom.MIME_TYPE);
				editLink.setAttribute("href", "?id=" + id);
				final Node next = publishedE.getNextSibling();
				if (next == null) {
					root.appendChild(editLink);
				} else {
					root.insertBefore(editLink, next);
				}

				try {
					// get the feed
					LOG.debug("Acquiring lock on feed document...");
					final ElementImpl feedRoot = (ElementImpl) feedDoc.getDocumentElement();

					// Lock the feed
					feedDoc.getUpdateLock().acquire(Lock.WRITE_LOCK);

					// Append the entry
					collection = broker.getOrCreateCollection(transaction, pathUri.append(ENTRY_COLLECTION_URI));
					setPermissions(broker, root, collection);
					broker.saveCollection(transaction, collection);
					final XmldbURI entryURI = entryURI(uuid);
					final DocumentImpl entryDoc = collection.getDocument(broker, entryURI);
					if (entryDoc != null) {
						throw new PermissionDeniedException("Entry with " + id
								+ " already exists.");
					}
					final IndexInfo info = collection.validateXMLResource(transaction, broker, entryURI, doc);
					setPermissions(broker, root, info.getDocument());
					// TODO : We should probably unlock the collection here
					collection.store(transaction, broker, info, doc, false);

					// Update the updated element
					DOMDB.replaceTextElement(transaction, feedRoot,
							Atom.NAMESPACE_STRING, "updated", currentDateTime,
							true);

					// Store the changes
					LOG.debug("Storing change...");
					broker.storeXMLResource(transaction, feedDoc);
					transact.commit(transaction);

					LOG.debug("Done!");

					//XXX: response outside of try-block
					response.setStatusCode(201);
					response.setHeader("Location", request.getModuleBase()
							+ request.getPath() + "?id=" + id);
					getEntryById(broker, request.getPath(), id, response);
					/*
					 * response.setContentType(Atom.MIME_TYPE+"; charset="+charset
					 * ); OutputStreamWriter w = new
					 * OutputStreamWriter(response.getOutputStream(),charset);
					 * Transformer identity =
					 * TransformerFactory.newInstance().newTransformer();
					 * identity.transform(new DOMSource(doc),new
					 * StreamResult(w)); w.flush(); w.close();
					 */
				} catch (final IOException ex) {
					transact.abort(transaction);
					throw new EXistException("IO error: " + ex.getMessage(), ex);
				} catch (final TriggerException ex) {
					transact.abort(transaction);
					throw new EXistException("Trigger failed: "
							+ ex.getMessage(), ex);
				} catch (final SAXException ex) {
					transact.abort(transaction);
					throw new EXistException("SAX error: " + ex.getMessage(),
							ex);
				} catch (final LockException ex) {
					transact.abort(transaction);
					throw new EXistException("Cannot acquire write lock.", ex);
					/*
					 * } catch (IOException ex) { throw new
					 * EXistException("Internal error while serializing result."
					 * ,ex); } catch (TransformerException ex) { throw new
					 * EXistException("Serialization error.",ex);
					 */
				} finally {
                    transact.close(transaction);
					if (feedDoc != null)
						{feedDoc.getUpdateLock().release(Lock.WRITE_LOCK);}
				}
			} else {
				throw new BadRequestException(
						"Unexpected element: {http://www.w3.org/2005/Atom}" + root.getLocalName());
			}

		} else {
			if (collection == null)
				{throw new BadRequestException("Collection " + request.getPath() + " does not exist.");}

			final DocumentImpl feedDoc = collection.getDocument(broker, FEED_DOCUMENT_URI);
			if (feedDoc == null)
				{throw new BadRequestException("Feed at " + request.getPath() + " does not exist.");}

			if (!feedDoc.getPermissions().validate(broker.getSubject(), Permission.WRITE))
				{throw new PermissionDeniedException(
						"Permission denied to update feed " + collection.getURI());}

			String filename = request.getHeader("Slug");
			if (filename == null) {
				final String ext = MimeTable.getInstance().getPreferredExtension(mime);
				int count = 1;
				while (filename == null) {
					filename = "resource" + count + ext;
					
					if (collection.getDocument(broker, XmldbURI.create(filename)) != null)
						{filename = null;}

					count++;
				}
			}

			final TransactionManager transact = broker.getBrokerPool().getTransactionManager();
			final Txn transaction = transact.beginTransaction();
			try {
				final XmldbURI docUri = XmldbURI.create(filename);
				if (collection.getDocument(broker, docUri) != null) {
					transact.abort(transaction);
					throw new BadRequestException("Resource " + docUri + " already exists in collection " + pathUri);
				}

				final File tempFile = storeInTemporaryFile(request.getInputStream(), request.getContentLength());

				if (mime.isXMLType()) {
					InputStream is = new FileInputStream(tempFile);
					
					final IndexInfo info = collection.validateXMLResource(
							transaction, broker, docUri, 
							new InputSource(new InputStreamReader(is, charset)));
					
					is.close();
					info.getDocument().getMetadata().setMimeType(contentType);
					is = new FileInputStream(tempFile);
					
					collection.store(transaction, broker, info, 
							new InputSource(new InputStreamReader(is, charset)), false);
					
					is.close();
				} else {
					final FileInputStream is = new FileInputStream(tempFile);
					collection.addBinaryResource(transaction, broker, docUri, is, contentType, tempFile.length());
					is.close();
				}

				try {
					LOG.debug("Acquiring lock on feed document...");
					feedDoc.getUpdateLock().acquire(Lock.WRITE_LOCK);
					
					String title = request.getHeader("Title");
					if (title == null)
						{title = filename;}

					final String created = DateFormatter.toXSDDateTime(new Date());
					final ElementImpl feedRoot = (ElementImpl) feedDoc.getDocumentElement();
					DOMDB.replaceTextElement(transaction, feedRoot, Atom.NAMESPACE_STRING, "updated", created, true);
					final String uuid = UUIDGenerator.getUUID();
					final String id = "urn:uuid:" + uuid;
					final Element mediaEntry = generateMediaEntry(id, created, title, filename, mime.getName());

					collection = broker.getOrCreateCollection(transaction, pathUri.append(ENTRY_COLLECTION_URI));
					broker.saveCollection(transaction, collection);
					final XmldbURI entryURI = entryURI(uuid);

					final DocumentImpl entryDoc = collection.getDocument(broker, entryURI);
					if (entryDoc != null)
						{throw new PermissionDeniedException("Entry with " + id + " already exists.");}

					final IndexInfo info = collection.validateXMLResource(transaction, broker, entryURI, mediaEntry);
					// TODO : We should probably unlock the collection here
					collection.store(transaction, broker, info, mediaEntry, false);
					// Update the updated element
					DOMDB.replaceTextElement(
							transaction, feedRoot, Atom.NAMESPACE_STRING, "updated", 
							currentDateTime, true);
					
					LOG.debug("Storing change...");
					broker.storeXMLResource(transaction, feedDoc);
					transact.commit(transaction);
					
					LOG.debug("Done!");
					
					//XXX: response outside ty-block
					response.setStatusCode(201);
					response.setHeader("Location", request.getModuleBase() + request.getPath() + "?id=" + id);
					response.setContentType(Atom.MIME_TYPE + "; charset=" + charset);
					final OutputStreamWriter w = new OutputStreamWriter(response.getOutputStream(), charset);
					final Transformer identity = TransformerFactory.newInstance().newTransformer();
					identity.transform(new DOMSource(mediaEntry), new StreamResult(w));
					w.flush();
					w.close();

				} catch (final ParserConfigurationException ex) {
					transact.abort(transaction);
					throw new EXistException("DOM implementation is misconfigured.", ex);
				} catch (final TransformerException ex) {
					throw new EXistException("Serialization error.", ex);
				} catch (final LockException ex) {
					transact.abort(transaction);
					throw new EXistException("Cannot acquire write lock.", ex);
				} finally {
                    transact.close(transaction);
					if (feedDoc != null)
						{feedDoc.getUpdateLock().release(Lock.WRITE_LOCK);}
				}

			} catch (final IOException ex) {
				transact.abort(transaction);
				throw new EXistException("I/O error while handling temporary files.", ex);
			} catch (final SAXParseException e) {
				transact.abort(transaction);
				throw new BadRequestException("Parsing exception at "
						+ e.getLineNumber() + "/" + e.getColumnNumber() + ": "
						+ e.toString());
			} catch (final TriggerException e) {
				transact.abort(transaction);
				throw new PermissionDeniedException(e.getMessage());
			} catch (SAXException e) {
				transact.abort(transaction);
				Exception o = e.getException();
				if (o == null)
					{o = e;}
				
				throw new BadRequestException("Parsing exception: " + o.getMessage());
			
			} catch (final LockException e) {
				transact.abort(transaction);
				throw new PermissionDeniedException(e.getMessage());
			} finally {
                transact.close(transaction);
            }
		}
	}

	@Override
	public void doPut(DBBroker broker, IncomingMessage request,
			OutgoingMessage response) throws BadRequestException,
			PermissionDeniedException, NotFoundException, EXistException {
		
		final XmldbURI pathUri = XmldbURI.create(request.getPath());
		String contentType = request.getHeader("Content-Type");
		String charset = getContext().getDefaultCharset();

		MimeType mime = MimeType.BINARY_TYPE;
		if (contentType != null) {
			final int semicolon = contentType.indexOf(';');
			if (semicolon > 0) {
				contentType = contentType.substring(0, semicolon).trim();
			}

			mime = MimeTable.getInstance().getContentType(contentType);
			if (mime == null) {
				mime = MimeType.BINARY_TYPE;
			}

			final int equals = contentType.indexOf('=', semicolon);
			if (equals > 0) {
				final String param = contentType.substring(semicolon + 1, equals)
						.trim();
				if (param.compareToIgnoreCase("charset=") == 0) {
					charset = param.substring(equals + 1).trim();
				}
			}
		}

		final String currentDateTime = DateFormatter.toXSDDateTime(new Date());

		Collection collection = broker.getCollection(pathUri);

		if (mime.getName().equals(Atom.MIME_TYPE)) {
			final DocumentBuilderFactory docFactory = DocumentBuilderFactory
					.newInstance();
			docFactory.setNamespaceAware(true);
			DocumentBuilder docBuilder = null;
			Document doc = null;
			try {
				final InputSource src = new InputSource(
						new InputStreamReader(request.getInputStream(), charset));
				
				docBuilder = docFactory.newDocumentBuilder();
				doc = docBuilder.parse(src);
			} catch (final IOException e) {
				LOG.warn(e);
				throw new BadRequestException(e.getMessage());
			} catch (final SAXException e) {
				LOG.warn(e);
				throw new BadRequestException(e.getMessage());
			} catch (final ParserConfigurationException e) {
				LOG.warn(e);
				throw new BadRequestException(e.getMessage());
			}

			final Element root = doc.getDocumentElement();
			final String ns = root.getNamespaceURI();
			if (ns == null || !ns.equals(Atom.NAMESPACE_STRING)) {
				throw new BadRequestException(
					"Any content posted with the Atom mime type must be in the Atom namespace.");
			}

			if ("feed".equals(root.getLocalName())) {
				DocumentImpl feedDoc = collection.getDocument(broker, FEED_DOCUMENT_URI);
				if (feedDoc == null)
					{throw new BadRequestException("Collection at "
							+ request.getPath() + " does not exist.");}

				feedDoc = collection.getDocument(broker, FEED_DOCUMENT_URI);
				if (!feedDoc.getPermissions().validate(broker.getSubject(), Permission.WRITE))
					{throw new PermissionDeniedException(
							"Permission denied to update feed "
									+ collection.getURI());}

				if (DOM.findChild(root, Atom.NAMESPACE_STRING, "title") == null)
					{throw new BadRequestException(
							"The feed metadata sent does not contain a title.");}

				if (!feedDoc.getPermissions().validate(broker.getSubject(), Permission.WRITE))
					{throw new PermissionDeniedException(
							"Permission denied to update feed "
									+ collection.getURI());}

				final TransactionManager transact = broker.getBrokerPool().getTransactionManager();
				final Txn transaction = transact.beginTransaction();
				try {
					feedDoc.getUpdateLock().acquire(Lock.WRITE_LOCK);
					final ElementImpl feedRoot = (ElementImpl) feedDoc.getDocumentElement();

					// Modify the feed by merging the new feed-level elements
					mergeFeed(broker, transaction, feedRoot, root, DateFormatter.toXSDDateTime(new Date()));

					// Store the feed
					broker.storeXMLResource(transaction, feedDoc);
					transact.commit(transaction);
					response.setStatusCode(204);

				} catch (final LockException ex) {
					transact.abort(transaction);
					throw new EXistException("Cannot acquire write lock.", ex);
				} catch (final RuntimeException ex) {
					transact.abort(transaction);
					throw ex;
				} finally {
                    transact.close(transaction);
					if (feedDoc != null)
						{feedDoc.getUpdateLock().release(Lock.WRITE_LOCK);}
				}

			} else if ("entry".equals(root.getLocalName())) {
				if (collection == null)
					{throw new BadRequestException("Collection " + request.getPath() + " does not exist.");}

				final String id = request.getParameter("id");
				if (id == null)
					{throw new BadRequestException(
							"The 'id' parameter for the entry is missing.");}

				LOG.debug("Updating entry " + id + " in collection " + request.getPath());
				DocumentImpl feedDoc = null;
				DocumentImpl entryDoc = null;
				final TransactionManager transact = broker.getBrokerPool().getTransactionManager();
				final Txn transaction = transact.beginTransaction();

				try {
					// Get the feed
					LOG.debug("Acquiring lock on feed document...");
					feedDoc = collection.getDocument(broker, FEED_DOCUMENT_URI);
					if (!feedDoc.getPermissions().validate(broker.getSubject(), Permission.WRITE))
						{throw new PermissionDeniedException(
								"Permission denied to update feed "
										+ collection.getURI());}
					
					feedDoc.getUpdateLock().acquire(Lock.WRITE_LOCK);

					// Find the entry
					final String uuid = id.substring(9);
					collection = broker.getCollection(pathUri.append(ENTRY_COLLECTION_URI));
					final XmldbURI entryURI = entryURI(uuid);
					entryDoc = collection.getDocument(broker, entryURI);
					if (entryDoc == null)
						{throw new BadRequestException(
								"Cannot find entry with id " + id);}

					// Lock the entry
					entryDoc.getUpdateLock().acquire(Lock.WRITE_LOCK);

					final Element entry = entryDoc.getDocumentElement();

					mergeEntry(transaction, (ElementImpl) entry, root, currentDateTime);

					// Update the feed time
					DOMDB.replaceTextElement(transaction,
							(ElementImpl) feedDoc.getDocumentElement(),
							Atom.NAMESPACE_STRING, "updated", currentDateTime,
							true);

					// Store the feed
					broker.storeXMLResource(transaction, feedDoc);
					broker.storeXMLResource(transaction, entryDoc);
					transact.commit(transaction);

					// Send back the changed entry
					response.setStatusCode(200);
					getEntryById(broker, request.getPath(), id, response);
					/*
					 * response.setStatusCode(200);
					 * response.setContentType(Atom.
					 * MIME_TYPE+"; charset="+charset); OutputStreamWriter w =
					 * new
					 * OutputStreamWriter(response.getOutputStream(),charset);
					 * Transformer identity =
					 * TransformerFactory.newInstance().newTransformer();
					 * identity.transform(new DOMSource(entry),new
					 * StreamResult(w)); w.flush(); w.close();
					 */
				} catch (final LockException ex) {
					transact.abort(transaction);
					throw new EXistException("Cannot acquire write lock.", ex);
					/*
					 * } catch (IOException ex) { throw new EXistException(
					 * "I/O exception during serialization of entry response."
					 * ,ex); } catch (TransformerException ex) { throw new
					 * EXistException("Serialization error.",ex);
					 */
				} finally {
                    transact.close(transaction);
					if (feedDoc != null)
						{feedDoc.getUpdateLock().release(Lock.WRITE_LOCK);}

					if (entryDoc != null)
						{entryDoc.getUpdateLock().release(Lock.WRITE_LOCK);}
				}

			} else {
				throw new BadRequestException(
						"Unexpected element: {http://www.w3.org/2005/Atom}"
								+ root.getLocalName());
			}

		} else {
			final TransactionManager transact = broker.getBrokerPool().getTransactionManager();
			final Txn transaction = transact.beginTransaction();
			try {
				final XmldbURI docUri = pathUri.lastSegment();
				final XmldbURI collUri = pathUri.removeLastSegment();

				if (docUri == null || collUri == null) {
					transact.abort(transaction);
					throw new BadRequestException("The path is not valid: "
							+ request.getPath());
				}

				collection = broker.getCollection(collUri);
				if (collection == null) {
					transact.abort(transaction);
					throw new BadRequestException(
							"The collection does not exist: " + collUri);
				}

				if (collection.getDocument(broker, docUri) == null) {
					transact.abort(transaction);
					throw new BadRequestException("Resource " + docUri
							+ " does not exist in collection " + collUri);
				}

				final File tempFile = storeInTemporaryFile(request.getInputStream(),
						request.getContentLength());

				if (mime.isXMLType()) {
					InputStream is = new FileInputStream(tempFile);
					
					final IndexInfo info = collection.validateXMLResource(
							transaction, broker, docUri, 
							new InputSource(new InputStreamReader(is, charset)));
					
					is.close();
					
					info.getDocument().getMetadata().setMimeType(contentType);
					
					is = new FileInputStream(tempFile);
					
					collection.store(transaction, broker, info, 
						new InputSource(new InputStreamReader(is, charset)), false);
					
					is.close();
				} else {
					final FileInputStream is = new FileInputStream(tempFile);
					collection.addBinaryResource(transaction, broker, docUri,
							is, contentType, tempFile.length());
					is.close();
				}

				transact.commit(transaction);

				// TODO: Change the entry updated and send back the change?
				response.setStatusCode(200);

			} catch (final IOException ex) {
				transact.abort(transaction);
				throw new EXistException("I/O error while handling temporary files.", ex);
			} catch (final SAXParseException e) {
				transact.abort(transaction);
				throw new BadRequestException("Parsing exception at "
						+ e.getLineNumber() + "/" + e.getColumnNumber() + ": "
						+ e.toString());
			} catch (final TriggerException e) {
				transact.abort(transaction);
				throw new PermissionDeniedException(e.getMessage());
			} catch (SAXException e) {
				transact.abort(transaction);
				
				Exception o = e.getException();
				if (o == null)
					{o = e;}
				
				throw new BadRequestException("Parsing exception: " + o.getMessage());
			} catch (final LockException e) {
				transact.abort(transaction);
				throw new PermissionDeniedException(e.getMessage());
			} finally {
                transact.close(transaction);
            }
		}
	}

	@Override
	public void doDelete(DBBroker broker, IncomingMessage request,
			OutgoingMessage response) throws BadRequestException,
			PermissionDeniedException, NotFoundException, EXistException,
			IOException, TriggerException {
		
		final XmldbURI pathUri = XmldbURI.create(request.getPath());
		XmldbURI srcUri = null;
		final Collection collection = broker.getCollection(pathUri);
		if (collection == null)
			{throw new BadRequestException("Collection " + request.getPath() + " does not exist.");}

		final String id = request.getParameter("id");
		if (id == null) {
			// delete collection
			final TransactionManager transact = broker.getBrokerPool().getTransactionManager();
			final Txn transaction = transact.beginTransaction();
			try {
				broker.removeCollection(transaction, collection);
				transact.commit(transaction);
				response.setStatusCode(204);
			} finally {
				transact.close(transaction);
			}
			return;
		}

		LOG.info("Deleting entry " + id + " in collection " + request.getPath());
		DocumentImpl feedDoc = null;
		final TransactionManager transact = broker.getBrokerPool().getTransactionManager();
		final Txn transaction = transact.beginTransaction();
		final String currentDateTime = DateFormatter.toXSDDateTime(new Date());
		try {
			// Get the feed
			// LOG.info("Acquiring lock on feed document...");
			feedDoc = collection.getDocument(broker, FEED_DOCUMENT_URI);
			if (!feedDoc.getPermissions().validate(broker.getSubject(), Permission.WRITE))
				{throw new PermissionDeniedException(
						"Permission denied to update feed "
								+ collection.getURI());}
			feedDoc.getUpdateLock().acquire(Lock.WRITE_LOCK);

			// Find the entry
			final String uuid = id.substring(9);
			final Collection entryCollection = broker.getCollection(pathUri.append(ENTRY_COLLECTION_URI));
			final XmldbURI entryURI = entryURI(uuid);
			final DocumentImpl entryDoc = entryCollection.getDocument(broker, entryURI);
			if (entryDoc == null)
				{throw new BadRequestException("Entry with id " + id + " cannot be found.");}

			final Element entry = entryDoc.getDocumentElement();

			// Remove the media resource if there is one
			final Element content = DOM.findChild(entry, Atom.NAMESPACE_STRING, "content");
			if (content != null) {
				final String src = content.getAttribute("src");
				LOG.debug("Found content element, checking for resource " + src);
				if (src != null && src.indexOf('/') < 0) {
					srcUri = XmldbURI.create(src);
					final DocumentImpl resource = collection.getDocument(broker, srcUri);
					if (resource != null) {
						LOG.debug("Deleting resource " + src + " from " + request.getPath());
						if (resource.getResourceType() == DocumentImpl.BINARY_FILE) {
							collection.removeBinaryResource(transaction, broker, srcUri);
						} else {
							collection.removeXMLResource(transaction, broker, srcUri);
						}
					}
				}
			}

			// Remove the entry
			entryCollection.removeXMLResource(transaction, broker, entryURI);

			// Update the feed time
			final ElementImpl feedRoot = (ElementImpl) feedDoc.getDocumentElement();
			DOMDB.replaceTextElement(transaction, feedRoot,
					Atom.NAMESPACE_STRING, "updated", currentDateTime, true);

			// Store the change on the feed
			LOG.debug("Storing change...");
			broker.storeXMLResource(transaction, feedDoc);
			transact.commit(transaction);
			LOG.debug("Done!");
			response.setStatusCode(204);

		} catch (final TriggerException ex) {
			transact.abort(transaction);
			throw new EXistException("Cannot delete media resource " + srcUri,
					ex);
		} catch (final LockException ex) {
			transact.abort(transaction);
			throw new EXistException("Cannot acquire write lock.", ex);
		} finally {
            transact.close(transaction);
			if (feedDoc != null) {
				feedDoc.getUpdateLock().release(Lock.WRITE_LOCK);
			}
		}

	}

	public void mergeEntry(final Txn transaction, final ElementImpl target,
			Element source, final String updated) {
		
		final List<Node> toRemove = new ArrayList<Node>();
		DOM.forEachChild(target, new NodeHandler() {
			@Override
			public void process(Node parent, Node child) {
				if (child.getNodeType() == Node.ELEMENT_NODE) {
					final String ns = child.getNamespaceURI();
					if (ns != null && ns.equals(Atom.NAMESPACE_STRING)) {
						final String lname = child.getLocalName();
						if ("updated".equals(lname)) {
							// Changed updated
							DOMDB.replaceText(transaction, (ElementImpl) child,
									updated);
						} else if ("link".equals(lname)) {
							final String rel = ((Element) child).getAttribute("rel");
							if (!"edit".equals(rel)
									&& !"edit-media".equals(rel)) {
								// remove it
								toRemove.add(child);
							}
						} else if (!"id".equals(lname)
								&& !"published".equals(lname)) {
							// remove it
							toRemove.add(child);
						}
					} else {
						// remove it
						toRemove.add(child);
					}
				} else {
					toRemove.add(child);
				}
			}
		});

		for (final Node child : toRemove) {
			target.removeChild(transaction, child);
		}

		DOM.forEachChild(source, new NodeHandler() {
			@Override
			public void process(Node parent, Node child) {
				if (child.getNodeType() == Node.ELEMENT_NODE) {
					final String ns = child.getNamespaceURI();
					if (ns != null && ns.equals(Atom.NAMESPACE_STRING)) {
						final String lname = child.getLocalName();

						// Skip server controls updated, published, and id
						// elements
						if ("updated".equals(lname)
								|| "published".equals(lname)
								|| "id".equals(lname)) {
							return;
						}
						// Skip the edit link relations
						if ("link".equals(lname)) {
							final String rel = ((Element) child).getAttribute("rel");
							if ("edit".equals(rel) || "edit-media".equals(rel)) {
								return;
							}
						}
					}
					DOMDB.appendChild(transaction, target, child);
				}
			}
		});
	}

	public void mergeFeed(final DBBroker broker, final Txn transaction,
			final ElementImpl target, Element source, final String updated) {
		
		final DocumentImpl ownerDocument = (DocumentImpl) target.getOwnerDocument();
		final List<Node> toRemove = new ArrayList<Node>();
		DOM.forEachChild(target, new NodeHandler() {
			@Override
			public void process(Node parent, Node child) {
				if (child.getNodeType() == Node.ELEMENT_NODE) {
					final String ns = child.getNamespaceURI();
					if (ns != null) {
						final String lname = child.getLocalName();
						if (ns.equals(Atom.NAMESPACE_STRING)) {
							if ("updated".equals(lname)) {
								// Changed updated
								DOMDB.replaceText(transaction, (ElementImpl) child, updated);
							} else if ("link".equals(lname)) {
								final Element echild = (Element) child;
								final String rel = echild.getAttribute("rel");
								if (!"edit".equals(rel)) {
									// remove it
									toRemove.add(child);
								}
							} else if (!"id".equals(lname)
									&& !"published".equals(lname)) {
								// remove it
								toRemove.add(child);
							}
						} else {
							// remove it
							toRemove.add(child);
						}
					} else {
						// remove it
						toRemove.add(child);
					}
				} else {
					// remove it
					toRemove.add(child);
				}
			}
		});

		for (final Node child : toRemove)
			target.removeChild(transaction, child);

		final NodeList nl = source.getChildNodes();

		for (int i = 0; i < nl.getLength(); i++) {
			final Node child = nl.item(i);
			if (child.getNodeType() == Node.ELEMENT_NODE) {
				final String ns = child.getNamespaceURI();
				if (ns != null && ns.equals(Atom.NAMESPACE_STRING)) {
					final String lname = child.getLocalName();

					// Skip server controls updated, published, and id elements
					if ("updated".equals(lname) || "published".equals(lname) || "id".equals(lname)) {
						continue;
					}
					// Skip the edit link relations
					if ("link".equals(lname)) {
						final String rel = ((Element) child).getAttribute("rel");
						if ("edit".equals(rel))
							{continue;}
					}
				}
				DOMDB.appendChild(transaction, target, child);
			}
		}
		ownerDocument.getMetadata().clearIndexListener();
		ownerDocument.getMetadata().setLastModified(System.currentTimeMillis());
	}

	protected Element findLink(Element parent, String rel) {
		final NodeList nl = parent.getElementsByTagNameNS(Atom.NAMESPACE_STRING,
				"link");
		for (int i = 0; i < nl.getLength(); i++) {
			final Element link = (Element) nl.item(i);
			if (link.getAttribute("rel").equals(rel)) {
				return link;
			}
		}
		return null;
	}

	/**
	 * Apply permissions to a collection. Owner, owner group and access
	 * permissions can be set when creating a new feed by passing an element
	 * &lt;exist:permissions&gt; in the document, e.g.:
	 * 
	 * <pre>
	 * &lt;exist:permissions mode="0775" owner="editor" group="users"/&gt;
	 * </pre>
	 */
	protected void setPermissions(DBBroker broker, Element parent,
			Collection collection) throws LockException,
			PermissionDeniedException, EXistException {
		
		final Element element = DOM.findChild(parent, Namespaces.EXIST_NS, "permissions");
		if (element != null) {
			final String mode = element.getAttribute("mode");
			if (mode != null) {
				try {
					final int permissions = Integer.parseInt(mode, 8);
					collection.setPermissions(permissions);
				} catch (final NumberFormatException e) {
					try {
						collection.getPermissionsNoLock().setMode(mode);
					} catch (final SyntaxException e1) {
						throw new PermissionDeniedException(
								"syntax error for mode attribute in exist:permissions element");
					}
				}
			}
			final String owner = element.getAttribute("owner");
			final org.exist.security.SecurityManager securityMan = broker.getBrokerPool().getSecurityManager();
			if (!securityMan.hasAccount(owner))
				{throw new PermissionDeniedException(
						"Failed to change feed owner: user " + owner
								+ " does not exist.");}
			collection.getPermissionsNoLock().setOwner(owner);
			
			final String group = element.getAttribute("group");
			if (!securityMan.hasGroup(group))
				try {
					securityMan.addGroup(group);
				} catch (final ConfigurationException e) {
					throw new EXistException(e.getMessage(), e);
				}

			parent.removeChild(element);
		}
	}

	protected void setPermissions(DBBroker broker, Element parent,
			DocumentImpl resource) throws LockException,
			PermissionDeniedException, EXistException {
		
		final Element element = DOM.findChild(parent, Namespaces.EXIST_NS, "permissions");
		if (element != null) {
			final String mode = element.getAttribute("mode");
			try {
				final int permissions = Integer.parseInt(mode, 8);
				resource.getPermissions().setMode(permissions);
			} catch (final NumberFormatException e) {
				try {
					resource.getPermissions().setMode(mode);
				} catch (final SyntaxException e1) {
					throw new PermissionDeniedException(
							"syntax error for mode attribute in exist:permissions element");
				}
			}
			final String owner = element.getAttribute("owner");
			final org.exist.security.SecurityManager securityMan = broker.getBrokerPool().getSecurityManager();
			
			if (!securityMan.hasAccount(owner))
				{throw new PermissionDeniedException(
						"Failed to change feed owner: user " + owner
								+ " does not exist.");}
			resource.getPermissions().setOwner(owner);
			
			final String group = element.getAttribute("group");
			if (!securityMan.hasGroup(group))
				try {
					securityMan.addGroup(group);
				} catch (final ConfigurationException e) {
					throw new EXistException(e.getMessage(), e);
				}

			parent.removeChild(element);
		}
	}

	protected XmldbURI entryURI(String uuid) {
		return XmldbURI.create(uuid + ".entry.atom");
	}

	public static Element generateMediaEntry(String id, String created,
			String title, String filename, String mimeType)
			throws ParserConfigurationException {
		
		final DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		docFactory.setNamespaceAware(true);
		final Document owner = docFactory.newDocumentBuilder().getDOMImplementation()
				.createDocument(Atom.NAMESPACE_STRING, "entry", null);
		
		final Element entry = owner.getDocumentElement();

		final Element idE = owner.createElementNS(Atom.NAMESPACE_STRING, "id");
		idE.appendChild(owner.createTextNode(id));
		entry.appendChild(idE);

		final Element publishedE = owner.createElementNS(Atom.NAMESPACE_STRING, "published");
		publishedE.appendChild(owner.createTextNode(created));
		entry.appendChild(publishedE);

		final Element updatedE = owner.createElementNS(Atom.NAMESPACE_STRING, "updated");
		updatedE.appendChild(owner.createTextNode(created));
		entry.appendChild(updatedE);

		final Element titleE = owner.createElementNS(Atom.NAMESPACE_STRING, "title");
		titleE.appendChild(owner.createTextNode(title));
		entry.appendChild(titleE);

		Element linkE = owner.createElementNS(Atom.NAMESPACE_STRING, "link");
		linkE.setAttribute("rel", "edit");
		linkE.setAttribute("type", Atom.MIME_TYPE);
		linkE.setAttribute("href", "?id=" + id);
		entry.appendChild(linkE);

		linkE = owner.createElementNS(Atom.NAMESPACE_STRING, "link");
		linkE.setAttribute("rel", "edit-media");
		linkE.setAttribute("type", mimeType);
		linkE.setAttribute("href", filename);
		entry.appendChild(linkE);

		final Element contentE = owner.createElementNS(Atom.NAMESPACE_STRING,
				"content");
		entry.appendChild(contentE);
		contentE.setAttribute("src", filename);
		contentE.setAttribute("type", mimeType);

		return entry;
	}
}