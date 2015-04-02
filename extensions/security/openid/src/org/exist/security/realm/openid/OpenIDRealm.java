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

import org.exist.EXistException;
import org.exist.config.Configuration;
import org.exist.config.ConfigurationException;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationFieldAsAttribute;
import org.exist.security.AbstractRealm;
import org.exist.security.Account;
import org.exist.security.AuthenticationException;
import org.exist.security.Group;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.security.internal.SecurityManagerImpl;

/**
 * OpenID realm.
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
@ConfigurationClass("realm") //TODO: id = OpenID
public class OpenIDRealm extends AbstractRealm {

    public static OpenIDRealm instance = null;
    
    @ConfigurationFieldAsAttribute("id")
    public static String ID = "OpenID";

    @ConfigurationFieldAsAttribute("version")
    public final static String version = "1.0";
    
    public OpenIDRealm(SecurityManagerImpl sm, Configuration config) {
		super(sm, config);
		
		instance = this;
	}

	@Override
	public String getId() {
		return ID;
	}

	@Override
	public Subject authenticate(final String accountName, Object credentials) throws AuthenticationException {
		return null;
	}

	@Override
	public boolean deleteAccount(Account account) throws PermissionDeniedException, EXistException, ConfigurationException {
		return false;
	}

	@Override
	public boolean updateGroup(Group group) throws PermissionDeniedException, EXistException, ConfigurationException {
		return false;
	}

	@Override
	public boolean deleteGroup(Group group) throws PermissionDeniedException, EXistException, ConfigurationException {
		return false;
	}
}
