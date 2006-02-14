/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
 *  wolfgang@exist-db.org
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 * 
 *  $Id$
 */
package org.exist.http.webdav.methods;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * @author wolf
 */
public class Put extends AbstractWebDAVMethod {
	
	public Put(BrokerPool pool) {
		super(pool);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.http.webdav.WebDAVMethod#process(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, org.exist.collections.Collection, org.exist.dom.DocumentImpl)
	 */
	public void process(User user, HttpServletRequest request,
			HttpServletResponse response, String path) throws ServletException, IOException {
		File tempFile = saveRequestContent(request);
		String url = tempFile.toURI().toASCIIString();
		String contentType = request.getContentType();
		DBBroker broker = null;
		Collection collection = null;
		boolean collectionLocked = true;
        TransactionManager transact = pool.getTransactionManager();
        Txn txn = transact.beginTransaction();
		try {
			broker = pool.get(user);
            ///TODO : use dedicated function in XmldbURI
			if(path == null)
				path = "";
			if(path.endsWith("/"))
				path = path.substring(0, path.length() - 1);
			int p = path.lastIndexOf("/");
            //TODO : strange test here -pb
			if(p < 1) {
                transact.abort(txn);
				response.sendError(HttpServletResponse.SC_CONFLICT, "No collection specified for PUT");
				return;
			}
			String collectionName = path.substring(0, p);
			path = path.substring(p + 1);
			
			collection = broker.openCollection(collectionName, Lock.WRITE_LOCK);
			if(collection == null) {
                transact.abort(txn);
				response.sendError(HttpServletResponse.SC_CONFLICT, "Parent collection " + collectionName +
				" not found");
				return;
			}
			if(collection.hasChildCollection(path)) {
                transact.abort(txn);
				response.sendError(HttpServletResponse.SC_CONFLICT, "Cannot overwrite an existing collection with a resource");
				return;
			}
            MimeType mime;
			if(contentType == null) {
				mime = MimeTable.getInstance().getContentTypeFor(path);
                if (mime != null)
                    contentType = mime.getName();
			} else {
			    mime = MimeTable.getInstance().getContentType(contentType);
            }
            if (mime == null)
                mime = MimeType.BINARY_TYPE;
			LOG.debug("storing document " + path + "; content-type = " + contentType);
            
			if(mime.isXMLType()) {
				InputSource is = new InputSource(url);
				IndexInfo info = collection.validateXMLResource(txn, broker, path, is);
                info.getDocument().getMetadata().setMimeType(contentType);
				collection.release();
				collectionLocked = false;
				collection.store(txn, broker, info, is, false);
			} else {
				byte[] chunk = new byte[4096];
				ByteArrayOutputStream os = new ByteArrayOutputStream();
				FileInputStream is = new FileInputStream(tempFile);
				int l;
				while((l = is.read(chunk)) > -1) {
					os.write(chunk, 0, l);
				}
				collection.addBinaryResource(txn, broker, path, os.toByteArray(), contentType);
			}
            transact.commit(txn);
		} catch (EXistException e) {
            transact.abort(txn);
			throw new ServletException("Failed to store resource: " + e.getMessage(), e);
		} catch (PermissionDeniedException e) {
            transact.abort(txn);
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
		} catch (TriggerException e) {
            transact.abort(txn);
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
		} catch (SAXException e) {
            transact.abort(txn);
			response.sendError(HttpServletResponse.SC_BAD_REQUEST);
		} catch (LockException e) {
            transact.abort(txn);
			response.sendError(HttpServletResponse.SC_CONFLICT);
		} finally {
			if(collectionLocked && collection != null)
				collection.release();
			pool.release(broker);
		}
        tempFile.delete();
		response.setStatus(HttpServletResponse.SC_CREATED);
	}
	
	private File saveRequestContent(HttpServletRequest request) throws IOException {
		ServletInputStream is = request.getInputStream();
		int len = request.getContentLength();
		// put may send a lot of data, so save it
		// to a temporary file first.
		File tempFile = File.createTempFile("existSRC", ".tmp");
		OutputStream os = new FileOutputStream(tempFile);
		byte[] buffer = new byte[4096];
		int count, l = 0;
		do {
			count = is.read(buffer);
			if (count > 0)
				os.write(buffer, 0, count);
			l += count;
		} while (l < len);
		os.close();
		return tempFile;
	}
}
