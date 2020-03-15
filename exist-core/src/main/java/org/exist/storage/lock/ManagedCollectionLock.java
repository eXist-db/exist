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
package org.exist.storage.lock;

import org.exist.xmldb.XmldbURI;
import uk.ac.ic.doc.slurp.multilock.MultiLock;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class ManagedCollectionLock extends ManagedLock<MultiLock[]> {

    private final XmldbURI collectionUri;

    public ManagedCollectionLock(final XmldbURI collectionUri, final MultiLock[] locks, final Runnable closer) {
        super(locks, closer);
        this.collectionUri = collectionUri;
    }

    public XmldbURI getPath() {
        return collectionUri;
    }

    public static ManagedCollectionLock notLocked(final XmldbURI collectionUri) {
        return new ManagedCollectionLock(collectionUri, null, () -> {});
    }
}
