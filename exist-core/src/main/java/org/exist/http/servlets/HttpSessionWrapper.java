/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.http.servlets;

import java.util.Enumeration;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;

/**
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class HttpSessionWrapper implements SessionWrapper {

	private final HttpSession session;

	/**
	 * @param session The HTTP Session
	 */
	public HttpSessionWrapper(final HttpSession session) {
		this.session = session;
	}

	/**
	 * Get the Servlet Context
	 *
	 * @return the servlet context
	 */
	public ServletContext getServletContext() {
		return session.getServletContext();
	}

	@Override
	public Object getAttribute(final String name) {
		return session.getAttribute(name);
	}

	@Override
	public Enumeration<String> getAttributeNames() {
		return session.getAttributeNames();
	}

	@Override
	public long getCreationTime() {
		return session.getCreationTime();
	}

	@Override
	public String getId() {
		return session.getId();
	}

	@Override
	public long getLastAccessedTime() {
		return session.getLastAccessedTime();
	}

	@Override
	public int getMaxInactiveInterval() {
		return session.getMaxInactiveInterval();
	}

	@Override
	public void invalidate() {
		session.invalidate();
	}

	@Override
	public boolean isNew() {
        return session.isNew();
    }

	@Override
	public void removeAttribute(final String name) {
		session.removeAttribute(name);
	}

	@Override
	public void setAttribute(final String name, final Object value) {
		session.setAttribute(name, value);
	}

	@Override
	public void setMaxInactiveInterval(final int interval) {
		session.setMaxInactiveInterval(interval);
	}

	@Override
	public boolean isInvalid() {
		try {
			session.getLastAccessedTime();  // will throw IllegalStateException if session is Invalidated
			return false;
		} catch (final IllegalStateException e) {
			// thrown by HttpSession#getLastAccessedTime() if the session was invalidated!
			return true;
		}
	}
}
