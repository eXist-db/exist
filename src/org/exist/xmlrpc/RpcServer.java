
/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
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

import it.unimi.dsi.fastutil.Int2ObjectOpenHashMap;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Stack;
import java.util.Vector;

import org.apache.log4j.Category;
import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.util.Configuration;
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
	private static Category LOG = Category.getInstance(RpcServer.class.getName());

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

	/**
	 *  Description of the Method
	 *
	 *@param  name                           Description of the Parameter
	 *@param  user                           Description of the Parameter
	 *@return                                Description of the Return Value
	 *@exception  EXistException             Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
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

	/**
	 *  Description of the Method
	 *
	 *@param  collection                     Description of the Parameter
	 *@param  user                           Description of the Parameter
	 *@return                                Description of the Return Value
	 *@exception  EXistException             Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	public String createId(User user, String collection)
		throws EXistException, PermissionDeniedException {
		RpcConnection con = pool.get();
		try {
			return con.createId(user, collection);
		} finally {
			pool.release(con);
		}
	}

	/**
	 *  Description of the Method
	 *
	 *@param  xpath                          Description of the Parameter
	 *@param  user                           Description of the Parameter
	 *@return                                Description of the Return Value
	 *@exception  EXistException             Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
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

	/**
	 *  Description of the Method
	 *
	 *@param  xpath                          Description of the Parameter
	 *@param  encoding                       Description of the Parameter
	 *@param  user                           Description of the Parameter
	 *@return                                Description of the Return Value
	 *@exception  EXistException             Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	public int executeQuery(User user, byte[] xpath, String encoding)
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

	/**
	 *  Description of the Method
	 *
	 *@param  xpath                          Description of the Parameter
	 *@param  user                           Description of the Parameter
	 *@return                                Description of the Return Value
	 *@exception  EXistException             Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	public int executeQuery(User user, byte[] xpath)
		throws EXistException, PermissionDeniedException {
		return executeQuery(user, xpath, null);
	}

	/**
	 *  Gets the collectionDesc attribute of the RpcServer object
	 *
	 *@param  rootCollection                 Description of the Parameter
	 *@param  user                           Description of the Parameter
	 *@return                                The collectionDesc value
	 *@exception  EXistException             Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
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

	/**
	 *  This method is provided to retrieve a document with encodings other than
	 *  UTF-8. Since the data is handled as binary data, character encodings are
	 *  preserved. byte[]-values are automatically BASE64-encoded by the XMLRPC
	 *  library.
	 *
	 *@param  name                           Description of the Parameter
	 *@param  encoding                       Description of the Parameter
	 *@param  prettyPrint                    Description of the Parameter
	 *@param  user                           Description of the Parameter
	 *@return                                The document value
	 *@exception  EXistException             Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	public byte[] getDocument(User user, String name, String encoding, int prettyPrint)
		throws EXistException, PermissionDeniedException {
		RpcConnection con = pool.get();
		try {
			String xml = con.getDocument(user, name, (prettyPrint > 0), encoding, null);
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

	/**
	 *  This method is provided to retrieve a document with encodings other than
	 *  UTF-8. Since the data is handled as binary data, character encodings are
	 *  preserved. byte[]-values are automatically BASE64-encoded by the XMLRPC
	 *  library.
	 *
	 *@param  name                           Description of the Parameter
	 *@param  encoding                       Description of the Parameter
	 *@param  prettyPrint                    Description of the Parameter
	 *@param  stylesheet                     Description of the Parameter
	 *@param  user                           Description of the Parameter
	 *@return                                The document value
	 *@exception  EXistException             Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	public byte[] getDocument(
		User user,
		String name,
		String encoding,
		int prettyPrint,
		String stylesheet)
		throws EXistException, PermissionDeniedException {
		RpcConnection con = pool.get();
		try {
			String xml = con.getDocument(user, name, (prettyPrint > 0), encoding, stylesheet);
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

	/**
	 *  get a list of all documents contained in the repository.
	 *
	 *@param  user                           Description of the Parameter
	 *@return                                The documentListing value
	 *@exception  EXistException             Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	public Vector getDocumentListing(User user) throws EXistException, PermissionDeniedException {
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

	/**
	 *  get a list of all documents contained in the collection.
	 *
	 *@param  collection                     Description of the Parameter
	 *@param  user                           Description of the Parameter
	 *@return                                The documentListing value
	 *@exception  EXistException             Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
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

	/**
	 *  Gets the hits attribute of the RpcServer object
	 *
	 *@param  resultId                       Description of the Parameter
	 *@param  user                           Description of the Parameter
	 *@return                                The hits value
	 *@exception  EXistException             Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	public int getHits(User user, int resultId) throws EXistException, PermissionDeniedException {
		RpcConnection con = pool.get();
		try {
			return con.getHits(user, resultId);
		} finally {
			pool.release(con);
		}
	}

	/**
	 *  Gets the permissions attribute of the RpcServer object
	 *
	 *@param  docName                        Description of the Parameter
	 *@param  user                           Description of the Parameter
	 *@return                                The permissions value
	 *@exception  EXistException             Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
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

	private void handleException(Exception e) throws EXistException, PermissionDeniedException {
		LOG.debug(e.getMessage(), e);
		if (e instanceof EXistException)
			throw (EXistException) e;
		else if (e instanceof PermissionDeniedException)
			throw (PermissionDeniedException) e;
		else
			throw new EXistException(e.getMessage());
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

	/**
	 *  parse an XML document and store it into the database. The document will
	 *  later be identified by <code>docName</code>. Some xmlrpc clients seem to
	 *  have problems with character encodings when sending xml content. To
	 *  avoid this, parse() accepts the xml document content as byte[].
	 *
	 *@param  xmlData                        the document's XML content as UTF-8
	 *      encoded array of bytes.
	 *@param  docName                        the document's name
	 *@param  overwrite                      replace an existing document with
	 *      the same name? (1=yes, 0=no)
	 *@param  user                           Description of the Parameter
	 *@return                                Description of the Return Value
	 *@exception  EXistException             Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	public boolean parse(User user, byte[] xmlData, String docName, int overwrite)
		throws EXistException, PermissionDeniedException {
		String xml = null;
		try {
			xml = new String(xmlData, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			xml = new String(xmlData);
		}
		// some clients (Perl) encode strings with a \0 at the end.
		// remove it ...
		if (xml.charAt(xml.length() - 1) == 0x0)
			xml = xml.substring(0, xml.length() - 1);

		return parse(user, xml, docName, overwrite);
	}

	/**
	 *  Description of the Method
	 *
	 *@param  xml                            Description of the Parameter
	 *@param  docName                        Description of the Parameter
	 *@param  overwrite                      Description of the Parameter
	 *@param  user                           Description of the Parameter
	 *@return                                Description of the Return Value
	 *@exception  EXistException             Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
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
	public boolean parseLocal(User user, String localFile, String docName, boolean replace)
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

	/**
	 *  Description of the Method
	 *
	 *@param  xml                            Description of the Parameter
	 *@param  docName                        Description of the Parameter
	 *@param  user                           Description of the Parameter
	 *@return                                Description of the Return Value
	 *@exception  EXistException             Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	public boolean parse(User user, String xml, String docName)
		throws EXistException, PermissionDeniedException {
		return parse(user, xml, docName, 0);
	}

	/**
	 *  execute XPath query and return a list of results. The result is an array
	 *  of String[][2], which represents a two dimensional table, where every
	 *  row consists of a document-name / node-id pair. e.g.:
	 *  <tableborder="1">
	 *
	 *    <tr>
	 *
	 *      <td>
	 *        hamlet.xml
	 *      </td>
	 *
	 *      <td>
	 *        8398
	 *      </td>
	 *
	 *    </tr>
	 *
	 *    <tr>
	 *
	 *      <td>
	 *        hamlet.xml
	 *      </td>
	 *
	 *      <td>
	 *        8399
	 *      </td>
	 *
	 *    </tr>
	 *
	 *  </table>
	 *  You may use this information with the retrieve-call to retrieve the
	 *  actual nodes.
	 *
	 *@param  xpath                          the XPath query to execute.
	 *@param  encoding                       Description of the Parameter
	 *@param  user                           Description of the Parameter
	 *@return                                string[][2]
	 *@exception  EXistException             Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	public Vector query(User user, byte[] xpath, String encoding)
		throws EXistException, PermissionDeniedException {
		String xpathString = null;
		if (encoding != null)
			try {
				xpathString = new String(xpath, encoding);
			} catch (UnsupportedEncodingException e) {
			}

		if (xpathString == null)
			xpathString = new String(xpath);

		// some clients (Perl) encode strings with a \0 at the end.
		// remove it ...
		if (xpathString.charAt(xpathString.length() - 1) == 0x0)
			xpathString = xpathString.substring(0, xpathString.length() - 1);

		return query(user, xpathString);
	}

	public Hashtable queryP(User user, byte[] xpath)
		throws EXistException, PermissionDeniedException {
		return queryP(user, xpath, null);
	}

	public Hashtable queryP(User user, byte[] xpath, byte[] sortExpr)
		throws EXistException, PermissionDeniedException {
		return queryP(user, xpath, null, null, sortExpr);
	}

	public Hashtable queryP(User user, byte[] xpath, String docName, String s_id)
		throws EXistException, PermissionDeniedException {
		return queryP(user, xpath, docName, s_id, null);
	}

	public Hashtable queryP(User user, byte[] xpath, String docName, String s_id, byte[] sortExpr)
		throws EXistException, PermissionDeniedException {
		String xpathString = null;
		String sortString = null;
		try {
			xpathString = new String(xpath, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new EXistException("failed to decode xpath expression");
		}
		if (sortExpr != null)
			try {
				sortString = new String(sortExpr, "UTF-8");
			} catch (UnsupportedEncodingException e) {
				throw new EXistException("failed to decode xpath expression");
			}

		// some clients (Perl) encode strings with a \0 at the end.
		// remove it ...
		if (xpathString.charAt(xpathString.length() - 1) == 0x0)
			xpathString = xpathString.substring(0, xpathString.length() - 1);
		RpcConnection con = pool.get();
		try {
			return con.queryP(user, xpathString, docName, s_id, sortString);
		} catch (Exception e) {
			handleException(e);
			return null;
		} finally {
			pool.release(con);
		}
	}

	public void releaseQueryResult(int handle) {
		RpcConnection con = pool.get();
		try {
			con.releaseQueryResult(handle);
		} finally {
			pool.release(con);
		}
	}

	public Vector query(User user, byte[] xpath) throws EXistException, PermissionDeniedException {
		return query(user, xpath, "UTF-8");
	}

	public Vector query(User user, String xpath) throws EXistException, PermissionDeniedException {
		return query(user, xpath, null, null);
	}

	public Vector query(User user, String xpath, String docId, String s_id)
		throws EXistException, PermissionDeniedException {
		RpcConnection con = pool.get();
		try {
			return con.query(user, xpath, docId, s_id);
		} catch (Exception e) {
			handleException(e);
			return null;
		} finally {
			pool.release(con);
		}
	}

	/**
	 *  execute XPath query and return howmany nodes from the result set,
	 *  starting at position <code>start</code>. If <code>prettyPrint</code> is
	 *  set to >0 (true), results are pretty printed.
	 *
	 *@param  xpath                          the XPath query to execute
	 *@param  howmany                        maximum number of results to
	 *      return.
	 *@param  start                          item in the result set to start
	 *      with.
	 *@param  prettyPrint                    turn on pretty printing if >0.
	 *@param  encoding                       the character encoding to use.
	 *@param  user                           Description of the Parameter
	 *@return                                Description of the Return Value
	 *@exception  EXistException             Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	public byte[] query(
		User user,
		String xpath,
		String encoding,
		int howmany,
		int start,
		int prettyPrint)
		throws EXistException, PermissionDeniedException {
		return query(user, xpath, encoding, howmany, start, prettyPrint, null);
	}

	/**
	 *  execute XPath query and return howmany nodes from the result set,
	 *  starting at position <code>start</code>. If <code>prettyPrint</code> is
	 *  set to >0 (true), results are pretty printed.
	 *
	 *@param  xpath                          the XPath query to execute
	 *@param  howmany                        maximum number of results to
	 *      return.
	 *@param  start                          item in the result set to start
	 *      with.
	 *@param  prettyPrint                    turn on pretty printing if >0.
	 *@param  encoding                       the character encoding to use.
	 *@param  sortExpr                       Description of the Parameter
	 *@param  user                           Description of the Parameter
	 *@return                                Description of the Return Value
	 *@exception  EXistException             Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	public byte[] query(
		User user,
		String xpath,
		String encoding,
		int howmany,
		int start,
		int prettyPrint,
		String sortExpr)
		throws EXistException, PermissionDeniedException {
		RpcConnection con = pool.get();
		String result = null;
		try {
			try {
				result =
					con.query(
						user,
						xpath,
						howmany,
						start,
						(prettyPrint > 0),
						false,
						encoding,
						sortExpr);
				return result.getBytes(encoding);
			} catch (UnsupportedEncodingException uee) {
				return result.getBytes();
			}
		} catch (Exception e) {
			handleException(e);
			return null;
		} finally {
			pool.release(con);
		}
	}

	/**
	 *  Description of the Method
	 *
	 *@param  resultId                       Description of the Parameter
	 *@param  user                           Description of the Parameter
	 *@return                                Description of the Return Value
	 *@exception  PermissionDeniedException  Description of the Exception
	 *@exception  EXistException             Description of the Exception
	 */
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

	/**
	 *  Description of the Method
	 *
	 *@param  name                           Description of the Parameter
	 *@param  user                           Description of the Parameter
	 *@return                                Description of the Return Value
	 *@exception  EXistException             Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
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

	/**
	 *  Description of the Method
	 *
	 *@param  user                           Description of the Parameter
	 *@param  name                           Description of the Parameter
	 *@return                                Description of the Return Value
	 *@exception  EXistException             Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
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
		return retrieve(user, doc, id, 0);
	}

	/**
	 *  retrieve a single node from a document. The node is identified by it's
	 *  internal id.
	 *
	 *@param  doc                            the document containing the node
	 *@param  id                             the node's internal id
	 *@param  prettyPrint                    result is pretty printed if >0
	 *@param  user                           Description of the Parameter
	 *@return                                Description of the Return Value
	 *@exception  EXistException             Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	public byte[] retrieve(User user, String doc, String id, int prettyPrint)
		throws EXistException, PermissionDeniedException {
		return retrieve(user, doc, id, prettyPrint, "UTF-8");
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
	public byte[] retrieve(User user, String doc, String id, int prettyPrint, String encoding)
		throws EXistException, PermissionDeniedException {
		RpcConnection con = pool.get();
		String xml = null;
		try {
			xml = con.retrieve(user, doc, id, (prettyPrint > 0), encoding);
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
	 *  Description of the Method
	 *
	 *@param  resultId                       Description of the Parameter
	 *@param  num                            Description of the Parameter
	 *@param  prettyPrint                    Description of the Parameter
	 *@param  encoding                       Description of the Parameter
	 *@param  user                           Description of the Parameter
	 *@return                                Description of the Return Value
	 *@exception  EXistException             Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	public byte[] retrieve(User user, int resultId, int num, int prettyPrint, String encoding)
		throws EXistException, PermissionDeniedException {
		RpcConnection con = pool.get();
		try {
			String xml = con.retrieve(user, resultId, num, (prettyPrint > 0), encoding);
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
	public boolean setUser(User user, String name, String password, Vector groups, String home)
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
		RpcConnection con = null;
		try {
			con = pool.get();
			String xupdateStr = new String(xupdate, "UTF-8");
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
			throw new PermissionDeniedException("not allowed to shut down" + "the database");
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

	/**
	 *  Description of the Class
	 *
	 *@author     wolf
	 *@created    28. Mai 2002
	 */
	class ConnectionPool {

		public final static int CHECK_INTERVAL = 5000;

		public final static int TIMEOUT = 180000;
		protected Configuration conf;
		protected int connections = 0;

		protected long lastCheck = System.currentTimeMillis();
		protected int max = 1;
		protected int min = 0;
		protected Int2ObjectOpenHashMap resultSets = new Int2ObjectOpenHashMap();
		protected Stack pool = new Stack();
		protected ArrayList threads = new ArrayList();

		public ConnectionPool(int min, int max, Configuration conf) {
			this.min = min;
			this.max = max;
			this.conf = conf;
			initialize();
		}

		private void checkResultSets() {
			for (Iterator i = resultSets.values().iterator(); i.hasNext();) {
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

}
