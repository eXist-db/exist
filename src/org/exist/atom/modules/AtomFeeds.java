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

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.EXistException;
import org.exist.atom.Atom;
import org.exist.atom.IncomingMessage;
import org.exist.atom.OutgoingMessage;
import org.exist.collections.Collection;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.http.BadRequestException;
import org.exist.http.NotFoundException;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.xacml.AccessContext;
import org.exist.source.URLSource;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.Serializer;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;

import org.xml.sax.SAXException;

/**
 * 
 * @author R. Alexander Milowski
 */
public class AtomFeeds extends AtomModuleBase implements Atom {

	protected final static Logger LOG = LogManager.getLogger(AtomProtocol.class);
	static final String FEED_DOCUMENT_NAME = ".feed.atom";
	static final XmldbURI FEED_DOCUMENT_URI = XmldbURI
			.create(FEED_DOCUMENT_NAME);
	URLSource entryByIdSource;
	URLSource getFeedSource;

	/** Creates a new instance of AtomProtocol */
	public AtomFeeds() {
		entryByIdSource = new URLSource(this.getClass().getResource(
				"entry-by-id.xq"));
		getFeedSource = new URLSource(this.getClass()
				.getResource("get-feed.xq"));
	}

	public void doGet(DBBroker broker, IncomingMessage request,
			OutgoingMessage response) throws BadRequestException,
			PermissionDeniedException, NotFoundException, EXistException {
		handleGet(true, broker, request, response);
	}

	public void doHead(DBBroker broker, IncomingMessage request,
			OutgoingMessage response) throws BadRequestException,
			PermissionDeniedException, NotFoundException, EXistException {
		handleGet(false, broker, request, response);
	}

	protected void handleGet(boolean returnContent, DBBroker broker,
			IncomingMessage request, OutgoingMessage response)
			throws BadRequestException, PermissionDeniedException,
			NotFoundException, EXistException {
		DocumentImpl resource = null;
		final XmldbURI pathUri = XmldbURI.create(request.getPath());
		try {

			resource = broker.getXMLResource(pathUri, Lock.READ_LOCK);

			if (resource == null) {

				String id = request.getParameter("id");
				if (id != null) {
					id = id.trim();
					if (id.length() == 0) {
						id = null;
					}
				}

				// Must be a collection
				final Collection collection = broker.getCollection(pathUri);
				if (collection != null) {
					if (!collection.getPermissionsNoLock().validate(
							broker.getSubject(), Permission.READ)) {
						throw new PermissionDeniedException(
								"Not allowed to read collection");
					}

					final DocumentImpl feedDoc = collection.getDocument(broker,
							FEED_DOCUMENT_URI);
					if (feedDoc == null) {
						throw new BadRequestException("Collection "
								+ request.getPath() + " is not an Atom feed.");
					}

					// Return the collection feed
					// String charset = getContext().getDefaultCharset();
					if (returnContent) {
						if (id == null) {
							response.setStatusCode(200);
							getFeed(broker, request.getPath(), response);
						} else {
							response.setStatusCode(200);
							getEntryById(broker, request.getPath(), id,
									response);
						}
					} else {
						response.setStatusCode(204);
					}

				} else {
					throw new NotFoundException("Resource " + request.getPath()
							+ " not found");
				}

			} else {
				// Do we have permission to read the resource
				if (!resource.getPermissions().validate(broker.getSubject(),
						Permission.READ)) {
					throw new PermissionDeniedException(
							"Not allowed to read resource");
				}

				if (returnContent) {
					response.setStatusCode(200);
					if (resource.getResourceType() == DocumentImpl.BINARY_FILE) {
						response.setContentType(resource.getMetadata()
								.getMimeType());
						try {
							final OutputStream os = response.getOutputStream();
							broker.readBinaryResource(
									(BinaryDocument) resource, os);
							os.flush();
						} catch (final IOException ex) {
							LOG.fatal(
									"Cannot read resource " + request.getPath(),
									ex);
							throw new EXistException(
									"I/O error on read of resource "
											+ request.getPath(), ex);
						}
					} else {
						// xml resource
						final Serializer serializer = broker.getSerializer();
						serializer.reset();

						final String charset = getContext().getDefaultCharset();
						// Serialize the document
						try {
							response.setContentType(resource.getMetadata()
									.getMimeType() + "; charset=" + charset);
							final Writer w = new OutputStreamWriter(
									response.getOutputStream(), charset);
							serializer.serialize(resource, w);
							w.flush();
							w.close();
						} catch (final IOException ex) {
							LOG.fatal(
									"Cannot read resource " + request.getPath(),
									ex);
							throw new EXistException(
									"I/O error on read of resource "
											+ request.getPath(), ex);
						} catch (final SAXException saxe) {
							LOG.warn(saxe);
							throw new BadRequestException(
									"Error while serializing XML: "
											+ saxe.getMessage());
						}
					}

				} else {
					response.setStatusCode(204);
				}
			}

		} finally {
			if (resource != null) {
				resource.getUpdateLock().release(Lock.READ_LOCK);
			}
		}
	}

	public void getEntryById(DBBroker broker, String path, String id,
			OutgoingMessage response) throws EXistException,
			BadRequestException, PermissionDeniedException {
		final XQuery xquery = broker.getXQueryService();
		CompiledXQuery feedQuery = xquery.getXQueryPool().borrowCompiledXQuery(
				broker, entryByIdSource);

		XQueryContext context;
		if (feedQuery == null) {
			context = xquery.newContext(AccessContext.REST);
			try {
				feedQuery = xquery.compile(context, entryByIdSource);
			} catch (final XPathException ex) {
				throw new EXistException("Cannot compile xquery "
						+ entryByIdSource.getURL(), ex);
			} catch (final IOException ex) {
				throw new EXistException(
						"I/O exception while compiling xquery "
								+ entryByIdSource.getURL(), ex);
			}
		} else {
			context = feedQuery.getContext();
		}
		context.setStaticallyKnownDocuments(new XmldbURI[] { XmldbURI.create(
				path).append(AtomProtocol.FEED_DOCUMENT_NAME) });

		try {
			context.declareVariable("id", id);
			final Sequence resultSequence = xquery.execute(feedQuery, null);
			if (resultSequence.isEmpty()) {
				throw new BadRequestException("No topic was found.");
			}

			final String charset = getContext().getDefaultCharset();
			response.setContentType("application/atom+xml; charset=" + charset);
			final Serializer serializer = broker.getSerializer();
			serializer.reset();
			try {
				final Writer w = new OutputStreamWriter(response.getOutputStream(), charset);
				final SAXSerializer sax = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);
				final Properties outputProperties = new Properties();
				sax.setOutput(w, outputProperties);
				serializer.setProperties(outputProperties);
				serializer.setSAXHandlers(sax, sax);

				serializer.toSAX(resultSequence, 1, 1, false, false);

				SerializerPool.getInstance().returnObject(sax);
				w.flush();
				w.close();
			} catch (final IOException ex) {
				LOG.fatal("Cannot read resource " + path, ex);
				throw new EXistException("I/O error on read of resource "
						+ path, ex);
			} catch (final SAXException saxe) {
				LOG.warn(saxe);
				throw new BadRequestException("Error while serializing XML: "
						+ saxe.getMessage());
			}
			resultSequence.itemAt(0);

		} catch (final XPathException ex) {
			throw new EXistException("Cannot execute xquery "
					+ entryByIdSource.getURL(), ex);

		} finally {
			xquery.getXQueryPool().returnCompiledXQuery(entryByIdSource, feedQuery);
		}

	}

	public void getFeed(DBBroker broker, String path, OutgoingMessage response)
			throws EXistException, BadRequestException,
			PermissionDeniedException {
		
		final XQuery xquery = broker.getXQueryService();
		CompiledXQuery feedQuery = xquery.getXQueryPool().borrowCompiledXQuery(
				broker, getFeedSource);

		XQueryContext context;
		if (feedQuery == null) {
			context = xquery.newContext(AccessContext.REST);
			try {
				feedQuery = xquery.compile(context, getFeedSource);
			} catch (final XPathException ex) {
				throw new EXistException("Cannot compile xquery "
						+ getFeedSource.getURL(), ex);
			} catch (final IOException ex) {
				throw new EXistException(
						"I/O exception while compiling xquery "
								+ getFeedSource.getURL(), ex);
			}
		} else {
			context = feedQuery.getContext();
		}
		context.setStaticallyKnownDocuments(
			new XmldbURI[] { 
					XmldbURI.create(path).append(AtomProtocol.FEED_DOCUMENT_NAME) 
			}
		);

		try {
			final Sequence resultSequence = xquery.execute(feedQuery, null);
			if (resultSequence.isEmpty()) {
				throw new BadRequestException("No feed was found.");
			}

			final String charset = getContext().getDefaultCharset();
			response.setContentType("application/atom+xml; charset=" + charset);
			final Serializer serializer = broker.getSerializer();
			serializer.reset();

			try {
				final Writer w = new OutputStreamWriter(response.getOutputStream(), charset);
				final SAXSerializer sax = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);
				final Properties outputProperties = new Properties();
				sax.setOutput(w, outputProperties);
				serializer.setProperties(outputProperties);
				serializer.setSAXHandlers(sax, sax);

				serializer.toSAX(resultSequence, 1, 1, false, false);

				SerializerPool.getInstance().returnObject(sax);
				w.flush();
				w.close();

			} catch (final IOException ex) {
				LOG.fatal("Cannot read resource " + path, ex);
				throw new EXistException("I/O error on read of resource " + path, ex);
			} catch (final SAXException saxe) {
				LOG.warn(saxe);
				throw new BadRequestException("Error while serializing XML: " + saxe.getMessage());
			}
			resultSequence.itemAt(0);

		} catch (final XPathException ex) {
			throw new EXistException("Cannot execute xquery " + getFeedSource.getURL(), ex);

		} finally {
			xquery.getXQueryPool().returnCompiledXQuery(getFeedSource, feedQuery);
		}
	}
}