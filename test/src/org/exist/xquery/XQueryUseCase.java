/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
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
package org.exist.xquery;

import org.apache.commons.io.output.ByteArrayOutputStream;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.exist.xmldb.XQueryService;
import org.exist.xmldb.XmldbURI;

import org.junit.Before;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class XQueryUseCase {

	private final static String baseDir = "samples/xquery/use-cases";

	private Collection root = null;

	@Before
	protected void setUp() throws Exception {
		// initialize driver
		Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
		Database database = (Database) cl.newInstance();
		database.setProperty("create-database", "true");
		DatabaseManager.registerDatabase(database);
		root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, "admin", "");
	}

	public void doTest(String useCase) throws Exception {
		CollectionManagementService service =
			(CollectionManagementService) root.getService(
				"CollectionManagementService",
				"1.0");
		root = service.createCollection(useCase);

		File file = new File(baseDir + File.separatorChar + useCase);
		if (!(file.canRead() && file.isDirectory()))
			throw new RuntimeException("Cannot read data for use-case " + useCase);
		setupData(file);
		processQueries(file);
	}

	protected void processQueries(File file) throws Exception {
		File[] files = file.listFiles(new FileFilter() {
			public boolean accept(File pathname) {
				return pathname.getName().startsWith("q") && pathname.getName().endsWith(".xq");
			}
		});
		for(int i = 0; i < files.length; i++) {
			System.out.println("processing use-case: " + files[i].getAbsolutePath());
			System.out.println("========================================================================");
			String query = readQuery(files[i]);
			System.out.println(query);
			System.out.println("_________________________________________________________________________________");
			XQueryService service = (XQueryService)root.getService("XQueryService", "1.0");
			ResourceSet results;
			try {
				results = service.query(query);
				for(int j = 0; j < results.getSize(); j++) {
					String output = (String)results.getResource(j).getContent();
					System.out.println(output);
				}
			} catch (Exception e) {
				Throwable cause = e.getCause();
				if ( cause == null )
					cause = e;
				System.err.println( "Exception: " + e.getClass() + " - "+ cause );
				for (int j = 0; j < 4; j++) {
					StackTraceElement el = cause.getStackTrace()[j];
					System.err.println( el );
				}
				e.getStackTrace();
				// rethrow for JUnit reporting
				throw e;
			}
			System.out.println("========================================================================");
		}
	}
	
	protected void setupData(File file) throws Exception {
		File[] files = file.listFiles(new FileFilter() {
			public boolean accept(File pathname) {
				return pathname.getName().endsWith(".xml");
			}
		});
		for (int i = 0; i < files.length; i++) {
			XMLResource res =
				(XMLResource) root.createResource(files[i].getName(), "XMLResource");
			res.setContent(files[i]);
			root.storeResource(res);
		}
	}
	
	protected String readQuery(File f) throws IOException {
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			byte[] t = new byte[512];
			InputStream is = new FileInputStream(f);
			try {
				int count = 0;
				while((count = is.read(t)) != -1) {
					os.write(t, 0, count);
				}
				return new String(os.toString("UTF-8"));
			} finally {
				is.close();
				os.close();
			}
		}
}
