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
 *  $Id: AbstractGroup.java 12846 2010-10-01 05:23:29Z shabanovd $
 */

package org.exist.security;

import java.util.HashSet;
import java.util.Set;

import org.exist.config.Configuration;
import org.exist.config.ConfigurationException;
import org.exist.config.Configurator;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationFieldAsElement;
import org.exist.security.internal.GroupImpl;

@ConfigurationClass("")
public abstract class AbstractGroup extends AbstractPrincipal implements Comparable<Object>, Group {

    @ConfigurationFieldAsElement("members-manager")
    private Set<Account> membersManagers = new HashSet<Account>();

    public AbstractGroup(AbstractRealm realm, int id, String name) throws ConfigurationException {
        super(realm, realm.collectionGroups, id, name);
    }

    public AbstractGroup(AbstractRealm realm, String name) throws ConfigurationException {
        super(realm, realm.collectionGroups, -1, name);
    }

    public AbstractGroup(AbstractRealm realm, Configuration configuration) throws ConfigurationException {
        super(realm, configuration);

        //it require, because class's fields initializing after super constructor
        if (this.configuration != null) {
                this.configuration = Configurator.configure(this, this.configuration);
        }
    }

    @Override
    public int compareTo(Object other) {
        if(!(other instanceof GroupImpl)) {
            throw new IllegalArgumentException("wrong type");
        }
        return name.compareTo(((GroupImpl)other).name);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("<group name=\"");
        buf.append(name);
        buf.append("\" id=\"");
        buf.append(Integer.toString(id));
        buf.append("\"/>");
        return buf.toString();
    }

    @Override
    public boolean isMembersManager(Account account) {
        return membersManagers.contains(account);
    }
}