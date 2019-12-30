/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2015 The eXist Project
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
package org.exist.xmldb;

import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RemoteDatabaseInstanceManager implements DatabaseInstanceManager {

    private final RemoteCallSite remoteCallSite;

    /**
     * Constructor for DatabaseInstanceManagerImpl.
     *
     * @param remoteCallSite the remote call site
     */
    public RemoteDatabaseInstanceManager(final RemoteCallSite remoteCallSite) {
        this.remoteCallSite = remoteCallSite;
    }

    @Override
    public String getName() throws XMLDBException {
        return "DatabaseInstanceManager";
    }

    @Override
    public String getVersion() throws XMLDBException {
        return "1.0";
    }

    @Override
    public boolean isLocalInstance() {
        return false;
    }

    @Override
    public void shutdown() throws XMLDBException {
        remoteCallSite.execute("shutdown", Collections.EMPTY_LIST);
    }

    @Override
    public void shutdown(long delay) throws XMLDBException {
        final List<Object> params = new ArrayList<>();
        if (delay > 0)
            params.add(Long.valueOf(delay).toString());
        remoteCallSite.execute("shutdown", params);
    }

    @Override
    public boolean enterServiceMode() throws XMLDBException {
        remoteCallSite.execute("enterServiceMode", Collections.EMPTY_LIST);
        return true;
    }

    @Override
    public void exitServiceMode() throws XMLDBException {
        remoteCallSite.execute("exitServiceMode", Collections.EMPTY_LIST);
    }

    @Override
    public void setCollection(final Collection collection) throws XMLDBException {
    }

    @Override
    public String getProperty(final String name) throws XMLDBException {
        return null;
    }

    @Override
    public void setProperty(final String name, final String value) throws XMLDBException {
    }

    @Override
    public DatabaseStatus getStatus() throws XMLDBException {
        throw new XMLDBException(ErrorCodes.NOT_IMPLEMENTED, "this method is not available for remote connections");
    }
}
