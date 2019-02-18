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

import org.exist.EXistException;
import org.exist.TestUtils;

import org.exist.collections.triggers.TriggerException;
import org.exist.security.PermissionDeniedException;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xmldb.concurrent.DBUtils;
import org.junit.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XPathQueryService;
import org.xmldb.api.modules.XUpdateQueryService;

/**
 * @author wolf
 *
 */
public class RemoveAppendTest {
    
    private final static String XU_REMOVE =
        "<xu:modifications xmlns:xu=\""+ XUpdateProcessor.XUPDATE_NS + "\" version=\"1.0\">" +
        "   <xu:remove select=\"/test/item[@id='5'][2]\"/>" +
        "</xu:modifications>";

    private static final int ITEM_COUNT = 0;
    
    private Collection rootCol;
    private Collection testCol;
    private final Random rand = new Random();

    @Rule
    public final ExistXmldbEmbeddedServer existXmldbEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    @Ignore
    @Test
    public void testRemoveAppend() throws Exception {
        XUpdateQueryService service = (XUpdateQueryService)
            testCol.getService("XUpdateQueryService", "1.0");
        XPathQueryService query = (XPathQueryService)
            testCol.getService("XPathQueryService", "1.0");
        for (int i = 1; i < 1000; i++) {
            int which = rand.nextInt(ITEM_COUNT) + 1;
            insert(service, which);
            remove(service, which);
            
            ResourceSet result = query.query("/test/item[@id='" + which + "']");
            assertEquals(result.getSize(), 1);
            result.getResource(0).getContent();
        }
    }
    
    @Test
    public void appendRemove() throws XMLDBException, IOException {
        XUpdateQueryService service = (XUpdateQueryService)
        testCol.getService("XUpdateQueryService", "1.0");
        XPathQueryService query = (XPathQueryService)
            testCol.getService("XPathQueryService", "1.0");
        for (int i = 1; i <= 100; i++) {
            append(service, i);
            
            ResourceSet result = query.query("/test/item[@id='" + i + "']");
            assertEquals(result.getSize(), 1);
        }
        
        for (int i = 100; i > 10; i--) {
            String xu = 
                "<xu:modifications xmlns:xu=\"http://www.xmldb.org/xupdate\" version=\"1.0\">" +
                "   <xu:remove select=\"/test/item[@id='" + i + "']\"/>" +
                "</xu:modifications>";
            long mods = service.update(xu);
            assertEquals(mods, 1);
            
            ResourceSet result = query.query("/test/item/e0");
        }
    }
    
    protected void append(final XUpdateQueryService service, final int id) throws IOException, XMLDBException {
        StringWriter out = new StringWriter();
        out.write("<xu:modifications xmlns:xu=\"http://www.xmldb.org/xupdate\" version=\"1.0\">");
        out.write("<xu:append select=\"/test\">");
        createItem(id, out);
        out.write("</xu:append>");
        out.write("</xu:modifications>");
        final long mods = service.update(out.toString());
        assertEquals(mods, 1);
    }
    
    protected void insert(final XUpdateQueryService service, final int id) throws IOException, XMLDBException {
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
    
    protected void remove(final XUpdateQueryService service, final int id) throws XMLDBException {
        @SuppressWarnings("unused")
		String xu = 
            "<xu:modifications xmlns:xu=\"http://www.xmldb.org/xupdate\" version=\"1.0\">" +
            "   <xu:remove select=\"/test/item[@id='" + id + "'][2]\"/>" +
            "</xu:modifications>";

        long mods = service.update(XU_REMOVE);
        assertEquals(mods, 1);
    }
    
    @Before
    public void setUp() throws Exception {
        rootCol = existXmldbEmbeddedServer.getRoot();
        
        testCol = rootCol.getChildCollection(XmldbURI.ROOT_COLLECTION + "/test");
        if(testCol != null) {
            CollectionManagementService mgr = DBUtils.getCollectionManagementService(rootCol);
            mgr.removeCollection(XmldbURI.ROOT_COLLECTION + "/test");
        }
        
        testCol = DBUtils.addCollection(rootCol, "test");
        assertNotNull(testCol);
        
        DBUtils.addXMLResource(testCol, "test.xml", "<test/>");
    }
    
    @After
    public void tearDown() throws LockException, TriggerException, PermissionDeniedException, EXistException, IOException {
        TestUtils.cleanupDB();
    }
    
    protected void createItem(final int id, final Writer out) throws IOException {
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
     * @throws IOException
     */
    private void addAttributes(final Writer out) throws IOException {
        for (int j = 0; j < 5; j++) {
            out.write(" attr");
            out.write(Integer.toString(j));
            out.write("=\"");
            out.write(Integer.toString(rand.nextInt()));
            out.write('"');
        }
    }
}
