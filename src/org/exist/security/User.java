/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2003-2009 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id:$
 */
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