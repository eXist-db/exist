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

import org.exist.xmldb.XmldbURI;
import org.exist.xmldb.concurrent.action.MultiResourcesAction;
import org.exist.xmldb.concurrent.action.XQueryAction;
import org.junit.After;
import org.junit.Before;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;

import static org.junit.Assert.assertNotNull;

/**
 * @author wolf
 */
public class ConcurrentResourceTest2 extends ConcurrentTestBase {

    private final static String URI = XmldbURI.LOCAL_DB;
    
    private final static String QUERY0 =
        "declare default element namespace 'http://www.loc.gov/mods/v3';" +
        "collection(\"" + XmldbURI.ROOT_COLLECTION + "\")//mods[titleInfo/title &= 'germany']";
    
    private final static String QUERY1 =
        "declare default element namespace 'http://www.loc.gov/mods/v3';" +
        "<result>{for $t in distinct-values(\"" + XmldbURI.ROOT_COLLECTION + "\")//mods/subject/topic) order by $t return <topic>{$t}</topic>}</result>";

    public ConcurrentResourceTest2() {
        super(URI, "C1");
    }

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        Collection c1 = DBUtils.addCollection(getTestCollection(), "C1-C2");
        assertNotNull(c1);
        addAction(new MultiResourcesAction("samples/mods", URI + "/C1/C1-C2"), 200, 0, 300);
        addAction(new MultiResourcesAction("samples/mods", URI + "/C1/C1-C2"), 200, 0, 300);
        addAction(new XQueryAction(URI + "/C1/C1-C2", "R1.xml", QUERY0), 200, 200, 500);
        addAction(new XQueryAction(URI + "/C1/C1-C2", "R1.xml", QUERY1), 200, 300, 500);
        //addAction(new XQueryAction(URI + "/C1/C1-C2", "R1.xml", QUERY0), 200, 400, 500);
        //addAction(new XQueryAction(URI + "/C1/C1-C2", "R1.xml", QUERY1), 200, 500, 500);
    }

    @After
    @Override
    public void tearDown() throws XMLDBException {
        super.tearDown();
    }
}
