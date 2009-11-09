/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2005-2009 The eXist Project
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
package org.exist.xquery.util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.exist.dom.DocumentImpl;
import org.exist.dom.NodeProxy;
import org.exist.memtree.SAXAdapter;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.exist.source.Source;
import org.exist.source.SourceFactory;
import org.exist.source.URLSource;
import org.exist.util.XMLReaderPool;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 * Utilities for XPath doc related functions
 * 
 * @author wolf
 * @author Pierrick Brihaye <pierrick.brihaye@free.fr>
 */
//TODO : many more improvements to handle efficiently any URI
public class DocUtils {	
	
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

	private static Sequence getDocumentByPath(XQueryContext context, String path) throws XPathException, PermissionDeniedException
	{	 
		Sequence document = Sequence.EMPTY_SEQUENCE;	

		if(path.matches("^[a-z]+:.*") && !path.startsWith("xmldb:"))
		{
            XMLReader reader = null;
			/* URL */
            try {
                Source source = SourceFactory.getSource(context.getBroker(), "", path, false);
                InputStream istream = source.getInputStream();
                if (source instanceof URLSource) {
                    int responseCode = ((URLSource) source).getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                        // Special case: '404'
                        return Sequence.EMPTY_SEQUENCE;
                    } else if (responseCode != HttpURLConnection.HTTP_OK) {
                        throw new PermissionDeniedException("Server returned code " + responseCode);
                    }
                }

                //TODO : process pseudo-protocols URLs more efficiently.
                org.exist.memtree.DocumentImpl memtreeDoc = null;
                // we use eXist's in-memory DOM implementation
                reader = context.getBroker().getBrokerPool().getParserPool().borrowXMLReader();
                //TODO : we should be able to cope with context.getBaseURI()
                InputSource src = new InputSource(istream);
                SAXAdapter adapter = new SAXAdapter();
                reader.setContentHandler(adapter);
                reader.parse(src);
                Document doc = adapter.getDocument();
                memtreeDoc = (org.exist.memtree.DocumentImpl)doc;
                memtreeDoc.setContext(context);
                memtreeDoc.setDocumentURI(path);
                document = memtreeDoc;

            } catch(ConnectException e) {
                // prevent long stacktraces
                throw new XPathException(e.getMessage()+ " ("+path+")");

            } catch(MalformedURLException e) {
                throw new XPathException(e.getMessage(), e);

            } catch(SAXException e) {
                throw new XPathException(e.getMessage(), e);
            }
            catch(IOException e) {
                // Special case: FileNotFoundException
                if(e instanceof FileNotFoundException)
                {
                    return Sequence.EMPTY_SEQUENCE;
                }
                else
                {
                    throw new XPathException(e.getMessage(), e);
                }
            } finally {
                if (reader != null)
                    context.getBroker().getBrokerPool().getParserPool().returnXMLReader(reader);
            }
		}
		else
		{
			/* Database documents */

			// check if the loaded documents should remain locked
			boolean lockOnLoad = context.lockDocumentsOnLoad();
            int lockType = lockOnLoad ? Lock.WRITE_LOCK : Lock.READ_LOCK;
			DocumentImpl doc = null;
			try
			{
				XmldbURI pathUri = XmldbURI.xmldbUriFor(path, false);
				// relative collection Path: add the current base URI
				pathUri = context.getBaseURI().toXmldbURI().resolveCollectionPath(pathUri);
				// try to open the document and acquire a lock
				doc = context.getBroker().getXMLResource(pathUri, lockType);
				if(doc != null)
				{
					if(!doc.getPermissions().validate(context.getUser(), Permission.READ))
					{
						doc.getUpdateLock().release(lockType);
						throw new PermissionDeniedException("Insufficient privileges to read resource " + path);
					}
					
                    if(doc.getResourceType() == DocumentImpl.BINARY_FILE)
                    {
                        throw new XPathException("Document is a binary resource, not an XML document. Please consider using the function util:binary-doc() to retrieve a reference to it.");
                    }

                    if(lockOnLoad)
                    {
						// add the document to the list of locked documents
						context.addLockedDocument(doc);
					}
					document = new NodeProxy(doc);
				}
			}
			catch(PermissionDeniedException e)
			{
				throw e;
			}
			catch(URISyntaxException e)
			{
				throw new XPathException(e);
			}
			finally
			{
				// release all locks unless lockOnLoad is true
				if(!lockOnLoad && doc != null)
					doc.getUpdateLock().release(lockType);
			}
		}	 	  
		return document;
	}
}
