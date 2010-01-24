/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010 The eXist Project
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
package org.exist.security.openid;

import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionAttributeListener;
import javax.servlet.http.HttpSessionBindingEvent;

import org.eclipse.jetty.security.Authenticator;
import org.eclipse.jetty.security.UserAuthentication;
import org.eclipse.jetty.server.UserIdentity;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class SessionAuthentication extends UserAuthentication implements HttpSessionAttributeListener {

	public static String __J_AUTHENTICATED = "org.eclipse.jetty.security.UserIdentity";
    
	HttpSession _session;
    
    public SessionAuthentication(HttpSession session,Authenticator authenticator, UserIdentity userIdentity) {
        super(authenticator,userIdentity);
        _session=session;
    }

    public void attributeAdded(HttpSessionBindingEvent event) {
    }

    public void attributeRemoved(HttpSessionBindingEvent event) {
        super.logout();
    }
    
    public void attributeReplaced(HttpSessionBindingEvent event) {
        if (event.getValue()==null)
            super.logout();
    }

    public void logout() {    
        _session.removeAttribute(SessionAuthentication.__J_AUTHENTICATED);
    }
    
    public String toString() {
        return "Session"+super.toString();
    }
}
