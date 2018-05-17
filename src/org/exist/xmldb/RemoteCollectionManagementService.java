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

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.XmlRpcException;
import org.w3c.dom.Document;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;


public class RemoteCollectionManagementService extends AbstractRemote implements EXistCollectionManagementService {

    private final XmlRpcClient client;

    public RemoteCollectionManagementService(final XmlRpcClient client, final RemoteCollection parent) {
        super(parent);
        this.client = client;
    }

    @Override
    public String getName() throws XMLDBException {
        return "CollectionManagementService";
    }

    @Override
    public String getVersion() throws XMLDBException {
        return "1.0";
    }

    /**
     * @deprecated {@link org.exist.xmldb.RemoteCollectionManagementService#createCollection(org.exist.xmldb.XmldbURI)}
     */
    @Deprecated
    @Override
    public Collection createCollection(final String collName) throws XMLDBException {
        return createCollection(collName, (Date) null);
    }

    @Override
    public Collection createCollection(final XmldbURI collName) throws XMLDBException {
        return createCollection(collName, null);
    }

    /**
     * @deprecated {@link org.exist.xmldb.RemoteCollectionManagementService#createCollection(org.exist.xmldb.XmldbURI, java.util.Date)}
     */
    @Deprecated
    @Override
    public Collection createCollection(final String collName, final Date created) throws XMLDBException {
        try {
            return createCollection(XmldbURI.xmldbUriFor(collName), created);
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e);
        }
    }

    @Override
    public Collection createCollection(final XmldbURI name, final Date created) throws XMLDBException {
        final XmldbURI collName = resolve(name);
        final List<Object> params = new ArrayList<>();
        params.add(collName.toString());
        if (created != null) {
            params.add(created);
        }

        try {
            client.execute("createCollection", params);
        } catch (final XmlRpcException xre) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, xre.getMessage(), xre);
        }

        return collection.getChildCollection(collName);
    }

    /**
     * Implements createCollection from interface CollectionManager. Gets
     * called by some applications based on Xindice.
     *
     * @param path          Description of the Parameter
     * @param configuration Description of the Parameter
     * @return Description of the Return Value
     * @throws XMLDBException Description of the Exception
     * @deprecated {@link org.exist.xmldb.RemoteCollectionManagementService#createCollection(org.exist.xmldb.XmldbURI)}
     */
    @Deprecated
    public Collection createCollection(final String path, final Document configuration)
            throws XMLDBException {
        return createCollection(path);
    }

    @Override
    public String getProperty(final String property) {
        return null;
    }


    /**
     * @deprecated {@link org.exist.xmldb.RemoteCollectionManagementService#removeCollection(org.exist.xmldb.XmldbURI)}
     */
    @Deprecated
    @Override
    public void removeCollection(final String collName) throws XMLDBException {
        try {
            removeCollection(XmldbURI.xmldbUriFor(collName));
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e);
        }
    }

    @Override
    public void removeCollection(final XmldbURI name) throws XMLDBException {
        final XmldbURI collName = resolve(name);
        final List<Object> params = new ArrayList<>();
        params.add(collName.toString());
        try {
            client.execute("removeCollection", params);
        } catch (final XmlRpcException xre) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, xre.getMessage(), xre);
        }
    }

    @Override
    public void setCollection(final Collection parent) throws XMLDBException {
        this.collection = (RemoteCollection) parent;
    }

    @Override
    public void setProperty(final String name, final String value) {
    }

    /**
     * @deprecated {@link org.exist.xmldb.RemoteCollectionManagementService#move(org.exist.xmldb.XmldbURI, org.exist.xmldb.XmldbURI, org.exist.xmldb.XmldbURI)}
     */
    @Deprecated
    @Override
    public void move(final String collectionPath, final String destinationPath, final String newName) throws XMLDBException {
        try {
            move(XmldbURI.xmldbUriFor(collectionPath), XmldbURI.xmldbUriFor(destinationPath), XmldbURI.xmldbUriFor(newName));
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e);
        }
    }

    @Deprecated
    @Override
    public void move(final XmldbURI src, final XmldbURI dest, final XmldbURI name) throws XMLDBException {
        final XmldbURI srcPath = resolve(src);
        final XmldbURI destPath = dest == null ? srcPath.removeLastSegment() : resolve(dest);
        final XmldbURI newName;
        if (name == null) {
            newName = srcPath.lastSegment();
        } else {
            newName = name;
        }
        final List<Object> params = new ArrayList<>();
        params.add(srcPath.toString());
        params.add(destPath.toString());
        params.add(newName.toString());
        try {
            client.execute("moveCollection", params);
        } catch (final XmlRpcException xre) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, xre.getMessage(), xre);
        }
    }

    /**
     * @deprecated {@link org.exist.xmldb.RemoteCollectionManagementService#moveResource(org.exist.xmldb.XmldbURI, org.exist.xmldb.XmldbURI, org.exist.xmldb.XmldbURI)}
     */
    @Deprecated
    @Override
    public void moveResource(final String resourcePath, final String destinationPath,
                             final String newName) throws XMLDBException {
        try {
            moveResource(XmldbURI.xmldbUriFor(resourcePath), XmldbURI.xmldbUriFor(destinationPath), XmldbURI.xmldbUriFor(newName));
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e);
        }
    }

    @Override
    public void moveResource(final XmldbURI src, final XmldbURI dest, final XmldbURI name)
            throws XMLDBException {
        final XmldbURI srcPath = resolve(src);
        final XmldbURI destPath = dest == null ? srcPath.removeLastSegment() : resolve(dest);
        final XmldbURI newName;
        if (name == null) {
            newName = srcPath.lastSegment();
        } else {
            newName = name;
        }

        final List<Object> params = new ArrayList<>();
        params.add(srcPath.toString());
        params.add(destPath.toString());
        params.add(newName.toString());
        try {
            client.execute("moveResource", params);
        } catch (final XmlRpcException xre) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR,
                    xre.getMessage(),
                    xre);
        }
    }

    /**
     * @deprecated {@link org.exist.xmldb.RemoteCollectionManagementService#copy(org.exist.xmldb.XmldbURI, org.exist.xmldb.XmldbURI, org.exist.xmldb.XmldbURI)}
     */
    @Deprecated
    @Override
    public void copy(final String collectionPath, final String destinationPath,
                     final String newName) throws XMLDBException {
        try {
            copy(XmldbURI.xmldbUriFor(collectionPath), XmldbURI.xmldbUriFor(destinationPath), XmldbURI.xmldbUriFor(newName), "DEFAULT");
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e);
        }
    }

    @Override
    public void copy(final XmldbURI src, final XmldbURI dest, final XmldbURI name) throws XMLDBException {
        copy(src, dest, name, "DEFAULT");
    }

    @Override
    public void copy(final XmldbURI src, final XmldbURI dest, final XmldbURI name, final String preserveType) throws XMLDBException {
        final XmldbURI srcPath = resolve(src);
        final XmldbURI destPath = dest == null ? srcPath.removeLastSegment() : resolve(dest);
        final XmldbURI newName;
        if (name == null) {
            newName = srcPath.lastSegment();
        } else {
            newName = name;
        }

        final List<Object> params = new ArrayList<>();
        params.add(srcPath.toString());
        params.add(destPath.toString());
        params.add(newName.toString());
        params.add(preserveType);
        try {
            client.execute("copyCollection", params);
        } catch (final XmlRpcException xre) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR,
                    xre.getMessage(),
                    xre);
        }
    }

    /**
     * @deprecated {@link org.exist.xmldb.RemoteCollectionManagementService#copy(org.exist.xmldb.XmldbURI, org.exist.xmldb.XmldbURI, org.exist.xmldb.XmldbURI)}
     */
    @Deprecated
    @Override
    public void copyResource(final String resourcePath, final String destinationPath,
            final String newName) throws XMLDBException {
        try {
            copyResource(XmldbURI.xmldbUriFor(resourcePath), XmldbURI.xmldbUriFor(destinationPath), XmldbURI.xmldbUriFor(newName), "DEFAULT");
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e);
        }
    }

    @Override
    public void copyResource(final XmldbURI src, final XmldbURI dest, final XmldbURI name)
            throws XMLDBException {
        copyResource(src, dest, name, "DEFAULT");
    }

    @Override
    public void copyResource(final XmldbURI src, final XmldbURI dest, final XmldbURI name, final String preserveType)
            throws XMLDBException {
        final XmldbURI srcPath = resolve(src);
        final XmldbURI destPath = dest == null ? srcPath.removeLastSegment() : resolve(dest);
        final XmldbURI newName;
        if (name == null) {
            newName = srcPath.lastSegment();
        } else {
            newName = name;
        }
        final List<Object> params = new ArrayList<>();
        params.add(srcPath.toString());
        params.add(destPath.toString());
        params.add(newName.toString());
        params.add(preserveType);
        try {
            client.execute("copyResource", params);
        } catch (final XmlRpcException xre) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR,
                    xre.getMessage(),
                    xre);
        }
    }

    @Override
    public void runCommand(final String[] cmdParams) throws XMLDBException {
        final List<Object> params = new ArrayList<>();
        params.add(collection.getPathURI());
        params.add(cmdParams);
        try {
            client.execute("runCommand", params);
        } catch (final XmlRpcException xre) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR,
                    xre.getMessage(),
                    xre);
        }
    }
}

