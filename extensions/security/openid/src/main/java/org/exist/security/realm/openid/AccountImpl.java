/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010-2011 The eXist Project
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

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;

import org.exist.config.ConfigurationException;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.security.AbstractAccount;
import org.exist.security.internal.SubjectAccreditedImpl;
import org.openid4java.discovery.Identifier;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
@ConfigurationClass("account")
public class AccountImpl extends SubjectAccreditedImpl {

	Identifier _identifier = null;
	
	protected static String escape(String id) throws ConfigurationException {
		URI uri;
		try {
			uri = new URI( id );
		} catch (URISyntaxException e) {
			throw new ConfigurationException(e);
		}
		if ("www.google.com".equals(uri.getAuthority()))
			return uri.getQuery().replace("id=", "") + "@google.com";
		
//		return uri.getAuthority();
		String tmp = id.replace("https://", "/");
		tmp = tmp.replace("http://", "/");
		if (tmp.endsWith("/"))
			tmp.subSequence(0, tmp.length()-1);
		
		try {
			return java.net.URLEncoder.encode(tmp,"UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new ConfigurationException(e);
		}
	}

	public AccountImpl(AbstractAccount account, Identifier identifier) throws ConfigurationException {
		super(account, identifier);
		_identifier = identifier;
	}

//	@Override
//	public void setPassword(String passwd) {
//	}
//
//	@Override
//	public String getPassword() {
//		return null;
//	}
//
//	@Override
//	public XmldbURI getHome() {
//		// TODO Auto-generated method stub
//		return null;
//	}
//
//	@Override
//	public String getDigestPassword() {
//		return null;
//	}
//
//	//TODO: find a place to construct 'full' name
//	public String getName_() {
//            String name = "";
//
//            Set<AXSchemaType> metadataKeys = getMetadataKeys();
//
//            if(metadataKeys.contains(AXSchemaType.FIRSTNAME)) {
//                name += getMetadataValue(AXSchemaType.FIRSTNAME);
//            }
//
//            if(metadataKeys.contains(AXSchemaType.LASTNAME)) {
//                if(name.length() > 0 ) {
//                    name += " ";
//                }
//                name += getMetadataValue(AXSchemaType.LASTNAME);
//            }
//
//            if(name.length() == 0) {
//                name += getMetadataValue(AXSchemaType.FULLNAME);
//            }
//
//            if(name.length() == 0) {
//                name = _identifier.getIdentifier();
//            }
//
//            return name;
//	}
//
//    @Override
//    public Group addGroup(Group group) throws PermissionDeniedException {
//
//        if(group == null){
//            return null;
//        }
//
//        Account user = getDatabase().getCurrentSubject();
//
//
//        if(!((user != null && user.hasDbaRole()) || ((GroupImpl)group).isMembersManager(user))){
//                throw new PermissionDeniedException("not allowed to change group memberships");
//        }
//
//        if(!groups.contains(group)) {
//            groups.add(group);
//
//            if(SecurityManager.DBA_GROUP.equals(name)) {
//                hasDbaRole = true;
//            }
//        }
//
//        return group;
//    }
}