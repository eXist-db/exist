/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * $Id$
 */

package org.exist.validation;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 *  Class for testing XML Parser and XML Transformer configuration.
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 */
public class ApacheXmlComponentsTest extends TestCase {
    
    public ApacheXmlComponentsTest(String testName) {
        super(testName);
    }
    
    public static Test suite() {
        TestSuite suite = new TestSuite(ApacheXmlComponentsTest.class);
        
        return suite;
    }
    
    protected void tearDown() {
        System.out.println("tearDown");
    }
    
    protected void setUp() {
        System.out.println("setUp");
    }
    
    
    public void testParserVersion() {
        StringBuffer xmlLibMessage = new StringBuffer();
        
        boolean validParser = XmlLibraryChecker.hasValidParser(xmlLibMessage);
        
        assertTrue(xmlLibMessage.toString(), validParser);
    }
    
    public void testTransformerVersion() {
        StringBuffer xmlLibMessage = new StringBuffer();
        
        boolean validTransformer = XmlLibraryChecker.hasValidTransformer(xmlLibMessage);
        
        assertTrue(xmlLibMessage.toString(), validTransformer);
    }
}
