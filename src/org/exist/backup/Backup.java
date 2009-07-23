/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2006 The eXist team
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software Foundation
 *  Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 *  $Id$
 */
package org.exist.backup;

import org.exist.Namespaces;
import org.exist.security.Permission;
import org.exist.storage.DBBroker;
import org.exist.storage.NativeBroker;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.xmldb.CollectionImpl;
import org.exist.xmldb.EXistResource;
import org.exist.xmldb.ExtendedResource;
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

import javax.swing.*;
import javax.xml.transform.OutputKeys;
import java.awt.*;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.Date;
import java.util.Properties;

public class Backup {

	private String target;
	private XmldbURI rootCollection;
	private String user;
	private String pass;

	private static final int currVersion = 1;
	
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
    
	public Backup(String user, String pass, String target, XmldbURI rootCollection) {
		this.user = user;
		this.pass = pass;
		this.target = target;
		this.rootCollection = rootCollection;		
	}

	public Backup(String user, String pass, String target) {
		this(user, pass, target, XmldbURI.create("xmldb:exist://" + DBBroker.ROOT_COLLECTION));
	}
	
	public Backup(String user, String pass, String target, XmldbURI rootCollection, Properties property) {
		this(user, pass, target, rootCollection);
		this.defaultOutputProperties.setProperty(OutputKeys.INDENT, property.getProperty("indent","no"));
	}

	public static String encode(String enco) {
		StringBuilder out = new StringBuilder();
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
	
	
	public static String decode(String enco) {
		StringBuilder out = new StringBuilder();
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
        String cname = current.getName();
		if (cname.charAt(0) != '/')
			cname = "/" + cname;
        String path = target + encode(URIUtils.urlDecodeUtf8(cname));
        BackupWriter output;
        if (target.endsWith(".zip"))
            output = new ZipWriter(target, encode(URIUtils.urlDecodeUtf8(cname)));
        else
            output = new FileSystemWriter(path);
        backup(current, output, dialog);
        output.close();
    }

    private void backup(Collection current, BackupWriter output, BackupDialog dialog)
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
		
		UserManagementService mgtService =
			(UserManagementService) current.getService("UserManagementService", "1.0");
		Permission perms[] = mgtService.listResourcePermissions();
		Permission currentPerms = mgtService.getPermissions(current);

		if (dialog != null) {
			dialog.setCollection(current.getName());
			dialog.setResourceCount(resources.length);
		}
        Writer contents = output.newContents();
		// serializer writes to __contents__.xml
		SAXSerializer serializer = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);
		serializer.setOutput(contents, contentsOutputProps);
		
		serializer.startDocument();
		serializer.startPrefixMapping("", Namespaces.EXIST_NS);
		// write <collection> element
		CollectionImpl cur = (CollectionImpl)current;
		AttributesImpl attr = new AttributesImpl();
		//The name should have come from an XmldbURI.toString() call
		attr.addAttribute(Namespaces.EXIST_NS, "name", "name", "CDATA", current.getName());
		attr.addAttribute(Namespaces.EXIST_NS, "owner", "owner", "CDATA", currentPerms.getOwner());
		attr.addAttribute(Namespaces.EXIST_NS, "group", "group", "CDATA", currentPerms.getOwnerGroup());
		attr.addAttribute(
				Namespaces.EXIST_NS,
			"mode",
			"mode",
			"CDATA",
			Integer.toOctalString(currentPerms.getPermissions()));
		attr.addAttribute(
				Namespaces.EXIST_NS,
				"created",
				"created",
				"CDATA",
				""+new DateTimeValue(cur.getCreationTime()));
		attr.addAttribute(Namespaces.EXIST_NS, "version", "version", "CDATA", String.valueOf(currVersion));
		serializer.startElement(Namespaces.EXIST_NS, "collection", "collection", attr);

		// scan through resources
		Resource resource;
		OutputStream os;
		BufferedWriter writer;
		SAXSerializer contentSerializer;
		for (int i = 0; i < resources.length; i++) {
            try {
                if (resources[i].equals("__contents__.xml")) {
                    //Skipping resources[i]
                    continue;
                }
                resource = current.getResource(resources[i]);

                if (dialog != null) {
                    dialog.setResource(resources[i]);
                    dialog.setProgress(i);
                }

                os = output.newEntry(encode(URIUtils.urlDecodeUtf8(resources[i])));
                if(resource instanceof ExtendedResource) {
                    ((ExtendedResource)resource).getContentIntoAStream(os);
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
                        writer.flush();
                    } catch(Exception e) {
                        System.err.println("An exception occurred while writing the resource: " + e.getMessage());
                        e.printStackTrace();
                        continue;
                    }
                }
                output.closeEntry();
                EXistResource ris = (EXistResource)resource;
                
                //store permissions
                attr.clear();
                attr.addAttribute(Namespaces.EXIST_NS, "type", "type", "CDATA", resource.getResourceType());
                attr.addAttribute(Namespaces.EXIST_NS, "name", "name", "CDATA", resources[i]);
                attr.addAttribute(Namespaces.EXIST_NS, "owner", "owner", "CDATA", perms[i].getOwner());
                attr.addAttribute(Namespaces.EXIST_NS, "group", "group", "CDATA", perms[i].getOwnerGroup());
                attr.addAttribute(
                		Namespaces.EXIST_NS,
                        "mode",
                        "mode",
                        "CDATA",
                        Integer.toOctalString(perms[i].getPermissions()));
                Date date = ris.getCreationTime();
                if (date != null)
                    attr.addAttribute(
                    		Namespaces.EXIST_NS,
                            "created",
                            "created",
                            "CDATA",
                            ""+new DateTimeValue(date));
                date = ris.getLastModificationTime();
                if (date != null)
                    attr.addAttribute(
                    		Namespaces.EXIST_NS,
                            "modified",
                            "modified",
                            "CDATA",
                            ""+new DateTimeValue(date));
                
                attr.addAttribute(
                		Namespaces.EXIST_NS,
                        "filename",
                        "filename",
                        "CDATA",
                        encode( URIUtils.urlDecodeUtf8(resources[i]) )
                );
                attr.addAttribute(
                		Namespaces.EXIST_NS,
                        "mimetype",
                        "mimetype",
                        "CDATA",
                        encode( ((EXistResource)resource).getMimeType())
                );
                
                if (!resource.getResourceType().equals("BinaryResource")) {
					if (ris.getDocType() != null) {
						if (ris.getDocType().getName() != null) {
							attr.addAttribute(Namespaces.EXIST_NS, "namedoctype", "namedoctype",
									"CDATA", ris.getDocType().getName());
						}
						if (ris.getDocType().getPublicId() != null) {
							attr.addAttribute(Namespaces.EXIST_NS, "publicid", "publicid",
									"CDATA", ris.getDocType().getPublicId());
						}
						if (ris.getDocType().getSystemId() != null) {
							attr.addAttribute(Namespaces.EXIST_NS, "systemid", "systemid",
									"CDATA", ris.getDocType().getSystemId());
						}					
					}
				}
                serializer.startElement(Namespaces.EXIST_NS, "resource", "resource", attr);
                serializer.endElement(Namespaces.EXIST_NS, "resource", "resource");
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
			attr.addAttribute(Namespaces.EXIST_NS, "name", "name", "CDATA", collections[i]);
			attr.addAttribute(Namespaces.EXIST_NS, "filename", "filename", "CDATA", encode(URIUtils.urlDecodeUtf8(collections[i])));
			serializer.startElement(Namespaces.EXIST_NS, "subcollection", "subcollection", attr);
			serializer.endElement(Namespaces.EXIST_NS, "subcollection", "subcollection");
		}
		// close <collection>
		serializer.endElement(Namespaces.EXIST_NS, "collection", "collection");
		serializer.endPrefixMapping("");
		serializer.endDocument();
		output.closeContents();

        SerializerPool.getInstance().returnObject(serializer);
		// descend into subcollections
		Collection child;
		for (int i = 0; i < collections.length; i++) {
			child = current.getChildCollection(collections[i]);
			if (child.getName().equals(NativeBroker.TEMP_COLLECTION))
				continue;
            output.newCollection(encode(URIUtils.urlDecodeUtf8(collections[i])));
            backup(child, output, dialog);
            output.closeCollection();
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
