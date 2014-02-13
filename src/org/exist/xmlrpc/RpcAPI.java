/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2010 The eXist Project
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
package org.exist.xmlrpc;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.security.internal.aider.ACEAider;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.xml.sax.SAXException;

/**
 *  Defines the methods callable through the XMLRPC interface.
 *
 * @author     Wolfgang Meier <wolfgang@exist-db.org>
 * modified by {Marco.Tampucci, Massimo.Martinelli} @isti.cnr.it
 */
public interface RpcAPI {

	public final static String SORT_EXPR = "sort-expr";
	public final static String NAMESPACES = "namespaces";
	public final static String VARIABLES = "variables";
	public final static String BASE_URI = "base-uri";
	public final static String STATIC_DOCUMENTS = "static-documents";
    public final static String PROTECTED_MODE = "protected";
    public static final String ERROR = "error";
	public static final String LINE = "line";
	public static final String COLUMN = "column";
    public static final String MODULE_LOAD_PATH = "module-load-path";

    /**
	 * Return the database version.
	 * 
	 * @return database version
	 */
    public String getVersion();

	/**
	 * Shut down the database immediately.
	 * 
	 * @return true if the shutdown succeeded, false otherwise
	 */
	public boolean shutdown() throws PermissionDeniedException;

	/**
	 * Shut down the database after the specified delay (in milliseconds).
	 * 
	 * @return true if the shutdown succeeded, false otherwise
	 * @throws PermissionDeniedException
	 */
	public boolean shutdown(String delay) throws PermissionDeniedException;
	
	/**
	 * Shut down the database after the specified delay (in milliseconds).
	 * 
	 * @return true if the shutdown succeeded, false otherwise
	 * @throws PermissionDeniedException
	 */
	public boolean shutdown(long delay) throws PermissionDeniedException;
	
	public boolean sync();
	
	/**
	 * Returns true if XACML is enabled for the current database instance
	 * @return if XACML is enabled
	 */
	public boolean isXACMLEnabled();

	/**
	 *  Retrieve document by name. XML content is indented if prettyPrint is set
	 *  to >=0. Use supplied encoding for output. 
	 * 
	 *  This method is provided to retrieve a document with encodings other than UTF-8. Since the data is
	 *  handled as binary data, character encodings are preserved. byte[]-values
	 *  are automatically BASE64-encoded by the XMLRPC library.
	 *
	 *@param  name                           the document's name.
	 *@param  prettyPrint                    pretty print XML if >0.
	 *@param  encoding                       character encoding to use.
	 *@return   Document data as binary array.
	 */
	byte[] getDocument(String name, String encoding, int prettyPrint)
		throws EXistException, PermissionDeniedException;

	/**
	 *  Retrieve document by name. XML content is indented if prettyPrint is set
	 *  to >=0. Use supplied encoding for output and apply the specified stylesheet. 
	 * 
	 *  This method is provided to retrieve a document with encodings other than UTF-8. Since the data is
	 *  handled as binary data, character encodings are preserved. byte[]-values
	 *  are automatically BASE64-encoded by the XMLRPC library.
	 *
	 *@param  name                           the document's name.
	 *@param  prettyPrint                    pretty print XML if >0.
	 *@param  encoding                       character encoding to use.
	 *@return                                The document value
	 */
	byte[] getDocument(String name, String encoding, int prettyPrint, String stylesheet)
		throws EXistException, PermissionDeniedException;
	
     /**
	 * Retrieve document by name.  All optional output parameters are passed as key/value pairs
	 * int the hashtable <code>parameters</code>.
	 * 
	 * Valid keys may either be taken from {@link javax.xml.transform.OutputKeys} or 
	 * {@link org.exist.storage.serializers.EXistOutputKeys}. For example, the encoding is identified by
	 * the value of key {@link javax.xml.transform.OutputKeys#ENCODING}.
	 *
	 *@param  name                           the document's name.
	 *@param  parameters                      HashMap of parameters.
	 *@return                                The document value
	 */		
	byte[] getDocument(String name, HashMap<String, Object> parameters)
			throws EXistException, PermissionDeniedException;	
		

	String getDocumentAsString(String name, int prettyPrint)
			throws EXistException, PermissionDeniedException;
			
	String getDocumentAsString(String name, int prettyPrint, String stylesheet)
		throws EXistException, PermissionDeniedException;
	
	String getDocumentAsString(String name, HashMap<String, Object> parameters)
		throws EXistException, PermissionDeniedException;
	
	/**
	 * Retrieve the specified document, but limit the number of bytes transmitted
	 * to avoid memory shortage on the server.
	 * 
	 * @param name
	 * @param parameters
	 * @throws EXistException
	 * @throws PermissionDeniedException
	 */
	HashMap<String, Object> getDocumentData(String name, HashMap<String, Object> parameters)
	throws EXistException, PermissionDeniedException;
	
	HashMap<String, Object> getNextChunk(String handle, int offset)
    throws EXistException, PermissionDeniedException;
	
	HashMap<String, Object> getNextExtendedChunk(String handle, String offset)
    throws EXistException, PermissionDeniedException;
	
	byte[] getBinaryResource(String name)
		throws EXistException, PermissionDeniedException, URISyntaxException;
	
	/**
	 *  Does the document identified by <code>name</code> exist in the
	 *  repository?
	 *
	 *@param  name                           Description of the Parameter
	 *@return                                Description of the Return Value
	 *@exception  EXistException             Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	boolean hasDocument(String name) throws EXistException, PermissionDeniedException, URISyntaxException;

	/**
	 *  Does the Collection identified by <code>name</code> exist in the
	 *  repository?
	 *
	 *@param  name                           Description of the Parameter
	 *@return                                Description of the Return Value
	 *@exception  EXistException             Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	boolean hasCollection(String name) throws EXistException, PermissionDeniedException, URISyntaxException;

        Vector<String> getCollectionListing(final String collection)
		throws EXistException, PermissionDeniedException, URISyntaxException;
        
	/**
	 *  Get a list of all documents contained in the database.
	 *
	 *@return  list of document paths
	 *@exception  EXistException             Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	Vector<String> getDocumentListing() throws EXistException, PermissionDeniedException;

	/**
	 *  Get a list of all documents contained in the collection.
	 *
	 *@param  collection                     the collection to use.
	 *@return                                list of document paths
	 *@exception  EXistException             Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	Vector<String> getDocumentListing(String collection)
		throws EXistException, PermissionDeniedException, URISyntaxException;

	HashMap<String, List<Object>> listDocumentPermissions(String name)
		throws EXistException, PermissionDeniedException, URISyntaxException;

	HashMap<XmldbURI, List<Object>> listCollectionPermissions(String name)
		throws EXistException, PermissionDeniedException, URISyntaxException;

        /**
         * Determines whether a Collection exists in the database
         * and whether the user may open the collection
         * 
         * @param collectionUri The URI of the collection of interest
         * 
         * @return true if the collection exists and the user can open it, false if the collection does not exist
         * @throws PermissionDeniedException If the user does not have permission to open the collection
         */
        boolean existsAndCanOpenCollection(final String collectionUri) throws EXistException, PermissionDeniedException;
        
	/**
	 *  Describe a collection: returns a struct with the  following fields:
	 *  
	 * <pre>
	 *	name				The name of the collection
	 *	
	 *	owner				The name of the user owning the collection.
	 *	
	 *	group				The group owning the collection.
	 *	
	 *	permissions	The permissions that apply to this collection (int value)
	 *	
	 *	created			The creation date of this collection (long value)
	 *	
	 *	collections		An array containing the names of all subcollections.
	 *	
	 *	documents		An array containing a struct for each document in the collection.
	 *	</pre>
	 *
	 *	Each of the elements in the "documents" array is another struct containing the properties
	 *	of the document:
	 *
	 *	<pre>
	 *	name				The full path of the document.
	 *	
	 *	owner				The name of the user owning the document.
	 *	
	 *	group				The group owning the document.
	 *	
	 *	permissions	The permissions that apply to this document (int)
	 *	
	 *	type					Type of the resource: either "XMLResource" or "BinaryResource"
	 *	</pre>
	 *
	 *@param  rootCollection                 Description of the Parameter
	 *@return                                The collectionDesc value
	 *@exception  EXistException             Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	HashMap<String, Object> getCollectionDesc(String rootCollection)
		throws EXistException, PermissionDeniedException;

	HashMap<String, Object> describeCollection(String collectionName)
		throws EXistException, PermissionDeniedException;
	
	HashMap<String, Object> describeResource(String resourceName)
		throws EXistException, PermissionDeniedException;
	
	/**
	 * Returns the number of resources in the collection identified by
	 * collectionName.
	 * 
	 * @param collectionName
	 * @return Number of resources
	 * @throws EXistException
	 * @throws PermissionDeniedException
	 */
	int getResourceCount(String collectionName)
		throws EXistException, PermissionDeniedException, URISyntaxException;
	
	/**
	 *  Retrieve a single node from a document. The node is identified by it's
	 *  internal id.
	 *
	 *@param  doc                            the document containing the node
	 *@param  id                             the node's internal id
	 *@return                                Description of the Return Value
	 *@exception  EXistException             Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	byte[] retrieve(String doc, String id)
		throws EXistException, PermissionDeniedException;

	/**
	 *  Retrieve a single node from a document. The node is identified by it's
	 *  internal id.
	 *
	 *@param  doc                            the document containing the node
	 *@param  id                             the node's internal id
	 *@return                                Description of the Return Value
	 *@exception  EXistException             Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	byte[] retrieve(String doc, String id, HashMap<String, Object> parameters)
		throws EXistException, PermissionDeniedException;

	/**
	 *  Retrieve a single node from a document. The node is identified by its
	 *  internal id. It is fetched the first chunk, and the next ones should
	 *  be fetched using getNextChunk or getNextExtendedChunk
	 *
	 *@param  doc                            the document containing the node
	 *@param  id                             the node's internal id
	 *@param  parameters                     a <code>HashMap</code> value
	 *@return                                Description of the Return Value
	 *@exception  EXistException             Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	HashMap<String, Object> retrieveFirstChunk(String doc, String id, HashMap<String, Object> parameters)
		throws EXistException, PermissionDeniedException;

	String retrieveAsString(String doc, String id, HashMap<String, Object> parameters)
		throws EXistException, PermissionDeniedException, URISyntaxException;

	public byte[] retrieveAll(int resultId, HashMap<String, Object> parameters)
	throws EXistException, PermissionDeniedException;
	
	public HashMap<String, Object> retrieveAllFirstChunk(int resultId, HashMap<String, Object> parameters)
		throws EXistException, PermissionDeniedException;
	
   HashMap<String, Object> compile(byte[] xquery, HashMap<String, Object> parameters) throws Exception;
   
	HashMap<String, Object> queryP(byte[] xpath, HashMap<String, Object> parameters)
		throws EXistException, PermissionDeniedException;

	HashMap<String, Object> queryP(byte[] xpath, String docName, String s_id, HashMap<String, Object> parameters)
            throws EXistException, PermissionDeniedException, URISyntaxException;
	
	/**
	 *  execute XPath query and return howmany nodes from the result set,
	 *  starting at position <code>start</code>. If <code>prettyPrint</code> is
	 *  set to >0 (true), results are pretty printed.
	 *
	 *@param  howmany                        maximum number of results to
	 *      return.
	 *@param  start                          item in the result set to start
	 *      with.
	 *@return                                Description of the Return Value
	 *@exception  EXistException             Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 *@deprecated                           use Vector query() or int
	 *      executeQuery() instead
	 */
	byte[] query(

		byte[] xquery,
		int howmany,
		int start,
		HashMap<String, Object> parameters)
		throws EXistException, PermissionDeniedException;

	/**
	 *  execute XPath query and return a summary of hits per document and hits
	 *  per doctype. This method returns a struct with the following fields:
	 *
	 *  <table border="1">
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
	 *@param  xquery                         Description of the Parameter
	 *@return                                Description of the Return Value
	 *@exception  EXistException             Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 *@deprecated                           use Vector query() or int
	 *      executeQuery() instead
	 */
	HashMap<String, Object> querySummary(String xquery)
		throws EXistException, PermissionDeniedException;

	/**
	 * Returns a diagnostic dump of the expression structure after
	 * compiling the query. The query is read from the query cache
	 * if it has already been run before.
	 * 
	 * @param query
	 * @throws EXistException
	 */
	public String printDiagnostics(String query, HashMap<String, Object> parameters)
	throws EXistException, PermissionDeniedException;
	
	String createResourceId(String collection)
		throws EXistException, PermissionDeniedException, URISyntaxException;
	
	/**
	 *  Parse an XML document and store it into the database. The document will
	 *  later be identified by <code>docName</code>. Some xmlrpc clients seem to
	 *  have problems with character encodings when sending xml content. To
	 *  avoid this, parse() accepts the xml document content as byte[]. If
	 *  <code>overwrite</code> is >0, an existing document with the same name
	 *  will be replaced by the new document.
	 *
	 *@param  xmlData                        The document data
	 *@param  docName                      The path where the document will be stored 
	 *@exception  EXistException
	 *@exception  PermissionDeniedException
	 */
	boolean parse(byte[] xmlData, String docName)
            throws EXistException, PermissionDeniedException, URISyntaxException;

	/**
	 *  Parse an XML document and store it into the database. The document will
	 *  later be identified by <code>docName</code>. Some xmlrpc clients seem to
	 *  have problems with character encodings when sending xml content. To
	 *  avoid this, parse() accepts the xml document content as byte[]. If
	 *  <code>overwrite</code> is >0, an existing document with the same name
	 *  will be replaced by the new document.
	 *
	 *@param  xmlData                        The document data
	 *@param  docName                      The path where the document will be stored 
	 *@param  overwrite                      Overwrite an existing document with the same path?
	 *@exception  EXistException
	 *@exception  PermissionDeniedException
	 */
	boolean parse(byte[] xmlData, String docName, int overwrite)
	throws EXistException, PermissionDeniedException, URISyntaxException;

	boolean parse(byte[] xmlData, String docName, int overwrite, Date created, Date modified)
	throws EXistException, PermissionDeniedException, URISyntaxException;

	boolean parse(String xml, String docName, int overwrite)
            throws EXistException, PermissionDeniedException, URISyntaxException;

	boolean parse(String xml, String docName)
            throws EXistException, PermissionDeniedException, URISyntaxException;

	/**
	 * An alternative to parse() for larger XML documents. The document
	 * is first uploaded chunk by chunk using upload(), then parseLocal() is
	 * called to actually store the uploaded file.
	 * 
	 * @param chunk the current chunk
	 * @param length total length of the file 
	 * @return the name of the file to which the chunk has been appended.
	 * @throws EXistException
	 * @throws PermissionDeniedException
	 */
	String upload(byte[] chunk, int length)
            throws EXistException, PermissionDeniedException, IOException;

	/**
	 * An alternative to parse() for larger XML documents. The document
	 * is first uploaded chunk by chunk using upload(), then parseLocal() is
	 * called to actually store the uploaded file.
	 * 
	 * @param chunk the current chunk
	 * @param file the name of the file to which the chunk will be appended. This
	 * should be the file name returned by the first call to upload.
	 * @param length total length of the file 
	 * @return the name of the file to which the chunk has been appended.
	 * @throws EXistException
	 * @throws PermissionDeniedException
	 */
	String upload(String file, byte[] chunk, int length)
            throws EXistException, PermissionDeniedException, IOException;

	String uploadCompressed(byte[] data, int length)
            throws EXistException, PermissionDeniedException, IOException;
	
	String uploadCompressed(String file, byte[] data, int length)
            throws EXistException, PermissionDeniedException, IOException;
	
	/**
	 * Parse a file previously uploaded with upload.
	 * 
	 * The temporary file will be removed.
	 * 
	 * @param localFile
	 * @throws EXistException
	 * @throws IOException
	 */
	public boolean parseLocal(String localFile, String docName, boolean replace, String mimeType)
            throws EXistException, PermissionDeniedException, SAXException, URISyntaxException;

	public boolean parseLocalExt(String localFile, String docName, boolean replace, String mimeType, boolean treatAsXML)
            throws EXistException, PermissionDeniedException, SAXException, URISyntaxException;

	public boolean parseLocal(String localFile, String docName, boolean replace, String mimeType, Date created, Date modified)
            throws EXistException, PermissionDeniedException, SAXException, URISyntaxException;
	
	public boolean parseLocalExt(String localFile, String docName, boolean replace, String mimeType, boolean treatAsXML, Date created, Date modified)
            throws EXistException, PermissionDeniedException, SAXException, URISyntaxException;
	
	/**
	 * Store data as a binary resource.
	 * 
	 * @param data the data to be stored
	 * @param docName the path to the new document
	 * @param replace if true, an old document with the same path will be overwritten
	 * @throws EXistException
	 * @throws PermissionDeniedException
	 */
	public boolean storeBinary(byte[] data, String docName, String mimeType, boolean replace)
            throws EXistException, PermissionDeniedException, URISyntaxException;
	
	public boolean storeBinary(byte[] data, String docName, String mimeType, boolean replace, Date created, Date modified)
            throws EXistException, PermissionDeniedException, URISyntaxException;
	
	/**
	 *  Remove a document from the database.
	 *
	 * @param  docName path to the document to be removed
	 *@exception  EXistException
	 *@exception  PermissionDeniedException  
	 */
	boolean remove(String docName) throws EXistException, PermissionDeniedException, URISyntaxException;

	/**
	 *  Remove an entire collection from the database.
	 *
	 *@param  name path to the collection to be removed.
	 *@exception  EXistException
	 *@exception  PermissionDeniedException 
	 */
	boolean removeCollection(String name)
		throws EXistException, PermissionDeniedException, URISyntaxException;

	/** 
	 * Create a new collection on the database.
	 * 
	 * @param name the path to the new collection.
	 * @throws EXistException
	 * @throws PermissionDeniedException
	 */
	boolean createCollection(String name)
		throws EXistException, PermissionDeniedException;
	
	boolean createCollection(String name, Date created)
	throws EXistException, PermissionDeniedException;

	boolean configureCollection(String collection, String configuration)
		throws EXistException, PermissionDeniedException;
	
	/**
     * Execute XPath query and return a reference to the result set. The
	 *  returned reference may be used later to get a summary of results or
	 *  retrieve the actual hits.
	 *
     * @param  xpath                          Description of the Parameter
     * @param  encoding                       Description of the Parameter
     * @param parameters a <code>HashMap</code> value
     * @return                                Description of the Return Value
     * @exception  EXistException             Description of the Exception
     * @exception  PermissionDeniedException  Description of the Exception
     */
    int executeQuery(byte[] xpath, String encoding, HashMap<String, Object> parameters)
		throws EXistException, PermissionDeniedException;

	int executeQuery(byte[] xpath, HashMap<String, Object> parameters) throws EXistException, PermissionDeniedException;

	int executeQuery(String xpath, HashMap<String, Object> parameters) throws EXistException, PermissionDeniedException;
        
	/**
	 *  Execute XPath/Xquery from path file (stored inside eXist)
	 *  returned reference may be used later to get a summary of results or
	 *  retrieve the actual hits.
	 */
	HashMap<String, Object> execute(String path, HashMap<String, Object> parameters)
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
	 *@return                                Description of the Return Value
	 *@exception  EXistException             Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	HashMap<String, Object> querySummary(int resultId)
		throws EXistException, PermissionDeniedException, XPathException;

	HashMap<String, Object> getPermissions(String resource)
		throws EXistException, PermissionDeniedException, URISyntaxException;

	/**
	 *  Get the number of hits in the result set identified by it's
	 *  result-set-id.
	 *
	 *@param  resultId                       Description of the Parameter
	 *@return                                The hits value
	 *@exception  EXistException             Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	int getHits(int resultId) throws EXistException, PermissionDeniedException;

	/**
	 *  Retrieve a single result from the result-set identified by resultId. The
	 *  XML fragment at position num in the result set is returned.
	 *
	 *@param  resultId                       Description of the Parameter
	 *@param  num                            Description of the Parameter
	 *@return                                Description of the Return Value
	 *@exception  EXistException             Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	byte[] retrieve(int resultId, int num, HashMap<String, Object> parameters)
		throws EXistException, PermissionDeniedException;

	/**
	 *  Retrieve a single result from the result-set identified by resultId. The
	 *  XML fragment at position num in the result set is returned. It is
	 *  fetched the first chunk, and the next ones should be fetched using
	 *  getNextChunk or getNextExtendedChunk
	 *
	 *@param  resultId                       Description of the Parameter
	 *@param  num                            Description of the Parameter
	 *@param  parameters                     a <code>HashMap</code> value
	 *@return                                Description of the Return Value
	 *@exception  EXistException             Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	HashMap<String, Object> retrieveFirstChunk(int resultId, int num, HashMap<String, Object> parameters)
		throws EXistException, PermissionDeniedException;

	boolean addAccount(String name, String passwd, String digestPassword,Vector<String> groups, Boolean isEnabled, Integer umask, Map<String, String> metadata)
		throws EXistException, PermissionDeniedException;
        
	boolean updateAccount(String name, String passwd, String digestPassword,Vector<String> groups)
		throws EXistException, PermissionDeniedException;

	boolean updateAccount(String name, String passwd, String digestPassword, Vector<String> groups, Boolean isEnabled, Integer umask, Map<String, String> metadata)
		throws EXistException, PermissionDeniedException;

	boolean addGroup(String name, Map<String, String> metadata) throws EXistException, PermissionDeniedException;

        boolean updateGroup(final String name, final Vector<String> managers, final Map<String, String> metadata) throws EXistException, PermissionDeniedException;
        
        Vector<String> getGroupMembers(final String groupName) throws EXistException, PermissionDeniedException;
        
        void addAccountToGroup(final String accountName, final String groupName) throws EXistException, PermissionDeniedException;
        
        void addGroupManager(final String manager, final String groupName) throws EXistException, PermissionDeniedException;
        
        public void removeGroupManager(final String groupName, final String manager) throws EXistException, PermissionDeniedException;
        
	boolean setPermissions(String resource, String permissions)
            throws EXistException, PermissionDeniedException, URISyntaxException;

	boolean setPermissions(String resource, int permissions)
            throws EXistException, PermissionDeniedException, URISyntaxException;

	boolean setPermissions(
		String resource,
		String owner,
		String ownerGroup,
		String permissions)
		throws EXistException, PermissionDeniedException, URISyntaxException;

	boolean setPermissions(
		String resource,
		String owner,
		String ownerGroup,
		int permissions)
		throws EXistException, PermissionDeniedException, URISyntaxException;

    boolean setPermissions(
            final String resource,
            final String owner,
            final String ownerGroup,
            final int mode,
            final List<ACEAider> aces)
            throws EXistException, PermissionDeniedException, URISyntaxException;

    public boolean chgrp(
        final String resource,
        final String ownerGroup)
        throws EXistException, PermissionDeniedException, URISyntaxException;

    public boolean chown(
        final String resource,
        final String owner)
        throws EXistException, PermissionDeniedException, URISyntaxException;

    public boolean chown(
        final String resource,
        final String owner,
        final String ownerGroup)
        throws EXistException, PermissionDeniedException, URISyntaxException;

	public boolean lockResource(String path, String userName)
	throws EXistException, PermissionDeniedException, URISyntaxException;
	
	public boolean unlockResource(String path)
	throws EXistException, PermissionDeniedException, URISyntaxException;

	public String hasUserLock(String path)
	throws EXistException, PermissionDeniedException, URISyntaxException;
	
	HashMap<String, Object> getAccount(String name) throws EXistException, PermissionDeniedException;

	Vector<HashMap<String, Object>> getAccounts() throws EXistException, PermissionDeniedException;

	boolean removeAccount(String name) throws EXistException, PermissionDeniedException;

        HashMap<String, Object> getGroup(String name) throws EXistException, PermissionDeniedException;

        void removeGroup(String name) throws EXistException, PermissionDeniedException;

	Vector<String> getGroups() throws EXistException, PermissionDeniedException;

	Vector<Vector<Object>> getIndexedElements(String collectionName, boolean inclusive)
		throws EXistException, PermissionDeniedException, URISyntaxException;

	Vector<Vector<Object>> scanIndexTerms(

		String collectionName,
		String start,
		String end,
		boolean inclusive)
		throws PermissionDeniedException, EXistException, URISyntaxException;

	Vector<Vector<Object>> scanIndexTerms(String xpath,
			String start, String end)
			throws PermissionDeniedException, EXistException, XPathException;
	
	boolean releaseQueryResult(int handle);

    boolean releaseQueryResult(int handle, int hash);

	int xupdate(String collectionName, byte[] xupdate)
            throws PermissionDeniedException, EXistException, SAXException, XPathException, LockException;
	
	int xupdateResource(String resource, byte[] xupdate)
		throws PermissionDeniedException, EXistException, SAXException;

	int xupdateResource(String resource, byte[] xupdate, String encoding)
            throws PermissionDeniedException, EXistException, SAXException, XPathException, LockException, URISyntaxException;

		
	Date getCreationDate(String collectionName)
		throws PermissionDeniedException, EXistException, URISyntaxException;
	
	Vector<Date> getTimestamps(String documentName)
		throws PermissionDeniedException, EXistException, URISyntaxException;
		
	boolean copyCollection(String name, String namedest)
	    throws PermissionDeniedException, EXistException;

	List<String> getDocumentChunk(String name, HashMap<String, Object> parameters)
	throws EXistException, PermissionDeniedException, IOException;
	
	byte[] getDocumentChunk(String name, int start, int stop)
	throws EXistException, PermissionDeniedException, IOException;
	
	boolean moveCollection(String collectionPath, String destinationPath, String newName)
            throws EXistException, PermissionDeniedException, URISyntaxException;
	
	boolean moveResource(String docPath, String destinationPath, String newName)
            throws EXistException, PermissionDeniedException, URISyntaxException;
	
	boolean copyCollection(String collectionPath, String destinationPath, String newName)
            throws EXistException, PermissionDeniedException, URISyntaxException;
	
	boolean copyResource(String docPath, String destinationPath, String newName)
            throws EXistException, PermissionDeniedException, URISyntaxException;
	
	boolean reindexCollection(String name)
	throws EXistException, PermissionDeniedException, URISyntaxException;
	
	boolean backup(String userbackup, String password, String destcollection, String collection)
	throws EXistException, PermissionDeniedException;
	
	boolean dataBackup(String dest) throws PermissionDeniedException;
    
    /// DWES
    boolean isValid(String name)	throws EXistException, PermissionDeniedException, URISyntaxException;
    
    Vector<String> getDocType(String documentName)
	throws PermissionDeniedException, EXistException, URISyntaxException;
    
    boolean setDocType(String documentName, String doctypename, String publicid, String systemid)
	throws EXistException, PermissionDeniedException, URISyntaxException;
    
    public void runCommand(XmldbURI collectionURI, Vector<String> params) throws EXistException, PermissionDeniedException;
    
    
    public long getSubCollectionCreationTime(String parentPath, String name) throws EXistException, PermissionDeniedException, URISyntaxException;
    public HashMap<String, Object> getSubCollectionPermissions(String parentPath, String name) throws EXistException, PermissionDeniedException, URISyntaxException;
    public HashMap<String, Object> getSubResourcePermissions(String parentPath, String name) throws EXistException, PermissionDeniedException, URISyntaxException;

    public boolean setTriggersEnabled(String path, String value) throws PermissionDeniedException;
}

