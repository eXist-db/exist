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
    public void restoredResource(final String resource) {
        SwingUtilities.invokeLater(() -> dialog.setResource(resource));
        super.restoredResource(resource);
        SwingUtilities.invokeLater(dialog::incrementFileCounter);
    }

    @Override
    public void processingDescriptor(final String backupDescriptor) {
        SwingUtilities.invokeLater(() -> dialog.setBackup(backupDescriptor));
        super.processingDescriptor(backupDescriptor);
    }

    public void hideDialog() {
        dialog.setVisible(false);
    }

    public boolean hasProblems() {
        return allProblems != null && allProblems.length() > 0;
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
