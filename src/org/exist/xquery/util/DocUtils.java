/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
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
package org.exist.xquery.util;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.exist.dom.DocumentImpl;
import org.exist.dom.NodeProxy;
import org.exist.memtree.SAXAdapter;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock;
import org.exist.util.LockException;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 * Utilities for XPath doc related functions
 * 
 * @author wolf
 * @author Pierrick Brihaye<pierrick.brihaye@free.fr>
 */
//TODO : many more improvements to handle efficiently any URI
public class DocUtils {
	
	//TODO : improve caching mechanism
	//private static Sequence currentDocument = null;
//	private static NodeProxy cachedNode = null;
//	private static String cachedPath = null;	
	
	public static Sequence getDocument(XQueryContext context, String path) throws XPathException, PermissionDeniedException {
		return getDocumentByPath(context, path);
	}
	
	public static boolean isDocumentAvailable(XQueryContext context, String path) throws XPathException {
		try {
			Sequence seq = getDocumentByPath(context, path);
			return (seq != null && seq.effectiveBooleanValue());
		}
		catch (PermissionDeniedException e) {
			return false;
		}
		
	}	

	private static Sequence getDocumentByPath(XQueryContext context, String path) throws XPathException, PermissionDeniedException {	 
		Sequence document = Sequence.EMPTY_SEQUENCE;	
	  	//URLs
		if (path.matches("^[a-z]+://.*")) {
			try {
				//Basic tests on the URL				
				URL url = new URL(path);
				URLConnection con = url.openConnection();
				if (con instanceof HttpURLConnection) {
					HttpURLConnection httpConnection = (HttpURLConnection)con;
					if (httpConnection.getResponseCode() != HttpURLConnection.HTTP_OK)
						//TODO : return another type 
						throw new PermissionDeniedException("Server returned code " + httpConnection.getResponseCode());	
				}
				
				//TODO : process pseudo-protocols URLs more efficiently.
				org.exist.memtree.DocumentImpl memtreeDoc = null;
				// we use eXist's in-memory DOM implementation
				SAXParserFactory factory = SAXParserFactory.newInstance();
				factory.setNamespaceAware(true);				
				//TODO : we should be able to cope with context.getBaseURI()				
				InputSource src = new InputSource(con.getInputStream());
				SAXParser parser = factory.newSAXParser();
				XMLReader reader = parser.getXMLReader();
				SAXAdapter adapter = new SAXAdapter();
				reader.setContentHandler(adapter);
				reader.parse(src);					
				Document doc = adapter.getDocument();
				memtreeDoc = (org.exist.memtree.DocumentImpl)doc;
				memtreeDoc.setContext(context);
				document = memtreeDoc;
			} catch (MalformedURLException e) {
				throw new XPathException(e.getMessage(), e);					
			} catch (ParserConfigurationException e) {				
				throw new XPathException(e.getMessage(), e);		
			} catch (SAXException e) {
				throw new XPathException(e.getMessage(), e);	
			} catch (IOException e) {
				throw new XPathException(e.getMessage(), e);	
			}			
		//Database documents
		} else {
			// relative collection Path: add the current base URI
			//TODO : use another strategy
			if (path.charAt(0) != '/')
				path = context.getBaseURI() + "/" + path;

			// check if the loaded documents should remain locked
			boolean lockOnLoad = context.lockDocumentsOnLoad();
			Lock dlock = null;

			// if the expression occurs in a nested context, we might have cached the
			// document set
//			if (path.equals(cachedPath) && cachedNode != null) {
//				dlock = cachedNode.getDocument().getUpdateLock();
//				try {
//					// wait for pending updates by acquiring a lock
//					dlock.acquire(Lock.READ_LOCK);
//					currentDocument =  cachedNode;
//				} catch (LockException e) {					
//					throw new XPathException("Failed to acquire lock on document " + path, e);
//				} finally {
//					dlock.release(Lock.READ_LOCK);
//				}
//			}

			DocumentImpl doc = null;
			try {
				// try to open the document and acquire a lock
				doc = (DocumentImpl) context.getBroker().getXMLResource(path, Lock.READ_LOCK);
				if (doc != null) {				
					if (!doc.getPermissions().validate(context.getUser(), Permission.READ)) {
						doc.getUpdateLock().release(Lock.READ_LOCK);
						throw new PermissionDeniedException("Insufficient privileges to read resource " + path);
					}
					
					if (lockOnLoad) {
						// add the document to the list of locked documents
						context.getLockedDocuments().add(doc);
					}
					document = new NodeProxy(doc);
				}
			} catch (PermissionDeniedException e) {
				throw e;
			} finally {
				// release all locks unless lockOnLoad is true
				if (!lockOnLoad && doc != null)
					doc.getUpdateLock().release(Lock.READ_LOCK);
			}
		}	 	  
		return document;
	}
		

}