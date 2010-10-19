/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010 The eXist Project
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
package org.exist.security.openid;

import java.util.HashMap;
import java.util.Map;

import org.exist.config.ConfigurationException;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.security.Group;
import org.exist.security.PermissionDeniedException;
import org.exist.security.UserAttributes;
import org.exist.security.AbstractAccount;
import org.exist.security.AbstractRealm;
import org.exist.security.Account;
import org.exist.security.internal.GroupImpl;
import org.exist.security.SecurityManager;
import org.exist.xmldb.XmldbURI;
import org.openid4java.discovery.Identifier;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
@ConfigurationClass("account")
public class AccountImpl extends AbstractAccount {

	Identifier  _identifier = null;
	
	public AccountImpl(AbstractRealm realm, Identifier identifier) throws ConfigurationException {
		super(realm, -1, identifier.getIdentifier());
		_identifier = identifier;
	}

	@Override
	public void setPassword(String passwd) {
	}

	@Override
	public String getPassword() {
		return null;
	}

	@Override
	public XmldbURI getHome() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getDigestPassword() {
		return null;
	}

	//TODO: find a place to construct 'full' name
	public String getName_() {
		String name = "";
		if (attributes.containsKey(UserAttributes.FIRTSNAME)) 
			name += attributes.get(UserAttributes.FIRTSNAME); 
		
		if (attributes.containsKey(UserAttributes.LASTNAME)) {
			if (name != "") name += " ";
			name += attributes.get(UserAttributes.LASTNAME);
		}
		
		if (name.equals("")) 
			name += attributes.get(UserAttributes.FULLNAME);
		
		if (name.equals("")) 
			return _identifier.getIdentifier();

		return name;
	}

	private Map<String, Object> attributes = new HashMap<String, Object>();
	
    /**
     * Add a named attribute.
     *
     * @param name
     * @param value
     */
	@Override
	public void setAttribute(String name, Object value) {
		String id = UserAttributes.alias.get(name);
		if (id == null)
			attributes.put(name, value);
		else
			attributes.put(id, value);
	}

    /**
     * Get the named attribute value.
     *
     * @param name The String that is the name of the attribute.
     * @return The value associated with the name or null if no value is associated with the name.
     */
	@Override
	public Object getAttribute(String name) {
		String id = UserAttributes.alias.get(name);
		if (id != null)
			return attributes.get(id);

		if (name.equalsIgnoreCase("id"))
			return _identifier.getIdentifier();
		
		return attributes.get(name);
	}

    @Override
    public Group addGroup(Group group) throws PermissionDeniedException {

        if(group == null){
            return null;
        }

        Account user = getDatabase().getSubject();


        if(!((user != null && user.hasDbaRole()) || ((GroupImpl)group).isMembersManager(user))){
                throw new PermissionDeniedException("not allowed to change group memberships");
        }

        if(!groups.contains(group)) {
            groups.add(group);

            if(SecurityManager.DBA_GROUP.equals(name)) {
                hasDbaRole = true;
            }
        }

        return group;
    }
}