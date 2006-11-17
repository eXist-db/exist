/*
 * EntityResolverTest.java
 *
 * Created on September 11, 2005, 9:46 PM
 *
 *  $Id$
 */

package org.exist.validation;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 *
 * @author wessels
 */
public class EntityResolverTest extends TestCase {  
    
    public EntityResolverTest(String testName) {
        super(testName);
    }
    
    public static Test suite() {
        TestSuite suite = new TestSuite(EntityResolverTest.class);
        
        return suite;
    }
    
    protected void tearDown() {
        //
    }
    
    protected void setUp() {
        //
    }
    
    public void testDummyTestmethod() {
        //
    }
}
