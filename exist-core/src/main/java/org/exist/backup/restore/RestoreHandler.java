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
package org.exist.backup.restore;

import org.exist.backup.BackupDescriptor;
import org.exist.backup.restore.listener.RestoreListener;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;

import javax.annotation.Nullable;
import java.util.*;

/**
 * SAX Content Handler that can act upon
 * and process Backup Descriptors to
 * restore the contents of the backup
 * into the database.
 */
public class RestoreHandler extends AbstractRestoreHandler {

    /**
     * @param broker the database broker
     * @param transaction the transaction to use for the entire restore,
     *                    or null if restoring each collection/resource
     *                    should occur in its own transaction
     * @param descriptor the backup descriptor to start restoring from
     * @param listener the listener to report restore events to
     * @param pathsToIgnore database paths to ignore in the backup
     */
    public RestoreHandler(final DBBroker broker, @Nullable final Txn transaction, final BackupDescriptor descriptor,
            final RestoreListener listener, final Set<String> pathsToIgnore) {
        super(broker, transaction, descriptor, listener, pathsToIgnore);
    }

    @Override
    protected AbstractRestoreHandler newSelf(final DBBroker broker, final @Nullable Txn transaction,
            final BackupDescriptor descriptor, final RestoreListener listener,
            @Nullable final Set<String> pathsToIgnore) {
        return new RestoreHandler(broker, transaction, descriptor, listener, pathsToIgnore);
    }
}
