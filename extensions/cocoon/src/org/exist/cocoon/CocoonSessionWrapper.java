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
package org.exist.cocoon;

import java.util.Enumeration;

import org.apache.cocoon.environment.Session;
import org.exist.http.servlets.SessionWrapper;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class CocoonSessionWrapper implements SessionWrapper {

	private Session session;
	
	/**
	 * 
	 */
	public CocoonSessionWrapper(Session session) {
		this.session = session;
	}

	/**
	 * @param arg0
	 */
	public Object getAttribute(String arg0) {
		return session.getAttribute(arg0);
	}

	/**
	 */
	public Enumeration getAttributeNames() {
		return session.getAttributeNames();
	}

	/**
	 */
	public long getCreationTime() {
		return session.getCreationTime();
	}

	/**
	 */
	public String getId() {
		return session.getId();
	}

	/**
	 */
	public long getLastAccessedTime() {
		return session.getLastAccessedTime();
	}

	/**
	 */
	public int getMaxInactiveInterval() {
		return session.getMaxInactiveInterval();
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	public int hashCode() {
		return session.hashCode();
	}

	/**
	 * 
	 */
	public void invalidate() {
		session.invalidate();
	}

	/**
	 */
	public boolean isNew() {
		return session.isNew();
	}

	/**
	 * @param arg0
	 */
	public void removeAttribute(String arg0) {
		session.removeAttribute(arg0);
	}

	/**
	 * @param arg0
	 * @param arg1
	 */
	public void setAttribute(String arg0, Object arg1) {
		session.setAttribute(arg0, arg1);
	}

	/**
	 * @param arg0
	 */
	public void setMaxInactiveInterval(int arg0) {
		session.setMaxInactiveInterval(arg0);
	}

}
