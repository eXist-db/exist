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

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XUpdateQueryService;

import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.ArrayList;

public class RemoteXUpdateQueryService implements XUpdateQueryService {

	private final static Logger LOG = LogManager.getLogger(RemoteXUpdateQueryService.class);

    private RemoteCollection parent;

    public RemoteXUpdateQueryService(final RemoteCollection parent) {
        this.parent = parent;
    }

    @Override
    public String getName() throws XMLDBException {
        return "XUpdateQueryService";
    }

    @Override
    public String getVersion() throws XMLDBException {
        return "1.0";
    }

    @Override
    public long update(final String commands) throws XMLDBException {
        LOG.debug("processing xupdate:\n" + commands);
        final List<Object> params = new ArrayList<>();
        final byte[] xupdateData = commands.getBytes(UTF_8);

        params.add(parent.getPath());
        params.add(xupdateData);
        final int mods = (int) parent.execute("xupdate", params);
        LOG.debug("processed " + mods + " modifications");
        return mods;
    }

    @Override
    public long updateResource(final String id, final String commands) throws XMLDBException {
        LOG.debug("processing xupdate:\n" + commands);
        final List<Object> params = new ArrayList<>();
        final byte[] xupdateData = commands.getBytes(UTF_8);
        //TODO : use dedicated function in XmldbURI
        params.add(parent.getPath() + "/" + id);
        params.add(xupdateData);
        final int mods = (int) parent.execute("xupdateResource", params);
        LOG.debug("processed " + mods + " modifications");
        return mods;
    }


    @Override
    public void setCollection(final Collection collection) throws XMLDBException {
        parent = (RemoteCollection) collection;
    }

    @Override
    public String getProperty(final String name) throws XMLDBException {
        return null;
    }

    @Override
    public void setProperty(final String name, final String value) throws XMLDBException {
    }
}
