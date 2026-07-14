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
package org.exist.storage;

import org.exist.dom.persistent.LockedDocument;
import org.exist.security.Permission;

/**
 * A resource which the current subject is allowed to EXECUTE, as resolved by
 * {@link DBBroker#getResourceForExecution(org.exist.xmldb.XmldbURI, org.exist.storage.lock.Lock.LockMode)}.
 *
 * Holding this handle means the subject has {@link Permission#EXECUTE} on the document;
 * it does not imply {@link Permission#READ}. {@link #callerCanRead()} reports whether the
 * subject may also read the source, which callers use to decide how much of a failure they
 * may disclose (see {@link org.exist.xquery.ErrorDisclosure}).
 *
 * @param document the locked document
 * @param callerCanRead whether the subject which resolved this resource also holds READ on it
 */
public record ExecutableResource(LockedDocument document, boolean callerCanRead) implements AutoCloseable {

    /**
     * Releases the lock held on the document.
     */
    @Override
    public void close() {
        document.close();
    }
}
