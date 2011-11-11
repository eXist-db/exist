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

import org.exist.config.Configurable;
import org.exist.config.ConfigurableObject;
import org.exist.config.Configuration;
import org.exist.config.Configurator;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationFieldAsAttribute;
import org.scribe.builder.ServiceBuilder;
import org.scribe.builder.api.FacebookApi;

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
	String name = null;

	@ConfigurationFieldAsAttribute("key")
	String apiKey = null;//"137486769663489";
    
    @ConfigurationFieldAsAttribute("secret")
    String apiSecret = null;//"aa5bcbe021ba65c4eaaf8fc29f5aa434";

	public Service(OAuthRealm realm, Configuration config) {

		configuration = Configurator.configure(this, config);
	}

	public String getName() {
		return name;
	}

	public ServiceBuilder getServiceBuilder() {
        return new ServiceBuilder()
        		.provider(FacebookApi.class)
        		.apiKey(apiKey)
        		.apiSecret(apiSecret);
	}

	public boolean isConfigured() {
		return configuration != null;
	}

	public Configuration getConfiguration() {
		return configuration;
	}
}