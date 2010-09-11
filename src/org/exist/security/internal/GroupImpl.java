/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2003-2010 The eXist Project
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
package org.exist.security.internal;

import org.exist.config.Configuration;
import org.exist.config.ConfigurationException;
import org.exist.config.Configurator;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.security.Group;

@ConfigurationClass("group")
public class GroupImpl extends AbstractPrincipal implements Comparable<Object>, Group {

	public GroupImpl(AbstractRealm realm, int id, String name) throws ConfigurationException {
		super(realm, realm.collectionGroups, id, name);
	}

	@Deprecated //remove after old LDAP security manager remove
	public GroupImpl(String name, int id) throws ConfigurationException {
		super(null, null, id, name);
	}

	public GroupImpl(AbstractRealm realm, Configuration configuration) throws ConfigurationException {
		super(realm, configuration);
		
		//it require, because class's fields initializing after super constructor
		if (this.configuration != null)
			this.configuration = Configurator.configure(this, this.configuration);
	}

	public int compareTo(Object other) {
		if(!(other instanceof GroupImpl))
			throw new IllegalArgumentException("wrong type");
		return name.compareTo(((GroupImpl)other).name);
	}

	public String toString() {
		StringBuffer buf = new StringBuffer();
		buf.append("<group name=\"");
		buf.append(name);
		buf.append("\" id=\"");
		buf.append(Integer.toString(id));
		buf.append("\"/>");
		return buf.toString();
	}
}
