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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import org.apache.xml.serialize.OutputFormat;
import org.apache.xml.serialize.XMLSerializer;
import org.exist.security.Permission;
import org.exist.xmldb.UserManagementService;
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
	private String rootCollection;
	private String user;
	private String pass;

    public final static String NS = "http://exist.sourceforge.net/NS/exist";
    
	public Backup(
		String user,
		String pass,
		String backupDir,
		String rootCollection) {
		this.user = user;
		this.pass = pass;
		this.backupDir = backupDir;
		this.rootCollection = rootCollection;
        System.out.println("using root collection: " + rootCollection);
	}

	public Backup(String user, String pass, String backupDir) {
		this(user, pass, backupDir, "xmldb:exist:///db");
	}

	public void backup() throws XMLDBException, IOException, SAXException {
		Collection current =
			DatabaseManager.getCollection(rootCollection, user, pass);
		backup(current);
	}

	public void backup(Collection current)
		throws XMLDBException, IOException, SAXException {
		if (current == null)
			return;
		current.setProperty("encoding", "UTF-8");
		String[] resources = current.listResources();
		String path = backupDir + current.getName();
		UserManagementService mgtService =
			(UserManagementService) current.getService(
				"UserManagementService",
				"1.0");
		Permission perms[] = mgtService.listResourcePermissions();
		Permission currentPerms = mgtService.getPermissions(current);

		File file = new File(path);
		if (!file.exists())
			file.mkdirs();
		BufferedWriter contents =
			new BufferedWriter(
				new OutputStreamWriter(
					new FileOutputStream(path + '/' + "__contents__.xml"),
					"UTF-8"));
		OutputFormat format = new OutputFormat("xml", "UTF-8", true);
		XMLSerializer serializer = new XMLSerializer(contents, format);
		serializer.startDocument();
        serializer.startPrefixMapping("", NS);
		AttributesImpl attr = new AttributesImpl();
		attr.addAttribute(NS, "name", "name", "CDATA", current.getName());
		attr.addAttribute(
			NS,
			"owner",
			"owner",
			"CDATA",
			currentPerms.getOwner());
		attr.addAttribute(
			NS,
			"group",
			"group",
			"CDATA",
			currentPerms.getOwnerGroup());
		attr.addAttribute(
			NS,
			"mode",
			"mode",
			"CDATA",
			Integer.toOctalString(currentPerms.getPermissions()));
		serializer.startElement(NS, "collection", "collection", attr);
        
        XMLResource resource;
        FileOutputStream os;
        BufferedWriter writer;
        XMLSerializer contentSerializer;
		for (int i = 0; i < resources.length; i++) {
			resource = (XMLResource)current.getResource(resources[i]);
			file = new File(path);
			if (!file.exists())
				file.mkdirs();
			System.out.println("writing " + path + '/' + resources[i]);
			writer = new BufferedWriter(
                new OutputStreamWriter(
                    new FileOutputStream(path + '/' + resources[i]), 
                    "UTF-8"
                )
            );
            contentSerializer = new XMLSerializer(writer, format);
			resource.getContentAsSAX(contentSerializer);
			writer.close();
			// store permissions
			attr.clear();
			attr.addAttribute(NS, "name", "name", "CDATA", resources[i]);
			attr.addAttribute(
				NS,
				"owner",
				"owner",
				"CDATA",
				perms[i].getOwner());
			attr.addAttribute(
				NS,
				"group",
				"group",
				"CDATA",
				perms[i].getOwnerGroup());
			attr.addAttribute(
				NS,
				"mode",
				"mode",
				"CDATA",
				Integer.toOctalString(perms[i].getPermissions()));
			serializer.startElement(NS, "resource", "resource", attr);
			serializer.endElement(NS, "resource", "resource");
		}
        String[] collections = current.listChildCollections();
        for (int i = 0; i < collections.length; i++) {
            attr.clear();
            attr.addAttribute(NS, "name", "name", "CDATA", collections[i]);
            serializer.startElement(NS, "subcollection", "subcollection", attr);
            serializer.endElement(NS, "subcollection", "subcollection");
        }
        serializer.endElement(NS, "collection", "collection");
        serializer.endPrefixMapping("");
        serializer.endDocument();
        contents.close();
		Collection child;
		for (int i = 0; i < collections.length; i++) {
			child = current.getChildCollection(collections[i]);
			backup(child);
		}
	}

	public static void main(String args[]) {
		try {
			Class cl = Class.forName("org.exist.xmldb.DatabaseImpl");
			Database database = (Database) cl.newInstance();
			database.setProperty("create-database", "true");
			DatabaseManager.registerDatabase(database);
			Backup backup = new Backup("admin", null, "backup", args[0]);
			backup.backup();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
