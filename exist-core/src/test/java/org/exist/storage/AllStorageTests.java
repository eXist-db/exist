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

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        NodePathTest.class,
        RemoveCollectionTest.class,
        ReindexTest.class,
        ReindexRecoveryTest.class,
        ShutdownTest.class,
        CollectionTest.class,
        CopyResourceRecoveryTest.class,
        MoveResourceRecoveryTest.class,
        CopyCollectionRecoveryTest.class,
        MoveCollectionRecoveryTest.class,
        MoveOverwriteCollectionTest.class,
        RecoverBinaryTest2.class,
        RecoveryTest.class,
        AppendTest.class,
        RemoveTest.class,
        RenameTest.class,
        ReplaceTest.class,
        UpdateTest.class,
        UpdateAttributeTest.class,
        UpdateRecoverTest.class,
        ResourceTest.class,
        RangeIndexUpdateTest.class,
        LargeValuesTest.class,
        StoreBinaryTest.class,
        ModificationTimeTest.class,
        StartupTriggerTest.class,
        ConcurrentBrokerPoolTest.class,
        StoreResourceTest.class
})
public class AllStorageTests {
}
