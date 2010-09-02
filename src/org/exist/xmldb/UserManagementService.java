package org.exist.xmldb;

import org.exist.security.Group;
import org.exist.security.Permission;
import org.exist.security.Account;
import org.exist.security.User;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.Service;
import org.xmldb.api.base.XMLDBException;

/**
 * An eXist-specific service which provides methods to manage users and
 * permissions.
 *
 * @author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 * Modified by {Marco.Tampucci, Massimo.Martinelli} @isti.cnr.it
 */
public interface UserManagementService extends Service {

    /**
     *  Get the name of this service
     *
     *@return    The name
     */
    public String getName();


    /**
     *  Get the version of this service
     *
     *@return    The version value
     */
    public String getVersion();

	/**
	 * Set permissions for the specified collection.
	 * 
	 * @param child
	 * @param perm
	 * @throws XMLDBException
	 */
	public void setPermissions(Collection child, Permission perm) throws XMLDBException;
	
	/**
	 * Set permissions for the specified resource.
	 * 
	 * @param resource
	 * @param perm
	 * @throws XMLDBException
	 */
	public void setPermissions(Resource resource, Permission perm) throws XMLDBException;
	
    /**
     *  Change owner and group of the current collection.
     *
     *@param  u                   Description of the Parameter
     *@param  group               Description of the Parameter
     *@exception  XMLDBException  Description of the Exception
     */
    public void chown( Account u, String group ) throws XMLDBException;


    /**
     *  Change owner and group of the specified resource.
     *
     *@param  res                 Description of the Parameter
     *@param  u                   Description of the Parameter
     *@param  group               Description of the Parameter
     *@exception  XMLDBException  Description of the Exception
     */
    public void chown( Resource res, Account u, String group )
         throws XMLDBException;


    /**
     *  Change permissions for the specified resource.
     *
     * Permissions are specified in a string according to the
     * following format:
     * 
     * <pre>[user|group|other]=[+|-][read|write|update]</pre>
     * 
     * For example, to grant all permissions to the group and
     * deny everything to others:
     * 
     * group=+write,+read,+update,other=-read
     * 
     * The changes are applied to the permissions currently
     * active for this resource.
     * 
     *@param  resource            Description of the Parameter
     *@param  modeStr             Description of the Parameter
     *@exception  XMLDBException  Description of the Exception
     */
    public void chmod( Resource resource, String modeStr ) throws XMLDBException;

    /**
     *  Change permissions for the current collection
     *
     *@param  modeStr             String describing the permissions to
     * grant or deny.
     *@exception  XMLDBException
     * 
     */
    public void chmod( String modeStr ) throws XMLDBException;
    
    public void chmod( int mode ) throws XMLDBException;
    
    /**
     * Change permissions for the specified resource.
     * 
     */
    public void chmod( Resource resource, int mode ) throws XMLDBException;
    
    /**
     * Lock the specified resource for the specified user.
     * 
     * A locked resource cannot be changed by other users (except
     * users in group DBA) until the lock is released. Users with admin
     * privileges can always change a resource.
     * 
     * @param res
     * @param u
     * @throws XMLDBException
     */
    public void lockResource(Resource res, Account u) throws XMLDBException;
    
    /**
     * Check if the resource has a user lock.
     * 
     * Returns the name of the owner of the lock or null
     * if no lock has been set on the resource.
     * 
     * @param res
     * @return Name of the owner of the lock
     * @throws XMLDBException
     */
    public String hasUserLock(Resource res) throws XMLDBException;
    
    /**
     * Unlock the specified resource.
     * 
     * The current user has to be same who locked the resource.
     * Exception: admin users can always unlock a resource.
     * 
     * @param res
     * @throws XMLDBException
     */
    public void unlockResource(Resource res) throws XMLDBException;
    
    /**
     *  Add a new account to the database
     *
     *@param  account             The feature to be added to the Account
     *@exception  XMLDBException  Description of the Exception
     */
    public void addAccount( Account account ) throws XMLDBException;


    /**
     *  Update existing account information
     *
     *@param  account             Description of the Parameter
     *@exception  XMLDBException  Description of the Exception
     */
    public void updateAccount( Account account ) throws XMLDBException;


    /**
     *  Get a account record from the database
     *
     *@param  name                Description of the Parameter
     *@return                     The user value
     *@exception  XMLDBException  Description of the Exception
     */
    public Account getAccount( String name ) throws XMLDBException;


    /**
     *  Retrieve a list of all existing accounts.
     *
     *@return                     The accounts value
     *@exception  XMLDBException  Description of the Exception
     */
    public Account[] getAccounts() throws XMLDBException;

    public Group getGroup( String name ) throws XMLDBException;

    /**
	 * Retrieve a list of all existing groups.
	 * 
	 * Please note: new groups are created automatically if a new group
	 * is assigned to a user. You can't add or remove them.
	 * 
	 * @return List of all existing groups.
	 * @throws XMLDBException
	 */
	public String[] getGroups() throws XMLDBException;
	
    /**
     * Get a property defined by this service.
     *
     * @param  property            Description of the Parameter
     * @return                     The property value
     * @exception  XMLDBException  Description of the Exception
     */
    public String getProperty( String property ) throws XMLDBException;

    /**
     *  Set a property for this service.
     *
     * @param  property            The new property value
     * @param  value               The new property value
     * @exception  XMLDBException  Description of the Exception
     */
    public void setProperty( String property, String value ) throws XMLDBException;

    /**
     *  Set the current collection for this service
     *
     *@param  collection          The new collection value
     *@exception  XMLDBException  Description of the Exception
     */
    public void setCollection( Collection collection ) throws XMLDBException;


    /**
     *  Get permissions for the specified collections
     *
     *@param  coll                Description of the Parameter
     *@return                     The permissions value
     *@exception  XMLDBException  Description of the Exception
     */
    public Permission getPermissions( Collection coll ) throws XMLDBException;


    /**
     *  Get permissions for the specified resource
     *
     *@param  res                 Description of the Parameter
     *@return                     The permissions value
     *@exception  XMLDBException  Description of the Exception
     */
    public Permission getPermissions( Resource res ) throws XMLDBException;


    /**
     * Get permissions for all resources contained in the current
     * collection. Returns a list of permissions in the same order
     * as Collection.listResources().
     * 
     * @return Permission[]
     * @throws XMLDBException
     */
    public Permission[] listResourcePermissions() throws XMLDBException;
    
    /**
     * Get permissions for all child collections contained in the current
     * collection. Returns a list of permissions in the same order
     * as Collection.listChildCollections().
     * 
     * @return Permission[]
     * @throws XMLDBException
     */
    public Permission[] listCollectionPermissions() throws XMLDBException;

    /**
     *  Delete a user from the database
     *
     *@param  user                User
     *@exception  XMLDBException
     */
    public void removeAccount( Account account ) throws XMLDBException;
    
    public void removeGroup( Group group ) throws XMLDBException;

    /**
	 *  Update the specified user without update user's password
	 *  Method added by {Marco.Tampucci, Massimo.Martinelli} @isti.cnr.it
	 *
	 *@param  user                Description of the Parameter
	 *@exception  XMLDBException  Description of the Exception
	 */
    public void addUserGroup(Account user) throws XMLDBException;
    
    /**
	 *  Update the specified user removing a group from user's group
	 *  Method added by {Marco.Tampucci, Massimo.Martinelli} @isti.cnr.it
	 *
	 *@param  user                Description of the Parameter
	 *@param  rmgroup             Description of group to remove 
	 *@exception  XMLDBException  Description of the Exception
	 */
    public void removeGroup(Account user, String rmgroup) throws XMLDBException;


	public void addGroup(Group group) throws XMLDBException;

	@Deprecated
	public void addUser( User user ) throws XMLDBException;
	
	@Deprecated
	public void updateUser( User user ) throws XMLDBException;

	@Deprecated
	public User getUser( String name ) throws XMLDBException;
	
	@Deprecated
	public User[] getUsers() throws XMLDBException;
	
	@Deprecated
	public void removeUser( User user ) throws XMLDBException;
}




