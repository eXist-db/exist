/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009 The eXist Project
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
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id:$
 */
package org.exist.xquery.xqts;

import java.io.File;

import junit.framework.Assert;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectHelper;
import org.exist.source.FileSource;
import org.exist.storage.BrokerPool;
import org.exist.xmldb.XQueryService;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.w3c.dom.Element;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * 
 */
public class XQTS_case {

	public static org.exist.start.Main database = null;
	private static int inUse = 0;
	protected static Collection testCollection = null;
	
	private static Thread shutdowner = null;

	static class Shutdowner implements Runnable {

		public void run() {
			try {
				Thread.sleep(2 * 60 * 1000);

				if (inUse == 0) {
					database.shutdown();

					System.out.println("database was shutdown");
					database = null;
				}
			} catch (InterruptedException e) {
			}
		}
		
	}
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		try {
			if (database == null) {
				database = new org.exist.start.Main("jetty");
				database.run(new String[] { "jetty" });

				testCollection = DatabaseManager.getCollection(
						"xmldb:exist:///db/XQTS", "admin", null);
				if (testCollection == null) {
					loadXQTS();
					testCollection = DatabaseManager.getCollection(
							"xmldb:exist:///db/XQTS", "admin", null);
					if (testCollection == null) {
						Assert.fail("There is no XQTS data at database");
					}
				}
				if (shutdowner == null) {
					shutdowner = new Thread(new Shutdowner());
					shutdowner.start();
				}
			}
			inUse++;
		} catch (Exception e) {
			e.printStackTrace();
		}
//		System.out.println("setUpBeforeClass PASSED");
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		inUse--;
//		System.out.println("tearDownAfterClass PASSED");
	}

	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
		// System.out.println("setUp PASSED");
	}

	/**
	 * @throws java.lang.Exception
	 */
	@After
	public void tearDown() throws Exception {
		// System.out.println("tearDown PASSED");
	}

	private static void loadXQTS() {
		File buildFile = new File("webapp/xqts/build.xml");
		Project p = new Project();
		p.setUserProperty("ant.file", buildFile.getAbsolutePath());
		p.setUserProperty("config.basedir", "../../test/external/XQTS_1_0_2");
		DefaultLogger consoleLogger = new DefaultLogger();
		consoleLogger.setErrorPrintStream(System.err);
		consoleLogger.setOutputPrintStream(System.out);
		consoleLogger.setMessageOutputLevel(Project.MSG_INFO);
		p.addBuildListener(consoleLogger);

		try {
			p.fireBuildStarted();
			p.init();
			ProjectHelper helper = ProjectHelper.getProjectHelper();
			p.addReference("ant.projectHelper", helper);
			helper.parse(p, buildFile);
			p.executeTarget("store");
			p.fireBuildFinished(null);
			Thread.sleep(60 * 1000);
		} catch (BuildException e) {
			p.fireBuildFinished(e);
		} catch (InterruptedException e) {
		}
	}

	protected void groupCase(String testGroup, String testCase) {
		BrokerPool pool;
		try {
			XQueryService service = (XQueryService) testCollection.getService(
					"XQueryService", "1.0");

			File xqts = new File("test/src/org/exist/xquery/xqts/xqts.xql");

			service.declareVariable("testGroup", testGroup);
			service.declareVariable("testCase", testCase);

			ResourceSet result = service.execute(new FileSource(xqts, "UTF8",
					true));

			Assert.assertEquals(1, result.getSize());

			XMLResource resource = (XMLResource) result.getResource(0);

			Element root = (Element) resource.getContentAsDOM();
			Assert.assertEquals(resource.getContent().toString(), "pass", root.getAttribute("result"));

		} catch (XMLDBException e) {
			Assert.fail(e.toString());
		}
	}

}
