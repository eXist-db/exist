/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2011 The eXist Project
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
 *  $Id$
 */
package org.exist.security.realm.oauth;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.scribe.model.OAuthConstants;
import org.scribe.model.OAuthRequest;
import org.scribe.model.Token;
import org.scribe.model.Verifier;
import org.scribe.oauth.OAuthService;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class OAuthServlet extends HttpServlet {
	
	private static final long serialVersionUID = -4097068486788440559L;
	
	private static final String RETURN_TO_PAGE = "returnToPage";

	private Token EMPTY_TOKEN = null;
    
	public void init() throws ServletException {
	}

	/* (non-Javadoc)
     * @see javax.servlet.http.HttpServlet#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        process(request, response);
    }

    /* (non-Javadoc)
     * @see javax.servlet.http.HttpServlet#doPost(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        process(request, response);
    }
    
    private void process(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    	if (request.getPathInfo() == null) return;
    	
        String path = request.getPathInfo().replace("/", "");
        
        if (OAuthRealm.LOG.isTraceEnabled())
        	OAuthRealm.LOG.trace("the " + request.getMethod() + " method, path info "+path);
        
        OAuthService service = 
    		OAuthRealm._.getServiceBulderByPath(path)
    			.getServiceBuilder()
    			.callback(request.getRequestURL().toString())
    			.build();
        
        if (request.getParameterMap().containsKey(RETURN_TO_PAGE)) {

        	String authorizationUrl = service.getAuthorizationUrl(EMPTY_TOKEN);
	        
	        request.getSession().setAttribute(RETURN_TO_PAGE, request.getParameter(RETURN_TO_PAGE));

	        response.sendRedirect(authorizationUrl);
	        return;
        }
        
        String verification = request.getParameter("code");
        
        Verifier verifier = new Verifier(verification);
        Token accessToken = null;
        //workaround google API
        if (OAuthRealm._.getServiceBulderByPath(path).provider.equalsIgnoreCase("google")) {
        	Google2Api api = new Google2Api();
        	
        	Service config = OAuthRealm._.getServiceBulderByPath(path);

	        OAuthRequest req = new OAuthRequest(api.getAccessTokenVerb(), api.getAccessTokenEndpoint());
	        req.addBodyParameter(OAuthConstants.CLIENT_ID, config.getApiKey());
	        req.addBodyParameter(OAuthConstants.CLIENT_SECRET, config.getApiSecret());
	        req.addBodyParameter(OAuthConstants.CODE, verification);
        	req.addBodyParameter(OAuthConstants.REDIRECT_URI, "http://localhost:8080/oauth/cook2gl");
	        req.addBodyParameter("grant_type", "authorization_code");
	        accessToken = api.getAccessTokenExtractor().extract(req.send().getBody());
        } else
        	accessToken = service.getAccessToken(EMPTY_TOKEN, verifier);
        
        try {
        	OAuthRealm._.getServiceBulderByPath(path).saveAccessToken(request, service, accessToken);
		} catch (Exception e) {
			throw new ServletException(e);
		}
        
        String returnToPage = (String)request.getSession().getAttribute(RETURN_TO_PAGE);
        response.sendRedirect(returnToPage);
    }
}
