/**
 * Admin.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.2.1 Jun 14, 2005 (09:15:57 EDT) WSDL2Java emitter.
 */

package org.exist.soap;

import java.rmi.RemoteException;

public interface Admin extends java.rmi.Remote {
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
    public void store(java.lang.String sessionId, byte[] data, java.lang.String encoding, java.lang.String path, boolean replace) throws java.rmi.RemoteException;
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
	 * @param userId
	 * @param password
	 * @return session-id a unique id for the created session 
	 * @throws RemoteException if the user cannot log in
	 */
    public java.lang.String connect(java.lang.String userId, java.lang.String password) throws java.rmi.RemoteException;
	/**
	 * Release a user session. This will free all resources (including result sets).
	 * 
	 * @param sessionId a valid session id as returned by connect().
	 * @throws java.rmi.RemoteException
	 */
    public void disconnect(java.lang.String sessionId) throws java.rmi.RemoteException;
	/**
	 * Remove the specified collection.
	 * 
	 * @param sessionId sessionId a unique id for the created session.
	 * @param path the full path to the collection.
	 * @return true on success.
	 * 
	 * @throws RemoteException
	 */
   public boolean removeCollection(java.lang.String sessionId, java.lang.String path) throws java.rmi.RemoteException;
	/**
	 * Remove the specified document.
	 * 
	 * @param sessionId a unique id for the created session.
	 * @param path the full path to the document.
	 * @return true on success.
	 * 
	 * @throws RemoteException
	 */
    public boolean removeDocument(java.lang.String sessionId, java.lang.String path) throws java.rmi.RemoteException;
	/**
	 * Create a new collection using the specified path.
	 * 
	 * @param sessionId a unique id for the created session.
	 * @param path the full path to the collection.
	 * @throws RemoteException
	 */
    public boolean createCollection(java.lang.String sessionId, java.lang.String path) throws java.rmi.RemoteException;
	/**
	 * Apply a set of XUpdate modifications to a collection.
	 * 
	 * @param sessionId a unique id for the created session.
	 * @param collectionName the full path to the collection.
	 * @param xupdate the XUpdate document to be applied.
	 * @throws RemoteException
	 */
    public int xupdate(java.lang.String sessionId, java.lang.String collectionName, java.lang.String xupdate) throws java.rmi.RemoteException;
	/**
	 * Apply a set of XUpdate modifications to the specified document.
	 * 
	 * @param sessionId a unique id for the created session.
	 * @param documentName the full path to the document.
	 * @param xupdate the XUpdate document to be applied.
	 * @throws RemoteException
	 */
    public int xupdateResource(java.lang.String sessionId, java.lang.String documentName, java.lang.String xupdate) throws java.rmi.RemoteException;
    /**
     * Retrieve a binary resource from the database
     * @param sessionId the session identifier
     * @param name the name of the binary resource
     * @return the binary resource data
     * @throws java.rmi.RemoteException
     */
    public byte[] getBinaryResource(java.lang.String sessionId, java.lang.String name) throws java.rmi.RemoteException;
    /**
     * Obtain a description of the specified collection.
     * 
     * The description contains 
     *   - the collection permissions
     *   - list of sub-collections
     *   - list of documents and their permissions
     * 
     * @param sessionId the session identifier
     * @param collectionName the collection
     * @return the collection descriptor
     * @throws java.rmi.RemoteException
     */
    public org.exist.soap.CollectionDesc getCollectionDesc(java.lang.String sessionId, java.lang.String collectionName) throws java.rmi.RemoteException;
    /**
     * Set the owner, group and access permissions for a document or collection
     * @param sessionId the session id
     * @param resource the document/collection that will get new permissions
     * @param owner the new owner
     * @param ownerGroup the new group
     * @param permissions the new access permissions
     * @throws java.rmi.RemoteException
     */
    public void setPermissions(java.lang.String sessionId, java.lang.String resource, java.lang.String owner, java.lang.String ownerGroup, int permissions) throws java.rmi.RemoteException;
    /**
     * Copy a resource to the destination collection and rename it.
     * @param sessionId the session identifier
     * @param docPath the resource to cop
     * @param destinationPath the destination collection
     * @param newName the new name for the resource
     * @throws java.rmi.RemoteException
     */
    public void copyResource(java.lang.String sessionId, java.lang.String docPath, java.lang.String destinationPath, java.lang.String newName) throws java.rmi.RemoteException;
    /**
     * Copy a collection to the destination collection and rename it. 
     * @param sessionId the session identifier
     * @param collectionPath the collection to rename
     * @param destinationPath the destination collection
     * @param newName the new name of the collection.
     * @throws java.rmi.RemoteException
     */
    public void copyCollection(java.lang.String sessionId, java.lang.String collectionPath, java.lang.String destinationPath, java.lang.String newName) throws java.rmi.RemoteException;
    /** Create a new user.
     * Requires Admin privilege.
     * @param sessionId the session identifier
     * @param name the name of the new user
     * @param password the password for the new user
     * @param groups the new user should belong to these groups 
     * @throws java.rmi.RemoteException
     */
    public void setUser(java.lang.String sessionId, java.lang.String name, java.lang.String password, org.exist.soap.Strings groups, java.lang.String home) throws java.rmi.RemoteException;
    /** 
     * Obtain information about an eXist user.
     * 
     * @param sessionId the session identifier
     * @param user the user
     * @return the user information - name, groups and home collection
     * @throws java.rmi.RemoteException if user doesn't exist
     */
    public org.exist.soap.UserDesc getUser(java.lang.String sessionId, java.lang.String user) throws java.rmi.RemoteException;
    /** 
     * Remove an eXist user account.
     * 
     * Requires Admin privilege
     * @param sessionId the session identifier
     * @param name the name of the user
     * @throws java.rmi.RemoteException
     */
    public void removeUser(java.lang.String sessionId, java.lang.String name) throws java.rmi.RemoteException;
    /** 
     * Get an list of users
     * 
     * @param sessionId the session identifier
     * @return an array of user infomation (name, groups, home collection)
     * @throws java.rmi.RemoteException
     */
    public org.exist.soap.UserDescs getUsers(java.lang.String sessionId) throws java.rmi.RemoteException;
    /**
     *  Obtain a list of the defined database groups
     *  
     * @param sessionId the session identifier
     * @return the list of groups
     * @throws java.rmi.RemoteException
     */
    public org.exist.soap.Strings getGroups(java.lang.String sessionId) throws java.rmi.RemoteException;
    /** 
     * Move a collection and its contents.
     * 
     * @param sessionId the session isentifier
     * @param collectionPath the collection to move
     * @param destinationPath the new parent collection 
     * @param newName the new collection name
     * @throws java.rmi.RemoteException
     */
    public void moveCollection(java.lang.String sessionId, java.lang.String collectionPath, java.lang.String destinationPath, java.lang.String newName) throws java.rmi.RemoteException;
    /** 
     * Move a resource.
     *  
     * @param sessionId the session identifier
     * @param docPath the resource to move
     * @param destinationPath the collection to receive the moved resource
     * @param newName the new name for the resource
     * @throws java.rmi.RemoteException
     */
    public void moveResource(java.lang.String sessionId, java.lang.String docPath, java.lang.String destinationPath, java.lang.String newName) throws java.rmi.RemoteException;
    /** Place a write lock on the specified resource 
     * @param sessionId the session identifier
     * @param path the path of the resource to lock
     * @param userName the user name of the lock owner
     * @throws java.rmi.RemoteException
     */
    public void lockResource(java.lang.String sessionId, java.lang.String path, java.lang.String userName) throws java.rmi.RemoteException;
    /** Release the lock on the specified resource
     * @param sessionId the session identifier
     * @param path path of the resource to unlock
     * @throws java.rmi.RemoteException
     */
    public void unlockResource(java.lang.String sessionId, java.lang.String path) throws java.rmi.RemoteException;
    /** Return the name of the user owning the lock on the specified resource
     * @param sessionId the session identifier
     * @param path the resource
     * @return the name of the lock owner or "" if there is no lock
     * @throws java.rmi.RemoteException
     */
    public java.lang.String hasUserLock(java.lang.String sessionId, java.lang.String path) throws java.rmi.RemoteException;
    /** Return the permissions of the specified collection/document
     * @param sessionId the session identifier
     * @param resource the collection or document
     * @return the permissions (owner, group, access permissions)
     * @throws java.rmi.RemoteException
     */
    public org.exist.soap.Permissions getPermissions(java.lang.String sessionId, java.lang.String resource) throws java.rmi.RemoteException;
    /** Return a list of the permissions of the child collections of the specified parent collection
     * @param sessionId the session identifier
     * @param name the name of the parent collection
     * @return array containing child collections with their permissions
     * @throws java.rmi.RemoteException
     */
    public org.exist.soap.EntityPermissionsList listCollectionPermissions(java.lang.String sessionId, java.lang.String name) throws java.rmi.RemoteException;
    /** Return a list of the permissions of the child documents of the specified parent collection
     * @param sessionId the session identifier
     * @param name name of the parent collection
     * @return array containing documents with their permissions
     * @throws java.rmi.RemoteException
     */
    public org.exist.soap.EntityPermissionsList listDocumentPermissions(java.lang.String sessionId, java.lang.String name) throws java.rmi.RemoteException;
    /**
     *  Return a list of Indexed Elements for a collection
     * @param sessionId the session identifier
     * @param collectionName the collection name
     * @param inclusive include sub-collections ?
     * @return the list of Indexed Elements
     * @throws java.rmi.RemoteException
     */
    public org.exist.soap.IndexedElements getIndexedElements(java.lang.String sessionId, java.lang.String collectionName, boolean inclusive) throws java.rmi.RemoteException;
    /**
     * Store a binary resource in the database
     * 
     * @param sessionId the session identifier
     * @param data the binary data
     * @param path the path for the new resource
     * @param mimeType the mime type for the resource
     * @param replace replace resource if it already exists
     * @throws java.rmi.RemoteException
     */
    public void storeBinary(java.lang.String sessionId, byte[] data, java.lang.String path, java.lang.String mimeType, boolean replace) throws java.rmi.RemoteException;
}
