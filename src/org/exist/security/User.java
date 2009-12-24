package org.exist.security;

import org.exist.xmldb.XmldbURI;

public interface User {

	public final static int PLAIN_ENCODING = 0;
	public final static int SIMPLE_MD5_ENCODING = 1;
	public final static int MD5_ENCODING = 2;

	/**
	 *  Add the user to a group
	 *
	 *@param  group  The feature to be added to the Group attribute
	 */
	public void addGroup(String group);

	/**
	 *  Remove the user to a group
	 *  Added by {Marco.Tampucci and Massimo.Martinelli}@isti.cnr.it  
	 *
	 *@param  group  The feature to be removed to the Group attribute
	 */
	public void remGroup(String group);

	public void setGroups(String[] groups);

	/**
	 *  Get all groups this user belongs to
	 *
	 *@return    The groups value
	 */
	public String[] getGroups();

	public boolean hasDbaRole();

	/**
	 *  Get the user name
	 *
	 *@return    The user value
	 */
	public String getName();

	public int getUID();

	/**
	 *  Get the primary group this user belongs to
	 *
	 *@return    The primaryGroup value
	 */
	public String getPrimaryGroup();

	/**
	 *  Is the user a member of group?
	 *
	 *@param  group  Description of the Parameter
	 *@return        Description of the Return Value
	 */
	public boolean hasGroup(String group);

	/**
	 *  Sets the password attribute of the User object
	 *
	 *@param  passwd  The new password value
	 */
	public void setPassword(String passwd);

	public void setHome(XmldbURI homeCollection);

	public XmldbURI getHome();

}