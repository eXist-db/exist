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

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.exist.dom.QName;
import org.exist.util.Occurrences;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;

public class RemoteIndexQueryService extends AbstractRemote implements IndexQueryService {

    public RemoteIndexQueryService(final RemoteCollection parent) {
        super(parent);
    }

    @Override
    public String getName() throws XMLDBException {
        return "IndexQueryService";
    }

    @Override
    public String getVersion() throws XMLDBException {
        return "1.0";
    }

    @Override
    public void reindexCollection() throws XMLDBException {
        reindexCollection(collection.getPath());
    }

    /**
     * @deprecated {@link org.exist.xmldb.IndexQueryService#reindexCollection(org.exist.xmldb.XmldbURI)}
     */
    @Deprecated
    @Override
    public void reindexCollection(final String collectionPath) throws XMLDBException {
        try {
            reindexCollection(XmldbURI.xmldbUriFor(collectionPath));
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e);
        }
    }

    @Override
    public void reindexCollection(final XmldbURI collectionUri) throws XMLDBException {
        final XmldbURI collectionPath = resolve(collectionUri);
        final List<Object> params = new ArrayList<>();
        params.add(collectionPath.toString());
        collection.execute("reindexCollection", params);
    }

    @Override
    public void reindexDocument(final String name) throws XMLDBException {
        final XmldbURI collectionPath = resolve(collection.getPathURI());
        final XmldbURI documentPath = collectionPath.append(name);
        final List<Object> params = new ArrayList<>();
        params.add(documentPath.toString());
        collection.execute("reindexDocument", params);
    }

    @Override
    public Occurrences[] getIndexedElements(final boolean inclusive) throws XMLDBException {
        final List<Object> params = new ArrayList<>();
        params.add(collection.getPath());
        params.add(inclusive);
        final Object[] result = (Object[]) collection.execute("getIndexedElements", params);

        final Stream<Occurrences> occurrences = Arrays.stream(result)
                .map(o -> (Object[]) o)
                .map(row -> new Occurrences(new QName(row[0].toString(), row[1].toString(), row[2].toString()), (Integer) row[3]));
        return occurrences.toArray(Occurrences[]::new);
    }

    @Override
    public void setCollection(final Collection collection) throws XMLDBException {
        this.collection = (RemoteCollection) collection;
    }

    @Override
    public String getProperty(final String name) throws XMLDBException {
        return null;
    }

    @Override
    public void setProperty(final String name, final String value) throws XMLDBException {
    }

    @Override
    public void configureCollection(final String configData) throws XMLDBException {
        final String path = collection.getPath();
        final List<Object> params = new ArrayList<>();
        params.add(path);
        params.add(configData);
        collection.execute("configureCollection", params);
    }
}
