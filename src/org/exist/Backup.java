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
package org.exist;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;
import java.io.IOException;

import org.exist.security.Permission;
import org.exist.xmldb.UserManagementService;
import org.xmldb.api.*;
import org.xmldb.api.base.*;

public class Backup {
	
	private String backupDir;
	private String rootCollection;
	private String user;
	private String pass;
	
	public Backup( String user, String pass, 
		String backupDir, String rootCollection ) {
		this.user = user;
		this.pass = pass;
		this.backupDir = backupDir;
		this.rootCollection = rootCollection;
	}
	
	public Backup( String user, String pass, String backupDir ) {
		this(user, pass, backupDir, "xmldb:exist:///db");
	}
	
	public void backup() throws XMLDBException, IOException {
		Collection current =
			DatabaseManager.getCollection(rootCollection, user, pass);
		backup(current);
	}
	
	public void backup(Collection current) 
		throws XMLDBException, IOException {
		if(current == null)
			return;
		current.setProperty("encoding", "UTF-8");
		String[] resources = current.listResources();
		String path = backupDir + current.getName();
		UserManagementService mgtService =
			(UserManagementService) current.getService( "UserManagementService", "1.0" );
		Permission perms[] = mgtService.listResourcePermissions();
		Permission currentPerms = mgtService.getPermissions( current );
		File file = new File(path);
		if(!file.exists())
			file.mkdirs();
		Resource resource;
		String xml;
		FileOutputStream os;
		OutputStreamWriter writer;
		BufferedWriter pwriter =
			new BufferedWriter(
				new FileWriter(path + '/' + ".permissions.xml")
			);
		pwriter.write("<permissions>\n");
		for(int i = 0; i < resources.length; i++) {
			resource = current.getResource(resources[i]);
			xml = (String)resource.getContent();
			file = new File( path );
			if(!file.exists())
				file.mkdirs();
			System.out.println("writing " + path + '/' + resources[i]);
			os = new FileOutputStream(path + '/' + resources[i]);
			writer = 
				new OutputStreamWriter(os, "UTF-8");
			writer.write(xml);
			writer.close();
			// store permissions
			pwriter.write("    <permission resource=\"");
			pwriter.write(resources[i]);
			pwriter.write("\" owner=\"");
			pwriter.write(perms[i].getOwner());
			pwriter.write("\" group=\"");
			pwriter.write(perms[i].getOwnerGroup());
			pwriter.write("\" mode=\"");
			pwriter.write(Integer.toOctalString(perms[i].getPermissions()));
			pwriter.write("\"/>\n");
		}
		pwriter.write("</permissions>");
		pwriter.close();
		String[] collections = current.listChildCollections();
		Collection child;
		for(int i = 0; i < collections.length; i++) {
			child = current.getChildCollection(collections[i]);
			backup(child);
		}
	}
	
	public static void main(String args[]) {
		try {
			Class cl = Class.forName( "org.exist.xmldb.DatabaseImpl" );
			Database database = (Database) cl.newInstance();
			database.setProperty( "create-database", "true" );
			DatabaseManager.registerDatabase( database );
			Backup backup = new Backup("admin", null, "backup", args[0]);
			backup.backup();
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
}
