/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2003-2010 The eXist Project
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
 *  $Id$
 */
package org.exist.backup;

import javax.swing.*;
import java.awt.*;


public class BackupDialog extends JDialog {
    private static final long serialVersionUID = -4960002499478536048L;

    JTextField currentCollection;
    JTextField currentFile;
    JProgressBar progress;

    public BackupDialog() throws HeadlessException {
        super();
        setupComponents();
    }


    /**
     * Creates a new BackupDialog object.
     *
     * @param owner Parent frame.
     * @param modal Flag to have modal window.
     * @throws HeadlessException Environment  does not support a keyboard, display, or mouse.
     */
    public BackupDialog(final Frame owner, final boolean modal) throws HeadlessException {
        super(owner, "Backup", modal);
        setupComponents();
        pack();
    }

    private void setupComponents() {
        final GridBagLayout grid = new GridBagLayout();
        getContentPane().setLayout(grid);
        final GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 5, 5, 5);

        JLabel label = new JLabel("Collection:");
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.NONE;
        grid.setConstraints(label, c);
        getContentPane().add(label);

        currentCollection = new JTextField(40);
        currentCollection.setEditable(false);
        c.gridx = 1;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        grid.setConstraints(currentCollection, c);
        getContentPane().add(currentCollection);

        label = new JLabel("Storing file:");
        c.gridx = 0;
        c.gridy = 1;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.NONE;
        grid.setConstraints(label, c);
        getContentPane().add(label);

        currentFile = new JTextField(40);
        currentFile.setEditable(false);
        c.gridx = 1;
        c.gridy = 1;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        grid.setConstraints(currentFile, c);
        getContentPane().add(currentFile);

        label = new JLabel("Collection Progress:");
        c.gridx = 0;
        c.gridy = 2;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.NONE;
        grid.setConstraints(label, c);
        getContentPane().add(label);

        progress = new JProgressBar();
        progress.setIndeterminate(false);
        progress.setStringPainted(true);
        progress.setMinimumSize(new Dimension(200, 30));
        c.gridx = 1;
        c.gridy = 2;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        grid.setConstraints(progress, c);
        getContentPane().add(progress);
    }


    public void setCollection(final String collection) {
        currentCollection.setText(collection);
    }


    public void setResource(final String resource) {
        currentFile.setText(resource);
    }


    public void setResourceCount(final int count) {
        progress.setMaximum(count);
    }


    public void setProgress(final int count) {
        progress.setValue(count);
    }
}
