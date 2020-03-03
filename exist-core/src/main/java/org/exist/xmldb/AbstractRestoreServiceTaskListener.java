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

import org.exist.util.FileUtils;

public abstract class AbstractRestoreServiceTaskListener implements RestoreServiceTaskListener {

    @Override
    public void startedZipForTransfer(final long totalUncompressedSize) {
        info("Creating Zip of restore data (uncompressed=" + FileUtils.humanSize(totalUncompressedSize)  + ")...");
    }

    @Override
    public void addedFileToZipForTransfer(final long uncompressedSize) {
        //no-op
    }

    @Override
    public void finishedZipForTransfer() {
        info("Finished creating Zip of restore data.");
    }

    @Override
    public void startedTransfer(final long transferSize) {
        info("Transferring restore data to remote server (size=" + FileUtils.humanSize(transferSize)  + ")...");
    }

    @Override
    public void transferred(final long chunkSize) {
        //no-op
    }

    @Override
    public void finishedTransfer() {
        info("Finished transferring restore data to remote server.");
    }

    @Override
    public void started(final long numberOfFiles) {
        info("Starting restore of backup...");
    }

    @Override
    public void processingDescriptor(final String backupDescriptor) {
        info("Restoring from: " + backupDescriptor);
    }

    @Override
    public void createdCollection(final String collection) {
        info("Creating collection " + collection);
    }

    @Override
    public void restoredResource(final String resource) {
        info("Restored " + resource);
    }

    @Override
    public void finished() {
        info("Finished restore of backup.");
    }
}
