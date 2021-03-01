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

import javax.servlet.http.HttpSession;
import java.util.Enumeration;

public interface SessionWrapper {

	/**
	 * Returns an Enumeration of String objects containing the names of all
	 * the objects bound to this session.
	 *
	 * @see HttpSession#getAttributeNames()
	 *
	 * @return an Enumeration of String objects specifying the names of all
	 *     the objects bound to this session.
	 */
	Enumeration<String> getAttributeNames();

	/**
	 * Returns the object bound with the specified name in this session,
	 * or null if no object is bound under the name.
	 *
	 * @see HttpSession#getAttribute(String)
	 *
	 * @param name a string specifying the name of the object.
	 *
	 * @return the object with the specified name.
	 */
	Object getAttribute(String name);

	/**
	 * Binds an object to this session, using the name specified.
	 * If an object of the same name is already bound to the session,
	 * the object is replaced.
	 *
	 * @see HttpSession#setAttribute(String, Object)
	 *
	 * @param name the name to which the object is bound; cannot be null.
	 * @param value the object to be bound.
	 */
	void setAttribute(String name, Object value);

	/**
	 * Removes the object bound with the specified name from this session.
	 *
	 * @see HttpSession#removeAttribute(String)
	 *
	 * @param name the name of the object to remove from this session.
	 */
	void removeAttribute(String name);

	/**
	 * Returns the time when this session was created, measured in milliseconds
	 * since midnight January 1, 1970 GMT.
	 *
	 * @see HttpSession#getCreationTime()
	 *
	 * @return a long specifying when this session was created, expressed
	 *     in milliseconds since 1/1/1970 GMT.
	 */
	long getCreationTime();

	/**
	 * Returns a string containing the unique identifier assigned to
	 * this session.
	 *
	 * @see HttpSession#getId()
	 *
	 * @return a string specifying the identifier assigned to this session.
	 */
	String getId();

	/**
	 * Returns the last time the client sent a request associated with this
	 * session, as the number of milliseconds since midnight January 1, 1970
	 * GMT, and marked by the time the container received the request.
	 *
	 * @see HttpSession#getLastAccessedTime()
	 *
	 * @return a long representing the last time the client sent a request
	 *     associated with this session, expressed in milliseconds since
	 *     1/1/1970 GMT.
	 */
	long getLastAccessedTime();

	/**
	 * Returns the maximum time interval, in seconds, that the servlet
	 * container will keep this session open between client accesses.
	 *
	 * @see HttpSession#getMaxInactiveInterval()
	 *
	 * @return an integer specifying the number of seconds this session remains
	 *     open between client requests.
	 */
	int getMaxInactiveInterval();

	/**
	 * Specifies the time, in seconds, between client requests before the
	 * servlet container will invalidate this session.
	 *
	 * @see HttpSession#setMaxInactiveInterval(int)
	 *
	 * @param interval An integer specifying the number of seconds.
	 */
	void setMaxInactiveInterval(int interval);

	/**
	 * Invalidates this session then unbinds any objects bound to it.
	 *
	 * @see HttpSession#invalidate()
	 */
	void invalidate();

	/**
	 * Returns true if the client does not yet know about the session or
	 * if the client chooses not to join the session.
	 *
	 * @see HttpSession#isNew()
	 *
	 * @return true if the server has created a session, but the client
	 *     has not yet joined.
	 */
	boolean isNew();

	/**
	 * Returns true of the session is invalid.
	 *
	 * @return true if the session is invalid, false otherwise.
	 */
	boolean isInvalid();
}