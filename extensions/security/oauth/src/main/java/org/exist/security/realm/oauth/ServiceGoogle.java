/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2012 The eXist Project
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

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.security.auth.Subject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.eclipse.jetty.security.DefaultIdentityService;
import org.eclipse.jetty.server.Authentication;
import org.eclipse.jetty.server.UserIdentity;
import org.exist.security.AXSchemaType;
import org.exist.security.AbstractAccount;
import org.exist.security.Account;
import org.exist.security.SchemaType;
import org.exist.security.internal.HttpSessionAuthentication;
import org.exist.security.internal.SubjectAccreditedImpl;
import org.json.JSONException;
import org.json.JSONObject;
import org.scribe.exceptions.OAuthException;
import org.scribe.model.OAuthRequest;
import org.scribe.model.Response;
import org.scribe.model.Token;
import org.scribe.model.Verb;
import org.scribe.oauth.OAuthService;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * 
 */
public class ServiceGoogle  {

    private static final String PROTECTED_RESOURCE_URL = "https://www.googleapis.com/oauth2/v1/userinfo";
	public static final String GOOGLE_ACCESS_TOKEN_SESSION = "GOOGLE_ACCESS_TOKEN_SESSION";

	public static void saveAccessToken(HttpServletRequest request, OAuthService service, Token accessToken) throws Exception {
		
        OAuthRequest _request = new OAuthRequest(Verb.GET, PROTECTED_RESOURCE_URL);
        service.signRequest(accessToken, _request);
        Response _response = _request.send();
        if (_response.getCode() != 200)
        	throw new OAuthException("Can query account information.");

        String contentType = _response.getHeader("Content-Type");
		if (contentType == null) contentType = "";
			
		//String charset = "";
		int semicolonPos = contentType.indexOf(';');
			
		if (semicolonPos > 0) {
			String _charset = contentType.substring(semicolonPos + 1).trim();
			if (_charset.startsWith("charset")) {
				//charset = _charset.substring(_charset.indexOf('=') + 1);
			}
			contentType = contentType.substring(0, semicolonPos);
		}
			
		Map<String, String> responseAttributes = null;
		String response = _response.getBody();
		if ("application/json".equals(contentType) || (response.startsWith("{") && response.endsWith("}"))) {
			JSONObject jsonResponse = new JSONObject(response);
			if (jsonResponse != null) {
				if (jsonResponse.has("error")) {
					throw new OAuthException("Error getting access token: " + System.getProperty("line.separator") + jsonResponse.toString());
				}
				
				responseAttributes = parseJSONObject(jsonResponse);
			}
		} else if ("text/plain".equals(contentType) || (response.contains("=") && response.contains("&"))) {
			//responseAttributes = OAuthUtil.parseQueryString(response);
		}
		
		if (responseAttributes == null)
        	throw new OAuthException("Get response, but no account information.");
			
		String id = responseAttributes.get("id");
		
		String accountName = id + "@google.com";

		Account found = OAuthRealm.instance.getAccount(accountName);
		
		if (found == null) {
			Map<SchemaType, String> metadata = new HashMap<SchemaType, String>();
			addMetadata(responseAttributes, metadata, GoogleSchemaType.ID, "id");
			addMetadata(responseAttributes, metadata, AXSchemaType.FIRSTNAME, "given_name");
			addMetadata(responseAttributes, metadata, AXSchemaType.LASTNAME, "family_name");
			addMetadata(responseAttributes, metadata, AXSchemaType.FULLNAME, "name");
			addMetadata(responseAttributes, metadata, AXSchemaType.TIMEZONE, "timezone");

			addMetadata(responseAttributes, metadata, GoogleSchemaType.PICTURE, "picture");
			addMetadata(responseAttributes, metadata, GoogleSchemaType.LOCALE, "locale");
			addMetadata(responseAttributes, metadata, GoogleSchemaType.LINK, "link");
			addMetadata(responseAttributes, metadata, GoogleSchemaType.GENDER, "gender");			
			
			found = OAuthRealm.instance.createAccountInDatabase(accountName, metadata);
		}
		
		Account principal = new SubjectAccreditedImpl((AbstractAccount) found, accessToken);
		
        HttpSession session = request.getSession(true);

		Subject subject = new Subject();

		//TODO: hardcoded to jetty - rewrite
		//*******************************************************
		DefaultIdentityService _identityService = new DefaultIdentityService();
		UserIdentity user = _identityService.newUserIdentity(subject, principal, new String[0]);
        
		Authentication cached=new HttpSessionAuthentication(session, user);
        session.setAttribute(HttpSessionAuthentication.__J_AUTHENTICATED, cached);
		//*******************************************************
			
			
		request.getSession().setAttribute(GOOGLE_ACCESS_TOKEN_SESSION, accessToken);
	}

	private static Map<String, String> parseJSONObject(JSONObject json) throws JSONException {
		Map<String, String> parameters = null;
		
		if (json != null && json.length() > 0) {
			parameters = new LinkedHashMap<String, String>();
			@SuppressWarnings("unchecked")
			Iterator<String> iter = json.keys();
			
			if (iter != null) {
				while (iter.hasNext()) {
					String key = iter.next();
					parameters.put(key, json.getString(key));
				}
			}
		}
		
		return parameters;
	}
	
	private static void addMetadata(Map<String, String> attributes, Map<SchemaType, String> metadata, SchemaType type, String attrName) {
	    String val = attributes.get(attrName);

	    if (val != null) {
	        metadata.put(type, val);
	    }
	}
	
	public enum GoogleSchemaType implements SchemaType {

	    ID("https://www.googleapis.com/oauth2/v1/userinfo", "id"),
	    PICTURE("https://www.googleapis.com/oauth2/v1/userinfo", "picture"),
	    LOCALE("https://www.googleapis.com/oauth2/v1/userinfo", "locale"),
	    LINK("https://www.googleapis.com/oauth2/v1/userinfo", "link"),
	    GENDER("https://www.googleapis.com/oauth2/v1/userinfo", "gender");
	    
	    private final String namespace;
	    private final String alias;

	    GoogleSchemaType(String namespace, String alias) {
	        this.namespace = namespace;
	        this.alias = alias;
	    }

	    public static GoogleSchemaType valueOfNamespace(String namespace) {
	        for(GoogleSchemaType schemaType : GoogleSchemaType.values()) {
	            if(schemaType.getNamespace().equals(namespace)) {
	                return schemaType;
	            }
	        }
	        return null;
	    }

	    public static GoogleSchemaType valueOfAlias(String alias) {
	        for(GoogleSchemaType schemaType : GoogleSchemaType.values()) {
	            if(schemaType.getAlias().equals(alias)) {
	                return schemaType;
	            }
	        }
	        return null;
	    }

	    public String getNamespace() {
	        return namespace;
	    }

	    public String getAlias() {
	        return alias;
	    }
	}
}