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
package org.exist.security.internal;

import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionAttributeListener;
import javax.servlet.http.HttpSessionBindingEvent;

import org.eclipse.jetty.security.UserAuthentication;
import org.eclipse.jetty.server.UserIdentity;
import org.eclipse.jetty.util.security.Constraint;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class HttpSessionAuthentication extends UserAuthentication implements HttpSessionAttributeListener {

	public static final String __J_AUTHENTICATED = "org.eclipse.jetty.security.UserIdentity";
    
	HttpSession _session;
    
    public HttpSessionAuthentication(HttpSession session, UserIdentity userIdentity) {
        super(Constraint.__FORM_AUTH, userIdentity);
        _session=session;
    }

    @Override
    public void attributeAdded(HttpSessionBindingEvent event) {
    }

    @Override
    public void attributeRemoved(HttpSessionBindingEvent event) {
        super.logout();
    }
    
    @Override
    public void attributeReplaced(HttpSessionBindingEvent event) {
        if (event.getValue()==null)
            {super.logout();}
    }

    @Override
    public void logout() {    
        _session.removeAttribute(HttpSessionAuthentication.__J_AUTHENTICATED);
    }
    
    @Override
    public String toString() {
        return "Session"+super.toString();
    }
}
