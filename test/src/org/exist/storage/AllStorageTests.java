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
package org.exist.storage;

import junit.framework.Test;
import junit.framework.TestSuite;

public class AllStorageTests {

    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }

    public static Test suite() {
        TestSuite suite = new TestSuite("Test for org.exist.storage");
        //$JUnit-BEGIN$
        // TODO: some problem here... uncommenting next test causes a db corruption
        suite.addTestSuite(ShutdownTest.class);
        suite.addTestSuite(CollectionTest.class);
        suite.addTestSuite(CopyResourceTest.class);
        suite.addTestSuite(MoveResourceTest.class);
        suite.addTestSuite(CopyCollectionTest.class);
        suite.addTestSuite(RecoverBinaryTest.class);
        suite.addTestSuite(RecoverBinaryTest2.class);
        suite.addTestSuite(RecoveryTest.class);
        suite.addTestSuite(RemoveCollectionTest.class);
        suite.addTestSuite(AppendTest.class);
        suite.addTestSuite(RemoveTest.class);
        suite.addTestSuite(RenameTest.class);
        suite.addTestSuite(ReplaceTest.class);
        suite.addTestSuite(UpdateTest.class);
        suite.addTestSuite(UpdateAttributeTest.class);
        suite.addTestSuite(UpdateRecoverTest.class);
        suite.addTestSuite(ResourceTest.class);
        //$JUnit-END$
        return suite;
    }
}
