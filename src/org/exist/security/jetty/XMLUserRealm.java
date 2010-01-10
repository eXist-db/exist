/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009 The eXist Project
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
package org.exist.security.jetty;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import org.exist.EXistException;
import org.exist.security.AuthenticationException;
import org.exist.security.Realm;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.mortbay.jetty.Request;
import org.mortbay.jetty.Response;
import org.mortbay.jetty.security.Credential;
import org.mortbay.jetty.security.SSORealm;
import org.mortbay.jetty.security.UserRealm;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class XMLUserRealm implements Realm, UserRealm, SSORealm {
	
	private Map<String, User> users = new HashMap<String, User>();

    private SSORealm ssoRealm;
	
	/* (non-Javadoc)
	 * @see org.mortbay.jetty.security.UserRealm#authenticate(java.lang.String, java.lang.Object, org.mortbay.jetty.Request)
	 */
	@Override
	public Principal authenticate(String username, Object credentials,
			Request request) {

		User user;
        synchronized (this)
        {
            user = users.get(username);
            if (user==null) {
    			try {
    				user = BrokerPool.getInstance().getSecurityManager().authenticate(this, username, credentials);
    			} catch (EXistException e) {
    				return null;
    			} catch (AuthenticationException e) {
    				return null;
				}

    	        users.put(username, user);
            }
        }
        
        if (user.authenticate(credentials))
            return user;
        
        return null;
	}

	/* (non-Javadoc)
	 * @see org.mortbay.jetty.security.UserRealm#disassociate(java.security.Principal)
	 */
	@Override
	public void disassociate(Principal user) {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.mortbay.jetty.security.UserRealm#getName()
	 */
	@Override
	public String getName() {
		return "eXist-DB";
	}

	/* (non-Javadoc)
	 * @see org.mortbay.jetty.security.UserRealm#getPrincipal(java.lang.String)
	 */
	@Override
	public Principal getPrincipal(String username) {
		return users.get(username);
	}

	/* (non-Javadoc)
	 * @see org.mortbay.jetty.security.UserRealm#isUserInRole(java.security.Principal, java.lang.String)
	 */
	@Override
	public synchronized boolean isUserInRole(Principal user, String role) {
        if (user==null || !(user instanceof User) || ((User)user).getRealm()!=this)
            return false;
        
        //role eq group ? -shabanovd
        return ((User)user).hasGroup(role);
	}

	/* (non-Javadoc)
	 * @see org.mortbay.jetty.security.UserRealm#logout(java.security.Principal)
	 */
	@Override
	public void logout(Principal user) {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.mortbay.jetty.security.UserRealm#popRole(java.security.Principal)
	 */
	@Override
	public Principal popRole(Principal user) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.mortbay.jetty.security.UserRealm#pushRole(java.security.Principal, java.lang.String)
	 */
	@Override
	public Principal pushRole(Principal user, String role) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.mortbay.jetty.security.UserRealm#reauthenticate(java.security.Principal)
	 */
	@Override
	public boolean reauthenticate(Principal user) {
		if (user instanceof User)
			return ((User) user).isAuthenticated();
			
		return false;
	}

	/* (non-Javadoc)
	 * @see org.mortbay.jetty.security.SSORealm#clearSingleSignOn(java.lang.String)
	 */
	@Override
	public void clearSingleSignOn(String username) {
        if (ssoRealm!=null)
            ssoRealm.clearSingleSignOn(username);
	}

	/* (non-Javadoc)
	 * @see org.mortbay.jetty.security.SSORealm#getSingleSignOn(org.mortbay.jetty.Request, org.mortbay.jetty.Response)
	 */
	@Override
	public Credential getSingleSignOn(Request request, Response response) {
        if (ssoRealm!=null)
            return ssoRealm.getSingleSignOn(request,response);
        return null;
	}

	/* (non-Javadoc)
	 * @see org.mortbay.jetty.security.SSORealm#setSingleSignOn(org.mortbay.jetty.Request, org.mortbay.jetty.Response, java.security.Principal, org.mortbay.jetty.security.Credential)
	 */
	@Override
	public void setSingleSignOn(Request request, Response response,
			Principal principal, Credential credential) {
        if (ssoRealm!=null)
        	ssoRealm.setSingleSignOn(request,response,principal,credential);
	}

    public String toString() {
        return "Realm["+getName()+"]=="+users.keySet();
    }
	
}
