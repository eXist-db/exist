/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * $Id$
 */
package org.exist.versioning;

import org.exist.collections.Collection;
import org.exist.dom.DocumentImpl;
import org.exist.xmldb.XmldbURI;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.util.LockException;

import java.util.Iterator;

public class VersioningHelper {

    public static DocumentImpl getBaseRevision(DBBroker broker, XmldbURI docPath) throws LockException {
        String docName = docPath.lastSegment().toString();
        XmldbURI collectionPath = docPath.removeLastSegment();
        XmldbURI path = VersioningTrigger.VERSIONS_COLLECTION.append(collectionPath);
        Collection vCollection = broker.openCollection(path, Lock.READ_LOCK);
        try {
            for (Iterator i = vCollection.iterator(broker); i.hasNext(); ) {
                DocumentImpl doc = (DocumentImpl) i.next();
                String fname = doc.getFileURI().toString();
                int p = fname.lastIndexOf('.');
                if (p > -1) {
                    String name = fname.substring(0, p);
                    if (name.equals(docName)) {
                        doc.getUpdateLock().acquire(Lock.READ_LOCK);
                        return doc;
                    }
                }
            }
        } finally {
            vCollection.release(Lock.READ_LOCK);
        }
        return null;
    }
}
