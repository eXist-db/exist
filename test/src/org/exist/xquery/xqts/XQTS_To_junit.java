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
 */
package org.exist.xquery.xqts;


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectHelper;
import org.exist.util.ConfigurationHelper;
import org.junit.After;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XPathQueryService;

/**
 * JUnit tests generator from XQTS Catalog.
 * 
 * @author @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class XQTS_To_junit {

	private org.exist.start.Main database;
    
    private String sep = File.separator;
	
	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
		XQTS_To_junit convertor = new XQTS_To_junit();
		
		try {
			convertor.startup();

			convertor.create();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			convertor.shutdown();
		}
	}

	public void startup() throws Exception {
    	database = new org.exist.start.Main("jetty");
    	database.run(new String[]{"jetty"});
	}

	/**
	 * @throws java.lang.Exception
	 */
	@After
	public void shutdown() throws Exception {
       	database.shutdown();

        System.out.println("database was shutdown");
	}
	
	private Collection collection;

	public void create() throws XMLDBException, IOException {
   		File file = ConfigurationHelper.getExistHome();
   		File folder = new File(file.getAbsolutePath()+sep+"test"+sep+"src"+sep+"org"+sep+"exist"+sep+"xquery"+sep+"xqts"+sep);
   		if (!folder.canRead()) {
   			throw new IOException("XQTS junit tests folder unreadable.");
   		}

		collection = DatabaseManager.getCollection("xmldb:exist:///db/XQTS", "admin", null);
		if (collection == null) {
			loadXQTS();
			collection = DatabaseManager.getCollection("xmldb:exist:///db/XQTS", "admin", null);
			if (collection == null) {
				throw new IOException("There is no XQTS data at database");
			}
		}
		
       	String query = "declare namespace catalog=\"http://www.w3.org/2005/02/query-test-XQTSCatalog\";"+
   		"let $XQTSCatalog := xmldb:document('/db/XQTS/XQTSCatalog.xml') "+
   		"return xs:string($XQTSCatalog/catalog:test-suite/@version)";

		XPathQueryService service = (XPathQueryService) collection.getService("XPathQueryService", "1.0");
       	ResourceSet results = service.query(query);
       	
       	if (results.getSize() != 0) {
   			String catalog = (String) results.getResource(0).getContent();
   			catalog = "XQTS_"+adoptString(catalog);
       	
   			File subfolder = new File(folder.getAbsolutePath()+sep+catalog);

   			processGroups(null, subfolder, "."+catalog);
       	}
	}
	
	private void loadXQTS() {
		File buildFile = new File("webapp/xqts/build.xml");
		File xqtsFile = new File("webapp/xqts/build.xml");
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
			Thread.sleep(60*1000);
		} catch (BuildException e) {
			p.fireBuildFinished(e);
		} catch (InterruptedException e) {
		}
	}

	private boolean processGroups(String parentName, File folder, String _package_) throws XMLDBException, IOException {
		XPathQueryService service = (XPathQueryService) collection.getService("XPathQueryService", "1.0");

       	String query = "declare namespace catalog=\"http://www.w3.org/2005/02/query-test-XQTSCatalog\";"+
       		"let $XQTSCatalog := xmldb:document('/db/XQTS/XQTSCatalog.xml')";
       	
       	if (parentName == null)
       		query += "for $testGroup in $XQTSCatalog/catalog:test-suite/catalog:test-group";
       	else
       		query += "for $testGroup in $XQTSCatalog//catalog:test-group[@name = '"+parentName+"']/catalog:test-group";
       	
       	query += "	return xs:string($testGroup/@name)";
       	
       	ResourceSet results = service.query(query);
       	
       	if (results.getSize() != 0) {
       		File subfolder;
       		String subPackage;
       		
//       		if (parentName == null) {
//       			subfolder = folder;
//       			subPackage = _package_;
//       		} else {
//       			subfolder = new File(folder.getAbsolutePath()+sep+parentName);
//       			subPackage = _package_+"."+adoptString(parentName);
//       		}

       		
       		BufferedWriter allTests = startAllTests(folder, _package_);
       		
       		boolean first = true;

   			if (testCases(parentName, folder, _package_)) {
   				if (!first)
   	       			allTests.write(",\n");
   				else
   					first = false;
       			allTests.write("		C_"+adoptString(parentName)+".class");
   			}
       		
       		for (int i = 0; i < results.getSize(); i++) {
       			String groupName = (String) results.getResource(i).getContent();
       			
    			subfolder = new File(folder.getAbsolutePath()+sep+groupName);
    			subPackage = _package_+"."+adoptString(groupName);

       			if (processGroups(groupName, subfolder, subPackage)) { 
       				if (!first)
       	       			allTests.write(",\n");
       				else
       					first = false;
           			allTests.write("		org.exist.xquery.xqts"+subPackage+".AllTests.class");
       			} else if (testCases(groupName, folder, _package_)) {
       				if (!first)
       	       			allTests.write(",\n");
       				else
       					first = false;
           			allTests.write("		C_"+adoptString(groupName)+".class");
       			}
       		}
       		
       		endAllTests(allTests);
       		return true;
       	}
		return false;
	}
	
	private BufferedWriter startAllTests(File folder, String _package_) throws IOException {
		folder.mkdirs();
   		File jTest = new File(folder.getAbsolutePath()+sep+"AllTests.java");

		FileWriter fstream = new FileWriter(jTest.getAbsoluteFile());
   	    BufferedWriter out = new BufferedWriter(fstream);
   	    
   	    out.write("package org.exist.xquery.xqts"+_package_+";\n\n" +
   	    		"import org.junit.runner.RunWith;\n" +
   	    		"import org.junit.runners.Suite;\n\n" +
   	    		"@RunWith(Suite.class)\n" +
   	    		"@Suite.SuiteClasses({\n");
//   	    		"        XmldbLocalTests.class," +
   	    
   	    return out;
	}
	
	private void endAllTests(BufferedWriter out) throws IOException {
		out.write("\n})\n\n"+
				"public class AllTests {\n\n" +
				"}");
		out.close();
	}
	
	private boolean testCases(String testGroup, File folder, String _package_) throws XMLDBException, IOException {
		XPathQueryService service = (XPathQueryService) collection.getService("XPathQueryService", "1.0");

       	String query = "declare namespace catalog=\"http://www.w3.org/2005/02/query-test-XQTSCatalog\";"+
       		"let $XQTSCatalog := xmldb:document('/db/XQTS/XQTSCatalog.xml')"+
       		"for $testGroup in $XQTSCatalog//catalog:test-group[@name = '"+testGroup+"']/catalog:test-case"+
       		"	return xs:string($testGroup/@name)";
       	
       	ResourceSet results = service.query(query);
	       	
       	if (results.getSize() != 0) {
       		folder.mkdirs();
       		File jTest = new File(folder.getAbsolutePath()+sep+"C_"+adoptString(testGroup)+".java");

   			FileWriter fstream = new FileWriter(jTest.getAbsoluteFile());
       	    BufferedWriter out = new BufferedWriter(fstream);
       	    
       	    out.write("package org.exist.xquery.xqts"+_package_+";\n\n"+
       	    		"import org.exist.xquery.xqts.XQTS_case;\n" +
       	    		"import static org.junit.Assert.*;\n" +
       	    		"import org.junit.Test;\n\n" +
       	    		"public class C_"+adoptString(testGroup)+" extends XQTS_case {\n" +
       	    		"	private String testGroup = \""+testGroup+"\";\n\n");
       	    
       	    for (int i = 0; i < results.getSize(); i++) {
       			String caseName = (String) results.getResource(i).getContent();

       	        out.write("	/* "+caseName+" */" +
       	        		"	@Test\n" +
       	        		"	public void test_"+adoptString(caseName)+"() {\n" +
       	        		"		groupCase(testGroup, \""+caseName+"\");"+
       	        		"	}\n\n");
       		}
       	    out.write("}");
   	        out.close();
   	        return true;
       	}
       	return false;
	}

	private String adoptString(String caseName) {
		String result = caseName.replace("-", "_");
		result = result.replace(".", "_");
		return result;
	}
}
