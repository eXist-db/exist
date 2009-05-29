package org.exist.backup;

import org.exist.Namespaces;
import org.exist.dom.DocumentTypeImpl;
import org.exist.security.SecurityManager;
import org.exist.security.User;
import org.exist.storage.DBBroker;
import org.exist.util.EXistInputSource;
import org.exist.xmldb.CollectionImpl;
import org.exist.xmldb.CollectionManagementServiceImpl;
import org.exist.xmldb.EXistResource;
import org.exist.xmldb.UserManagementService;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.util.URIUtils;
import org.exist.xquery.value.DateTimeValue;
import org.w3c.dom.DocumentType;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;

import javax.swing.*;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.Observable;
import java.util.Stack;
import java.util.Properties;


/**
 * Restore.java
 * 
 * @author Wolfgang Meier
 */
public class Restore extends DefaultHandler {

	private BackupDescriptor contents;
	private String uri;
	private String username;
	private String pass;
	private XMLReader reader;
	private CollectionImpl current;
	private Stack stack = new Stack();
	private RestoreDialog dialog = null;
	private int version=0;
	private RestoreListener listener;

	private static final int strictUriVersion = 1;

	/**
	 * Constructor for Restore.
	 * @throws XMLDBException 
	 * @throws URISyntaxException 
	 */
	public Restore(String user, String pass, String newAdminPass, File contents, String uri)
		throws ParserConfigurationException, SAXException, XMLDBException, URISyntaxException {
		this.username = user;
		this.pass = pass;
		this.uri = uri;

      this.listener = new DefaultListener();

		if (newAdminPass != null)
			setAdminCredentials(newAdminPass);
		
		SAXParserFactory saxFactory = SAXParserFactory.newInstance();
		saxFactory.setNamespaceAware(true);
		saxFactory.setValidating(false);
		SAXParser sax = saxFactory.newSAXParser();
		reader = sax.getXMLReader();
		reader.setContentHandler(this);


        do {
            BackupDescriptor bd=null;
            Properties properties = null;
            try {
                if(contents.isDirectory()) {
                    bd=new FileSystemBackupDescriptor(new File(contents,BackupDescriptor.COLLECTION_DESCRIPTOR));
                } else if(contents.getName().endsWith(".zip") || contents.getName().endsWith(".ZIP")) {
                    bd=new ZipArchiveBackupDescriptor(contents);
                } else {
                    bd=new FileSystemBackupDescriptor(contents);
                }
                properties = bd.getProperties();
            } catch(Exception e) {
                e.printStackTrace();
                throw new SAXException("Unable to create backup descriptor object from "+contents,e);
            }

            stack.push(bd);

            // check if the system collection is in the backup. We have to process
            // this first to create users.
            //TODO : find a way to make a corespondance with DBRoker's named constants
            BackupDescriptor sysbd=bd.getChildBackupDescriptor("system");
            if (sysbd!=null) {
                stack.push(sysbd);
            }

            contents = null;
            if (properties != null && properties.getProperty("incremental", "no").equals("yes")) {
                String previous = properties.getProperty("previous", "");
                if (previous.length() > 0) {
                    contents = new File(bd.getParentDir(), previous);
                    if (!contents.canRead())
                        throw new SAXException("Required part of incremental backup not found: " + contents.getAbsolutePath());
                }
            }
        } while (contents != null);
    }

   public void setListener(RestoreListener listener) {
      this.listener = listener;
   }
   
	public void restore(boolean showGUI, JFrame parent)
		throws XMLDBException, FileNotFoundException, IOException, SAXException {
		if (showGUI) {
			dialog = new RestoreDialog(parent, "Restoring data ...", false);
			dialog.setVisible(true);
			Thread restoreThread = new Thread() {
				public void run() {
					while (!stack.isEmpty()) {
						try {
							contents = (BackupDescriptor) stack.pop();
                            dialog.setBackup(contents.getSymbolicPath());
							reader.parse(contents.getInputSource());
						} catch (FileNotFoundException e) {
							dialog.displayMessage(e.getMessage());
						} catch (IOException e) {
							dialog.displayMessage(e.getMessage());
						} catch (SAXException e) {
							dialog.displayMessage(e.getMessage());
						}
					}
					dialog.setVisible(false);
				}
			};
			restoreThread.start();
			if(parent == null) {
				while (restoreThread.isAlive()) {
					synchronized (this) {
						try {
							wait(20);
						} catch (InterruptedException e) {
						}
					}
				}
			}
		} else {
			while(!stack.isEmpty()) {
				contents = (BackupDescriptor) stack.pop();
				EXistInputSource is = contents.getInputSource();
				is.setEncoding("UTF-8");
				//restoring sysId
				reader.parse(is);
			}
		}
	}

	/**
	 * @see org.xml.sax.ContentHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
	 */
	public void startElement(String namespaceURI, String localName, String qName, Attributes atts)
		throws SAXException {
		if (namespaceURI.equals(Namespaces.EXIST_NS)) {
			if (localName.equals("collection")) {
				final String name = atts.getValue("name");
				final String owner = atts.getValue("owner");
				final String group = atts.getValue("group");
				final String mode = atts.getValue("mode");
				final String created = atts.getValue("created");
				String strVersion = atts.getValue("version");
				if(strVersion!=null) {
					try {
						this.version = Integer.parseInt(strVersion);
					} catch (NumberFormatException e) {
						this.version=0;
					}
				}

				
				if (name == null)
					throw new SAXException("collection requires a name " + "attribute");
				try {
					listener.createCollection(name);
					XmldbURI collUri;
					if(version >= strictUriVersion) {
						collUri = XmldbURI.create(name);
					} else {
						try {
							collUri = URIUtils.encodeXmldbUriFor(name);
						} catch (URISyntaxException e) {
							listener.warn("Could not parse document name into a URI: "+e.getMessage());
							return;
						}
					}
					
					Date date_created = null;
					
					if (created != null)
						try {
							date_created = new DateTimeValue(created).getDate();
						} catch (XPathException e2) {
						} 

					 
					
					current = mkcol(collUri, date_created);
                    if (current == null)
                        throw new SAXException("Collection not found: " + collUri);
					UserManagementService service =
						(UserManagementService) current.getService("UserManagementService", "1.0");
					User u = new User(owner, null, group);
					service.chown(u, group);
					service.chmod(Integer.parseInt(mode, 8));
				} catch (Exception e) {
               listener.warn("An unrecoverable error occurred while restoring\ncollection '" + name + "'. " +
                       "Aborting restore!");
               e.printStackTrace();
					throw new SAXException(e.getMessage(), e);
				}
				if(dialog != null)
					dialog.setCollection(name);
			} else if (localName.equals("subcollection")) {
				
				 String name = atts.getValue("filename");
				
				if (name == null) {
					name = atts.getValue("name");
				}
				
				BackupDescriptor subbd=contents.getChildBackupDescriptor(name);
				if (subbd!=null)
					stack.push(subbd);
				else
					listener.warn(name + " does not exist or is not readable.");
            } else if (localName.equals("resource")) {
                String skip = atts.getValue("skip");
                if (skip == null || skip.equals("no")) {
                    String type = atts.getValue("type");
                    if(type == null)
                        type ="XMLResource";
                    final String name = atts.getValue("name");
                    final String owner = atts.getValue("owner");
                    final String group = atts.getValue("group");
                    final String perms = atts.getValue("mode");

                    String filename = atts.getValue("filename");
                    final String mimetype = atts.getValue("mimetype");
                    final String created = atts.getValue("created");
                    final String modified = atts.getValue("modified");

                    final String publicid = atts.getValue("publicid");
                    final String systemid = atts.getValue("systemid");
                    final String namedoctype = atts.getValue("namedoctype");

                    if (filename == null) filename = name;

                    if (name == null) {
                      listener.warn("Wrong entry in backup descriptor: resource requires a name attribute.");
                    }
                    XmldbURI docUri;
                    if(version >= strictUriVersion) {
                        docUri = XmldbURI.create(name);
                    } else {
                        try {
                            docUri = URIUtils.encodeXmldbUriFor(name);
                        } catch (URISyntaxException e) {
                            listener.warn("Could not parse document name into a URI: "+e.getMessage());
                            return;
                        }
                    }
                    EXistInputSource is=contents.getInputSource(filename);
		    if(is==null)
                       listener.warn("Failed to restore resource '" + name + "'\nfrom file '" +
                               contents.getSymbolicPath(name,false) + "'.\nReason: Unable to obtain its EXistInputSource");
                    try {
                        if (dialog != null && current instanceof Observable) {
                            ((Observable) current).addObserver(dialog.getObserver());
                        }
                        if(dialog != null)
                            dialog.setResource(name);
                        final Resource res =
                                current.createResource(docUri.toString(), type);
                        if (mimetype != null)
                            ((EXistResource)res).setMimeType(mimetype);

                        if(is.getByteStreamLength() > 0) {
                            res.setContent(is);
                        } else {
                            res.setContent("");
                        }

                        // Restoring name

                        Date date_created = null;
                        Date date_modified = null;

                        if (created != null)
                            try {
                              date_created = (new DateTimeValue(created)).getDate();
                            } catch (XPathException e2) {
                              listener.warn("Illegal creation date. Skipping ...");
                            }

                        if (modified != null)
                            try {
                                date_modified = (Date)(new DateTimeValue(modified)).getDate();
                            } catch (XPathException e2) {
                                listener.warn("Illegal modification date. Skipping ...");
                            }

                        current.storeResource(res, date_created, date_modified);


                        if (publicid != null  || systemid != null )
                        {
                            DocumentType doctype = new DocumentTypeImpl(namedoctype,publicid,systemid );
                            try {
                                ((EXistResource)res).setDocType(doctype);
                            } catch (XMLDBException e1) {
                                e1.printStackTrace();
                            }
                        }

                        UserManagementService service =
                                (UserManagementService) current.getService("UserManagementService", "1.0");
                        User u = new User(owner, null, group);
                        try {
                            service.chown(res, u, group);
                        } catch (XMLDBException e1) {
                           listener.warn("Failed to change owner on document '" + name + "'; skipping ...");
                        }
                        service.chmod(res, Integer.parseInt(perms, 8));
                       listener.restored(name);
                    } catch (Exception e) {
                       listener.warn("Failed to restore resource '" + name + "'\nfrom file '" +
                               contents.getSymbolicPath(name,false) + "'.\nReason: " + e.getMessage());
                    }
                }
            } else if (localName.equals("deleted")) {
                final String name = atts.getValue("name");
                final String type = atts.getValue("type");
                if (type.equals("collection")) {
                    try {
                        Collection child = current.getChildCollection(name);
                        if (child != null) {
                            CollectionManagementService cmgt = (CollectionManagementService)
                                    current.getService("CollectionManagementService", "1.0");
                            cmgt.removeCollection(name);
                        }
                    } catch (XMLDBException e) {
                      listener.warn("Failed to remove deleted collection: " + name + ": " + e.getMessage());
                    }
                } else if (type.equals("resource")) {
                    try {
                        Resource resource = current.getResource(name);
                        if (resource != null)
                            current.removeResource(resource);
                    } catch (XMLDBException e) {
                            listener.warn("Failed to remove deleted resource: " + name + ": " +
                                e.getMessage());
                    }
                }
            }
        }
	}

	private final CollectionImpl mkcol(XmldbURI collPath, Date created) throws XMLDBException, URISyntaxException {
		XmldbURI[] segments = collPath.getPathSegments();
		CollectionManagementServiceImpl mgtService;
		Collection c;
		XmldbURI dbUri;
        if (!uri.endsWith(DBBroker.ROOT_COLLECTION))
            dbUri = XmldbURI.xmldbUriFor(uri + DBBroker.ROOT_COLLECTION);
        else
            dbUri = XmldbURI.xmldbUriFor(uri);
		Collection current = DatabaseManager.getCollection(dbUri.toString(), username, pass);
		XmldbURI p = XmldbURI.ROOT_COLLECTION_URI;
		for(int i=1;i<segments.length;i++) {
			p = p.append(segments[i]);
			XmldbURI xmldbURI = dbUri.resolveCollectionPath(p);
			c = DatabaseManager.getCollection(xmldbURI.toString(), username, pass);
			if (c == null) {
				mgtService =
					(CollectionManagementServiceImpl) current.getService(
						"CollectionManagementService",
						"1.0");
				//current = mgtService.createCollection(token);
				current = mgtService.createCollection(segments[i], created);
			} else
				current = c;
		}
		return (CollectionImpl)current;
	}
    
	private void setAdminCredentials(String adminPassword) throws XMLDBException, URISyntaxException {
		XmldbURI dbUri;
		if (!uri.endsWith(DBBroker.ROOT_COLLECTION))
            dbUri = XmldbURI.xmldbUriFor(uri + DBBroker.ROOT_COLLECTION);
        else
            dbUri = XmldbURI.xmldbUriFor(uri);
		Collection root = DatabaseManager.getCollection(dbUri.toString(), username, pass);
		UserManagementService mgmt = (UserManagementService)
			root.getService("UserManagementService", "1.0");
		User dba = mgmt.getUser(SecurityManager.DBA_USER);
		dba.setPassword(adminPassword);
		mgmt.updateUser(dba);
		
		pass = adminPassword;
	}
	
	public static void showErrorMessage(String message) {
        JTextArea msgArea = new JTextArea(message);
        msgArea.setEditable(false);
        msgArea.setBackground(null);
        JScrollPane scroll = new JScrollPane(msgArea);
        JOptionPane optionPane = new JOptionPane();
        optionPane.setMessage(new Object[]{scroll});
        optionPane.setMessageType(JOptionPane.ERROR_MESSAGE);
        JDialog dialog = optionPane.createDialog(null, "Error");
        dialog.setResizable(true);
        dialog.pack();
        dialog.setVisible(true);
        return;
    }

   public interface RestoreListener {

      void createCollection(String collection);

      void restored(String resource);

      void info(String message);

      void warn(String message);
   }

   private class DefaultListener implements RestoreListener {

      public void createCollection(String collection) {
         info("creating collection " + collection);
      }

      public void restored(String resource) {
         info("restored " + resource);
      }

      public void info(String message) {
         if (dialog != null)
            dialog.displayMessage(message);
         else
            System.err.println(message);
      }

      public void warn(String message) {
         if (dialog != null)
            dialog.displayMessage(message);
         else
            System.err.println(message);
      }
   }
}
