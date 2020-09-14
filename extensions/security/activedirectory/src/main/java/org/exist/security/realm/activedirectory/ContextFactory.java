/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.security.realm.activedirectory;

import org.exist.config.Configuration;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationFieldAsElement;
import org.exist.security.realm.ldap.LdapContextFactory;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
@ConfigurationClass("context")
public class ContextFactory extends LdapContextFactory {

	@ConfigurationFieldAsElement("domain")
	protected String domain = null;

	@ConfigurationFieldAsElement("searchBase")
	private String searchBase = null;

	protected ContextFactory(Configuration config) {
		super(config);

//		if (domain == null) {
//			//throw error?
//			domain = "";
//		}
//			
//		principalPatternFormat = new MessageFormat("{0}@"+domain);
	}
	
	public String getSearchBase() {
		return searchBase;
	}

	public String getDomain() {
		return domain;
	}

}
