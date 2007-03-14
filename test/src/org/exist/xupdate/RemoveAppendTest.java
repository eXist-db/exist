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

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Random;

import junit.framework.TestCase;
import junit.textui.TestRunner;

import org.exist.storage.DBBroker;
import org.exist.xmldb.concurrent.DBUtils;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XPathQueryService;
import org.xmldb.api.modules.XUpdateQueryService;

/**
 * @author wolf
 *
 */
public class RemoveAppendTest extends TestCase {
    
    public static void main(String[] args) {
        TestRunner.run(RemoveAppendTest.class);
    }
    
    private final static String URI = "xmldb:exist://" + DBBroker.ROOT_COLLECTION;
    
    private final static String XU_INSERT_START =
        "<xu:modifications xmlns:xu=\""+ XUpdateProcessor.XUPDATE_NS + "\" version=\"1.0\">" +
        "   <xu:insert-before select=\"/test/item[@id='5']\">";
    
    private final static String XU_INSERT_END =
        "   </xu:insert-before>" +
        "</xu:modifications>";
    
    private final static String XU_REMOVE =
        "<xu:modifications xmlns:xu=\""+ XUpdateProcessor.XUPDATE_NS + "\" version=\"1.0\">" +
        "   <xu:remove select=\"/test/item[@id='5'][2]\"/>" +
        "</xu:modifications>";
    
    private static final int ITEM_COUNT = 0;
    
    private Collection rootCol;
    private Collection testCol;
    private Random rand = new Random();
    
//    public void testRemoveAppend() throws Exception {
//        XUpdateQueryService service = (XUpdateQueryService)
//            testCol.getService("XUpdateQueryService", "1.0");
//        XPathQueryService query = (XPathQueryService)
//            testCol.getService("XPathQueryService", "1.0");
//        for (int i = 1; i < 1000; i++) {
//            int which = rand.nextInt(ITEM_COUNT) + 1;
//            insert(service, which);
//            remove(service, which);
//            
//            ResourceSet result = query.query("/test/item[@id='" + which + "']");
//            assertEquals(result.getSize(), 1);
//            System.out.println(result.getResource(0).getContent());
//        }
//    }
    
    public void testAppendRemove() throws Exception {
        XUpdateQueryService service = (XUpdateQueryService)
        testCol.getService("XUpdateQueryService", "1.0");
        XPathQueryService query = (XPathQueryService)
            testCol.getService("XPathQueryService", "1.0");
        for (int i = 1; i <= 100; i++) {
            append(service, i);
            
            ResourceSet result = query.query("/test/item[@id='" + i + "']");
            assertEquals(result.getSize(), 1);
            System.out.println(result.getResource(0).getContent());
        }
        
        for (int i = 100; i > 10; i--) {
            String xu = 
                "<xu:modifications xmlns:xu=\"http://www.xmldb.org/xupdate\" version=\"1.0\">" +
                "   <xu:remove select=\"/test/item[@id='" + i + "']\"/>" +
                "</xu:modifications>";
            long mods = service.update(xu);
            assertEquals(mods, 1);
            
            ResourceSet result = query.query("/test/item/e0");
            System.out.println(result.getResource(0).getContent());
        }
    }
    
    protected void append(XUpdateQueryService service, int id) throws Exception {
        StringWriter out = new StringWriter();
        out.write("<xu:modifications xmlns:xu=\"http://www.xmldb.org/xupdate\" version=\"1.0\">");
        out.write("<xu:append select=\"/test\">");
        createItem(id, out);
        out.write("</xu:append>");
        out.write("</xu:modifications>");
         long mods = service.update(out.toString());
         assertEquals(mods, 1);
    }
    
    protected void insert(XUpdateQueryService service, int id) throws Exception {
        StringWriter out = new StringWriter();
        out.write("<xu:modifications xmlns:xu=\"http://www.xmldb.org/xupdate\" version=\"1.0\">");
        out.write("<xu:insert-before select=\"/test/item[@id='");
        out.write(Integer.toString(id));
        out.write("']\">");
        createItem(5, out);
        out.write("</xu:insert-before>");
        out.write("</xu:modifications>");
         long mods = service.update(out.toString());
         assertEquals(mods, 1);
    }
    
    protected void remove(XUpdateQueryService service, int id) throws Exception {
        String xu = 
            "<xu:modifications xmlns:xu=\"http://www.xmldb.org/xupdate\" version=\"1.0\">" +
            "   <xu:remove select=\"/test/item[@id='" + id + "'][2]\"/>" +
            "</xu:modifications>";

        long mods = service.update(XU_REMOVE);
        assertEquals(mods, 1);
    }
    
    protected void setUp() throws Exception {
        rootCol = DBUtils.setupDB(URI);
        

        testCol = rootCol.getChildCollection(DBBroker.ROOT_COLLECTION + "/test");
        if(testCol != null) {
            CollectionManagementService mgr = DBUtils.getCollectionManagementService(rootCol);
            mgr.removeCollection(DBBroker.ROOT_COLLECTION + "/test");
        }
        
        testCol = DBUtils.addCollection(rootCol, "test");
        assertNotNull(testCol);
        
        DBUtils.addXMLResource(testCol, "test.xml", "<test/>");
    }
    
    protected void tearDown() throws Exception {
        DBUtils.shutdownDB(URI);
    }
    
    protected void createItem(int id, Writer out) throws IOException {
        out.write("<item ");
        out.write("id=\"");
        out.write(Integer.toString(id));
        out.write("\"");
        addAttributes(out);
        out.write(">");
        for (int i = 0; i < 10; i++) {
            out.write("<e");
            out.write(Integer.toString(i));
            addAttributes(out);
            out.write(">");
            out.write(Integer.toString(rand.nextInt()));
            out.write("</e");
            out.write(Integer.toString(i));
            out.write(">");
        }
        out.write("</item>");
    }

    /**
     * @param out
     * @param rand
     * @throws IOException
     */
    private void addAttributes(Writer out) throws IOException {
        for (int j = 0; j < 5; j++) {
            out.write(" attr");
            out.write(Integer.toString(j));
            out.write("=\"");
            out.write(Integer.toString(rand.nextInt()));
            out.write('"');
        }
    }
}
