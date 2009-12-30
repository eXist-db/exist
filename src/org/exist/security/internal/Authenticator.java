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
 *  $Id:$
 */
package org.exist.security.internal;

import java.io.IOException;
import java.io.Serializable;
import java.security.Principal;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;

import org.apache.log4j.Logger;
import org.mortbay.jetty.Request;
import org.mortbay.jetty.Response;
import org.mortbay.jetty.security.Credential;
import org.mortbay.jetty.security.Password;
import org.mortbay.jetty.security.SSORealm;
import org.mortbay.jetty.security.UserRealm;
import org.mortbay.util.StringUtil;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Authenticator extends org.mortbay.jetty.security.FormAuthenticator {
	
	private static final long serialVersionUID = -3435898377862122388L;

	private final static Logger LOG = Logger.getLogger(Authenticator.class);

	public final static String AUTHENTICATED="org.exist.jetty.Auth";
    public final static String URI="org.exist.jetty.URI";

//    public final static String SECURITY_CHECK="/exist_security_check";
	public final static String USERNAME="exist_username";
	public final static String PASSWORD="exist_password";

	@Override
	public Principal authenticate(UserRealm realm, String pathInContext,
			Request request, Response response) throws IOException {

        // Setup session 
        HttpSession session=request.getSession(response!=null);
        if (session==null)
            return null;
        
        if ( pathInContext.endsWith(__J_SECURITY_CHECK) ) {
            // Check the session object for login info.
            EXistCredential cred=new EXistCredential();
            cred.authenticate(realm, request.getParameter(USERNAME), request.getParameter(PASSWORD), request);
            
            String nuri = request.getRequestURI();
            nuri = nuri.substring(0, nuri.length() - __J_SECURITY_CHECK.length());
            
            if (cred.user != null) {
                // Authenticated OK
                session.removeAttribute(URI); // Remove popped return URI.
                request.setAuthType(getAuthMethod());
                request.setUserPrincipal(cred.user);
                session.setAttribute(AUTHENTICATED,cred);

                // Sign-on to SSO mechanism
                if (realm instanceof SSORealm)
                    ((SSORealm)realm).setSingleSignOn(request,response,cred.user,new Password(cred._password));

                // Redirect to original request
                if (response != null) {
                    response.setContentLength(0);
                    response.sendRedirect(response.encodeRedirectURL(nuri));
                }
            } else {
                if(LOG.isDebugEnabled())LOG.debug("Form authentication FAILED for "+StringUtil.printable(cred._username));
                
//                if (_formErrorPage==null) {
                    if (response != null) 
                        response.sendError(HttpServletResponse.SC_FORBIDDEN);
//                } else {
//                    if (response != null)
//                        response.setContentLength(0);
//                        response.sendRedirect(response.encodeRedirectURL
//                                          (URIUtil.addPaths(request.getContextPath(),
//                                                        _formErrorPage)));
            }
            // Security check is always false, only true after final redirection.
            return null;
        }
        
        // Check if the session is already authenticated.
        EXistCredential cred = (EXistCredential) session.getAttribute(AUTHENTICATED);
        
        if (cred != null) {
            // We have a credential. Has it been distributed?
            if (cred.user==null) {
                // This credential appears to have been distributed.  Need to reauth
                cred.authenticate(realm, request);
                
                // Sign-on to SSO mechanism
                if (cred.user!=null && realm instanceof SSORealm)
                    ((SSORealm)realm).setSingleSignOn(request,response,cred.user,new Password(cred._password));
                
            } else if (!realm.reauthenticate(cred.user))
                // Else check that it is still authenticated.
                cred.user=null;

            // If this credential is still authenticated
            if (cred.user!=null) {
                if(LOG.isDebugEnabled())LOG.debug("FORM Authenticated for "+cred.user.getName());
                request.setAuthType(getAuthMethod());
                request.setUserPrincipal(cred.user);
                return cred.user;
            } else
                session.setAttribute(AUTHENTICATED,null);
        } else if (realm instanceof SSORealm) {
            // Try a single sign on.
            Credential credSSO = ((SSORealm)realm).getSingleSignOn(request,response);
            
            if (request.getUserPrincipal()!=null)
            {
                cred=new EXistCredential();
                cred.user=request.getUserPrincipal();
                cred._username = cred.user.getName();
                if (credSSO!=null)
                    cred._password = credSSO.toString();
                if(LOG.isDebugEnabled())LOG.debug("SSO for "+cred.user);
                           
                request.setAuthType(getAuthMethod());
                session.setAttribute(AUTHENTICATED,cred);
                return cred.user;
            }
        }
        
//        // Don't authenticate authform or errorpage
//        if (isLoginOrErrorPage(pathInContext))
//            return SecurityHandler.__NOBODY;
//        
//        // redirect to login page
//        if (response!=null)
//        {
//            if (request.getQueryString()!=null)
//                uri+="?"+request.getQueryString();
//            session.setAttribute(URI, 
//                                 request.getScheme() +
//                                 "://" + request.getServerName() +
//                                 ":" + request.getServerPort() +
//                                 URIUtil.addPaths(request.getContextPath(),uri));
//            response.setContentLength(0);
//            response.sendRedirect(response.encodeRedirectURL(URIUtil.addPaths(request.getContextPath(),
//                                                                          _formLoginPage)));
//        }

        return null;
	}

	@Override
	public String getAuthMethod() {
		return HttpServletRequest.FORM_AUTH;
	}

	private static class EXistCredential implements Serializable, HttpSessionBindingListener {

		private static final long serialVersionUID = -3962684008216876908L;

		private Principal user = null;
		
		private String _username;
		private String _password;
		
		private boolean authenticate(UserRealm realm, String username, String password, Request request) {
			_username = username;
			_password = password;
			
			user = realm.authenticate(username, password, request);
	        if (user == null) {
	        	LOG.warn("AUTH FAILURE: user "+request.getParameter(USERNAME));
	            request.setUserPrincipal(null);
	            return false;
	        }
	        
	        if(LOG.isDebugEnabled())LOG.debug("Form authentication OK for "+username);
	        
	        return true;
		}
		
		public void authenticate(UserRealm realm, Request request) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void valueBound(HttpSessionBindingEvent event) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void valueUnbound(HttpSessionBindingEvent event) {
			// TODO Auto-generated method stub
			
		}
		
	}
}
