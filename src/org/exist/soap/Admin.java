package org.exist.soap;

import java.rmi.RemoteException;

/**
 * This interface defines eXist's SOAP service for write 
 * operations on the database.
 */
public interface Admin extends java.rmi.Remote {

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
	 * Store a new document into the database. The document will be stored using
	 * the name and location as specified by the path argument. To avoid any conflicts
	 * with the SOAP transport layer, document contents are passed as base64 encoded
	 * binary data. Internally, all documents are stored in UTF-8 encoding.
	 * 
	 * The method will automatically replace an already existing document with the same
	 * path if the replace argument is set to true (and the user has sufficient privileges).
	 *  
	 * @param sessionId a unique id for the created session.
	 * @param data the document contents as base64 encoded binary data.
	 * @param encoding the character encoding used for the document data.
	 * @param path the target path for the new document.
	 * @param replace should an existing document be replaced? 
	 * @throws RemoteException
	 */
	public void store(String sessionId, byte[] data, String encoding, String path, boolean replace)
		throws RemoteException;

	/**
	 * Remove the specified collection.
	 * 
	 * @param sessionId sessionId a unique id for the created session.
	 * @param path the full path to the collection.
	 * @return true on success.
	 * 
	 * @throws RemoteException
	 */
	public boolean removeCollection(String sessionId, String path) throws RemoteException;

	/**
	 * Remove the specified document.
	 * 
	 * @param sessionId a unique id for the created session.
	 * @param path the full path to the document.
	 * @return true on success.
	 * 
	 * @throws RemoteException
	 */
	public boolean removeDocument(String sessionId, String path) throws RemoteException;

	/**
	 * Create a new collection using the specified path.
	 * 
	 * @param sessionId a unique id for the created session.
	 * @param path the full path to the collection.
	 * @return
	 * @throws RemoteException
	 */
	public boolean createCollection(String sessionId, String path) throws RemoteException;

	/**
	 * Apply a set of XUpdate modifications to a collection.
	 * 
	 * @param sessionId a unique id for the created session.
	 * @param collectionName the full path to the collection.
	 * @param xupdate the XUpdate document to be applied.
	 * @return
	 * @throws RemoteException
	 */
	public int xupdate(String sessionId, String collectionName, String xupdate)
		throws RemoteException;

	/**
	 * Apply a set of XUpdate modifications to the specified document.
	 * 
	 * @param sessionId a unique id for the created session.
	 * @param documentName the full path to the document.
	 * @param xupdate the XUpdate document to be applied.
	 * @return
	 * @throws RemoteException
	 */
	public int xupdateResource(String sessionId, String documentName, String xupdate)
		throws RemoteException;
}
