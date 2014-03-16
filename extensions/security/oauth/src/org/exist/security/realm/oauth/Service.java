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

import javax.servlet.http.HttpServletRequest;

import org.exist.config.Configurable;
import org.exist.config.Configuration;
import org.exist.config.Configurator;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationFieldAsAttribute;
import org.scribe.builder.ServiceBuilder;
import org.scribe.builder.api.Api;
import org.scribe.builder.api.FacebookApi;
import org.scribe.model.Token;
import org.scribe.oauth.OAuthService;

/**
 * <service name="app" key="APP_ID" secret="APP_SECRET" />
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * 
 */
@ConfigurationClass("service")
public class Service implements Configurable {

    private Configuration configuration = null;

    @ConfigurationFieldAsAttribute("name")
    String name;

    @ConfigurationFieldAsAttribute("key")
    String apiKey;

    @ConfigurationFieldAsAttribute("secret")
    String apiSecret;

    @ConfigurationFieldAsAttribute("provider")
    String provider;

    @ConfigurationFieldAsAttribute("return-url")
    String return_url;

    public Service(OAuthRealm realm, Configuration config) {

        configuration = Configurator.configure(this, config);
    }

    public String getName() {
        return name;
    }

    public ServiceBuilder getServiceBuilder() {
        return new ServiceBuilder()
            .provider(getProviderClass())
            .apiKey(apiKey)
            .apiSecret(apiSecret);
    }

    private String getProvider() {
        if (provider == null)
            throw new IllegalArgumentException("Provider was not set.");

        return provider;
    }

    private Class<? extends Api> getProviderClass() {
        String provider = getProvider().toLowerCase();

        if (provider.equalsIgnoreCase("facebook"))
            return FacebookApi.class;
        else if (provider.equalsIgnoreCase("google"))
            return Google2Api.class;

        throw new IllegalArgumentException("Unknown provider '" + provider + "'");
    }

    public void saveAccessToken(HttpServletRequest request, OAuthService service, Token accessToken) throws Exception {
        String provider = getProvider().toLowerCase();

        if (provider.equalsIgnoreCase("facebook")) {
            ServiceFacebook.saveAccessToken(request, service, accessToken);
        
        } else if (provider.equalsIgnoreCase("google")) {
            ServiceGoogle.saveAccessToken(request, service, accessToken);
        
        } else {
            throw new IllegalArgumentException("Unknown provider '" + provider + "'");
        }
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getApiSecret() {
        return apiSecret;
    }
    
    public String getReturnURL() {
        return return_url;
    }

    public boolean isConfigured() {
        return configuration != null;
    }

    public Configuration getConfiguration() {
        return configuration;
    }
}