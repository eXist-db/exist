/*
 * eXist Open Source Native XML Database Copyright (C) 2001-06 Wolfgang M.
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
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Observable;
import java.util.Observer;
import java.util.Properties;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.border.BevelBorder;
import javax.xml.transform.OutputKeys;

import org.exist.security.User;
import org.exist.storage.ElementIndex;
import org.exist.storage.TextSearchEngine;
import org.exist.util.ProgressIndicator;
import org.exist.xmldb.UserManagementService;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.util.URIUtils;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;
class DocumentView extends JFrame {
	
	protected InteractiveClient client;
	private	 XmldbURI resourceName;
	protected Resource resource;
	protected Collection collection;
	protected boolean readOnly = false;
	protected ClientTextArea text;
	protected JButton saveButton;
	protected JButton saveAsButton;
	protected JTextField statusMessage;
	protected JProgressBar progress;
	protected JPopupMenu popup;
	protected Properties properties;
	
	public DocumentView(InteractiveClient client, XmldbURI resourceName, Properties properties) throws XMLDBException
	{
		super("View Document ");
		this.resourceName = resourceName;
		this.resource = client.retrieve(resourceName, properties.getProperty(OutputKeys.INDENT, "yes"));
		this.client = client;
		this.collection = client.getCollection();
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
	
	public void viewDocument()
	{
		try{
			if (resource.getResourceType().equals("XMLResource"))
	            setText((String) resource.getContent());
	        else
	            setText(new String((byte[]) resource.getContent()));
	        
	        // lock the resource for editing
	        UserManagementService service = (UserManagementService)
	        client.current.getService("UserManagementService", "1.0");
	        User user = service.getUser(properties.getProperty("user"));
	        String lockOwner = service.hasUserLock(resource);
	        if(lockOwner != null) {
	            if(JOptionPane.showConfirmDialog(this,
	                    "Resource is already locked by user " + lockOwner +
	                    ". Should I try to relock it?",
	                    "Resource locked",
	                    JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
	                dispose();
	                this.setCursor(Cursor.getDefaultCursor());
	                return;
	            }
	        }
	        
	        try {
	            service.lockResource(resource, user);
	        } catch(XMLDBException ex) {
	            System.out.println(ex.getMessage());
	            JOptionPane.showMessageDialog(this,
	                    "Resource cannot be locked. Opening read-only.");
	            setReadOnly();
	        }
	        setVisible(true);
		}
		catch (XMLDBException ex) {
			showErrorMessage("XMLDB error: " + ex.getMessage(), ex);
		}
	}
	
	
	private static void showErrorMessage(String message, Throwable t) {
        JScrollPane scroll = null;
        JTextArea msgArea = new JTextArea(message);
        msgArea.setBorder(BorderFactory.createTitledBorder("Message:"));
        msgArea.setEditable(false);
        msgArea.setBackground(null);
        if (t != null) {
            StringWriter out = new StringWriter();
            PrintWriter writer = new PrintWriter(out);
            t.printStackTrace(writer);
            JTextArea stacktrace = new JTextArea(out.toString(), 20, 50);
            stacktrace.setBackground(null);
            stacktrace.setEditable(false);
            scroll = new JScrollPane(stacktrace);
            scroll.setPreferredSize(new Dimension(250, 300));
            scroll.setBorder(BorderFactory
                    .createTitledBorder("Exception Stacktrace:"));
        }
        JOptionPane optionPane = new JOptionPane();
        optionPane.setMessage(new Object[]{msgArea, scroll});
        optionPane.setMessageType(JOptionPane.ERROR_MESSAGE);
        JDialog dialog = optionPane.createDialog(null, "Error");
        dialog.setResizable(true);
        dialog.pack();
        dialog.setVisible(true);
        return;
    }
	
	public void setReadOnly() {
		text.setEditable(false);
		saveButton.setEnabled(false);
		readOnly = true;
	}
	
	private void close() {
		unlockView();
	}
	
	private void unlockView()
	{
		if (readOnly)
			return;
		try
		{
			UserManagementService service = (UserManagementService) collection
					.getService("UserManagementService", "1.0");
			service.unlockResource(resource);
		}
		catch (XMLDBException e)
		{
			e.printStackTrace();
		}
	}
	
	private void setupComponents() throws XMLDBException {

        /* start of menubar */
        JMenuBar menubar = new JMenuBar();

        JMenu fileMenu = new JMenu("File");
        fileMenu.setMnemonic(KeyEvent.VK_F);
        menubar.add(fileMenu); 

        JMenuItem item;
        // Save to database
        item = new JMenuItem("Save", KeyEvent.VK_S);
        item.setAccelerator(KeyStroke.getKeyStroke("control S"));
        item.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                save();
            }
        });
        fileMenu.add(item);
        /*
        // Refresh
        item = new JMenuItem("Refresh", KeyEvent.VK_R);
        item.setAccelerator(KeyStroke.getKeyStroke("control R"));
        item.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    refresh() ;
                } catch (XMLDBException u) {
                    u.printStackTrace();
                }
            }
        });
        fileMenu.add(item);
        */

        setJMenuBar(menubar);
        /* end of menubar */
        
        /* The icon toolbar */

		JToolBar toolbar = new JToolBar();
		
		//Save button
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
		
		//Save As button
		url = getClass().getResource("icons/SaveAs24.gif");
		saveAsButton = new JButton(new ImageIcon(url));
		saveAsButton
				.setToolTipText("Store a new document into the database.");
		saveAsButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				saveAs();
			}
		});
		toolbar.add(saveAsButton);
		
		//Export button
		url = getClass().getResource("icons/Export24.gif");
		JButton button = new JButton(new ImageIcon(url));
		button.setToolTipText("Export to file.");
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e)  {
			try {
				export() ;
			} catch (XMLDBException u) {
				u.printStackTrace();
			}
			}
		});
		toolbar.add(button);
		
		toolbar.addSeparator();
		
		//Copy button
		url = getClass().getResource("icons/Copy24.gif");
		button = new JButton(new ImageIcon(url));
		button.setToolTipText("Copy selection.");
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				text.copy();
			}
		});
		toolbar.add(button);
		
		//Cut button
		url = getClass().getResource("icons/Cut24.gif");
		button = new JButton(new ImageIcon(url));
		button.setToolTipText("Cut selection.");
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				text.cut();
			}
		});
		toolbar.add(button);
		
		//Paste button
		url = getClass().getResource("icons/Paste24.gif");
		button = new JButton(new ImageIcon(url));
		button.setToolTipText("Paste selection.");
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				text.paste();
			}
		});
		toolbar.add(button);
		
		toolbar.addSeparator();
		
		//Refresh button
		url = getClass().getResource("icons/Refresh24.gif");
		button = new JButton(new ImageIcon(url));
		button.setToolTipText("Refresh Document.");
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e)  {
    			try {
    				refresh() ;
    			} catch (XMLDBException u) {
    				u.printStackTrace();
    			}
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
		statusMessage.setText("Loading " + URIUtils.urlDecodeUtf8(resource.getId()) + " ...");
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
					statusMessage.setText("Storing " + URIUtils.urlDecodeUtf8(resource.getId()));
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
	
	private void saveAs()
	{
		new Thread()
		{
			public void run()
			{

				//Get the name to save the resource as
				String nameres = JOptionPane.showInputDialog(null, "Name of the XML resource (extension incluse)");
				if (nameres != null)
				{
					try
					{
						//Change status message and display a progress dialog
						statusMessage.setText("Storing " + nameres);
						if (collection instanceof Observable)
							((Observable) collection).addObserver(new ProgressObserver());
						progress.setIndeterminate(true);
						progress.setVisible(true);
					
						//Create a new resource as named, set the content, store the resource
						XMLResource result = null;
						result = (XMLResource) collection.createResource(URIUtils.encodeXmldbUriFor(nameres).toString(), XMLResource.RESOURCE_TYPE);
						result.setContent(text.getText());
						collection.storeResource(result);
						client.reloadCollection();	//reload the client collection
						if (collection instanceof Observable)
							((Observable) collection).deleteObservers();
					}
					catch (XMLDBException e)
					{
						ClientFrame.showErrorMessage("XMLDBException: " + e.getMessage(), e);
					} catch (URISyntaxException e) {
						ClientFrame.showErrorMessage("URISyntaxException: " + e.getMessage(), e);
					}
					finally
					{
						//hide the progress dialog
						progress.setVisible(false);
					}
				}
			}
		}.start();
	}
	
	private void export() throws XMLDBException {
		String workDir = properties.getProperty("working-dir", System
				.getProperty("user.dir"));
		JFileChooser chooser = new JFileChooser(workDir);
		chooser.setMultiSelectionEnabled(false);
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		chooser.setSelectedFile(new File(resource.getId())); 
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
	
	private void refresh() throws XMLDBException
	{	
		//First unlock the resource
		unlockView();
		
		//Reload the resource
		this.resource = client.retrieve(resourceName, properties.getProperty(OutputKeys.INDENT, "yes"));
		
		//View and lock the resource
		viewDocument();
	}
	
	public void setText(String content) throws XMLDBException	{
		text.setText("");
		text.setText(content);
		text.setCaretPosition(0);
		text.scrollToCaret();
		statusMessage.setText("Loaded " + XmldbURI.create(client.getCollection().getName()).append(resourceName) +" from "+properties.getProperty("uri"));
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
