/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
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
 *  $Id$
 */
package org.exist.xupdate;

import java.util.Random;

import junit.framework.TestCase;
import junit.textui.TestRunner;

import org.exist.xmldb.XmldbURI;
import org.exist.xmldb.concurrent.DBUtils;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XPathQueryService;
import org.xmldb.api.modules.XUpdateQueryService;

/**
 * @author wolf
 *
 */
public class StressTest extends TestCase {

    public static void main(String[] args) {
        TestRunner.run(StressTest.class);
    }
    
    private final static String XML = "<root><a/><b/><c/></root>";
    
    private final static String URI = XmldbURI.LOCAL_DB;
    
    private final static int RUNS = 1000;
    
    private Collection rootCol;
    private Collection testCol;
    private Random rand = new Random();
    
    private String[] tags;
    
    public void test() throws Exception {
        try {
            insertTags();
            removeTags();
            fetchDb();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void insertTags() throws Exception {
        XUpdateQueryService service = (XUpdateQueryService)
            testCol.getService("XUpdateQueryService", "1.0");
        XPathQueryService xquery = (XPathQueryService)
            testCol.getService("XPathQueryService", "1.0");
        
        String[] tagsWritten = new String[RUNS];
        for (int i = 0; i < RUNS; i++) {
            String tag = tags[i];
            String parent;
            if (i > 0 && rand.nextInt(100) < 70) {
                parent = "//" + tagsWritten[rand.nextInt(i) / 2];
            } else
                parent = "/root";
            String xupdate = "<xupdate:modifications version=\"1.0\" xmlns:xupdate=\"http://www.xmldb.org/xupdate\">" +
                "<xupdate:append select=\"" + parent + "\">" +
                "<xupdate:element name=\"" + tag + "\"/>" +
                "</xupdate:append>" +
                "</xupdate:modifications>";
            
            long mods = service.updateResource("test.xml", xupdate);
            System.out.println("Inserted " + tag + ": " + mods + " ; parent = " + parent);
            assertEquals(mods, 1);
            
            tagsWritten[i] = tag;
            String query = "//" + tagsWritten[rand.nextInt(i + 1)];
            ResourceSet result = xquery.query(query);
            assertEquals(result.getSize(), 1);
            
            System.out.println(result.getResource(0).getContent());
        }
        
        XMLResource res = (XMLResource) testCol.getResource("test.xml");
        assertNotNull(res);
        System.out.println(res.getContent());
    }
    
    private void removeTags() throws Exception {
        XUpdateQueryService service = (XUpdateQueryService)
            testCol.getService("XUpdateQueryService", "1.0");
        int start = rand.nextInt(RUNS / 4);
        for (int i = start; i < RUNS; i++) {
            String xupdate = "<xupdate:modifications version=\"1.0\" xmlns:xupdate=\"http://www.xmldb.org/xupdate\">" +
            "<xupdate:remove select=\"//" + tags[i] + "\"/>" +
            "</xupdate:modifications>";
            
            @SuppressWarnings("unused")
			long mods = service.updateResource("test.xml", xupdate);
            System.out.println("Removed: " + tags[i]);
            
            i += rand.nextInt(3);
        }
    }
    
    private void fetchDb() throws Exception {
        XPathQueryService xquery = (XPathQueryService)
            testCol.getService("XPathQueryService", "1.0");
        ResourceSet result = xquery.query("for $n in collection('" + XmldbURI.ROOT_COLLECTION + "/test')//* return local-name($n)");
        
        for (int i = 0; i < result.getSize(); i++) {
            Resource r = result.getResource(i);
            String tag = r.getContent().toString();
            System.out.println("Retrieving " + tag);
            
            ResourceSet result2 = xquery.query("//" + tag);
            assertEquals(result2.getSize(), 1);
            
            System.out.println(result2.getResource(0).getContent());
        }
    }
    
    protected void setUp() throws Exception {
        rootCol = DBUtils.setupDB(URI);
        

        testCol = rootCol.getChildCollection(XmldbURI.ROOT_COLLECTION + "/test");
        if(testCol != null) {
            CollectionManagementService mgr = DBUtils.getCollectionManagementService(rootCol);
            mgr.removeCollection(XmldbURI.ROOT_COLLECTION + "/test");
        }
        
        testCol = DBUtils.addCollection(rootCol, "test");
        assertNotNull(testCol);
        
        System.out.println("Generating " + RUNS + " tags ...");
        
        tags = new String[RUNS];
        for (int i = 0; i < RUNS; i++) {
            tags[i] = "TAG" + i;
        }
        
        
        DBUtils.addXMLResource(testCol, "test.xml", XML);
    }
    
    protected void tearDown() throws Exception {
//        DBUtils.shutdownDB(URI);
    }
}
