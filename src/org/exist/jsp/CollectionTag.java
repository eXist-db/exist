/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
package org.exist.jsp;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.tagext.TagSupport;

import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 *
 */
public class CollectionTag extends TagSupport {
	
	public final static String DRIVER = "org.exist.xmldb.DatabaseImpl";
	
	private String varName;
	private String uri;
	private String user = "guest";
	private String password = "guest";
	
	private Collection collection = null;
	
	/* (non-Javadoc)
	 * @see javax.servlet.jsp.tagext.TagSupport#doStartTag()
	 */
	public int doStartTag() throws JspException {
		try {
			Class clazz = Class.forName(DRIVER);
			Database database = (Database)clazz.newInstance();
			DatabaseManager.registerDatabase(database);
			
			collection = DatabaseManager.getCollection(uri, user, password);
		} catch (ClassNotFoundException e) {
			throw new JspException("Database driver class not found", e);
		} catch (InstantiationException e) {
			throw new JspException("Failed to initialize database driver", e);
		} catch (IllegalAccessException e) {
			throw new JspException("Failed to initialize database driver", e);
		} catch (XMLDBException e) {
			throw new JspException(e.getMessage(), e);
		}
		pageContext.setAttribute(varName, collection);
		return EVAL_BODY_INCLUDE;
	}
	
	/* (non-Javadoc)
	 * @see javax.servlet.jsp.tagext.TagSupport#doEndTag()
	 */
	public int doEndTag() throws JspException {
		return EVAL_PAGE;
	}

	public Collection getCollection() {
		return collection;
	}
	
	public void setVar(String var) {
		this.varName = var;
	}
	
	public String getVar() {
		return varName;
	}
	
	/**
	 * @return Returns the password.
	 */
	public String getPassword() {
		return password;
	}

	/**
	 * @param password The password to set.
	 */
	public void setPassword(String password) {
		this.password = password;
	}

	/**
	 * @return Returns the uri.
	 */
	public String getUri() {
		return uri;
	}

	/**
	 * @param uri The uri to set.
	 */
	public void setUri(String uri) {
		this.uri = uri;
	}

	/**
	 * @return Returns the user.
	 */
	public String getUser() {
		return user;
	}

	/**
	 * @param user The user to set.
	 */
	public void setUser(String user) {
		this.user = user;
	}

}
