
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
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectProcedure;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Random;
import java.util.Stack;
import java.util.Vector;
import java.util.WeakHashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.log4j.Category;
import org.apache.xml.serialize.OutputFormat;
import org.apache.xml.serialize.XMLSerializer;
import org.exist.EXistException;
import org.exist.Parser;
import org.exist.dom.ArraySet;
import org.exist.dom.Collection;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.SortedNodeSet;
import org.exist.parser.XPathLexer;
import org.exist.parser.XPathParser;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.RelationalBroker;
import org.exist.storage.serializers.Serializer;
import org.exist.util.Configuration;
import org.exist.util.SyntaxException;
import org.exist.xpath.PathExpr;
import org.exist.xpath.Value;
import org.exist.xpath.ValueSet;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

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
	private static Category LOG =
		Category.getInstance(RpcServer.class.getName());

	protected final static int MIN_CONNECT = 1;
	protected final static int MAX_CONNECT = 10;
	protected DocumentBuilder docBuilder = null;

	protected ConnectionPool pool;

	protected TIntObjectHashMap resultSets = new TIntObjectHashMap();

	/**
	 *  Constructor for the RpcServer object
	 *
	 *@param  conf                Description of the Parameter
	 *@exception  EXistException  Description of the Exception
	 */
	public RpcServer(Configuration conf) throws EXistException {
		pool = new ConnectionPool(MIN_CONNECT, MAX_CONNECT, conf);
		DocumentBuilderFactory docFactory =
			DocumentBuilderFactory.newInstance();
		try {
			docBuilder = docFactory.newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			LOG.warn(e);
			throw new EXistException(e);
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
			throw new EXistException(
				"collection " + rootCollection + " not found!");
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
	public byte[] getDocument(
		User user,
		String name,
		String encoding,
		int prettyPrint)
		throws EXistException, PermissionDeniedException {
		RpcConnection con = pool.get();
		try {
			String xml =
				con.getDocument(user, name, (prettyPrint > 0), encoding, null);
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
			String xml =
				con.getDocument(
					user,
					name,
					(prettyPrint > 0),
					encoding,
					stylesheet);
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

	public Vector listDocumentPermissions(User user, String name)
		throws EXistException, PermissionDeniedException {
		RpcConnection con = pool.get();
		try {
			return con.listDocumentPermissions(user, name);
		} finally {
			pool.release(con);
		}
	}

	public Vector listCollectionPermissions(User user, String name)
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
	public int getHits(User user, int resultId)
		throws EXistException, PermissionDeniedException {
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

	private void handleException(Exception e)
		throws EXistException, PermissionDeniedException {
		LOG.debug(e.getMessage());
		StringWriter sw = new StringWriter();
		e.printStackTrace(new PrintWriter(sw));
		LOG.error(sw.toString());
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
	public boolean parse(
		User user,
		byte[] xmlData,
		String docName,
		int overwrite)
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
			return con.parse(user, xml, docName, (overwrite != 0));
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

	/**
	 *  Description of the Method
	 *
	 *@param  xpath                          Description of the Parameter
	 *@param  user                           Description of the Parameter
	 *@return                                Description of the Return Value
	 *@exception  EXistException             Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	public Vector query(User user, byte[] xpath)
		throws EXistException, PermissionDeniedException {
		return query(user, xpath, "UTF-8");
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
	public Vector query(User user, String xpath)
		throws EXistException, PermissionDeniedException {
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
		return retrieve(user, doc, id, 1);
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
	public byte[] retrieve(
		User user,
		String doc,
		String id,
		int prettyPrint,
		String encoding)
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
	public byte[] retrieve(
		User user,
		int resultId,
		int num,
		int prettyPrint,
		String encoding)
		throws EXistException, PermissionDeniedException {
		RpcConnection con = pool.get();
		try {
			String xml =
				con.retrieve(user, resultId, num, (prettyPrint > 0), encoding);
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
	public boolean setPermissions(
		User user,
		String resource,
		String permissions)
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
			return con.setPermissions(
				user,
				resource,
				owner,
				ownerGroup,
				permissions);
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
		Vector groups)
		throws EXistException, PermissionDeniedException {
		RpcConnection con = null;
		try {
			con = pool.get();
			return con.setUser(user, name, password, groups);
		} finally {
			pool.release(con);
		}
	}

	public boolean shutdown(User user) throws PermissionDeniedException {
		if(!user.hasGroup("dba"))
			throw new PermissionDeniedException("not allowed to shut down" +
				"the database");
		try {
			BrokerPool.stop();
			return true;
		} catch (EXistException e) {
			LOG.warn("shutdown failed", e);
			return false;
		}
	}

	/**
	 *  Description of the Class
	 *
	 *@author     wolf
	 *@created    28. Mai 2002
	 */
	class ConnectionPool {

		/**  Description of the Field */
		public final static int CHECK_INTERVAL = 5000;
		/**  Description of the Field */
		public final static int TIMEOUT = 600000;
		protected Configuration conf;
		protected int connections = 0;

		protected long lastCheck = System.currentTimeMillis();
		protected int max = 1;
		protected int min = 0;

		protected Stack pool = new Stack();
		protected ArrayList threads = new ArrayList();

		/**
		 *  Constructor for the ConnectionPool object
		 *
		 *@param  min   Description of the Parameter
		 *@param  max   Description of the Parameter
		 *@param  conf  Description of the Parameter
		 */
		public ConnectionPool(int min, int max, Configuration conf) {
			this.min = min;
			this.max = max;
			this.conf = conf;
			initialize();
		}

		private void checkResultSets() {
			resultSets.forEachEntry(new CheckProcedure());
		}

		/**
		 *  Description of the Method
		 *
		 *@return    Description of the Return Value
		 */
		protected RpcConnection createConnection() {
			try {
				RpcConnection con = new RpcConnection(conf);
				threads.add(con);
				con.start();
				connections++;
				return con;
			} catch (EXistException ee) {
				LOG.warn(ee);
				return null;
			}
		}

		/**
		 *  Description of the Method
		 *
		 *@return    Description of the Return Value
		 */
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

		/**  Description of the Method */
		protected void initialize() {
			RpcConnection con;
			for (int i = 0; i < min; i++) {
				con = createConnection();
				pool.push(con);
			}
		}

		/**
		 *  Description of the Method
		 *
		 *@param  con  Description of the Parameter
		 */
		public synchronized void release(RpcConnection con) {
			pool.push(con);
			this.notifyAll();
		}

		/**  Description of the Method */
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

		/**  Description of the Method */
		public synchronized void synchronize() {
			for (Iterator i = threads.iterator(); i.hasNext();)
				 ((RpcConnection) i.next()).synchronize();

		}

		/**
		 *  Description of the Class
		 *
		 *@author     wolf
		 *@created    28. Mai 2002
		 */
		private class CheckProcedure implements TIntObjectProcedure {

			/**
			 *  Description of the Method
			 *
			 *@param  hashCode  Description of the Parameter
			 *@param  qr        Description of the Parameter
			 *@return           Description of the Return Value
			 */
			public boolean execute(int hashCode, Object qr) {
				long ts = ((QueryResult) qr).timestamp;
				if (System.currentTimeMillis() - ts > TIMEOUT) {
					resultSets.remove(hashCode);
					LOG.debug("removing result set " + hashCode);
				}
				return true;
			}
		}
	}

	/**
	 *  Description of the Class
	 *
	 *@author     wolf
	 *@created    28. Mai 2002
	 */
	private class QueryResult {
		long queryTime = 0;
		Value result;
		long timestamp = 0;

		/**
		 *  Constructor for the QueryResult object
		 *
		 *@param  result     Description of the Parameter
		 *@param  queryTime  Description of the Parameter
		 */
		public QueryResult(Value result, long queryTime) {
			this.result = result;
			this.queryTime = queryTime;
			this.timestamp = System.currentTimeMillis();
		}
	}

	/**
	 *  Description of the Class
	 *
	 *@author     wolf
	 *@created    28. Mai 2002
	 */
	class RpcConnection extends Thread {
		protected BrokerPool brokerPool;
		protected WeakHashMap documentCache = new WeakHashMap();
		protected Parser parser = null;
		protected boolean terminate = false;

		/**
		 *  Constructor for the RpcConnection object
		 *
		 *@param  conf                Description of the Parameter
		 *@exception  EXistException  Description of the Exception
		 */
		public RpcConnection(Configuration conf) throws EXistException {
			super();
			brokerPool = BrokerPool.getInstance();
		}

		/**
		 *  Description of the Method
		 *
		 *@param  name                           Description of the Parameter
		 *@param  user                           Description of the Parameter
		 *@exception  Exception                  Description of the Exception
		 *@exception  PermissionDeniedException  Description of the Exception
		 */
		public void createCollection(User user, String name)
			throws Exception, PermissionDeniedException {
			DBBroker broker = brokerPool.get();
			try {
				Collection current = broker.getOrCreateCollection(user, name);
				LOG.debug("creating collection " + name);
				broker.saveCollection(current);
				broker.flush();
				broker.sync();
				LOG.debug("collection " + name + " has been created");
			} catch (Exception e) {
				LOG.debug(e);
				throw e;
			} finally {
				brokerPool.release(broker);
			}
		}

		/**
		 *  Description of the Method
		 *
		 *@param  collName            Description of the Parameter
		 *@param  user                Description of the Parameter
		 *@return                     Description of the Return Value
		 *@exception  EXistException  Description of the Exception
		 */
		public String createId(User user, String collName)
			throws EXistException {
			DBBroker broker = brokerPool.get();
			try {
				Collection collection = broker.getCollection(collName);
				if (collection == null)
					throw new EXistException(
						"collection " + collName + " not found!");
				String id;
				Random rand = new Random();
				boolean ok;
				do {
					ok = true;
					id = Integer.toHexString(rand.nextInt()) + ".xml";
					// check if this id does already exist
					if (collection.hasDocument(id))
						ok = false;

					if (collection.hasSubcollection(id))
						ok = false;

				} while (!ok);
				return id;
			} finally {
				brokerPool.release(broker);
			}
		}

		/**
		 *  Description of the Method
		 *
		 *@param  xpath          Description of the Parameter
		 *@param  user           Description of the Parameter
		 *@return                Description of the Return Value
		 *@exception  Exception  Description of the Exception
		 */
		protected Value doQuery(
			User user,
			String xpath,
			DocumentSet docs,
			NodeSet context)
			throws Exception {
			XPathLexer lexer = new XPathLexer(new StringReader(xpath));
			XPathParser parser = new XPathParser(brokerPool, user, lexer);
			PathExpr expr = new PathExpr(brokerPool);
			parser.expr(expr);
			LOG.info("query: " + expr.pprint());
			long start = System.currentTimeMillis();
			if (parser.foundErrors())
				throw new EXistException(parser.getErrorMsg());
			DocumentSet ndocs =
				(docs == null ? expr.preselect() : expr.preselect(docs));
			if (ndocs.getLength() == 0)
				return null;
			LOG.info(
				"pre-select took "
					+ (System.currentTimeMillis() - start)
					+ "ms.");
			Value result = expr.eval(ndocs, context, null);
			LOG.info(
				"query took " + (System.currentTimeMillis() - start) + "ms.");
			return result;
		}

		/**
		 *  Description of the Method
		 *
		 *@param  xpath          Description of the Parameter
		 *@param  user           Description of the Parameter
		 *@return                Description of the Return Value
		 *@exception  Exception  Description of the Exception
		 */
		public int executeQuery(User user, String xpath) throws Exception {
			long startTime = System.currentTimeMillis();
			LOG.debug("query: " + xpath);
			Value resultValue = doQuery(user, xpath, null, null);
			QueryResult qr =
				new QueryResult(
					resultValue,
					(System.currentTimeMillis() - startTime));
			resultSets.put(qr.hashCode(), qr);
			return qr.hashCode();
		}

		/**
		 *  Description of the Method
		 *
		 *@param  message  Description of the Parameter
		 *@return          Description of the Return Value
		 */
		protected String formatErrorMsg(String message) {
			return formatErrorMsg("error", message);
		}

		/**
		 *  Description of the Method
		 *
		 *@param  type     Description of the Parameter
		 *@param  message  Description of the Parameter
		 *@return          Description of the Return Value
		 */
		protected String formatErrorMsg(String type, String message) {
			StringBuffer buf = new StringBuffer();
			buf.append(
				"<exist:result xmlns:exist=\"http://exist.sourceforge.net/NS/exist\" ");
			buf.append("hitCount=\"0\">");
			buf.append('<');
			buf.append(type);
			buf.append('>');
			buf.append(message);
			buf.append("</");
			buf.append(type);
			buf.append("></exist:result>");
			return buf.toString();
		}

		/**
		 *  Gets the collectionDesc attribute of the RpcConnection object
		 *
		 *@param  rootCollection  Description of the Parameter
		 *@param  user            Description of the Parameter
		 *@return                 The collectionDesc value
		 *@exception  Exception   Description of the Exception
		 */
		public Hashtable getCollectionDesc(User user, String rootCollection)
			throws Exception {
			DBBroker broker = brokerPool.get();
			try {
				if (rootCollection == null)
					rootCollection = "/";

				Collection collection = broker.getCollection(rootCollection);
				if (collection == null)
					throw new EXistException(
						"collection " + rootCollection + " not found!");
				Hashtable desc = new Hashtable();
				Vector docs = new Vector();
				Vector collections = new Vector();
				if (collection
					.getPermissions()
					.validate(user, Permission.READ)) {
					DocumentImpl doc;
					for (Iterator i = collection.iterator(); i.hasNext();) {
						doc = (DocumentImpl) i.next();
						docs.addElement(doc.getFileName());
					}
					for (Iterator i = collection.collectionIterator();
						i.hasNext();
						)
						collections.addElement((String) i.next());
				}
				desc.put("collections", collections);
				desc.put("documents", docs);
				desc.put("name", collection.getName());
				return desc;
			} finally {
				brokerPool.release(broker);
			}
		}

		/**
		 *  Gets the document attribute of the RpcConnection object
		 *
		 *@param  name           Description of the Parameter
		 *@param  prettyPrint    Description of the Parameter
		 *@param  encoding       Description of the Parameter
		 *@param  stylesheet     Description of the Parameter
		 *@param  user           Description of the Parameter
		 *@return                The document value
		 *@exception  Exception  Description of the Exception
		 */
		public String getDocument(
			User user,
			String name,
			boolean prettyPrint,
			String encoding,
			String stylesheet)
			throws Exception {
			long start = System.currentTimeMillis();
			DBBroker broker = brokerPool.get();
			try {
				DocumentImpl doc =
					(DocumentImpl) broker.getDocument(user, name);
				if (doc == null) {
					LOG.debug("document " + name + " not found!");
					throw new EXistException("document not found");
				}
				broker.setRetrvMode(RelationalBroker.PRELOAD);
				Serializer serializer = broker.getSerializer();
				serializer.setEncoding(encoding);
				if (stylesheet != null) {
					if (!stylesheet.startsWith("/")) {
						// make path relative to current collection
						String collection;
						if (doc.getCollection() != null)
							collection = doc.getCollection().getName();
						else {
							int cp = doc.getFileName().lastIndexOf("/");
							collection =
								(cp > 0)
									? doc.getFileName().substring(0, cp)
									: "/";
						}
						stylesheet =
							(collection.equals("/")
								? '/' + stylesheet
								: collection + '/' + stylesheet);
					}
					serializer.setStylesheet(stylesheet);
				}
				String xml;
				serializer.setIndent(prettyPrint);
				xml = serializer.serialize(doc);

				broker.setRetrvMode(RelationalBroker.SINGLE);
				return xml;
			} catch (NoSuchMethodError nsme) {
				nsme.printStackTrace();
				return null;
			} finally {
				brokerPool.release(broker);
			}
		}

		/**
		 *  Gets the documentListing attribute of the RpcConnection object
		 *
		 *@param  user                Description of the Parameter
		 *@return                     The documentListing value
		 *@exception  EXistException  Description of the Exception
		 */
		public Vector getDocumentListing(User user) throws EXistException {
			DBBroker broker = brokerPool.get();
			try {
				DocumentSet docs = broker.getAllDocuments();
				String names[] = docs.getNames();
				Vector vec = new Vector();
				for (int i = 0; i < names.length; i++)
					vec.addElement(names[i]);

				return vec;
			} finally {
				brokerPool.release(broker);
			}
		}

		/**
		 *  Gets the documentListing attribute of the RpcConnection object
		 *
		 *@param  collection                     Description of the Parameter
		 *@param  user                           Description of the Parameter
		 *@return                                The documentListing value
		 *@exception  EXistException             Description of the Exception
		 *@exception  PermissionDeniedException  Description of the Exception
		 */
		public Vector getDocumentListing(User user, String name)
			throws EXistException, PermissionDeniedException {
			DBBroker broker = brokerPool.get();
			try {
				if (!name.startsWith("/"))
					name = '/' + name;
				if (!name.startsWith("/db"))
					name = "/db" + name;
				Collection collection = broker.getCollection(name);
				Vector vec = new Vector();
				if (collection == null)
					return vec;
				String resource;
				int p;
				for (Iterator i = collection.iterator(); i.hasNext();) {
					resource = ((DocumentImpl) i.next()).getFileName();
					p = resource.lastIndexOf('/');
					vec.addElement(
						p < 0 ? resource : resource.substring(p + 1));
				}
				return vec;
			} finally {
				brokerPool.release(broker);
			}
		}

		public Vector listDocumentPermissions(User user, String name)
			throws EXistException, PermissionDeniedException {
			DBBroker broker = null;
			try {
				broker = brokerPool.get();
				if (!name.startsWith("/"))
					name = '/' + name;
				if (!name.startsWith("/db"))
					name = "/db" + name;
				Collection collection = broker.getCollection(name);
				if (!collection
					.getPermissions()
					.validate(user, Permission.READ))
					throw new PermissionDeniedException(
						"not allowed to read collection " + name);
				Vector vec = new Vector(collection.getDocumentCount());
				if (collection == null)
					return vec;
				DocumentImpl doc;
				Permission perm;
				Vector tmp;
				for (Iterator i = collection.iterator(); i.hasNext();) {
					doc = (DocumentImpl) i.next();
					perm = doc.getPermissions();
					tmp = new Vector(3);
					tmp.addElement(perm.getOwner());
					tmp.addElement(perm.getOwnerGroup());
					tmp.addElement(new Integer(perm.getPermissions()));
					vec.addElement(tmp);
				}
				return vec;
			} finally {
				brokerPool.release(broker);
			}
		}

		public Vector listCollectionPermissions(User user, String name)
			throws EXistException, PermissionDeniedException {
			DBBroker broker = null;
			try {
				broker = brokerPool.get();
				if (!name.startsWith("/"))
					name = '/' + name;
				if (!name.startsWith("/db"))
					name = "/db" + name;
				Collection collection = broker.getCollection(name);
				if (!collection
					.getPermissions()
					.validate(user, Permission.READ))
					throw new PermissionDeniedException(
						"not allowed to read collection " + name);
				Vector vec = new Vector(collection.getChildCollectionCount());
				if (collection == null)
					return vec;
				String child;
				Collection childColl;
				Permission perm;
				Vector tmp;
				for (Iterator i = collection.collectionIterator();
					i.hasNext();
					) {
					child = (String) i.next();
					childColl =
						broker.getCollection(
							collection.getName() + '/' + (child));
					perm = childColl.getPermissions();
					tmp = new Vector(3);
					tmp.addElement(perm.getOwner());
					tmp.addElement(perm.getOwnerGroup());
					tmp.addElement(new Integer(perm.getPermissions()));
					vec.addElement(tmp);
				}
				return vec;
			} finally {
				brokerPool.release(broker);
			}
		}

		/**
		 *  Gets the hits attribute of the RpcConnection object
		 *
		 *@param  resultId            Description of the Parameter
		 *@param  user                Description of the Parameter
		 *@return                     The hits value
		 *@exception  EXistException  Description of the Exception
		 */
		public int getHits(User user, int resultId) throws EXistException {
			QueryResult qr = (QueryResult) resultSets.get(resultId);
			if (qr == null)
				throw new EXistException("result set unknown or timed out");
			if (qr.result == null)
				return 0;
			switch (qr.result.getType()) {
				case Value.isNodeList :
					return qr.result.getNodeList().getLength();
				default :
					return qr.result.getValueSet().getLength();
			}
		}

		/**
		 *  Get permissions for the given collection or resource
		 *
		 *@param  name                           Description of the Parameter
		 *@param  user                           Description of the Parameter
		 *@return                                The permissions value
		 *@exception  EXistException             Description of the Exception
		 *@exception  PermissionDeniedException  Description of the Exception
		 */
		public Hashtable getPermissions(User user, String name)
			throws EXistException, PermissionDeniedException {
			DBBroker broker = null;
			try {
				broker = brokerPool.get();
				User admin = brokerPool.getSecurityManager().getUser("admin");
				if (!name.startsWith("/"))
					name = '/' + name;
				if (!name.startsWith("/db"))
					name = "/db" + name;
				Collection collection = broker.getCollection(name);
				Permission perm = null;
				if (collection == null) {
					DocumentImpl doc =
						(DocumentImpl) broker.getDocument(admin, name);
					if (doc == null)
						throw new EXistException(
							"document or collection " + name + " not found");
					perm = doc.getPermissions();
				} else {
					perm = collection.getPermissions();
					LOG.debug("collection found finally");
				}
				Hashtable result = new Hashtable();
				result.put("owner", perm.getOwner());
				result.put("group", perm.getOwnerGroup());
				result.put("permissions", new Integer(perm.getPermissions()));
				return result;
			} finally {
				brokerPool.release(broker);
			}
		}

		/**
		 *  Gets the permissions attribute of the RpcConnection object
		 *
		 *@param  user                           Description of the Parameter
		 *@param  name                           Description of the Parameter
		 *@return                                The permissions value
		 *@exception  EXistException             Description of the Exception
		 *@exception  PermissionDeniedException  Description of the Exception
		 */
		public Hashtable getUser(User user, String name)
			throws EXistException, PermissionDeniedException {
			User u = brokerPool.getSecurityManager().getUser(name);
			if (u == null)
				throw new EXistException("user " + name + " does not exist");
			Hashtable tab = new Hashtable();
			tab.put("name", u.getName());
			Vector groups = new Vector();
			for (Iterator i = u.getGroups(); i.hasNext();)
				groups.addElement(i.next());
			tab.put("groups", groups);
			return tab;
		}

		/**
		 *  Description of the Method
		 *
		 *@param  name           Description of the Parameter
		 *@param  user           Description of the Parameter
		 *@return                Description of the Return Value
		 *@exception  Exception  Description of the Exception
		 */
		public boolean hasDocument(User user, String name) throws Exception {
			DBBroker broker = brokerPool.get();
			boolean r = (broker.getDocument(name) != null);
			brokerPool.release(broker);
			return r;
		}

		/**
		 *  Description of the Method
		 *
		 *@param  docName        Description of the Parameter
		 *@param  replace        Description of the Parameter
		 *@param  xml            Description of the Parameter
		 *@param  user           Description of the Parameter
		 *@return                Description of the Return Value
		 *@exception  Exception  Description of the Exception
		 */
		public boolean parse(
			User user,
			String xml,
			String docName,
			boolean replace)
			throws Exception {
			DBBroker broker = null;
			DocumentImpl doc;
			try {
				broker = brokerPool.get();
				long startTime = System.currentTimeMillis();
				if (parser == null)
					parser = new Parser(broker, user, replace);
				else {
					parser.setBroker(broker);
					parser.setUser(user);
					parser.setOverwrite(replace);
				}
				doc = parser.parse(xml, docName);
				broker.flush();
				//LOG.debug( "sync" );
				//broker.sync();
				LOG.debug(
					"parsing "
						+ docName
						+ " took "
						+ (System.currentTimeMillis() - startTime)
						+ "ms.");
				return doc != null;
			} catch (Exception e) {
				LOG.debug(e);
				throw e;
			} finally {
				brokerPool.release(broker);
			}
		}

		/**
		 *  Description of the Method
		 *
		 *@param  broker         Description of the Parameter
		 *@param  resultSet      Description of the Parameter
		 *@param  howmany        Description of the Parameter
		 *@param  start          Description of the Parameter
		 *@param  prettyPrint    Description of the Parameter
		 *@param  queryTime      Description of the Parameter
		 *@param  encoding       Description of the Parameter
		 *@return                Description of the Return Value
		 *@exception  Exception  Description of the Exception
		 */
		protected String printAll(
			DBBroker broker,
			NodeList resultSet,
			int howmany,
			int start,
			boolean prettyPrint,
			long queryTime,
			String encoding)
			throws Exception {
			if (resultSet.getLength() == 0)
				return "<?xml version=\"1.0\"?>\n"
					+ "<exist:result xmlns:exist=\"http://exist.sourceforge.net/NS/exist\" "
					+ "hitCount=\"0\"/>";
			Node n;
			Node nn;
			Element temp;
			DocumentImpl owner;
			if (howmany > resultSet.getLength() || howmany == 0)
				howmany = resultSet.getLength();

			if (start < 1 || start > resultSet.getLength())
				throw new EXistException("start parameter out of range");
			Serializer serializer = broker.getSerializer();
			serializer.setEncoding(encoding);
			if (prettyPrint) {
				StringWriter sout = new StringWriter();
				OutputFormat format = new OutputFormat("xml", encoding, true);
				format.setOmitXMLDeclaration(false);
				format.setOmitComments(false);
				format.setLineWidth(60);
				XMLSerializer xmlout = new XMLSerializer(sout, format);
				serializer.setContentHandler(xmlout);
				serializer.setLexicalHandler(xmlout);
				serializer.toSAX(
					(NodeSet) resultSet,
					start,
					howmany,
					queryTime);
				return sout.toString();
			} else
				return serializer.serialize(
					(NodeSet) resultSet,
					start,
					howmany,
					queryTime);
		}

		/**
		 *  Description of the Method
		 *
		 *@param  resultSet      Description of the Parameter
		 *@param  howmany        Description of the Parameter
		 *@param  start          Description of the Parameter
		 *@param  prettyPrint    Description of the Parameter
		 *@param  encoding       Description of the Parameter
		 *@return                Description of the Return Value
		 *@exception  Exception  Description of the Exception
		 */
		protected String printValues(
			ValueSet resultSet,
			int howmany,
			int start,
			boolean prettyPrint,
			String encoding)
			throws Exception {
			if (resultSet.getLength() == 0)
				return "<?xml version=\"1.0\" encoding=\""
					+ encoding
					+ "\"?>\n"
					+ "<exist:result xmlns:exist=\"http://exist.sourceforge.net/NS/exist\" "
					+ "hitCount=\"0\"/>";
			if (howmany > resultSet.getLength() || howmany == 0)
				howmany = resultSet.getLength();

			if (start < 1 || start > resultSet.getLength())
				throw new EXistException("start parameter out of range");
			Value value;
			Document dest = docBuilder.newDocument();
			Element root =
				dest.createElementNS(
					"http://exist.sourceforge.net/NS/exist",
					"exist:result");
			root.setAttribute(
				"xmlns:exist",
				"http://exist.sourceforge.net/NS/exist");
			root.setAttribute(
				"hitCount",
				Integer.toString(resultSet.getLength()));
			dest.appendChild(root);

			Element temp;
			for (int i = start - 1; i < start + howmany - 1; i++) {
				value = resultSet.get(i);
				switch (value.getType()) {
					case Value.isNumber :
						temp =
							dest.createElementNS(
								"http://exist.sourceforge.net/NS/exist",
								"exist:number");
						break;
					case Value.isString :
						temp =
							dest.createElementNS(
								"http://exist.sourceforge.net/NS/exist",
								"exist:string");
						break;
					case Value.isBoolean :
						temp =
							dest.createElementNS(
								"http://exist.sourceforge.net/NS/exist",
								"exist:boolean");
						break;
					default :
						LOG.debug("unknown type: " + value.getType());
						continue;
				}
				temp.appendChild(dest.createTextNode(value.getStringValue()));
				root.appendChild(temp);
			}
			StringWriter sout = new StringWriter();
			OutputFormat format =
				new OutputFormat("xml", encoding, prettyPrint);
			format.setOmitXMLDeclaration(false);
			format.setOmitComments(false);
			format.setLineWidth(60);
			XMLSerializer xmlout = new XMLSerializer(sout, format);
			try {
				xmlout.serialize(dest);
			} catch (IOException ioe) {
				LOG.warn(ioe);
				throw ioe;
			}
			return sout.toString();
		}

		/**
		 *  Description of the Method
		 *
		 *@param  xpath          Description of the Parameter
		 *@param  howmany        Description of the Parameter
		 *@param  start          Description of the Parameter
		 *@param  prettyPrint    Description of the Parameter
		 *@param  summary        Description of the Parameter
		 *@param  encoding       Description of the Parameter
		 *@param  user           Description of the Parameter
		 *@return                Description of the Return Value
		 *@exception  Exception  Description of the Exception
		 */
		public String query(
			User user,
			String xpath,
			int howmany,
			int start,
			boolean prettyPrint,
			boolean summary,
			String encoding)
			throws Exception {
			return query(
				user,
				xpath,
				howmany,
				start,
				prettyPrint,
				summary,
				encoding,
				null);
		}

		/**
		 *  Description of the Method
		 *
		 *@param  xpath          Description of the Parameter
		 *@param  howmany        Description of the Parameter
		 *@param  start          Description of the Parameter
		 *@param  prettyPrint    Description of the Parameter
		 *@param  summary        Description of the Parameter
		 *@param  encoding       Description of the Parameter
		 *@param  sortExpr       Description of the Parameter
		 *@param  user           Description of the Parameter
		 *@return                Description of the Return Value
		 *@exception  Exception  Description of the Exception
		 */
		public String query(
			User user,
			String xpath,
			int howmany,
			int start,
			boolean prettyPrint,
			boolean summary,
			String encoding,
			String sortExpr)
			throws Exception {
			long startTime = System.currentTimeMillis();
			Value resultValue = doQuery(user, xpath, null, null);
			if (resultValue == null)
				return "<?xml version=\"1.0\"?>\n"
					+ "<exist:result xmlns:exist=\"http://exist.sourceforge.net/NS/exist\" "
					+ "hitCount=\"0\"/>";
			String result;
			DBBroker broker = null;
			try {
				broker = brokerPool.get();
				broker.setRetrvMode(RelationalBroker.PRELOAD);
				switch (resultValue.getType()) {
					case Value.isNodeList :
						NodeList resultSet = resultValue.getNodeList();
						if (sortExpr != null) {
							SortedNodeSet sorted =
								new SortedNodeSet(brokerPool, sortExpr);
							sorted.addAll(resultSet);
							resultSet = sorted;
						}
						result =
							printAll(
								broker,
								resultSet,
								howmany,
								start,
								prettyPrint,
								(System.currentTimeMillis() - startTime),
								encoding);
						break;
					default :
						ValueSet valueSet = resultValue.getValueSet();
						result =
							printValues(
								valueSet,
								howmany,
								start,
								prettyPrint,
								encoding);
						break;
				}
				broker.setRetrvMode(RelationalBroker.SINGLE);
			} finally {
				brokerPool.release(broker);
			}
			return result;
		}

		public Vector query(User user, String xpath) throws Exception {
			return query(user, xpath, null, null);
		}

		/**
		 *  Description of the Method
		 *
		 *@param  xpath          Description of the Parameter
		 *@param  user           Description of the Parameter
		 *@return                Description of the Return Value
		 *@exception  Exception  Description of the Exception
		 */
		public Vector query(
			User user,
			String xpath,
			String docName,
			String s_id)
			throws Exception {
			long startTime = System.currentTimeMillis();
			Vector result = new Vector();
			NodeSet nodes = null;
			DocumentSet docs = null;
			if (docName != null && s_id != null) {
				long id = Long.parseLong(s_id);
				DocumentImpl doc;
				if (!documentCache.containsKey(docName)) {
					DBBroker broker = null;
					try {
						broker = brokerPool.get();
						doc = (DocumentImpl) broker.getDocument(docName);
						documentCache.put(docName, doc);
					} finally {
						brokerPool.release(broker);
					}
				} else
					doc = (DocumentImpl) documentCache.get(docName);
				NodeProxy node = new NodeProxy(doc, id);
				nodes = new ArraySet(1);
				nodes.add(node);
				docs = new DocumentSet();
				docs.add(node.doc);
			}
			Value resultValue = doQuery(user, xpath, docs, nodes);
			if (resultValue == null)
				return result;
			switch (resultValue.getType()) {
				case Value.isNodeList :
					NodeList resultSet = resultValue.getNodeList();
					NodeProxy p;
					Vector entry;
					for (Iterator i = ((NodeSet) resultSet).iterator();
						i.hasNext();
						) {
						p = (NodeProxy) i.next();
						entry = new Vector();
						entry.addElement(p.doc.getFileName());
						entry.addElement(Long.toString(p.getGID()));
						result.addElement(entry);
					}
					break;
				default :
					ValueSet valueSet = resultValue.getValueSet();
					Value val;
					for (int i = 0; i < valueSet.getLength(); i++) {
						val = valueSet.get(i);
						result.addElement(val.getStringValue());
					}
			}
			return result;
		}

		/**
		 *  Description of the Method
		 *
		 *@param  docName        Description of the Parameter
		 *@param  user           Description of the Parameter
		 *@exception  Exception  Description of the Exception
		 */
		public void remove(User user, String docName) throws Exception {
			DBBroker broker = brokerPool.get();
			try {
				if (broker.getDocument(user, docName) == null)
					throw new EXistException(
						"document [" + docName + "] not found!");
				broker.removeDocument(user, docName);
			} finally {
				brokerPool.release(broker);
			}
		}

		/**
		 *  Description of the Method
		 *
		 *@param  name           Description of the Parameter
		 *@param  user           Description of the Parameter
		 *@return                Description of the Return Value
		 *@exception  Exception  Description of the Exception
		 */
		public boolean removeCollection(User user, String name)
			throws Exception {
			DBBroker broker = brokerPool.get();
			try {
				if (broker.getCollection(name) == null)
					return false;
				LOG.debug("removing collection " + name);
				return broker.removeCollection(user, name);
			} finally {
				brokerPool.release(broker);
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
			org.exist.security.SecurityManager manager =
				brokerPool.getSecurityManager();
			if (!manager.hasAdminPrivileges(user))
				throw new PermissionDeniedException("you are not allowed to remove users");

			manager.deleteUser(name);
			return true;
		}

		/**
		 *  Description of the Method
		 *
		 *@param  docName        Description of the Parameter
		 *@param  s_id           Description of the Parameter
		 *@param  prettyPrint    Description of the Parameter
		 *@param  encoding       Description of the Parameter
		 *@param  user           Description of the Parameter
		 *@return                Description of the Return Value
		 *@exception  Exception  Description of the Exception
		 */
		public String retrieve(
			User user,
			String docName,
			String s_id,
			boolean prettyPrint,
			String encoding)
			throws Exception {
			DBBroker broker = brokerPool.get();
			try {
				long id = Long.parseLong(s_id);
				DocumentImpl doc;
				if (!documentCache.containsKey(docName)) {
					doc = (DocumentImpl) broker.getDocument(docName);
					documentCache.put(docName, doc);
				} else
					doc = (DocumentImpl) documentCache.get(docName);

				NodeProxy node = new NodeProxy(doc, id);
				Serializer serializer = broker.getSerializer();
				serializer.setEncoding(encoding);
				serializer.setIndent(prettyPrint);
				return serializer.serialize(node);
			} finally {
				brokerPool.release(broker);
			}
		}

		/**
		 *  Description of the Method
		 *
		 *@param  resultId       Description of the Parameter
		 *@param  num            Description of the Parameter
		 *@param  prettyPrint    Description of the Parameter
		 *@param  encoding       Description of the Parameter
		 *@param  user           Description of the Parameter
		 *@return                Description of the Return Value
		 *@exception  Exception  Description of the Exception
		 */
		public String retrieve(
			User user,
			int resultId,
			int num,
			boolean prettyPrint,
			String encoding)
			throws Exception {
			DBBroker broker = brokerPool.get();
			try {
				QueryResult qr = (QueryResult) resultSets.get(resultId);
				if (qr == null)
					throw new EXistException("result set unknown or timed out");
				switch (qr.result.getType()) {
					case Value.isNodeList :
						NodeList resultSet = qr.result.getNodeList();
						NodeProxy proxy = ((NodeSet) resultSet).get(num);
						if (proxy == null)
							throw new EXistException("index out of range");
						Serializer serializer = broker.getSerializer();
						serializer.setEncoding(encoding);
						serializer.setIndent(prettyPrint);
						return serializer.serialize(proxy);
					default :
						ValueSet valueSet = qr.result.getValueSet();
						Value val = valueSet.get(num);
						return val.getStringValue();
				}
			} finally {
				brokerPool.release(broker);
			}
		}

		/**  Main processing method for the RpcConnection object */
		public void run() {
			synchronized (this) {
				while (!terminate)
					try {
						this.wait(500);
					} catch (InterruptedException inte) {
					}

			}
			// broker.shutdown();
		}

		/**
		 *  Sets the permissions attribute of the RpcConnection object
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
			DBBroker broker = null;
			try {
				broker = brokerPool.get();
				org.exist.security.SecurityManager manager =
					brokerPool.getSecurityManager();
				Collection collection = broker.getCollection(resource);
				if (collection == null) {
					DocumentImpl doc =
						(DocumentImpl) broker.getDocument(user, resource);
					if (doc == null)
						throw new EXistException(
							"document or collection "
								+ resource
								+ " not found");
					LOG.debug("changing permissions on document " + resource);
					Permission perm = doc.getPermissions();
					if (perm.getOwner().equals(user.getName())
						|| manager.hasAdminPrivileges(user)) {
						if (owner != null) {
							perm.setOwner(owner);
							perm.setGroup(ownerGroup);
						}
						if (permissions != null)
							perm.setPermissions(permissions);
						broker.saveCollection(doc.getCollection());
						broker.flush();
						broker.sync();
						return true;
					} else
						throw new PermissionDeniedException("not allowed to change permissions");
				} else {
					LOG.debug("changing permissions on collection " + resource);
					Permission perm = collection.getPermissions();
					if (perm.getOwner().equals(user.getName())
						|| manager.hasAdminPrivileges(user)) {
						if (permissions != null)
							perm.setPermissions(permissions);
						if (owner != null) {
							perm.setOwner(owner);
							perm.setGroup(ownerGroup);
						}
						broker.saveCollection(collection);
						broker.flush();
						broker.sync();
						return true;
					} else
						throw new PermissionDeniedException("not allowed to change permissions");
				}
			} catch (SyntaxException e) {
				throw new EXistException(e.getMessage());
			} catch (PermissionDeniedException e) {
				throw new EXistException(e.getMessage());
			} finally {
				brokerPool.release(broker);
			}
		}

		/**
		 *  Sets the password attribute of the RpcConnection object
		 *
		 *@param  user                           The new password value
		 *@param  name                           The new password value
		 *@param  passwd                         The new password value
		 *@param  groups                         The new user value
		 *@return                                Description of the Return Value
		 *@exception  EXistException             Description of the Exception
		 *@exception  PermissionDeniedException  Description of the Exception
		 */
		public boolean setUser(
			User user,
			String name,
			String passwd,
			Vector groups)
			throws EXistException, PermissionDeniedException {
			org.exist.security.SecurityManager manager =
				brokerPool.getSecurityManager();
			User u;
			if (!manager.hasUser(name)) {
				if (!manager.hasAdminPrivileges(user))
					throw new PermissionDeniedException("not allowed to create user");
				u = new User(name);
				u.setPasswordDigest(passwd);
			} else {
				u = manager.getUser(name);
				if (!(u.getName().equals(user.getName())
					|| manager.hasAdminPrivileges(user)))
					throw new PermissionDeniedException("you are not allowed to change this user");
				u.setPasswordDigest(passwd);
			}
			String g;
			for (Iterator i = groups.iterator(); i.hasNext();) {
				g = (String) i.next();
				if (!u.hasGroup(g))
					u.addGroup(g);
			}
			manager.setUser(u);
			return true;
		}

		/**
		 *  Description of the Method
		 *
		 *@param  xpath          Description of the Parameter
		 *@param  user           Description of the Parameter
		 *@return                Description of the Return Value
		 *@exception  Exception  Description of the Exception
		 */
		public Hashtable summary(User user, String xpath) throws Exception {
			long startTime = System.currentTimeMillis();
			Value resultValue = doQuery(user, xpath, null, null);
			if (resultValue == null)
				return new Hashtable();
			NodeList resultSet = resultValue.getNodeList();
			HashMap map = new HashMap();
			HashMap doctypes = new HashMap();
			NodeProxy p;
			String docName;
			DocumentType doctype;
			NodeCount counter;
			DoctypeCount doctypeCounter;
			for (Iterator i = ((NodeSet) resultSet).iterator(); i.hasNext();) {
				p = (NodeProxy) i.next();
				docName = p.doc.getFileName();
				doctype = p.doc.getDoctype();
				if (map.containsKey(docName)) {
					counter = (NodeCount) map.get(docName);
					counter.inc();
				} else {
					counter = new NodeCount(p.doc);
					map.put(docName, counter);
				}
				if (doctype == null)
					continue;
				if (doctypes.containsKey(doctype.getName())) {
					doctypeCounter =
						(DoctypeCount) doctypes.get(doctype.getName());
					doctypeCounter.inc();
				} else {
					doctypeCounter = new DoctypeCount(doctype);
					doctypes.put(doctype.getName(), doctypeCounter);
				}
			}
			Hashtable result = new Hashtable();
			result.put(
				"queryTime",
				new Integer((int) (System.currentTimeMillis() - startTime)));
			result.put("hits", new Integer(resultSet.getLength()));
			Vector documents = new Vector();
			Vector hitsByDoc;
			for (Iterator i = map.values().iterator(); i.hasNext();) {
				counter = (NodeCount) i.next();
				hitsByDoc = new Vector();
				hitsByDoc.addElement(counter.doc.getFileName());
				hitsByDoc.addElement(new Integer(counter.doc.getDocId()));
				hitsByDoc.addElement(new Integer(counter.count));
				documents.addElement(hitsByDoc);
			}
			result.put("documents", documents);
			Vector dtypes = new Vector();
			Vector hitsByType;
			DoctypeCount docTemp;
			for (Iterator i = doctypes.values().iterator(); i.hasNext();) {
				docTemp = (DoctypeCount) i.next();
				hitsByType = new Vector();
				hitsByType.addElement(docTemp.doctype.getName());
				hitsByType.addElement(new Integer(docTemp.count));
				dtypes.addElement(hitsByType);
			}
			result.put("doctypes", dtypes);
			return result;
		}

		/**
		 *  Description of the Method
		 *
		 *@param  resultId            Description of the Parameter
		 *@param  user                Description of the Parameter
		 *@return                     Description of the Return Value
		 *@exception  EXistException  Description of the Exception
		 */
		public Hashtable summary(User user, int resultId)
			throws EXistException {
			long startTime = System.currentTimeMillis();
			QueryResult qr = (QueryResult) resultSets.get(resultId);
			if (qr == null)
				throw new EXistException("result set unknown or timed out");
			Hashtable result = new Hashtable();
			result.put("queryTime", new Integer((int) qr.queryTime));
			if (qr.result == null) {
				result.put("hits", new Integer(0));
				return result;
			}
			DBBroker broker = brokerPool.get();
			try {
				NodeList resultSet = qr.result.getNodeList();
				HashMap map = new HashMap();
				HashMap doctypes = new HashMap();
				NodeProxy p;
				String docName;
				DocumentType doctype;
				NodeCount counter;
				DoctypeCount doctypeCounter;
				for (Iterator i = ((NodeSet) resultSet).iterator();
					i.hasNext();
					) {
					p = (NodeProxy) i.next();
					docName = p.doc.getFileName();
					doctype = p.doc.getDoctype();
					if (map.containsKey(docName)) {
						counter = (NodeCount) map.get(docName);
						counter.inc();
					} else {
						counter = new NodeCount(p.doc);
						map.put(docName, counter);
					}
					if (doctype == null)
						continue;
					if (doctypes.containsKey(doctype.getName())) {
						doctypeCounter =
							(DoctypeCount) doctypes.get(doctype.getName());
						doctypeCounter.inc();
					} else {
						doctypeCounter = new DoctypeCount(doctype);
						doctypes.put(doctype.getName(), doctypeCounter);
					}
				}
				result.put("hits", new Integer(resultSet.getLength()));
				Vector documents = new Vector();
				Vector hitsByDoc;
				for (Iterator i = map.values().iterator(); i.hasNext();) {
					counter = (NodeCount) i.next();
					hitsByDoc = new Vector();
					hitsByDoc.addElement(counter.doc.getFileName());
					hitsByDoc.addElement(new Integer(counter.doc.getDocId()));
					hitsByDoc.addElement(new Integer(counter.count));
					documents.addElement(hitsByDoc);
				}
				result.put("documents", documents);
				Vector dtypes = new Vector();
				Vector hitsByType;
				DoctypeCount docTemp;
				for (Iterator i = doctypes.values().iterator(); i.hasNext();) {
					docTemp = (DoctypeCount) i.next();
					hitsByType = new Vector();
					hitsByType.addElement(docTemp.doctype.getName());
					hitsByType.addElement(new Integer(docTemp.count));
					dtypes.addElement(hitsByType);
				}
				result.put("doctypes", dtypes);
				return result;
			} finally {
				brokerPool.release(broker);
			}
		}

		/**  Description of the Method */
		public void synchronize() {
			documentCache.clear();
		}

		/**  Description of the Method */
		public void terminate() {
			terminate = true;
		}

		/**
		 *  Description of the Class
		 *
		 *@author     wolf
		 *@created    28. Mai 2002
		 */
		class DoctypeCount {
			int count = 1;
			DocumentType doctype;

			/**
			 *  Constructor for the DoctypeCount object
			 *
			 *@param  doctype  Description of the Parameter
			 */
			public DoctypeCount(DocumentType doctype) {
				this.doctype = doctype;
			}

			/**  Description of the Method */
			public void inc() {
				count++;
			}
		}

		/**
		 *  Description of the Class
		 *
		 *@author     wolf
		 *@created    28. Mai 2002
		 */
		class NodeCount {
			int count = 1;
			DocumentImpl doc;

			/**
			 *  Constructor for the NodeCount object
			 *
			 *@param  doc  Description of the Parameter
			 */
			public NodeCount(DocumentImpl doc) {
				this.doc = doc;
			}

			/**  Description of the Method */
			public void inc() {
				count++;
			}
		}
	}
}
