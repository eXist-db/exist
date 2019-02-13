/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009-2011 The eXist Project
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
package org.exist.security.realm.openid;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import javax.security.auth.Subject;
import javax.servlet.*;
import javax.servlet.http.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.jetty.security.DefaultIdentityService;
import org.eclipse.jetty.server.Authentication;
import org.eclipse.jetty.server.UserIdentity;
import org.exist.Database;
import org.exist.EXistException;
import org.exist.config.ConfigurationException;
import org.exist.security.AXSchemaType;
import org.exist.security.AbstractAccount;
import org.exist.security.Account;
import org.exist.security.AbstractRealm;
import org.exist.security.PermissionDeniedException;
import org.exist.security.internal.HttpSessionAuthentication;
import org.exist.security.internal.SubjectAccreditedImpl;
import org.exist.security.internal.aider.UserAider;
import org.exist.storage.DBBroker;
import org.exist.xquery.util.HTTPUtils;
import org.openid4java.OpenIDException;
import org.openid4java.association.AssociationSessionType;
import org.openid4java.consumer.*;
import org.openid4java.discovery.*;
import org.openid4java.message.*;
import org.openid4java.message.ax.AxMessage;
import org.openid4java.message.ax.FetchRequest;
import org.openid4java.message.ax.FetchResponse;
import org.openid4java.message.sreg.SRegMessage;
import org.openid4java.message.sreg.SRegRequest;
import org.openid4java.message.sreg.SRegResponse;
import org.openid4java.util.*;

/**
 * OpenId authenticator servlet.
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * 
 */
public class AuthenticatorOpenIdServlet extends HttpServlet {

	private static final long serialVersionUID = -558037449837549034L;

	private static final Log LOG = LogFactory.getLog(AuthenticatorOpenIdServlet.class);

    public ConsumerManager manager;
    
    public static AbstractRealm realm = null;

	public AuthenticatorOpenIdServlet() throws ConsumerException {
	}

    @Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);

		// --- Forward proxy setup (only if needed) ---
		ProxyProperties proxyProps = getProxyProperties(config);
		if (proxyProps != null) {
			LOG.debug("ProxyProperties: " + proxyProps);
			HttpClientFactory.setProxyProperties(proxyProps);
		}

		manager = new ConsumerManager();
		manager.setAssociations(new InMemoryConsumerAssociationStore());
		manager.setNonceVerifier(new InMemoryNonceVerifier(5000));
		manager.setMinAssocSessEnc(AssociationSessionType.DH_SHA256);
	}

    @Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		doPost(req, resp);
	}

    @Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		if ("true".equals(req.getParameter("is_return"))) {
			processReturn(req, resp);
		} else {
			String identifier = req.getParameter("openid_identifier");
			if (identifier != null) {
				this.authRequest(identifier, req, resp);
			} else {
				//this.getServletContext().getRequestDispatcher("/openid/login.xql").forward(req, resp);
				resp.sendRedirect(
					OpenIDRealm.instance.getSecurityManager().getAuthenticationEntryPoint()
				);
			}
		}
	}

	private void processReturn(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		Account principal = this.verifyResponse(req);
		
		//System.out.println(principal);
        
		String returnURL = req.getParameter("exist_return");

        if (principal == null) {
			//this.getServletContext().getRequestDispatcher("/openid/login.xql").forward(req, resp);
			resp.sendRedirect(returnURL);
		} else {
	        HttpSession session = req.getSession(true);

			//((XQueryURLRewrite.RequestWrapper)req).setUserPrincipal(principal);

			Subject subject = new Subject();
			
			//TODO: hardcoded to jetty - rewrite
			//*******************************************************
			DefaultIdentityService _identityService = new DefaultIdentityService();
			UserIdentity user = _identityService.newUserIdentity(subject, principal, new String[0]);
            
			Authentication cached=new HttpSessionAuthentication(session, user);
            session.setAttribute(HttpSessionAuthentication.__J_AUTHENTICATED, cached);
			//*******************************************************
            
			resp.sendRedirect(returnURL);
		}
	}

	// authentication request
	public String authRequest(String userSuppliedString,
			HttpServletRequest httpReq, HttpServletResponse httpResp)
			throws IOException, ServletException {

		if (OpenIDRealm.instance == null) {
			ServletOutputStream out = httpResp.getOutputStream();
	        httpResp.setContentType("text/html; charset=\"UTF-8\"");
	        httpResp.addHeader( "pragma", "no-cache" );
	        httpResp.addHeader( "Cache-Control", "no-cache" );

	        httpResp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

	        out.print("<html><head>");
	        out.print("<title>OpenIDServlet Error</title>");
	        out.print("<link rel=\"stylesheet\" type=\"text/css\" href=\"error.css\"></link></head>");
	        out.print("<body><div id=\"container\"><h1>Error found</h1>");
            
	        out.print("<h2>Message:");
            out.print("OpenID realm wasn't initialized.");
            out.print("</h2>");
	        
            //out.print(HTTPUtils.printStackTraceHTML(t));
	        
	        out.print("</div></body></html>");
	        return null;
		}
		try {
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
			
			if (authReq.getOPEndpoint().indexOf("myopenid.com")>0) {
				SRegRequest sregReq = SRegRequest.createFetchRequest();

				sregReq.addAttribute(AXSchemaType.FULLNAME.name().toLowerCase(), true);
				sregReq.addAttribute(AXSchemaType.EMAIL.name().toLowerCase(), true);
				sregReq.addAttribute(AXSchemaType.COUNTRY.name().toLowerCase(), true);
				sregReq.addAttribute(AXSchemaType.LANGUAGE.name().toLowerCase(), true);

				authReq.addExtension(sregReq);
			} else {

				FetchRequest fetch = FetchRequest.createFetchRequest();

				fetch.addAttribute(AXSchemaType.FIRSTNAME.getAlias(), AXSchemaType.FIRSTNAME.getNamespace(), true);
				fetch.addAttribute(AXSchemaType.LASTNAME.getAlias(), AXSchemaType.LASTNAME.getNamespace(), true);
				fetch.addAttribute(AXSchemaType.EMAIL.getAlias(), AXSchemaType.EMAIL.getNamespace(), true);
				fetch.addAttribute(AXSchemaType.COUNTRY.getAlias(), AXSchemaType.COUNTRY.getNamespace(), true);
				fetch.addAttribute(AXSchemaType.LANGUAGE.getAlias(), AXSchemaType.LANGUAGE.getNamespace(), true);

				// wants up to three email addresses
				fetch.setCount(AXSchemaType.EMAIL.getAlias(), 3);

				authReq.addExtension(fetch);
			}
			
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
				for (Entry<String, String> entry : parameterMap.entrySet()) {
					out.println("	<input type=\"hidden\" name=\""+entry.getKey()+"\" value=\""+entry.getValue()+"\"/>");
				}
				
				out.println("        <button type=\"submit\">Continue...</button>");
				out.println("    </form>");
				out.println("</body>");
				out.println("</html>");
				
				out.flush();
			}
		} catch (OpenIDException e) {
			// present error to the user
			LOG.debug("OpenIDException",e);

			ServletOutputStream out = httpResp.getOutputStream();
	        httpResp.setContentType("text/html; charset=\"UTF-8\"");
	        httpResp.addHeader( "pragma", "no-cache" );
	        httpResp.addHeader( "Cache-Control", "no-cache" );

	        httpResp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

	        out.print("<html><head>");
	        out.print("<title>OpenIDServlet Error</title>");
	        out.print("<link rel=\"stylesheet\" type=\"text/css\" href=\"error.css\"></link></head>");
	        out.print("<body><div id=\"container\"><h1>Error found</h1>");
            
	        out.print("<h2>Message:");
            out.print(e.getMessage());
            out.print("</h2>");
	        
	        Throwable t = e.getCause();
	        if(t!=null){
	            // t can be null
	            out.print(HTTPUtils.printStackTraceHTML(t));
	        }
	        
	        out.print("</div></body></html>");
		}

		return null;
	}

	// authentication response
	public Account verifyResponse(HttpServletRequest httpReq)
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
			VerificationResult verification = manager.verify(receivingURL.toString(), response, discovered);

			// examine the verification result and extract the verified
			// identifier
			Identifier verified = verification.getVerifiedId();
			if (verified != null) {
				// success
				
				String accountName = AccountImpl.escape(verified.getIdentifier());
				AbstractAccount account = (AbstractAccount) OpenIDRealm.instance.getAccount(accountName);
				if (account == null) {
					Database db = OpenIDRealm.instance.getDatabase();
					try(final DBBroker broker = db.get(Optional.of(db.getSecurityManager().getSystemSubject()))) {
						//XXX: set OpenID group by default 
						account = (AbstractAccount) OpenIDRealm.instance.addAccount(
								new UserAider(OpenIDRealm.instance.getId(), accountName)
							);
					}
				}
				
				org.exist.security.Subject principal = 
					new SubjectAccreditedImpl( account, verified );
				
				AuthSuccess authSuccess = (AuthSuccess) verification.getAuthResponse();
				authSuccess.getExtensions();

				if (authSuccess.hasExtension(SRegMessage.OPENID_NS_SREG)) {
					MessageExtension ext = authSuccess.getExtension(SRegMessage.OPENID_NS_SREG);
					if (ext instanceof SRegResponse) {
						SRegResponse sregResp = (SRegResponse) ext;
						for (Iterator iter = sregResp.getAttributeNames().iterator(); iter.hasNext();) {
							String name = (String) iter.next();
							if (LOG.isDebugEnabled())
								LOG.debug(name + " : " + sregResp.getParameterValue(name));
							principal.setMetadataValue(AXSchemaType.valueOfNamespace(name), sregResp.getParameterValue(name));
						}
					}
				}
				if (authSuccess.hasExtension(AxMessage.OPENID_NS_AX)) {
					FetchResponse fetchResp = (FetchResponse) authSuccess.getExtension(AxMessage.OPENID_NS_AX);

					List aliases = fetchResp.getAttributeAliases();
					for (Iterator iter = aliases.iterator(); iter.hasNext();) {
						String alias = (String) iter.next();
						List values = fetchResp.getAttributeValues(alias);
						if (values.size() > 0) {
							if (LOG.isDebugEnabled())
								LOG.debug(alias + " : " + values.get(0));
							principal.setMetadataValue(AXSchemaType.valueOfAlias(alias), (String)values.get(0));
						}
					}
				}
				//update metadata
				Database db = OpenIDRealm.instance.getDatabase();
				try(final DBBroker broker = db.get(Optional.of(db.getSecurityManager().getSystemSubject()))) {
					OpenIDRealm.instance.updateAccount(principal);
				}
				
                OpenIDUtility.registerUser(principal);
				return principal; 
			}
		} catch (OpenIDException e) {
			LOG.error(e);
		} catch (ConfigurationException e) {
			LOG.error(e);
		} catch (PermissionDeniedException e) {
			LOG.error(e);
		} catch (EXistException e) {
			LOG.error(e);
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
