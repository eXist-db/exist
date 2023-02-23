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

import jakarta.annotation.Nullable;
import org.exist.xmldb.XmldbURI;
import uk.ac.ic.doc.slurp.multilock.MultiLock;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class ManagedSingleLockDocumentLock extends ManagedDocumentLock<MultiLock> {

    private final long groupId;
    private final Lock.LockMode lockMode;

    @Nullable private final LockTable lockTable;  // NOTE(AR) only null when called via private constructor from {@link #notLocked(XmldbURI)}.

    public ManagedSingleLockDocumentLock(final XmldbURI documentUri, final long groupId, final MultiLock lock, final Lock.LockMode lockMode, final LockTable lockTable) {
        super(documentUri, lock, null);  // NOTE(AR) we can set the closer as null here, because we override {@link #close()} below!
        this.groupId = groupId;
        this.lockMode = lockMode;
        this.lockTable = lockTable;
    }

    private ManagedSingleLockDocumentLock(final XmldbURI documentUri) {
        this(documentUri, -1, null, Lock.LockMode.NO_LOCK, null);
    }

    @Override
    public void close() {
        if (!closed) {
            if (lock != null) {  // NOTE(AR) only null when constructed from {@link #notLocked(XmldbURI)}.
                LockManager.unlock(lock, lockMode);
                lockTable.released(groupId, documentUri.toString(), Lock.LockType.DOCUMENT, lockMode);
            }
        }
        this.closed = true;
    }

    public static ManagedSingleLockDocumentLock notLocked(final XmldbURI documentUri) {
        return new ManagedSingleLockDocumentLock(documentUri);
    }
}
