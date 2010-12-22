package org.exist.xupdate;


import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

import org.exist.test.EmbeddedExistTester;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Ignore;


/**
 *  Test to show an xupdate/replace issue with the results of util:parse()
 * @author wessels
 */
public class AnotherXupdateTest extends EmbeddedExistTester {

    public AnotherXupdateTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }


     @Test @Ignore("Failing test")
     public void updateReplace() {
        try {
            // Store document
            Collection newCol = createCollection(rootCollection, "xupdatereplace");
            storeResource(newCol, "test.xml", "<x><y/></x>".getBytes());

            // check results
            ResourceSet results0 = executeQuery("doc('/db/xupdatereplace/test.xml')");
            assertEquals(1, results0.getSize());

            // perform query
            ResourceSet results1 = executeQuery("update replace doc('/db/xupdatereplace/test.xml')//y with util:parse('<y/>')");
            assertEquals(1, results1.getSize());

            // check results
            ResourceSet results2 = executeQuery("doc('/db/xupdatereplace/test.xml')");
            assertEquals(1, results2.getSize());

        } catch (XMLDBException ex) {
            LOG.error(ex);
            fail(ex.getMessage());
        }



     }

}