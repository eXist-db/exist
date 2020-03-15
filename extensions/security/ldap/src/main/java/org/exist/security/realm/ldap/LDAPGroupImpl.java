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
package org.exist.security.realm.ldap;

import org.exist.config.Configuration;
import org.exist.config.ConfigurationException;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.security.AbstractGroup;
import org.exist.security.AbstractRealm;
import org.exist.storage.DBBroker;

/**
 * @author aretter
 */
@ConfigurationClass("group")
public class LDAPGroupImpl extends AbstractGroup {

    public LDAPGroupImpl(final AbstractRealm realm, final Configuration configuration) throws ConfigurationException {
        super(realm, configuration);
    }

    public LDAPGroupImpl(final DBBroker broker, final AbstractRealm realm, final int id, final String name) throws ConfigurationException {
        super(broker, realm, id, name, null);
    }

    LDAPGroupImpl(final AbstractRealm realm, final Configuration config, final boolean removed) throws ConfigurationException {
        this(realm, config);
        this.removed = removed;
    }

    LDAPGroupImpl(final DBBroker broker, final AbstractRealm realm, final String name) throws ConfigurationException {
        super(broker, realm, name);
    }
}