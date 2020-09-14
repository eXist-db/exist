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
package org.exist.security.utils;

import java.io.IOException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Utils {

    public static Collection createCollection(DBBroker broker, Txn txn, XmldbURI uri) throws PermissionDeniedException, IOException, LockException, TriggerException {

        final Collection collection = broker.getOrCreateCollection(txn, uri);

        if(collection == null) {
            throw new IOException("Collection " + uri + " cannot be created.");
        }

        collection.setPermissions(broker, Permission.DEFAULT_SYSTEM_SECURITY_COLLECTION_PERM);
        broker.saveCollection(txn, collection);

        return collection;
    }
    
    public static Collection getOrCreateCollection(DBBroker broker, Txn txn, XmldbURI collectionUri) throws PermissionDeniedException, IOException, LockException, TriggerException {
        Collection col = broker.getCollection(collectionUri);
        if(col == null) {
            col = createCollection(broker, txn, collectionUri);
        }
        return col;
    }
}
