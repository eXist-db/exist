/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2012 The eXist Project
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
package org.exist.client;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EtchedBorder;
import org.exist.xmldb.EXistResource;
import org.exist.xquery.util.URIUtils;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.BinaryResource;
import org.xmldb.api.modules.XMLResource;

/**
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class NewResourceDialog extends JFrame {
    
    private final static String DEFAULT_FILENAME = "new-resource";
    
    private final static String DEFAULT_MODULE_NS = "http://module1";
    private final static String DEFAULT_MODULE_NS_PREFIX = "mod1";
    
    private final InteractiveClient client;
    
    public NewResourceDialog(final InteractiveClient client) {
        super("New Resource...");
        setupComponents();
        this.client = client;
    }
    
    private enum ResourceType {
        XML_DOCUMENT("XML Document", "xml", "application/xml", "xml-resource.tmpl"),
        XQUERY_MAIN("XQuery Main Module", "xqy", "application/xquery", "xquery-resource.tmpl"),
        XQUERY_LIBRARY("XQuery Library Module", "xqm", "application/xquery", "xquery-lib-resource.tmpl");
        
        private final String label;
        private final String fileExtension;
        private final String mimeType;
        private final String templatePath;
        
        ResourceType(final String label, final String fileExtension, final String mimeType, final String templatePath) {
            this.label = label;
            this.fileExtension = fileExtension;
            this.mimeType = mimeType;
            this.templatePath = templatePath;
        }

        public String getFileExtension() {
            return fileExtension;
        }

        public String getMimeType() {
            return mimeType;
        }

        public String getTemplatePath() {
            return templatePath;
        }

        @Override
        public String toString() {
            return label;
        }
    }
    
    private void setupComponents() {
        
        final JLabel lblLibModule = new JLabel("Library Module");
        final JPanel panLibModule = new JPanel(new BorderLayout());
        
        final GridBagLayout grid = new GridBagLayout();
        getContentPane().setLayout(grid);
        final GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 5, 5, 5);

        final JLabel lblResourceType = new JLabel("Resource Type" + ": ");
        c.gridx = 0;
        c.gridy = 1;
        c.gridwidth = 1;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.NONE;
        grid.setConstraints(lblResourceType, c);
        getContentPane().add(lblResourceType);
        
        //final JComboBox cmbResourceTypes = new JComboBox<ResourceType>(ResourceType.values());
        final JComboBox cmbResourceTypes = new JComboBox(ResourceType.values());
        cmbResourceTypes.setSelectedIndex(0);
        cmbResourceTypes.addActionListener(e -> {
            final Object src = e.getSource();
            if(src.equals(cmbResourceTypes)) {
                final boolean visible1;
                switch((ResourceType)cmbResourceTypes.getSelectedItem()) {
                    case XQUERY_LIBRARY:
                        visible1 = true;
                        break;

                    default:
                        visible1 = false;
                }

                lblLibModule.setVisible(visible1);
                panLibModule.setVisible(visible1);
                pack();
            }
        });
        
        c.gridx = 1;
        c.gridy = 1;
        c.gridwidth = 1;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.NONE;
        grid.setConstraints(cmbResourceTypes, c);
        getContentPane().add(cmbResourceTypes);
        
        final JLabel lblFilename = new JLabel("Filename (excluding extension)" + ": ");
        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 1;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.NONE;
        grid.setConstraints(lblFilename, c);
        getContentPane().add(lblFilename);
        
        final JTextField txtFilename = new JTextField(20);
        c.gridx = 1;
        c.gridy = 2;
        c.gridwidth = 1;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.NONE;
        grid.setConstraints(txtFilename, c);
        getContentPane().add(txtFilename);
        
        //controls for library module parameters
        //lblLibModule
        c.gridx = 0;
        c.gridy = 3;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.NONE;
        grid.setConstraints(lblLibModule, c);
        getContentPane().add(lblLibModule);
        lblLibModule.setVisible(false);
        
        //panLibModule
        panLibModule.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
        final GridBagLayout panGrid = new GridBagLayout();
        panLibModule.setLayout(panGrid);
        final GridBagConstraints cPan = new GridBagConstraints();
        cPan.insets = new Insets(5, 5, 5, 5);
        
        final JLabel lblLibModuleNamespace = new JLabel("Namespace" + ": ");
        cPan.gridx = 0;
        cPan.gridy = 0;
        cPan.gridwidth = 1;
        cPan.anchor = GridBagConstraints.WEST;
        cPan.fill = GridBagConstraints.NONE;
        panGrid.setConstraints(lblLibModuleNamespace, cPan);
        panLibModule.add(lblLibModuleNamespace);
        
        final JTextField txtLibModuleNamespace = new JTextField(DEFAULT_MODULE_NS, 50);
        cPan.gridx = 1;
        cPan.gridy = 0;
        cPan.gridwidth = 1;
        cPan.anchor = GridBagConstraints.WEST;
        cPan.fill = GridBagConstraints.NONE;
        panGrid.setConstraints(txtLibModuleNamespace, cPan);
        panLibModule.add(txtLibModuleNamespace);
        
        final JLabel lblLibModulePrefix = new JLabel("Namespace prefix" + ": ");
        cPan.gridx = 0;
        cPan.gridy = 1;
        cPan.gridwidth = 1;
        cPan.anchor = GridBagConstraints.WEST;
        cPan.fill = GridBagConstraints.NONE;
        panGrid.setConstraints(lblLibModulePrefix, cPan);
        panLibModule.add(lblLibModulePrefix);
        
        final JTextField txtLibModulePrefix = new JTextField(DEFAULT_MODULE_NS_PREFIX, 10);
        cPan.gridx = 1;
        cPan.gridy = 1;
        cPan.gridwidth = 1;
        cPan.anchor = GridBagConstraints.WEST;
        cPan.fill = GridBagConstraints.NONE;
        panGrid.setConstraints(txtLibModulePrefix, cPan);
        panLibModule.add(txtLibModulePrefix);
        
        c.gridx = 0;
        c.gridy = 4;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.NONE;
        grid.setConstraints(panLibModule, c);
        getContentPane().add(panLibModule);
        panLibModule.setVisible(false);
        
        
        final JButton btnCancel = new JButton("Cancel");
        btnCancel.addActionListener(e -> {
            setVisible(false);
            dispose();
        });
        c.gridx = 0;
        c.gridy = 5;
        c.gridwidth = GridBagConstraints.RELATIVE;
        c.anchor = GridBagConstraints.EAST;
        c.fill = GridBagConstraints.NONE;
        grid.setConstraints(btnCancel, c);
        getContentPane().add(btnCancel);
                
        final JButton btnCreate = new JButton("Create Resource");
        btnCreate.addActionListener(e -> {
            createResource((ResourceType)cmbResourceTypes.getSelectedItem(), txtFilename.getText(), txtLibModuleNamespace.getText(), txtLibModulePrefix.getText());

            setVisible(false);
            dispose();
        });
        c.gridx = 1;
        c.gridy = 5;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.anchor = GridBagConstraints.EAST;
        c.fill = GridBagConstraints.NONE;
        grid.setConstraints(btnCreate, c);
        getContentPane().add(btnCreate);
        
        
        pack();
    }
    
    private void createResource(final ResourceType resourceType, final String filename, final String moduleNamespace, final String moduleNamespacePrefix) {
        
        final StringBuilder resourceContentBuilder = new StringBuilder();
        try(final InputStream is = getClass().getResourceAsStream(resourceType.getTemplatePath());
                final Reader reader = new InputStreamReader(is)) {
            final char buf[] = new char[1024];
            int read = -1;
            while((read = reader.read(buf)) > -1) {
                resourceContentBuilder.append(buf, 0, read);
            }
        } catch(final IOException ioe) {
            ClientFrame.showErrorMessage(ioe.getMessage(), ioe);
        }
        
        final String resourceContent;
        if(resourceType == ResourceType.XQUERY_LIBRARY) {
            resourceContent = resourceContentBuilder.toString().replaceAll("\\$NS", moduleNamespace).replaceAll("\\$PREFIX", moduleNamespacePrefix);
        } else {
            resourceContent = resourceContentBuilder.toString();
        }
        
        try {
            final String resName = URIUtils.urlEncodeUtf8((isNullOrEmpty(filename) ? DEFAULT_FILENAME : filename) + "." + resourceType.getFileExtension());
            final String resType = resourceType == ResourceType.XML_DOCUMENT ? XMLResource.RESOURCE_TYPE : BinaryResource.RESOURCE_TYPE;
            
            final Collection collection = client.current;
            
            final Resource resource = collection.createResource(resName, resType);
            resource.setContent(resourceContent);
            ((EXistResource)resource).setMimeType(resourceType.getMimeType());
            collection.storeResource(resource);
            collection.close();
            client.reloadCollection();
        } catch(final XMLDBException xmldbe) {
            ClientFrame.showErrorMessage(xmldbe.getMessage(), xmldbe);
        }
    }

    private boolean isNullOrEmpty(String str) {
        return str == null || str.isEmpty();
    }
}