package org.exist.soap;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * This interface defines eXist's SOAP service for (read-only) 
 * queries on the database.
 */
public interface Query extends Remote {
	
	/**
	 * Create a new user session. Authenticates the user against the database.
	 * The user has to be a valid database user. If the provided user information
	 * is valid, a new session will be registered on the server and a session id
	 * will be returned.
	 * 
	 * The session will be valid for at least 60 minutes. Please call disconnect() to
	 * release the session.
	 * 
	 * Sessions are shared between the Query and Admin services. A session created
	 * through the Query service can be used with the Admin service and vice versa.
	 * 
	 * @param user
	 * @param password
	 * @return session-id a unique id for the created session 
	 * @throws RemoteException if the user cannot log in
	 */
	public String connect(String user, String password) throws RemoteException;
	
	/**
	 * Release a user session. This will free all resources (including result sets).
	 * 
	 * @param sessionId a valid session id as returned by connect().
	 * @throws java.rmi.RemoteException
	 */
	public void disconnect(String sessionId) throws java.rmi.RemoteException;
	
	/**
	 * Retrieve a document from the database.
	 * 
	 * @param sessionId a valid session id as returned by connect().
	 * @param path the full path to the document.
	 * @param indent should the document be pretty-printed (indented)?
	 * @param xinclude should xinclude tags be expanded?
	 * @return the resource as string
	 * @throws RemoteException
	 */
    public String getResource(String sessionId, String path, boolean indent, boolean xinclude) 
    throws RemoteException;
    
    /**
     * Retrieve a document from the database.
     * 
     * This method returns the document data in binary form to avoid possible
     * conflicts.
     * 
     * @param sessionId a valid session id as returned by connect().
     * @param path the full path to the document.
     * @param indent should the document be pretty-printed (indented)?
     * @param xinclude should xinclude tags be expanded?
     * @param processXSLPI should XSL processing instructions be processed?
     * @return the resource in base64 binary encoding
     * @throws RemoteException
     */
    public byte[] getResourceData(String sessionId, String path, boolean indent, boolean xinclude,
    		boolean processXSLPI) 
	throws RemoteException;
    
    /**
     * Execute a simple XPath query passed as string.
     * 
     * @param sessionId a valid session id as returned by connect().
     * @param xpath XPath query string.
     * @return QueryResponse describing the query results.
     * @throws RemoteException
     * @deprecated use {@link #xquery(String, byte[])} instead.
     */
    public QueryResponse query(String sessionId, String xpath) throws RemoteException;
    
    /**
     * Execute an XQuery.
     * 
     * @param sessionId a valid session id as returned by connect().
     * @param xquery the XQuery script in binary encoding.
     * @return
     * @throws RemoteException
     */
    public QueryResponse xquery(String sessionId, byte[] xquery) throws RemoteException;
    
    /**
     * Retrieve a set of query results from the last query executed within
     * the current session.
     * 
     * The first result to be retrieved from the result set is defined by the
     * start-parameter. Results are counted from 1.
     *  
     * @param sessionId a valid session id as returned by connect().
     * @param start the first result to retrieve.
     * @param howmany number of results to be returned.
     * @param indent should the XML be pretty-printed?
     * @param xinclude should xinclude tags be expanded?
     * @param highlight highlight matching search terms within elements
     * or attributes. Possible values are: "elements" for elements only,
     * "attributes" for attributes only, "both" for elements and attributes,
     * "none" to disable highlighting. For elements, matching terms are
     * surrounded by &lt;exist:match&gt; tags. For attributes, terms are
     * marked with the char sequence "||".
     * 
     * @return
     * @throws RemoteException
     */
    public String[] retrieve(String sessionId, int start, int howmany, boolean indent, 
    	boolean xinclude, String highlight) throws RemoteException;

    /**
     * Retrieve a set of query results from the last query executed within
     * the current session.
     * 
     * This method returns the data as an array of base64 encoded data.
     * The first result to be retrieved from the result set is defined by the
     * start-parameter. Results are counted from 1.
     *  
     * @param sessionId a valid session id as returned by connect().
     * @param start the first result to retrieve.
     * @param howmany number of results to be returned.
     * @param indent should the XML be pretty-printed?
     * @param xinclude should xinclude tags be expanded?
     * @param highlight highlight matching search terms within elements
     * or attributes. Possible values are: "elements" for elements only,
     * "attributes" for attributes only, "both" for elements and attributes,
     * "none" to disable highlighting. For elements, matching terms are
     * surrounded by &lt;exist:match&gt; tags. For attributes, terms are
     * marked with the char sequence "||".
     * 
     * @return
     * @throws RemoteException
     */
    public byte[][] retrieveData(String sessionId, int start, int howmany, boolean indent, 
    		boolean xinclude, String highlight) throws RemoteException;
    
	/**
	 * For the specified document, retrieve a set of query results from 
	 * the last query executed within the current session. Only hits in
	 * the given document (identified by its path) are returned. 
	 * 
	 * The first result to be retrieved from the result set is defined by the
	 * start-parameter. Results are counted from 1.
	 *  
	 * @param sessionId a valid session id as returned by connect().
	 * @param start the first result to retrieve.
	 * @param howmany number of results to be returned.
	 * @param path the full path to the document.
	 * @param indent should the XML be pretty-printed?
	 * @param xinclude should xinclude tags be expanded?
	 * @param highlight highlight matching search terms within elements
	 * or attributes. Possible values are: "elements" for elements only,
	 * "attributes" for attributes only, "both" for elements and attributes,
	 * "none" to disable highlighting. For elements, matching terms are
	 * surrounded by &lt;exist:match&gt; tags. For attributes, terms are
	 * marked with the char sequence "||".
	 * 
	 * @return
	 * @throws RemoteException
	 */
    public String[] retrieveByDocument(String sessionId, int start, int howmany, 
    	String path, boolean indent, boolean xinclude, String highlight) throws RemoteException;
    
    /**
     * Get information on the specified collection.
     * 
     * @param sessionId a valid session id as returned by connect().
     * @param path the full path to the collection.
     * @return
     * @throws java.rmi.RemoteException
     */
    public Collection listCollection(String sessionId, String path) throws java.rmi.RemoteException;
}
