/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2017 The eXist Project
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.storage.lock;

import org.exist.xmldb.XmldbURI;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class ManagedDocumentLock extends ManagedLock<java.util.concurrent.locks.Lock> {

    private final XmldbURI documentUri;

    public ManagedDocumentLock(final XmldbURI documentUri, final java.util.concurrent.locks.Lock lock, final Runnable closer) {
        super(lock, closer);
        this.documentUri = documentUri;
    }

    public XmldbURI getPath() {
        return documentUri;
    }

    public static ManagedDocumentLock notLocked(final XmldbURI documentUri) {
        return new ManagedDocumentLock(documentUri, null, () -> {});
    }
}
