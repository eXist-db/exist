/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2003-2014 The eXist-db Project
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
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 */
package org.exist.backup;

import org.exist.client.Messages;
import org.exist.client.MimeTypeFileFilter;
import org.exist.security.PermissionDeniedException;
import org.exist.xmldb.XmldbURI;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Vector;


public class CreateBackupDialog extends JPanel {
    private static final long serialVersionUID = 4571248257313559856L;

    JComboBox collections;
    JTextField backupTarget;
    JCheckBox deduplicateBlobs;

    final String uri;
    final String user;
    final String passwd;
    Path backupDir;
    final String defaultSelectedCollection;

    public CreateBackupDialog(final String uri, final String user, final String passwd, final Path backupDir) throws HeadlessException {
        this(uri, user, passwd, backupDir, null);
    }

    public CreateBackupDialog(final String uri, final String user, final String passwd, final Path backupDir, final String defaultSelectedCollection) throws HeadlessException {
        super(false);

        this.uri = uri;
        this.user = user;
        this.passwd = passwd;
        this.backupDir = backupDir;
        this.defaultSelectedCollection = defaultSelectedCollection;

        setupComponents();
        setSize(new Dimension(350, 200));
    }

    private void setupComponents() {
        final GridBagLayout grid = new GridBagLayout();
        setLayout(grid);
        final GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 5, 5, 5);


        JLabel label = new JLabel(Messages.getString("CreateBackupDialog.1"));
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.NONE;
        grid.setConstraints(label, c);
        add(label);

        final Vector<String> v = getAllCollections();
        Collections.sort(v);
        collections = new JComboBox(v);
        c.gridx = 1;
        c.gridy = 0;
        c.gridwidth = 2;
        c.anchor = GridBagConstraints.EAST;
        c.fill = GridBagConstraints.HORIZONTAL;
        grid.setConstraints(collections, c);
        add(collections);

        if (defaultSelectedCollection != null) {
            for (int i = 0; i < collections.getItemCount(); i++) {
                final Object col = collections.getItemAt(i);
                if (col.toString().equals(defaultSelectedCollection)) {
                    collections.setSelectedIndex(i);
                    break;
                }
            }
        }

        label = new JLabel(Messages.getString("CreateBackupDialog.2"));
        c.gridx = 0;
        c.gridy = 1;
        c.gridwidth = 1;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.NONE;
        grid.setConstraints(label, c);
        add(label);

        backupTarget = new JTextField(backupDir.resolve("eXist-backup.zip").toAbsolutePath().toString(), 40);
        c.gridx = 1;
        c.gridy = 1;
        c.anchor = GridBagConstraints.EAST;
        c.fill = GridBagConstraints.HORIZONTAL;
        grid.setConstraints(backupTarget, c);
        add(backupTarget);


        deduplicateBlobs = new JCheckBox("Deduplicate BLOBs");
        c.gridx = 1;
        c.gridy = 2;
        c.anchor = GridBagConstraints.EAST;
        c.fill = GridBagConstraints.HORIZONTAL;
        grid.setConstraints(deduplicateBlobs, c);
        add(deduplicateBlobs);


        final JButton select = new JButton(Messages.getString("CreateBackupDialog.3"));
        select.addActionListener(e -> actionSelect());
        c.gridx = 2;
        c.gridy = 1;
        c.anchor = GridBagConstraints.EAST;
        c.fill = GridBagConstraints.NONE;
        grid.setConstraints(select, c);

        select.setToolTipText(Messages.getString("CreateBackupDialog.4"));
        add(select);
    }


    private void actionSelect() {
        final JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(false);
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        chooser.addChoosableFileFilter(new MimeTypeFileFilter("application/zip"));
        chooser.setSelectedFile(Paths.get("eXist-backup.zip").toFile());
        chooser.setCurrentDirectory(backupDir.toFile());

        if (chooser.showDialog(this, Messages.getString("CreateBackupDialog.5")) == JFileChooser.APPROVE_OPTION) {
            backupTarget.setText(chooser.getSelectedFile().getAbsolutePath());
            backupDir = chooser.getCurrentDirectory().toPath();
        }
    }


    private Vector<String> getAllCollections() {
        final Vector<String> list = new Vector<>();

        try {
            final Collection root = DatabaseManager.getCollection(uri + XmldbURI.ROOT_COLLECTION, user, passwd);
            getAllCollections(root, list);
        } catch (final XMLDBException e) {
            e.printStackTrace();
        }
        return list;
    }


    private void getAllCollections(final Collection collection, final Vector<String> collections) throws XMLDBException {
        collections.add(collection.getName());
        final String[] childCollections = collection.listChildCollections();
        Collection child = null;

        for (final String childCollection : childCollections) {
            try {
                child = collection.getChildCollection(childCollection);
            } catch (final XMLDBException xmldbe) {
                if (xmldbe.getCause() instanceof PermissionDeniedException) {
                    continue;
                } else {
                    throw xmldbe;
                }
            } catch (final Exception npe) {
                System.out.println("Corrupted resource/collection skipped: " + child != null ? child.getName() != null ? child.getName() : "unknown" : "unknown");
                continue;
            }
            try {
                getAllCollections(child, collections);
            } catch (final Exception ee) {
                System.out.println("Corrupted resource/collection skipped: " + child != null ? child.getName() != null ? child.getName() : "unknown" : "unknown");
                continue;
            }
        }
    }


    public String getCollection() {
        return (String) collections.getSelectedItem();
    }


    public String getBackupTarget() {
        return backupTarget.getText();
    }


    public Path getBackupDir() {
        return backupDir;
    }


    public boolean getDeduplicateBlobs() {
        return deduplicateBlobs.isSelected();
    }
}
