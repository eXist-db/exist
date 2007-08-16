/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2007 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.xquery;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.exist.storage.DBBroker;
import org.exist.xquery.value.AnyURITest;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class AllXqueryTests {

    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }

    public static Test suite() {
        TestSuite suite = new TestSuite("Test for org.exist.xquery");
        //$JUnit-BEGIN$
        XPathQueryTest.setURI("xmldb:exist://" + DBBroker.ROOT_COLLECTION);
//        suite.addTestSuite(XQueryUpdateTest.class);
        suite.addTestSuite(XQueryFunctionsTest.class);
        suite.addTestSuite(JavaFunctionsTest.class);
        suite.addTestSuite(XPathQueryTest.class);
        suite.addTestSuite(XQueryTest.class);
        suite.addTestSuite(EntitiesTest.class);
        suite.addTestSuite(SpecialNamesTest.class);
        suite.addTestSuite(ValueIndexTest.class);
        suite.addTestSuite(LexerTest.class); // jmv: Note: LexerTest needs /db/test created by XPathQueryTest
        suite.addTestSuite(DeepEqualTest.class);
        suite.addTestSuite(SeqOpTest.class);
        suite.addTestSuite(XMLNodeAsXQueryParameterTest.class);
        suite.addTestSuite(OpNumericTest.class);
        suite.addTestSuite(FtQueryTest.class);
        suite.addTestSuite(DocumentUpdateTest.class);
        suite.addTestSuite(AnyURITest.class);
        suite.addTestSuite(XQueryGroupByTest.class);
        suite.addTestSuite(ConstructedNodesTest.class);
        suite.addTestSuite(ConstructedNodesRecoveryTest.class);
        //		suite.addTestSuite(XQueryUseCasesTest.class);
        //$JUnit-END$
        return suite;
    }
}
