
/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU Library General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 *  $Id$
 */
package org.exist.xmlrpc;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Stack;
import java.util.Vector;

import javax.xml.transform.OutputKeys;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.util.Configuration;
import org.exist.util.hashtable.Int2ObjectHashMap;
import org.xml.sax.SAXException;

/**
 *  Handler class for XMLRPC calls. <p>
 *
 *  To allow calls by many parallel users, RpcServer does not directly execute
 *  calls. Instead it delegates all calls to instances of the inner class
 *  RpcConnection, which run in their own thread.</p> <p>
 *
 *  On startup, RpcServer creates a pool of RpcConnections. For every call the
 *  server first gets a RpcConnection object from the pool, executes the call
 *  and releases the RpcConnection.</p> <p>
 *
 *  If the pool's maximum of concurrent connections (MAX_CONNECT) is reached,
 *  RpcServer will block until a connection is available.</p> <p>
 *
 *  All methods returning XML data will return UTF-8 encoded strings, unless an
 *  encoding is specified. Methods that allow to set the encoding will always
 *  return byte[] instead of string. byte[]-values are handled as binary data
 *  and are automatically BASE64-encoded by the XMLRPC engine. This way the
 *  correct character encoding is preserved during transport.</p>
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    18. Mai 2002
 */
public class RpcServer implements RpcAPI {
	private static Logger LOG = Logger.getLogger(RpcServer.class);

	protected final static int MIN_CONNECT = 1;
	protected final static int MAX_CONNECT = 10;

	protected ConnectionPool pool;

	/**
	 *  Constructor for the RpcServer object
	 *
	 *@param  conf                Description of the Parameter
	 *@exception  EXistException  Description of the Exception
	 */
	public RpcServer(Configuration conf) throws EXistException {
		pool = new ConnectionPool(MIN_CONNECT, MAX_CONNECT, conf);
	}

	public boolean createCollection(User user, String name)
		throws EXistException, PermissionDeniedException {
		RpcConnection con = pool.get();
		try {
			con.createCollection(user, name);
			return true;
		} catch (Exception e) {
			handleException(e);
			return false;
		} finally {
			pool.release(con);
		}
	}

	public String createId(User user, String collection)
		throws EXistException, PermissionDeniedException {
		RpcConnection con = pool.get();
		try {
			return con.createId(user, collection);
		} finally {
			pool.release(con);
		}
	}

	public int executeQuery(User user, String xpath)
		throws EXistException, PermissionDeniedException {
		RpcConnection con = pool.get();
		try {
			return con.executeQuery(user, xpath);
		} catch (Exception e) {
			handleException(e);
			return -1;
		} finally {
			pool.release(con);
		}
	}

	public int executeQuery(
		User user,
		byte[] xpath,
		String encoding)
		throws EXistException, PermissionDeniedException {
		String xpathString = null;
		if (encoding != null)
			try {
				xpathString = new String(xpath, encoding);
			} catch (UnsupportedEncodingException e) {
			}

		if (xpathString == null)
			xpathString = new String(xpath);

		LOG.debug("query: " + xpathString);
		return executeQuery(user, xpathString);
	}

	public int executeQuery(User user, byte[] xpath)
		throws EXistException, PermissionDeniedException {
		return executeQuery(user, xpath, null);
	}

	public Hashtable getCollectionDesc(User user, String rootCollection)
		throws EXistException, PermissionDeniedException {
		RpcConnection con = pool.get();
		try {
			return con.getCollectionDesc(user, rootCollection);
		} catch (Exception e) {
			handleException(e);
			throw new EXistException("collection " + rootCollection + " not found!");
		} finally {
			pool.release(con);
		}
	}

	public byte[] getDocument(User user, String name, String encoding, int prettyPrint)
		throws EXistException, PermissionDeniedException {
		RpcConnection con = pool.get();
		try {
			Hashtable parametri = new Hashtable();

			if (prettyPrint > 0) {
				parametri.put(OutputKeys.INDENT, "yes");
			} else {
				parametri.put(OutputKeys.INDENT, "no");
			}

			parametri.put(OutputKeys.ENCODING, encoding);

			//	String xml = con.getDocument(user, name, (prettyPrint > 0), encoding, null);
			String xml = con.getDocument(user, name, parametri);

			if (xml == null)
				throw new EXistException("document " + name + " not found!");
			try {
				return xml.getBytes(encoding);
			} catch (UnsupportedEncodingException uee) {
				return xml.getBytes();
			}
		} catch (Exception e) {
			handleException(e);
			return null;
		} finally {
			pool.release(con);
		}
	}

	public byte[] getDocument(
		User user,
		String name,
		String encoding,
		int prettyPrint,
		String stylesheet)
		throws EXistException, PermissionDeniedException {
		RpcConnection con = pool.get();
		try {

			Hashtable parametri = new Hashtable();

			if (prettyPrint > 0) {
				parametri.put(OutputKeys.INDENT, "yes");
			} else {
				parametri.put(OutputKeys.INDENT, "no");
			}

			if (stylesheet != null) {
				parametri.put(EXistOutputKeys.STYLESHEET, stylesheet);
			}

			parametri.put(OutputKeys.ENCODING, encoding);

			//String xml = con.getDocument(user, name, (prettyPrint > 0), encoding, stylesheet);
			String xml = con.getDocument(user, name, parametri);
			if (xml == null)
				throw new EXistException("document " + name + " not found!");
			try {
				return xml.getBytes(encoding);
			} catch (UnsupportedEncodingException uee) {
				return xml.getBytes();
			}
		} catch (Exception e) {
			handleException(e);
			return null;
		} finally {
			pool.release(con);
		}
	}

	public String getDocumentAsString(User user, String name, int prettyPrint)
		throws EXistException, PermissionDeniedException {
		return getDocumentAsString(user, name, prettyPrint, null);
	}

	public String getDocumentAsString(
		User user,
		String name,
		int prettyPrint,
		String stylesheet)
		throws EXistException, PermissionDeniedException {
		RpcConnection con = pool.get();
		try {
			//String xml = con.getDocument(user, name, (prettyPrint > 0), "UTF-8", stylesheet);
			Hashtable parametri = new Hashtable();

			if (prettyPrint > 0) {
				parametri.put(OutputKeys.INDENT, "yes");
			} else {
				parametri.put(OutputKeys.INDENT, "no");
			}

			if (stylesheet != null) {
				parametri.put(EXistOutputKeys.STYLESHEET, stylesheet);
			}

			String xml = con.getDocument(user, name, parametri);

			if (xml == null)
				throw new EXistException("document " + name + " not found!");
			else
				return xml;
		} catch (Exception e) {
			handleException(e);
			return null;
		} finally {
			pool.release(con);
		}
	}

	public byte[] getBinaryResource(User user, String name)
		throws EXistException, PermissionDeniedException {
		RpcConnection con = pool.get();
		try {
			return con.getBinaryResource(user, name);
		} catch(Exception e) {
			handleException(e);
			return null;
		} finally {
			pool.release(con);
		}
	}
	
	/**
	 * Retrieve a document. The document data is returned as a string.
	 */
	public String getDocumentAsString(User user, String name, Hashtable parameters)
		throws EXistException, PermissionDeniedException {
		RpcConnection con = pool.get();
		try {
			return con.getDocument(user, name, parameters);
		} catch (Exception e) {
			handleException(e);
			return null;
		} finally {
			pool.release(con);
		}
	}
	
	public byte[] getDocument(User user, String name, Hashtable parametri)
		throws EXistException, PermissionDeniedException {

		String encoding = "UTF-8";

		if (((String) parametri.get("encoding")) == null) {
			encoding = "UTF-8";
		} else {
			encoding = (String) parametri.get("encoding");
		}
		RpcConnection con = pool.get();
		try {
			String xml = con.getDocument(user, name, parametri);
			if (xml == null)
				throw new EXistException("document " + name + " not found!");
			try {
				return xml.getBytes(encoding);
			} catch (UnsupportedEncodingException uee) {
				return xml.getBytes();
			}
		} catch (Exception e) {
			handleException(e);
			return null;
		} finally {
			pool.release(con);
		}
	}

	public Vector getDocumentListing(User user)
		throws EXistException, PermissionDeniedException {
		RpcConnection con = pool.get();
		try {
			Vector result = con.getDocumentListing(user);
			return result;
		} catch (Exception e) {
			handleException(e);
			return null;
		} finally {
			pool.release(con);
		}
	}

	public Vector getDocumentListing(User user, String collection)
		throws EXistException, PermissionDeniedException {
		RpcConnection con = pool.get();
		try {
			Vector result = con.getDocumentListing(user, collection);
			return result;
		} catch (Exception e) {
			handleException(e);
			return null;
		} finally {
			pool.release(con);
		}
	}

	public Hashtable listDocumentPermissions(User user, String name)
		throws EXistException, PermissionDeniedException {
		RpcConnection con = pool.get();
		try {
			return con.listDocumentPermissions(user, name);
		} finally {
			pool.release(con);
		}
	}

	public Hashtable listCollectionPermissions(User user, String name)
		throws EXistException, PermissionDeniedException {
		RpcConnection con = pool.get();
		try {
			return con.listCollectionPermissions(user, name);
		} catch (Exception e) {
			handleException(e);
			return null;
		} finally {
			pool.release(con);
		}
	}

	public int getHits(User user, int resultId)
		throws EXistException, PermissionDeniedException {
		RpcConnection con = pool.get();
		try {
			return con.getHits(user, resultId);
		} finally {
			pool.release(con);
		}
	}

	public Hashtable getPermissions(User user, String docName)
		throws EXistException, PermissionDeniedException {
		RpcConnection con = pool.get();
		try {
			return con.getPermissions(user, docName);
		} catch (Exception e) {
			handleException(e);
			return null;
		} finally {
			pool.release(con);
		}
	}

	public Date getCreationDate(User user, String collectionName)
		throws PermissionDeniedException, EXistException {
		RpcConnection con = pool.get();
		try {
			return con.getCreationDate(user, collectionName);
		} catch (Exception e) {
			handleException(e);
			return null;
		} finally {
			pool.release(con);
		}
	}

	public Vector getTimestamps(User user, String documentName)
		throws PermissionDeniedException, EXistException {
		RpcConnection con = pool.get();
		try {
			return con.getTimestamps(user, documentName);
		} catch (Exception e) {
			handleException(e);
			return null;
		} finally {
			pool.release(con);
		}
	}

	/**
	 *  Gets the user attribute of the RpcServer object
	 *
	 *@param  user                           Description of the Parameter
	 *@param  name                           Description of the Parameter
	 *@return                                The user value
	 *@exception  EXistException             Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	public Hashtable getUser(User user, String name)
		throws EXistException, PermissionDeniedException {
		RpcConnection con = null;
		try {
			con = pool.get();
			return con.getUser(user, name);
		} finally {
			pool.release(con);
		}
	}

	public Vector getUsers(User user) throws EXistException, PermissionDeniedException {
		RpcConnection con = null;
		try {
			con = pool.get();
			return con.getUsers(user);
		} finally {
			pool.release(con);
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xmlrpc.RpcAPI#getGroups(org.exist.security.User)
	 */
	public Vector getGroups(User user) throws EXistException, PermissionDeniedException {
		RpcConnection con = null;
		try {
			con = pool.get();
			return con.getGroups(user);
		} finally {
			pool.release(con);
		}
	}

	public Vector getIndexedElements(User user, String collectionName, boolean inclusive)
		throws EXistException, PermissionDeniedException {
		RpcConnection con = null;
		try {
			con = pool.get();
			return con.getIndexedElements(user, collectionName, inclusive);
		} finally {
			pool.release(con);
		}
	}

	public Vector scanIndexTerms(
		User user,
		String collectionName,
		String start,
		String end,
		boolean inclusive)
		throws PermissionDeniedException, EXistException {
		RpcConnection con = null;
		try {
			con = pool.get();
			return con.scanIndexTerms(user, collectionName, start, end, inclusive);
		} finally {
			pool.release(con);
		}
	}

	private void handleException(Exception e)
		throws EXistException, PermissionDeniedException {
		LOG.debug(e.getMessage(), e);
		if (e instanceof EXistException)
			throw (EXistException) e;
		else if (e instanceof PermissionDeniedException)
			throw (PermissionDeniedException) e;
		else {
			System.out.println(e.getClass().getName());
			throw new EXistException(e);
		}
	}

	/**
	 *  does a document called <code>name</code> exist in the repository?
	 *
	 *@param  name                           Description of the Parameter
	 *@param  user                           Description of the Parameter
	 *@return                                Description of the Return Value
	 *@exception  EXistException             Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	public boolean hasDocument(User user, String name)
		throws EXistException, PermissionDeniedException {
		RpcConnection con = pool.get();
		try {
			return con.hasDocument(user, name);
		} catch (Exception e) {
			handleException(e);
			return false;
		} finally {
			pool.release(con);
		}
	}

	/**
	 *  parse an XML document and store it into the database. The document will
	 *  later be identified by <code>docName</code>. Some xmlrpc clients seem to
	 *  have problems with character encodings when sending xml content. To
	 *  avoid this, parse() accepts the xml document content as byte[].
	 *
	 *@param  xmlData                        the document's XML content as UTF-8
	 *      encoded array of bytes.
	 *@param  docName                        the document's name
	 *@param  user                           Description of the Parameter
	 *@return                                Description of the Return Value
	 *@exception  EXistException             Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	public boolean parse(User user, byte[] xmlData, String docName)
		throws EXistException, PermissionDeniedException {
		return parse(user, xmlData, docName, 0);
	}
	
	public boolean parse(User user, byte[] xmlData, String docName, int overwrite)
		throws EXistException, PermissionDeniedException {
		// some clients (Perl) encode strings with a \0 at the end.
		// remove it ...
		if (xmlData[xmlData.length - 1] == 0) {
			byte[] temp = new byte[xmlData.length - 1];
			System.arraycopy(xmlData, 0, temp, 0, xmlData.length - 1);
			xmlData = temp;
		}
		RpcConnection con = pool.get();
		try {
			return con.parse(user, xmlData, docName, (overwrite != 0));
		} catch (Exception e) {
			handleException(e);
			return false;
		} finally {
			con.synchronize();
			pool.release(con);
		}
	}

	public boolean parse(User user, String xml, String docName, int overwrite)
		throws EXistException, PermissionDeniedException {
		RpcConnection con = pool.get();
		try {
			return con.parse(user, xml.getBytes("UTF-8"), docName, (overwrite != 0));
		} catch (Exception e) {
			handleException(e);
			return false;
		} finally {
			con.synchronize();
			pool.release(con);
		}
	}

	/**
	 * Parse a file previously uploaded with upload.
	 * 
	 * The temporary file will be removed.
	 * 
	 * @param user
	 * @param localFile
	 * @throws EXistException
	 * @throws IOException
	 */
	public boolean parseLocal(
		User user,
		String localFile,
		String docName,
		boolean replace)
		throws EXistException, PermissionDeniedException, SAXException {
		RpcConnection con = pool.get();
		try {
			return con.parseLocal(user, localFile, docName, replace);
		} catch (Exception e) {
			handleException(e);
			return false;
		} finally {
			con.synchronize();
			pool.release(con);
		}
	}

	public String upload(User user, byte[] data, int length)
		throws EXistException, PermissionDeniedException {
		return upload(user, null, data, length);
	}

	public String upload(User user, String file, byte[] data, int length)
		throws EXistException, PermissionDeniedException {
		RpcConnection con = pool.get();
		try {
			return con.upload(user, data, length, file);
		} catch (Exception e) {
			handleException(e);
			return null;
		} finally {
			con.synchronize();
			pool.release(con);
		}
	}

	public boolean parse(User user, String xml, String docName)
		throws EXistException, PermissionDeniedException {
		return parse(user, xml, docName, 0);
	}

	public boolean storeBinary(User user, byte[] data, String docName, boolean replace)
	throws EXistException, PermissionDeniedException {
		RpcConnection con = pool.get();
		try {
			return con.storeBinary(user, data, docName, replace);
		} catch(Exception e) {
			handleException(e);
			return false;
		} finally {
			con.synchronize();
			pool.release(con);
		}
	}
	
	public Hashtable queryP(User user, byte[] xpath, Hashtable parameters)
		throws EXistException, PermissionDeniedException {
		return queryP(user, xpath, null, null, parameters);
	}
	
	public Hashtable queryP(
		User user,
		byte[] xpath,
		String docName,
		String s_id,
		Hashtable parameters)
		throws EXistException, PermissionDeniedException {
		String xpathString = null;
		try {
			xpathString = new String(xpath, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new EXistException("failed to decode xpath expression");
		}

		// some clients (Perl) encode strings with a \0 at the end.
		// remove it ...
		if (xpathString.charAt(xpathString.length() - 1) == 0x0)
			xpathString = xpathString.substring(0, xpathString.length() - 1);
		RpcConnection con = pool.get();
		try {
			return con.queryP(user, xpathString, docName, s_id, parameters);
		} catch (Exception e) {
			handleException(e);
			return null;
		} finally {
			pool.release(con);
		}
	}

	public boolean releaseQueryResult(User user, int handle) {
		RpcConnection con = pool.get();
		try {
			con.releaseQueryResult(handle);
		} finally {
			pool.release(con);
		}
		return true;
	}

	/**
	 *  execute XPath query and return howmany nodes from the result set,
	 *  starting at position <code>start</code>. If <code>prettyPrint</code> is
	 *  set to >0 (true), results are pretty printed.
	 *
	 */
	public String query(
		User user,
		String xpath,
		int howmany,
		int start,
		Hashtable parameters)
		throws EXistException, PermissionDeniedException {
		RpcConnection con = pool.get();
		String result = null;
		try {
			result =
				con.query(
					user,
					xpath,
					howmany,
					start,
					parameters);
			return result;
		} catch (Exception e) {
			handleException(e);
			return null;
		} finally {
			pool.release(con);
		}
	}

	public Hashtable querySummary(User user, int resultId)
		throws EXistException, PermissionDeniedException {
		RpcConnection con = pool.get();
		try {
			return con.summary(user, resultId);
		} finally {
			pool.release(con);
		}
	}

	/**
	 *  execute XPath query and return a summary of hits per document and hits
	 *  per doctype. This method returns a struct with the following fields:
	 *
	 *  <tableborder="1">
	 *
	 *    <tr>
	 *
	 *      <td>
	 *        "queryTime"
	 *      </td>
	 *
	 *      <td>
	 *        int
	 *      </td>
	 *
	 *    </tr>
	 *
	 *    <tr>
	 *
	 *      <td>
	 *        "hits"
	 *      </td>
	 *
	 *      <td>
	 *        int
	 *      </td>
	 *
	 *    </tr>
	 *
	 *    <tr>
	 *
	 *      <td>
	 *        "documents"
	 *      </td>
	 *
	 *      <td>
	 *        array of array: Object[][3]
	 *      </td>
	 *
	 *    </tr>
	 *
	 *    <tr>
	 *
	 *      <td>
	 *        "doctypes"
	 *      </td>
	 *
	 *      <td>
	 *        array of array: Object[][2]
	 *      </td>
	 *
	 *    </tr>
	 *
	 *  </table>
	 *  Documents and doctypes represent tables where each row describes one
	 *  document or doctype for which hits were found. Each document entry has
	 *  the following structure: docId (int), docName (string), hits (int) The
	 *  doctype entry has this structure: doctypeName (string), hits (int)
	 *
	 *@param  xpath                          Description of the Parameter
	 *@param  user                           Description of the Parameter
	 *@return                                Description of the Return Value
	 *@exception  EXistException             Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	public Hashtable querySummary(User user, String xpath)
		throws EXistException, PermissionDeniedException {
		RpcConnection con = pool.get();
		try {
			return con.summary(user, xpath);
		} catch (Exception e) {
			handleException(e);
			throw new EXistException(e);
		} finally {
			pool.release(con);
		}
	}

	/**
	 *  remove a document from the repository.
	 *
	 *@param  docName                        Description of the Parameter
	 *@param  user                           Description of the Parameter
	 *@return                                Description of the Return Value
	 *@exception  EXistException             Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	public boolean remove(User user, String docName)
		throws EXistException, PermissionDeniedException {
		RpcConnection con = pool.get();
		try {
			con.remove(user, docName);
			return true;
		} catch (Exception e) {
			handleException(e);
			return false;
		} finally {
			con.synchronize();
			pool.release(con);
		}
	}

	public boolean removeCollection(User user, String name)
		throws EXistException, PermissionDeniedException {
		RpcConnection con = pool.get();
		try {
			return con.removeCollection(user, name);
		} catch (Exception e) {
			handleException(e);
			return false;
		} finally {
			con.synchronize();
			pool.release(con);
		}
	}

	public boolean removeUser(User user, String name)
		throws EXistException, PermissionDeniedException {
		RpcConnection con = pool.get();
		try {
			return con.removeUser(user, name);
		} finally {
			pool.release(con);
		}
	}

	/**
	 *  retrieve a single node from a document. The node is identified by it's
	 *  internal id.
	 *
	 *@param  doc                            the document containing the node
	 *@param  id                             the node's internal id
	 *@param  user                           Description of the Parameter
	 *@return                                Description of the Return Value
	 *@exception  EXistException             Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	public byte[] retrieve(User user, String doc, String id)
		throws EXistException, PermissionDeniedException {
		return retrieve(user, doc, id, null);
	}

	/**
	 *  retrieve a single node from a document. The node is identified by it's
	 *  internal id.
	 *
	 *@param  doc                            the document containing the node
	 *@param  id                             the node's internal id
	 *@param  prettyPrint                    result is pretty printed if >0
	 *@param  encoding                       character encoding to use
	 *@param  user                           Description of the Parameter
	 *@return                                Description of the Return Value
	 *@exception  EXistException             Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	public byte[] retrieve(
		User user,
		String doc,
		String id,
		Hashtable parameters)
		throws EXistException, PermissionDeniedException {
		RpcConnection con = pool.get();
		String xml = null;
		try {
			xml = con.retrieve(user, doc, id, parameters);
			try {
				String encoding = (String)parameters.get(OutputKeys.ENCODING);
				if(encoding == null)
					encoding = "UTF-8";
				return xml.getBytes(encoding);
			} catch (UnsupportedEncodingException uee) {
				return xml.getBytes();
			}
		} catch (Exception e) {
			handleException(e);
			return null;
		} finally {
			pool.release(con);
		}
	}

	public String retrieveAsString(User user, String doc, String id, Hashtable parameters)
		throws EXistException, PermissionDeniedException {
		RpcConnection con = pool.get();
		try {
			return con.retrieve(user, doc, id, parameters);
		} catch (Exception e) {
			handleException(e);
			return null;
		} finally {
			pool.release(con);
		}
	}


	public byte[] retrieve(
		User user,
		int resultId,
		int num,
		Hashtable parameters)
		throws EXistException, PermissionDeniedException {
		RpcConnection con = pool.get();
		try {
			String xml = con.retrieve(user, resultId, num, parameters);
			String encoding = (String)parameters.get(OutputKeys.ENCODING);
			if(encoding == null)
				encoding = "UTF-8";
			try {
				return xml.getBytes(encoding);
			} catch (UnsupportedEncodingException uee) {
				return xml.getBytes();
			}
		} catch (Exception e) {
			handleException(e);
			return null;
		} finally {
			pool.release(con);
		}
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xmlrpc.RpcAPI#retrieveAll(org.exist.security.User, int, java.util.Hashtable)
	 */
	public byte[] retrieveAll(User user, int resultId, Hashtable parameters) throws EXistException, PermissionDeniedException {
		RpcConnection con = null;
		try {
			con = pool.get();
			String xml = con.retrieveAll(user, resultId, parameters);
			String encoding = (String)parameters.get(OutputKeys.ENCODING);
			if(encoding == null)
				encoding = "UTF-8";
			try {
				return xml.getBytes(encoding);
			} catch (UnsupportedEncodingException uee) {
				return xml.getBytes();
			}
		} catch (Exception e) {
			handleException(e);
			return null;
		} finally {
			pool.release(con);
		}
	}

	/**
	 *  Sets the permissions attribute of the RpcServer object
	 *
	 *@param  user                           The new permissions value
	 *@param  resource                       The new permissions value
	 *@param  permissions                    The new permissions value
	 *@return                                Description of the Return Value
	 *@exception  EXistException             Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	public boolean setPermissions(User user, String resource, String permissions)
		throws EXistException, PermissionDeniedException {
		return setPermissions(user, resource, null, null, permissions);
	}

	/**
	 *  Sets the permissions attribute of the RpcServer object
	 *
	 *@param  user                           The new permissions value
	 *@param  resource                       The new permissions value
	 *@param  permissions                    The new permissions value
	 *@param  owner                          The new permissions value
	 *@param  ownerGroup                     The new permissions value
	 *@return                                Description of the Return Value
	 *@exception  EXistException             Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	public boolean setPermissions(
		User user,
		String resource,
		String owner,
		String ownerGroup,
		String permissions)
		throws EXistException, PermissionDeniedException {
		RpcConnection con = null;
		try {
			con = pool.get();
			return con.setPermissions(user, resource, owner, ownerGroup, permissions);
		} catch (Exception e) {
			handleException(e);
			return false;
		} finally {
			pool.release(con);
		}
	}

	/**
	     * @see org.exist.xmlrpc.RpcAPI#setPermissions(org.exist.security.User, java.lang.String, int)
	     */
	public boolean setPermissions(User user, String resource, int permissions)
		throws EXistException, PermissionDeniedException {
		RpcConnection con = null;
		try {
			con = pool.get();
			return con.setPermissions(user, resource, null, null, permissions);
		} catch (Exception e) {
			handleException(e);
			return false;
		} finally {
			pool.release(con);
		}
	}

	/**
	 * @see org.exist.xmlrpc.RpcAPI#setPermissions(org.exist.security.User, java.lang.String, java.lang.String, java.lang.String, int)
	 */
	public boolean setPermissions(
		User user,
		String resource,
		String owner,
		String ownerGroup,
		int permissions)
		throws EXistException, PermissionDeniedException {
		RpcConnection con = null;
		try {
			con = pool.get();
			return con.setPermissions(user, resource, owner, ownerGroup, permissions);
		} catch (Exception e) {
			handleException(e);
			return false;
		} finally {
			pool.release(con);
		}
	}

	/**
	 *  Sets the password attribute of the RpcServer object
	 *
	 *@param  user                           The new password value
	 *@param  name                           The new password value
	 *@param  password                       The new password value
	 *@param  groups                         The new user value
	 *@return                                Description of the Return Value
	 *@exception  EXistException             Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	public boolean setUser(
		User user,
		String name,
		String password,
		Vector groups,
		String home)
		throws EXistException, PermissionDeniedException {
		RpcConnection con = null;
		try {
			con = pool.get();
			return con.setUser(user, name, password, groups, home);
		} catch (Exception e) {
			handleException(e);
			return false;
		} finally {
			pool.release(con);
		}
	}

	public boolean setUser(User user, String name, String password, Vector groups)
		throws EXistException, PermissionDeniedException {
		return setUser(user, name, password, groups, null);
	}

	/* (non-Javadoc)
		 * @see org.exist.xmlrpc.RpcAPI#xupdate(org.exist.security.User, java.lang.String, byte[])
		 */
	public int xupdate(User user, String collectionName, byte[] xupdate)
		throws PermissionDeniedException, EXistException, SAXException {
		RpcConnection con = null;
		try {
			con = pool.get();
			String xupdateStr = new String(xupdate, "UTF-8");
			return con.xupdate(user, collectionName, xupdateStr);
		} catch (Exception e) {
			handleException(e);
			return 0;
		} finally {
			pool.release(con);
		}
	}



	/* (non-Javadoc)
	 * @see org.exist.xmlrpc.RpcAPI#xupdateResource(org.exist.security.User, java.lang.String, byte[])
	 */
	public int xupdateResource(User user, String resource, byte[] xupdate)
		throws PermissionDeniedException, EXistException, SAXException {			
		return xupdateResource(user, resource, xupdate, "UTF-8");
	}

	/* (non-Javadoc)
	 * @see org.exist.xmlrpc.RpcAPI#xupdateResource(org.exist.security.User, java.lang.String, byte[])
	 */
	public int xupdateResource(User user, String resource, byte[] xupdate, String encoding)
		throws PermissionDeniedException, EXistException, SAXException {
		RpcConnection con = null;
		try {
			con = pool.get();
			String xupdateStr = new String(xupdate, encoding);
			return con.xupdateResource(user, resource, xupdateStr);
		} catch (Exception e) {
			handleException(e);
			return 0;
		} finally {
			pool.release(con);
		}
	}

	public boolean shutdown(User user) throws PermissionDeniedException {
		if (!user.hasGroup("dba"))
			throw new PermissionDeniedException(
				"not allowed to shut down" + "the database");
		try {
			BrokerPool.stop();
			return true;
		} catch (EXistException e) {
			LOG.warn("shutdown failed", e);
			return false;
		}
	}

	public boolean sync(User user) {
		RpcConnection con = null;
		try {
			con = pool.get();
			con.sync();
		} finally {
			pool.release(con);
		}
		return true;
	}

	class ConnectionPool {

		public final static int CHECK_INTERVAL = 5000;

		public final static int TIMEOUT = 180000;
		protected Configuration conf;
		protected int connections = 0;

		protected long lastCheck = System.currentTimeMillis();
		protected int max = 1;
		protected int min = 0;
		protected Int2ObjectHashMap resultSets = new Int2ObjectHashMap(128);
		protected Stack pool = new Stack();
		protected ArrayList threads = new ArrayList();

		public ConnectionPool(int min, int max, Configuration conf) {
			this.min = min;
			this.max = max;
			this.conf = conf;
			initialize();
		}

		private void checkResultSets() {
			for (Iterator i = resultSets.valueIterator(); i.hasNext();) {
				final QueryResult qr = (QueryResult) i.next();
				long ts = ((QueryResult) qr).timestamp;
				if (System.currentTimeMillis() - ts > TIMEOUT) {
					LOG.debug("releasing result set " + qr.hashCode());
					i.remove();
				}
			}
		}

		protected RpcConnection createConnection() {
			try {
				RpcConnection con = new RpcConnection(conf, this);
				threads.add(con);
				con.start();
				connections++;
				return con;
			} catch (EXistException ee) {
				LOG.warn(ee);
				return null;
			}
		}

		public synchronized RpcConnection get() {
			if (pool.isEmpty()) {
				if (connections < max)
					return createConnection();
				else
					while (pool.isEmpty()) {
						LOG.debug("waiting for connection to become available");
						try {
							this.wait();
						} catch (InterruptedException e) {
						}
					}
			}
			RpcConnection con = (RpcConnection) pool.pop();
			this.notifyAll();
			if (System.currentTimeMillis() - lastCheck > CHECK_INTERVAL)
				checkResultSets();
			return con;
		}

		protected void initialize() {
			RpcConnection con;
			for (int i = 0; i < min; i++) {
				con = createConnection();
				pool.push(con);
			}
		}

		public synchronized void release(RpcConnection con) {
			pool.push(con);
			this.notifyAll();
		}

		public synchronized void shutdown() {
			for (Iterator i = threads.iterator(); i.hasNext();)
				 ((RpcConnection) i.next()).terminate();
			while (pool.size() < connections)
				try {
					this.wait();
				} catch (InterruptedException e) {
				}
			try {
				BrokerPool.stop();
			} catch (EXistException e) {
				LOG.warn("shutdown failed", e);
			}
		}

		public synchronized void synchronize() {
			for (Iterator i = threads.iterator(); i.hasNext();)
				 ((RpcConnection) i.next()).synchronize();

		}
	}

	public boolean copyCollection(User user, String name, String namedest)
		throws EXistException, PermissionDeniedException {
		RpcConnection con = pool.get();
		try {

			createCollection(user, namedest);

			Hashtable parametri = new Hashtable();
			parametri.put(OutputKeys.INDENT, "no");
			parametri.put(EXistOutputKeys.EXPAND_XINCLUDES, "no");
			parametri.put(OutputKeys.ENCODING, "UTF-8");

			Hashtable lista = getCollectionDesc(user, name);
			Vector collezioni = (Vector) lista.get("collections");
			Vector documents = (Vector) lista.get("documents");

			//ricrea le directory
			Iterator collezioniItr = collezioni.iterator();
			String nome;
			while (collezioniItr.hasNext()) {
				nome = collezioniItr.next().toString();
				createCollection(user, namedest + "/" + nome);
				copyCollection(user, name + "/" + nome, namedest + "/" + nome);
			}

			//Copy i file
			Hashtable hash;
			int p, dsize = documents.size();
			for (int i = 0; i < dsize; i++) {
				hash = (Hashtable) documents.elementAt(i);
				nome = (String) hash.get("name");
				if ((p = nome.lastIndexOf('/')) > -1)
					nome = nome.substring(p + 1);

				byte[] xml = getDocument(user, name + "/" + nome, parametri);
				parse(user, xml, namedest + "/" + nome);
			}

			return true;
		} catch (Exception e) {
			handleException(e);
			return false;
		} finally {
			pool.release(con);
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xmlrpc.RpcAPI#lockResource(org.exist.security.User, java.lang.String, java.lang.String)
	 */
	public boolean lockResource(User user, String path, String userName) throws EXistException, PermissionDeniedException {
		RpcConnection con = pool.get();
		try {
			con.lockResource(user, path, userName);
			return true;
		} catch (Exception e) {
			handleException(e);
			return false;
		} finally {
			pool.release(con);
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xmlrpc.RpcAPI#hasUserLock(org.exist.security.User, java.lang.String)
	 */
	public String hasUserLock(User user, String path) throws EXistException, PermissionDeniedException {
		RpcConnection con = pool.get();
		try {
			return con.hasUserLock(user, path);
		} catch (Exception e) {
			handleException(e);
			return "";
		} finally {
			pool.release(con);
		}
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xmlrpc.RpcAPI#unlockResource(org.exist.security.User, java.lang.String)
	 */
	public boolean unlockResource(User user, String path) throws EXistException, PermissionDeniedException {
		RpcConnection con = pool.get();
		try {
			con.unlockResource(user, path);
			return true;
		} catch (Exception e) {
			handleException(e);
			return false;
		} finally {
			pool.release(con);
		}
	}

}
