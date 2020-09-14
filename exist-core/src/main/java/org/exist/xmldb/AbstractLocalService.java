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
package org.exist.xmldb;

import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.Service;
import org.xmldb.api.base.XMLDBException;

/**
 * Base class for Local XMLDB Services
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public abstract class AbstractLocalService extends AbstractLocal implements Service {

    AbstractLocalService(final Subject user, final BrokerPool brokerPool, final LocalCollection collection) {
        super(user, brokerPool, collection);
    }

    @Override
    public void setCollection(final Collection collection) throws XMLDBException {
        if(!(collection instanceof LocalCollection)) {
            throw new XMLDBException(ErrorCodes.INVALID_COLLECTION, "incompatible collection type: " + collection.getClass().getName());
        }
        this.collection = (LocalCollection) collection;
    }
}
