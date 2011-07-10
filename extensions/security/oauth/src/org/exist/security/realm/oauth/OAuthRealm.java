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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.config.Configuration;
import org.exist.config.ConfigurationException;
import org.exist.config.Configurator;
import org.exist.config.annotation.*;
import org.exist.security.AbstractRealm;
import org.exist.security.Account;
import org.exist.security.AuthenticationException;
import org.exist.security.Group;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SchemaType;
import org.exist.security.Subject;
import org.exist.security.internal.SecurityManagerImpl;
import org.exist.security.internal.aider.GroupAider;
import org.exist.security.internal.aider.UserAider;
import org.exist.storage.DBBroker;

import com.neurologic.oauth.config.OAuthConfig;
import com.neurologic.oauth.config.ServiceConfig;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
@ConfigurationClass("realm") //TODO: id = OAuth
public class OAuthRealm extends AbstractRealm {

    protected final static Logger LOG = Logger.getLogger(OAuthRealm.class);
    private final static String OAUTH = "OAuth";
    
    protected static OAuthRealm _ = null;
    
    @ConfigurationFieldAsAttribute("id")
    public final static String ID = "OAuth";

    @ConfigurationFieldAsAttribute("version")
    public final static String version = "1.0";
    
    private Group primaryGroup = null;

    public OAuthRealm(final SecurityManagerImpl sm, Configuration config) throws ConfigurationException {
        super(sm, config);
        _ = this;
        
		configuration = Configurator.configure(this, config);
    }

	@Override
	public String getId() {
		return ID;
	}
	
	private synchronized Group getPrimaryGroup() throws PermissionDeniedException {
		if (primaryGroup == null) {
			primaryGroup = getGroup(OAUTH);
			if (primaryGroup == null)
				try {
					primaryGroup = executeAsSystemUser(new Unit<Group>() {
						@Override
						public Group execute(DBBroker broker) throws EXistException, PermissionDeniedException {
							return addGroup(new GroupAider(ID, OAUTH));
						}
					});
		            
					if (primaryGroup == null)
						throw new ConfigurationException("OAuth realm can not create primary group 'OAuth'.");
					
				} catch (PermissionDeniedException e) {
					throw e;
				} catch (ConfigurationException e) {
					throw new PermissionDeniedException(e);
				} catch (EXistException e) {
					throw new PermissionDeniedException(e);
				}
		}
		return primaryGroup;
	}

	@Override
	public List<String> findUsernamesWhereNameStarts(Subject invokingUser, String startsWith) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<String> findUsernamesWhereUsernameStarts(Subject invokingUser, String startsWith) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<String> findAllGroupNames(Subject invokingUser) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<String> findAllGroupMembers(Subject invokingUser, String groupName) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<? extends String> findGroupnamesWhereGroupnameStarts(Subject invokingUser, String startsWith) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Subject authenticate(String accountName, Object credentials) throws AuthenticationException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean deleteAccount(Account account) throws PermissionDeniedException, EXistException, ConfigurationException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean deleteGroup(Group group) throws PermissionDeniedException, EXistException, ConfigurationException {
		// TODO Auto-generated method stub
		return false;
	}

	//OAuth servlet's methods
	
	/*
	 * <server name="twitter" oauth-version="1">
	 *  <consumer key="TWITTER_KEY" secret="TWITTER_SECRET" />
	 *  <provider 
	 *    requestTokenUrl="https://api.twitter.com/oauth/request_token" 
	 *    authorizationUrl="https://api.twitter.com/oauth/authorize" 
	 *    accessTokenUrl="https://api.twitter.com/oauth/access_token" />
	 * 
	 * </server>
	 * 
	 * <server name="facebook" oauth-version="2">
	 *  <consumer key="APP_ID" secret="APP_SECRET" />
	 *  <provider 
	 *    authorizationUrl="https://graph.facebook.com/oauth/authorize" 
	 *    accessTokenUrl="https://graph.facebook.com/oauth/access_token" />
	 * </server>
	 */
	@ConfigurationFieldAsElement("server")
	@NewClass(
		name = "com.neurologic.oauth.config.OAuthConfig",
		mapper = "org/exist/security/realm/oauth/OAuthConfig.xml")
	private List<OAuthConfig> oauthConfigList = new ArrayList<OAuthConfig>();
	
	/*
	 * <service 
	 *   path="/request_token_ready" 
	 *   class="org.exist.security.realm.oauth.TwitterOAuthService" 
	 *   server="twitter">
	 *  
	 *  <success path="/start.htm" />
	 * 
	 * </service>
	 * 
	 * <service path="/oauth_redirect" class="com.neurologic.example.FacebookOAuthService" server="facebook">
	 *  <success path="/start.htm" />
	 * </service>
	 */
	@ConfigurationFieldAsElement("service")
	@NewClass(
		name = "com.neurologic.oauth.config.ServiceConfig", 
		mapper = "org/exist/security/realm/oauth/ServiceConfig.xml")
    private List<ServiceConfig> serviceConfigList = new ArrayList<ServiceConfig>();

	public ServiceConfig getServiceConfigByPath(String path) throws Exception {
        for (ServiceConfig service : serviceConfigList) {
            if (path.equals(service.getPath())) {
                return service;
            }
        }
    
		throw new Exception("No <service> defined for path='" + path + "'.");
    }

	public OAuthConfig getOAuthConfigByName(String oauthName) throws Exception {
        for (OAuthConfig oauth : oauthConfigList) {
            if (oauthName.equals(oauth.getName())) {
            	return oauth;
            }
        }
    
        throw new Exception("No <server> defined with name='" + oauthName + "'.");
    }
	
    protected Account createAccountInDatabase(final String username, final Map<SchemaType, String> metadata) throws AuthenticationException {

        try {
            return executeAsSystemUser(new Unit<Account>(){
                @Override
                public Account execute(DBBroker broker) throws EXistException, PermissionDeniedException {
                    //create the user account
                    UserAider userAider = new UserAider(ID, username, getPrimaryGroup());

                    //store any requested metadata
                    for(Entry<SchemaType, String> entry : metadata.entrySet())
                        userAider.setMetadataValue(entry.getKey(), entry.getValue());

                    Account account = getSecurityManager().addAccount(userAider);

                    return account;
                }
            });
        } catch(Exception e) {
            throw new AuthenticationException(AuthenticationException.UNNOWN_EXCEPTION, e.getMessage(), e);
        }
    }

}
