/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001 Wolfgang M. Meier
 *  meier@ifs.tu-darmstadt.de
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
 *  $Id:
 */
package org.exist.backup;

import java.awt.Dimension;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import javax.swing.JFrame;

import org.apache.xml.serialize.OutputFormat;
import org.apache.xml.serialize.XMLSerializer;
import org.exist.security.Permission;
import org.exist.xmldb.UserManagementService;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

public class Backup {

	private String backupDir;
	private String rootCollection;
	private String user;
	private String pass;

	public final static String NS = "http://exist.sourceforge.net/NS/exist";

	public Backup(String user, String pass, String backupDir, String rootCollection) {
		this.user = user;
		this.pass = pass;
		this.backupDir = backupDir;
		this.rootCollection = rootCollection;
	}

	public Backup(String user, String pass, String backupDir) {
		this(user, pass, backupDir, "xmldb:exist:///db");
	}

	public void backup(boolean guiMode, JFrame parent) throws XMLDBException, IOException, SAXException {
		Collection current = DatabaseManager.getCollection(rootCollection, user, pass);
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
		current.setProperty("encoding", "UTF-8");

		// get resources and permissions
		String[] resources = current.listResources();
		String cname = current.getName();
		if (cname.charAt(0) != '/')
			cname = '/' + cname;
		String path = backupDir + cname;
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
					new FileOutputStream(path + '/' + "__contents__.xml"),
					"UTF-8"));
		OutputFormat format = new OutputFormat("xml", "UTF-8", true);
		// serializer writes to __contents__.xml
		XMLSerializer serializer = new XMLSerializer(contents, format);
		serializer.startDocument();
		serializer.startPrefixMapping("", NS);
		// write <collection> element
		AttributesImpl attr = new AttributesImpl();
		attr.addAttribute(NS, "name", "name", "CDATA", current.getName());
		attr.addAttribute(NS, "owner", "owner", "CDATA", currentPerms.getOwner());
		attr.addAttribute(NS, "group", "group", "CDATA", currentPerms.getOwnerGroup());
		attr.addAttribute(
			NS,
			"mode",
			"mode",
			"CDATA",
			Integer.toOctalString(currentPerms.getPermissions()));
		serializer.startElement(NS, "collection", "collection", attr);

		// scan through resources
		XMLResource resource;
		FileOutputStream os;
		BufferedWriter writer;
		XMLSerializer contentSerializer;
		for (int i = 0; i < resources.length; i++) {
			resource = (XMLResource) current.getResource(resources[i]);
			file = new File(path);
			if (!file.exists())
				file.mkdirs();
			if (dialog == null)
				System.out.println("writing " + path + '/' + resources[i]);
			else {
				dialog.setResource(resources[i]);
				dialog.setProgress(i);
			}
			writer =
				new BufferedWriter(
					new OutputStreamWriter(
						new FileOutputStream(path + '/' + resources[i]),
						"UTF-8"));
			// write resource to contentSerializer
			contentSerializer = new XMLSerializer(writer, format);
			resource.getContentAsSAX(contentSerializer);
			writer.close();
			// store permissions
			attr.clear();
			attr.addAttribute(NS, "name", "name", "CDATA", resources[i]);
			attr.addAttribute(NS, "owner", "owner", "CDATA", perms[i].getOwner());
			attr.addAttribute(NS, "group", "group", "CDATA", perms[i].getOwnerGroup());
			attr.addAttribute(
				NS,
				"mode",
				"mode",
				"CDATA",
				Integer.toOctalString(perms[i].getPermissions()));
			serializer.startElement(NS, "resource", "resource", attr);
			serializer.endElement(NS, "resource", "resource");
		}
		// write subcollections
		String[] collections = current.listChildCollections();
		for (int i = 0; i < collections.length; i++) {
			if (current.getName().equals("db") && collections[i].equals("system"))
				continue;
			attr.clear();
			attr.addAttribute(NS, "name", "name", "CDATA", collections[i]);
			serializer.startElement(NS, "subcollection", "subcollection", attr);
			serializer.endElement(NS, "subcollection", "subcollection");
		}
		// close <collection>
		serializer.endElement(NS, "collection", "collection");
		serializer.endPrefixMapping("");
		serializer.endDocument();
		contents.close();
		// descend into subcollections
		Collection child;
		for (int i = 0; i < collections.length; i++) {
			child = current.getChildCollection(collections[i]);
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
			}
		}
	}

	public static void main(String args[]) {
		try {
			Class cl = Class.forName("org.exist.xmldb.DatabaseImpl");
			Database database = (Database) cl.newInstance();
			database.setProperty("create-database", "true");
			DatabaseManager.registerDatabase(database);
			Backup backup = new Backup("admin", null, "backup", args[0]);
			backup.backup(false, null);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
