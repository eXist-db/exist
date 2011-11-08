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
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.oauth.consumer.OAuth2Consumer;
import net.oauth.enums.ResponseType;
import net.oauth.exception.OAuthException;

import com.neurologic.oauth.config.ConsumerConfig;
import com.neurologic.oauth.config.OAuthConfig;
import com.neurologic.oauth.config.ProviderConfig;
import com.neurologic.oauth.config.ServiceConfig;
import com.neurologic.oauth.config.SuccessConfig;
import com.neurologic.oauth.service.OAuthService;
import com.neurologic.oauth.service.factory.OAuthServiceAbstractFactory;
import com.neurologic.oauth.service.impl.OAuth2Service;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class OAuthServlet extends HttpServlet {
	
	private static final long serialVersionUID = -6984614473391594578L;

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
        String path = request.getPathInfo();
        
        if (OAuthRealm.LOG.isTraceEnabled())
        	OAuthRealm.LOG.trace("the " + request.getMethod() + " method, path info "+path);
        
        try {
	        ServiceConfig serviceConfig = OAuthRealm._.getServiceConfigByPath(path);
	        
	        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
	        if (classLoader == null)
	            classLoader = this.getClass().getClassLoader();
	        
	        Class<?> clazz = classLoader.loadClass(serviceConfig.getServiceClass());
	        if (clazz == null)
	            throw new Exception("No class exits for " + serviceConfig.getServiceClass());
	        
	        OAuthConfig oauthConfig = OAuthRealm._.getOAuthConfigByName(serviceConfig.getRefOAuth());
	        
	        ProviderConfig providerConfig = oauthConfig.getProvider();
	        ConsumerConfig consumerConfig = oauthConfig.getConsumer();
	        if (providerConfig == null)
	            throw new Exception("No <provider> defined under <oauth>. Cannot create OAuth Service Provider.");
	        
	        if (consumerConfig == null)
	            throw new Exception("No <consumer> defined under <oauth>. Cannot create OAuth Consumer.");
	        
	        OAuthService service = OAuthServiceAbstractFactory.getOAuthServiceFactory(oauthConfig.getVersion()).createOAuthService(clazz, providerConfig, consumerConfig);
	        if (service instanceof OAuth2ServiceAtEXist) {
				((OAuth2ServiceAtEXist) service).setServiceConfig(serviceConfig);
				((OAuth2ServiceAtEXist) service).setRedirectUri(request.getRequestURL().toString());
			}

			if (request.getParameterMap().containsKey("auth")) {
            	if (service instanceof OAuth2Service) {
            		OAuth2Service s2 = (OAuth2Service)service; 
					response.sendRedirect(
							getConsumer(s2).generateRequestAuthorizationUrl(
							ResponseType.CODE, getRedirectUri(s2), null, (String[])null)
					);
					return;
				} else
					throw new OAuthException("unsuppored OAuth service "+service);
			}
				
	        service.execute(request, response);
	        
	        //Finally
	        SuccessConfig successConfig = serviceConfig.getSuccessConfig();
	        if (successConfig != null) {
	            if (OAuthRealm.LOG.isInfoEnabled())
	            	OAuthRealm.LOG.info("Dispatching to path \"" + successConfig.getPath() + "\".");
	                
	            RequestDispatcher dispatcher = request.getRequestDispatcher(successConfig.getPath());
	            dispatcher.forward(request, response);
	        }
        } catch (Exception e) {
			throw new ServletException(e.getMessage(), e);
		}
    }
    
    private OAuth2Consumer getConsumer(OAuth2Service s2) throws Exception {
    	Field field = OAuth2Service.class.getDeclaredField("consumer");
    	field.setAccessible(true);
    	return (OAuth2Consumer) field.get(s2);
    }

    private String getRedirectUri(OAuth2Service s2) throws Exception {
    	Method method = s2.getClass().getMethod("getRedirectUri");
    	method.setAccessible(true);
    	return (String) method.invoke(s2);
    }
}
