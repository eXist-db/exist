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

import org.exist.xquery.value.AnyURITest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
        XQueryFunctionsTest.class,
        JavaFunctionsTest.class,
        XPathQueryTest.class,
        XQueryTest.class,
        AnnotationsTest.class,
        EntitiesTest.class,
        SpecialNamesTest.class,
        ValueIndexTest.class,
        LexerTest.class,
        DeepEqualTest.class,
        SeqOpTest.class,
        XMLNodeAsXQueryParameterTest.class,
        OpNumericTest.class,
        DocumentUpdateTest.class,
        AnyURITest.class,
        ConstructedNodesTest.class,
        ConstructedNodesRecoveryTest.class,
        DuplicateAttributesTest.class,
        StoredModuleTest.class,
        TransformTest.class,
        DeferredFunctionCallTest.class,
        UnionTest.class,
        TestXPathOpOrSpecialCase.class,
        MemtreeDescendantOrSelfNodeKindTest.class,
        PersistentDescendantOrSelfNodeKindTest.class,
        CleanupTest.class,
        RangeSequenceTest.class
})
public class AllXqueryTests {
}
