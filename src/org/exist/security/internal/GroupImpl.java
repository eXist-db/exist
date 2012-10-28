/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2003-2011 The eXist Project
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

import java.util.List;
import org.exist.config.Configuration;
import org.exist.config.ConfigurationException;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.security.AbstractGroup;
import org.exist.security.AbstractRealm;
import org.exist.security.Account;

@ConfigurationClass("group")
public class GroupImpl extends AbstractGroup {

    public GroupImpl(AbstractRealm realm, Configuration configuration) throws ConfigurationException {
        super(realm, configuration);
    }

    GroupImpl(AbstractRealm realm, Configuration configuration, boolean removed) throws ConfigurationException {
        super(realm, configuration);
        this.removed = removed;
    }

    public GroupImpl(AbstractRealm realm, int id, String name) throws ConfigurationException {
        this(realm, id, name, null);
    }

    public GroupImpl(AbstractRealm realm, int id, String name, List<Account> managers) throws ConfigurationException {
        super(realm, id, name, managers);
    }

    GroupImpl(AbstractRealm realm, String name) throws ConfigurationException {
        super(realm, name);
    }

    
}