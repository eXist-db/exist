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
package org.exist.xmldb.concurrent;

import org.exist.storage.DBBroker;
import org.exist.xmldb.concurrent.action.CreateCollectionAction;
import org.exist.xmldb.concurrent.action.ReplaceResourceAction;
import org.exist.xmldb.concurrent.action.XQueryAction;
import org.exist.xmldb.concurrent.action.XQueryUpdateAction;
import org.xmldb.api.base.XMLDBException;

public class FragmentsTest extends ConcurrentTestBase {

    /**
     * @param args
     */
    public static void main(String[] args) {
        junit.textui.TestRunner.run(FragmentsTest.class);
    }

    private final static String URI = "xmldb:exist://localhost:8080/exist/xmlrpc" + DBBroker.ROOT_COLLECTION;
    
    private final static String QUERY =
        "let $node := " +
        "   <root>" +
        "       <nodeA><nodeB>BBB</nodeB></nodeA>" +
        "       <nodeC>CCC</nodeC>" +
        "   </root>" +
        "return" +
        "   $node/nodeA/nodeB";
    
    public FragmentsTest(String name) {
        super(name, URI, "C1");
    }
    
    protected void setUp() {
        try {
            super.setUp();
            
            addAction(new XQueryAction(URI + "/C1", "test.xml", QUERY), 200, 0, 200);
            addAction(new XQueryAction(URI + "/C2", "test.xml", QUERY), 200, 0, 200);
            addAction(new CreateCollectionAction(URI + "/C1", "testappend.xml"), 200, 0, 0);
        } catch (Exception e) {
            fail(e.getMessage()); 
        }
    }
    
    /* (non-Javadoc)
     * @see org.exist.xmldb.test.concurrent.ConcurrentTestBase#tearDown()
     */
    protected void tearDown() {
//        try {
//            DBUtils.shutdownDB(URI);
//        } catch (XMLDBException e) {
//            e.printStackTrace();
//        }
    }
}
