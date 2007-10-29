/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 * 
 *  $Id$
 */
package org.exist.xquery;

import org.exist.TestUtils;
import org.exist.storage.DBBroker;
import org.exist.util.XMLFilenameFilter;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.IndexQueryService;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XQueryService;

import java.io.File;
import java.io.IOException;

/**
 * 
 */
public class OptimizerTest {

    private final static String OPTIMIZE = "declare option exist:optimize 'enable=yes';";
    private final static String NO_OPTIMIZE = "declare option exist:optimize 'enable=no';";
    private final static String NAMESPACES = "declare namespace mods='http://www.loc.gov/mods/v3';";

    private static final String MSG_OPT_ERROR = "Optimized query should return same number of results.";

    private final static String XML =
            "<root>" +
            "   <a><b>one</b></a>" +
            "   <a><c><b>one</b></c></a>" +
            "   <c><a><c><b>two</b></c></a></c>" +
            "</root>";

    private final static String COLLECTION_CONFIG =
        "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
    	"	<index xmlns:mods=\"http://www.loc.gov/mods/v3\">" +
    	"		<fulltext default=\"none\">" +
        "           <create qname=\"LINE\"/>" +
        "           <create qname=\"SPEAKER\"/>" +
        "           <create qname=\"mods:title\"/>" +
        "           <create qname=\"mods:topic\"/>" +
        "		</fulltext>" +
    	"		<create qname=\"b\" type=\"xs:string\"/>" +
        "        <create qname=\"SPEAKER\" type=\"xs:string\"/>" +
        "        <create qname=\"mods:internetMediaType\" type=\"xs:string\"/>" +
        "	</index>" +
    	"</collection>";
    private static Collection testCollection;

    @Test
    public void nestedQuery() {
        execute("/root/a[descendant::b = 'one']", true, "Inner b node should be returned.", 2);
        execute("/root/a[b = 'one']", true, "Inner b node should not be returned.", 1);
        execute("/root/a[b = 'one']", false, "Inner b node should not be returned.", 1);
    }

    @Test
    public void simplePredicates() {
        int r = execute("//SPEECH[LINE &= 'king']", false);
        execute("//SPEECH[LINE &= 'king']", true, MSG_OPT_ERROR, r);

        r = execute("//SPEECH[SPEAKER = 'HAMLET']", false);
        execute("//SPEECH[SPEAKER = 'HAMLET']", true, MSG_OPT_ERROR, r);

        r = execute("//SPEECH[descendant::SPEAKER = 'HAMLET']", false);
        execute("//SPEECH[descendant::SPEAKER = 'HAMLET']", true, MSG_OPT_ERROR, r);
        
        r = execute("//SCENE[descendant::LINE &= 'king']", false);
        execute("//SCENE[descendant::LINE &= 'king']", true, MSG_OPT_ERROR, r);

        r = execute("//LINE[. &= 'king']", false);
        execute("//LINE[. &= 'king']", true, MSG_OPT_ERROR, r);

        r = execute("//SPEAKER[. = 'HAMLET']", false);
        execute("//SPEAKER[. = 'HAMLET']", true, MSG_OPT_ERROR, r);

        r = execute("//LINE[descendant-or-self::LINE &= 'king']", false);
        execute("//LINE[descendant-or-self::LINE &= 'king']", true, MSG_OPT_ERROR, r);

        r = execute("//SPEAKER[descendant-or-self::SPEAKER = 'HAMLET']", false);
        execute("//SPEAKER[descendant-or-self::SPEAKER = 'HAMLET']", true, MSG_OPT_ERROR, r);

        r = execute("//SPEECH/LINE[. &= 'king']", false);
        execute("//SPEECH/LINE[. &= 'king']", true, MSG_OPT_ERROR, r);
        
        r = execute("//*[LINE &= 'king']", false);
        execute("//*[LINE &= 'king']", true, MSG_OPT_ERROR, r);

        r = execute("//*[SPEAKER = 'HAMLET']", false);
        execute("//*[SPEAKER = 'HAMLET']", true, MSG_OPT_ERROR, r);
    }

    @Test
    public void namespaces() {
        int r = execute("//mods:mods/mods:titleInfo[mods:title &= 'ethnic']", false);
        execute("//mods:mods/mods:titleInfo[mods:title &= 'ethnic']", true, MSG_OPT_ERROR, r);
        r = execute("//mods:mods/mods:physicalDescription[mods:internetMediaType &= 'application/pdf']", false);
        execute("//mods:mods/mods:physicalDescription[mods:internetMediaType &= 'application/pdf']", true, MSG_OPT_ERROR, r);
        r = execute("//mods:mods/mods:*[mods:title &= 'ethnic']", false);
        execute("//mods:mods/mods:*[mods:title &= 'ethnic']", true, MSG_OPT_ERROR, r);
    }

    @Test
    public void simplePredicatesRegex() {
        int r = execute("//SPEECH[LINE &= 'nor*']", false);
        execute("//SPEECH[LINE &= 'nor*']", true, MSG_OPT_ERROR, r);
        r = execute("//SPEECH[LINE &= 'skirts nor*']", false);
        execute("//SPEECH[LINE &= 'skirts nor*']", true, MSG_OPT_ERROR, r);
        r = execute("//SPEECH[near(LINE, 'skirts nor*', 2)]", false);
        execute("//SPEECH[near(LINE, 'skirts nor*', 2)]", true, MSG_OPT_ERROR, r);

        //Test old and new functions
        r = execute("//SPEECH[fn:match-all(LINE, 'skirts', 'nor.*')]", false);
        execute("//SPEECH[fn:match-all(LINE, 'skirts', 'nor.*')]", true, MSG_OPT_ERROR, r);
        execute("//SPEECH[text:match-all(LINE, ('skirts', 'nor.*'))]", false, "Query should return same number of results.", r);

        //Test old and new functions
        r = execute("//SPEECH[fn:match-any(LINE, 'skirts', 'nor.*')]", false);
        execute("//SPEECH[fn:match-any(LINE, 'skirts', 'nor.*')]", true, MSG_OPT_ERROR, r);
        execute("//SPEECH[text:match-any(LINE, ('skirts', 'nor.*'), 'w')]", false, "Query should return same number of results.", r);
        execute("//SPEECH[text:match-any(LINE, ('skirts', 'nor.*'), 'w')]", true, MSG_OPT_ERROR, r);
        execute("//SPEECH[text:match-any(LINE, ('skirts', '^nor.*$'))]", true, MSG_OPT_ERROR, r);

        r = execute("//SPEECH[matches(SPEAKER, '^HAM.*')]", false);
        execute("//SPEECH[matches(SPEAKER, '^HAM.*')]", true, MSG_OPT_ERROR, r);
        r = execute("//SPEECH[starts-with(SPEAKER, 'HAML')]", false);
        execute("//SPEECH[starts-with(SPEAKER, 'HAML')]", true, MSG_OPT_ERROR, r);
        r = execute("//SPEECH[ends-with(SPEAKER, 'EO')]", false);
        execute("//SPEECH[ends-with(SPEAKER, 'EO')]", true, MSG_OPT_ERROR, r);
        r = execute("//SPEECH[matches(descendant::SPEAKER, 'HAML.*')]", false);
        execute("//SPEECH[matches(descendant::SPEAKER, 'HAML.*')]", true, MSG_OPT_ERROR, r);
    }

    @Test
    public void twoPredicates() {
        int r = execute("//SPEECH[LINE &= 'king'][SPEAKER='HAMLET']", false);
        execute("//SPEECH[LINE &= 'king'][SPEAKER='HAMLET']", true, MSG_OPT_ERROR, r);
        r = execute("//SPEECH[SPEAKER='HAMLET'][LINE &= 'king']", false);
        execute("//SPEECH[SPEAKER='HAMLET'][LINE &= 'king']", true, MSG_OPT_ERROR, r);
    }

    @Test
    public void noOptimization() {
        int r = execute("//mods:title[ancestor-or-self::mods:title &= 'ethnic']", false);
        execute("//mods:title[ancestor-or-self::mods:title &= 'ethnic']", true, "Ancestor axis should not be optimized.", r);
        r = execute("//node()[parent::mods:title &= 'ethnic']", false);
        execute("//node()[parent::mods:title &= 'ethnic']", true, "Parent axis should not be optimized.", r);

        r = execute("/root//b[parent::c/b = 'two']", false);
        Assert.assertEquals(1, r);
        execute("/root//b[parent::c/b = 'two']", true, "Parent axis should not be optimized.", r);
        
        r = execute("/root//b[ancestor::a/c/b = 'two']", false);
        Assert.assertEquals(1, r);
        execute("/root//b[ancestor::a/c/b = 'two']", true, "Ancestor axis should not be optimized.", r);

        r = execute("/root//b[ancestor::a/b = 'two']", false);
        Assert.assertEquals(0, r);
        execute("/root//b[ancestor::a/b = 'two']", true, "Ancestor axis should not be optimized.", r);

        r = execute("/root//b[text()/parent::b = 'two']", false);
        Assert.assertEquals(1, r);
        execute("/root//b[text()/parent::b = 'two']", true, "Parent axis should not be optimized.", r);

        r = execute("/root//b[matches(text()/parent::b, 'two')]", false);
        Assert.assertEquals(1, r);
        execute("/root//b[matches(text()/parent::b, 'two')]", true, "Parent axis should not be optimized.", r);
    }

    @Test
    public void complexPaths() {
        int r = execute("//mods:mods[mods:titleInfo/mods:title &= 'ethnic']", false);
        execute("//mods:mods[mods:titleInfo/mods:title &= 'ethnic']", true, MSG_OPT_ERROR, r);

        r = execute("//mods:mods[text:match-all(mods:titleInfo/mods:title, 'and')]", false);
        execute("//mods:mods[text:match-all(mods:titleInfo/mods:title, 'and')]", true, MSG_OPT_ERROR, r);

        r = execute("//mods:mods[./mods:titleInfo/mods:title &= 'ethnic']", false);
        execute("//mods:mods[./mods:titleInfo/mods:title &= 'ethnic']", true, MSG_OPT_ERROR, r);

        r = execute("//mods:mods[*/mods:title &= 'ethnic']", false);
        execute("//mods:mods[*/mods:title &= 'ethnic']", true, MSG_OPT_ERROR, r);

        r = execute("//mods:mods[mods:physicalDescription/mods:internetMediaType = 'text/html']", false);
        execute("//mods:mods[mods:physicalDescription/mods:internetMediaType = 'text/html']", true, MSG_OPT_ERROR, r);

        r = execute("//mods:mods[./mods:physicalDescription/mods:internetMediaType = 'text/html']", false);
        execute("//mods:mods[./mods:physicalDescription/mods:internetMediaType = 'text/html']", true, MSG_OPT_ERROR, r);

        r = execute("//mods:mods[*/mods:internetMediaType = 'text/html']", false);
        execute("//mods:mods[*/mods:internetMediaType = 'text/html']", true, MSG_OPT_ERROR, r);

        r = execute("//mods:mods[matches(mods:physicalDescription/mods:internetMediaType, 'text/html')]", false);
        execute("//mods:mods[matches(mods:physicalDescription/mods:internetMediaType, 'text/html')]", true, MSG_OPT_ERROR, r);

        r = execute("//mods:mods[matches(*/mods:internetMediaType, 'text/html')]", false);
        execute("//mods:mods[matches(*/mods:internetMediaType, 'text/html')]", true, MSG_OPT_ERROR, r);
    }

    @Test
    public void reversePaths() {
        int r = execute("/root//b/parent::c[b = 'two']", false);
        Assert.assertEquals(1, r);
        execute("/root//b/parent::c[b = 'two']", true, MSG_OPT_ERROR, r);

        r = execute("//mods:url/ancestor::mods:mods[mods:titleInfo/mods:title &= 'and']", false);
        Assert.assertEquals(11, r);
        execute("//mods:url/ancestor::mods:mods[mods:titleInfo/mods:title &= 'and']", true, MSG_OPT_ERROR, r);
    }

    @Test
    public void booleanOperator() {
        int r = execute("//SPEECH[LINE &= 'king'][SPEAKER='HAMLET']", false);
        execute("//SPEECH[LINE &= 'king' and SPEAKER='HAMLET']", false, MSG_OPT_ERROR, r);
        execute("//SPEECH[LINE &= 'king' and SPEAKER='HAMLET']", true, MSG_OPT_ERROR, r);
        r = execute("//SPEECH[LINE &= 'king' or SPEAKER='HAMLET']", false);
        execute("//SPEECH[LINE &= 'king' or SPEAKER='HAMLET']", true, MSG_OPT_ERROR, r);

        r = execute("//SPEECH[LINE &= 'love' and LINE &= \"woman's\" and SPEAKER='HAMLET']", false);
        execute("//SPEECH[LINE &= 'love' and LINE &= \"woman's\" and SPEAKER='HAMLET']", true, MSG_OPT_ERROR, r);

        r = execute("//SPEECH[(LINE &= 'king' or LINE &= 'love') and SPEAKER='HAMLET']", false);
        execute("//SPEECH[(LINE &= 'king' or LINE &= 'love') and SPEAKER='HAMLET']", true, MSG_OPT_ERROR, r);

        r = execute("//SPEECH[(LINE &= 'juliet' and LINE &= 'romeo') or SPEAKER='HAMLET']", false);
        Assert.assertEquals(368, r);
        execute("//SPEECH[(LINE &= 'juliet' and LINE &= 'romeo') or SPEAKER='HAMLET']", true, MSG_OPT_ERROR, r);

        r = execute("//SPEECH[(LINE &= 'juliet' and LINE &= 'romeo') and SPEAKER='HAMLET']", false);
        Assert.assertEquals(0, r);
        execute("//SPEECH[(LINE &= 'juliet' and LINE &= 'romeo') and SPEAKER='HAMLET']", true, MSG_OPT_ERROR, r);

        r = execute("//SPEECH[LINE &= 'juliet' or (LINE &= 'king' and SPEAKER='HAMLET')]", false);
        Assert.assertEquals(65, r);
        execute("//SPEECH[LINE &= 'juliet' or (LINE &= 'king' and SPEAKER='HAMLET')]", true, MSG_OPT_ERROR, r);

        execute("//SPEECH[true() and false()]", true, MSG_OPT_ERROR, 0);
        execute("//SPEECH[true() and true()]", true, MSG_OPT_ERROR, 2628);
    }

    private int execute(String query, boolean optimize) {
        try {
            System.out.println("--- Query: " + query + "; Optimize: " + Boolean.toString(optimize));
            XQueryService service = (XQueryService) testCollection.getService("XQueryService", "1.0");
            if (optimize)
                query = OPTIMIZE + query;
            else
                query = NO_OPTIMIZE + query;
            query = NAMESPACES + query;
            ResourceSet result = service.query(query);
            System.out.println("-- Found: " + result.getSize());
            return (int) result.getSize();
        } catch (XMLDBException e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
        return 0;
    }

    private void execute(String query, boolean optimize, String message, int expected) {
        try {
            System.out.println("--- Query: " + query + "; Optimize: " + Boolean.toString(optimize));
            XQueryService service = (XQueryService) testCollection.getService("XQueryService", "1.0");
            if (optimize)
                query = NAMESPACES + OPTIMIZE + query;
            else
                query = NAMESPACES + NO_OPTIMIZE + query;
            ResourceSet result = service.query(query);
            System.out.println("-- Found: " + result.getSize());
            Assert.assertEquals(message, expected, result.getSize());
        } catch (XMLDBException e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }
    
    @BeforeClass
    public static void initDatabase() {
		try {
			// initialize driver
			Class cl = Class.forName("org.exist.xmldb.DatabaseImpl");
			Database database = (Database) cl.newInstance();
			database.setProperty("create-database", "true");
			DatabaseManager.registerDatabase(database);

			Collection root =
				DatabaseManager.getCollection("xmldb:exist://" + DBBroker.ROOT_COLLECTION, "admin",	null);
			CollectionManagementService service =
				(CollectionManagementService) root.getService("CollectionManagementService", "1.0");
			testCollection = service.createCollection("test");
			Assert.assertNotNull(testCollection);

            IndexQueryService idxConf = (IndexQueryService) testCollection.getService("IndexQueryService", "1.0");
            idxConf.configureCollection(COLLECTION_CONFIG);
            
            XMLResource resource = (XMLResource) testCollection.createResource("test.xml", "XMLResource");
            resource.setContent(XML);
            testCollection.storeResource(resource);

            String existHome = System.getProperty("exist.home");
            File existDir = existHome==null ? new File(".") : new File(existHome);
            File dir = new File(existDir, "samples/shakespeare");
            if (!dir.canRead())
                throw new IOException("Unable to read samples directory");
            File[] files = dir.listFiles(new XMLFilenameFilter());
            for (File file : files) {
                resource = (XMLResource) testCollection.createResource(file.getName(), "XMLResource");
                resource.setContent(file);
                testCollection.storeResource(resource);
            }

            dir = new File(existDir, "samples/mods");
            if (!dir.canRead())
                throw new IOException("Unable to read samples directory");
            files = dir.listFiles(new XMLFilenameFilter());
            for (File file : files) {
                resource = (XMLResource) testCollection.createResource(file.getName(), "XMLResource");
                resource.setContent(file);
                testCollection.storeResource(resource);
            }
        } catch (Exception e) {
			e.printStackTrace();
            Assert.fail(e.getMessage());
        }
	}

    @AfterClass
    public static void shutdownDB() {
        try {
            TestUtils.cleanupDB();
            DatabaseInstanceManager dim =
                (DatabaseInstanceManager) testCollection.getService(
                    "DatabaseInstanceManager", "1.0");
            dim.shutdown();
        } catch (XMLDBException e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
        testCollection = null;

		System.out.println("tearDown PASSED");
	}
}
