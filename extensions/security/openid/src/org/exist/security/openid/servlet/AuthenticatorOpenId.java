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
package org.exist.security.openid.servlet;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.security.auth.Subject;
import javax.servlet.*;
import javax.servlet.http.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.jetty.security.DefaultIdentityService;
import org.eclipse.jetty.security.authentication.FormAuthenticator;
import org.eclipse.jetty.server.Authentication;
import org.eclipse.jetty.server.UserIdentity;
import org.exist.security.User;
import org.exist.security.openid.SessionAuthentication;
import org.exist.security.openid.UserImpl;
import org.openid4java.OpenIDException;
import org.openid4java.association.AssociationSessionType;
import org.openid4java.consumer.*;
import org.openid4java.discovery.*;
import org.openid4java.message.*;
import org.openid4java.util.*;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * 
 */
public class AuthenticatorOpenId extends HttpServlet {

	private static final long serialVersionUID = -2924397314671034627L;

	private static final Log LOG = LogFactory.getLog(AuthenticatorOpenId.class);

	public ConsumerManager manager;

	public AuthenticatorOpenId() throws ConsumerException {
	}

	public void init(ServletConfig config) throws ServletException {
		super.init(config);

		// --- Forward proxy setup (only if needed) ---
		ProxyProperties proxyProps = getProxyProperties(config);
		if (proxyProps != null) {
			LOG.debug("ProxyProperties: " + proxyProps);
			HttpClientFactory.setProxyProperties(proxyProps);
		}

		try {
			this.manager = new ConsumerManager();
		} catch (ConsumerException e) {
			throw new ServletException(e);
		}

		manager.setAssociations(new InMemoryConsumerAssociationStore());
		manager.setNonceVerifier(new InMemoryNonceVerifier(5000));
		manager.setMinAssocSessEnc(AssociationSessionType.DH_SHA256);
	}

	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		doPost(req, resp);
	}

	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		if ("true".equals(req.getParameter("is_return"))) {
			processReturn(req, resp);
		} else {
			String identifier = req.getParameter("openid_identifier");
			if (identifier != null) {
				this.authRequest(identifier, req, resp);
			} else {

//				this.getServletContext().getRequestDispatcher("/openid/login.xql")
//						.forward(req, resp);
				resp.sendRedirect("openid/login.xql");
			}
		}
	}

	private void processReturn(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		Identifier identifier = this.verifyResponse(req);
		
		LOG.debug("identifier: " + identifier);
		System.out.println("identifier: " + identifier);
		
        String returnURL = req.getParameter("exist_return");

        if (identifier == null) {
//			this.getServletContext().getRequestDispatcher("/openid/login.xql").forward(req, resp);
			resp.sendRedirect(returnURL);
		} else {
	        HttpSession session = req.getSession(true);

	        User principal = new UserImpl(identifier);
//			((XQueryURLRewrite.RequestWrapper)req).setUserPrincipal(principal);

			Subject subject = new Subject();
			
			//TODO: hardcoded to jetty - rewrite
			DefaultIdentityService _identityService = new DefaultIdentityService();
			UserIdentity user = _identityService.newUserIdentity(subject, principal, new String[0]);
            
			FormAuthenticator authenticator = new FormAuthenticator("","",false);
			
			Authentication cached=new SessionAuthentication(session,authenticator,user);
            session.setAttribute(SessionAuthentication.__J_AUTHENTICATED, cached);
			
			resp.sendRedirect(returnURL);
		}
	}

	// authentication request
	public String authRequest(String userSuppliedString,
			HttpServletRequest httpReq, HttpServletResponse httpResp)
			throws IOException, ServletException {

		try {
			httpReq.getContextPath();

			String returnAfterAuthentication = httpReq.getParameter("return_to");

			// configure the return_to URL where your application will receive
			// the authentication responses from the OpenID provider
			String returnToUrl = httpReq.getRequestURL().toString() + "?is_return=true&exist_return="+returnAfterAuthentication;

			// perform discovery on the user-supplied identifier
			List<?> discoveries = manager.discover(userSuppliedString);

			// attempt to associate with the OpenID provider
			// and retrieve one service endpoint for authentication
			DiscoveryInformation discovered = manager.associate(discoveries);

			// store the discovery information in the user's session
			httpReq.getSession().setAttribute("openid-disc", discovered);

			// obtain a AuthRequest message to be sent to the OpenID provider
			AuthRequest authReq = manager.authenticate(discovered, returnToUrl);

			if (!discovered.isVersion2()) {
				// Option 1: GET HTTP-redirect to the OpenID Provider endpoint
				// The only method supported in OpenID 1.x
				// redirect-URL usually limited ~2048 bytes
				httpResp.sendRedirect(authReq.getDestinationUrl(true));
				return null;

			} else {
				// Option 2: HTML FORM Redirection (Allows payloads >2048 bytes)

				Object OPEndpoint = authReq.getDestinationUrl(false);
				
				ServletOutputStream out = httpResp.getOutputStream();
		        
				httpResp.setContentType("text/html; charset=UTF-8");
				httpResp.addHeader( "pragma", "no-cache" );
				httpResp.addHeader( "Cache-Control", "no-cache" );

		        out.println("<html xmlns=\"http://www.w3.org/1999/xhtml\">");
				out.println("<head>");
				out.println("    <title>OpenID HTML FORM Redirection</title>");
				out.println("</head>");
				out.println("<body onload=\"document.forms['openid-form-redirection'].submit();\">");
				out.println("    <form name=\"openid-form-redirection\" action=\""+OPEndpoint+"\" method=\"post\" accept-charset=\"utf-8\">");

				Map<String, String> parameterMap = authReq.getParameterMap();
				for (String key : parameterMap.keySet()) {
					out.println("	<input type=\"hidden\" name=\""+key+"\" value=\""+parameterMap.get(key)+"\"/>");
				}
				
				out.println("        <button type=\"submit\">Continue...</button>");
				out.println("    </form>");
				out.println("</body>");
				out.println("</html>");
				
				out.flush();
			}
		} catch (OpenIDException e) {
			// present error to the user
		}

		return null;

	}

	// authentication response
	public Identifier verifyResponse(HttpServletRequest httpReq)
			throws ServletException {

		try {
			// extract the parameters from the authentication response
			// (which comes in as a HTTP request from the OpenID provider)
			ParameterList response = new ParameterList(httpReq
					.getParameterMap());

			// retrieve the previously stored discovery information
			DiscoveryInformation discovered = (DiscoveryInformation) httpReq
					.getSession().getAttribute("openid-disc");

			// extract the receiving URL from the HTTP request
			StringBuffer receivingURL = httpReq.getRequestURL();
			String queryString = httpReq.getQueryString();
			if (queryString != null && queryString.length() > 0)
				receivingURL.append("?").append(httpReq.getQueryString());

			// verify the response; ConsumerManager needs to be the same
			// (static) instance used to place the authentication request
			VerificationResult verification = manager.verify(receivingURL
					.toString(), response, discovered);

			// examine the verification result and extract the verified
			// identifier
			Identifier verified = verification.getVerifiedId();
			if (verified != null) {

				return verified; // success
			}
		} catch (OpenIDException e) {
			// present error to the user
		}

		return null;
	}

	private static ProxyProperties getProxyProperties(ServletConfig config) {
		ProxyProperties proxyProps;
		String host = config.getInitParameter("proxy.host");
		LOG.debug("proxy.host: " + host);
		if (host == null) {
			proxyProps = null;
		} else {
			proxyProps = new ProxyProperties();
			String port = config.getInitParameter("proxy.port");
			String username = config.getInitParameter("proxy.username");
			String password = config.getInitParameter("proxy.password");
			String domain = config.getInitParameter("proxy.domain");
			proxyProps.setProxyHostName(host);
			proxyProps.setProxyPort(Integer.parseInt(port));
			proxyProps.setUserName(username);
			proxyProps.setPassword(password);
			proxyProps.setDomain(domain);
		}
		return proxyProps;
	}

}
