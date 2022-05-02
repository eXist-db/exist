/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2021 The eXist-db Authors
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
package org.exist.security.realm.jwt;

import org.exist.config.Configuration;
import org.exist.config.ConfigurationException;
import org.exist.security.AbstractRealm;
import org.exist.security.Account;
import org.exist.security.internal.GroupImpl;
import org.exist.storage.DBBroker;

import java.util.List;

/**
 * @author <a href="mailto:loren.cahlander@gmail.com">Loren Cahlander</a>
 *
 */
public class JWTGroupImpl extends GroupImpl {
    public JWTGroupImpl(AbstractRealm realm, Configuration configuration) throws ConfigurationException {
        super(realm, configuration);
    }

    public JWTGroupImpl(DBBroker broker, AbstractRealm realm, int id, String name) throws ConfigurationException {
        super(broker, realm, id, name);
    }

    public JWTGroupImpl(DBBroker broker, AbstractRealm realm, int id, String name, List<Account> managers) throws ConfigurationException {
        super(broker, realm, id, name, managers);
    }
}
