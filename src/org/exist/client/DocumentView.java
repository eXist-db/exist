/*
 * eXist Open Source Native XML Database Copyright (C) 2001-03 Wolfgang M.
 * Meier wolfgang@exist-db.org http://exist.sourceforge.net
 * 
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation; either version 2 of the License, or (at your
 * option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 * 
 * $Id$
 */
package org.exist.client;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Observable;
import java.util.Observer;
import java.util.Properties;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.border.BevelBorder;

import org.exist.storage.ElementIndex;
import org.exist.storage.TextSearchEngine;
import org.exist.util.ProgressIndicator;
import org.exist.xmldb.UserManagementService;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
class DocumentView extends JFrame {
	protected Resource resource;
	protected Collection collection;
	protected boolean readOnly = false;
	protected ClientTextArea text;
	protected JButton saveButton;
	protected JTextField statusMessage;
	protected JProgressBar progress;
	protected JPopupMenu popup;
	protected Properties properties;
	public DocumentView(Collection collection, Resource resource,
			Properties properties) throws XMLDBException {
		super("View Document");
		this.collection = collection;
		this.resource = resource;
		this.properties = properties;
		getContentPane().setLayout(new BorderLayout());
		setupComponents();
		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent ev) {
				close();
			}
		});
		pack();
	}
	public void setReadOnly() {
		text.setEditable(false);
		saveButton.setEnabled(false);
		readOnly = true;
	}
	private void close() {
		if (readOnly)
			return;
		try {
			UserManagementService service = (UserManagementService) collection
					.getService("UserManagementService", "1.0");
			service.unlockResource(resource);
		} catch (XMLDBException e) {
			e.printStackTrace();
		}
	}
	private void setupComponents() throws XMLDBException {
		JToolBar toolbar = new JToolBar();
		URL url = getClass().getResource("icons/Save24.gif");
		saveButton = new JButton(new ImageIcon(url));
		saveButton
				.setToolTipText("Store the modified data back into the database.");
		saveButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				save();
			}
		});
		toolbar.add(saveButton);
		url = getClass().getResource("icons/Export24.gif");
		JButton button = new JButton(new ImageIcon(url));
		button.setToolTipText("Export to file.");
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				export();
			}
		});
		toolbar.add(button);
		toolbar.addSeparator();
		url = getClass().getResource("icons/Copy24.gif");
		button = new JButton(new ImageIcon(url));
		button.setToolTipText("Copy selection.");
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				text.copy();
			}
		});
		toolbar.add(button);
		url = getClass().getResource("icons/Cut24.gif");
		button = new JButton(new ImageIcon(url));
		button.setToolTipText("Cut selection.");
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				text.cut();
			}
		});
		toolbar.add(button);
		url = getClass().getResource("icons/Paste24.gif");
		button = new JButton(new ImageIcon(url));
		button.setToolTipText("Paste selection.");
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				text.paste();
			}
		});
		toolbar.add(button);
		getContentPane().add(toolbar, BorderLayout.NORTH);
		text = new ClientTextArea(true, "XML");
		getContentPane().add(text, BorderLayout.CENTER);
		Box statusbar = Box.createHorizontalBox();
		statusbar.setBorder(BorderFactory
				.createBevelBorder(BevelBorder.LOWERED));
		statusMessage = new JTextField(20);
		statusMessage.setEditable(false);
		statusMessage.setFocusable(false);
		statusMessage.setText("Loading " + resource.getId() + " ...");
		statusbar.add(statusMessage);
		progress = new JProgressBar();
		progress.setPreferredSize(new Dimension(200, 30));
		progress.setVisible(false);
		statusbar.add(progress);
		getContentPane().add(statusbar, BorderLayout.SOUTH);
	}
	private void save() {
		new Thread() {
			public void run() {
				try {
					statusMessage.setText("Storing " + resource.getId());
					if (collection instanceof Observable)
						((Observable) collection)
								.addObserver(new ProgressObserver());
					progress.setIndeterminate(true);
					progress.setVisible(true);
					resource.setContent(text.getText());
					collection.storeResource(resource);
					if (collection instanceof Observable)
						((Observable) collection).deleteObservers();
				} catch (XMLDBException e) {
					ClientFrame.showErrorMessage("XMLDBException: "
							+ e.getMessage(), e);
				} finally {
					progress.setVisible(false);
				}
			}
		}.start();
	}
	private void export() {
		String workDir = properties.getProperty("working-dir", System
				.getProperty("user.dir"));
		JFileChooser chooser = new JFileChooser(workDir);
		chooser.setMultiSelectionEnabled(false);
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		if (chooser.showDialog(this, "Select file for export") == JFileChooser.APPROVE_OPTION) {
			File file = chooser.getSelectedFile();
			if (file.exists()
					&& JOptionPane.showConfirmDialog(this,
							"File exists. Overwrite?", "Overwrite?",
							JOptionPane.YES_NO_OPTION) == JOptionPane.NO_OPTION)
				return;
			try {
				OutputStreamWriter writer = new OutputStreamWriter(
						new FileOutputStream(file), Charset.forName(properties
								.getProperty("encoding")));
				writer.write(text.getText());
				writer.close();
			} catch (IOException e) {
				ClientFrame.showErrorMessage("XMLDBException: "
						+ e.getMessage(), e);
			}
			File selectedDir = chooser.getCurrentDirectory();
			properties
					.setProperty("working-dir", selectedDir.getAbsolutePath());
		}
	}
	public void setText(String content) throws XMLDBException {
		text.setText("");
		text.setText(content);
		text.setCaretPosition(0);
		text.scrollToCaret();
		statusMessage.setText("Loaded " + resource.getId());
	}
	class ProgressObserver implements Observer {
		int mode = 0;
		public void update(Observable o, Object arg) {
			progress.setIndeterminate(false);
			ProgressIndicator ind = (ProgressIndicator) arg;
			progress.setValue(ind.getPercentage());
			if (o instanceof TextSearchEngine)
				progress.setString("Storing words");
			else if (o instanceof ElementIndex)
				progress.setString("Storing elements");
		}
	}
}
