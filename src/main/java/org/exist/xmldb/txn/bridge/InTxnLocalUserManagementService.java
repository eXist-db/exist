/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2016 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exist.xmldb.txn.bridge;

import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.xmldb.LocalCollection;
import org.exist.xmldb.LocalUserManagementService;
import org.exist.xmldb.function.LocalXmldbFunction;
import org.xmldb.api.base.XMLDBException;

/**
 * @author Adam Retter
 */
public class InTxnLocalUserManagementService extends LocalUserManagementService {
    public InTxnLocalUserManagementService(final Subject user, final BrokerPool pool, final LocalCollection collection) {
        super(user, pool, collection);
    }

    @Override
    protected <R> R withDb(final LocalXmldbFunction<R> dbOperation) throws XMLDBException {
        return InTxnLocalCollection.withDb(brokerPool, user, dbOperation);
    }
}
