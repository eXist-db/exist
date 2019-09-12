/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2019 The eXist Project
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

package org.exist.xmldb;

import org.exist.backup.Restore;
import org.exist.backup.restore.listener.RestoreListener;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.xml.sax.SAXException;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;

import javax.annotation.Nullable;
import java.nio.file.Paths;

public class LocalRestoreService extends AbstractLocalService implements EXistRestoreService {

    public LocalRestoreService(final Subject user, final BrokerPool pool, final LocalCollection parent) {
        super(user, pool, parent);
    }

    @Override
    public String getName() {
        return "RestoreService";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public void restore(final String backup, final @Nullable String newAdminPassword,
            final RestoreServiceTaskListener restoreListener, final boolean overwriteApps) throws XMLDBException {
        final Restore restore = new Restore();
        withDb((broker, transaction) -> {
            try {
                restore.restore(broker, transaction, newAdminPassword, Paths.get(backup),
                        new RestoreListenerAdapter(restoreListener), overwriteApps);
            } catch (final SAXException e) {
                throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage());
            }
            return null;
        });
    }

    @Override
    public String getProperty(final String s) {
        return null;
    }

    @Override
    public void setProperty(final String s, final String s1) {
    }

    private static class RestoreListenerAdapter implements RestoreListener {
        private final RestoreServiceTaskListener restoreListener;

        public RestoreListenerAdapter(final RestoreServiceTaskListener restoreListener) {
            this.restoreListener = restoreListener;
        }

        @Override
        public void started(final long numberOfFiles) {
            restoreListener.started(numberOfFiles);
        }

        @Override
        public void processingDescriptor(final String backupDescriptor) {
            restoreListener.processingDescriptor(backupDescriptor);
        }

        @Override
        public void createdCollection(final String collection) {
            restoreListener.createdCollection(collection);
        }

        @Override
        public void restoredResource(final String resource) {
            restoreListener.restoredResource(resource);
        }

        @Override
        public void info(final String message) {
            restoreListener.info(message);
        }

        @Override
        public void warn(final String message) {
            restoreListener.warn(message);
        }

        @Override
        public void error(final String message) {
            restoreListener.error(message);
        }

        @Override
        public void finished() {
            restoreListener.finished();
        }
    }
}
