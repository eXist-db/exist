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
import org.exist.xmldb.concurrent.action.CreateCollectionAction;
import org.exist.xmldb.concurrent.action.XQueryAction;

import java.util.Arrays;
import java.util.List;

public class FragmentsTest extends ConcurrentTestBase {
    
    private final static String QUERY =
        "let $node := " +
        "   <root>" +
        "       <nodeA><nodeB>BBB</nodeB></nodeA>" +
        "       <nodeC>CCC</nodeC>" +
        "   </root>" +
        "return" +
        "   $node/nodeA/nodeB";

    @Override
    public String getTestCollectionName() {
        return "C1";
    }

    @Override
    public List<Runner> getRunners() {
        return Arrays.asList(
                new Runner(new XQueryAction(XmldbURI.LOCAL_DB + "/C1", "test.xml", QUERY), 200, 0, 50),
                new Runner(new XQueryAction(XmldbURI.LOCAL_DB + "/C2", "test.xml", QUERY), 200, 0, 50),
                new Runner(new CreateCollectionAction(XmldbURI.LOCAL_DB + "/C1", "testappend.xml"), 200, 0, 0)
        );
    }
}
