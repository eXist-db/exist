/*
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This file was originally ported from FusionDB to eXist-db by
 * Evolved Binary, for the benefit of the eXist-db Open Source community.
 * Only the ported code as it appears in this file, at the time that
 * it was contributed to eXist-db, was re-licensed under The GNU
 * Lesser General Public License v2.1 only for use in eXist-db.
 *
 * This license grant applies only to a snapshot of the code as it
 * appeared when ported, it does not offer or infer any rights to either
 * updates of this source code or access to the original source code.
 *
 * The GNU Lesser General Public License v2.1 only license follows.
 *
 * ---------------------------------------------------------------------
 *
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; version 2.1.
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
public class ManagedDocumentLock extends ManagedLock<MultiLock[]> {

    private final XmldbURI documentUri;

    public ManagedDocumentLock(final XmldbURI documentUri, final MultiLock lock, final Runnable closer) {
        super(new MultiLock[] { lock }, closer);
        this.documentUri = documentUri;
    }

    public ManagedDocumentLock(final XmldbURI documentUri, final MultiLock[] locks, final Runnable closer) {
        super(locks, closer);
        this.documentUri = documentUri;
    }

    public XmldbURI getPath() {
        return documentUri;
    }

    public static ManagedDocumentLock notLocked(final XmldbURI documentUri) {
        return new ManagedDocumentLock(documentUri, (MultiLock[])null, () -> {});
    }
}
