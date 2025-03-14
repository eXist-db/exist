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
package org.exist.backup;

import org.exist.xmldb.AbstractRestoreServiceTaskListener;

import javax.annotation.Nullable;
import javax.swing.*;

/**
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 */
public class GuiRestoreServiceTaskListener extends AbstractRestoreServiceTaskListener {
    private final RestoreDialog dialog;
    private StringBuilder allProblems = null;

    public GuiRestoreServiceTaskListener() {
        this(null);
    }

    public GuiRestoreServiceTaskListener(@Nullable final JFrame parent) {
        this.dialog = new RestoreDialog(parent, "Restoring data ...", false);
        this.dialog.setVisible(true);
    }

    @Override
    public void startedZipForTransfer(final long totalUncompressedSize) {
        dialog.setTotalRestoreUncompressedSize(totalUncompressedSize);
        super.startedZipForTransfer(totalUncompressedSize);
    }

    @Override
    public void startedTransfer(final long transferSize) {
        dialog.setTotalTransferSize(transferSize);
        super.startedTransfer(transferSize);
    }

    @Override
    public void started(final long numberOfFiles) {
        dialog.setTotalNumberOfFiles(numberOfFiles);
        super.started(numberOfFiles);
    }

    @Override
    public void info(final String message) {
        SwingUtilities.invokeLater(() -> dialog.displayMessage(message));
    }

    @Override
    public void warn(final String message) {
        SwingUtilities.invokeLater(() -> dialog.displayMessage(message));
        addProblem(true, message);
    }

    @Override
    public void error(final String message) {
        SwingUtilities.invokeLater(() -> dialog.displayMessage(message));
        addProblem(false, message);
    }

    @Override
    public void createdCollection(final String collection) {
        SwingUtilities.invokeLater(() -> dialog.setCollection(collection));
        super.createdCollection(collection);
    }

    @Override
    public void addedFileToZipForTransfer(final long uncompressedSize) {
        SwingUtilities.invokeLater(() -> dialog.addedFileToZip(uncompressedSize));
        super.addedFileToZipForTransfer(uncompressedSize);
    }

    @Override
    public void transferred(final long chunkSize) {
        SwingUtilities.invokeLater(() -> dialog.transferred(chunkSize));
        super.transferred(chunkSize);
    }

    @Override
    public void restoredResource(final String resource) {
        SwingUtilities.invokeLater(() -> dialog.setResource(resource));
        SwingUtilities.invokeLater(dialog::incrementFileCounter);
        super.restoredResource(resource);
    }

    @Override
    public void skipResources(final String message, final long count) {
        SwingUtilities.invokeLater(() -> dialog.incrementFileCounter(count));
        super.skipResources(message, count);
    }

    @Override
    public void processingDescriptor(final String backupDescriptor) {
        SwingUtilities.invokeLater(() -> dialog.setBackup(backupDescriptor));
        super.processingDescriptor(backupDescriptor);
    }

    public void enableDismissDialogButton() {
        dialog.dismissButton.setEnabled(true);
        dialog.dismissButton.grabFocus();
    }

    public void hideDialog() {
        dialog.setVisible(false);
    }

    public boolean hasProblems() {
        return allProblems != null && !allProblems.isEmpty();
    }

    public String getAllProblems() {
        return allProblems.toString();
    }

    private void addProblem(final boolean warning, final String message) {
        final String sep = System.getProperty("line.separator");
        if (allProblems == null) {
            allProblems = new StringBuilder();
            allProblems.append("------------------------------------").append(sep);
            allProblems.append("Problems occurred found during restore:").append(sep);
        }

        if (warning) {
            allProblems.append("WARN: ");
        } else {
            allProblems.append("ERROR: ");
        }
        allProblems.append(message);
        allProblems.append(sep);
    }
}
