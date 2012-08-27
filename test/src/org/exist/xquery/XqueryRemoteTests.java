/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 The eXist Project
 *  http://exist-db.org
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

import junit.framework.Test;
import junit.framework.TestSuite;

import org.exist.xmldb.XmldbURI;

public class XqueryRemoteTests {
    
    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }

    public static Test suite() {
        TestSuite suite = new TestSuite("Test for org.exist.xquery");
        //$JUnit-BEGIN$
	// jetty.port.standalone
        XPathQueryTest.setURI("xmldb:exist://localhost:" + System.getProperty("jetty.port") + "/xmlrpc" + XmldbURI.ROOT_COLLECTION);
        suite.addTestSuite(XPathQueryTest.class);
        //$JUnit-END$
        return suite;
    }
}
