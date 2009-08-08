/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2007 The eXist Project
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
 *  $Id$
 */
package org.exist.client;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URL;
import java.util.Iterator;
import java.util.Properties;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.BevelBorder;
import javax.xml.transform.OutputKeys;

import org.exist.storage.DBBroker;
import org.exist.util.MimeTable;
import org.exist.xmldb.XQueryService;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.CompiledExpression;
import org.xmldb.api.base.ResourceIterator;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

public class QueryDialog extends JFrame {

    private InteractiveClient client;
	private Collection collection;
	private Properties properties;
	private ClientTextArea query;
	private ClientTextArea resultDisplay;
	private ClientTextArea exprDisplay;
	private JComboBox collections= null;
	private SpinnerNumberModel count;
	private DefaultComboBoxModel history= new DefaultComboBoxModel();
    private Font display = new Font("Monospaced", Font.BOLD, 12);
	private JTextField statusMessage;
	private JTextField queryPositionDisplay;
	private JProgressBar progress;

	public QueryDialog(InteractiveClient client, Collection collection, Properties properties) {
		super("Query Dialog");
		this.collection= collection;
		this.properties= properties;
        this.client = client;
		setupComponents();
		pack();
	}

	private void setupComponents() {
		getContentPane().setLayout(new BorderLayout());
		JToolBar toolbar = new JToolBar();
		
		URL url= getClass().getResource("icons/Open24.gif");
		JButton button= new JButton(new ImageIcon(url));
		button.setToolTipText(
		"Read query from file.");
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				open();
			}
		});
		toolbar.add(button);
		
		url= getClass().getResource("icons/SaveAs24.gif");
		button= new JButton(new ImageIcon(url));
		button.setToolTipText(
		"Write query to file.");
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				save(query.getText(), "query");
			}
		});
		toolbar.add(button);

		url= getClass().getResource("icons/SaveAs24.gif");
		button= new JButton(new ImageIcon(url));
		button.setToolTipText(
		"Write result to file.");
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				save(resultDisplay.getText(), "result");
			}
		});
		toolbar.add(button);
		
		toolbar.addSeparator();
		url = getClass().getResource("icons/Copy24.gif");
		button = new JButton(new ImageIcon(url));
		button.setToolTipText("Copy selection.");
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				query.copy();
			}
		});
		toolbar.add(button);
		url = getClass().getResource("icons/Cut24.gif");
		button = new JButton(new ImageIcon(url));
		button.setToolTipText("Cut selection.");
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				query.cut();
			}
		});
		toolbar.add(button);
		url = getClass().getResource("icons/Paste24.gif");
		button = new JButton(new ImageIcon(url));
		button.setToolTipText("Paste selection.");
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
			   query.paste();
			}
		});
		toolbar.add(button);
		
		toolbar.addSeparator();
		//TODO: change icon
		url= getClass().getResource("icons/Find24.gif");
		button= new JButton(new ImageIcon(url));
		button.setToolTipText("Compile only query.");
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
			   compileQuery();
			}
		});
		toolbar.add(button);
		
		toolbar.addSeparator();
        url= getClass().getResource("icons/Find24.gif");
		button= new JButton("Submit", new ImageIcon(url));
		button.setToolTipText("Submit query.");
		toolbar.add(button);
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				doQuery();
			}
		});
		toolbar.add(button);
		
		
		JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
		split.setResizeWeight(0.5);
		
		JComponent qbox= createQueryBox();
		split.setTopComponent(qbox);

        JPanel vbox = new JPanel();
        vbox.setLayout(new BorderLayout());
        
        JLabel label = new JLabel("Results:");
        vbox.add(label, BorderLayout.NORTH);
        
        JTabbedPane tabs = new JTabbedPane();
        
		resultDisplay= new ClientTextArea(false, "XML");
		resultDisplay.setText("");
		resultDisplay.setPreferredSize(new Dimension(400, 250));
		tabs.add("XML", resultDisplay);
		
		exprDisplay = new ClientTextArea(false, "Dump");
		exprDisplay.setText("");
		exprDisplay.setPreferredSize(new Dimension(400, 250));
		tabs.add("Trace", exprDisplay);
		
        vbox.add(tabs, BorderLayout.CENTER);
        
        Box statusbar = Box.createHorizontalBox();
        statusbar.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
        statusMessage = new JTextField(20);
        statusMessage.setEditable(false);
        statusMessage.setFocusable(true);
        statusbar.add(statusMessage);
        queryPositionDisplay = new JTextField(5);
        queryPositionDisplay.setEditable(false);
        queryPositionDisplay.setFocusable(true);
        statusbar.add(queryPositionDisplay);
        query.setPositionOutputTextArea(queryPositionDisplay);
        
        progress = new JProgressBar();
        progress.setPreferredSize(new Dimension(200, statusbar.getHeight()));
        progress.setVisible(false);
        statusbar.add(progress);
        
        vbox.add(statusbar, BorderLayout.SOUTH);
        
		split.setBottomComponent(vbox);
		split.setDividerLocation(0.4);
		getContentPane().add(toolbar, BorderLayout.NORTH);
        getContentPane().add(split, BorderLayout.CENTER);
	}

	private JComponent createQueryBox() {
		JTabbedPane tabs = new JTabbedPane();

		JPanel inputVBox = new JPanel();
		inputVBox.setLayout(new BorderLayout());
		tabs.add("Query Input:", inputVBox);
		
		Box historyBox= Box.createHorizontalBox();
		JLabel label= new JLabel("History: ");
		historyBox.add(label);
		final JComboBox historyList= new JComboBox(history);
		for(Iterator i = client.queryHistory.iterator(); i.hasNext(); ) {
			addQuery((String) i.next());
		}
		historyList.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String item = (String)client.queryHistory.get(historyList.getSelectedIndex());
				query.setText(item);
			}
		});
		historyBox.add(historyList);
		inputVBox.add(historyBox, BorderLayout.NORTH);
        
        query = new ClientTextArea(true, "XQUERY");
        query.setElectricScroll(1);
		query.setEditable(true);
		query.setPreferredSize(new Dimension(350, 200));
        inputVBox.add(query, BorderLayout.CENTER);
        
		Box optionsPanel = Box.createHorizontalBox();
        
        label = new JLabel("Context:");
        optionsPanel.add(label);
        
		final Vector data= new Vector();
		try {
			Collection root = client.getCollection(DBBroker.ROOT_COLLECTION);
			data.addElement(collection.getName());
			getCollections(root, collection, data);
		} catch (XMLDBException e) {
			ClientFrame.showErrorMessage(
					"An error occurred while retrieving collections list.", e);
		}
		collections= new JComboBox(data);
		collections.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				int p = collections.getSelectedIndex();
				String context = (String)data.elementAt(p);
				try {
					collection = client.getCollection(context);
				} catch (XMLDBException e1) {
				}
			}
		});
        optionsPanel.add(collections);

		label= new JLabel(" Display max.:");
        optionsPanel.add(label);
        
		count= new SpinnerNumberModel(100, 1, 10000, 50);
		JSpinner spinner= new JSpinner(count);
		spinner.setMaximumSize(new Dimension(400,100));
		optionsPanel.add(spinner);
       
		inputVBox.add(optionsPanel, BorderLayout.SOUTH);
		return tabs;
	}

	private Vector getCollections(Collection root, Collection collection, Vector collectionsList)
			throws XMLDBException {
		if(!collection.getName().equals(root.getName()))
			collectionsList.add(root.getName());
		String[] childCollections= root.listChildCollections();
		Collection child;
		for (int i= 0; i < childCollections.length; i++) {
			child= root.getChildCollection(childCollections[i]);
			getCollections(child, collection, collectionsList);
		}
		return collectionsList;
	}

	private void open() {
		String workDir = properties.getProperty("working-dir", System.getProperty("user.dir"));
		JFileChooser chooser = new JFileChooser();
		chooser.setCurrentDirectory(new File(workDir));
		chooser.setMultiSelectionEnabled(false);
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		chooser.addChoosableFileFilter(new MimeTypeFileFilter("application/xquery"));
		
		if (chooser.showDialog(this, "Select query file")
			== JFileChooser.APPROVE_OPTION) {
			File selectedDir = chooser.getCurrentDirectory();
			properties.setProperty("working-dir", selectedDir.getAbsolutePath());
			File file = chooser.getSelectedFile();
			if(!file.canRead())
				JOptionPane.showInternalMessageDialog(this, "Cannot read query from file " + file.getAbsolutePath(),
					"Error", JOptionPane.ERROR_MESSAGE);
			try {
				BufferedReader reader = new BufferedReader(new FileReader(file));
				StringBuilder buf = new StringBuilder();
				String line;
				while((line = reader.readLine()) != null) {
					buf.append(line);
					buf.append('\n');
				}
				query.setText(buf.toString());
			} catch (FileNotFoundException e) {
				ClientFrame.showErrorMessage(e.getMessage(), e);
			} catch (IOException e) {
				ClientFrame.showErrorMessage(e.getMessage(), e);
			}
		}
	}
	
	private void save(String stringToSave, String fileCategory) {
		if ( stringToSave == null || "".equals(stringToSave) )
			return;
		String workDir = properties.getProperty("working-dir", System.getProperty("user.dir"));
		JFileChooser chooser = new JFileChooser();
		chooser.setMultiSelectionEnabled(false);
		chooser.setCurrentDirectory(new File(workDir));
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		if(fileCategory.equals("result"))
		{
			chooser.addChoosableFileFilter(new MimeTypeFileFilter("application/xhtml+xml"));
			chooser.addChoosableFileFilter(new MimeTypeFileFilter("application/xml"));
		}
		else
		{
			chooser.addChoosableFileFilter(new MimeTypeFileFilter("application/xquery"));
		}
		if (chooser.showDialog(this, "Select file for " +fileCategory+ " export")
			== JFileChooser.APPROVE_OPTION) {
			File selectedDir = chooser.getCurrentDirectory();
			properties.setProperty("working-dir", selectedDir.getAbsolutePath());
			File file = chooser.getSelectedFile();
			if(file.exists() && (!file.canWrite()))
				JOptionPane.showMessageDialog(this, "Can not write " +fileCategory+ " to file " + file.getAbsolutePath(),
						"Error", JOptionPane.ERROR_MESSAGE);
			if(file.exists() &&
				JOptionPane.showConfirmDialog(this, "File exists. Overwrite?", "Overwrite?", 
					JOptionPane.YES_NO_OPTION) == JOptionPane.NO_OPTION)
				return;
			try {
				FileWriter writer = new FileWriter(file);
				writer.write(stringToSave);
				writer.close();
			} catch (FileNotFoundException e) {
				ClientFrame.showErrorMessage(e.getMessage(), e);
			} catch (IOException e) {
				ClientFrame.showErrorMessage(e.getMessage(), e);
			}
		}
	}
	
	private void doQuery() {
		String xpath= (String) query.getText();
		if (xpath.length() == 0)
			return;
		resultDisplay.setText("");
		new QueryThread(xpath).start();
        System.gc();
	}
	
	
	private void compileQuery() {
		String xpath= (String) query.getText();
		if (xpath.length() == 0)
			return;
		resultDisplay.setText("");
		
		{
			statusMessage.setText("Compiling query ...");
			setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
			long tResult =0;
			long tCompiled=0;
			
			try {
				XQueryService service= (XQueryService) collection.getService("XQueryService", "1.0");
				service.setProperty(OutputKeys.INDENT, properties.getProperty(OutputKeys.INDENT, "yes"));
				long t0 = System.currentTimeMillis();
				CompiledExpression compiled = service.compile(xpath);
				long t1 = System.currentTimeMillis();
				tCompiled = t1 - t0;
				statusMessage.setText("Compilation: " + tCompiled + "ms");
				
			} catch (Throwable e) {
				statusMessage.setText("Error: "+InteractiveClient.getExceptionMessage(e)+". Compilation: " + tCompiled + "ms, Execution: " + tResult+"ms");
		
				ClientFrame.showErrorMessageQuery(
						"An exception occurred during query compilation: "
						+ InteractiveClient.getExceptionMessage(e), e);
				
			} 
			
			setCursor(Cursor.getDefaultCursor());
			
		}
		
		
		
	}
	
	
	class QueryThread extends Thread {

		private String xpath;
		
		public QueryThread(String query) {
			super();
			this.xpath = query;
		}
		
		/**
		 * @see java.lang.Thread#run()
		 */
		public void run() {
			statusMessage.setText("Processing query ...");
			progress.setVisible(true);
			progress.setIndeterminate(true);
			setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
			long tResult =0;
			long tCompiled=0;
			ResourceSet result = null;
			try {
				XQueryService service= (XQueryService) collection.getService("XQueryService", "1.0");
				service.setProperty(OutputKeys.INDENT, properties.getProperty(OutputKeys.INDENT, "yes"));
				long t0 = System.currentTimeMillis();
				CompiledExpression compiled = service.compile(xpath);
				long t1 = System.currentTimeMillis();
				tCompiled = t1 - t0;
				result = service.execute(compiled);
				tResult = System.currentTimeMillis() - t1;
				
				StringWriter writer = new StringWriter();
				service.dump(compiled, writer);
				exprDisplay.setText(writer.toString());
				
				statusMessage.setText("Retrieving results ...");
				XMLResource resource;
				int howmany= count.getNumber().intValue();
				progress.setIndeterminate(false);
				progress.setMinimum(1);
				progress.setMaximum(howmany);
				int j= 0;
				int select=-1;
				StringBuilder contents = new StringBuilder();
				for (ResourceIterator i = result.getIterator(); i.hasMoreResources() && j < howmany; j++) {
					resource= (XMLResource) i.nextResource();
					progress.setValue(j);
					try {
						contents.append((String) resource.getContent());
						contents.append("\n");
					} catch (XMLDBException e) {
						select = ClientFrame.showErrorMessageQuery(
								"An error occurred while retrieving results: "
								+ InteractiveClient.getExceptionMessage(e), e);
						if (select == 3) break;
					}
				}
				resultDisplay.setText(contents.toString());
				resultDisplay.setCaretPosition(0);
				resultDisplay.scrollToCaret();
				statusMessage.setText("Found " + result.getSize() + " items." + 
					" Compilation: " + tCompiled + "ms, Execution: " + tResult+"ms");
			} catch (Throwable e) {
				statusMessage.setText("Error: "+InteractiveClient.getExceptionMessage(e)+". Compilation: " + tCompiled + "ms, Execution: " + tResult+"ms");
			    progress.setVisible(false);
			    
			
				ClientFrame.showErrorMessageQuery(
						"An exception occurred during query execution: "
						+ InteractiveClient.getExceptionMessage(e), e);
			} finally {
                if (result != null)
                    try {
                        result.clear();
                    } catch (XMLDBException e) {
                    }
            }
			if(client.queryHistory.isEmpty() || !((String)client.queryHistory.getLast()).equals(xpath)) {
				client.addToHistory(xpath);
				client.writeQueryHistory();
				addQuery(xpath);
			}
			setCursor(Cursor.getDefaultCursor());
			progress.setVisible(false);
		}
	}
	private void addQuery(String query) {
		if(query.length() > 40)
			query = query.substring(0, 40);
		history.addElement(Integer.toString(history.getSize()+1) + ". " + query);
	}
}
