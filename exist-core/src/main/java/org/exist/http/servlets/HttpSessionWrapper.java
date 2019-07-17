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
 */
package org.exist.http.servlets;

import java.util.Enumeration;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;

/**
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public class HttpSessionWrapper implements SessionWrapper {

	private HttpSession session;

	/**
	 * Get the Servlet Context
	 *
	 * @return the servlet context
	 */
	public ServletContext getServletContext() {
		return session.getServletContext();
	}

	/**
	 * @param session The HTTP Session
	 */
	public HttpSessionWrapper(HttpSession session) {
		this.session = session;
	}

	/**
	 * @param name the name of the attribute
	 * @return Returns the session attribute object or null
	 */
	public Object getAttribute(String name) {
		return session.getAttribute(name);
	}

	/**
	 * @return An enumeration of all the attribute names
	 */
	public Enumeration getAttributeNames() {
		return session.getAttributeNames();
	}

	/**
	 * @return The creation time of the session
	 */
	public long getCreationTime() {
		return session.getCreationTime();
	}

	/**
	 * @return The id of the session
	 */
	public String getId() {
		return session.getId();
	}

	/**
	 * @return The last time the session was accessed
	 */
	public long getLastAccessedTime() {
		return session.getLastAccessedTime();
	}

	/**
	 * @return The maximum inactive interval.
	 */
	public int getMaxInactiveInterval() {
		return session.getMaxInactiveInterval();
	}

	/**
	 * Invalidate the session.
	 */
	public void invalidate() {
		session.invalidate();
	}

	/**
	 * @return A boolean indicating if the session was just created
	 */
	public boolean isNew() {
        return session.isNew();
    }

	/**
	 * @param name the name of the attribute
	 */
	public void removeAttribute(String name) {
		session.removeAttribute(name);
	}

	/**
	 * @param name the name of the attribute
	 * @param value the value of the attribute
	 */
	public void setAttribute(String name, Object value) {
		session.setAttribute(name, value);
	}

	/**
	 * @param interval the maximum inactive interval
	 */
	public void setMaxInactiveInterval(int interval) {
		session.setMaxInactiveInterval(interval);
	}

}
