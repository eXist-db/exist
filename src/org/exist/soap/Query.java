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
     * 
     * @param sessionId a valid session id as returned by connect().
     * @param xpath XPath query string.
     * @return QueryResponse describing the query results.
     * @throws RemoteException
     */
    public QueryResponse query(String sessionId, String xpath) throws RemoteException;
    
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
