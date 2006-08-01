/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 *  $Id$
 */
package org.exist.backup;

import java.awt.Dimension;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.Date;
import java.util.Properties;

import javax.swing.JFrame;
import javax.xml.transform.OutputKeys;

import org.exist.security.Permission;
import org.exist.storage.NativeBroker;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.xmldb.CollectionImpl;
import org.exist.xmldb.EXistResource;
import org.exist.xmldb.UserManagementService;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.util.URIUtils;
import org.exist.xquery.value.DateTimeValue;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

public class Backup {

	private String backupDir;
	private XmldbURI rootCollection;
	private String user;
	private String pass;

	private static final int currVersion = 1;
	
	public final static String NS = "http://exist.sourceforge.net/NS/exist";

	public Properties defaultOutputProperties = new Properties();
	{
		defaultOutputProperties.setProperty(OutputKeys.INDENT, "no");
		defaultOutputProperties.setProperty(OutputKeys.ENCODING, "UTF-8");
		defaultOutputProperties.setProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
		defaultOutputProperties.setProperty(EXistOutputKeys.EXPAND_XINCLUDES, "no");
		defaultOutputProperties.setProperty(EXistOutputKeys.PROCESS_XSL_PI, "no");
	}
		
	public Properties contentsOutputProps = new Properties();
	{
	    contentsOutputProps.setProperty(OutputKeys.INDENT, "yes");
    }
    
	public Backup(String user, String pass, String backupDir, XmldbURI rootCollection) {
		this.user = user;
		this.pass = pass;
		this.backupDir = backupDir;
		this.rootCollection = rootCollection;		
	}

	public Backup(String user, String pass, String backupDir) {
		this(user, pass, backupDir, XmldbURI.create("xmldb:exist://").append(XmldbURI.ROOT_COLLECTION_URI));
	}
	
	public Backup(String user, String pass, String backupDir, XmldbURI rootCollection, Properties property) {
		this(user, pass, backupDir, rootCollection);
		this.defaultOutputProperties.setProperty(OutputKeys.INDENT, property.getProperty("indent","no"));
	}

	public String encode(String enco) {		
		StringBuffer out = new StringBuffer();
		char t;
		for (int y=0; y < enco.length(); y++) {
			t= enco.charAt(y);
			if (t == '"') {
	            out.append("&22;");
			} else if (t == '&') {
				out.append("&26;");
			} else if (t == '*') {
				out.append("&2A;");
	        } else if (t ==':') {
	        	out.append("&3A;");
	        } else if (t =='<') {
	        	out.append("&3C;");
	        } else if (t =='>') {
	        	out.append("&3E;");
	        } else if (t =='?') {
	        	out.append("&3F;");
	        } else if (t =='\\') {
	        	out.append("&5C;");
	        } else if (t =='|') {
	        	out.append("&7C;");
	        } else {
	        	out.append(t);
	        }
		}		
		return out.toString();
	}
	
	
	public String decode(String enco) {
		StringBuffer out = new StringBuffer();
		String temp="";
		char t;
		for (int y=0; y < enco.length(); y++) {
			t= enco.charAt(y);
			if (t != '&') {
				out.append(t);
			}
			else {
			    temp = enco.substring(y,y+4);
                if (temp.equals("&22;")) {
                	out.append('"');
                } else if (temp.equals("&26;")) {
                	out.append('&');
                } else if (temp.equals("&2A;")) {
                	out.append('*');
                } else if (temp.equals("&3A;")) {
                	out.append(':');
                } else if (temp.equals("&3C;")) {
                	out.append('<');
                } else if (temp.equals("&3E;")) {
                	out.append(">");
                } else if (temp.equals("&3F;")) {
                	out.append('?');
                } else if (temp.equals("&5C;")) {
                	out.append('\\');
                } else if (temp.equals("&7C;")) {
                	out.append('|');
                } else {
                	}			    
			    y=y+3;
			}			
		}		
		return out.toString();
	}
	
	public void backup(boolean guiMode, JFrame parent) throws XMLDBException, IOException, SAXException {
		Collection current = DatabaseManager.getCollection(rootCollection.toString(), user, pass);
		if (guiMode) {
			BackupDialog dialog = new BackupDialog(parent, false);
			dialog.setSize(new Dimension(350, 150));
			dialog.setVisible(true);
			BackupThread thread = new BackupThread(current, dialog);
			thread.start();
			if(parent == null) {
				// if backup runs as a single dialog, wait for it (or app will terminate)
				while (thread.isAlive()) {
					synchronized (this) {
						try {
							wait(20);
						} catch (InterruptedException e) {
						}
					}
				}
			}
		} else
			backup(current, null);
	}

	private void backup(Collection current, BackupDialog dialog)
		throws XMLDBException, IOException, SAXException {
		if (current == null)
			return;
		
		current.setProperty(OutputKeys.ENCODING, defaultOutputProperties.getProperty(OutputKeys.ENCODING));
		current.setProperty(OutputKeys.INDENT, defaultOutputProperties.getProperty(OutputKeys.INDENT));
		current.setProperty(EXistOutputKeys.EXPAND_XINCLUDES,  defaultOutputProperties.getProperty(EXistOutputKeys.EXPAND_XINCLUDES));
		current.setProperty(EXistOutputKeys.PROCESS_XSL_PI,  defaultOutputProperties.getProperty(EXistOutputKeys.PROCESS_XSL_PI));
		
		// get resources and permissions
		String[] resources = current.listResources();
		Arrays.sort(resources);
        
//      TODO : use dedicated function in XmldbURI
		String cname = current.getName();
		if (cname.charAt(0) != '/')
			cname = "/" + cname;
		String path = backupDir + encode(URIUtils.urlDecodeUtf8(cname));
		
		UserManagementService mgtService =
			(UserManagementService) current.getService("UserManagementService", "1.0");
		Permission perms[] = mgtService.listResourcePermissions();
		Permission currentPerms = mgtService.getPermissions(current);

		if (dialog != null) {
			dialog.setCollection(current.getName());
			dialog.setResourceCount(resources.length);
		}
		// create directory and open __contents__.xml
		File file = new File(path);
		if(file.exists()) {
			System.out.println("removing " + path);
			file.delete();
		}
		file.mkdirs();
		BufferedWriter contents =
			new BufferedWriter(
				new OutputStreamWriter(
					new FileOutputStream(path + File.separatorChar + "__contents__.xml"), "UTF-8"));
		// serializer writes to __contents__.xml
		SAXSerializer serializer = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);
		serializer.setOutput(contents, contentsOutputProps);
		
		serializer.startDocument();
		serializer.startPrefixMapping("", NS);
		// write <collection> element
		CollectionImpl cur = (CollectionImpl)current;
		AttributesImpl attr = new AttributesImpl();
		//The name should have come from an XmldbURI.toString() call
		attr.addAttribute(NS, "name", "name", "CDATA", current.getName());
		attr.addAttribute(NS, "owner", "owner", "CDATA", currentPerms.getOwner());
		attr.addAttribute(NS, "group", "group", "CDATA", currentPerms.getOwnerGroup());
		attr.addAttribute(
			NS,
			"mode",
			"mode",
			"CDATA",
			Integer.toOctalString(currentPerms.getPermissions()));
		attr.addAttribute(
				NS,
				"created",
				"created",
				"CDATA",
				""+new DateTimeValue(cur.getCreationTime()));
		attr.addAttribute(NS, "version", "version", "CDATA", String.valueOf(currVersion));
		serializer.startElement(NS, "collection", "collection", attr);

		// scan through resources
		Resource resource;
		FileOutputStream os;
		BufferedWriter writer;
		SAXSerializer contentSerializer;
		for (int i = 0; i < resources.length; i++) {
            try {
                resource = current.getResource(resources[i]);
                file = new File(path);
                if (!file.exists())
                    file.mkdirs();
                if (dialog == null)
                    System.out.println("writing " + path + File.separatorChar + resources[i]);
                else {
                    dialog.setResource(resources[i]);
                    dialog.setProgress(i);
                }
                //os = new FileOutputStream(path + File.eparatorChar + resources[i]);
                os = new FileOutputStream(path + File.separatorChar + encode(URIUtils.urlDecodeUtf8(resources[i])));
                if(resource.getResourceType().equals("BinaryResource")) {
                    byte[] bdata = (byte[])resource.getContent();
                    os.write(bdata);
                    os.close();
                } else {
                    try {
                        writer =
                            new BufferedWriter(
                                    new OutputStreamWriter(os, "UTF-8"));
                        // write resource to contentSerializer
                        contentSerializer = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);
                        contentSerializer.setOutput(writer, defaultOutputProperties);
                        ((EXistResource)resource).setLexicalHandler(contentSerializer);
                        ((XMLResource)resource).getContentAsSAX(contentSerializer);
                        SerializerPool.getInstance().returnObject(contentSerializer);
                        writer.close();
                    } catch(Exception e) {
                        System.err.println("An exception occurred while writing the resource: " + e.getMessage());
                        e.printStackTrace();
                        continue;
                    }
                }
                EXistResource ris = (EXistResource)resource;
                
                //store permissions
                attr.clear();
                attr.addAttribute(NS, "type", "type", "CDATA", resource.getResourceType());
                attr.addAttribute(NS, "name", "name", "CDATA", resources[i]);
                attr.addAttribute(NS, "owner", "owner", "CDATA", perms[i].getOwner());
                attr.addAttribute(NS, "group", "group", "CDATA", perms[i].getOwnerGroup());
                attr.addAttribute(
                        NS,
                        "mode",
                        "mode",
                        "CDATA",
                        Integer.toOctalString(perms[i].getPermissions()));
                Date date = ris.getCreationTime();
                if (date != null)
                    attr.addAttribute(
                            NS,
                            "created",
                            "created",
                            "CDATA",
                            ""+new DateTimeValue(date));
                date = ris.getLastModificationTime();
                if (date != null)
                    attr.addAttribute(
                            NS,
                            "modified",
                            "modified",
                            "CDATA",
                            ""+new DateTimeValue(date));
                
                attr.addAttribute(
                        NS,
                        "filename",
                        "filename",
                        "CDATA",
                        encode( URIUtils.urlDecodeUtf8(resources[i]) )
                );
                attr.addAttribute(
                        NS,
                        "mimetype",
                        "mimetype",
                        "CDATA",
                        encode( ((EXistResource)resource).getMimeType())
                );
                
                if (!resource.getResourceType().equals("BinaryResource")) {
					if (ris.getDocType() != null) {
						if (ris.getDocType().getName() != null) {
							attr.addAttribute(NS, "namedoctype", "namedoctype",
									"CDATA", ris.getDocType().getName());
						}
						if (ris.getDocType().getPublicId() != null) {
							attr.addAttribute(NS, "publicid", "publicid",
									"CDATA", ris.getDocType().getPublicId());
						}
						if (ris.getDocType().getSystemId() != null) {
							attr.addAttribute(NS, "systemid", "systemid",
									"CDATA", ris.getDocType().getSystemId());
						}					
					}
				}
                serializer.startElement(NS, "resource", "resource", attr);
                serializer.endElement(NS, "resource", "resource");
            } catch(XMLDBException e) {
                System.err.println("Failed to backup resource " + resources[i] + " from collection " + current.getName());
            }
      }
		// write subcollections
		String[] collections = current.listChildCollections();
		for (int i = 0; i < collections.length; i++) {
			if (current.getName().equals(NativeBroker.SYSTEM_COLLECTION) && collections[i].equals("temp"))
				continue;
			attr.clear();
			attr.addAttribute(NS, "name", "name", "CDATA", collections[i]);
			attr.addAttribute(NS, "filename", "filename", "CDATA", encode(URIUtils.urlDecodeUtf8(collections[i])));
			serializer.startElement(NS, "subcollection", "subcollection", attr);
			serializer.endElement(NS, "subcollection", "subcollection");
		}
		// close <collection>
		serializer.endElement(NS, "collection", "collection");
		serializer.endPrefixMapping("");
		serializer.endDocument();
		contents.close();
		SerializerPool.getInstance().returnObject(serializer);
		// descend into subcollections
		Collection child;
		for (int i = 0; i < collections.length; i++) {
			child = current.getChildCollection(collections[i]);
			if (child.getName().equals(NativeBroker.TEMP_COLLECTION))
				continue;
			backup(child, dialog);
		}
	}

	class BackupThread extends Thread {

		Collection collection_;
		BackupDialog dialog_;
		
		public BackupThread(Collection collection, BackupDialog dialog) {
			super();
			collection_ = collection;
			dialog_ = dialog;
		}

		public void run() {
			try {
				backup(collection_, dialog_);
				dialog_.setVisible(false);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public static void main(String args[]) {
		try {
			Class cl = Class.forName("org.exist.xmldb.DatabaseImpl");
			Database database = (Database) cl.newInstance();
			database.setProperty("create-database", "true");
			DatabaseManager.registerDatabase(database);
			Backup backup = new Backup("admin", null, "backup", URIUtils.encodeXmldbUriFor(args[0]));
			backup.backup(false, null);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
