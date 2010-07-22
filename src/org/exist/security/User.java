/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2003-2010 The eXist Project
 *  http://exist-db.org
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 *  $Id$
 */
package org.exist.security;

import java.security.Principal;
import java.util.Set;

import org.exist.xmldb.XmldbURI;

public interface User extends Principal {

	public final static int PLAIN_ENCODING = 0;
	public final static int SIMPLE_MD5_ENCODING = 1;
	public final static int MD5_ENCODING = 2;

	/**
	 * Add the user to a group
	 *
	 * @param  group  The feature to be added to the Group attribute
	 */
	public Group addGroup(String name);

	/**
	 * Add the user to a group
	 *
	 * @param  group  The feature to be added to the Group attribute
	 */
	public Group addGroup(Group group);

	/**
	 *  Remove the user to a group
	 *  Added by {Marco.Tampucci and Massimo.Martinelli}@isti.cnr.it  
	 *
	 *@param  group  The feature to be removed to the Group attribute
	 */
	public void remGroup(String group);

	/**
	 *  Get all groups this user belongs to
	 *
	 *@return    The groups value
	 */
	public String[] getGroups();

	public boolean hasDbaRole();

	public int getUID();

	/**
	 *  Get the primary group this user belongs to
	 *
	 *@return    The primaryGroup value
	 */
	public String getPrimaryGroup();
	public Group getDefaultGroup();

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
	 * @param  passwd  The new password value
	 */
	public void setPassword(String passwd);

	public void setHome(XmldbURI homeCollection);

	public XmldbURI getHome();

	public boolean authenticate(Object credentials);

	public boolean isAuthenticated();

	public Realm getRealm();

	@Deprecated
	public void setUID(int uid);

	/**
	 * Get the user's password
	 * 
	 * @return Description of the Return Value
	 * @deprecated
	 */
	public String getPassword();

	@Deprecated
	public String getDigestPassword();

	@Deprecated
	public void setGroups(String[] groups);
	
    /**
     * Add a named attribute.
     *
     * @param name
     * @param value
     */
	public void setAttribute(String name, Object value);

    /**
     * Get the named attribute value.
     *
     * @param name The String that is the name of the attribute.
     * @return The value associated with the name or null if no value is associated with the name.
     */
	public Object getAttribute(String name);

    /**
     * Returns the set of attributes names.
     *
     * @return the Set of attribute names.
     */
    public Set<String> getAttributeNames();

}