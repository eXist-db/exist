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
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
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
import org.exist.security.PermissionDeniedException;
import org.exist.xmldb.LocalCollection;
import org.exist.xmldb.LocalXPathQueryService;
import org.exist.xmldb.UserManagementService;
import org.exist.xmldb.XQueryService;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.XQueryWatchDog;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.CompiledExpression;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceIterator;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

public class QueryDialog extends JFrame {

	private static final long serialVersionUID = 1L;

	private InteractiveClient client;
	private Collection collection;
	private Properties properties;
	private ClientTextArea query;
	private JTabbedPane resultTabs;
	private ClientTextArea resultDisplay;
	private ClientTextArea exprDisplay;
	private JComboBox collections= null;
	private SpinnerNumberModel count;
	private DefaultComboBoxModel history= new DefaultComboBoxModel();
        private Font display = new Font("Monospaced", Font.BOLD, 12);
	private JTextField statusMessage;
	private JTextField queryPositionDisplay;
	private JProgressBar progress;
	private JButton submitButton;
	private JButton killButton;
	private QueryThread q=null;
        private Resource resource = null;

        private QueryDialog(final InteractiveClient client, final Collection collection, final Properties properties, boolean loadedFromDb) {
            super(Messages.getString("QueryDialog.0"));
            this.collection= collection;
            this.properties= properties;
            this.client = client;
            this.setIconImage(InteractiveClient.getExistIcon(getClass()).getImage());
            setupComponents(loadedFromDb);
            pack();
	}
        
	public QueryDialog(final InteractiveClient client, final Collection collection, final Properties properties) {
            this(client, collection, properties, false);
	}

        public QueryDialog(final InteractiveClient client, final Collection collection, final Resource resource, final Properties properties) throws XMLDBException {
            this(client, collection, properties, true);
            this.resource = resource;
            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent ev) {
                    try {
                        UserManagementService service = (UserManagementService) collection
                            .getService("UserManagementService", "1.0"); //$NON-NLS-1$ //$NON-NLS-2$
                        service.unlockResource(resource);
                    } catch (XMLDBException e) {
                        e.printStackTrace();
                    }
                }
            });
            
            //set the content of the query
            query.setText(new String((byte[])resource.getContent()));
            
            //set title
            setTitle(Messages.getString("QueryDialog.0") + ": " + resource.getId());
        }

        private void saveToDb(final String queryText) {
            
            try {
                resource.setContent(queryText);
                collection.storeResource(resource);
            } catch(final XMLDBException xmldbe) {
                ClientFrame.showErrorMessage(xmldbe.getMessage(), xmldbe);
            }
        }
        
	private void setupComponents(boolean loadedFromDb) {
		getContentPane().setLayout(new BorderLayout());
		JToolBar toolbar = new JToolBar();
		
		URL url= getClass().getResource("icons/Open24.gif");
		JButton button= new JButton(new ImageIcon(url));
		button.setToolTipText(Messages.getString("QueryDialog.opentooltip"));
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				open();
			}
		});
		toolbar.add(button);
		
                if(loadedFromDb) {
                    url= getClass().getResource("icons/SaveAs23.gif");
                    button= new JButton(new ImageIcon(url));
                    button.setToolTipText("Save to database");
                    button.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            saveToDb(query.getText());
                        }
                    });
                    toolbar.add(button);
                }
                
		url= getClass().getResource("icons/SaveAs24.gif");
		button= new JButton(new ImageIcon(url));
		button.setToolTipText(Messages.getString("QueryDialog.saveastooltip"));
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				save(query.getText(), "query");
			}
		});
		toolbar.add(button);

		url= getClass().getResource("icons/SaveAs25.gif");
		button= new JButton(new ImageIcon(url));
		button.setToolTipText(Messages.getString("QueryDialog.saveresultstooltip"));
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				save(resultDisplay.getText(), "result");
			}
		});
		toolbar.add(button);
		
		toolbar.addSeparator();
		url = getClass().getResource("icons/Copy24.gif");
		button = new JButton(new ImageIcon(url));
		button.setToolTipText(Messages.getString("QueryDialog.copytooltip"));
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				query.copy();
			}
		});
		toolbar.add(button);
		url = getClass().getResource("icons/Cut24.gif");
		button = new JButton(new ImageIcon(url));
		button.setToolTipText(Messages.getString("QueryDialog.cuttooltip"));
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				query.cut();
			}
		});
		toolbar.add(button);
		url = getClass().getResource("icons/Paste24.gif");
		button = new JButton(new ImageIcon(url));
		button.setToolTipText(Messages.getString("QueryDialog.pastetooltip"));
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
		button.setToolTipText(Messages.getString("QueryDialog.compiletooltip"));
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
			   compileQuery();
			}
		});
		toolbar.add(button);
		
		toolbar.addSeparator();
        url= getClass().getResource("icons/Find24.gif");
		submitButton= new JButton(Messages.getString("QueryDialog.submitbutton"), new ImageIcon(url));
		submitButton.setToolTipText(Messages.getString("QueryDialog.submittooltip"));
		toolbar.add(submitButton);
		submitButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				submitButton.setEnabled(false);
				if(collection instanceof LocalCollection)
					killButton.setEnabled(true);
				q=doQuery();
			}
		});
		
		toolbar.addSeparator();
		url= getClass().getResource("icons/Delete24.gif");
		killButton= new JButton(Messages.getString("QueryDialog.killbutton"), new ImageIcon(url));
		killButton.setToolTipText(Messages.getString("QueryDialog.killtooltip"));
		toolbar.add(killButton);
		killButton.setEnabled(false);
		killButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if(q!=null) {
					q.killQuery();
					killButton.setEnabled(false);

					q = null;
				}
			}
		});
		
		JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
		split.setResizeWeight(0.5);
		
		JComponent qbox= createQueryBox();
		split.setTopComponent(qbox);

        JPanel vbox = new JPanel();
        vbox.setLayout(new BorderLayout());
        
        JLabel label = new JLabel(Messages.getString("QueryDialog.resultslabel"));
        vbox.add(label, BorderLayout.NORTH);
        
		resultTabs = new JTabbedPane();
        
		resultDisplay= new ClientTextArea(false, "XML");
		resultDisplay.setText("");
		resultDisplay.setPreferredSize(new Dimension(400, 250));
		resultTabs.add(Messages.getString("QueryDialog.XMLtab"), resultDisplay);
		
		exprDisplay = new ClientTextArea(false, "Dump");
		exprDisplay.setText("");
		exprDisplay.setPreferredSize(new Dimension(400, 250));
		resultTabs.add(Messages.getString("QueryDialog.tracetab"), exprDisplay);
		
        vbox.add(resultTabs, BorderLayout.CENTER);
        
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
		tabs.add(Messages.getString("QueryDialog.inputtab"), inputVBox);
		
		Box historyBox= Box.createHorizontalBox();
		JLabel label= new JLabel(Messages.getString("QueryDialog.historylabel"));
		historyBox.add(label);
		final JComboBox historyList= new JComboBox(history);
		for(String query : client.queryHistory) {
			addQuery(query);
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
        
        label = new JLabel(Messages.getString("QueryDialog.contextlabel"));
        optionsPanel.add(label);
        
		final List<String> data= new ArrayList<String>();
		try {
			Collection root = client.getCollection(XmldbURI.ROOT_COLLECTION);
			data.add(collection.getName());
			getCollections(root, collection, data);
		} catch (XMLDBException e) {
			ClientFrame.showErrorMessage(
					Messages.getString("QueryDialog.collectionretrievalerrormessage")+".", e);
		}
		collections= new JComboBox(new java.util.Vector(data));
		collections.addActionListener(new ActionListener() {
                    @Override
			public void actionPerformed(ActionEvent e) {
				int p = collections.getSelectedIndex();
				String context = data.get(p);
				try {
					collection = client.getCollection(context);
				} catch (XMLDBException e1) {
				}
			}
		});
        optionsPanel.add(collections);

		label= new JLabel(Messages.getString("QueryDialog.maxlabel"));
        optionsPanel.add(label);
        
		count= new SpinnerNumberModel(100, 1, 10000, 50);
		JSpinner spinner= new JSpinner(count);
		spinner.setMaximumSize(new Dimension(400,100));
		optionsPanel.add(spinner);
       
		inputVBox.add(optionsPanel, BorderLayout.SOUTH);
		return tabs;
	}

	private List<String> getCollections(final Collection root, final Collection collection, final List<String> collectionsList) throws XMLDBException {
            if(!collection.getName().equals(root.getName())) {
                collectionsList.add(root.getName());
            }
            final String[] childCollections = root.listChildCollections();
            Collection child = null;
            for(int i = 0; i < childCollections.length; i++) {
                try {
                    child = root.getChildCollection(childCollections[i]);
                } catch(final XMLDBException xmldbe) {
                    if(xmldbe.getCause() instanceof PermissionDeniedException) {
                        continue;
                    } else {
                        throw xmldbe;
                    }
                }
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
		
		if (chooser.showDialog(this, Messages.getString("QueryDialog.opendialog"))
			== JFileChooser.APPROVE_OPTION) {
			File selectedDir = chooser.getCurrentDirectory();
			properties.setProperty("working-dir", selectedDir.getAbsolutePath());
			File file = chooser.getSelectedFile();
			if(!file.canRead())
				JOptionPane.showInternalMessageDialog(this, Messages.getString("QueryDialog.cannotreadmessage")+" "+ file.getAbsolutePath(),
					Messages.getString("QueryDialog.Error"), JOptionPane.ERROR_MESSAGE);
			try {
				BufferedReader reader = new BufferedReader(new FileReader(file));
				try {
					StringBuilder buf = new StringBuilder();
					String line;
					while((line = reader.readLine()) != null) {
						buf.append(line);
						buf.append('\n');
					}
					query.setText(buf.toString());
				} finally {
					reader.close();
				}
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
		if (chooser.showDialog(this, Messages.getString("QueryDialog.savedialogpre")+" " +fileCategory+ " "+Messages.getString("QueryDialog.savedialogpost"))
			== JFileChooser.APPROVE_OPTION) {
			File selectedDir = chooser.getCurrentDirectory();
			properties.setProperty("working-dir", selectedDir.getAbsolutePath());
			File file = chooser.getSelectedFile();
			if(file.exists() && (!file.canWrite()))
				JOptionPane.showMessageDialog(this, Messages.getString("QueryDialog.cannotsavemessagepre")+" " +fileCategory+ " "+Messages.getString("QueryDialog.cannotsavemessageinf")+" " + file.getAbsolutePath(),
						Messages.getString("QueryDialog.Error"), JOptionPane.ERROR_MESSAGE);
			if(file.exists() &&
				JOptionPane.showConfirmDialog(this, Messages.getString("QueryDialog.savedialogconfirm"), "Overwrite?", 
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
	
	private QueryThread doQuery() {
		String xpath= (String) query.getText();
		if (xpath.length() == 0)
			return null;
		resultDisplay.setText("");
		QueryThread q = new QueryThread(xpath);
		q.start();
		System.gc();
		return q;
	}
	
	
	private void compileQuery() {
		String xpath= (String) query.getText();
		if (xpath.length() == 0)
			return;
		resultDisplay.setText("");
		
		{
			statusMessage.setText(Messages.getString("QueryDialog.compilemessage"));
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
				
				// In this way we can see the parsed structure meanwhile the query is
				StringWriter writer = new StringWriter();
				service.dump(compiled, writer);
				exprDisplay.setText(writer.toString());
				resultTabs.setSelectedComponent(exprDisplay);
				
				statusMessage.setText(Messages.getString("QueryDialog.Compilation")+": " + tCompiled + "ms");
				
			} catch (Throwable e) {
				statusMessage.setText(Messages.getString("QueryDialog.Error")+": "+InteractiveClient.getExceptionMessage(e)+". "+Messages.getString("QueryDialog.Compilation")+": " + tCompiled + "ms, "+Messages.getString("QueryDialog.Execution")+": " + tResult+"ms");
		
				ClientFrame.showErrorMessageQuery(
						Messages.getString("QueryDialog.compilationerrormessage")+": "
						+ InteractiveClient.getExceptionMessage(e), e);
				
			} 
			
			setCursor(Cursor.getDefaultCursor());
			
		}
	}
	
	
	class QueryThread extends Thread {

		private String xpath;

		private XQueryContext context;
		
		public QueryThread(String query) {
			super();
			this.xpath = query;
			this.context = null;
		}
		
		public boolean killQuery() {
			if(context!=null) {
				XQueryWatchDog xwd = context.getWatchDog();
				boolean retval = !xwd.isTerminating();
				if( retval )
					xwd.kill(0);
				context = null;

				return retval;
			}

			return false;
		}

		/**
		 * @see java.lang.Thread#run()
		 */
		public void run() {
			statusMessage.setText(Messages.getString("QueryDialog.processingquerymessage"));
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
				
				if (service instanceof LocalXPathQueryService && resource != null) {
                    ((LocalXPathQueryService) service).setModuleLoadPath(
                            XmldbURI.EMBEDDED_SERVER_URI_PREFIX + resource.getParentCollection().getName());
                }
				
				CompiledExpression compiled = service.compile(xpath);
				long t1 = System.currentTimeMillis();
				// Check could also be collection instanceof LocalCollection
				if(compiled instanceof CompiledXQuery)
					context = ((CompiledXQuery)compiled).getContext();
				tCompiled = t1 - t0;
				
				// In this way we can see the parsed structure meanwhile the query is
				StringWriter writer = new StringWriter();
				service.dump(compiled, writer);
				exprDisplay.setText(writer.toString());
				
				result = service.execute(compiled);
				context = null;
				tResult = System.currentTimeMillis() - t1;
				
				// jmfg: Is this still needed? I don't think so
				writer = new StringWriter();
				service.dump(compiled, writer);
				exprDisplay.setText(writer.toString());
				
				statusMessage.setText(Messages.getString("QueryDialog.retrievingmessage"));
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
								Messages.getString("QueryDialog.retrievalerrormessage")+": "
								+ InteractiveClient.getExceptionMessage(e), e);
						if (select == 3) break;
					}
				}
				resultTabs.setSelectedComponent(resultDisplay);
				resultDisplay.setText(contents.toString());
				resultDisplay.setCaretPosition(0);
				resultDisplay.scrollToCaret();
				statusMessage.setText(Messages.getString("QueryDialog.Found")+" " + result.getSize() + " "+Messages.getString("QueryDialog.items")+"." + 
					" "+Messages.getString("QueryDialog.Compilation")+": " + tCompiled + "ms, "+Messages.getString("QueryDialog.Execution")+": " + tResult+"ms");
			} catch (Throwable e) {
				statusMessage.setText(Messages.getString("QueryDialog.Error")+": "+InteractiveClient.getExceptionMessage(e)+". "+Messages.getString("QueryDialog.Compilation")+": " + tCompiled + "ms, "+Messages.getString("QueryDialog.Execution")+": " + tResult+"ms");
			    progress.setVisible(false);
			    
			
				ClientFrame.showErrorMessageQuery(
						Messages.getString("QueryDialog.queryrunerrormessage")+": "
						+ InteractiveClient.getExceptionMessage(e), e);
			} finally {
                                if(context != null) {
                                    context.cleanupBinaryValueInstances();
                                }
				context = null;
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
			killButton.setEnabled(false);
			submitButton.setEnabled(true);
		}
	}
	
	private void addQuery(String query) {
		if(query.length() > 40)
			query = query.substring(0, 40);
		history.addElement(Integer.toString(history.getSize()+1) + ". " + query);
	}
}
