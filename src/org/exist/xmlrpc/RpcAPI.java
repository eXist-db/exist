
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
import java.util.Hashtable;

import java.util.Vector;
import org.exist.*;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.xml.sax.SAXException;

/**
 *  Description of the Interface
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    21. Juni 2002
 */
public interface RpcAPI {

	/**
	 * Shut down the database. This will not stop the
	 * XML-RPC server.
	 * 
	 * @return boolean
	 */
	public boolean shutdown(User user) throws PermissionDeniedException;

	public boolean sync(User user);
	 
    /**
     *  retrieve document by name. XML content is indented if prettyPrint is set
     *  to >=0. Use supplied encoding for output. This method is provided to
     *  retrieve a document with encodings other than UTF-8. Since the data is
     *  handled as binary data, character encodings are preserved. byte[]-values
     *  are automatically BASE64-encoded by the XMLRPC library.
     *
     *@param  name                           the document's name.
     *@param  prettyPrint                    pretty print XML if >0.
     *@param  encoding                       character encoding to use.
     *@param  user                           Description of the Parameter
     *@return                                The document value
     *@exception  EXistException             Description of the Exception
     *@exception  PermissionDeniedException  Description of the Exception
     */
    byte[] getDocument( User user, String name, String encoding, int prettyPrint )
         throws EXistException, PermissionDeniedException;


    byte[] getDocument( User user, String name, String encoding, int prettyPrint,
                        String stylesheet )
         throws EXistException, PermissionDeniedException;


    /**
     *  does the document identified by <code>name</code> exist in the
     *  repository?
     *
     *@param  name                           Description of the Parameter
     *@param  user                           Description of the Parameter
     *@return                                Description of the Return Value
     *@exception  EXistException             Description of the Exception
     *@exception  PermissionDeniedException  Description of the Exception
     */
    boolean hasDocument( User user, String name )
         throws EXistException, PermissionDeniedException;


    /**
     *  get a list of all documents contained in the repository.
     *
     *@param  user                           Description of the Parameter
     *@return                                list of document paths
     *@exception  EXistException             Description of the Exception
     *@exception  PermissionDeniedException  Description of the Exception
     */
    Vector getDocumentListing( User user )
         throws EXistException, PermissionDeniedException;


    /**
     *  get a list of all documents contained in the collection.
     *
     *@param  collection                     the collection to use.
     *@param  user                           Description of the Parameter
     *@return                                list of document paths
     *@exception  EXistException             Description of the Exception
     *@exception  PermissionDeniedException  Description of the Exception
     */
    Vector getDocumentListing( User user, String collection )
         throws EXistException, PermissionDeniedException;

    Hashtable listDocumentPermissions(User user, String name)
        throws EXistException, PermissionDeniedException;
    
    Hashtable listCollectionPermissions(User user, String name)
        throws EXistException, PermissionDeniedException;
        
    /**
     *  describe a collection This method will return a struct with the
     *  following fields:
     *  <tableborder="1">
     *
     *    <tr>
     *
     *      <td>
     *        documents
     *      </td>
     *
     *      <td>
     *        array of all document names contained in this collection.
     *      </td>
     *
     *    </tr>
     *
     *    <tr>
     *
     *      <td>
     *        collections
     *      </td>
     *
     *      <td>
     *        an array containing the names of all subcollections in this
     *        collection.
     *      </td>
     *
     *    </tr>
     *
     *    <tr>
     *
     *      <td>
     *        name
     *      </td>
     *
     *      <td>
     *        the collection's name
     *      </td>
     *
     *    </tr>
     *
     *  </table>
     *
     *
     *@param  rootCollection                 Description of the Parameter
     *@param  user                           Description of the Parameter
     *@return                                The collectionDesc value
     *@exception  EXistException             Description of the Exception
     *@exception  PermissionDeniedException  Description of the Exception
     */
    Hashtable getCollectionDesc( User user, String rootCollection )
         throws EXistException, PermissionDeniedException;


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
    byte[] retrieve( User user, String doc, String id )
         throws EXistException, PermissionDeniedException;


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
    byte[] retrieve( User user, String doc, String id, int prettyPrint )
         throws EXistException, PermissionDeniedException;


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
    byte[] retrieve( User user, String doc, String id, int prettyPrint, String encoding )
         throws EXistException, PermissionDeniedException;


    /**
     *  <p>
     *
     *  execute XPath query and return a list of results. If the result is a
     *  node set, it will be returned as array of String[][2], which represents
     *  a two dimensional table. Every row in this table consists of a
     *  document-name / node-id pair. e.g.:</p>
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
     *  <p>
     *
     *  You may use this information with the retrieve-call to retrieve the
     *  actual nodes.</p> <p>
     *
     *  Otherwise, if the result is a set of values, each value will be
     *  converted to string. In this case the return type is an array of strings
     *  String[].</p>
     *
     *@param  xpath                          the XPath query to execute.
     *@param  user                           Description of the Parameter
     *@return                                string[][2], if result is a node
     *      set, string[] otherwise.
     *@exception  EXistException             Description of the Exception
     *@exception  PermissionDeniedException  Description of the Exception
     */
    Vector query( User user, String xpath )
         throws EXistException, PermissionDeniedException;

    Vector query( User user, String xpath, String docId,
        String s_id) throws EXistException, PermissionDeniedException;
        
    Vector query( User user, byte[] xpath )
         throws EXistException, PermissionDeniedException;

	Hashtable queryP(User user, byte[] xpath)
		throws EXistException, PermissionDeniedException;
	
	Hashtable queryP(User user, byte[] xpath, byte[] sortExpr)
		throws EXistException, PermissionDeniedException;
	
	Hashtable queryP(User user, byte[] xpath, String docName, 
		String s_id, byte[] sortExpr)
		throws EXistException, PermissionDeniedException;
		
	Hashtable queryP(User user, byte[] xpath, String docName, 
		String s_id)
		throws EXistException, PermissionDeniedException; 

    /**
     *  <p>
     *
     *  execute XPath query and return a list of results. The query is passed as
     *  base64 encoded string data. Use this method if your query contains
     *  characters in an encoding other than UTF-8. If the result is a node set,
     *  it will be returned as array of String[][2], which represents a two
     *  dimensional table. Every row in this table consists of a document-name /
     *  node-id pair. e.g.:</p>
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
     *  <p>
     *
     *  You may use this information with the retrieve-call to retrieve the
     *  actual nodes.</p> <p>
     *
     *  Otherwise, if the result is a set of values, each value will be
     *  converted to string. In this case the return type is an array of strings
     *  String[].</p>
     *
     *@param  xpath                          the XPath query to execute.
     *@param  encoding                       the character encoding used for the
     *      query string.
     *@param  user                           Description of the Parameter
     *@return                                string[][2], if result is a node
     *      set, string[] otherwise.
     *@exception  EXistException             Description of the Exception
     *@exception  PermissionDeniedException  Description of the Exception
     */
    Vector query( User user, byte[] xpath, String encoding )
         throws EXistException, PermissionDeniedException;


    /**
     *  execute XPath query and return the resulting node set as a new document.
     *  howmany nodes will be included, starting at position <code>start</code>.
     *  If <code>prettyPrint</code> is set to >0 (true), results are pretty
     *  printed.
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
     *@depreceated                           use Vector query() or int
     *      executeQuery() instead
     */
    byte[] query( User user, String xpath, String encoding, int howmany,
                  int start, int prettyPrint )
         throws EXistException, PermissionDeniedException;


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
     *@depreceated                           use Vector query() or int
     *      executeQuery() instead
     */
    byte[] query( User user, String xpath, String encoding, int howmany,
                  int start, int prettyPrint, String sortExpr )
         throws EXistException, PermissionDeniedException;


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
     *@depreceated                           use Vector query() or int
     *      executeQuery() instead
     */
    Hashtable querySummary( User user, String xpath )
         throws EXistException, PermissionDeniedException;


    /**
     *  parse an XML document and store it into the database. The document will
     *  later be identified by <code>docName</code>. Some xmlrpc clients seem to
     *  have problems with character encodings when sending xml content. To
     *  avoid this, parse() accepts the xml document content as byte[]. If
     *  <code>overwrite</code> is >0, an existing document with the same name
     *  will be replaced by the new document.
     *
     *@param  xmlData                        Description of the Parameter
     *@param  docName                        Description of the Parameter
     *@param  user                           Description of the Parameter
     *@return                                Description of the Return Value
     *@exception  EXistException             Description of the Exception
     *@exception  PermissionDeniedException  Description of the Exception
     */
    boolean parse( User user, byte[] xmlData, String docName )
         throws EXistException, PermissionDeniedException;


    /**
     *  parse an XML document and store it into the database. The document will
     *  later be identified by <code>docName</code>. Some xmlrpc clients seem to
     *  have problems with character encodings when sending xml content. To
     *  avoid this, parse() accepts the xml document content as byte[]. If
     *  <code>overwrite</code> is >0, an existing document with the same name
     *  will be replaced by the new document.
     *
     *@param  xmlData                        Description of the Parameter
     *@param  docName                        Description of the Parameter
     *@param  overwrite                      Description of the Parameter
     *@param  user                           Description of the Parameter
     *@return                                Description of the Return Value
     *@exception  EXistException             Description of the Exception
     *@exception  PermissionDeniedException  Description of the Exception
     */
    boolean parse( User user, byte[] xmlData, String docName,
                   int overwrite )
         throws EXistException, PermissionDeniedException;


    boolean parse( User user, String xml, String docName,
                   int overwrite )
         throws EXistException, PermissionDeniedException;


    boolean parse( User user, String xml, String docName )
         throws EXistException, PermissionDeniedException;


    /**
     *  remove a document from the repository.
     *
     *@param  docName                        Description of the Parameter
     *@param  user                           Description of the Parameter
     *@return                                Description of the Return Value
     *@exception  EXistException             Description of the Exception
     *@exception  PermissionDeniedException  Description of the Exception
     */
    boolean remove( User user, String docName )
         throws EXistException, PermissionDeniedException;


    /**
     *  remove a complete collection from the repository.
     *
     *@param  name                           Description of the Parameter
     *@param  user                           Description of the Parameter
     *@return                                Description of the Return Value
     *@exception  EXistException             Description of the Exception
     *@exception  PermissionDeniedException  Description of the Exception
     */
    boolean removeCollection( User user, String name )
         throws EXistException, PermissionDeniedException;


    boolean createCollection( User user, String name )
         throws EXistException, PermissionDeniedException;


    /**
     *  Execute XPath query and return a reference to the result set. The
     *  returned reference may be used later to get a summary of results or
     *  retrieve the actual hits.
     *
     *@param  xpath                          Description of the Parameter
     *@param  encoding                       Description of the Parameter
     *@param  user                           Description of the Parameter
     *@return                                Description of the Return Value
     *@exception  EXistException             Description of the Exception
     *@exception  PermissionDeniedException  Description of the Exception
     */
    int executeQuery( User user, byte[] xpath, String encoding )
         throws EXistException, PermissionDeniedException;


    int executeQuery( User user, byte[] xpath )
         throws EXistException, PermissionDeniedException;


    int executeQuery( User user, String xpath )
         throws EXistException, PermissionDeniedException;


    /**
     *  Retrieve a summary of the result set identified by it's result-set-id.
     *  This method returns a struct with the following fields:
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
     *@param  resultId                       Description of the Parameter
     *@param  user                           Description of the Parameter
     *@return                                Description of the Return Value
     *@exception  EXistException             Description of the Exception
     *@exception  PermissionDeniedException  Description of the Exception
     */
    Hashtable querySummary( User user, int resultId )
         throws EXistException, PermissionDeniedException;


    Hashtable getPermissions( User user, String resource )
         throws EXistException, PermissionDeniedException;


    /**
     *  Get the number of hits in the result set identified by it's
     *  result-set-id.
     *
     *@param  resultId                       Description of the Parameter
     *@param  user                           Description of the Parameter
     *@return                                The hits value
     *@exception  EXistException             Description of the Exception
     *@exception  PermissionDeniedException  Description of the Exception
     */
    int getHits( User user, int resultId )
         throws EXistException, PermissionDeniedException;


    /**
     *  Retrieve a single result from the result-set identified by resultId. The
     *  XML fragment at position num in the result set is returned.
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
    byte[] retrieve( User user, int resultId, int num, int prettyPrint,
                     String encoding )
         throws EXistException, PermissionDeniedException;


    boolean setUser( User user, String name, String passwd,
                     Vector groups, String home )
         throws EXistException, PermissionDeniedException;

	boolean setUser( User user, String name, String passwd,
		Vector groups )
		throws EXistException, PermissionDeniedException;

    boolean setPermissions( User user, String resource, String permissions )
        throws EXistException, PermissionDeniedException;

    boolean setPermissions( User user, String resource, int permissions )
        throws EXistException, PermissionDeniedException;
        
    boolean setPermissions( User user, String resource,
                            String owner, String ownerGroup, String permissions )
        throws EXistException, PermissionDeniedException;

    boolean setPermissions( User user, String resource,
                            String owner, String ownerGroup, int permissions )
        throws EXistException, PermissionDeniedException;
             
    Hashtable getUser( User user, String name )
         throws EXistException, PermissionDeniedException;
         
    Vector getUsers(User user)
         throws EXistException, PermissionDeniedException;


    boolean removeUser( User user, String name )
         throws EXistException, PermissionDeniedException;
         
	public Vector getIndexedElements(User user, String collectionName, boolean inclusive)
		throws EXistException, PermissionDeniedException;
		
	public Vector scanIndexTerms(User user, String collectionName,
		String start, String end, boolean inclusive)
		throws PermissionDeniedException, EXistException;
		
	public void releaseQueryResult(int handle);
	
	public int xupdate(User user, String collectionName, byte[] xupdate)
		throws PermissionDeniedException, EXistException, SAXException;
		
	public int xupdateResource(User user, String resource, byte[] xupdate)
		throws PermissionDeniedException, EXistException, SAXException;
}


